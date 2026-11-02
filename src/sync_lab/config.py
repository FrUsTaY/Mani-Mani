"""
Server configuration and environment settings for Sync Lab.
Supports 12-factor app principles and dotenv.
"""

import os
from pathlib import Path
from typing import List
from dotenv import load_dotenv

# Base directory for the sync-lab package
PACKAGE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = PACKAGE_DIR.parent.parent

# Load .env if present in root or sync-lab directory
env_path = PROJECT_ROOT / ".env"
if not env_path.exists():
    env_path = PROJECT_ROOT / "sync-lab" / ".env"
if env_path.exists():
    load_dotenv(dotenv_path=env_path)


class Settings:
    """Application settings loaded from environment variables."""

    # Server binding
    HOST: str = os.getenv("SYNC_LAB_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("SYNC_LAB_PORT", "8000"))

    # File paths
    _data_dir_env = os.getenv("SYNC_LAB_DATA_DIR")
    if _data_dir_env:
        DATA_DIR: Path = Path(_data_dir_env).resolve()
    else:
        DATA_DIR: Path = (PROJECT_ROOT / "data").resolve()

    DB_PATH: Path = DATA_DIR / os.getenv("SYNC_LAB_DB_NAME", "sync_lab.db")
    EXPORTS_DIR: Path = DATA_DIR / "exports"
    USER_DATA_DIR: Path = DATA_DIR / "browser_profiles"

    # Application
    LOG_LEVEL: str = os.getenv("SYNC_LAB_LOG_LEVEL", "INFO")
    API_V1_STR: str = "/api/v1"
    PROJECT_NAME: str = "Mani-Mani Sync Lab Server"
    VERSION: str = "0.2.0"

    # Security
    API_KEY_HEADER_NAME: str = "X-API-Key"
    DEFAULT_USER_ID: str = "default_user"
    DEFAULT_USERNAME: str = "default_user"

    # CORS
    CORS_ORIGINS: List[str] = [
        origin.strip()
        for origin in os.getenv("SYNC_LAB_CORS_ORIGINS", "*").split(",")
        if origin.strip()
    ]


settings = Settings()
