"""
Unit tests for SQLite repository and JSON export.
"""

import json
from pathlib import Path
import pytest
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository
from sync_lab.core.models import (
    BankAccountInfo,
    AccountType,
    RawBankTransaction,
    NormalizedTransaction,
    TransactionType
)


@pytest.fixture
def repo(tmp_path):
    db_file = tmp_path / "test_sync.db"
    return SqliteTransactionRepository(db_file)


def test_save_and_retrieve_accounts(repo):
    acc = BankAccountInfo(
        id="acc_1",
        name="Карта Для Жизни",
        account_type=AccountType.DEBIT,
        balance=45230.50,
        currency="RUB",
        card_last4="5544"
    )
    saved = repo.save_accounts([acc])
    assert saved == 1


def test_save_and_deduplicate_normalized_transactions(repo):
    tx1 = NormalizedTransaction(
        bank_id="tx_vtb_001",
        bank_name="ВТБ",
        type=TransactionType.EXPENSE,
        amount=1200.0,
        currency="RUB",
        timestamp=1772500000000,
        description="Супермаркет Перекресток",
        merchant="Перекресток",
        card_last4="1122"
    )

    # First insert
    saved1 = repo.save_normalized_transactions([tx1])
    assert saved1 == 1

    # Second insert with same bank_id (idempotent / deduplication)
    saved2 = repo.save_normalized_transactions([tx1])
    assert saved2 == 1

    txs = repo.get_normalized_transactions(bank_name="ВТБ")
    assert len(txs) == 1
    assert txs[0].bank_id == "tx_vtb_001"
    assert txs[0].amount == 1200.0


def test_export_to_json(repo, tmp_path):
    tx = NormalizedTransaction(
        bank_id="tx_vtb_002",
        bank_name="ВТБ",
        type=TransactionType.INCOME,
        amount=35000.0,
        currency="RUB",
        timestamp=1772600000000,
        description="Зарплата",
        merchant="Работодатель"
    )
    repo.save_normalized_transactions([tx])

    json_out = tmp_path / "export.json"
    repo.export_to_json(json_out)

    assert json_out.exists()
    with open(json_out, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert data["transactions_count"] == 1
    assert data["transactions"][0]["amount"] == 35000.0
    assert data["transactions"][0]["type"] == "INCOME"
    assert data["transactions"][0]["externalId"] == "tx_vtb_002"
