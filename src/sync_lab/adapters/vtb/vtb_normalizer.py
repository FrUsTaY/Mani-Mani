"""
VTB specific transaction normalizer.

Extends the core BaseTransactionNormalizer with bank-specific rules for VTB Online:
- Parses official VTB API structures (e.g. operationAmount.sum, parentCategory.name)
- Correctly uses the `debet` boolean flag (True = EXPENSE, False = INCOME)
- Identifies internal transfers ("Между своими счетами") -> TRANSFER
- Disentangles commission fees (feeAmount) and balances from the transaction amount.

Reusability Guide:
1. Reusable / Android mapping:
   - Normalization logic directly translates into Kotlin parsing rules in Mani-Mani.
"""

from typing import Optional
from sync_lab.core.normalizer import BaseTransactionNormalizer
from sync_lab.core.models import (
    RawBankTransaction,
    NormalizedTransaction,
    TransactionType,
)


class VtbTransactionNormalizer(BaseTransactionNormalizer):
    """
    Normalizes VTB operations into Mani-Mani entities.
    """

    def normalize_transaction(
        self, raw: RawBankTransaction
    ) -> Optional[NormalizedTransaction]:
        raw_data = raw.raw_payload.get("raw_data", {})

        # 1. Amount and currency extraction
        amount = 0.0
        sign = None
        currency = raw.raw_currency or "RUB"

        # Check raw_data structured objects first (highest precision)
        if isinstance(raw_data, dict):
            amt_obj = (
                raw_data.get("operationAmount") or
                raw_data.get("transactionAmount") or
                raw_data.get("amount") or
                raw_data.get("operationSum") or
                raw_data.get("sum")
            )
            if isinstance(amt_obj, dict):
                raw_num = (
                    amt_obj.get("sum") if amt_obj.get("sum") is not None else
                    amt_obj.get("value") if amt_obj.get("value") is not None else
                    amt_obj.get("amount")
                )
                if raw_num is not None:
                    try:
                        amount = abs(float(raw_num))
                    except (ValueError, TypeError):
                        pass
                currency = str(amt_obj.get("currency") or amt_obj.get("currencyCode") or currency)
            elif amt_obj is not None:
                parsed_amt, parsed_sign = self.parse_amount(str(amt_obj))
                amount = parsed_amt
                sign = parsed_sign

        # Fallback to raw_amount string if amount is not yet resolved
        if amount <= 0.0 and raw.raw_amount:
            amount, sign = self.parse_amount(raw.raw_amount)

        if amount <= 0.0:
            return None

        # 2. Date and Timestamp
        raw_date_str = raw.raw_date
        if isinstance(raw_data, dict) and not raw_date_str:
            raw_date_str = str(
                raw_data.get("transactionDate") or
                raw_data.get("operationDate") or
                raw_data.get("processedDate") or
                raw_data.get("date") or
                raw_data.get("timestamp") or
                ""
            )
        timestamp = self.parse_timestamp(raw_date_str)

        # 3. Category & Description
        category = raw.raw_category
        if isinstance(raw_data, dict):
            pcat = raw_data.get("parentCategory")
            if isinstance(pcat, dict) and pcat.get("name"):
                category = str(pcat.get("name"))
            elif not category:
                cat_obj = raw_data.get("category")
                if isinstance(cat_obj, dict):
                    category = cat_obj.get("name") or cat_obj.get("title")
                elif isinstance(cat_obj, str):
                    category = cat_obj

        title = raw.raw_title
        if (not title or title == "Банковская операция") and isinstance(raw_data, dict):
            title = str(
                raw_data.get("operationName") or
                raw_data.get("fullOperationName") or
                raw_data.get("description") or
                raw_data.get("title") or
                raw_data.get("merchantName") or
                raw_data.get("details") or
                "Операция ВТБ"
            )

        # 4. Transaction Type Detection
        # In VTB API:
        # - debet: True indicates funds deducted from account (EXPENSE / outgoing TRANSFER)
        # - debet: False indicates funds received (INCOME / incoming TRANSFER)
        op_type = str(
            raw_data.get("operationType") or
            raw_data.get("type") or
            ""
        ).strip()
        debet = raw_data.get("debet") if isinstance(raw_data, dict) else None

        is_internal = "между своими счетами" in op_type.lower() or (bool(category) and "между счетами" in category.lower())
        if is_internal:
            tx_type = TransactionType.TRANSFER
        elif debet is True:
            tx_type = TransactionType.EXPENSE
        elif debet is False:
            tx_type = TransactionType.INCOME
        else:
            tx_type = self.determine_type(title, sign, category)

        # 5. Accounts and Cards
        card_last4 = raw.card_tail
        account_last4 = raw.account_tail

        if isinstance(raw_data, dict):
            acc_val = str(raw_data.get("account") or "")
            if acc_val:
                # Extract 4 digits if present (e.g. *6708 -> 6708)
                import re
                digits = re.findall(r"\d{4}", acc_val)
                if digits:
                    account_last4 = digits[-1]
                else:
                    account_last4 = acc_val

            # Check transaction system cards
            txs_list = raw_data.get("transactions")
            if isinstance(txs_list, list) and not card_last4:
                for tx_item in txs_list:
                    if isinstance(tx_item, dict):
                        tid = str(tx_item.get("transactionId") or "")
                        if "*" in tid:
                            card_digits = re.findall(r"\d{4}", tid)
                            if card_digits:
                                card_last4 = card_digits[-1]
                                break

        # 6. Merchant and MCC
        mcc = None
        merchant_name = category or title
        if isinstance(raw_data, dict):
            merchant_obj = raw_data.get("merchant")
            if isinstance(merchant_obj, dict):
                mcc_val = merchant_obj.get("mcc")
                if mcc_val and str(mcc_val).isdigit():
                    mcc = int(mcc_val)
                if merchant_obj.get("originalName") or merchant_obj.get("title"):
                    merchant_name = str(merchant_obj.get("originalName") or merchant_obj.get("title"))

        # 7. Fee / Commission
        fee = 0.0
        if isinstance(raw_data, dict):
            fee_obj = raw_data.get("feeAmount") or raw_data.get("fee") or raw_data.get("commission")
            if isinstance(fee_obj, dict):
                fee_val = fee_obj.get("sum") if fee_obj.get("sum") is not None else fee_obj.get("value")
                if fee_val is not None:
                    try:
                        fee = abs(float(fee_val))
                    except (ValueError, TypeError):
                        pass
            elif fee_obj is not None:
                fee_val, _ = self.parse_amount(str(fee_obj))
                fee = fee_val

        # 8. Status
        status = "CONFIRMED"
        if isinstance(raw_data, dict):
            if raw_data.get("isHold") is True:
                status = "HOLD"
            else:
                raw_st = str(raw_data.get("status") or "").lower()
                if "hold" in raw_st or "обработк" in raw_st or "pending" in raw_st:
                    status = "HOLD"
                elif "отклон" in raw_st or "declin" in raw_st or "cancel" in raw_st:
                    status = "FAILED"

        # 9. Stable Unique Bank Transaction ID
        # VTB's `operationId` is an ephemeral session/request UUID generated on each API call.
        # The true persistent transaction identifier in VTB's core banking is `internalId` (or `rrn`).
        # For transfers between own accounts (TRANSFER), the same internalId connects two distinct entries:
        # 1) Debit on source account (debet=True)
        # 2) Credit on destination account (debet=False)
        # Therefore, unique entry key is: f"vtb_{internalId}_{account_identifier}_{direction}"
        internal_id = None
        if isinstance(raw_data, dict):
            internal_id = raw_data.get("internalId") or raw_data.get("rrn")

        if internal_id:
            direction_code = "dr" if debet is True else ("cr" if debet is False else "tx")
            acc_code = account_last4 or (str(raw_data.get("accountProductId"))[-8:] if isinstance(raw_data, dict) and raw_data.get("accountProductId") else "acc")
            stable_bank_id = f"vtb_{internal_id}_{acc_code}_{direction_code}"
        else:
            stable_bank_id = raw.raw_id

        return NormalizedTransaction(
            bank_id=stable_bank_id,
            user_id=raw.user_id,
            bank_name="ВТБ",
            type=tx_type,
            amount=amount,
            currency=currency,
            timestamp=timestamp,
            description=title.strip(),
            merchant=merchant_name.strip(),
            mcc=mcc,
            card_last4=card_last4,
            account_last4=account_last4,
            balance_after=None,
            fee=fee,
            status=status,
            category_suggestion=category,
            raw_source_id=raw.raw_id,
            is_internal_transfer=is_internal
        )
