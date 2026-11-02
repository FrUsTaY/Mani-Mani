"""
VTB Bank Adapter.

Main entry point for VTB Online synchronization.
Implements BaseBankAdapter lifecycle.

Reusability Guide:
1. Reusable / Android equivalent:
   - Orchestrates the sync workflow. On Android, a Kotlin class `VtbBankSyncService`
     will orchestrate the WebView and Room DAO in the exact same flow.
2. Android adaptation needed:
   - Lifecycle binding to Android ViewModel/Worker.
3. Research-only:
   - Browser launching and desktop session handling.
"""

import logging
import re
from pathlib import Path
from typing import List, Optional
from sync_lab.core.interfaces import BaseBankAdapter
from sync_lab.core.models import BankAccountInfo, AccountType, RawBankTransaction
from sync_lab.adapters.vtb.auth_manager import VtbAuthManager
from sync_lab.adapters.vtb.network_interceptor import VtbNetworkInterceptor
from sync_lab.adapters.vtb.history_collector import VtbHistoryCollector

logger = logging.getLogger("sync_lab.vtb.adapter")


class VtbBankAdapter(BaseBankAdapter):
    """
    Adapter for VTB Online.
    """

    def __init__(self, user_data_dir: Path, headless: bool = False):
        self.user_data_dir = Path(user_data_dir)
        self.headless = headless
        self.interceptor = VtbNetworkInterceptor()
        self.auth_manager = VtbAuthManager(
            user_data_dir=self.user_data_dir,
            interceptor=self.interceptor,
            headless=self.headless
        )
        self.collector: Optional[VtbHistoryCollector] = None

    @property
    def bank_name(self) -> str:
        return "ВТБ"

    async def initialize(self) -> None:
        """Starts browser context and attaches network monitors."""
        page = await self.auth_manager.start()
        self.collector = VtbHistoryCollector(page=page, interceptor=self.interceptor)

    async def open_login_page(self) -> None:
        """Navigates to login page for user manual authorization."""
        await self.auth_manager.open_login_page()

    async def is_authenticated(self) -> bool:
        """Checks if session is authenticated."""
        return await self.auth_manager.is_authenticated()

    async def wait_for_login(self, timeout_seconds: int = 300) -> bool:
        """Awaits manual user authentication."""
        return await self.auth_manager.wait_for_user_auth(timeout_seconds=timeout_seconds)

    async def fetch_accounts(
        self, raw_transactions: Optional[List[RawBankTransaction]] = None
    ) -> List[BankAccountInfo]:
        """
        Extracts bank accounts and cards from captured network responses
        and aggregates distinct accounts from transaction history.
        """
        accounts: List[BankAccountInfo] = []
        known_ids = set()

        # 1. First priority: Extract from dedicated portfolio / accounts API responses
        for resp in self.interceptor.captured_account_responses:
            data = resp.get("data", {})
            acc_list = []
            if isinstance(data, list):
                acc_list = data
            elif isinstance(data, dict):
                for k in ["accounts", "cards", "products", "items"]:
                    if isinstance(data.get(k), list):
                        acc_list = data[k]
                        break

            for item in acc_list:
                if not isinstance(item, dict):
                    continue
                acc_id = str(item.get("id") or item.get("accountNumber") or item.get("number") or f"vtb_acc_{len(accounts)+1}")
                if acc_id in known_ids:
                    continue
                known_ids.add(acc_id)

                name = str(item.get("name") or item.get("title") or "Счет ВТБ")
                balance_obj = item.get("balance") or item.get("amount") or 0.0
                balance_val = 0.0
                currency = "RUB"
                if isinstance(balance_obj, dict):
                    balance_val = float(balance_obj.get("value") or balance_obj.get("amount") or 0.0)
                    currency = str(balance_obj.get("currency") or "RUB")
                elif isinstance(balance_obj, (int, float)):
                    balance_val = float(balance_obj)

                accounts.append(BankAccountInfo(
                    id=acc_id,
                    name=name,
                    balance=balance_val,
                    currency=currency,
                    card_last4=item.get("cardLastDigits") or item.get("cardTail"),
                    account_last4=item.get("accountLastDigits") or item.get("accountTail"),
                    raw_details={"bank_name": self.bank_name, "raw": item}
                ))

        # 2. Extract distinct accounts directly from intercepted history operations
        if raw_transactions:
            for tx in raw_transactions:
                raw_data = tx.raw_payload.get("raw_data", {})
                acc_prod_id = str(raw_data.get("accountProductId") or tx.account_tail or "")
                acc_display = str(raw_data.get("account") or tx.account_tail or "")

                if not acc_prod_id and not acc_display:
                    continue

                acc_id = acc_prod_id or acc_display
                if acc_id in known_ids:
                    continue
                known_ids.add(acc_id)

                acc_last4 = None
                digits = re.findall(r"\d{4}", acc_display)
                if digits:
                    acc_last4 = digits[-1]

                accounts.append(BankAccountInfo(
                    id=acc_id,
                    name=f"Счет ВТБ {acc_display}".strip(),
                    account_type=AccountType.DEBIT,
                    balance=0.0,
                    currency=tx.raw_currency or "RUB",
                    account_last4=acc_last4,
                    raw_details={"bank_name": self.bank_name, "accountProductId": acc_prod_id}
                ))

        return accounts

    async def fetch_transactions(self, days_back: int = 30) -> List[RawBankTransaction]:
        """
        Navigates to history, triggers network requests, and collects raw transactions.
        """
        if not self.collector:
            raise RuntimeError("Adapter not initialized. Call initialize() first.")

        # 1. Navigate to history page in UI
        await self.collector.navigate_to_history()

        # 2. Scroll to trigger requests
        await self.collector.scroll_history(scroll_count=3)

        # 3. First priority: Extract from intercepted official JSON responses
        txs = self.collector.extract_from_intercepted_responses()

        # 4. Fallback / supplementary: extract from rendered DOM if network responses were not captured
        if not txs:
            logger.info("No API responses captured yet. Falling back to DOM inspection...")
            txs = await self.collector.extract_from_dom()

        return txs

    async def close(self) -> None:
        """Closes browser session."""
        await self.auth_manager.close()
