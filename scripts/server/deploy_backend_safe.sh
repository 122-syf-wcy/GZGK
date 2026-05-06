#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_SERVER_DIR="${LOCAL_SERVER_DIR:-$PROJECT_DIR/gzly-server}"
LOCAL_JAR="${LOCAL_JAR:-$LOCAL_SERVER_DIR/target/gzly-server-1.0.0.jar}"

REMOTE_HOST="${REMOTE_HOST:-}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_JAR="${REMOTE_JAR:-/opt/gzly/backend/app.jar}"
SERVICE_NAME="${SERVICE_NAME:-gzly}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8090/api/volunteer/metrics}"
RUN_TESTS="${RUN_TESTS:-1}"
SSH_STRICT_HOST_KEY_CHECKING="${SSH_STRICT_HOST_KEY_CHECKING:-accept-new}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Safe backend deploy for GZLY.

Required environment:
  REMOTE_HOST=server-ip-or-host

Optional environment:
  REMOTE_USER=root
  REMOTE_JAR=/opt/gzly/backend/app.jar
  SERVICE_NAME=gzly
  HEALTH_URL=http://127.0.0.1:8090/api/volunteer/metrics
  RUN_TESTS=1
  LOCAL_SERVER_DIR=/path/to/gzly-server
  LOCAL_JAR=/path/to/app.jar

Example:
  REMOTE_HOST=39.97.232.141 bash scripts/server/deploy_backend_safe.sh
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -n "$REMOTE_HOST" ]] || { usage >&2; die "REMOTE_HOST is required"; }

for cmd in ssh scp; do
  command -v "$cmd" >/dev/null 2>&1 || die "Missing command: $cmd"
done

[[ -d "$LOCAL_SERVER_DIR" ]] || die "LOCAL_SERVER_DIR does not exist: $LOCAL_SERVER_DIR"
[[ -x "$LOCAL_SERVER_DIR/mvnw" ]] || die "Maven wrapper is not executable: $LOCAL_SERVER_DIR/mvnw"

log "Building backend: RUN_TESTS=$RUN_TESTS"
(
  cd "$LOCAL_SERVER_DIR"
  if [[ "$RUN_TESTS" == "1" ]]; then
    ./mvnw -q clean package
  else
    ./mvnw -q clean package -DskipTests
  fi
)

[[ -f "$LOCAL_JAR" ]] || die "Build artifact not found: $LOCAL_JAR"

timestamp="$(date '+%Y%m%d%H%M%S')"
remote_tmp="/tmp/gzly-app-${timestamp}.jar"
remote="${REMOTE_USER}@${REMOTE_HOST}"

log "Uploading JAR to $remote:$remote_tmp"
scp -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$LOCAL_JAR" "$remote:$remote_tmp"

log "Installing JAR on remote host with backup and health check"
ssh -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$remote" \
  "REMOTE_TMP='$remote_tmp' REMOTE_JAR='$REMOTE_JAR' SERVICE_NAME='$SERVICE_NAME' HEALTH_URL='$HEALTH_URL' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -f "$REMOTE_TMP" ]] || die "Uploaded JAR missing: $REMOTE_TMP"
command -v systemctl >/dev/null 2>&1 || die "systemctl not found"
command -v curl >/dev/null 2>&1 || die "curl not found"

remote_dir="$(dirname "$REMOTE_JAR")"
backup_dir="$remote_dir/backup"
install_tmp="$remote_dir/.app.jar.next"
backup_file=""

cleanup() {
  rm -f "$REMOTE_TMP" "$install_tmp"
}
trap cleanup EXIT

mkdir -p "$remote_dir" "$backup_dir"

if [[ -f "$REMOTE_JAR" ]]; then
  backup_file="$backup_dir/app.jar.$(date '+%Y%m%d%H%M%S').bak"
  log "Backing up current JAR to $backup_file"
  cp -p "$REMOTE_JAR" "$backup_file"
fi

rollback() {
  if [[ -n "$backup_file" && -f "$backup_file" ]]; then
    log "Rolling back to $backup_file"
    cp -p "$backup_file" "$REMOTE_JAR"
    systemctl restart "$SERVICE_NAME"
  fi
}

log "Stopping service: $SERVICE_NAME"
systemctl stop "$SERVICE_NAME"

log "Replacing JAR atomically"
install -m 0644 "$REMOTE_TMP" "$install_tmp"
mv -f "$install_tmp" "$REMOTE_JAR"

log "Starting service: $SERVICE_NAME"
systemctl start "$SERVICE_NAME"

for i in $(seq 1 30); do
  if systemctl is-active --quiet "$SERVICE_NAME" && curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null; then
    log "Health check passed: $HEALTH_URL"
    exit 0
  fi
  sleep 2
done

log "Health check failed, collecting recent logs"
journalctl -u "$SERVICE_NAME" -n 80 --no-pager || true
rollback
die "Deployment failed health check"
REMOTE_SCRIPT

log "Deploy completed successfully"
