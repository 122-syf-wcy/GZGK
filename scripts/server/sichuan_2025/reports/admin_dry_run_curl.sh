#!/usr/bin/env bash
set -Eeuo pipefail
: "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"
ADMIN_BASE="${ADMIN_BASE:-http://127.0.0.1:8090/api/admin/sichuan-data}"

echo 'dry-run group-lines group-lines-001.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-001.json | python3 -m json.tool

echo 'dry-run group-lines group-lines-002.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-002.json | python3 -m json.tool

echo 'dry-run group-lines group-lines-003.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-lines-003.json | python3 -m json.tool

echo 'dry-run group-plans group-plans-001.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-001.json | python3 -m json.tool

echo 'dry-run group-plans group-plans-002.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-002.json | python3 -m json.tool

echo 'dry-run group-plans group-plans-003.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/server/sichuan_2025/payloads/group-plans-003.json | python3 -m json.tool
