"""
Unit tests for core Transaction Normalizer.
"""

import pytest
from sync_lab.core.normalizer import BaseTransactionNormalizer
from sync_lab.core.models import RawBankTransaction, TransactionType


@pytest.fixture
def normalizer():
    return BaseTransactionNormalizer()


def test_parse_amount_various_formats(normalizer):
    # Russian format with comma and spaces
    amt, sign = normalizer.parse_amount("- 1 250,50 ₽")
    assert amt == 1250.50
    assert sign == "-"

    # Plus sign
    amt, sign = normalizer.parse_amount("+5 000 руб.")
    assert amt == 5000.0
    assert sign == "+"

    # Dot decimal separator
    amt, sign = normalizer.parse_amount("349.99")
    assert amt == 349.99
    assert sign is None

    # Thousand separator dot and decimal comma
    amt, sign = normalizer.parse_amount("10.500,00 ₽")
    assert amt == 10500.00


def test_parse_timestamp_localized_russian_and_iso(normalizer):
    from datetime import datetime, timezone
    
    # 1. ISO string with day=1, month=11 (1 November)
    ts_nov1 = normalizer.parse_timestamp("2026-11-01T16:11:20.954")
    dt_nov1 = datetime.fromtimestamp(ts_nov1 / 1000, tz=timezone.utc)
    assert dt_nov1.year == 2026
    assert dt_nov1.month == 11
    assert dt_nov1.day == 1

    # 2. ISO string with day=12, month=10 (12 October) - must NOT become 10 December!
    ts_oct12 = normalizer.parse_timestamp("2026-10-12T08:34:34.387")
    dt_oct12 = datetime.fromtimestamp(ts_oct12 / 1000, tz=timezone.utc)
    assert dt_oct12.year == 2026
    assert dt_oct12.month == 10
    assert dt_oct12.day == 12

    # 3. Russian dot format DD.MM.YYYY
    ts_dot = normalizer.parse_timestamp("01.11.2026")
    dt_dot = datetime.fromtimestamp(ts_dot / 1000, tz=timezone.utc)
    assert dt_dot.year == 2026
    assert dt_dot.month == 11
    assert dt_dot.day == 1

    # 4. Russian month text: 12 мая 2026, 14:30
    ts_may = normalizer.parse_timestamp("12 мая 2026, 14:30")
    dt_may = datetime.fromtimestamp(ts_may / 1000, tz=timezone.utc)
    assert dt_may.year == 2026
    assert dt_may.month == 5
    assert dt_may.day == 12


def test_determine_type(normalizer):
    # Expense
    assert normalizer.determine_type("ВкусВилл", "-", "Супермаркеты") == TransactionType.EXPENSE

    # Income
    assert normalizer.determine_type("Зачисление зарплаты", "+", "Доходы") == TransactionType.INCOME

    # Transfer
    assert normalizer.determine_type("Перевод через СБП", "-", "Переводы") == TransactionType.TRANSFER
    assert normalizer.determine_type("Перевод между своими счетами", None, None) == TransactionType.TRANSFER


def test_normalize_transaction_prevents_zero_amount(normalizer):
    raw = RawBankTransaction(
        raw_id="tx_0",
        bank_name="ВТБ",
        raw_title="Нулевая тестовая операция",
        raw_amount="0.00 ₽",
        raw_date="2026-01-01"
    )
    result = normalizer.normalize_transaction(raw)
    assert result is None  # Zero or invalid amounts should not produce Mani-Mani transactions


def test_normalize_transaction_card_and_account_extraction(normalizer):
    raw = RawBankTransaction(
        raw_id="tx_1",
        bank_name="ВТБ",
        raw_title="Покупка в Ozon по карте *4321",
        raw_amount="- 1 540 ₽",
        raw_date="2026-02-15T12:00:00Z",
        raw_category="Маркетплейсы"
    )
    norm = normalizer.normalize_transaction(raw)
    assert norm is not None
    assert norm.amount == 1540.0
    assert norm.type == TransactionType.EXPENSE
    assert norm.card_last4 == "4321"
    assert norm.description == "Покупка в Ozon по карте *4321"
