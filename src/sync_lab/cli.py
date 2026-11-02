"""
CLI runner for Mani-Mani Sync Lab research experiments and Server administration.

Commands:
  server       Run the HTTP API server
  user         Manage multi-tenant users (create, list)
  api-key      Manage secure API keys (create, list, revoke)
  sync         Launch browser, await manual authentication, capture & normalize transactions
  test-auth    Launch Chromium and navigate to VTB Online login page
  reprocess    Reprocess raw transactions already stored in SQLite with updated normalizer
  status       Show SQLite database contents (accounts, raw, normalized)
  export-json  Export stored normalized transactions to JSON
"""

import argparse
import asyncio
import json
import logging
import re
import sqlite3
import sys
from pathlib import Path
from rich.console import Console
from rich.table import Table

from sync_lab.config import settings
from sync_lab.adapters.vtb.adapter import VtbBankAdapter
from sync_lab.adapters.vtb.vtb_normalizer import VtbTransactionNormalizer
from sync_lab.core.models import RawBankTransaction, BankAccountInfo, AccountType
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository

console = Console()
logging.basicConfig(level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO), format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")


def get_paths():
    return settings.USER_DATA_DIR, settings.DB_PATH, settings.EXPORTS_DIR


async def run_test_auth(args):
    user_data_dir, _, _ = get_paths()
    console.print("[bold cyan]=== Mani-Mani Sync Lab: VTB Online Authentication Test ===[/bold cyan]")
    console.print(f"Profile directory: [yellow]{user_data_dir}[/yellow]")
    console.print(f"Browser mode: [bold]{'Headless (Server)' if args.headless else 'Visible UI (Desktop)'}[/bold]")
    console.print("Launching browser with persistent profile...")

    adapter = VtbBankAdapter(user_data_dir=user_data_dir, headless=args.headless)
    try:
        await adapter.initialize()
        await adapter.open_login_page()
        console.print("[green]Login page successfully requested.[/green]")
        console.print("[white]Please complete authentication in the browser window if prompted.[/white]")
        console.print("Waiting for login status check (30 seconds probe)...")

        is_auth = await adapter.wait_for_login(timeout_seconds=30)
        if is_auth:
            console.print("[bold green]User is AUTHENTICATED in VTB Online![/bold green]")
        else:
            console.print("[yellow]User is not authenticated yet (or requires more time). Session state is preserved.[/yellow]")
    finally:
        if not getattr(args, "keep_open", False):
            await adapter.close()
            console.print("Browser closed.")
        else:
            console.print("[bold yellow]Browser left open. Close it manually or press Ctrl+C.[/bold yellow]")
            while True:
                await asyncio.sleep(1)


async def run_sync(args):
    user_data_dir, db_path, export_dir = get_paths()
    repo = SqliteTransactionRepository(db_path)
    normalizer = VtbTransactionNormalizer()

    console.print("[bold cyan]=== Mani-Mani Sync Lab: VTB Online Full Synchronization Probe ===[/bold cyan]")
    console.print(f"Browser mode: [bold]{'Headless (Server)' if args.headless else 'Visible UI (Desktop)'}[/bold]")
    adapter = VtbBankAdapter(user_data_dir=user_data_dir, headless=args.headless)
    try:
        await adapter.initialize()
        await adapter.open_login_page()

        console.print("[cyan]Waiting for user manual authentication (up to 300 seconds)...[/cyan]")
        is_auth = await adapter.wait_for_login(timeout_seconds=300)
        if not is_auth:
            console.print("[bold red]Authentication was not completed in time. Aborting sync probe.[/bold red]")
            return

        console.print("[bold green]Authentication confirmed. Proceeding to fetch history...[/bold green]")

        # 1. Fetch Transactions
        raw_txs = await adapter.fetch_transactions(days_back=30)
        console.print(f"[cyan]Captured {len(raw_txs)} raw transactions.[/cyan]")
        if raw_txs:
            repo.save_raw_transactions(raw_txs, user_id=args.user_id)

            # 2. Fetch Accounts (from portfolio and transaction history)
            accounts = await adapter.fetch_accounts(raw_transactions=raw_txs)
            if accounts:
                saved_accs = repo.save_accounts(accounts, user_id=args.user_id)
                console.print(f"[green]Saved {saved_accs} bank accounts/cards to SQLite.[/green]")

            # 3. Normalize & Deduplicate
            normalized = []
            for raw in raw_txs:
                norm = normalizer.normalize_transaction(raw)
                if norm:
                    normalized.append(norm)

            dedup_normalized = list({tx.bank_id: tx for tx in normalized}.values())
            saved = repo.save_normalized_transactions(dedup_normalized, user_id=args.user_id)
            console.print(f"[bold green]Normalized and saved {saved} unique transactions to SQLite.[/bold green]")

            # 4. Auto-export to JSON
            export_path = export_dir / f"vtb_sync_{args.user_id}.json"
            repo.export_to_json(export_path, user_id=args.user_id)
            console.print(f"[green]Exported snapshot to {export_path}[/green]")
        else:
            console.print("[yellow]No raw transactions intercepted during sync session.[/yellow]")
    finally:
        await adapter.close()


def run_status(user_id: str):
    _, db_path, _ = get_paths()
    if not db_path.exists():
        console.print(f"[yellow]Database not found at {db_path}[/yellow]")
        return

    repo = SqliteTransactionRepository(db_path)
    accounts = repo.get_accounts(user_id=user_id)
    raw_count = 0
    with repo._get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM raw_transactions WHERE user_id = ?", (user_id,))
        raw_count = cur.fetchone()[0]

    normalized = repo.get_normalized_transactions(user_id=user_id, limit=25)
    total_norm = repo.count_normalized_transactions(user_id=user_id)

    console.print(f"[bold cyan]Database: {db_path} (User: {user_id})[/bold cyan]")
    console.print(f"Summary: Accounts: [green]{len(accounts)}[/green] | Raw: [yellow]{raw_count}[/yellow] | Normalized: [green]{total_norm}[/green]")

    if normalized:
        table = Table(title=f"Stored Normalized Transactions (Showing latest {len(normalized)} of {total_norm})")
        table.add_column("Date (UTC)", style="cyan")
        table.add_column("Type", style="magenta")
        table.add_column("Amount", justify="right", style="green")
        table.add_column("Cur", style="white")
        table.add_column("Description", style="white")
        table.add_column("Account", style="yellow")
        table.add_column("Status", style="bold")

        for tx in normalized:
            import datetime
            dt_str = datetime.datetime.fromtimestamp(tx.timestamp / 1000, tz=datetime.timezone.utc).strftime("%Y-%m-%d %H:%M")
            acc_str = tx.account_last4 or tx.card_last4 or "-"
            table.add_row(
                dt_str,
                tx.type.value,
                f"{tx.amount:.2f}",
                tx.currency,
                tx.description[:30],
                acc_str,
                tx.status
            )
        console.print(table)


def run_export(output: str, user_id: str):
    _, db_path, export_dir = get_paths()
    repo = SqliteTransactionRepository(db_path)
    out_path = Path(output) if output else export_dir / f"mani_mani_export_{user_id}.json"
    repo.export_to_json(out_path, user_id=user_id)
    console.print(f"[bold green]Successfully exported JSON to {out_path}[/bold green]")


def run_reprocess(user_id: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    normalizer = VtbTransactionNormalizer()

    console.print(f"[bold cyan]Reprocessing raw transactions for user '{user_id}'...[/bold cyan]")

    with repo._get_connection() as conn:
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute("SELECT * FROM raw_transactions WHERE user_id = ?", (user_id,))
        rows = cur.fetchall()

    if not rows:
        console.print("[yellow]No raw transactions found to reprocess.[/yellow]")
        return

    raw_txs = []
    accounts_map = {}

    for r in rows:
        payload = json.loads(r["payload_json"]) if r["payload_json"] else {}
        raw = RawBankTransaction(
            raw_id=r["raw_id"],
            user_id=r["user_id"],
            bank_name=r["bank_name"],
            raw_title=r["raw_title"],
            raw_amount=r["raw_amount"],
            raw_currency=r["raw_currency"],
            raw_date=r["raw_date"],
            raw_category=r["raw_category"],
            raw_status=r["raw_status"],
            account_tail=r["account_tail"],
            card_tail=r["card_tail"],
            raw_payload=payload
        )
        raw_txs.append(raw)

        # Extract accounts
        raw_data = payload.get("raw_data", {})
        acc_val = raw_data.get("account")
        prod_id = str(raw_data.get("accountProductId") or acc_val or "")
        if acc_val and prod_id not in accounts_map:
            digits = re.findall(r"\d{4}", str(acc_val))
            last4 = digits[-1] if digits else str(acc_val)
            accounts_map[prod_id] = BankAccountInfo(
                id=prod_id,
                user_id=user_id,
                name=f"Счет ВТБ {acc_val}".strip(),
                account_type=AccountType.DEBIT,
                balance=0.0,
                currency=r["raw_currency"] or "RUB",
                account_last4=last4,
                account_product_id=prod_id,
                raw_details={"bank_name": "ВТБ", "account": acc_val, "accountProductId": prod_id}
            )

    if accounts_map:
        saved_accs = repo.save_accounts(list(accounts_map.values()), user_id=user_id)
        console.print(f"[green]Discovered and saved {saved_accs} distinct accounts to SQLite.[/green]")

    normalized = []
    failed = 0
    for raw in raw_txs:
        norm = normalizer.normalize_transaction(raw)
        if norm:
            normalized.append(norm)
        else:
            failed += 1

    dedup_normalized = list({tx.bank_id: tx for tx in normalized}.values())

    # Replace normalized records for user cleanly
    with repo._get_connection() as conn:
        conn.execute("DELETE FROM normalized_transactions WHERE user_id = ?", (user_id,))
        conn.commit()

    saved = repo.save_normalized_transactions(dedup_normalized, user_id=user_id)
    console.print(f"[bold green]Reprocessed {len(raw_txs)} raw rows -> Saved {saved} unique normalized transactions.[/bold green]")


def run_server(host: str, port: int, reload: bool):
    import uvicorn
    console.print(f"[bold green]Starting Mani-Mani Sync Lab Server on {host}:{port}...[/bold green]")
    uvicorn.run("sync_lab.server.app:app", host=host, port=port, reload=reload)


def run_user_create(username: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    existing = repo.get_user_by_username(username)
    if existing:
        console.print(f"[yellow]User '{username}' already exists with ID: {existing.id}[/yellow]")
        return
    user = repo.create_user(username=username)
    console.print("[bold green]User created successfully:[/bold green]")
    console.print(f"  ID:       [cyan]{user.id}[/cyan]")
    console.print(f"  Username: [green]{user.username}[/green]")
    console.print(f"  Active:   {user.is_active}")


def run_user_list():
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    users = repo.list_users()
    table = Table(title=f"Sync Lab Users ({len(users)})")
    table.add_column("User ID", style="cyan")
    table.add_column("Username", style="green")
    table.add_column("Active", style="magenta")
    for u in users:
        table.add_row(u.id, u.username, str(u.is_active))
    console.print(table)


def run_api_key_create(user_identifier: str, name: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    user = repo.get_user(user_identifier) or repo.get_user_by_username(user_identifier)
    if not user:
        console.print(f"[red]User '{user_identifier}' not found. Create user first with 'user create'.[/red]")
        return
    raw_key, api_key = repo.create_api_key(user_id=user.id, name=name)
    console.print("[bold green]API Key generated successfully![/bold green]")
    console.print("[bold yellow]IMPORTANT: Store this key securely. It cannot be recovered later![/bold yellow]")
    console.print(f"  Raw Secret Key:  [bold white on blue] {raw_key} [/bold white on blue]")
    console.print(f"  Prefix:          [cyan]{api_key.key_prefix}[/cyan]")
    console.print(f"  User ID:         [cyan]{user.id}[/cyan] ({user.username})")
    console.print(f"  Name:            [white]{api_key.name}[/white]")


def run_api_key_list(user_identifier: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    user = repo.get_user(user_identifier) or repo.get_user_by_username(user_identifier)
    if not user:
        console.print(f"[red]User '{user_identifier}' not found.[/red]")
        return
    keys = repo.list_api_keys(user.id)
    table = Table(title=f"API Keys for {user.username} ({len(keys)})")
    table.add_column("Prefix", style="cyan")
    table.add_column("Name", style="white")
    table.add_column("Revoked", style="red")
    table.add_column("Key Hash (SHA-256)", style="dim")
    for k in keys:
        table.add_row(k.key_prefix, k.name, str(k.is_revoked), k.key_hash[:16] + "...")
    console.print(table)


def run_api_key_revoke(key_identifier: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    revoked = repo.revoke_api_key(key_identifier)
    if revoked:
        console.print(f"[bold green]API key '{key_identifier}' successfully revoked.[/bold green]")
    else:
        console.print(f"[yellow]No matching active key found for '{key_identifier}'.[/yellow]")


def main():
    parser = argparse.ArgumentParser(description="Mani-Mani Sync Lab CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Server command
    srv_parser = subparsers.add_parser("server", help="Run HTTP API server")
    srv_parser.add_argument("--host", default=settings.HOST, help="Host to bind")
    srv_parser.add_argument("--port", type=int, default=settings.PORT, help="Port to bind")
    srv_parser.add_argument("--reload", action="store_true", help="Enable hot reload")

    # User management (flexible: supports positional or --username flag)
    user_parser = subparsers.add_parser("user", help="User management commands")
    user_sub = user_parser.add_subparsers(dest="subcommand", required=True)
    uc = user_sub.add_parser("create", help="Create a user")
    uc.add_argument("username_pos", nargs="?", help="Unique username")
    uc.add_argument("--username", "-u", dest="username_flag", help="Unique username")
    user_sub.add_parser("list", help="List all users")

    # API Key management (flexible: supports positional or flags)
    key_parser = subparsers.add_parser("api-key", help="API key management commands")
    key_sub = key_parser.add_subparsers(dest="subcommand", required=True)
    kc = key_sub.add_parser("create", help="Generate an API key")
    kc.add_argument("user_pos", nargs="?", help="Target username or user ID")
    kc.add_argument("--user-id", "--username", "-u", dest="user_flag", help="Target user ID or username")
    kc.add_argument("--name", "-n", default="Mani-Mani Client", help="Friendly key name")

    kl = key_sub.add_parser("list", help="List API keys for a user")
    kl.add_argument("user_pos", nargs="?", help="Target username or user ID")
    kl.add_argument("--user-id", "--username", "-u", dest="user_flag", help="Target user ID or username")

    kr = key_sub.add_parser("revoke", help="Revoke an API key")
    kr.add_argument("id_pos", nargs="?", help="Key hash or prefix")
    kr.add_argument("--prefix", "--hash", "-p", dest="id_flag", help="Key hash or prefix")

    # Testing & Sync commands
    test_parser = subparsers.add_parser("test-auth", help="Launch browser and open VTB login page")
    test_parser.add_argument("--keep-open", action="store_true", help="Keep browser open after probe")
    test_parser.add_argument("--headless", action="store_true", default=settings.HEADLESS, help="Run browser in headless mode")
    test_parser.add_argument("--no-headless", action="store_false", dest="headless", help="Run browser with visible UI")

    sync_parser = subparsers.add_parser("sync", help="Run full VTB sync pipeline")
    sync_parser.add_argument("--user-id", default="default_user", help="Target user ID")
    sync_parser.add_argument("--headless", action="store_true", default=settings.HEADLESS, help="Run browser in headless mode")
    sync_parser.add_argument("--no-headless", action="store_false", dest="headless", help="Run browser with visible UI")

    rep_parser = subparsers.add_parser("reprocess", help="Reprocess raw transactions")
    rep_parser.add_argument("--user-id", default="default_user", help="Target user ID")

    status_parser = subparsers.add_parser("status", help="Show SQLite database status")
    status_parser.add_argument("--user-id", default="default_user", help="Target user ID")

    export_parser = subparsers.add_parser("export-json", help="Export transactions to Mani-Mani JSON")
    export_parser.add_argument("-o", "--output", help="Path to output JSON file")
    export_parser.add_argument("--user-id", default="default_user", help="Target user ID")

    args = parser.parse_args()

    if args.command == "server":
        run_server(args.host, args.port, args.reload)
    elif args.command == "user":
        if args.subcommand == "create":
            uname = args.username_flag or args.username_pos
            if not uname:
                console.print("[red]Error: username is required. Provide as argument or --username <name>[/red]")
                sys.exit(1)
            run_user_create(uname)
        elif args.subcommand == "list":
            run_user_list()
    elif args.command == "api-key":
        if args.subcommand == "create":
            u_ident = args.user_flag or args.user_pos
            if not u_ident:
                console.print("[red]Error: user ID or username is required. Provide as argument or --user-id <id>[/red]")
                sys.exit(1)
            run_api_key_create(u_ident, args.name)
        elif args.subcommand == "list":
            u_ident = args.user_flag or args.user_pos
            if not u_ident:
                console.print("[red]Error: user ID or username is required. Provide as argument or --user-id <id>[/red]")
                sys.exit(1)
            run_api_key_list(u_ident)
        elif args.subcommand == "revoke":
            ident = args.id_flag or args.id_pos
            if not ident:
                console.print("[red]Error: key prefix or hash is required. Provide as argument or --prefix <prefix>[/red]")
                sys.exit(1)
            run_api_key_revoke(ident)
    elif args.command == "test-auth":
        asyncio.run(run_test_auth(args))
    elif args.command == "sync":
        asyncio.run(run_sync(args))
    elif args.command == "reprocess":
        run_reprocess(args.user_id)
    elif args.command == "status":
        run_status(args.user_id)
    elif args.command == "export-json":
        run_export(args.output, args.user_id)


if __name__ == "__main__":
    main()
