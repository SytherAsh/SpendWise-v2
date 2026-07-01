"""Application settings for the FastAPI ML service.

Reads the FastAPI environment variables listed in docs/deployment.md
(Environment Variables -> FastAPI ML Service).
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    supabase_url: str = ""
    supabase_key: str = ""
    sentry_dsn: str = ""
    model_path: str = "./models/"
    ml_internal_key: str = ""


def get_settings() -> Settings:
    return Settings()
