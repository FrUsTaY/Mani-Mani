"""
Core interfaces and contracts for Bank Adapters and Storage.

Reusability Guide:
1. Reusable / 1:1 mapping with Kotlin interfaces:
   - BankAdapter contract (Kotlin interface)
   - TransactionRepository contract (Kotlin DAO/Repository)
2. Android adaptation needed:
   - Implementation will bind to Room DAO on Android instead of standalone SQLite
3. Research-only:
   - Headed browser lifecycle interfaces
"""

from abc import ABC, abstractmethod
from typing import List, Optional
from pathlib import Path
from sync_lab.core.models import (
    BankAccountInfo,
    RawBankTransaction,
    NormalizedTransaction,
    SyncSession,
)


class BaseBankAdapter(ABC):
    """
    Abstract contract for a bank integration module.
    Every bank (VTB, Sber, Alfa, etc.) implements this lifecycle.
    """

    @property
    @abstractmethod
    def bank_name(self) -> str:
        """Name of the bank (e.g. 'ВТБ', 'Сбербанк')."""
        pass

    @abstractmethod
    async def initialize(self) -> None:
        """Initialize browser, context or session."""
        pass

    @abstractmethod
    async def open_login_page(self) -> None:
        """Navigate to bank login page and wait for user manual authentication."""
        pass

    @abstractmethod
    async def is_authenticated(self) -> bool:
        """Check if user has successfully authenticated in bank online cabinet."""
        pass

    @abstractmethod
    async def fetch_accounts(self) -> List[BankAccountInfo]:
        """Retrieve list of accounts, cards and current balances."""
        pass

    @abstractmethod
    async def fetch_transactions(
        self, days_back: int = 30
    ) -> List[RawBankTransaction]:
        """Fetch raw transaction list for specified period."""
        pass

    @abstractmethod
    async def close(self) -> None:
        """Gracefully release browser and network resources."""
        pass


class BaseNormalizer(ABC):
    """
    Normalizes bank-specific raw transactions into standard Mani-Mani entities.
    """

    @abstractmethod
    def normalize_transaction(
        self, raw: RawBankTransaction
    ) -> Optional[NormalizedTransaction]:
        """Normalize a single raw transaction. Returns None if it is a spam/system message."""
        pass


class BaseTransactionRepository(ABC):
    """
    Local storage interface for saving bank accounts and transactions.
    """

    @abstractmethod
    def save_accounts(self, accounts: List[BankAccountInfo]) -> int:
        pass

    @abstractmethod
    def save_raw_transactions(self, raw_txs: List[RawBankTransaction]) -> int:
        pass

    @abstractmethod
    def save_normalized_transactions(
        self, txs: List[NormalizedTransaction]
    ) -> int:
        pass

    @abstractmethod
    def get_normalized_transactions(
        self,
        bank_name: Optional[str] = None,
        limit: int = 100,
        offset: int = 0
    ) -> List[NormalizedTransaction]:
        pass

    @abstractmethod
    def export_to_json(self, output_path: Path) -> None:
        pass
