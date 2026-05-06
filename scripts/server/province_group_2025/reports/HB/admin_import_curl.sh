#!/usr/bin/env bash
set -Eeuo pipefail
: "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"
ADMIN_BASE="${ADMIN_BASE:-http://127.0.0.1:8090/api/admin/province-data/HB}"

: "${CONFIRM_PROVINCE_IMPORT:?Set CONFIRM_PROVINCE_IMPORT=HB_2025_REVIEWED after dry-run rejected=0}"
if [[ "$CONFIRM_PROVINCE_IMPORT" != "HB_2025_REVIEWED" ]]; then
  echo "Refuse import: CONFIRM_PROVINCE_IMPORT must be HB_2025_REVIEWED" >&2
  exit 1
fi
