#!/usr/bin/env bash
# Syncs infra/, contracts/, and PG ETL scripts to /opt/gzgk-v2 on the remote server
# without overwriting remote secrets (.env).
#
# Usage:
#   REMOTE_HOST=my-server bash scripts/deploy/sync_infra.sh [core|gov|obs]

set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:?Set REMOTE_HOST (SSH alias or user@ip)}"
SSH_PORT="${SSH_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/gzgk-v2}"
PROFILE="${1:-core}"

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

echo "[deploy] Ensuring ${REMOTE_DIR} exists on ${REMOTE_HOST}..."
ssh -p "${SSH_PORT}" "${REMOTE_HOST}" "mkdir -p ${REMOTE_DIR}/{infra,contracts,scripts}"

echo "[deploy] Syncing infra/ and contracts/ (excluding .env)..."
rsync -az --delete \
  --exclude='.env' \
  -e "ssh -p ${SSH_PORT}" \
  "${ROOT_DIR}/infra/" "${REMOTE_HOST}:${REMOTE_DIR}/infra/"

rsync -az --delete \
  -e "ssh -p ${SSH_PORT}" \
  "${ROOT_DIR}/contracts/" "${REMOTE_HOST}:${REMOTE_DIR}/contracts/"

echo "[deploy] Checking remote .env and starting docker compose profile '${PROFILE}'..."
ssh -p "${SSH_PORT}" "${REMOTE_HOST}" bash -s "${REMOTE_DIR}" "${PROFILE}" <<'EOF'
set -euo pipefail
REMOTE_DIR="$1"
PROFILE="$2"
cd "${REMOTE_DIR}/infra"

if [ ! -f .env ]; then
  echo "[deploy] ERROR: ${REMOTE_DIR}/infra/.env not found. Copy .env.example and set strong passwords first."
  exit 1
fi

docker compose --env-file .env --profile "${PROFILE}" up -d
docker compose --env-file .env --profile "${PROFILE}" ps
EOF
