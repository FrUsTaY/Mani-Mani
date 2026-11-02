"""
Pydantic schemas for the Sync Lab HTTP API.
Strictly compatible with Mani-Mani Android entities.
"""

from typing import List, Optional
from pydantic import BaseModel, Field
from sync_lab.core.models import TransactionType, AccountType


class BankAccountResponse(BaseModel):
    id: str = Field(..., description="Unique account identifier in Sync Lab")
    bank_name: str = Field(..., description="Bank name, e.g. ВТБ")
    name: str = Field(..., description="Human-readable account title")
    account_type: AccountType = Field(..., description="Type: DEBIT, CREDIT, DEPOSIT, etc.")
    balance: float = Field(..., description="Current balance")
    currency: str = Field(default="RUB", description="ISO currency code")
    card_last4: Optional[str] = Field(None, description="Last 4 digits of linked card if available")
    account_last4: Optional[str] = Field(None, description="Last 4 digits of bank account")
    is_blocked: bool = Field(default=False, description="Whether account is blocked")


class AccountListResponse(BaseModel):
    items: List[BankAccountResponse]
    total: int


class TransactionResponse(BaseModel):
    bank_id: str = Field(..., description="Stable persistent transaction identifier")
    bank_name: str = Field(default="ВТБ")
    type: TransactionType = Field(..., description="Operation type: EXPENSE, INCOME, TRANSFER")
    amount: float = Field(..., gt=0.0, description="Strictly positive transaction amount")
    currency: str = Field(default="RUB")
    timestamp: int = Field(..., description="Epoch milliseconds (System.currentTimeMillis())")
    description: str = Field(..., description="Operation title, recipient, or purpose")
    merchant: Optional[str] = Field(None, description="Merchant title")
    mcc: Optional[int] = Field(None, description="MCC category code")
    card_last4: Optional[str] = Field(None, description="Last 4 digits of card used")
    account_last4: Optional[str] = Field(None, description="Last 4 digits of account")
    balance_after: Optional[float] = Field(None, description="Account balance after operation")
    fee: float = Field(default=0.0, description="Operation commission/fee")
    status: str = Field(default="CONFIRMED", description="Operation status: CONFIRMED, HOLD, FAILED")
    category_suggestion: Optional[str] = Field(None, description="Category name suggested by bank")
    is_internal_transfer: bool = Field(
        default=False,
        description="True if transaction is a Me2Me transfer between user's own accounts"
    )


class PaginatedTransactionsResponse(BaseModel):
    items: List[TransactionResponse]
    total: int
    limit: int
    offset: int
    has_more: bool


class HealthResponse(BaseModel):
    status: str = "ok"
    version: str
    database: str = "connected"
    timestamp: int
    uptime_seconds: float
    total_accounts: int
    total_transactions: int


class UserResponse(BaseModel):
    id: str
    username: str
    created_at: int
    is_active: bool


class ApiKeyCreatedResponse(BaseModel):
    api_key: str = Field(..., description="Raw secret API key. Store safely, shown only once!")
    key_prefix: str
    name: str
    created_at: int


class ApiKeyResponse(BaseModel):
    key_prefix: str
    name: str
    created_at: int
    last_used_at: Optional[int] = None
    is_revoked: bool
