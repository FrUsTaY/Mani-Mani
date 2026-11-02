"""
API v1 main router aggregator.
"""

from fastapi import APIRouter
from sync_lab.server.api.v1.health import router as health_router
from sync_lab.server.api.v1.accounts import router as accounts_router
from sync_lab.server.api.v1.transactions import router as transactions_router

api_v1_router = APIRouter()

api_v1_router.include_router(health_router)
api_v1_router.include_router(accounts_router)
api_v1_router.include_router(transactions_router)
