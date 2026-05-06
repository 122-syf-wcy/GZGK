#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_ML_DIR="${LOCAL_ML_DIR:-$PROJECT_DIR/ml-service}"
REMOTE_HOST="${REMOTE_HOST:-}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_DIR="${REMOTE_DIR:-/opt/gzly/ml-service}"
SERVICE_NAME="${SERVICE_NAME:-gzly-ml}"
REMOTE_PORT="${REMOTE_PORT:-8091}"
SSH_STRICT_HOST_KEY_CHECKING="${SSH_STRICT_HOST_KEY_CHECKING:-accept-new}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Deploy GZLY ML FastAPI service to production.

Required:
  REMOTE_HOST=39.97.232.141

Optional:
  REMOTE_USER=root
  LOCAL_ML_DIR=/path/to/ml-service
  REMOTE_DIR=/opt/gzly/ml-service
  SERVICE_NAME=gzly-ml
  REMOTE_PORT=8091

Example:
  REMOTE_HOST=39.97.232.141 bash scripts/server/deploy_ml_service.sh
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -n "$REMOTE_HOST" ]] || { usage >&2; die "REMOTE_HOST is required"; }
[[ -d "$LOCAL_ML_DIR/app" ]] || die "LOCAL_ML_DIR is not a valid ml-service directory: $LOCAL_ML_DIR"

for cmd in ssh rsync; do
  command -v "$cmd" >/dev/null 2>&1 || die "Missing command: $cmd"
done

remote="${REMOTE_USER}@${REMOTE_HOST}"

log "Syncing ML service to $remote:$REMOTE_DIR"
rsync -az --delete \
  --exclude '.venv/' \
  --exclude '__pycache__/' \
  --exclude '.pytest_cache/' \
  --exclude '*.pyc' \
  -e "ssh -o StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" \
  "$LOCAL_ML_DIR/" "$remote:$REMOTE_DIR/"

log "Installing dependencies and systemd unit"
ssh -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$remote" \
  "REMOTE_DIR='$REMOTE_DIR' SERVICE_NAME='$SERVICE_NAME' REMOTE_PORT='$REMOTE_PORT' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

PYTHON_BIN=""
for candidate in python3.11 python3.10 python3.9 python3; do
  if command -v "$candidate" >/dev/null 2>&1; then
    version="$($candidate - <<'PY'
import sys
print(f"{sys.version_info.major}.{sys.version_info.minor}")
PY
)"
    case "$version" in
      3.9|3.10|3.11|3.12|3.13|3.14) PYTHON_BIN="$(command -v "$candidate")"; break ;;
    esac
  fi
done
[[ -n "$PYTHON_BIN" ]] || die "python >= 3.9 not found"
command -v systemctl >/dev/null 2>&1 || die "systemctl not found"
command -v curl >/dev/null 2>&1 || die "curl not found"

mkdir -p "$REMOTE_DIR"
cd "$REMOTE_DIR"

if [[ -x .venv/bin/python ]]; then
  venv_version="$(.venv/bin/python - <<'PY'
import sys
print(f"{sys.version_info.major}.{sys.version_info.minor}")
PY
)"
  case "$venv_version" in
    3.9|3.10|3.11|3.12|3.13|3.14) ;;
    *) log "Removing old virtualenv with Python $venv_version"; rm -rf .venv ;;
  esac
fi

if [[ ! -d .venv ]]; then
  log "Creating virtualenv with $PYTHON_BIN"
  "$PYTHON_BIN" -m venv .venv
fi

log "Installing Python dependencies"
.venv/bin/python -m pip install --upgrade pip >/dev/null
.venv/bin/pip install -r requirements.txt >/dev/null

cat >"/etc/systemd/system/${SERVICE_NAME}.service" <<UNIT
[Unit]
Description=GZLY ML Service
After=network.target

[Service]
Type=simple
WorkingDirectory=${REMOTE_DIR}
Environment=PYTHONUNBUFFERED=1
ExecStart=${REMOTE_DIR}/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port ${REMOTE_PORT}
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT

log "Starting ${SERVICE_NAME}"
systemctl daemon-reload
systemctl enable "$SERVICE_NAME" >/dev/null
systemctl restart "$SERVICE_NAME"

for i in $(seq 1 30); do
  if systemctl is-active --quiet "$SERVICE_NAME" && curl -fsS --max-time 3 "http://127.0.0.1:${REMOTE_PORT}/ml/health" >/dev/null; then
    log "ML health check passed"
    exit 0
  fi
  sleep 2
done

journalctl -u "$SERVICE_NAME" -n 80 --no-pager || true
die "ML service failed health check"
REMOTE_SCRIPT

log "ML deploy completed successfully"
