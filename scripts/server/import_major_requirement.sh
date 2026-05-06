#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="${WORK_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
INPUT_CSV="${INPUT_CSV:-$WORK_DIR/data/major_requirements_gz.csv}"
OUTPUT_SQL="${OUTPUT_SQL:-$WORK_DIR/data/export/major_requirements_gz.sql}"
DB_NAME="${DB_NAME:-gzly}"
DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_PASS="${DB_PASS:-}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -n "$DB_PASS" ]] || die "DB_PASS is required"
[[ -f "$INPUT_CSV" ]] || die "INPUT_CSV does not exist: $INPUT_CSV"

cd "$WORK_DIR"
if [[ -f "venv/bin/activate" ]]; then
  # shellcheck disable=SC1091
  source "venv/bin/activate"
fi

log "Building major requirement SQL from $INPUT_CSV"
python3 "server/build_major_requirement_import.py" --input-csv "$INPUT_CSV" --output-sql "$OUTPUT_SQL"

log "Importing major requirements into $DB_NAME"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$OUTPUT_SQL"

log "Imported rows:"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "
  SELECT year, subject_type, COUNT(*), SUM(resubject_requirement = '' OR resubject_requirement = '不限')
  FROM data_major_requirement_gz
  GROUP BY year, subject_type
  ORDER BY year DESC, subject_type;
"
