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
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")


def get_paths():
    return settings.USER_DATA_DIR, settings.DB_PATH, settings.EXPORTS_DIR


async def run_test_auth(args):
    user_data_dir, _, _ = get_paths()
    console.print("[bold cyan]=== Mani-Mani Sync Lab: VTB Online Authentication Test ===[/bold cyan]")
    console.print(f"Profile directory: [yellow]{user_data_dir}[/yellow]")
    console.print("Launching browser with persistent profile (manual login mode)...")

    adapter = VtbBankAdapter(user_data_dir=user_data_dir, headless=False)
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
        if not args.keep_open:
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
    adapter = VtbBankAdapter(user_data_dir=user_data_dir, headless=False)
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
            saved_count = repo.save_normalized_transactions(dedup_normalized, user_id=args.user_id)
            console.print(f"[bold green]Successfully normalized and saved {saved_count} unique verified transactions (raw captured: {len(raw_txs)})![/bold green]")

            # 4. Export JSON
            export_file = export_dir / "vtb_transactions.json"
            repo.export_to_json(export_file, user_id=args.user_id)
            console.print(f"[bold green]Exported Mani-Mani compatible JSON to: {export_file}[/bold green]")
        else:
            console.print("[yellow]No transactions captured. Check if history section finished loading.[/yellow]")

    finally:
        await adapter.close()
        console.print("Sync process finished.")


def run_reprocess(user_id: str = "default_user"):
    _, db_path, export_dir = get_paths()
    if not db_path.exists():
        console.print(f"[red]Database not found at {db_path}[/red]")
        return

    console.print(f"[bold cyan]Reprocessing stored raw transactions from: {db_path}[/bold cyan]")
    repo = SqliteTransactionRepository(db_path)
    normalizer = VtbTransactionNormalizer()

    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    c = conn.cursor()
    c.execute("SELECT * FROM raw_transactions WHERE user_id = ?", (user_id,))
    rows = c.fetchall()
    conn.close()

    if not rows:
        console.print(f"[yellow]No raw transactions in database for user '{user_id}'.[/yellow]")
        return

    console.print(f"Loaded {len(rows)} raw transactions from database.")

    raw_txs = []
    accounts_map = {}

    import re
    for r in rows:
        payload = json.loads(r["payload_json"])
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
        prod_id = raw_data.get("accountProductId") or acc_val
        if acc_val and prod_id not in accounts_map:
            digits = re.findall(r"\d{4}", str(acc_val))
            last4 = digits[-1] if digits else str(acc_val)
            accounts_map[prod_id] = BankAccountInfo(
                id=str(prod_id),
                user_id=user_id,
                name=f"Счет ВТБ {acc_val}".strip(),
                account_type=AccountType.DEBIT,
                balance=0.0,
                currency=r["raw_currency"] or "RUB",
                account_last4=last4,
                raw_details={"bank_name": "ВТБ", "account": acc_val, "accountProductId": prod_id}
            )

    # Save accounts
    if accounts_map:
        saved_accs = repo.save_accounts(list(accounts_map.values()), user_id=user_id)
        console.print(f"[green]Discovered and saved {saved_accs} distinct accounts to SQLite.[/green]")

    # Normalize transactions
    normalized = []
    failed = 0

    for raw in raw_txs:
        norm = normalizer.normalize_transaction(raw)
        if norm:
            normalized.append(norm)
        else:
            failed += 1

    # Deduplicate normalized transactions by stable bank_id
    dedup_normalized = list({tx.bank_id: tx for tx in normalized}.values())

    type_counts = {}
    for tx in dedup_normalized:
        type_counts[tx.type.value] = type_counts.get(tx.type.value, 0) + 1

    # Clear old normalized table for this user to eliminate previously stored ephemeral ID duplicates
    with repo._get_connection() as conn:
        conn.execute("DELETE FROM normalized_transactions WHERE user_id = ?", (user_id,))
        conn.commit()

    saved_count = repo.save_normalized_transactions(dedup_normalized, user_id=user_id)
    console.print(f"[bold green]Normalized & saved: {saved_count} unique verified transactions (from {len(raw_txs)} raw items, duplicates removed: {len(raw_txs) - len(dedup_normalized)}, Failed: {failed})[/bold green]")
    console.print(f"Distribution: [magenta]{type_counts}[/magenta]")

    # Export to JSON
    export_file = export_dir / "vtb_transactions.json"
    repo.export_to_json(export_file, user_id=user_id)
    console.print(f"[bold green]Exported Mani-Mani JSON to: {export_file}[/bold green]")


def run_status(user_id: str = "default_user"):
    _, db_path, _ = get_paths()
    if not db_path.exists():
        console.print(f"[yellow]Database file does not exist yet at {db_path}[/yellow]")
        return

    repo = SqliteTransactionRepository(db_path)
    txs = repo.get_normalized_transactions(user_id=user_id, limit=25)

    conn = sqlite3.connect(str(db_path))
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM bank_accounts WHERE user_id = ?", (user_id,))
    acc_count = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM raw_transactions WHERE user_id = ?", (user_id,))
    raw_count = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM normalized_transactions WHERE user_id = ?", (user_id,))
    norm_count = c.fetchone()[0]
    conn.close()

    console.print(f"[bold cyan]Database: {db_path}[/bold cyan] (User: [yellow]{user_id}[/yellow])")
    console.print(f"Summary: Accounts: [bold green]{acc_count}[/bold green] | Raw: [bold green]{raw_count}[/bold green] | Normalized: [bold green]{norm_count}[/bold green]")

    if not txs:
        console.print("[yellow]No normalized transactions for this user yet.[/yellow]")
        return

    table = Table(title=f"Stored Normalized Transactions (Showing latest {len(txs)} of {norm_count})")
    table.add_column("Date (UTC)", style="cyan")
    table.add_column("Type", style="magenta")
    table.add_column("Amount", style="green", justify="right")
    table.add_column("Cur", style="white")
    table.add_column("Description", style="white")
    table.add_column("Account", style="yellow")
    table.add_column("Status", style="bold")

    from datetime import datetime, timezone
    for t in txs:
        dt_str = datetime.fromtimestamp(t.timestamp / 1000, tz=timezone.utc).strftime("%Y-%m-%d %H:%M")
        table.add_row(
            dt_str,
            t.type.value,
            f"{t.amount:.2f}",
            t.currency,
            t.description[:40],
            t.account_last4 or "-",
            t.status
        )

    console.print(table)


def run_export(output: str, user_id: str = "default_user"):
    _, db_path, export_dir = get_paths()
    out_path = Path(output) if output else export_dir / "transactions_export.json"
    repo = SqliteTransactionRepository(db_path)
    repo.export_to_json(out_path, user_id=user_id)
    console.print(f"[green]Successfully exported transactions for user '{user_id}' to [bold]{out_path}[/bold][/green]")


def run_server(host: str, port: int, reload: bool):
    import uvicorn
    console.print(f"[bold cyan]Starting {settings.PROJECT_NAME} on {host}:{port}...[/bold cyan]")
    uvicorn.run("sync_lab.server.app:app", host=host, port=port, reload=reload)


def run_user_create(username: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    existing = repo.get_user_by_username(username)
    if existing:
        console.print(f"[yellow]User '{username}' already exists (ID: {existing.id}).[/yellow]")
        return
    user = repo.create_user(username)
    console.print(f"[bold green]User created successfully![/bold green] ID: [cyan]{user.id}[/cyan], Username: [cyan]{user.username}[/cyan]")


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


def run_api_key_create(username: str, name: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    user = repo.get_user_by_username(username)
    if not user:
        console.print(f"[red]User '{username}' not found. Create user first with 'user create'.[/red]")
        return
    raw_key, api_key = repo.create_api_key(user_id=user.id, name=name)
    console.print("[bold green]API Key generated successfully![/bold green]")
    console.print("[bold yellow]IMPORTANT: Save this key now! It will not be shown again.[/bold yellow]")
    console.print(f"Key: [bold white on blue] {raw_key} [/bold white on blue]")
    console.print(f"Prefix: {api_key.key_prefix} | User: {user.username} | Name: {api_key.name}")


def run_api_key_list(username: str):
    _, db_path, _ = get_paths()
    repo = SqliteTransactionRepository(db_path)
    user = repo.get_user_by_username(username)
    if not user:
        console.print(f"[red]User '{username}' not found.[/red]")
        return
    keys = repo.list_api_keys(user.id)
    table = Table(title=f"API Keys for {username} ({len(keys)})")
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

    # User management
    user_parser = subparsers.add_parser("user", help="User management commands")
    user_sub = user_parser.add_subparsers(dest="subcommand", required=True)
    uc = user_sub.add_parser("create", help="Create a user")
    uc.add_argument("username", help="Unique username")
    user_sub.add_parser("list", help="List all users")

    # API Key management
    key_parser = subparsers.add_parser("api-key", help="API key management commands")
    key_sub = key_parser.add_subparsers(dest="subcommand", required=True)
    kc = key_sub.add_parser("create", help="Generate an API key")
    kc.add_argument("username", help="Target username")
    kc.add_argument("--name", default="Mani-Mani Client", help="Friendly key name")
    kl = key_sub.add_parser("list", help="List API keys for a user")
    kl.add_argument("username", help="Target username")
    kr = key_sub.add_parser("revoke", help="Revoke an API key")
    kr.add_argument("identifier", help="Key hash or prefix")

    # Testing & Sync commands
    test_parser = subparsers.add_parser("test-auth", help="Launch browser and open VTB login page")
    test_parser.add_argument("--keep-open", action="store_true", help="Keep browser open after probe")

    sync_parser = subparsers.add_parser("sync", help="Run full VTB sync pipeline")
    sync_parser.add_argument("--user-id", default="default_user", help="Target user ID")

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
            run_user_create(args.username)
        elif args.subcommand == "list":
            run_user_list()
    elif args.command == "api-key":
        if args.subcommand == "create":
            run_api_key_create(args.username, args.name)
        elif args.subcommand == "list":
            run_api_key_list(args.username)
        elif args.subcommand == "revoke":
            run_api_key_revoke(args.identifier)
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
