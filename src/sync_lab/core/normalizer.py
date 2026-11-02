"""
Core Transaction Normalizer for Mani-Mani Sync Lab.

Handles amount parsing, date conversions, transaction type detection,
and validation against Mani-Mani business rules.

Reusability Guide:
1. Reusable / 1:1 mapping:
   - Regex patterns and amount parsing logic are directly portable to Kotlin.
2. Android adaptation needed:
   - Kotlin string/date parsing helper (similar to Helpers.kt / BankNotificationParser.kt).
"""

import re
from datetime import datetime, timezone
from typing import Optional, Tuple
from dateutil import parser as date_parser
from sync_lab.core.interfaces import BaseNormalizer
from sync_lab.core.models import (
    RawBankTransaction,
    NormalizedTransaction,
    TransactionType,
)


class BaseTransactionNormalizer(BaseNormalizer):
    """
    Standard normalizer applying Mani-Mani rules to raw bank transactions.
    """

    CLEAN_AMOUNT_REGEX = re.compile(r"[-+]?\s*([0-9\s\xa0]+(?:[.,][0-9]{1,2})?)")
    CARD_REGEX = re.compile(r"(?:карт[аеы]|card|\*)\s*(\d{4})", re.IGNORECASE)
    ACCOUNT_REGEX = re.compile(r"(?:счет[аеу]?|счёт[аеу]?|acc|account)\s*(\d{4})", re.IGNORECASE)

    TRANSFER_KEYWORDS = [
        "перевод", "между своими", "сбп", "перечисление", "me2me",
        "пополнение карты с карты", "перевод на карту"
    ]

    INCOME_KEYWORDS = [
        "зачисление", "зарплата", "аванс", "пополнение", "возврат покупки",
        "кешбэк", "кэшбэк", "проценты на остаток", "выплата процентов"
    ]

    def parse_amount(self, raw_str: str) -> Tuple[float, Optional[str]]:
        """
        Extract numeric amount and optional sign/currency.
        Returns: (abs_amount, sign_or_none)
        Examples:
          "- 1 250,50 ₽" -> (1250.50, "-")
          "+5 000 руб." -> (5000.0, "+")
          "349.99" -> (349.99, None)
        """
        if not raw_str:
            return 0.0, None

        cleaned = raw_str.replace("\xa0", " ").strip()
        sign = "-" if "-" in cleaned else ("+" if "+" in cleaned else None)

        # Remove everything except digits, dots and commas
        digits_part = re.sub(r"[^\d.,]", "", cleaned)
        if not digits_part:
            return 0.0, sign

        # Handle decimal separator
        if "," in digits_part and "." in digits_part:
            # e.g., 1,250.50 or 1.250,50
            if digits_part.rfind(",") > digits_part.rfind("."):
                digits_part = digits_part.replace(".", "").replace(",", ".")
            else:
                digits_part = digits_part.replace(",", "")
        elif "," in digits_part:
            digits_part = digits_part.replace(",", ".")

        try:
            val = abs(float(digits_part))
            return val, sign
        except ValueError:
            return 0.0, sign

    def parse_timestamp(self, raw_date_str: str) -> int:
        """
        Convert bank date string into Unix epoch milliseconds.
        Handles ISO strings, timestamps, and localized Russian date formats.
        """
        if not raw_date_str:
            return int(datetime.now(timezone.utc).timestamp() * 1000)

        # Check if already numeric epoch (ms or s)
        if raw_date_str.isdigit():
            val = int(raw_date_str)
            if val < 10_000_000_000:  # Seconds
                return val * 1000
            return val  # Milliseconds

        # Localized Russian month names
        months_ru = {
            "января": "Jan", "янв": "Jan",
            "февраля": "Feb", "фев": "Feb",
            "марта": "Mar", "мар": "Mar",
            "апреля": "Apr", "апр": "Apr",
            "мая": "May", "май": "May",
            "июня": "Jun", "июн": "Jun",
            "июля": "Jul", "июл": "Jul",
            "августа": "Aug", "авг": "Aug",
            "сентября": "Sep", "сен": "Sep",
            "октября": "Oct", "окт": "Oct",
            "ноября": "Nov", "ноя": "Nov",
            "декабря": "Dec", "дек": "Dec"
        }

        date_clean = raw_date_str.strip().lower()
        for ru_m, en_m in months_ru.items():
            if ru_m in date_clean:
                date_clean = date_clean.replace(ru_m, en_m)
                break

        try:
            # Check if date string starts with ISO year format: YYYY-MM-DD
            # In ISO 8601, the order is strictly year-month-day (never dayfirst)
            if len(date_clean) >= 10 and date_clean[:4].isdigit() and date_clean[4] == "-":
                try:
                    dt = date_parser.isoparse(date_clean)
                except Exception:
                    dt = date_parser.parse(date_clean, dayfirst=False)
            else:
                # Local Russian format: DD.MM.YYYY or DD Month YYYY
                dt = date_parser.parse(date_clean, dayfirst=True)

            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return int(dt.timestamp() * 1000)
        except Exception:
            # Fallback to current time
            return int(datetime.now(timezone.utc).timestamp() * 1000)

    def determine_type(
        self,
        raw_title: str,
        sign: Optional[str],
        raw_category: Optional[str] = None
    ) -> TransactionType:
        """
        Classifies transaction into EXPENSE, INCOME, or TRANSFER.
        Mani-Mani requires unambiguous operation type.
        """
        text = f"{raw_title} {raw_category or ''}".lower()

        if any(kw in text for kw in self.TRANSFER_KEYWORDS):
            return TransactionType.TRANSFER

        if sign == "+":
            return TransactionType.INCOME
        elif sign == "-":
            return TransactionType.EXPENSE

        if any(kw in text for kw in self.INCOME_KEYWORDS):
            return TransactionType.INCOME

        # Default fallback for commercial operations
        return TransactionType.EXPENSE

    def normalize_transaction(
        self, raw: RawBankTransaction
    ) -> Optional[NormalizedTransaction]:
        """Normalize a raw transaction into a verified Mani-Mani transaction."""
        amount, sign = self.parse_amount(raw.raw_amount)
        if amount <= 0.0:
            return None

        tx_type = self.determine_type(raw.raw_title, sign, raw.raw_category)
        timestamp = self.parse_timestamp(raw.raw_date)

        # Card / Account extraction
        card_last4 = raw.card_tail
        if not card_last4 and raw.raw_title:
            match = self.CARD_REGEX.search(raw.raw_title)
            if match:
                card_last4 = match.group(1)

        account_last4 = raw.account_tail
        if not account_last4 and raw.raw_title:
            match = self.ACCOUNT_REGEX.search(raw.raw_title)
            if match:
                account_last4 = match.group(1)

        # Parse balance after if present in raw payload
        balance_after = None
        if "balance" in raw.raw_payload:
            bal_val, _ = self.parse_amount(str(raw.raw_payload.get("balance", "")))
            balance_after = bal_val

        # Status
        status = "CONFIRMED"
        if raw.raw_status:
            status_lower = raw.raw_status.lower()
            if "в обработке" in status_lower or "hold" in status_lower or "pending" in status_lower:
                status = "HOLD"
            elif "отклон" in status_lower or "отмен" in status_lower or "failed" in status_lower:
                status = "FAILED"

        return NormalizedTransaction(
            bank_id=raw.raw_id,
            bank_name=raw.bank_name,
            type=tx_type,
            amount=amount,
            currency=raw.raw_currency or "RUB",
            timestamp=timestamp,
            description=raw.raw_title.strip(),
            merchant=raw.raw_category or raw.raw_title.strip(),
            card_last4=card_last4,
            account_last4=account_last4,
            balance_after=balance_after,
            status=status,
            category_suggestion=raw.raw_category,
            raw_source_id=raw.raw_id
        )
