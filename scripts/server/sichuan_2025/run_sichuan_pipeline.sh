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
Usage: ./run_sichuan_pipeline.sh <command> [args...]

Commands:
  baseline             Print current SC row counts and source status.
  official             Crawl SCEEA official sources and download child images/files.
  schools              Crawl a safe school-site sample, default --limit 20.
  review-queue         Classify crawled school candidates for manual review.
  ocr-score-rank       Extract official score-rank image drafts with local Tesseract.
  sicau-official       Extract Sichuan Agricultural University official API drafts.
  cuit-official        Extract Chengdu University of Information Technology official drafts.
  assemble-official    Assemble verified official drafts into reviewed CSVs.
  extract              Run model extraction for downloaded official files.
  normalize            Normalize draft outputs and create reviewed templates.
  validate-reviewed    Validate manually reviewed CSVs before payload generation.
  payloads             Build admin dry-run payloads from reviewed CSVs.

Environment:
  GZLY_ENV_FILE        Default /etc/gzly/gzly.env
  PYTHON_BIN           Default python3.11, then /root/gzly_scraper/venv/bin/python, then python3
  VISION_BASE_URL      OpenAI-compatible base URL for extract
  VISION_API_KEY       API key for extract
  VISION_MODEL         Vision model name
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
    mysql -N -B -u"${GZLY_DB_USERNAME:-root}" gzly <<'SQL'
SELECT 'data_score_rank_SC', year, subject_type, COUNT(*) FROM data_score_rank WHERE province_code='SC' GROUP BY year, subject_type ORDER BY year, subject_type;
SELECT 'data_admission_group_line_SC', year, subject_type, COUNT(*) FROM data_admission_group_line WHERE province_code='SC' GROUP BY year, subject_type ORDER BY year, subject_type;
SELECT 'data_admission_group_plan_SC', year, subject_type, COUNT(*) FROM data_admission_group_plan WHERE province_code='SC' GROUP BY year, subject_type ORDER BY year, subject_type;
SELECT 'data_source_registry_SC', data_type, year, subject_type, status, COUNT(*), COALESCE(SUM(row_count),0) FROM data_source_registry WHERE province_code='SC' GROUP BY data_type, year, subject_type, status ORDER BY data_type, year, subject_type, status;
SELECT 'official_link_coverage', COUNT(*), SUM(school_site<>''), SUM(admission_site<>''), SUM(admission_brochure_url<>''), SUM(major_catalog_url<>'') FROM uni_official_link;
SQL
    ;;
  official)
    "$python_bin" crawl_sichuan_sources.py --download-children "$@"
    ;;
  schools)
    "$python_bin" crawl_school_sources.py --env-file "$env_file" "$@"
    ;;
  review-queue)
    "$python_bin" build_school_review_queue.py "$@"
    ;;
  ocr-score-rank)
    "$python_bin" extract_score_rank_with_tesseract.py "$@"
    ;;
  sicau-official)
    "$python_bin" extract_sicau_official_api.py "$@"
    ;;
  cuit-official)
    "$python_bin" extract_cuit_official_pages.py "$@"
    ;;
  assemble-official)
    "$python_bin" assemble_official_reviewed.py "$@"
    ;;
  extract)
    if [[ -f "$vision_env_file" ]]; then
      set -a
      # shellcheck disable=SC1090
      source "$vision_env_file"
      set +a
    fi
    "$python_bin" extract_with_vision.py "$@"
    ;;
  normalize)
    "$python_bin" normalize_sichuan_data.py "$@"
    ;;
  validate-reviewed)
    "$python_bin" validate_reviewed_payloads.py "$@"
    ;;
  payloads)
    "$python_bin" validate_reviewed_payloads.py "$@"
    "$python_bin" build_admin_payloads.py --write-curl-script
    ;;
  *)
    echo "Unknown command: $command" >&2
    usage >&2
    exit 1
    ;;
esac
