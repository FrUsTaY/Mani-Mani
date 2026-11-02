"""
VTB Online URL constants, API patterns, and selectors.

Reusability Guide:
1. Reusable / Android mapping:
   - URLs and API endpoints can be directly referenced by Android WebView.
2. Research-only:
   - Playwright DOM selectors and desktop timeout configs.
"""

# Official VTB Online URLs
VTB_LOGIN_URL = "https://online.vtb.ru/login"
VTB_MAIN_URL = "https://online.vtb.ru/"
VTB_HISTORY_URL = "https://online.vtb.ru/history"

# URL substrings that indicate user is still in authentication flow
AUTH_PATH_INDICATORS = [
    "/login",
    "/auth",
    "/confirmation",
    "/challenge",
    "/password"
]

# URL substrings that indicate user is in authenticated cabinet
AUTHENTICATED_PATH_INDICATORS = [
    "/main",
    "/home",
    "/products",
    "/history",
    "/operations",
    "/payments"
]

# Known API endpoint substrings for passive network interception
API_HISTORY_PATTERNS = [
    "/history",
    "/operations",
    "/statement",
    "/transactions",
    "/timeline",
    "/events"
]

API_ACCOUNTS_PATTERNS = [
    "/products",
    "/cards",
    "/accounts",
    "/balance",
    "/portfolio"
]
