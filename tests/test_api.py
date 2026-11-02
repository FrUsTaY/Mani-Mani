"""
Unit and integration tests for FastAPI REST API endpoints.
Tests authentication, authorization, multi-tenant isolation, pagination, and filtering.
"""

import pytest
from fastapi.testclient import TestClient

from sync_lab.server.app import create_app
from sync_lab.server.dependencies import get_repository
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository
from sync_lab.core.models import (
    BankAccountInfo,
    AccountType,
    NormalizedTransaction,
    TransactionType
)


@pytest.fixture
def repo(tmp_path):
    db_file = tmp_path / "test_api_sync.db"
    return SqliteTransactionRepository(db_file)


@pytest.fixture
def test_setup(repo):
    # Create two users
    u1 = repo.create_user(username="alice")
    u2 = repo.create_user(username="bob")

    # Create API keys
    raw_key_alice, key_alice_record = repo.create_api_key(u1.id, name="Alice Phone")
    raw_key_bob, key_bob_record = repo.create_api_key(u2.id, name="Bob Phone")

    # Insert Alice's accounts
    acc_a1 = BankAccountInfo(
        id="acc_alice_1",
        name="Alice Debit",
        account_type=AccountType.DEBIT,
        balance=15000.0,
        currency="RUB",
        card_last4="1111",
        user_id=u1.id
    )
    repo.save_accounts([acc_a1], user_id=u1.id)

    # Insert Bob's accounts
    acc_b1 = BankAccountInfo(
        id="acc_bob_1",
        name="Bob Savings",
        account_type=AccountType.DEPOSIT,
        balance=50000.0,
        currency="RUB",
        card_last4="2222",
        user_id=u2.id
    )
    repo.save_accounts([acc_b1], user_id=u2.id)

    # Insert Alice's transactions
    tx_a1 = NormalizedTransaction(
        bank_id="tx_a_101",
        bank_name="ВТБ",
        type=TransactionType.EXPENSE,
        amount=1500.0,
        currency="RUB",
        timestamp=1772500000000,
        description="Grocery Store",
        merchant="Supermarket",
        card_last4="1111",
        user_id=u1.id,
        is_internal_transfer=False
    )
    tx_a2 = NormalizedTransaction(
        bank_id="tx_a_102",
        bank_name="ВТБ",
        type=TransactionType.TRANSFER,
        amount=5000.0,
        currency="RUB",
        timestamp=1772600000000,
        description="Между своими счетами",
        merchant=None,
        card_last4="1111",
        user_id=u1.id,
        is_internal_transfer=True
    )
    tx_a3 = NormalizedTransaction(
        bank_id="tx_a_103",
        bank_name="ВТБ",
        type=TransactionType.INCOME,
        amount=30000.0,
        currency="RUB",
        timestamp=1772700000000,
        description="Salary",
        merchant="Company",
        card_last4="1111",
        user_id=u1.id,
        is_internal_transfer=False
    )
    repo.save_normalized_transactions([tx_a1, tx_a2, tx_a3], user_id=u1.id)

    # Insert Bob's transactions
    tx_b1 = NormalizedTransaction(
        bank_id="tx_b_201",
        bank_name="ВТБ",
        type=TransactionType.EXPENSE,
        amount=999.0,
        currency="RUB",
        timestamp=1772550000000,
        description="Bob Dinner",
        merchant="Restaurant",
        card_last4="2222",
        user_id=u2.id,
        is_internal_transfer=False
    )
    repo.save_normalized_transactions([tx_b1], user_id=u2.id)

    return {
        "repo": repo,
        "alice": u1,
        "bob": u2,
        "key_alice": raw_key_alice,
        "key_alice_prefix": key_alice_record.key_prefix,
        "key_bob": raw_key_bob,
        "key_bob_prefix": key_bob_record.key_prefix,
    }


@pytest.fixture
def client(repo):
    app = create_app()
    app.dependency_overrides[get_repository] = lambda: repo
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def test_health_check_public(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["database"] == "connected"
    assert "uptime_seconds" in data

    resp_v1 = client.get("/api/v1/health")
    assert resp_v1.status_code == 200


def test_unauthorized_access(client):
    # Missing API key
    res = client.get("/api/v1/accounts")
    assert res.status_code == 401

    # Invalid API key
    res = client.get("/api/v1/accounts", headers={"X-API-Key": "invalid_key_12345"})
    assert res.status_code == 401


def test_auth_headers_x_api_key_and_bearer(client, test_setup):
    alice_key = test_setup["key_alice"]

    # Test via X-API-Key header
    res1 = client.get("/api/v1/accounts", headers={"X-API-Key": alice_key})
    assert res1.status_code == 200

    # Test via Authorization: Bearer <key> header
    res2 = client.get("/api/v1/accounts", headers={"Authorization": f"Bearer {alice_key}"})
    assert res2.status_code == 200
    assert res1.json() == res2.json()


def test_multi_tenant_accounts_isolation(client, test_setup):
    alice_key = test_setup["key_alice"]
    bob_key = test_setup["key_bob"]

    res_alice = client.get("/api/v1/accounts", headers={"X-API-Key": alice_key})
    assert res_alice.status_code == 200
    data_alice = res_alice.json()
    assert data_alice["total"] == 1
    assert len(data_alice["items"]) == 1
    assert data_alice["items"][0]["id"] == "acc_alice_1"

    res_bob = client.get("/api/v1/accounts", headers={"X-API-Key": bob_key})
    assert res_bob.status_code == 200
    data_bob = res_bob.json()
    assert data_bob["total"] == 1
    assert len(data_bob["items"]) == 1
    assert data_bob["items"][0]["id"] == "acc_bob_1"


def test_multi_tenant_transactions_isolation(client, test_setup):
    alice_key = test_setup["key_alice"]
    bob_key = test_setup["key_bob"]

    res_alice = client.get("/api/v1/transactions", headers={"X-API-Key": alice_key})
    assert res_alice.status_code == 200
    data_a = res_alice.json()
    assert data_a["total"] == 3
    alice_bank_ids = {t["bank_id"] for t in data_a["items"]}
    assert alice_bank_ids == {"tx_a_101", "tx_a_102", "tx_a_103"}

    res_bob = client.get("/api/v1/transactions", headers={"X-API-Key": bob_key})
    assert res_bob.status_code == 200
    data_b = res_bob.json()
    assert data_b["total"] == 1
    assert data_b["items"][0]["bank_id"] == "tx_b_201"


def test_transactions_pagination(client, test_setup):
    alice_key = test_setup["key_alice"]

    # First page: limit 2, offset 0
    res_p1 = client.get("/api/v1/transactions?limit=2&offset=0", headers={"X-API-Key": alice_key})
    assert res_p1.status_code == 200
    data_p1 = res_p1.json()
    assert data_p1["total"] == 3
    assert len(data_p1["items"]) == 2
    assert data_p1["has_more"] is True
    assert data_p1["limit"] == 2
    assert data_p1["offset"] == 0

    # Second page: limit 2, offset 2
    res_p2 = client.get("/api/v1/transactions?limit=2&offset=2", headers={"X-API-Key": alice_key})
    assert res_p2.status_code == 200
    data_p2 = res_p2.json()
    assert data_p2["total"] == 3
    assert len(data_p2["items"]) == 1
    assert data_p2["has_more"] is False


def test_transactions_filters(client, test_setup):
    alice_key = test_setup["key_alice"]

    # Filter by type: EXPENSE
    res_exp = client.get("/api/v1/transactions?type=EXPENSE", headers={"X-API-Key": alice_key})
    assert res_exp.status_code == 200
    data = res_exp.json()
    assert data["total"] == 1
    assert data["items"][0]["bank_id"] == "tx_a_101"

    # Filter by is_internal_transfer: true
    res_transfer = client.get("/api/v1/transactions?is_internal_transfer=true", headers={"X-API-Key": alice_key})
    assert res_transfer.status_code == 200
    data = res_transfer.json()
    assert data["total"] == 1
    assert data["items"][0]["bank_id"] == "tx_a_102"
    assert data["items"][0]["is_internal_transfer"] is True

    # Filter by since_timestamp (incremental sync)
    res_since = client.get("/api/v1/transactions?since_timestamp=1772600000000", headers={"X-API-Key": alice_key})
    assert res_since.status_code == 200
    data = res_since.json()
    # Timestamp > 1772600000000 -> only tx_a_103 (1772700000000)
    assert data["total"] == 1
    assert data["items"][0]["bank_id"] == "tx_a_103"


def test_get_single_transaction_and_isolation(client, test_setup):
    alice_key = test_setup["key_alice"]
    bob_key = test_setup["key_bob"]

    # Alice can get her own transaction
    res = client.get("/api/v1/transactions/tx_a_101", headers={"X-API-Key": alice_key})
    assert res.status_code == 200
    assert res.json()["bank_id"] == "tx_a_101"

    # Bob cannot access Alice's transaction -> returns 404 (not 403, for security obfuscation)
    res_cross = client.get("/api/v1/transactions/tx_a_101", headers={"X-API-Key": bob_key})
    assert res_cross.status_code == 404

    # Non-existent transaction returns 404
    res_none = client.get("/api/v1/transactions/non_existent_id", headers={"X-API-Key": alice_key})
    assert res_none.status_code == 404


def test_get_single_account_and_isolation(client, test_setup):
    alice_key = test_setup["key_alice"]
    bob_key = test_setup["key_bob"]

    # Alice gets her account
    res = client.get("/api/v1/accounts/acc_alice_1", headers={"X-API-Key": alice_key})
    assert res.status_code == 200
    assert res.json()["id"] == "acc_alice_1"

    # Bob cannot access Alice's account -> 404
    res_cross = client.get("/api/v1/accounts/acc_alice_1", headers={"X-API-Key": bob_key})
    assert res_cross.status_code == 404


def test_api_key_revocation(client, test_setup):
    alice_key = test_setup["key_alice"]
    alice_key_prefix = test_setup["key_alice_prefix"]
    repo = test_setup["repo"]

    # Works before revocation
    res = client.get("/api/v1/accounts", headers={"X-API-Key": alice_key})
    assert res.status_code == 200

    # Revoke key
    revoked = repo.revoke_api_key(alice_key_prefix)
    assert revoked is True

    # After revocation -> 401
    res_revoked = client.get("/api/v1/accounts", headers={"X-API-Key": alice_key})
    assert res_revoked.status_code == 401
