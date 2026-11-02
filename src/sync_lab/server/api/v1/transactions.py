"""
Transactions API endpoints for retrieving normalized financial operations.
"""

from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status

from sync_lab.core.models import User, TransactionType
from sync_lab.server.dependencies import get_current_user, get_repository
from sync_lab.server.schemas import (
    PaginatedTransactionsResponse,
    TransactionResponse,
)
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository

router = APIRouter(prefix="/transactions", tags=["Transactions"])


@router.get("", response_model=PaginatedTransactionsResponse)
async def list_transactions(
    account_last4: Optional[str] = Query(None, description="Filter by account/card last 4 digits"),
    account_product_id: Optional[str] = Query(None, description="Filter by bank account product ID"),
    type: Optional[TransactionType] = Query(None, description="Operation type: EXPENSE, INCOME, TRANSFER"),
    from_timestamp: Optional[int] = Query(None, description="Start date range in epoch milliseconds"),
    to_timestamp: Optional[int] = Query(None, description="End date range in epoch milliseconds"),
    since_timestamp: Optional[int] = Query(None, description="Incremental sync cursor: fetch only operations modified/created after this epoch ms"),
    is_internal_transfer: Optional[bool] = Query(None, description="Filter transfers between user's own accounts (Me2Me)"),
    limit: int = Query(50, ge=1, le=200, description="Items per page (max 200)"),
    offset: int = Query(0, ge=0, description="Page offset"),
    current_user: User = Depends(get_current_user),
    repo: SqliteTransactionRepository = Depends(get_repository),
):
    """
    Returns paginated normalized transactions for the authenticated user.
    Supports granular filtering and cursor-based incremental sync for mobile clients.
    """
    total = repo.count_normalized_transactions(
        user_id=current_user.id,
        account_last4=account_last4,
        account_product_id=account_product_id,
        tx_type=type,
        from_timestamp=from_timestamp,
        to_timestamp=to_timestamp,
        since_timestamp=since_timestamp,
        is_internal_transfer=is_internal_transfer,
    )

    transactions = repo.get_normalized_transactions(
        user_id=current_user.id,
        account_last4=account_last4,
        account_product_id=account_product_id,
        tx_type=type,
        from_timestamp=from_timestamp,
        to_timestamp=to_timestamp,
        since_timestamp=since_timestamp,
        is_internal_transfer=is_internal_transfer,
        limit=limit,
        offset=offset,
    )

    items = [
        TransactionResponse(
            bank_id=tx.bank_id,
            bank_name=tx.bank_name,
            type=tx.type,
            amount=tx.amount,
            currency=tx.currency,
            timestamp=tx.timestamp,
            description=tx.description,
            merchant=tx.merchant,
            mcc=tx.mcc,
            card_last4=tx.card_last4,
            account_last4=tx.account_last4,
            account_product_id=tx.account_product_id,
            balance_after=tx.balance_after,
            fee=tx.fee,
            status=tx.status,
            category_suggestion=tx.category_suggestion,
            is_internal_transfer=tx.is_internal_transfer,
            created_at=tx.created_at,
            updated_at=tx.updated_at,
        )
        for tx in transactions
    ]

    has_more = (offset + len(items)) < total

    return PaginatedTransactionsResponse(
        items=items,
        total=total,
        limit=limit,
        offset=offset,
        has_more=has_more,
    )


@router.get("/{bank_id}", response_model=TransactionResponse)
async def get_transaction(
    bank_id: str,
    current_user: User = Depends(get_current_user),
    repo: SqliteTransactionRepository = Depends(get_repository),
):
    """Returns a single transaction by stable bank_id."""
    tx = repo.get_transaction_by_id(bank_id=bank_id, user_id=current_user.id)
    if not tx:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Transaction '{bank_id}' not found.",
        )

    return TransactionResponse(
        bank_id=tx.bank_id,
        bank_name=tx.bank_name,
        type=tx.type,
        amount=tx.amount,
        currency=tx.currency,
        timestamp=tx.timestamp,
        description=tx.description,
        merchant=tx.merchant,
        mcc=tx.mcc,
        card_last4=tx.card_last4,
        account_last4=tx.account_last4,
        account_product_id=tx.account_product_id,
        balance_after=tx.balance_after,
        fee=tx.fee,
        status=tx.status,
        category_suggestion=tx.category_suggestion,
        is_internal_transfer=tx.is_internal_transfer,
        created_at=tx.created_at,
        updated_at=tx.updated_at,
    )
