#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd "$SCRIPT_DIR"

if [[ -n "${PYTHON_BIN:-}" ]]; then
  python_bin="$PYTHON_BIN"
elif command -v python3.11 >/dev/null 2>&1; then
  python_bin="$(command -v python3.11)"
elif [[ -x /root/gzly_scraper/venv/bin/python ]]; then
  python_bin="/root/gzly_scraper/venv/bin/python"
else
  python_bin="$(command -v python3)"
fi

usage() {
  cat <<'EOF'
Usage: ./run_province_group_pipeline.sh <command> [args...]

Commands:
  baseline     Print SC/HB/AH imported row counts.
  official     Crawl official/provincial source pages.
  schools      Crawl school official-site candidates.
  ocr-score-rank  OCR official score-rank images into draft CSV.
  grid-score-rank  Grid/cell OCR official score-rank images into draft CSV.
  score-rank-review-queue  Build manual review queue from OCR drafts.
  vision-group-lines  Extract official group-line images into draft review queue.
  init-reviewed  Create empty reviewed CSV templates.
  sync-sichuan-reviewed  Copy legacy sichuan_2025 reviewed CSVs into SC templates.
  validate-reviewed  Validate manually reviewed CSVs.
  payloads     Validate reviewed CSVs and build admin dry-run payloads.
  all          Run official and schools in one pass.

Common args:
  --province SC|HB|AH|ALL|SC,HB,AH
  --limit 80
  --workers 4
  --max-pages-per-school 6

Environment:
  GZLY_ENV_FILE        Default /etc/gzly/gzly.env
  PYTHON_BIN           Default python3.11, venv python, then python3

Collection commands write only raw/ and reports/. Reviewed/payload commands
only use manually reviewed CSVs and never write production tables.
EOF
}

env_file="${GZLY_ENV_FILE:-/etc/gzly/gzly.env}"
vision_env_file="${GZLY_VISION_ENV_FILE:-/etc/gzly/vision.env}"
command="${1:-}"
if [[ -z "$command" || "$command" == "-h" || "$command" == "--help" ]]; then
  usage
  exit 0
fi
shift || true

case "$command" in
  baseline)
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
    export MYSQL_PWD="${GZLY_DB_PASSWORD:-}"
    mysql -N -B -h"${GZLY_DB_HOST:-127.0.0.1}" -P"${GZLY_DB_PORT:-3306}" -u"${GZLY_DB_USERNAME:-root}" "${GZLY_DB_NAME:-gzly}" <<'SQL'
SELECT 'data_score_rank', province_code, year, subject_type, COUNT(*) FROM data_score_rank WHERE province_code IN ('SC','HB','AH') GROUP BY province_code, year, subject_type ORDER BY province_code, year, subject_type;
SELECT 'data_admission_group_line', province_code, year, subject_type, COUNT(*) FROM data_admission_group_line WHERE province_code IN ('SC','HB','AH') GROUP BY province_code, year, subject_type ORDER BY province_code, year, subject_type;
SELECT 'data_admission_group_plan', province_code, year, subject_type, COUNT(*) FROM data_admission_group_plan WHERE province_code IN ('SC','HB','AH') GROUP BY province_code, year, subject_type ORDER BY province_code, year, subject_type;
SELECT 'data_source_registry', province_code, data_type, year, subject_type, status, COUNT(*), COALESCE(SUM(row_count),0) FROM data_source_registry WHERE province_code IN ('SC','HB','AH') GROUP BY province_code, data_type, year, subject_type, status ORDER BY province_code, data_type, year, subject_type, status;
SQL
    ;;
  official|schools|all)
    "$python_bin" crawl_province_group_sources.py "$command" --env-file "$env_file" "$@"
    ;;
  ocr-score-rank)
    "$python_bin" extract_score_rank_with_tesseract.py "$@"
    ;;
  grid-score-rank)
    "$python_bin" extract_score_rank_grid.py "$@"
    ;;
  score-rank-review-queue)
    "$python_bin" build_score_rank_review_queue.py "$@"
    ;;
  vision-group-lines)
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    if [[ -f "$vision_env_file" ]]; then
      # shellcheck disable=SC1090
      source "$vision_env_file"
    fi
    set +a
    "$python_bin" extract_group_lines_with_vision.py "$@"
    ;;
  init-reviewed|sync-sichuan-reviewed|validate-reviewed|payloads)
    "$python_bin" reviewed_payloads.py "$command" "$@"
    ;;
  *)
    echo "Unknown command: $command" >&2
    usage >&2
    exit 1
    ;;
esac
