from __future__ import annotations

from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

REPO_ROOT = Path(__file__).resolve().parents[3]


class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    agent_port: int = 8001
    core_grpc_target: str = "127.0.0.1:9090"
    core_http_base: str = "http://127.0.0.1:8082"
    pg_dsn: str = ""
    redis_url: str = ""
    kafka_bootstrap: str = ""

    # OpenAI-compatible LLM routing (DeepSeek / DashScope / OpenAI / Ollama)
    llm_mock_mode: bool = True
    llm_base_url: str = "https://api.deepseek.com/v1"
    llm_api_key: str = ""
    llm_primary_model: str = "deepseek-chat"
    llm_router_model: str = "deepseek-chat"
    llm_backup_base_url: str = ""
    llm_backup_api_key: str = ""
    llm_backup_model: str = ""

    embedding_base_url: str = ""
    embedding_api_key: str = ""
    embedding_model: str = "text-embedding-v3"
    embedding_dim: int = 1024

    contracts_root: str = str(REPO_ROOT / "contracts")
    seed_kb_dir: str = str(REPO_ROOT / "scripts" / "pg_etl" / "seed_kb_docs")

    @property
    def use_live_llm(self) -> bool:
        return (not self.llm_mock_mode) and bool(self.llm_api_key.strip())


settings = AgentSettings()
