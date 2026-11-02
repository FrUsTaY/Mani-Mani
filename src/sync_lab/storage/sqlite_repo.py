"""
SQLite repository implementation for Mani-Mani Sync Lab.

Stores captured bank accounts, raw payloads (sanitized) and normalized transactions.
Supports multi-tenancy with isolated users and hashed API keys.

Reusability Guide:
1. Reusable / 1:1 mapping:
   - Database schema directly corresponds to Room tables in Android:
     `accounts`, `transactions`, `raw_bank_transactions`.
2. Android adaptation needed:
   - On Android this will be Room DAO (FinanceDaos.kt).
3. Server-side features:
   - User isolation, API key SHA-256 authentication, pagination, and since_timestamp sync.
"""

import hashlib
import json
import secrets
import sqlite3
import time
from pathlib import Path
from typing import List, Optional, Tuple, Dict, Any

from sync_lab.core.interfaces import BaseTransactionRepository
from sync_lab.core.models import (
    BankAccountInfo,
    AccountType,
    RawBankTransaction,
    NormalizedTransaction,
    TransactionType,
    User,
    ApiKey,
)


class SqliteTransactionRepository(BaseTransactionRepository):
    """
    Local SQLite repository for Sync Lab research and multi-user server API.
    """

    def __init__(self, db_path: Path):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path))
        conn.row_factory = sqlite3.Row
        # Enable foreign keys
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def _init_db(self) -> None:
        with self._get_connection() as conn:
            cursor = conn.cursor()

            # 1. Users table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    created_at INTEGER NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1
                )
            """)

            # 2. API Keys table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS api_keys (
                    key_hash TEXT PRIMARY KEY,
                    key_prefix TEXT NOT NULL,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_used_at INTEGER,
                    is_revoked INTEGER NOT NULL DEFAULT 0
                )
            """)

            # 3. Bank Accounts table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS bank_accounts (
                    id TEXT NOT NULL,
                    user_id TEXT NOT NULL DEFAULT 'default_user',
                    bank_name TEXT NOT NULL,
                    name TEXT NOT NULL,
                    account_type TEXT NOT NULL,
                    balance REAL NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'RUB',
                    card_last4 TEXT,
                    account_last4 TEXT,
                    is_blocked INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (user_id, id)
                )
            """)

            # 4. Raw transactions table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS raw_transactions (
                    raw_id TEXT NOT NULL,
                    user_id TEXT NOT NULL DEFAULT 'default_user',
                    bank_name TEXT NOT NULL,
                    raw_title TEXT NOT NULL,
                    raw_amount TEXT NOT NULL,
                    raw_currency TEXT,
                    raw_date TEXT NOT NULL,
                    raw_category TEXT,
                    raw_status TEXT,
                    account_tail TEXT,
                    card_tail TEXT,
                    payload_json TEXT,
                    captured_at INTEGER NOT NULL,
                    PRIMARY KEY (user_id, raw_id)
                )
            """)

            # 5. Normalized transactions table (aligns with Mani-Mani TransactionEntity)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS normalized_transactions (
                    bank_id TEXT NOT NULL,
                    user_id TEXT NOT NULL DEFAULT 'default_user',
                    bank_name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'RUB',
                    timestamp INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    merchant TEXT,
                    mcc INTEGER,
                    card_last4 TEXT,
                    account_last4 TEXT,
                    balance_after REAL,
                    fee REAL NOT NULL DEFAULT 0.0,
                    status TEXT NOT NULL DEFAULT 'CONFIRMED',
                    category_suggestion TEXT,
                    raw_source_id TEXT,
                    is_internal_transfer INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, bank_id)
                )
            """)

            # Run safe migrations for existing databases that were created without user_id
            self._migrate_schema_if_needed(cursor)

            # Ensure default user exists so legacy data remains accessible
            now = int(time.time() * 1000)
            cursor.execute("""
                INSERT OR IGNORE INTO users (id, username, created_at, is_active)
                VALUES ('default_user', 'default_user', ?, 1)
            """, (now,))

            # Create performance indexes
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_norm_tx_user_ts ON normalized_transactions(user_id, timestamp DESC)")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_norm_tx_user_acc ON normalized_transactions(user_id, account_last4)")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id)")

            conn.commit()

    def _migrate_schema_if_needed(self, cursor: sqlite3.Cursor) -> None:
        """Safely alters existing tables if columns are missing."""
        tables_to_check = {
            "bank_accounts": ["user_id"],
            "raw_transactions": ["user_id"],
            "normalized_transactions": ["user_id", "is_internal_transfer", "created_at"],
        }
        for table, required_cols in tables_to_check.items():
            cursor.execute(f"PRAGMA table_info({table})")
            existing_cols = {row["name"] for row in cursor.fetchall()}
            for col in required_cols:
                if col not in existing_cols:
                    if col == "user_id":
                        cursor.execute(f"ALTER TABLE {table} ADD COLUMN user_id TEXT NOT NULL DEFAULT 'default_user'")
                    elif col == "is_internal_transfer":
                        cursor.execute(f"ALTER TABLE {table} ADD COLUMN is_internal_transfer INTEGER NOT NULL DEFAULT 0")
                    elif col == "created_at":
                        cursor.execute(f"ALTER TABLE {table} ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")

    # ---------------------------------------------------------
    # User and API Key Management
    # ---------------------------------------------------------

    def create_user(self, username: str, user_id: Optional[str] = None) -> User:
        """Create a new isolated user account."""
        now = int(time.time() * 1000)
        uid = user_id or f"usr_{secrets.token_hex(8)}"
        with self._get_connection() as conn:
            conn.execute("""
                INSERT INTO users (id, username, created_at, is_active)
                VALUES (?, ?, ?, 1)
            """, (uid, username, now))
            conn.commit()
        return User(id=uid, username=username, created_at=now, is_active=True)

    def get_user(self, user_id: str) -> Optional[User]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT * FROM users WHERE id = ?", (user_id,))
            row = cur.fetchone()
            if row:
                return User(
                    id=row["id"],
                    username=row["username"],
                    created_at=row["created_at"],
                    is_active=bool(row["is_active"]),
                )
        return None

    def get_user_by_username(self, username: str) -> Optional[User]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT * FROM users WHERE username = ?", (username,))
            row = cur.fetchone()
            if row:
                return User(
                    id=row["id"],
                    username=row["username"],
                    created_at=row["created_at"],
                    is_active=bool(row["is_active"]),
                )
        return None

    def list_users(self) -> List[User]:
        results = []
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT * FROM users ORDER BY created_at ASC")
            for row in cur.fetchall():
                results.append(User(
                    id=row["id"],
                    username=row["username"],
                    created_at=row["created_at"],
                    is_active=bool(row["is_active"]),
                ))
        return results

    @staticmethod
    def _hash_key(raw_key: str) -> str:
        return hashlib.sha256(raw_key.encode("utf-8")).hexdigest()

    def create_api_key(self, user_id: str, name: str) -> Tuple[str, ApiKey]:
        """
        Generates a secure API key for the user.
        Returns: (raw_secret_key, ApiKeyModel)
        The raw key is shown only once and cannot be recovered from hash.
        """
        raw_key = f"sk_live_{secrets.token_urlsafe(32)}"
        key_hash = self._hash_key(raw_key)
        key_prefix = raw_key[:12] + "..."
        now = int(time.time() * 1000)

        with self._get_connection() as conn:
            conn.execute("""
                INSERT INTO api_keys (key_hash, key_prefix, user_id, name, created_at, is_revoked)
                VALUES (?, ?, ?, ?, ?, 0)
            """, (key_hash, key_prefix, user_id, name, now))
            conn.commit()

        api_key = ApiKey(
            key_hash=key_hash,
            key_prefix=key_prefix,
            user_id=user_id,
            name=name,
            created_at=now,
            is_revoked=False
        )
        return raw_key, api_key

    def verify_api_key(self, raw_key: str) -> Optional[User]:
        """Validates key, updates last_used_at, and returns the User."""
        if not raw_key:
            return None
        key_hash = self._hash_key(raw_key)
        now = int(time.time() * 1000)

        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT k.user_id, u.username, u.is_active, k.is_revoked
                FROM api_keys k
                JOIN users u ON k.user_id = u.id
                WHERE k.key_hash = ?
            """, (key_hash,))
            row = cur.fetchone()

            if not row:
                return None
            if row["is_revoked"] or not row["is_active"]:
                return None

            # Update last used
            conn.execute("UPDATE api_keys SET last_used_at = ? WHERE key_hash = ?", (now, key_hash))
            conn.commit()

            return User(id=row["user_id"], username=row["username"], created_at=0, is_active=True)

    def revoke_api_key(self, key_hash_or_prefix: str, user_id: Optional[str] = None) -> bool:
        """Revokes an active API key."""
        with self._get_connection() as conn:
            query = "UPDATE api_keys SET is_revoked = 1 WHERE (key_hash = ? OR key_prefix LIKE ?)"
            params = [key_hash_or_prefix, f"{key_hash_or_prefix}%"]
            if user_id:
                query += " AND user_id = ?"
                params.append(user_id)
            cursor = conn.execute(query, params)
            conn.commit()
            return cursor.rowcount > 0

    def list_api_keys(self, user_id: str) -> List[ApiKey]:
        results = []
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT * FROM api_keys WHERE user_id = ? ORDER BY created_at DESC", (user_id,))
            for r in cur.fetchall():
                results.append(ApiKey(
                    key_hash=r["key_hash"],
                    key_prefix=r["key_prefix"],
                    user_id=r["user_id"],
                    name=r["name"],
                    created_at=r["created_at"],
                    last_used_at=r["last_used_at"],
                    is_revoked=bool(r["is_revoked"]),
                ))
        return results

    # ---------------------------------------------------------
    # Bank Data Operations
    # ---------------------------------------------------------

    def save_accounts(self, accounts: List[BankAccountInfo], user_id: str = "default_user") -> int:
        now = int(time.time() * 1000)
        saved = 0
        with self._get_connection() as conn:
            cursor = conn.cursor()
            for acc in accounts:
                target_user = acc.user_id if acc.user_id != "default_user" else user_id
                cursor.execute("""
                    INSERT OR REPLACE INTO bank_accounts (
                        id, user_id, bank_name, name, account_type, balance, currency,
                        card_last4, account_last4, is_blocked, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    acc.id,
                    target_user,
                    acc.raw_details.get("bank_name", "ВТБ"),
                    acc.name,
                    acc.account_type.value,
                    acc.balance,
                    acc.currency,
                    acc.card_last4,
                    acc.account_last4,
                    1 if acc.is_blocked else 0,
                    now
                ))
                saved += 1
            conn.commit()
        return saved

    def get_accounts(self, user_id: str = "default_user") -> List[BankAccountInfo]:
        results = []
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT * FROM bank_accounts
                WHERE user_id = ?
                ORDER BY name ASC
            """, (user_id,))
            for r in cur.fetchall():
                results.append(BankAccountInfo(
                    id=r["id"],
                    user_id=r["user_id"],
                    name=r["name"],
                    account_type=AccountType(r["account_type"]),
                    balance=r["balance"],
                    currency=r["currency"],
                    card_last4=r["card_last4"],
                    account_last4=r["account_last4"],
                    is_blocked=bool(r["is_blocked"]),
                    raw_details={"bank_name": r["bank_name"]}
                ))
        return results

    def save_raw_transactions(self, raw_txs: List[RawBankTransaction], user_id: str = "default_user") -> int:
        now = int(time.time() * 1000)
        saved = 0
        with self._get_connection() as conn:
            cursor = conn.cursor()
            for tx in raw_txs:
                target_user = tx.user_id if tx.user_id != "default_user" else user_id
                cursor.execute("""
                    INSERT OR REPLACE INTO raw_transactions (
                        raw_id, user_id, bank_name, raw_title, raw_amount, raw_currency,
                        raw_date, raw_category, raw_status, account_tail, card_tail,
                        payload_json, captured_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    tx.raw_id,
                    target_user,
                    tx.bank_name,
                    tx.raw_title,
                    tx.raw_amount,
                    tx.raw_currency,
                    tx.raw_date,
                    tx.raw_category,
                    tx.raw_status,
                    tx.account_tail,
                    tx.card_tail,
                    json.dumps(tx.raw_payload, ensure_ascii=False),
                    now
                ))
                saved += 1
            conn.commit()
        return saved

    def save_normalized_transactions(
        self, txs: List[NormalizedTransaction], user_id: str = "default_user"
    ) -> int:
        saved = 0
        now = int(time.time() * 1000)
        with self._get_connection() as conn:
            cursor = conn.cursor()
            for tx in txs:
                target_user = tx.user_id if tx.user_id != "default_user" else user_id
                cursor.execute("""
                    INSERT OR REPLACE INTO normalized_transactions (
                        bank_id, user_id, bank_name, type, amount, currency, timestamp,
                        description, merchant, mcc, card_last4, account_last4,
                        balance_after, fee, status, category_suggestion, raw_source_id,
                        is_internal_transfer, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    tx.bank_id,
                    target_user,
                    tx.bank_name,
                    tx.type.value,
                    tx.amount,
                    tx.currency,
                    tx.timestamp,
                    tx.description,
                    tx.merchant,
                    tx.mcc,
                    tx.card_last4,
                    tx.account_last4,
                    tx.balance_after,
                    tx.fee,
                    tx.status,
                    tx.category_suggestion,
                    tx.raw_source_id,
                    1 if tx.is_internal_transfer else 0,
                    now
                ))
                saved += 1
            conn.commit()
        return saved

    def get_normalized_transactions(
        self,
        bank_name: Optional[str] = None,
        user_id: str = "default_user",
        account_last4: Optional[str] = None,
        tx_type: Optional[TransactionType] = None,
        from_timestamp: Optional[int] = None,
        to_timestamp: Optional[int] = None,
        since_timestamp: Optional[int] = None,
        is_internal_transfer: Optional[bool] = None,
        limit: int = 100,
        offset: int = 0
    ) -> List[NormalizedTransaction]:
        results = []
        query = "SELECT * FROM normalized_transactions WHERE user_id = ?"
        params: List[Any] = [user_id]

        if bank_name:
            query += " AND bank_name = ?"
            params.append(bank_name)
        if account_last4:
            query += " AND account_last4 = ?"
            params.append(account_last4)
        if tx_type:
            query += " AND type = ?"
            params.append(tx_type.value)
        if from_timestamp is not None:
            query += " AND timestamp >= ?"
            params.append(from_timestamp)
        if to_timestamp is not None:
            query += " AND timestamp <= ?"
            params.append(to_timestamp)
        if since_timestamp is not None:
            # Incremental sync cursor: operations occurred after since_timestamp
            query += " AND timestamp > ?"
            params.append(since_timestamp)
        if is_internal_transfer is not None:
            query += " AND is_internal_transfer = ?"
            params.append(1 if is_internal_transfer else 0)

        query += " ORDER BY timestamp DESC LIMIT ? OFFSET ?"
        params.extend([limit, offset])

        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(query, params)
            rows = cursor.fetchall()
            for r in rows:
                results.append(self._row_to_normalized_transaction(r))
        return results

    def count_normalized_transactions(
        self,
        user_id: str = "default_user",
        bank_name: Optional[str] = None,
        account_last4: Optional[str] = None,
        tx_type: Optional[TransactionType] = None,
        from_timestamp: Optional[int] = None,
        to_timestamp: Optional[int] = None,
        since_timestamp: Optional[int] = None,
        is_internal_transfer: Optional[bool] = None,
    ) -> int:
        query = "SELECT COUNT(*) FROM normalized_transactions WHERE user_id = ?"
        params: List[Any] = [user_id]

        if bank_name:
            query += " AND bank_name = ?"
            params.append(bank_name)
        if account_last4:
            query += " AND account_last4 = ?"
            params.append(account_last4)
        if tx_type:
            query += " AND type = ?"
            params.append(tx_type.value)
        if from_timestamp is not None:
            query += " AND timestamp >= ?"
            params.append(from_timestamp)
        if to_timestamp is not None:
            query += " AND timestamp <= ?"
            params.append(to_timestamp)
        if since_timestamp is not None:
            query += " AND timestamp > ?"
            params.append(since_timestamp)
        if is_internal_transfer is not None:
            query += " AND is_internal_transfer = ?"
            params.append(1 if is_internal_transfer else 0)

        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute(query, params)
            return cur.fetchone()[0]

    def get_transaction_by_id(
        self, bank_id: str, user_id: str = "default_user"
    ) -> Optional[NormalizedTransaction]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT * FROM normalized_transactions
                WHERE user_id = ? AND bank_id = ?
            """, (user_id, bank_id))
            row = cur.fetchone()
            if row:
                return self._row_to_normalized_transaction(row)
        return None

    def _row_to_normalized_transaction(self, r: sqlite3.Row) -> NormalizedTransaction:
        return NormalizedTransaction(
            bank_id=r["bank_id"],
            user_id=r["user_id"],
            bank_name=r["bank_name"],
            type=TransactionType(r["type"]),
            amount=r["amount"],
            currency=r["currency"],
            timestamp=r["timestamp"],
            description=r["description"],
            merchant=r["merchant"],
            mcc=r["mcc"],
            card_last4=r["card_last4"],
            account_last4=r["account_last4"],
            balance_after=r["balance_after"],
            fee=r["fee"],
            status=r["status"],
            category_suggestion=r["category_suggestion"],
            raw_source_id=r["raw_source_id"],
            is_internal_transfer=bool(r["is_internal_transfer"]) if "is_internal_transfer" in r.keys() else False,
        )

    def export_to_json(self, output_path: Path, user_id: str = "default_user") -> None:
        """
        Exports normalized transactions to a JSON format for Mani-Mani import.
        """
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        txs = self.get_normalized_transactions(user_id=user_id, limit=100000)
        export_data = {
            "version": 1,
            "source": "Mani-Mani Sync Lab Server",
            "user_id": user_id,
            "exported_at": int(time.time() * 1000),
            "transactions_count": len(txs),
            "transactions": [tx.to_mani_mani_dict() for tx in txs]
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(export_data, f, ensure_ascii=False, indent=2)
