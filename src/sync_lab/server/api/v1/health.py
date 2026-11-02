"""
Health check endpoints for Docker container monitoring and uptime diagnostics.
"""

import time
from fastapi import APIRouter, Depends
from sync_lab.config import settings
from sync_lab.server.dependencies import get_repository
from sync_lab.server.schemas import HealthResponse
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository

router = APIRouter(tags=["Health"])

START_TIME = time.time()


@router.get("/health", response_model=HealthResponse)
async def check_health(repo: SqliteTransactionRepository = Depends(get_repository)):
    """Health check endpoint used by Docker HEALTHCHECK and monitoring tools."""
    # Test DB query safely without leaking private multi-tenant metrics
    with repo._get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT 1")
        cur.fetchone()

    return HealthResponse(
        status="ok",
        version=settings.VERSION,
        database="connected",
        timestamp=int(time.time() * 1000),
        uptime_seconds=round(time.time() - START_TIME, 2),
    )
