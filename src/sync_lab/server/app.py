"""
Main FastAPI Application entrypoint for Mani-Mani Sync Lab Server.
"""

import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from sync_lab.config import settings
from sync_lab.server.api.v1.health import router as root_health_router
from sync_lab.server.api.v1.router import api_v1_router
from sync_lab.server.dependencies import get_repository

logger = logging.getLogger("sync_lab.server")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifecycle event handler: initializes DB and cleanly shuts down."""
    logger.info(f"Starting {settings.PROJECT_NAME} v{settings.VERSION}...")
    # Initialize repository
    repo = get_repository()
    logger.info(f"Database initialized at: {repo.db_path}")
    yield
    logger.info(f"Gracefully shutting down {settings.PROJECT_NAME}...")


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.PROJECT_NAME,
        version=settings.VERSION,
        description="Bank synchronization server API for Mani-Mani mobile app.",
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    # CORS configuration
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Root health check (required by Docker HEALTHCHECK)
    app.include_router(root_health_router)

    # Versioned API routes
    app.include_router(api_v1_router, prefix=settings.API_V1_STR)

    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception):
        logger.error(f"Unhandled error processing {request.method} {request.url.path}: {exc}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "detail": "An internal server error occurred.",
                "type": type(exc).__name__,
            },
        )

    return app


app = create_app()
