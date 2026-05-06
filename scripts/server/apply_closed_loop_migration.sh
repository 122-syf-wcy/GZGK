#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_FILE="${SQL_FILE:-$PROJECT_DIR/gzly-server/src/main/resources/db/20260430_volunteer_closed_loop.sql}"
REMOTE_HOST="${REMOTE_HOST:-}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_DB="${REMOTE_DB:-gzly}"
SERVICE_NAME="${SERVICE_NAME:-gzly}"
SSH_STRICT_HOST_KEY_CHECKING="${SSH_STRICT_HOST_KEY_CHECKING:-accept-new}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Backup production DB and apply the idempotent volunteer closed-loop migration.

Required:
  REMOTE_HOST=39.97.232.141

Optional:
  REMOTE_DB=gzly
  SERVICE_NAME=gzly
  SQL_FILE=/path/to/20260430_volunteer_closed_loop.sql

The remote script reads DB credentials from systemd environment first, then
/opt/gzly/backend/gzly.env when present.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -n "$REMOTE_HOST" ]] || { usage >&2; die "REMOTE_HOST is required"; }
[[ -f "$SQL_FILE" ]] || die "SQL_FILE not found: $SQL_FILE"

for cmd in ssh scp; do
  command -v "$cmd" >/dev/null 2>&1 || die "Missing command: $cmd"
done

remote="${REMOTE_USER}@${REMOTE_HOST}"
remote_sql="/tmp/$(basename "$SQL_FILE").$$.sql"

log "Uploading migration SQL"
scp -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$SQL_FILE" "$remote:$remote_sql"

log "Backing up and applying migration on remote"
ssh -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$remote" \
  "REMOTE_SQL='$remote_sql' REMOTE_DB='$REMOTE_DB' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

cleanup() { rm -f "$REMOTE_SQL"; }
trap cleanup EXIT

command -v mysql >/dev/null 2>&1 || die "mysql client not found"
command -v mysqldump >/dev/null 2>&1 || die "mysqldump not found"

for env_file in /etc/gzly/gzly.env /opt/gzly/backend/gzly.env; do
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
done

systemd_env="$(systemctl show "$SERVICE_NAME" -p Environment --value 2>/dev/null || true)"
db_user="${GZLY_DB_USERNAME:-${MYSQL_USER:-root}}"
db_pass="${GZLY_DB_PASSWORD:-${MYSQL_PASSWORD:-}}"
db_name="${GZLY_DB_NAME:-$REMOTE_DB}"

if [[ -n "$systemd_env" ]]; then
  for token in $systemd_env; do
    case "$token" in
      GZLY_DB_USERNAME=*) db_user="${token#GZLY_DB_USERNAME=}" ;;
      GZLY_DB_PASSWORD=*) db_pass="${token#GZLY_DB_PASSWORD=}" ;;
      GZLY_DB_NAME=*) db_name="${token#GZLY_DB_NAME=}" ;;
    esac
  done
fi

mkdir -p /opt/gzly/backups
backup="/opt/gzly/backups/${db_name}_$(date '+%Y%m%d%H%M%S').sql"
mysql_args=(-u"$db_user")
if [[ -n "$db_pass" ]]; then
  mysql_args+=(-p"$db_pass")
fi

log "Backing up database $db_name to $backup"
mysqldump "${mysql_args[@]}" "$db_name" > "$backup"

log "Applying migration $REMOTE_SQL"
mysql "${mysql_args[@]}" "$db_name" < "$REMOTE_SQL"

log "Migration completed"
REMOTE_SCRIPT
