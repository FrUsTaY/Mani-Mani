"""
VTB Online Authentication Manager.

Manages the Chromium browser session for user manual authentication.
Ensures zero credential interception: credentials, OTPs, and passwords
are handled exclusively by the user in the official VTB UI.

Reusability Guide:
1. Android equivalent:
   - Android WebView with CookieManager and WebChromeClient.
2. Research-only:
   - Playwright persistent browser context management.
"""

import asyncio
import logging
from pathlib import Path
from typing import Optional
from playwright.async_api import BrowserContext, Page, async_playwright
from sync_lab.adapters.vtb.constants import (
    VTB_LOGIN_URL,
    VTB_MAIN_URL,
    AUTH_PATH_INDICATORS,
    AUTHENTICATED_PATH_INDICATORS
)
from sync_lab.adapters.vtb.network_interceptor import VtbNetworkInterceptor

logger = logging.getLogger("sync_lab.vtb.auth")


class VtbAuthManager:
    """
    Coordinates browser lifecycle and detects user authentication status.
    """

    def __init__(
        self,
        user_data_dir: Path,
        interceptor: Optional[VtbNetworkInterceptor] = None,
        headless: bool = False
    ):
        self.user_data_dir = Path(user_data_dir)
        self.user_data_dir.mkdir(parents=True, exist_ok=True)
        self.interceptor = interceptor or VtbNetworkInterceptor()
        self.headless = headless
        self._playwright = None
        self.context: Optional[BrowserContext] = None
        self.page: Optional[Page] = None

    async def start(self) -> Page:
        """
        Launches persistent Chromium context preserving user cookies / session.
        """
        logger.info("Initializing Chromium browser for VTB Online...")
        self._playwright = await async_playwright().start()

        # Launch persistent context with TLS/SSL error bypass for Russian CA certificates
        self.context = await self._playwright.chromium.launch_persistent_context(
            user_data_dir=str(self.user_data_dir),
            headless=self.headless,
            no_viewport=True,
            ignore_https_errors=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--start-maximized",
                "--new-window",
                "--ignore-certificate-errors",
                "--ignore-certificate-errors-spki-list"
            ],
            locale="ru-RU"
        )

        # Hook network interception
        if self.context.pages:
            self.page = self.context.pages[0]
        else:
            self.page = await self.context.new_page()

        self.page.on("response", self.interceptor.handle_response)
        return self.page

    async def open_login_page(self) -> None:
        """Navigate to official VTB Online entrance."""
        if not self.page:
            await self.start()

        logger.info(f"Navigating to VTB Online: {VTB_LOGIN_URL}")
        await self.page.goto(VTB_LOGIN_URL, wait_until="domcontentloaded", timeout=60000)
        try:
            await self.page.bring_to_front()
        except Exception:
            pass

    async def is_authenticated(self) -> bool:
        """
        Checks whether the current session is authenticated by analyzing
        the page URL and DOM cues.
        """
        if not self.page:
            return False

        try:
            current_url = self.page.url.lower()

            # If still on explicit login paths
            for auth_path in AUTH_PATH_INDICATORS:
                if auth_path in current_url:
                    return False

            # If on known authenticated path
            for authed_path in AUTHENTICATED_PATH_INDICATORS:
                if authed_path in current_url:
                    return True

            # Check DOM for authenticated elements (e.g., exit button, profile button, accounts list)
            # Safe non-invasive checks
            is_logged_in = await self.page.evaluate("""() => {
                const text = document.body ? document.body.innerText : '';
                const hasExit = /выйти|выход|личный кабинет|счета и карты|мои продукты/i.test(text);
                const hasLoginInput = document.querySelector('input[type="password"]') !== null;
                return hasExit && !hasLoginInput;
            }""")

            return bool(is_logged_in)
        except Exception as e:
            logger.debug(f"Error checking auth status: {e}")
            return False

    async def wait_for_user_auth(self, timeout_seconds: int = 300) -> bool:
        """
        Waits for the user to complete the manual login, SMS code, etc.
        Polls non-intrusively every 2 seconds.
        """
        logger.info(
            "Waiting for user manual authentication in VTB Online... "
            f"(timeout: {timeout_seconds}s)"
        )

        elapsed = 0
        poll_interval = 2
        while elapsed < timeout_seconds:
            if await self.is_authenticated():
                logger.info("Authentication detected successfully!")
                return True
            await asyncio.sleep(poll_interval)
            elapsed += poll_interval

        logger.warning("Authentication timeout reached without successful login.")
        return False

    async def close(self) -> None:
        """Close browser context."""
        try:
            if self.context:
                await self.context.close()
            if self._playwright:
                await self._playwright.stop()
        except Exception as e:
            logger.debug(f"Error closing browser: {e}")
