"""
Domain Models for Mani-Mani Sync Lab.

Strictly aligned with Mani-Mani Android entities:
- AccountEntity
- TransactionEntity
- PendingNotificationEntity
- CurrencyRates
"""

from enum import Enum
from typing import Dict, Any, Optional, List, TypeVar, Generic
from pydantic import BaseModel, Field


class TransactionType(str, Enum):
    """Matches com.easybudget.core.model.TransactionType in Android."""
    EXPENSE = "EXPENSE"
    INCOME = "INCOME"
    TRANSFER = "TRANSFER"


class AccountType(str, Enum):
    """Matches com.easybudget.core.model.AccountType in Android."""
    DEBIT = "DEBIT"
    CREDIT = "CREDIT"
    DEPOSIT = "DEPOSIT"
    INVESTMENT = "INVESTMENT"
    CASH = "CASH"
    CRYPTO = "CRYPTO"


class User(BaseModel):
    """Multi-tenant user representation."""
    id: str
    username: str
    created_at: int
    is_active: bool = True


class ApiKey(BaseModel):
    """Metadata of an active or revoked API key."""
    key_hash: str
    key_prefix: str
    user_id: str
    name: str
    created_at: int
    last_used_at: Optional[int] = None
    is_revoked: bool = False


class BankAccountInfo(BaseModel):
    """
    Bank account or card representation extracted from bank interface.
    Matches AccountEntity concept in Mani-Mani.
    """
    id: str  # Bank-internal ID or account tail
    user_id: str = "default_user"
    name: str  # e.g., "Карта для жизни", "Накопительный счет"
    account_type: AccountType = AccountType.DEBIT
    balance: float
    currency: str = "RUB"
    card_last4: Optional[str] = None
    account_last4: Optional[str] = None
    account_product_id: Optional[str] = None  # Stable bank product ID (accountProductId) for Mani-Mani matching
    is_blocked: bool = False
    raw_details: Dict[str, Any] = Field(default_factory=dict)


class RawBankTransaction(BaseModel):
    """
    Unprocessed raw transaction received from bank web requests or DOM.
    Contains raw strings directly from the bank's response.
    """
    raw_id: str
    user_id: str = "default_user"
    bank_name: str
    raw_title: str
    raw_amount: str
    raw_currency: Optional[str] = "RUB"
    raw_date: str
    raw_category: Optional[str] = None
    raw_status: Optional[str] = None
    account_tail: Optional[str] = None
    card_tail: Optional[str] = None
    raw_payload: Dict[str, Any] = Field(default_factory=dict)


class NormalizedTransaction(BaseModel):
    """
    Clean, normalized transaction ready to be saved into Mani-Mani SQLite/Room database
    or retrieved by the Android client via Server API.
    Strictly conforms to Mani-Mani TransactionEntity with additional multi-tenant fields.
    """
    bank_id: str  # Unique bank transaction ID for deduplication
    user_id: str = "default_user"
    bank_name: str = "ВТБ"
    type: TransactionType
    amount: float  # Strictly positive (> 0.0) as in Mani-Mani
    currency: str = "RUB"
    timestamp: int  # Epoch milliseconds (System.currentTimeMillis() in Android)
    description: str  # Operation title, note, or purpose
    merchant: Optional[str] = None  # Merchant name for category suggestion
    mcc: Optional[int] = None  # MCC code if provided by bank
    card_last4: Optional[str] = None
    account_last4: Optional[str] = None
    account_product_id: Optional[str] = None  # Stable bank product ID (accountProductId)
    balance_after: Optional[float] = None  # Account balance after operation
    fee: float = 0.0  # Operation fee if any
    status: str = "CONFIRMED"  # CONFIRMED, HOLD, FAILED
    category_suggestion: Optional[str] = None
    raw_source_id: Optional[str] = None
    is_internal_transfer: bool = False  # Distinguishes Me2Me (own accounts) from peer-to-peer transfers
    created_at: Optional[int] = None  # Server ingestion timestamp in ms
    updated_at: Optional[int] = None  # Server ingestion or modification timestamp in ms

    def to_mani_mani_dict(self) -> Dict[str, Any]:
        """Convert to format suitable for Mani-Mani JSON backup / import."""
        return {
            "type": self.type.value,
            "amount": self.amount,
            "currency": self.currency,
            "timestamp": self.timestamp,
            "note": self.description,
            "tag": self.bank_name,
            "excludeFromStats": False,
            "merchant": self.merchant,
            "cardLast4": self.card_last4,
            "accountLast4": self.account_last4,
            "accountProductId": self.account_product_id,
            "balanceAfter": self.balance_after,
            "fee": self.fee,
            "status": self.status,
            "externalId": self.bank_id,
            "isInternalTransfer": self.is_internal_transfer,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
        }


T = TypeVar("T")


class PaginatedResponse(BaseModel, Generic[T]):
    items: List[T]
    total: int
    limit: int
    offset: int
    has_more: bool


class SyncSession(BaseModel):
    """Tracks state and results of a bank sync exploration session."""
    session_id: str
    user_id: str = "default_user"
    bank_name: str
    started_at: int
    ended_at: Optional[int] = None
    is_authenticated: bool = False
    accounts_found: int = 0
    raw_transactions_captured: int = 0
    normalized_transactions_count: int = 0
    errors: List[str] = Field(default_factory=list)
