#!/usr/bin/env bash
set -Eeuo pipefail
: "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"
ADMIN_BASE="${ADMIN_BASE:-http://127.0.0.1:8090/api/admin/sichuan-data}"

: "${CONFIRM_SICHUAN_IMPORT:?Set CONFIRM_SICHUAN_IMPORT=SC_2025_REVIEWED after dry-run rejected=0}"
if [[ "$CONFIRM_SICHUAN_IMPORT" != "SC_2025_REVIEWED" ]]; then
  echo "Refuse import: CONFIRM_SICHUAN_IMPORT must be SC_2025_REVIEWED" >&2
  exit 1
fi

echo 'import group-lines group-lines-001.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-001.json | python3 -m json.tool

echo 'import group-lines group-lines-002.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-002.json | python3 -m json.tool

echo 'import group-lines group-lines-003.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-003.json | python3 -m json.tool

echo 'import group-plans group-plans-001.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-001.json | python3 -m json.tool

echo 'import group-plans group-plans-002.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-002.json | python3 -m json.tool

echo 'import group-plans group-plans-003.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-003.json | python3 -m json.tool
