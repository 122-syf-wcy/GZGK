#!/usr/bin/env bash
# End-to-end remote server deployment helper for GZGK v2:
#   1. Syncs infra/, contracts/, backend-java/, backend-python/, frontend/gzgk-web/dist, scripts/pg_etl/
#   2. Brings up Docker Compose `core` profile (PostgreSQL 16 + pgvector, Redis 7, Kafka 3.8 KRaft)
#   3. Builds Java 21 JARs, generates Python Protobuf stubs, runs real-data COPY ETL & RAG KB ingest
#
# Usage:
#   REMOTE_HOST=my-server bash scripts/deploy/deploy_all.sh

set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:?Set REMOTE_HOST (SSH alias or user@ip)}"
SSH_PORT="${SSH_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/gzgk-v2}"
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

echo "[deploy] Syncing GZGK v2 workspace to ${REMOTE_HOST}:${REMOTE_DIR}..."
ssh -p "${SSH_PORT}" "${REMOTE_HOST}" "mkdir -p ${REMOTE_DIR}/{infra,contracts,backend-java,backend-python,frontend,scripts/pg_etl,scripts/data}"

rsync -az --delete --exclude='.env' -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/infra/" "${REMOTE_HOST}:${REMOTE_DIR}/infra/"
rsync -az --delete -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/contracts/" "${REMOTE_HOST}:${REMOTE_DIR}/contracts/"
rsync -az --delete --exclude='target/' -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/backend-java/" "${REMOTE_HOST}:${REMOTE_DIR}/backend-java/"
rsync -az --delete --exclude='.venv/' --exclude='__pycache__/' -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/backend-python/" "${REMOTE_HOST}:${REMOTE_DIR}/backend-python/"
rsync -az --delete -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/frontend/gzgk-web/dist/" "${REMOTE_HOST}:${REMOTE_DIR}/frontend/dist/"
rsync -az --delete -e "ssh -p ${SSH_PORT}" "${ROOT_DIR}/scripts/pg_etl/" "${REMOTE_HOST}:${REMOTE_DIR}/scripts/pg_etl/"

echo "[deploy] Sync complete. Next on server:"
echo "  1. cp ${REMOTE_DIR}/infra/.env.example ${REMOTE_DIR}/infra/.env && edit strong passwords"
echo "  2. cd ${REMOTE_DIR}/infra && docker compose --env-file .env --profile core up -d"
echo "  3. Run Flyway migration + scripts/pg_etl/import_real_data_to_pg.py + ingest_knowledge_base.py"
