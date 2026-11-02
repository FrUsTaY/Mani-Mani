"""
Accounts API endpoints for retrieving bank products and balances.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sync_lab.core.models import User
from sync_lab.server.dependencies import get_current_user, get_repository
from sync_lab.server.schemas import AccountListResponse, BankAccountResponse
from sync_lab.storage.sqlite_repo import SqliteTransactionRepository

router = APIRouter(prefix="/accounts", tags=["Accounts"])


@router.get("", response_model=AccountListResponse)
async def list_accounts(
    current_user: User = Depends(get_current_user),
    repo: SqliteTransactionRepository = Depends(get_repository),
):
    """
    Returns all bank accounts, deposits, and cards belonging to the authenticated user.
    """
    accounts = repo.get_accounts(user_id=current_user.id)
    items = [
        BankAccountResponse(
            id=acc.id,
            bank_name=acc.raw_details.get("bank_name", "ВТБ"),
            name=acc.name,
            account_type=acc.account_type,
            balance=acc.balance,
            currency=acc.currency,
            card_last4=acc.card_last4,
            account_last4=acc.account_last4,
            account_product_id=acc.account_product_id or acc.raw_details.get("accountProductId") or acc.id,
            is_blocked=acc.is_blocked,
        )
        for acc in accounts
    ]
    return AccountListResponse(items=items, total=len(items))


@router.get("/{account_id}", response_model=BankAccountResponse)
async def get_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    repo: SqliteTransactionRepository = Depends(get_repository),
):
    """Returns single account details by account ID."""
    accounts = repo.get_accounts(user_id=current_user.id)
    match = next((a for a in accounts if a.id == account_id), None)
    if not match:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Account with id '{account_id}' not found.",
        )
    return BankAccountResponse(
        id=match.id,
        bank_name=match.raw_details.get("bank_name", "ВТБ"),
        name=match.name,
        account_type=match.account_type,
        balance=match.balance,
        currency=match.currency,
        card_last4=match.card_last4,
        account_last4=match.account_last4,
        account_product_id=match.account_product_id or match.raw_details.get("accountProductId") or match.id,
        is_blocked=match.is_blocked,
    )
