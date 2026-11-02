"""
VTB Online History Collector.

Combines passive network interception of official JSON responses
and DOM extraction of transaction items from the web interface.

Reusability Guide:
1. Android equivalent:
   - On Android, network interception in WebView or DOM injection (`evaluateJavascript`)
     provides equivalent extraction.
2. Android adaptation needed:
   - Rewriting payload parser into Kotlin JSON parsing (Moshi / Kotlinx.Serialization).
3. Research-only:
   - Playwright page scrolling and navigation automation.
"""

import asyncio
import logging
from typing import List, Dict, Any, Optional
from playwright.async_api import Page
from sync_lab.core.models import RawBankTransaction, BankAccountInfo, AccountType
from sync_lab.adapters.vtb.network_interceptor import VtbNetworkInterceptor
from sync_lab.adapters.vtb.constants import VTB_HISTORY_URL

logger = logging.getLogger("sync_lab.vtb.history")


class VtbHistoryCollector:
    """
    Collects raw accounts and transaction events from VTB Online.
    """

    def __init__(self, page: Page, interceptor: VtbNetworkInterceptor):
        self.page = page
        self.interceptor = interceptor

    async def navigate_to_history(self) -> None:
        """Navigates to the transaction history section."""
        logger.info("Navigating to History section...")
        try:
            # Try clicking on history link or navigation item first
            clicked = await self.page.evaluate("""() => {
                const links = Array.from(document.querySelectorAll('a, button, [role="tab"]'));
                const histLink = links.find(el => /история|операции/i.test(el.textContent || ''));
                if (histLink) {
                    histLink.click();
                    return true;
                }
                return false;
            }""")
            if not clicked:
                await self.page.goto(VTB_HISTORY_URL, wait_until="domcontentloaded", timeout=30000)
            else:
                await asyncio.sleep(2)
        except Exception as e:
            logger.warning(f"Could not directly navigate via UI: {e}. Trying direct URL...")
            try:
                await self.page.goto(VTB_HISTORY_URL, wait_until="domcontentloaded", timeout=30000)
            except Exception as e2:
                logger.error(f"Navigation failed: {e2}")

    async def scroll_history(self, scroll_count: int = 3) -> None:
        """
        Scrolls the page to trigger infinite scroll or lazy loading of older transactions.
        """
        logger.info(f"Scrolling history {scroll_count} times to trigger lazy-loading...")
        for i in range(scroll_count):
            await self.page.evaluate("window.scrollBy(0, 1000);")
            await asyncio.sleep(2)

    def extract_from_intercepted_responses(self) -> List[RawBankTransaction]:
        """
        Analyzes intercepted JSON payloads from VTB Online API.
        Extracts transaction objects into RawBankTransaction representations.
        """
        transactions: List[RawBankTransaction] = []
        captured = self.interceptor.captured_history_responses

        seen_tx_keys = set()

        for item in captured:
            data = item.get("data", {})
            tx_candidates = []

            # 1. Inspect common REST structures: {"items": [...]}, {"operations": [...]}, {"transactions": [...]}, or direct list [...]
            if isinstance(data, list):
                tx_candidates = data
            elif isinstance(data, dict):
                for key in ["items", "operations", "transactions", "history", "statement", "events"]:
                    val = data.get(key)
                    if isinstance(val, list):
                        tx_candidates = val
                        break

            for idx, raw_tx in enumerate(tx_candidates):
                if not isinstance(raw_tx, dict):
                    continue

                account_val = str(raw_tx.get("account") or raw_tx.get("accountLastDigits") or raw_tx.get("accountTail") or "")
                acc = account_val or str(raw_tx.get("accountProductId") or "")
                debet_val = raw_tx.get("debet")
                raw_internal_id = raw_tx.get("internalId") or raw_tx.get("rrn")
                iid = raw_internal_id or raw_tx.get("operationId") or raw_tx.get("id")

                dedup_key = f"{iid}_{acc}_{debet_val}"
                if dedup_key in seen_tx_keys:
                    continue
                seen_tx_keys.add(dedup_key)

                if raw_internal_id:
                    import re
                    digits = re.findall(r"\d{4}", acc)
                    acc_suffix = digits[-1] if digits else (acc[-6:] if acc else "acc")
                    debet_code = "dr" if debet_val is True else ("cr" if debet_val is False else "tx")
                    raw_id = f"vtb_{raw_internal_id}_{acc_suffix}_{debet_code}"
                else:
                    raw_id = str(raw_tx.get("operationId") or raw_tx.get("id") or f"vtb_intercepted_{len(transactions)+1}")
                title = str(
                    raw_tx.get("operationName") or
                    raw_tx.get("fullOperationName") or
                    raw_tx.get("title") or
                    raw_tx.get("description") or
                    raw_tx.get("merchantName") or
                    raw_tx.get("name") or
                    "Банковская операция"
                )

                # Amount extraction from dictionary or scalar
                # VTB returns: "operationAmount": {"currency": "RUB", "sum": 1500.0}
                amount_obj = (
                    raw_tx.get("operationAmount") or
                    raw_tx.get("transactionAmount") or
                    raw_tx.get("amount") or
                    raw_tx.get("sum") or
                    raw_tx.get("operationSum")
                )
                currency = "RUB"
                amount_str = ""

                if isinstance(amount_obj, dict):
                    raw_num = (
                        amount_obj.get("sum") if amount_obj.get("sum") is not None else
                        amount_obj.get("value") if amount_obj.get("value") is not None else
                        amount_obj.get("amount") or 0.0
                    )
                    # Check debet flag: debet=True is expense (-), debet=False is income (+)
                    debet_flag = raw_tx.get("debet")
                    sign_prefix = "-" if debet_flag is True else ("+" if debet_flag is False else "")
                    amount_str = f"{sign_prefix}{raw_num}"
                    currency = str(amount_obj.get("currency") or amount_obj.get("currencyCode") or "RUB")
                elif amount_obj is not None:
                    amount_str = str(amount_obj)

                date_val = str(
                    raw_tx.get("transactionDate") or
                    raw_tx.get("operationDate") or
                    raw_tx.get("date") or
                    raw_tx.get("timestamp") or
                    ""
                )

                # Category extraction: VTB returns "parentCategory": {"name": "Супермаркеты", ...}
                category_val = ""
                parent_cat = raw_tx.get("parentCategory")
                if isinstance(parent_cat, dict):
                    category_val = str(parent_cat.get("name") or "")
                elif isinstance(parent_cat, str):
                    category_val = parent_cat
                if not category_val:
                    category_val = str(raw_tx.get("category") or raw_tx.get("categoryName") or raw_tx.get("mccDescription") or "")

                status_val = str(raw_tx.get("status") or raw_tx.get("state") or "")
                card_tail = str(raw_tx.get("cardLastDigits") or raw_tx.get("cardTail") or "")

                transactions.append(RawBankTransaction(
                    raw_id=raw_id,
                    bank_name="ВТБ",
                    raw_title=title,
                    raw_amount=amount_str,
                    raw_currency=currency,
                    raw_date=date_val,
                    raw_category=category_val if category_val else None,
                    raw_status=status_val if status_val else None,
                    account_tail=account_val if account_val else None,
                    card_tail=card_tail if card_tail else None,
                    raw_payload={
                        "source": "api_intercepted",
                        "endpoint": item.get("url"),
                        "raw_data": raw_tx
                    }
                ))

        return transactions

    async def extract_from_dom(self) -> List[RawBankTransaction]:
        """
        Fallback / verification extractor: inspects rendered HTML elements in the browser.
        Extracts visible transaction list items directly from DOM.
        """
        logger.info("Extracting transactions from rendered DOM...")
        dom_items: List[Dict[str, Any]] = await self.page.evaluate("""() => {
            const results = [];
            // Common transaction row selectors / containers
            const rows = document.querySelectorAll(
                '[data-testid*="history-item"], [data-testid*="operation"], .history-item, .operation-row, [class*="HistoryItem"], [class*="OperationRow"], [class*="transactionRow"]'
            );

            rows.forEach((row, idx) => {
                const text = row.innerText || '';
                const lines = text.split('\\n').map(l => l.trim()).filter(l => l.length > 0);
                results.append ? results.push({ index: idx, fullText: text, lines: lines }) : results.push({ index: idx, fullText: text, lines: lines });
            });

            return results;
        }""")

        transactions: List[RawBankTransaction] = []
        for item in dom_items:
            lines = item.get("lines", [])
            if not lines:
                continue

            full_text = " | ".join(lines)
            raw_id = f"vtb_dom_{item.get('index', 0)}"

            # First line is usually title or merchant, or date
            title = lines[0]
            amount_str = ""
            for line in lines:
                if any(curr in line for curr in ["₽", "руб", "RUB", "€", "$"]):
                    amount_str = line
                    break

            transactions.append(RawBankTransaction(
                raw_id=raw_id,
                bank_name="ВТБ",
                raw_title=title,
                raw_amount=amount_str,
                raw_currency="RUB",
                raw_date="",
                raw_payload={"source": "dom_extracted", "lines": lines}
            ))

        return transactions
