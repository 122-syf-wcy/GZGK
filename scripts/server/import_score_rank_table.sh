#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="${WORK_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
DB_NAME="${DB_NAME:-gzly}"
DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_PASS="${DB_PASS:-}"
YEAR="${YEAR:-2025}"
YEARS="${YEARS:-}"
SKIP_DOWNLOAD="${SKIP_DOWNLOAD:-0}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -n "$DB_PASS" ]] || die "DB_PASS is required"
[[ -d "$WORK_DIR" ]] || die "WORK_DIR does not exist: $WORK_DIR"

cd "$WORK_DIR"
if [[ -f "venv/bin/activate" ]]; then
  # shellcheck disable=SC1091
  source "venv/bin/activate"
fi

args=(--raw-dir "data/raw" --output-csv "data/export/score_rank_gz.csv" --output-sql "data/export/score_rank_gz.sql")
if [[ -n "$YEARS" ]]; then
  args+=(--years "$YEARS")
else
  args+=(--year "$YEAR")
fi
if [[ "$SKIP_DOWNLOAD" == "1" ]]; then
  args+=(--skip-download)
fi

log "Building official score-rank import files"
python3 "server/build_score_rank_table.py" "${args[@]}"

ensure_column() {
  local column="$1"
  local ddl="$2"
  local exists
  exists="$(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'data_score_rank_gz'
      AND column_name = '$column';
  ")"
  if [[ "$exists" == "0" ]]; then
    log "Adding data_score_rank_gz.$column"
    mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
      ALTER TABLE data_score_rank_gz ADD COLUMN $ddl;
    "
  fi
}

ensure_column "source_page_url" "\`source_page_url\` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面' AFTER \`source_url\`"
ensure_column "parse_method" "\`parse_method\` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式' AFTER \`source_file\`"

log "Importing official score-rank table into $DB_NAME"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "data/export/score_rank_gz.sql"

log "Imported rows:"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "
  SELECT year, subject_type, COUNT(*), MIN(score), MAX(score), MAX(cumulative_count)
  FROM data_score_rank_gz
  GROUP BY year, subject_type
  ORDER BY year DESC, subject_type;
"
