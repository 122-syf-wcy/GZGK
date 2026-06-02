#!/usr/bin/env bash
set -euo pipefail

# GZLY 2026 retrain plan skeleton.
# Default mode is dry-run. It does not train, register, activate, update readiness, restart services, or write production DB.

DRY_RUN=1
CONFIRM_TRAIN=0
CONFIRM_REGISTER=0
CONFIRM_ACTIVATE=0
ML_DIR="${ML_DIR:-/opt/gzly/ml-service}"
WORK_DIR="${WORK_DIR:-/tmp/gzly_2026_retrain_plan}"
TRAIN_YEARS="2024,2025,2026"
MIN_ROWS="${MIN_ROWS:-1000}"

usage() {
  cat <<'USAGE'
Usage:
  retrain_2026_plan.sh [--execute-train] [--register-draft] [--activate]

Default:
  Dry-run only. Prints commands and required gates.

Guards:
  --execute-train   allows local/offline training command generation/execution
  --register-draft  allows draft registration only; never active by default
  --activate        requires --execute-train and --register-draft; still must be run manually after offline report approval
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --execute-train)
      DRY_RUN=0
      CONFIRM_TRAIN=1
      shift
      ;;
    --register-draft)
      CONFIRM_REGISTER=1
      shift
      ;;
    --activate)
      CONFIRM_ACTIVATE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$CONFIRM_ACTIVATE" == "1" ]]; then
  echo "ERROR: activation is intentionally not implemented in this skeleton." >&2
  echo "Use admin API activation only after offline_backtest_report.md is approved and rollback target is recorded." >&2
  exit 3
fi

mkdir -p "$WORK_DIR"
CSV="$WORK_DIR/training_rank_2024_2026.csv"
REPORT="$WORK_DIR/retrain_2026_quality_report.md"
FEATURE_REPORT="$WORK_DIR/feature_quality_report.md"
PLAN_TREND_REPORT="$WORK_DIR/plan_trend_evaluation_report.md"

cat > "$REPORT" <<REPORT
# GZLY 2026 Retrain Quality Report Skeleton

- trainingYears: $TRAIN_YEARS
- dryRun: $DRY_RUN
- boundary: no fake data, no production activation, no readiness update, no service restart.

## Required Gates

- [ ] 2026 official score segment ready.
- [ ] 2026 official admission plan ready.
- [ ] 2026 policy rules confirmed.
- [ ] 2026 major requirements ready.
- [ ] 2026 major meta/restrictions ready.
- [ ] Training CSV rows >= $MIN_ROWS.
- [ ] Critical feature completeness checked in $FEATURE_REPORT.
- [ ] rank model metrics do not regress.
- [ ] chance model metrics do not regress.
- [ ] plan trend evaluation report generated at $PLAN_TREND_REPORT and does not regress or remains rule-only.
- [ ] New models registered as draft only.
- [ ] Old active model versions recorded for rollback.
- [ ] data_year_readiness is not updated until activation is approved.
REPORT

echo "[plan] wrote $REPORT"

echo "[plan] training CSV target: $CSV"
echo "[plan] training years: $TRAIN_YEARS"

BUILD_CMD=(
  python scripts/build_training_csv.py
  --output "$CSV"
  --train-years "$TRAIN_YEARS"
  --require-train-years
  --quality-report "$FEATURE_REPORT"
  --min-rows "$MIN_ROWS"
  --strict
)
TRAIN_CMD=(
  python -m scripts.train_models_on_server
  --models rank chance plan-trend
  --csv "$CSV"
  --output-dir "$WORK_DIR/models"
  --train-year-range "$TRAIN_YEARS"
  --quality-report "$FEATURE_REPORT"
  --require-train-years
  --plan-trend-report "$PLAN_TREND_REPORT"
  --status draft
)
if [[ "$CONFIRM_REGISTER" == "1" ]]; then
  TRAIN_CMD+=(--register)
fi

if [[ "$DRY_RUN" == "1" || "$CONFIRM_TRAIN" != "1" ]]; then
  printf '[dry-run] cd %q && ' "$ML_DIR"
  printf '%q ' "${BUILD_CMD[@]}"
  printf '\n'
  printf '[dry-run] cd %q && ' "$ML_DIR"
  printf '%q ' "${TRAIN_CMD[@]}"
  printf '\n'
  echo "[dry-run] no command executed."
  exit 0
fi

cd "$ML_DIR"
"${BUILD_CMD[@]}"
"${TRAIN_CMD[@]}"

echo "[done] draft training flow finished. Review $REPORT and generated metrics before any activation."
