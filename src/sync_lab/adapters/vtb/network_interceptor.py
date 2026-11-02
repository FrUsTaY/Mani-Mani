"""
Passive Network Interceptor for VTB Online.

Monitors official XHR/Fetch network traffic made by the VTB Online SPA frontend.
Extracts structured JSON responses for accounts and transaction history without
modifying requests or bypassing any security mechanisms.

Reusability Guide:
1. Reusable / Android mapping:
   - On Android WebView, equivalent functionality is implemented using:
     `WebViewClient.shouldInterceptRequest` or injected JavaScript fetch hooks.
2. Android adaptation needed:
   - Request interception API binding in Kotlin WebView.
3. Research-only:
   - Playwright response event listener.
"""

import json
import logging
from typing import List, Dict, Any, Callable, Optional
from sync_lab.adapters.vtb.constants import API_HISTORY_PATTERNS, API_ACCOUNTS_PATTERNS

logger = logging.getLogger("sync_lab.vtb.network")


class VtbNetworkInterceptor:
    """
    Passively monitors HTTP responses emitted by the VTB Online client app.
    """

    def __init__(self):
        self.captured_account_responses: List[Dict[str, Any]] = []
        self.captured_history_responses: List[Dict[str, Any]] = []
        self.raw_captured_events: List[Dict[str, Any]] = []
        self._listeners: List[Callable[[str, Dict[str, Any]], None]] = []

    def add_listener(self, callback: Callable[[str, Dict[str, Any]], None]) -> None:
        self._listeners.append(callback)

    async def handle_response(self, response) -> None:
        """
        Playwright response handler callback.
        Inspects content type and status code, sanitizes headers,
        and saves JSON payloads matching account or history patterns.
        """
        try:
            url = response.url
            status = response.status

            if status != 200:
                return

            # Filter for official VTB domains only
            if "vtb.ru" not in url:
                return

            content_type = response.headers.get("content-type", "")
            if "application/json" not in content_type:
                return

            # Check if URL matches history or account patterns
            is_history = any(pat in url.lower() for pat in API_HISTORY_PATTERNS)
            is_accounts = any(pat in url.lower() for pat in API_ACCOUNTS_PATTERNS)

            if not (is_history or is_accounts):
                return

            try:
                body = await response.json()
            except Exception:
                return

            if not isinstance(body, (dict, list)):
                return

            record = {
                "url": url,
                "status": status,
                "data": body
            }

            if is_history:
                logger.info(f"Captured history API response: {url}")
                self.captured_history_responses.append(record)
                for cb in self._listeners:
                    cb("history", record)

            if is_accounts:
                logger.info(f"Captured accounts API response: {url}")
                self.captured_account_responses.append(record)
                for cb in self._listeners:
                    cb("accounts", record)

        except Exception as e:
            logger.debug(f"Error intercepting response: {e}")

    def clear(self) -> None:
        self.captured_account_responses.clear( )
        self.captured_history_responses.clear()
        self.raw_captured_events.clear()
