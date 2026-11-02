# Syntax=docker/dockerfile:1
FROM python:3.11-slim-bookworm

LABEL maintainer="Mani-Mani Team"
LABEL description="Mani-Mani Sync-Lab Service: Bank Sync & Multi-Tenant Transaction API"

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8000 \
    PYTHONPATH=/app/src \
    SYNC_LAB_DATA_DIR=/data

# Install minimal system dependencies (curl for health check)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Security: Create non-root unprivileged service user and group
RUN groupadd -g 10001 appuser && \
    useradd -u 10001 -g appuser -s /bin/sh -m appuser

# Set up application workspace and persistent data mount directory
WORKDIR /app
RUN mkdir -p /data && chown -R appuser:appuser /data /app

# Install Python dependencies first for layer caching
COPY --chown=appuser:appuser requirements.txt pyproject.toml README.md ./
RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir -e .

# Copy application source code
COPY --chown=appuser:appuser src/ ./src/

# Switch to non-root user
USER appuser

# Expose HTTP API port
EXPOSE 8000

# Periodic container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://127.0.0.1:${PORT:-8000}/health || exit 1

# Default startup command: Launch FastAPI via CLI runner
CMD ["python", "-m", "sync_lab.cli", "server", "--host", "0.0.0.0", "--port", "8000"]
