#!/usr/bin/env bash
set -Eeuo pipefail

WORK_DIR="${WORK_DIR:-/root/gzly_scraper}"
SCOPE="${SCOPE:-official-missing}"
LIMIT="${LIMIT:-0}"
WORKERS="${WORKERS:-3}"
DELAY="${DELAY:-1.2}"
CHECKPOINT_EVERY="${CHECKPOINT_EVERY:-20}"
PROXY_LIST="${SCRAPER_PROXY_LIST:-${1:-http://127.0.0.1:7890}}"
RUN_IMPORTER="${RUN_IMPORTER:-0}"
CLEAR_CHECKPOINT="${CLEAR_CHECKPOINT:-1}"
BACKGROUND_CRAWLER="${BACKGROUND_CRAWLER:-0}"
STOP_OLD_CRAWLER="${STOP_OLD_CRAWLER:-1}"
LOCK_FILE="${LOCK_FILE:-/tmp/gzly-data-gap-supplement.lock}"
DB_NAME="${DB_NAME:-gzly}"
DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_PASS="${DB_PASS:-}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -d "$WORK_DIR" ]] || die "WORK_DIR does not exist: $WORK_DIR"
[[ -n "$DB_PASS" ]] || die "DB_PASS is required"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  die "another data gap supplement task is already running: $LOCK_FILE"
fi

cd "$WORK_DIR"
[[ -f "venv/bin/activate" ]] || die "Python venv missing: $WORK_DIR/venv"
source venv/bin/activate

mkdir -p data/official_links

seed_file="data/official_links/gap_seed_${SCOPE}_$(date '+%Y%m%d%H%M%S').json"
log "Building crawler seed: scope=$SCOPE limit=$LIMIT"
python3 server/build_gap_seed.py \
  --scope "$SCOPE" \
  --limit "$LIMIT" \
  --output "$seed_file" \
  --db-name "$DB_NAME" \
  --db-user "$DB_USER" \
  --db-host "$DB_HOST" \
  --db-port "$DB_PORT" \
  --db-pass "$DB_PASS"

seed_count="$(python3 - "$seed_file" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
print(len(json.loads(path.read_text(encoding="utf-8"))))
PY
)"

if [[ "$seed_count" == "0" ]]; then
  log "No data gaps found for scope=$SCOPE. Nothing to crawl."
  exit 0
fi

if [[ "$STOP_OLD_CRAWLER" == "1" ]]; then
  log "Stopping old official-link crawler jobs"
  pgrep -f 'scrape_official_links.py' | xargs -r kill -TERM || true
fi

clear_args=()
if [[ "$CLEAR_CHECKPOINT" == "1" ]]; then
  clear_args+=(--clear-checkpoint)
fi

log "Starting crawler: seed_count=$seed_count workers=$WORKERS delay=$DELAY"
crawler_cmd=(
  python3 -u scrape_official_links.py
  --seed-file "$seed_file"
  --workers "$WORKERS"
  --delay "$DELAY"
  --checkpoint-every "$CHECKPOINT_EVERY"
  --proxy "$PROXY_LIST"
  "${clear_args[@]}"
)

if [[ "$BACKGROUND_CRAWLER" == "1" ]]; then
  nohup "${crawler_cmd[@]}" > "data/official_links/data_gap_${SCOPE}.log" 2>&1 < /dev/null &
  log "SCRAPER_PID:$!"
else
  "${crawler_cmd[@]}" 2>&1 | tee "data/official_links/data_gap_${SCOPE}.log"
fi

if [[ "$RUN_IMPORTER" == "1" ]]; then
  if command -v systemctl >/dev/null 2>&1 && systemctl cat gzly-official-importer.service >/dev/null 2>&1; then
    log "Ensuring systemd importer is running"
    systemctl start gzly-official-importer.service
  else
    log "Starting importer loop in background because systemd unit is unavailable"
    DB_NAME="$DB_NAME" DB_USER="$DB_USER" DB_PASS="$DB_PASS" \
      nohup bash server/import_official_links.sh \
      > "data/official_links/data_gap_${SCOPE}_import.log" 2>&1 < /dev/null &
    log "IMPORTER_PID:$!"
  fi
else
  log "Importer disabled."
fi
