"""
FastAPI dependencies for authentication and database access.
"""

from typing import Optional
from fastapi import Header, HTTPException, Security, status, Depends
from fastapi.security import APIKeyHeader

from sync_lab.config import settings
from sync_lab.core.models import User
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository

# Singleton or factory repository
_repo_instance: Optional[SqliteTransactionRepository] = None


def get_repository() -> SqliteTransactionRepository:
    global _repo_instance
    if _repo_instance is None:
        _repo_instance = SqliteTransactionRepository(settings.DB_PATH)
    return _repo_instance


api_key_header = APIKeyHeader(name=settings.API_KEY_HEADER_NAME, auto_error=False)


async def get_current_user(
    x_api_key: Optional[str] = Security(api_key_header),
    authorization: Optional[str] = Header(None),
    repo: SqliteTransactionRepository = Depends(get_repository),
) -> User:
    """
    Authenticates request via X-API-Key header or Authorization: Bearer <token>.
    Guarantees user isolation.
    """
    raw_key = x_api_key

    # Check Authorization header if X-API-Key is omitted
    if not raw_key and authorization:
        parts = authorization.split()
        if len(parts) == 2 and parts[0].lower() in ["bearer", "token", "apikey"]:
            raw_key = parts[1]

    if not raw_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing API key. Provide 'X-API-Key' or 'Authorization: Bearer <key>' header.",
            headers={"WWW-Authenticate": "ApiKey"},
        )

    user = repo.verify_api_key(raw_key)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid, expired, or revoked API key.",
            headers={"WWW-Authenticate": "ApiKey"},
        )

    return user
