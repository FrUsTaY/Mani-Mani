"""
Unit tests for VTB-specific Normalizer.
"""

import pytest
from sync_lab.adapters.vtb.vtb_normalizer import VtbTransactionNormalizer
from sync_lab.core.models import RawBankTransaction, TransactionType


@pytest.fixture
def vtb_normalizer():
    return VtbTransactionNormalizer()


def test_vtb_real_api_structure_normalization(vtb_normalizer):
    """
    Test using exact schema intercepted from VTB history/byUser endpoint.
    """
    raw = RawBankTransaction(
        raw_id="ac3b18dc-fc0c-4d8a-80f1-c3aeb3e6c648",
        bank_name="ВТБ",
        raw_title="БубльГум_SBP",
        raw_amount="",  # Raw amount is empty initially, resolved from payload
        raw_currency="RUB",
        raw_date="2026-11-01T16:11:20.954",
        raw_payload={
            "source": "api_intercepted",
            "endpoint": "https://online.vtb.ru/msa/api-gw/private/history-hub/history-hub-homer/v1/history/byUser",
            "raw_data": {
                "operationId": "ac3b18dc-fc0c-4d8a-80f1-c3aeb3e6c648",
                "operationName": "БубльГум_SBP",
                "operationType": "Оплата по СБП",
                "debet": True,
                "operationAmount": {"currency": "RUB", "sum": 1250.50},
                "transactionAmount": {"currency": "RUB", "sum": 1250.50},
                "feeAmount": {"currency": "RUB", "sum": 0.0},
                "isHold": False,
                "status": "Processed",
                "account": "*6708",
                "accountProductId": "prod_1002345678",
                "merchant": {"mcc": "5641", "title": "БубльГум"},
                "parentCategory": {"id": 105, "name": "Детские товары"}
            }
        }
    )

    norm = vtb_normalizer.normalize_transaction(raw)
    assert norm is not None
    assert norm.amount == 1250.50
    assert norm.type == TransactionType.EXPENSE
    assert norm.currency == "RUB"
    assert norm.account_last4 == "6708"
    assert norm.account_product_id == "prod_1002345678"
    assert norm.mcc == 5641
    assert norm.fee == 0.0
    assert norm.status == "CONFIRMED"
    assert norm.category_suggestion == "Детские товары"


def test_vtb_stable_bank_id_deduplicates_repeated_api_calls(vtb_normalizer):
    """
    Simulates VTB returning the same ledger entry across two separate API calls
    with different ephemeral operationId, but identical internalId.
    """
    raw_call1 = RawBankTransaction(
        raw_id="op_call1_uuid",
        bank_name="ВТБ",
        raw_title="Аптека",
        raw_amount="",
        raw_date="2026-10-12T08:34:34.387",
        raw_payload={
            "raw_data": {
                "operationId": "op_call1_uuid",
                "internalId": "bank_internal_ledger_101",
                "account": "*6708",
                "debet": True,
                "operationAmount": {"currency": "RUB", "sum": 70.0}
            }
        }
    )

    raw_call2 = RawBankTransaction(
        raw_id="op_call2_uuid",  # Different session UUID from second API response!
        bank_name="ВТБ",
        raw_title="Аптека",
        raw_amount="",
        raw_date="2026-10-12T08:34:34.387",
        raw_payload={
            "raw_data": {
                "operationId": "op_call2_uuid",
                "internalId": "bank_internal_ledger_101",  # Same bank ledger internal ID
                "account": "*6708",
                "debet": True,
                "operationAmount": {"currency": "RUB", "sum": 70.0}
            }
        }
    )

    norm1 = vtb_normalizer.normalize_transaction(raw_call1)
    norm2 = vtb_normalizer.normalize_transaction(raw_call2)

    assert norm1 is not None and norm2 is not None
    # Both must produce the EXACT same stable bank_id for deduplication
    assert norm1.bank_id == norm2.bank_id
    assert norm1.bank_id == "vtb_bank_internal_ledger_101_6708_dr"


def test_vtb_internal_transfer_type_detection(vtb_normalizer):
    raw = RawBankTransaction(
        raw_id="transfer_op_1",
        bank_name="ВТБ",
        raw_title="Перевод",
        raw_amount="",
        raw_date="2026-11-01T15:30:25.000",
        raw_payload={
            "source": "api_intercepted",
            "raw_data": {
                "operationType": "Между своими счетами",
                "debet": True,
                "operationAmount": {"currency": "RUB", "sum": 5000.0},
                "account": "*2864",
                "parentCategory": {"name": "Между счетами"}
            }
        }
    )

    norm = vtb_normalizer.normalize_transaction(raw)
    assert norm is not None
    assert norm.amount == 5000.0
    assert norm.type == TransactionType.TRANSFER
    assert norm.account_last4 == "2864"


def test_vtb_salary_income_type_detection(vtb_normalizer):
    raw = RawBankTransaction(
        raw_id="salary_op_1",
        bank_name="ВТБ",
        raw_title="Зачисление",
        raw_amount="",
        raw_date="2026-10-25T10:00:00.000",
        raw_payload={
            "source": "api_intercepted",
            "raw_data": {
                "operationType": "Поступление заработной платы",
                "debet": False,
                "operationAmount": {"currency": "RUB", "sum": 85000.0},
                "account": "*6708",
                "parentCategory": {"name": "Зарплата"}
            }
        }
    )

    norm = vtb_normalizer.normalize_transaction(raw)
    assert norm is not None
    assert norm.amount == 85000.0
    assert norm.type == TransactionType.INCOME
    assert norm.account_last4 == "6708"


def test_vtb_hold_status_detection(vtb_normalizer):
    raw = RawBankTransaction(
        raw_id="vtb_op_1002",
        bank_name="ВТБ",
        raw_title="Кафе Ресторан Уют",
        raw_amount="- 850 ₽",
        raw_date="2026-03-02T19:00:00Z",
        raw_payload={
            "source": "api_intercepted",
            "raw_data": {
                "isHold": True,
                "operationAmount": {"currency": "RUB", "sum": 850.0}
            }
        }
    )

    norm = vtb_normalizer.normalize_transaction(raw)
    assert norm is not None
    assert norm.amount == 850.0
    assert norm.status == "HOLD"
