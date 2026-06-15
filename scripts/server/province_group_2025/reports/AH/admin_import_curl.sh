#!/usr/bin/env bash
set -Eeuo pipefail
: "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"
ADMIN_BASE="${ADMIN_BASE:-http://127.0.0.1:8090/api/admin/province-data/AH}"

: "${CONFIRM_PROVINCE_IMPORT:?Set CONFIRM_PROVINCE_IMPORT=AH_2025_REVIEWED after dry-run rejected=0}"
if [[ "$CONFIRM_PROVINCE_IMPORT" != "AH_2025_REVIEWED" ]]; then
  echo "Refuse import: CONFIRM_PROVINCE_IMPORT must be AH_2025_REVIEWED" >&2
  exit 1
fi

echo 'import AH group-lines group-lines-001.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-001.json | python3 -m json.tool

echo 'import AH group-lines group-lines-002.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-002.json | python3 -m json.tool

echo 'import AH group-lines group-lines-003.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-003.json | python3 -m json.tool

echo 'import AH group-lines group-lines-004.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-004.json | python3 -m json.tool

echo 'import AH group-lines group-lines-005.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-005.json | python3 -m json.tool

echo 'import AH group-lines group-lines-006.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-006.json | python3 -m json.tool

echo 'import AH group-lines group-lines-007.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-007.json | python3 -m json.tool

echo 'import AH group-lines group-lines-008.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-008.json | python3 -m json.tool

echo 'import AH group-lines group-lines-009.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-009.json | python3 -m json.tool

echo 'import AH group-lines group-lines-010.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-010.json | python3 -m json.tool

echo 'import AH group-lines group-lines-011.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-011.json | python3 -m json.tool

echo 'import AH group-lines group-lines-012.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-012.json | python3 -m json.tool

echo 'import AH group-lines group-lines-013.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-013.json | python3 -m json.tool

echo 'import AH group-lines group-lines-014.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-014.json | python3 -m json.tool

echo 'import AH group-lines group-lines-015.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-015.json | python3 -m json.tool

echo 'import AH group-lines group-lines-016.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-016.json | python3 -m json.tool

echo 'import AH group-lines group-lines-017.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-017.json | python3 -m json.tool

echo 'import AH group-lines group-lines-018.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-018.json | python3 -m json.tool

echo 'import AH group-lines group-lines-019.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-019.json | python3 -m json.tool

echo 'import AH group-lines group-lines-020.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-020.json | python3 -m json.tool

echo 'import AH group-lines group-lines-021.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-021.json | python3 -m json.tool

echo 'import AH group-lines group-lines-022.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-022.json | python3 -m json.tool

echo 'import AH group-lines group-lines-023.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-023.json | python3 -m json.tool

echo 'import AH group-lines group-lines-024.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-024.json | python3 -m json.tool

echo 'import AH group-lines group-lines-025.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-025.json | python3 -m json.tool

echo 'import AH group-lines group-lines-026.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-026.json | python3 -m json.tool

echo 'import AH group-lines group-lines-027.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-027.json | python3 -m json.tool

echo 'import AH group-lines group-lines-028.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-028.json | python3 -m json.tool

echo 'import AH group-lines group-lines-029.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-029.json | python3 -m json.tool

echo 'import AH group-lines group-lines-030.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-030.json | python3 -m json.tool

echo 'import AH group-lines group-lines-031.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-031.json | python3 -m json.tool

echo 'import AH group-lines group-lines-032.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-032.json | python3 -m json.tool

echo 'import AH group-lines group-lines-033.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-033.json | python3 -m json.tool

echo 'import AH group-lines group-lines-034.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-034.json | python3 -m json.tool

echo 'import AH group-lines group-lines-035.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-035.json | python3 -m json.tool

echo 'import AH group-lines group-lines-036.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-036.json | python3 -m json.tool

echo 'import AH group-lines group-lines-037.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-037.json | python3 -m json.tool

echo 'import AH group-lines group-lines-038.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-038.json | python3 -m json.tool

echo 'import AH group-lines group-lines-039.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-039.json | python3 -m json.tool

echo 'import AH group-lines group-lines-040.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-040.json | python3 -m json.tool

echo 'import AH group-lines group-lines-041.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-041.json | python3 -m json.tool

echo 'import AH group-lines group-lines-042.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-042.json | python3 -m json.tool

echo 'import AH group-lines group-lines-043.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-043.json | python3 -m json.tool

echo 'import AH group-lines group-lines-044.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-044.json | python3 -m json.tool

echo 'import AH group-lines group-lines-045.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-045.json | python3 -m json.tool

echo 'import AH group-lines group-lines-046.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-046.json | python3 -m json.tool

echo 'import AH group-lines group-lines-047.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-047.json | python3 -m json.tool

echo 'import AH group-lines group-lines-048.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-048.json | python3 -m json.tool

echo 'import AH group-lines group-lines-049.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-049.json | python3 -m json.tool

echo 'import AH group-lines group-lines-050.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-050.json | python3 -m json.tool

echo 'import AH group-lines group-lines-051.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-051.json | python3 -m json.tool

echo 'import AH group-lines group-lines-052.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-052.json | python3 -m json.tool

echo 'import AH group-lines group-lines-053.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-053.json | python3 -m json.tool

echo 'import AH group-lines group-lines-054.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-054.json | python3 -m json.tool

echo 'import AH group-lines group-lines-055.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-055.json | python3 -m json.tool

echo 'import AH group-lines group-lines-056.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-056.json | python3 -m json.tool

echo 'import AH group-lines group-lines-057.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-057.json | python3 -m json.tool

echo 'import AH group-lines group-lines-058.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-058.json | python3 -m json.tool

echo 'import AH group-lines group-lines-059.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-059.json | python3 -m json.tool

echo 'import AH group-lines group-lines-060.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-060.json | python3 -m json.tool

echo 'import AH group-lines group-lines-061.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-061.json | python3 -m json.tool

echo 'import AH group-lines group-lines-062.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-062.json | python3 -m json.tool

echo 'import AH group-lines group-lines-063.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-063.json | python3 -m json.tool

echo 'import AH group-lines group-lines-064.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-064.json | python3 -m json.tool

echo 'import AH group-lines group-lines-065.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-065.json | python3 -m json.tool

echo 'import AH group-lines group-lines-066.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-066.json | python3 -m json.tool

echo 'import AH group-lines group-lines-067.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-067.json | python3 -m json.tool

echo 'import AH group-lines group-lines-068.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-068.json | python3 -m json.tool

echo 'import AH group-lines group-lines-069.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-069.json | python3 -m json.tool

echo 'import AH group-lines group-lines-070.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-070.json | python3 -m json.tool

echo 'import AH group-lines group-lines-071.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-071.json | python3 -m json.tool

echo 'import AH group-lines group-lines-072.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-072.json | python3 -m json.tool

echo 'import AH group-lines group-lines-073.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-073.json | python3 -m json.tool

echo 'import AH group-lines group-lines-074.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-074.json | python3 -m json.tool

echo 'import AH group-lines group-lines-075.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-075.json | python3 -m json.tool

echo 'import AH group-lines group-lines-076.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-076.json | python3 -m json.tool

echo 'import AH group-lines group-lines-077.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-077.json | python3 -m json.tool

echo 'import AH group-lines group-lines-078.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-078.json | python3 -m json.tool

echo 'import AH group-lines group-lines-079.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-079.json | python3 -m json.tool

echo 'import AH group-lines group-lines-080.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-080.json | python3 -m json.tool

echo 'import AH group-lines group-lines-081.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-081.json | python3 -m json.tool

echo 'import AH group-lines group-lines-082.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-082.json | python3 -m json.tool

echo 'import AH group-lines group-lines-083.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-083.json | python3 -m json.tool

echo 'import AH group-lines group-lines-084.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-084.json | python3 -m json.tool

echo 'import AH group-lines group-lines-085.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-085.json | python3 -m json.tool

echo 'import AH group-lines group-lines-086.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-086.json | python3 -m json.tool

echo 'import AH group-lines group-lines-087.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-087.json | python3 -m json.tool

echo 'import AH group-lines group-lines-088.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-088.json | python3 -m json.tool

echo 'import AH group-lines group-lines-089.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-089.json | python3 -m json.tool

echo 'import AH group-lines group-lines-090.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-090.json | python3 -m json.tool

echo 'import AH group-lines group-lines-091.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-091.json | python3 -m json.tool

echo 'import AH group-lines group-lines-092.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-092.json | python3 -m json.tool

echo 'import AH group-lines group-lines-093.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-093.json | python3 -m json.tool

echo 'import AH group-lines group-lines-094.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-094.json | python3 -m json.tool

echo 'import AH group-lines group-lines-095.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-095.json | python3 -m json.tool

echo 'import AH group-lines group-lines-096.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-096.json | python3 -m json.tool

echo 'import AH group-lines group-lines-097.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-097.json | python3 -m json.tool

echo 'import AH group-lines group-lines-098.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-098.json | python3 -m json.tool

echo 'import AH group-lines group-lines-099.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-099.json | python3 -m json.tool

echo 'import AH group-lines group-lines-100.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-100.json | python3 -m json.tool

echo 'import AH group-lines group-lines-101.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-101.json | python3 -m json.tool

echo 'import AH group-lines group-lines-102.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-102.json | python3 -m json.tool

echo 'import AH group-lines group-lines-103.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-103.json | python3 -m json.tool

echo 'import AH group-lines group-lines-104.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-104.json | python3 -m json.tool

echo 'import AH group-lines group-lines-105.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-105.json | python3 -m json.tool

echo 'import AH group-lines group-lines-106.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-106.json | python3 -m json.tool

echo 'import AH group-lines group-lines-107.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-107.json | python3 -m json.tool

echo 'import AH group-lines group-lines-108.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-108.json | python3 -m json.tool

echo 'import AH group-lines group-lines-109.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-109.json | python3 -m json.tool

echo 'import AH group-lines group-lines-110.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-110.json | python3 -m json.tool

echo 'import AH group-lines group-lines-111.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-111.json | python3 -m json.tool

echo 'import AH group-lines group-lines-112.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-112.json | python3 -m json.tool

echo 'import AH group-lines group-lines-113.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-113.json | python3 -m json.tool

echo 'import AH group-lines group-lines-114.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-114.json | python3 -m json.tool

echo 'import AH group-lines group-lines-115.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-115.json | python3 -m json.tool

echo 'import AH group-lines group-lines-116.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-116.json | python3 -m json.tool

echo 'import AH group-lines group-lines-117.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-117.json | python3 -m json.tool

echo 'import AH group-lines group-lines-118.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-118.json | python3 -m json.tool

echo 'import AH group-lines group-lines-119.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-119.json | python3 -m json.tool

echo 'import AH group-lines group-lines-120.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-120.json | python3 -m json.tool

echo 'import AH group-lines group-lines-121.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-121.json | python3 -m json.tool

echo 'import AH group-lines group-lines-122.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-122.json | python3 -m json.tool

echo 'import AH group-lines group-lines-123.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-123.json | python3 -m json.tool

echo 'import AH group-lines group-lines-124.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-124.json | python3 -m json.tool

echo 'import AH group-lines group-lines-125.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-125.json | python3 -m json.tool

echo 'import AH group-lines group-lines-126.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-126.json | python3 -m json.tool

echo 'import AH group-lines group-lines-127.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-127.json | python3 -m json.tool

echo 'import AH group-lines group-lines-128.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-128.json | python3 -m json.tool

echo 'import AH group-lines group-lines-129.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-129.json | python3 -m json.tool

echo 'import AH group-lines group-lines-130.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-130.json | python3 -m json.tool

echo 'import AH group-lines group-lines-131.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-131.json | python3 -m json.tool

echo 'import AH group-lines group-lines-132.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-132.json | python3 -m json.tool

echo 'import AH group-lines group-lines-133.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-133.json | python3 -m json.tool

echo 'import AH group-lines group-lines-134.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-134.json | python3 -m json.tool

echo 'import AH group-lines group-lines-135.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-135.json | python3 -m json.tool

echo 'import AH group-lines group-lines-136.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-136.json | python3 -m json.tool

echo 'import AH group-lines group-lines-137.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-137.json | python3 -m json.tool

echo 'import AH group-lines group-lines-138.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-138.json | python3 -m json.tool

echo 'import AH group-lines group-lines-139.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-139.json | python3 -m json.tool

echo 'import AH group-lines group-lines-140.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-140.json | python3 -m json.tool

echo 'import AH group-lines group-lines-141.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-141.json | python3 -m json.tool

echo 'import AH group-lines group-lines-142.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-142.json | python3 -m json.tool

echo 'import AH group-lines group-lines-143.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-143.json | python3 -m json.tool

echo 'import AH group-lines group-lines-144.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-144.json | python3 -m json.tool

echo 'import AH group-lines group-lines-145.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-145.json | python3 -m json.tool

echo 'import AH group-lines group-lines-146.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-146.json | python3 -m json.tool

echo 'import AH group-lines group-lines-147.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-147.json | python3 -m json.tool

echo 'import AH group-lines group-lines-148.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-148.json | python3 -m json.tool

echo 'import AH group-lines group-lines-149.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-149.json | python3 -m json.tool

echo 'import AH group-lines group-lines-150.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-150.json | python3 -m json.tool

echo 'import AH group-lines group-lines-151.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-151.json | python3 -m json.tool

echo 'import AH group-lines group-lines-152.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-152.json | python3 -m json.tool

echo 'import AH group-lines group-lines-153.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-153.json | python3 -m json.tool

echo 'import AH group-lines group-lines-154.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-154.json | python3 -m json.tool

echo 'import AH group-lines group-lines-155.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-155.json | python3 -m json.tool

echo 'import AH group-lines group-lines-156.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-156.json | python3 -m json.tool

echo 'import AH group-lines group-lines-157.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-157.json | python3 -m json.tool

echo 'import AH group-lines group-lines-158.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-158.json | python3 -m json.tool

echo 'import AH group-lines group-lines-159.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-159.json | python3 -m json.tool

echo 'import AH group-lines group-lines-160.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-160.json | python3 -m json.tool

echo 'import AH group-lines group-lines-161.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-161.json | python3 -m json.tool

echo 'import AH group-lines group-lines-162.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-162.json | python3 -m json.tool

echo 'import AH group-lines group-lines-163.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-163.json | python3 -m json.tool

echo 'import AH group-lines group-lines-164.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-164.json | python3 -m json.tool

echo 'import AH group-lines group-lines-165.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-165.json | python3 -m json.tool

echo 'import AH group-lines group-lines-166.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-166.json | python3 -m json.tool

echo 'import AH group-lines group-lines-167.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-167.json | python3 -m json.tool

echo 'import AH group-lines group-lines-168.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-168.json | python3 -m json.tool

echo 'import AH group-lines group-lines-169.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-169.json | python3 -m json.tool

echo 'import AH group-lines group-lines-170.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-170.json | python3 -m json.tool

echo 'import AH group-lines group-lines-171.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-171.json | python3 -m json.tool

echo 'import AH group-lines group-lines-172.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-172.json | python3 -m json.tool

echo 'import AH group-lines group-lines-173.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-173.json | python3 -m json.tool

echo 'import AH group-lines group-lines-174.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-174.json | python3 -m json.tool

echo 'import AH group-lines group-lines-175.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-175.json | python3 -m json.tool

echo 'import AH group-lines group-lines-176.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-176.json | python3 -m json.tool

echo 'import AH group-lines group-lines-177.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-177.json | python3 -m json.tool

echo 'import AH group-lines group-lines-178.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-178.json | python3 -m json.tool

echo 'import AH group-lines group-lines-179.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-179.json | python3 -m json.tool

echo 'import AH group-lines group-lines-180.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-180.json | python3 -m json.tool

echo 'import AH group-lines group-lines-181.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-181.json | python3 -m json.tool

echo 'import AH group-lines group-lines-182.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-182.json | python3 -m json.tool

echo 'import AH group-lines group-lines-183.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-183.json | python3 -m json.tool

echo 'import AH group-lines group-lines-184.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-184.json | python3 -m json.tool

echo 'import AH group-lines group-lines-185.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-185.json | python3 -m json.tool

echo 'import AH group-lines group-lines-186.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-186.json | python3 -m json.tool

echo 'import AH group-lines group-lines-187.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-187.json | python3 -m json.tool

echo 'import AH group-lines group-lines-188.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-188.json | python3 -m json.tool

echo 'import AH group-lines group-lines-189.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-189.json | python3 -m json.tool

echo 'import AH group-lines group-lines-190.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-190.json | python3 -m json.tool

echo 'import AH group-lines group-lines-191.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-191.json | python3 -m json.tool

echo 'import AH group-lines group-lines-192.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-192.json | python3 -m json.tool

echo 'import AH group-lines group-lines-193.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-193.json | python3 -m json.tool

echo 'import AH group-lines group-lines-194.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-194.json | python3 -m json.tool

echo 'import AH group-lines group-lines-195.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-195.json | python3 -m json.tool

echo 'import AH group-lines group-lines-196.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-196.json | python3 -m json.tool

echo 'import AH group-lines group-lines-197.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-197.json | python3 -m json.tool

echo 'import AH group-lines group-lines-198.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-198.json | python3 -m json.tool

echo 'import AH group-lines group-lines-199.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-199.json | python3 -m json.tool

echo 'import AH group-lines group-lines-200.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-200.json | python3 -m json.tool

echo 'import AH group-lines group-lines-201.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-201.json | python3 -m json.tool

echo 'import AH group-lines group-lines-202.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-202.json | python3 -m json.tool

echo 'import AH group-lines group-lines-203.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-203.json | python3 -m json.tool

echo 'import AH group-lines group-lines-204.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-204.json | python3 -m json.tool

echo 'import AH group-lines group-lines-205.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-205.json | python3 -m json.tool

echo 'import AH group-lines group-lines-206.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-206.json | python3 -m json.tool

echo 'import AH group-lines group-lines-207.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-207.json | python3 -m json.tool

echo 'import AH group-lines group-lines-208.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-208.json | python3 -m json.tool

echo 'import AH group-lines group-lines-209.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-209.json | python3 -m json.tool

echo 'import AH group-lines group-lines-210.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-210.json | python3 -m json.tool

echo 'import AH group-lines group-lines-211.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-211.json | python3 -m json.tool

echo 'import AH group-lines group-lines-212.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-212.json | python3 -m json.tool

echo 'import AH group-lines group-lines-213.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-213.json | python3 -m json.tool

echo 'import AH group-lines group-lines-214.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-214.json | python3 -m json.tool

echo 'import AH group-lines group-lines-215.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-215.json | python3 -m json.tool

echo 'import AH group-lines group-lines-216.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-216.json | python3 -m json.tool

echo 'import AH group-lines group-lines-217.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-217.json | python3 -m json.tool

echo 'import AH group-lines group-lines-218.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-218.json | python3 -m json.tool

echo 'import AH group-lines group-lines-219.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-219.json | python3 -m json.tool

echo 'import AH group-lines group-lines-220.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-220.json | python3 -m json.tool

echo 'import AH group-lines group-lines-221.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-221.json | python3 -m json.tool

echo 'import AH group-lines group-lines-222.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-222.json | python3 -m json.tool

echo 'import AH group-lines group-lines-223.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-223.json | python3 -m json.tool

echo 'import AH group-lines group-lines-224.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-224.json | python3 -m json.tool

echo 'import AH group-lines group-lines-225.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-225.json | python3 -m json.tool

echo 'import AH group-lines group-lines-226.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-226.json | python3 -m json.tool

echo 'import AH group-lines group-lines-227.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-227.json | python3 -m json.tool

echo 'import AH group-lines group-lines-228.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-228.json | python3 -m json.tool

echo 'import AH group-lines group-lines-229.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-229.json | python3 -m json.tool

echo 'import AH group-lines group-lines-230.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-230.json | python3 -m json.tool

echo 'import AH group-lines group-lines-231.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-231.json | python3 -m json.tool

echo 'import AH group-lines group-lines-232.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-232.json | python3 -m json.tool

echo 'import AH group-lines group-lines-233.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-233.json | python3 -m json.tool

echo 'import AH group-lines group-lines-234.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-234.json | python3 -m json.tool

echo 'import AH group-lines group-lines-235.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-235.json | python3 -m json.tool

echo 'import AH group-lines group-lines-236.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-236.json | python3 -m json.tool

echo 'import AH group-lines group-lines-237.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-237.json | python3 -m json.tool

echo 'import AH group-lines group-lines-238.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-238.json | python3 -m json.tool

echo 'import AH group-lines group-lines-239.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-239.json | python3 -m json.tool

echo 'import AH group-lines group-lines-240.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-240.json | python3 -m json.tool

echo 'import AH group-lines group-lines-241.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-241.json | python3 -m json.tool

echo 'import AH group-lines group-lines-242.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-242.json | python3 -m json.tool

echo 'import AH group-lines group-lines-243.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-243.json | python3 -m json.tool

echo 'import AH group-lines group-lines-244.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-244.json | python3 -m json.tool

echo 'import AH group-lines group-lines-245.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-245.json | python3 -m json.tool

echo 'import AH group-lines group-lines-246.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-246.json | python3 -m json.tool

echo 'import AH group-lines group-lines-247.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-247.json | python3 -m json.tool

echo 'import AH group-lines group-lines-248.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-248.json | python3 -m json.tool

echo 'import AH group-lines group-lines-249.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-249.json | python3 -m json.tool

echo 'import AH group-lines group-lines-250.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-250.json | python3 -m json.tool

echo 'import AH group-lines group-lines-251.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-251.json | python3 -m json.tool

echo 'import AH group-lines group-lines-252.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-252.json | python3 -m json.tool

echo 'import AH group-lines group-lines-253.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-253.json | python3 -m json.tool

echo 'import AH group-lines group-lines-254.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-254.json | python3 -m json.tool

echo 'import AH group-lines group-lines-255.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-255.json | python3 -m json.tool

echo 'import AH group-lines group-lines-256.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-256.json | python3 -m json.tool

echo 'import AH group-lines group-lines-257.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-257.json | python3 -m json.tool

echo 'import AH group-lines group-lines-258.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-258.json | python3 -m json.tool

echo 'import AH group-lines group-lines-259.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-259.json | python3 -m json.tool

echo 'import AH group-lines group-lines-260.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-260.json | python3 -m json.tool

echo 'import AH group-lines group-lines-261.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-261.json | python3 -m json.tool

echo 'import AH group-lines group-lines-262.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-262.json | python3 -m json.tool

echo 'import AH group-lines group-lines-263.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-263.json | python3 -m json.tool

echo 'import AH group-lines group-lines-264.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-264.json | python3 -m json.tool

echo 'import AH group-lines group-lines-265.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-265.json | python3 -m json.tool

echo 'import AH group-lines group-lines-266.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-266.json | python3 -m json.tool

echo 'import AH group-lines group-lines-267.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-267.json | python3 -m json.tool

echo 'import AH group-lines group-lines-268.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-268.json | python3 -m json.tool

echo 'import AH group-lines group-lines-269.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-269.json | python3 -m json.tool

echo 'import AH group-lines group-lines-270.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-270.json | python3 -m json.tool

echo 'import AH group-lines group-lines-271.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-271.json | python3 -m json.tool

echo 'import AH group-lines group-lines-272.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-272.json | python3 -m json.tool

echo 'import AH group-lines group-lines-273.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-273.json | python3 -m json.tool

echo 'import AH group-lines group-lines-274.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-274.json | python3 -m json.tool

echo 'import AH group-lines group-lines-275.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-275.json | python3 -m json.tool

echo 'import AH group-lines group-lines-276.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-276.json | python3 -m json.tool

echo 'import AH group-lines group-lines-277.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-277.json | python3 -m json.tool

echo 'import AH group-lines group-lines-278.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-278.json | python3 -m json.tool

echo 'import AH group-lines group-lines-279.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-279.json | python3 -m json.tool

echo 'import AH group-lines group-lines-280.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-280.json | python3 -m json.tool

echo 'import AH group-lines group-lines-281.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-281.json | python3 -m json.tool

echo 'import AH group-lines group-lines-282.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-282.json | python3 -m json.tool

echo 'import AH group-lines group-lines-283.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-283.json | python3 -m json.tool

echo 'import AH group-lines group-lines-284.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-284.json | python3 -m json.tool

echo 'import AH group-lines group-lines-285.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-285.json | python3 -m json.tool

echo 'import AH group-lines group-lines-286.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-286.json | python3 -m json.tool

echo 'import AH group-lines group-lines-287.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-287.json | python3 -m json.tool

echo 'import AH group-lines group-lines-288.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-288.json | python3 -m json.tool

echo 'import AH group-lines group-lines-289.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-289.json | python3 -m json.tool

echo 'import AH group-lines group-lines-290.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-290.json | python3 -m json.tool

echo 'import AH group-lines group-lines-291.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-291.json | python3 -m json.tool

echo 'import AH group-lines group-lines-292.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-292.json | python3 -m json.tool

echo 'import AH group-lines group-lines-293.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-293.json | python3 -m json.tool

echo 'import AH group-lines group-lines-294.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-294.json | python3 -m json.tool

echo 'import AH group-lines group-lines-295.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-295.json | python3 -m json.tool

echo 'import AH group-lines group-lines-296.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-296.json | python3 -m json.tool

echo 'import AH group-lines group-lines-297.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-297.json | python3 -m json.tool

echo 'import AH group-lines group-lines-298.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-298.json | python3 -m json.tool

echo 'import AH group-lines group-lines-299.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-299.json | python3 -m json.tool

echo 'import AH group-lines group-lines-300.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-300.json | python3 -m json.tool

echo 'import AH group-lines group-lines-301.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-301.json | python3 -m json.tool

echo 'import AH group-lines group-lines-302.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-302.json | python3 -m json.tool

echo 'import AH group-lines group-lines-303.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-303.json | python3 -m json.tool

echo 'import AH group-lines group-lines-304.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-304.json | python3 -m json.tool

echo 'import AH group-lines group-lines-305.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-305.json | python3 -m json.tool

echo 'import AH group-lines group-lines-306.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-306.json | python3 -m json.tool

echo 'import AH group-lines group-lines-307.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-307.json | python3 -m json.tool

echo 'import AH group-lines group-lines-308.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-308.json | python3 -m json.tool

echo 'import AH group-lines group-lines-309.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-309.json | python3 -m json.tool

echo 'import AH group-lines group-lines-310.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-310.json | python3 -m json.tool

echo 'import AH group-lines group-lines-311.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-311.json | python3 -m json.tool

echo 'import AH group-lines group-lines-312.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-312.json | python3 -m json.tool

echo 'import AH group-lines group-lines-313.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-313.json | python3 -m json.tool

echo 'import AH group-lines group-lines-314.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-314.json | python3 -m json.tool

echo 'import AH group-lines group-lines-315.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-315.json | python3 -m json.tool

echo 'import AH group-lines group-lines-316.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-316.json | python3 -m json.tool

echo 'import AH group-lines group-lines-317.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-317.json | python3 -m json.tool

echo 'import AH group-lines group-lines-318.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-318.json | python3 -m json.tool

echo 'import AH group-lines group-lines-319.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-319.json | python3 -m json.tool

echo 'import AH group-lines group-lines-320.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-320.json | python3 -m json.tool

echo 'import AH group-lines group-lines-321.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-321.json | python3 -m json.tool

echo 'import AH group-lines group-lines-322.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-322.json | python3 -m json.tool

echo 'import AH group-lines group-lines-323.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-323.json | python3 -m json.tool

echo 'import AH group-lines group-lines-324.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-324.json | python3 -m json.tool

echo 'import AH group-lines group-lines-325.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-325.json | python3 -m json.tool

echo 'import AH group-lines group-lines-326.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-326.json | python3 -m json.tool

echo 'import AH group-lines group-lines-327.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-327.json | python3 -m json.tool

echo 'import AH group-lines group-lines-328.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-328.json | python3 -m json.tool

echo 'import AH group-lines group-lines-329.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-329.json | python3 -m json.tool

echo 'import AH group-lines group-lines-330.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-330.json | python3 -m json.tool

echo 'import AH group-lines group-lines-331.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-331.json | python3 -m json.tool

echo 'import AH group-lines group-lines-332.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-332.json | python3 -m json.tool

echo 'import AH group-lines group-lines-333.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-333.json | python3 -m json.tool

echo 'import AH group-lines group-lines-334.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-334.json | python3 -m json.tool

echo 'import AH group-lines group-lines-335.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-335.json | python3 -m json.tool

echo 'import AH group-lines group-lines-336.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-336.json | python3 -m json.tool

echo 'import AH group-lines group-lines-337.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-337.json | python3 -m json.tool

echo 'import AH group-lines group-lines-338.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-338.json | python3 -m json.tool

echo 'import AH group-lines group-lines-339.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-339.json | python3 -m json.tool

echo 'import AH group-lines group-lines-340.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-340.json | python3 -m json.tool

echo 'import AH group-lines group-lines-341.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-341.json | python3 -m json.tool

echo 'import AH group-lines group-lines-342.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-342.json | python3 -m json.tool

echo 'import AH group-lines group-lines-343.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-343.json | python3 -m json.tool

echo 'import AH group-lines group-lines-344.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-344.json | python3 -m json.tool

echo 'import AH group-lines group-lines-345.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-345.json | python3 -m json.tool

echo 'import AH group-lines group-lines-346.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-346.json | python3 -m json.tool

echo 'import AH group-lines group-lines-347.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-347.json | python3 -m json.tool

echo 'import AH group-lines group-lines-348.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-348.json | python3 -m json.tool

echo 'import AH group-lines group-lines-349.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-349.json | python3 -m json.tool

echo 'import AH group-lines group-lines-350.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-350.json | python3 -m json.tool

echo 'import AH group-lines group-lines-351.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-351.json | python3 -m json.tool

echo 'import AH group-lines group-lines-352.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-352.json | python3 -m json.tool

echo 'import AH group-lines group-lines-353.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-353.json | python3 -m json.tool

echo 'import AH group-lines group-lines-354.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-354.json | python3 -m json.tool

echo 'import AH group-lines group-lines-355.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-355.json | python3 -m json.tool

echo 'import AH group-lines group-lines-356.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-356.json | python3 -m json.tool

echo 'import AH group-lines group-lines-357.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-357.json | python3 -m json.tool

echo 'import AH group-lines group-lines-358.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-358.json | python3 -m json.tool

echo 'import AH group-lines group-lines-359.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-359.json | python3 -m json.tool

echo 'import AH group-lines group-lines-360.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-360.json | python3 -m json.tool

echo 'import AH group-lines group-lines-361.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-361.json | python3 -m json.tool

echo 'import AH group-lines group-lines-362.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-362.json | python3 -m json.tool

echo 'import AH group-lines group-lines-363.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-363.json | python3 -m json.tool

echo 'import AH group-lines group-lines-364.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-364.json | python3 -m json.tool

echo 'import AH group-lines group-lines-365.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-365.json | python3 -m json.tool

echo 'import AH group-lines group-lines-366.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-366.json | python3 -m json.tool

echo 'import AH group-lines group-lines-367.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-367.json | python3 -m json.tool

echo 'import AH group-lines group-lines-368.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-368.json | python3 -m json.tool

echo 'import AH group-lines group-lines-369.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-369.json | python3 -m json.tool

echo 'import AH group-lines group-lines-370.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-370.json | python3 -m json.tool

echo 'import AH group-lines group-lines-371.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-371.json | python3 -m json.tool

echo 'import AH group-lines group-lines-372.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-372.json | python3 -m json.tool

echo 'import AH group-lines group-lines-373.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-373.json | python3 -m json.tool

echo 'import AH group-lines group-lines-374.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-374.json | python3 -m json.tool

echo 'import AH group-lines group-lines-375.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-375.json | python3 -m json.tool

echo 'import AH group-lines group-lines-376.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-376.json | python3 -m json.tool

echo 'import AH group-lines group-lines-377.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-377.json | python3 -m json.tool

echo 'import AH group-lines group-lines-378.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-378.json | python3 -m json.tool

echo 'import AH group-lines group-lines-379.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-379.json | python3 -m json.tool

echo 'import AH group-lines group-lines-380.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-380.json | python3 -m json.tool

echo 'import AH group-lines group-lines-381.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-381.json | python3 -m json.tool

echo 'import AH group-lines group-lines-382.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-382.json | python3 -m json.tool

echo 'import AH group-lines group-lines-383.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-383.json | python3 -m json.tool

echo 'import AH group-lines group-lines-384.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-384.json | python3 -m json.tool

echo 'import AH group-lines group-lines-385.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-385.json | python3 -m json.tool

echo 'import AH group-lines group-lines-386.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-386.json | python3 -m json.tool

echo 'import AH group-lines group-lines-387.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-387.json | python3 -m json.tool

echo 'import AH group-lines group-lines-388.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-388.json | python3 -m json.tool

echo 'import AH group-lines group-lines-389.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-389.json | python3 -m json.tool

echo 'import AH group-lines group-lines-390.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-390.json | python3 -m json.tool

echo 'import AH group-lines group-lines-391.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-391.json | python3 -m json.tool

echo 'import AH group-lines group-lines-392.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-392.json | python3 -m json.tool

echo 'import AH group-lines group-lines-393.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-393.json | python3 -m json.tool

echo 'import AH group-lines group-lines-394.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-394.json | python3 -m json.tool

echo 'import AH group-lines group-lines-395.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-395.json | python3 -m json.tool

echo 'import AH group-lines group-lines-396.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-396.json | python3 -m json.tool

echo 'import AH group-lines group-lines-397.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-397.json | python3 -m json.tool

echo 'import AH group-lines group-lines-398.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-398.json | python3 -m json.tool

echo 'import AH group-lines group-lines-399.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-399.json | python3 -m json.tool

echo 'import AH group-lines group-lines-400.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-400.json | python3 -m json.tool

echo 'import AH group-lines group-lines-401.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-401.json | python3 -m json.tool

echo 'import AH group-lines group-lines-402.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-402.json | python3 -m json.tool

echo 'import AH group-lines group-lines-403.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-403.json | python3 -m json.tool

echo 'import AH group-lines group-lines-404.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-404.json | python3 -m json.tool

echo 'import AH group-lines group-lines-405.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-405.json | python3 -m json.tool

echo 'import AH group-lines group-lines-406.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-406.json | python3 -m json.tool

echo 'import AH group-lines group-lines-407.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-407.json | python3 -m json.tool

echo 'import AH group-lines group-lines-408.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-408.json | python3 -m json.tool

echo 'import AH group-lines group-lines-409.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-409.json | python3 -m json.tool

echo 'import AH group-lines group-lines-410.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-410.json | python3 -m json.tool

echo 'import AH group-lines group-lines-411.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-411.json | python3 -m json.tool

echo 'import AH group-lines group-lines-412.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-412.json | python3 -m json.tool

echo 'import AH group-lines group-lines-413.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-413.json | python3 -m json.tool

echo 'import AH group-lines group-lines-414.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-414.json | python3 -m json.tool

echo 'import AH group-lines group-lines-415.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-415.json | python3 -m json.tool

echo 'import AH group-lines group-lines-416.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-416.json | python3 -m json.tool

echo 'import AH group-lines group-lines-417.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-417.json | python3 -m json.tool

echo 'import AH group-lines group-lines-418.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-418.json | python3 -m json.tool

echo 'import AH group-lines group-lines-419.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-419.json | python3 -m json.tool

echo 'import AH group-lines group-lines-420.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-420.json | python3 -m json.tool

echo 'import AH group-lines group-lines-421.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-421.json | python3 -m json.tool

echo 'import AH group-lines group-lines-422.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-422.json | python3 -m json.tool

echo 'import AH group-lines group-lines-423.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-423.json | python3 -m json.tool

echo 'import AH group-lines group-lines-424.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-424.json | python3 -m json.tool

echo 'import AH group-lines group-lines-425.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-425.json | python3 -m json.tool

echo 'import AH group-lines group-lines-426.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-426.json | python3 -m json.tool

echo 'import AH group-lines group-lines-427.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-427.json | python3 -m json.tool

echo 'import AH group-lines group-lines-428.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-428.json | python3 -m json.tool

echo 'import AH group-lines group-lines-429.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-429.json | python3 -m json.tool

echo 'import AH group-lines group-lines-430.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-430.json | python3 -m json.tool

echo 'import AH group-lines group-lines-431.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-431.json | python3 -m json.tool

echo 'import AH group-lines group-lines-432.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-432.json | python3 -m json.tool

echo 'import AH group-lines group-lines-433.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-433.json | python3 -m json.tool

echo 'import AH group-lines group-lines-434.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-434.json | python3 -m json.tool

echo 'import AH group-lines group-lines-435.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-435.json | python3 -m json.tool

echo 'import AH group-lines group-lines-436.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-436.json | python3 -m json.tool

echo 'import AH group-lines group-lines-437.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-437.json | python3 -m json.tool

echo 'import AH group-lines group-lines-438.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-438.json | python3 -m json.tool

echo 'import AH group-lines group-lines-439.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-439.json | python3 -m json.tool

echo 'import AH group-lines group-lines-440.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-440.json | python3 -m json.tool

echo 'import AH group-lines group-lines-441.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-441.json | python3 -m json.tool

echo 'import AH group-lines group-lines-442.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-442.json | python3 -m json.tool

echo 'import AH group-lines group-lines-443.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-443.json | python3 -m json.tool

echo 'import AH group-lines group-lines-444.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-444.json | python3 -m json.tool

echo 'import AH group-lines group-lines-445.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-445.json | python3 -m json.tool

echo 'import AH group-lines group-lines-446.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-446.json | python3 -m json.tool

echo 'import AH group-lines group-lines-447.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-447.json | python3 -m json.tool

echo 'import AH group-lines group-lines-448.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-448.json | python3 -m json.tool

echo 'import AH group-lines group-lines-449.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-449.json | python3 -m json.tool

echo 'import AH group-lines group-lines-450.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-450.json | python3 -m json.tool

echo 'import AH group-lines group-lines-451.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-451.json | python3 -m json.tool

echo 'import AH group-lines group-lines-452.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-452.json | python3 -m json.tool

echo 'import AH group-lines group-lines-453.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-453.json | python3 -m json.tool

echo 'import AH group-lines group-lines-454.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-454.json | python3 -m json.tool

echo 'import AH group-lines group-lines-455.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-455.json | python3 -m json.tool

echo 'import AH group-lines group-lines-456.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-456.json | python3 -m json.tool

echo 'import AH group-lines group-lines-457.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-457.json | python3 -m json.tool

echo 'import AH group-lines group-lines-458.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-458.json | python3 -m json.tool

echo 'import AH group-lines group-lines-459.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-459.json | python3 -m json.tool

echo 'import AH group-lines group-lines-460.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-460.json | python3 -m json.tool

echo 'import AH group-lines group-lines-461.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-461.json | python3 -m json.tool

echo 'import AH group-lines group-lines-462.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-462.json | python3 -m json.tool

echo 'import AH group-lines group-lines-463.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-463.json | python3 -m json.tool

echo 'import AH group-lines group-lines-464.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-464.json | python3 -m json.tool

echo 'import AH group-lines group-lines-465.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-465.json | python3 -m json.tool

echo 'import AH group-lines group-lines-466.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-466.json | python3 -m json.tool

echo 'import AH group-lines group-lines-467.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-467.json | python3 -m json.tool

echo 'import AH group-lines group-lines-468.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-468.json | python3 -m json.tool

echo 'import AH group-lines group-lines-469.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-469.json | python3 -m json.tool

echo 'import AH group-lines group-lines-470.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-470.json | python3 -m json.tool

echo 'import AH group-lines group-lines-471.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-471.json | python3 -m json.tool

echo 'import AH group-lines group-lines-472.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-472.json | python3 -m json.tool

echo 'import AH group-lines group-lines-473.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-473.json | python3 -m json.tool

echo 'import AH group-lines group-lines-474.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-474.json | python3 -m json.tool

echo 'import AH group-lines group-lines-475.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-475.json | python3 -m json.tool

echo 'import AH group-lines group-lines-476.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-476.json | python3 -m json.tool

echo 'import AH group-lines group-lines-477.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-477.json | python3 -m json.tool

echo 'import AH group-lines group-lines-478.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-478.json | python3 -m json.tool

echo 'import AH group-lines group-lines-479.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-479.json | python3 -m json.tool

echo 'import AH group-lines group-lines-480.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-480.json | python3 -m json.tool

echo 'import AH group-lines group-lines-481.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-481.json | python3 -m json.tool

echo 'import AH group-lines group-lines-482.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-482.json | python3 -m json.tool

echo 'import AH group-lines group-lines-483.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-483.json | python3 -m json.tool

echo 'import AH group-lines group-lines-484.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-484.json | python3 -m json.tool

echo 'import AH group-lines group-lines-485.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-485.json | python3 -m json.tool

echo 'import AH group-lines group-lines-486.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-486.json | python3 -m json.tool

echo 'import AH group-lines group-lines-487.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-487.json | python3 -m json.tool

echo 'import AH group-lines group-lines-488.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-488.json | python3 -m json.tool

echo 'import AH group-lines group-lines-489.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-489.json | python3 -m json.tool

echo 'import AH group-lines group-lines-490.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-490.json | python3 -m json.tool

echo 'import AH group-lines group-lines-491.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-491.json | python3 -m json.tool

echo 'import AH group-lines group-lines-492.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-492.json | python3 -m json.tool

echo 'import AH group-lines group-lines-493.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-493.json | python3 -m json.tool

echo 'import AH group-lines group-lines-494.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-494.json | python3 -m json.tool

echo 'import AH group-lines group-lines-495.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-495.json | python3 -m json.tool

echo 'import AH group-lines group-lines-496.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-496.json | python3 -m json.tool

echo 'import AH group-lines group-lines-497.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-497.json | python3 -m json.tool

echo 'import AH group-lines group-lines-498.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-498.json | python3 -m json.tool

echo 'import AH group-lines group-lines-499.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-499.json | python3 -m json.tool

echo 'import AH group-lines group-lines-500.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-500.json | python3 -m json.tool

echo 'import AH group-lines group-lines-501.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-501.json | python3 -m json.tool

echo 'import AH group-lines group-lines-502.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-502.json | python3 -m json.tool

echo 'import AH group-lines group-lines-503.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-503.json | python3 -m json.tool

echo 'import AH group-lines group-lines-504.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-504.json | python3 -m json.tool

echo 'import AH group-lines group-lines-505.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-505.json | python3 -m json.tool

echo 'import AH group-lines group-lines-506.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-506.json | python3 -m json.tool

echo 'import AH group-lines group-lines-507.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-507.json | python3 -m json.tool

echo 'import AH group-lines group-lines-508.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-508.json | python3 -m json.tool

echo 'import AH group-lines group-lines-509.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-509.json | python3 -m json.tool

echo 'import AH group-lines group-lines-510.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-510.json | python3 -m json.tool

echo 'import AH group-lines group-lines-511.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-511.json | python3 -m json.tool

echo 'import AH group-lines group-lines-512.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-512.json | python3 -m json.tool

echo 'import AH group-lines group-lines-513.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-513.json | python3 -m json.tool

echo 'import AH group-lines group-lines-514.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-514.json | python3 -m json.tool

echo 'import AH group-lines group-lines-515.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-515.json | python3 -m json.tool

echo 'import AH group-lines group-lines-516.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-516.json | python3 -m json.tool

echo 'import AH group-lines group-lines-517.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-517.json | python3 -m json.tool

echo 'import AH group-lines group-lines-518.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-518.json | python3 -m json.tool

echo 'import AH group-lines group-lines-519.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-519.json | python3 -m json.tool

echo 'import AH group-lines group-lines-520.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-520.json | python3 -m json.tool

echo 'import AH group-lines group-lines-521.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-521.json | python3 -m json.tool

echo 'import AH group-lines group-lines-522.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-522.json | python3 -m json.tool

echo 'import AH group-lines group-lines-523.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-523.json | python3 -m json.tool

echo 'import AH group-lines group-lines-524.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-524.json | python3 -m json.tool

echo 'import AH group-lines group-lines-525.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-525.json | python3 -m json.tool

echo 'import AH group-lines group-lines-526.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-526.json | python3 -m json.tool

echo 'import AH group-lines group-lines-527.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-527.json | python3 -m json.tool

echo 'import AH group-lines group-lines-528.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-528.json | python3 -m json.tool

echo 'import AH group-lines group-lines-529.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-529.json | python3 -m json.tool

echo 'import AH group-lines group-lines-530.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-530.json | python3 -m json.tool

echo 'import AH group-lines group-lines-531.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-531.json | python3 -m json.tool

echo 'import AH group-lines group-lines-532.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-532.json | python3 -m json.tool

echo 'import AH group-lines group-lines-533.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-533.json | python3 -m json.tool

echo 'import AH group-lines group-lines-534.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-534.json | python3 -m json.tool

echo 'import AH group-lines group-lines-535.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-535.json | python3 -m json.tool

echo 'import AH group-lines group-lines-536.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-536.json | python3 -m json.tool

echo 'import AH group-lines group-lines-537.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-537.json | python3 -m json.tool

echo 'import AH group-lines group-lines-538.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-538.json | python3 -m json.tool

echo 'import AH group-lines group-lines-539.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-539.json | python3 -m json.tool

echo 'import AH group-lines group-lines-540.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-540.json | python3 -m json.tool

echo 'import AH group-lines group-lines-541.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-541.json | python3 -m json.tool

echo 'import AH group-lines group-lines-542.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-542.json | python3 -m json.tool

echo 'import AH group-lines group-lines-543.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-543.json | python3 -m json.tool

echo 'import AH group-lines group-lines-544.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-544.json | python3 -m json.tool

echo 'import AH group-lines group-lines-545.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-545.json | python3 -m json.tool

echo 'import AH group-lines group-lines-546.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-546.json | python3 -m json.tool

echo 'import AH group-lines group-lines-547.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-547.json | python3 -m json.tool

echo 'import AH group-lines group-lines-548.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-548.json | python3 -m json.tool

echo 'import AH group-lines group-lines-549.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-549.json | python3 -m json.tool

echo 'import AH group-lines group-lines-550.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-550.json | python3 -m json.tool

echo 'import AH group-lines group-lines-551.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-551.json | python3 -m json.tool

echo 'import AH group-lines group-lines-552.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-552.json | python3 -m json.tool

echo 'import AH group-lines group-lines-553.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-553.json | python3 -m json.tool

echo 'import AH group-lines group-lines-554.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-554.json | python3 -m json.tool

echo 'import AH group-lines group-lines-555.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-555.json | python3 -m json.tool

echo 'import AH group-lines group-lines-556.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-556.json | python3 -m json.tool

echo 'import AH group-lines group-lines-557.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-557.json | python3 -m json.tool

echo 'import AH group-lines group-lines-558.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-558.json | python3 -m json.tool

echo 'import AH group-lines group-lines-559.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-559.json | python3 -m json.tool

echo 'import AH group-lines group-lines-560.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-560.json | python3 -m json.tool

echo 'import AH group-lines group-lines-561.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-561.json | python3 -m json.tool

echo 'import AH group-lines group-lines-562.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-562.json | python3 -m json.tool

echo 'import AH group-lines group-lines-563.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-563.json | python3 -m json.tool

echo 'import AH group-lines group-lines-564.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-564.json | python3 -m json.tool

echo 'import AH group-lines group-lines-565.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-565.json | python3 -m json.tool

echo 'import AH group-lines group-lines-566.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-566.json | python3 -m json.tool

echo 'import AH group-lines group-lines-567.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-567.json | python3 -m json.tool

echo 'import AH group-lines group-lines-568.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-568.json | python3 -m json.tool

echo 'import AH group-lines group-lines-569.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-569.json | python3 -m json.tool

echo 'import AH group-lines group-lines-570.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-570.json | python3 -m json.tool

echo 'import AH group-lines group-lines-571.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-571.json | python3 -m json.tool

echo 'import AH group-lines group-lines-572.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-572.json | python3 -m json.tool

echo 'import AH group-lines group-lines-573.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-573.json | python3 -m json.tool

echo 'import AH group-lines group-lines-574.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-574.json | python3 -m json.tool

echo 'import AH group-lines group-lines-575.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-575.json | python3 -m json.tool

echo 'import AH group-lines group-lines-576.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-576.json | python3 -m json.tool

echo 'import AH group-lines group-lines-577.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-577.json | python3 -m json.tool

echo 'import AH group-lines group-lines-578.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-578.json | python3 -m json.tool

echo 'import AH group-lines group-lines-579.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-579.json | python3 -m json.tool

echo 'import AH group-lines group-lines-580.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-580.json | python3 -m json.tool

echo 'import AH group-lines group-lines-581.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-581.json | python3 -m json.tool

echo 'import AH group-lines group-lines-582.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-582.json | python3 -m json.tool

echo 'import AH group-lines group-lines-583.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-583.json | python3 -m json.tool

echo 'import AH group-lines group-lines-584.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-584.json | python3 -m json.tool

echo 'import AH group-lines group-lines-585.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-585.json | python3 -m json.tool

echo 'import AH group-lines group-lines-586.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-586.json | python3 -m json.tool

echo 'import AH group-lines group-lines-587.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-587.json | python3 -m json.tool

echo 'import AH group-lines group-lines-588.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-588.json | python3 -m json.tool

echo 'import AH group-lines group-lines-589.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-589.json | python3 -m json.tool

echo 'import AH group-lines group-lines-590.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-590.json | python3 -m json.tool

echo 'import AH group-lines group-lines-591.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-591.json | python3 -m json.tool

echo 'import AH group-lines group-lines-592.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-592.json | python3 -m json.tool

echo 'import AH group-lines group-lines-593.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-593.json | python3 -m json.tool

echo 'import AH group-lines group-lines-594.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-594.json | python3 -m json.tool

echo 'import AH group-lines group-lines-595.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-595.json | python3 -m json.tool

echo 'import AH group-lines group-lines-596.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-596.json | python3 -m json.tool

echo 'import AH group-lines group-lines-597.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-597.json | python3 -m json.tool

echo 'import AH group-lines group-lines-598.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-598.json | python3 -m json.tool

echo 'import AH group-lines group-lines-599.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-599.json | python3 -m json.tool

echo 'import AH group-lines group-lines-600.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-600.json | python3 -m json.tool

echo 'import AH group-lines group-lines-601.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-601.json | python3 -m json.tool

echo 'import AH group-lines group-lines-602.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-602.json | python3 -m json.tool

echo 'import AH group-lines group-lines-603.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-603.json | python3 -m json.tool

echo 'import AH group-lines group-lines-604.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-604.json | python3 -m json.tool

echo 'import AH group-lines group-lines-605.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-605.json | python3 -m json.tool

echo 'import AH group-lines group-lines-606.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-606.json | python3 -m json.tool

echo 'import AH group-lines group-lines-607.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-607.json | python3 -m json.tool

echo 'import AH group-lines group-lines-608.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-608.json | python3 -m json.tool

echo 'import AH group-lines group-lines-609.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-609.json | python3 -m json.tool

echo 'import AH group-lines group-lines-610.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-610.json | python3 -m json.tool

echo 'import AH group-lines group-lines-611.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-611.json | python3 -m json.tool

echo 'import AH group-lines group-lines-612.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-612.json | python3 -m json.tool

echo 'import AH group-lines group-lines-613.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-613.json | python3 -m json.tool

echo 'import AH group-lines group-lines-614.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-614.json | python3 -m json.tool

echo 'import AH group-lines group-lines-615.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-615.json | python3 -m json.tool

echo 'import AH group-lines group-lines-616.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-616.json | python3 -m json.tool

echo 'import AH group-lines group-lines-617.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-617.json | python3 -m json.tool

echo 'import AH group-lines group-lines-618.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-618.json | python3 -m json.tool

echo 'import AH group-lines group-lines-619.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-619.json | python3 -m json.tool

echo 'import AH group-lines group-lines-620.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-620.json | python3 -m json.tool

echo 'import AH group-lines group-lines-621.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-621.json | python3 -m json.tool

echo 'import AH group-lines group-lines-622.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-622.json | python3 -m json.tool

echo 'import AH group-lines group-lines-623.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-623.json | python3 -m json.tool

echo 'import AH group-lines group-lines-624.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-624.json | python3 -m json.tool

echo 'import AH group-lines group-lines-625.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-625.json | python3 -m json.tool

echo 'import AH group-lines group-lines-626.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-626.json | python3 -m json.tool

echo 'import AH group-lines group-lines-627.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-627.json | python3 -m json.tool

echo 'import AH group-lines group-lines-628.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-628.json | python3 -m json.tool

echo 'import AH group-lines group-lines-629.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-629.json | python3 -m json.tool

echo 'import AH group-lines group-lines-630.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-630.json | python3 -m json.tool

echo 'import AH group-lines group-lines-631.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-631.json | python3 -m json.tool

echo 'import AH group-lines group-lines-632.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-632.json | python3 -m json.tool

echo 'import AH group-lines group-lines-633.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-633.json | python3 -m json.tool

echo 'import AH group-lines group-lines-634.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-634.json | python3 -m json.tool

echo 'import AH group-lines group-lines-635.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-635.json | python3 -m json.tool

echo 'import AH group-lines group-lines-636.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-636.json | python3 -m json.tool

echo 'import AH group-lines group-lines-637.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-637.json | python3 -m json.tool

echo 'import AH group-lines group-lines-638.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-638.json | python3 -m json.tool

echo 'import AH group-lines group-lines-639.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-639.json | python3 -m json.tool

echo 'import AH group-lines group-lines-640.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-640.json | python3 -m json.tool

echo 'import AH group-lines group-lines-641.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-641.json | python3 -m json.tool

echo 'import AH group-lines group-lines-642.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-642.json | python3 -m json.tool

echo 'import AH group-lines group-lines-643.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-643.json | python3 -m json.tool

echo 'import AH group-lines group-lines-644.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-644.json | python3 -m json.tool

echo 'import AH group-lines group-lines-645.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-645.json | python3 -m json.tool

echo 'import AH group-lines group-lines-646.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-646.json | python3 -m json.tool

echo 'import AH group-lines group-lines-647.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-647.json | python3 -m json.tool

echo 'import AH group-lines group-lines-648.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-648.json | python3 -m json.tool

echo 'import AH group-lines group-lines-649.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-649.json | python3 -m json.tool

echo 'import AH group-lines group-lines-650.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-650.json | python3 -m json.tool

echo 'import AH group-lines group-lines-651.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-651.json | python3 -m json.tool

echo 'import AH group-lines group-lines-652.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-652.json | python3 -m json.tool

echo 'import AH group-lines group-lines-653.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-653.json | python3 -m json.tool

echo 'import AH group-lines group-lines-654.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-654.json | python3 -m json.tool

echo 'import AH group-lines group-lines-655.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-655.json | python3 -m json.tool

echo 'import AH group-lines group-lines-656.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-656.json | python3 -m json.tool

echo 'import AH group-lines group-lines-657.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-657.json | python3 -m json.tool

echo 'import AH group-lines group-lines-658.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-658.json | python3 -m json.tool

echo 'import AH group-lines group-lines-659.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-659.json | python3 -m json.tool

echo 'import AH group-lines group-lines-660.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-660.json | python3 -m json.tool

echo 'import AH group-lines group-lines-661.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-661.json | python3 -m json.tool

echo 'import AH group-lines group-lines-662.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-662.json | python3 -m json.tool

echo 'import AH group-lines group-lines-663.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-663.json | python3 -m json.tool

echo 'import AH group-lines group-lines-664.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-664.json | python3 -m json.tool

echo 'import AH group-lines group-lines-665.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-665.json | python3 -m json.tool

echo 'import AH group-lines group-lines-666.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-666.json | python3 -m json.tool

echo 'import AH group-lines group-lines-667.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-667.json | python3 -m json.tool

echo 'import AH group-lines group-lines-668.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-668.json | python3 -m json.tool

echo 'import AH group-lines group-lines-669.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-669.json | python3 -m json.tool

echo 'import AH group-lines group-lines-670.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-670.json | python3 -m json.tool

echo 'import AH group-lines group-lines-671.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-671.json | python3 -m json.tool

echo 'import AH group-lines group-lines-672.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-672.json | python3 -m json.tool

echo 'import AH group-lines group-lines-673.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-673.json | python3 -m json.tool

echo 'import AH group-lines group-lines-674.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-674.json | python3 -m json.tool

echo 'import AH group-lines group-lines-675.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-675.json | python3 -m json.tool

echo 'import AH group-lines group-lines-676.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-676.json | python3 -m json.tool

echo 'import AH group-lines group-lines-677.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-677.json | python3 -m json.tool

echo 'import AH group-lines group-lines-678.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-678.json | python3 -m json.tool

echo 'import AH group-lines group-lines-679.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-679.json | python3 -m json.tool

echo 'import AH group-lines group-lines-680.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-680.json | python3 -m json.tool

echo 'import AH group-lines group-lines-681.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-681.json | python3 -m json.tool

echo 'import AH group-lines group-lines-682.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-682.json | python3 -m json.tool

echo 'import AH group-lines group-lines-683.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-683.json | python3 -m json.tool

echo 'import AH group-lines group-lines-684.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-684.json | python3 -m json.tool

echo 'import AH group-lines group-lines-685.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-685.json | python3 -m json.tool

echo 'import AH group-lines group-lines-686.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-686.json | python3 -m json.tool

echo 'import AH group-lines group-lines-687.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-687.json | python3 -m json.tool

echo 'import AH group-lines group-lines-688.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-688.json | python3 -m json.tool

echo 'import AH group-lines group-lines-689.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-689.json | python3 -m json.tool

echo 'import AH group-lines group-lines-690.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-690.json | python3 -m json.tool

echo 'import AH group-lines group-lines-691.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-691.json | python3 -m json.tool

echo 'import AH group-lines group-lines-692.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-692.json | python3 -m json.tool

echo 'import AH group-lines group-lines-693.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-693.json | python3 -m json.tool

echo 'import AH group-lines group-lines-694.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-694.json | python3 -m json.tool

echo 'import AH group-lines group-lines-695.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-695.json | python3 -m json.tool

echo 'import AH group-lines group-lines-696.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-696.json | python3 -m json.tool

echo 'import AH group-lines group-lines-697.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-697.json | python3 -m json.tool

echo 'import AH group-lines group-lines-698.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-698.json | python3 -m json.tool

echo 'import AH group-lines group-lines-699.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-699.json | python3 -m json.tool

echo 'import AH group-lines group-lines-700.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-700.json | python3 -m json.tool

echo 'import AH group-lines group-lines-701.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-701.json | python3 -m json.tool

echo 'import AH group-lines group-lines-702.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-702.json | python3 -m json.tool

echo 'import AH group-lines group-lines-703.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-703.json | python3 -m json.tool

echo 'import AH group-lines group-lines-704.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-704.json | python3 -m json.tool

echo 'import AH group-lines group-lines-705.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-705.json | python3 -m json.tool

echo 'import AH group-lines group-lines-706.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-706.json | python3 -m json.tool

echo 'import AH group-lines group-lines-707.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-707.json | python3 -m json.tool

echo 'import AH group-lines group-lines-708.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-708.json | python3 -m json.tool

echo 'import AH group-lines group-lines-709.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-709.json | python3 -m json.tool

echo 'import AH group-lines group-lines-710.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-710.json | python3 -m json.tool

echo 'import AH group-lines group-lines-711.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-711.json | python3 -m json.tool

echo 'import AH group-lines group-lines-712.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-712.json | python3 -m json.tool

echo 'import AH group-lines group-lines-713.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-713.json | python3 -m json.tool

echo 'import AH group-lines group-lines-714.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-714.json | python3 -m json.tool

echo 'import AH group-lines group-lines-715.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-715.json | python3 -m json.tool

echo 'import AH group-lines group-lines-716.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-716.json | python3 -m json.tool

echo 'import AH group-lines group-lines-717.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-717.json | python3 -m json.tool

echo 'import AH group-lines group-lines-718.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-718.json | python3 -m json.tool

echo 'import AH group-lines group-lines-719.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-719.json | python3 -m json.tool

echo 'import AH group-lines group-lines-720.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-720.json | python3 -m json.tool

echo 'import AH group-lines group-lines-721.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-721.json | python3 -m json.tool

echo 'import AH group-lines group-lines-722.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-722.json | python3 -m json.tool

echo 'import AH group-lines group-lines-723.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-723.json | python3 -m json.tool

echo 'import AH group-lines group-lines-724.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-724.json | python3 -m json.tool

echo 'import AH group-lines group-lines-725.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-725.json | python3 -m json.tool

echo 'import AH group-lines group-lines-726.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-726.json | python3 -m json.tool

echo 'import AH group-lines group-lines-727.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-727.json | python3 -m json.tool

echo 'import AH group-lines group-lines-728.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-728.json | python3 -m json.tool

echo 'import AH group-lines group-lines-729.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-729.json | python3 -m json.tool

echo 'import AH group-lines group-lines-730.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-730.json | python3 -m json.tool

echo 'import AH group-lines group-lines-731.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-731.json | python3 -m json.tool

echo 'import AH group-lines group-lines-732.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-732.json | python3 -m json.tool

echo 'import AH group-lines group-lines-733.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-733.json | python3 -m json.tool

echo 'import AH group-lines group-lines-734.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-734.json | python3 -m json.tool

echo 'import AH group-lines group-lines-735.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-735.json | python3 -m json.tool

echo 'import AH group-lines group-lines-736.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-736.json | python3 -m json.tool

echo 'import AH group-lines group-lines-737.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-737.json | python3 -m json.tool

echo 'import AH group-lines group-lines-738.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-738.json | python3 -m json.tool

echo 'import AH group-lines group-lines-739.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-739.json | python3 -m json.tool

echo 'import AH group-lines group-lines-740.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-740.json | python3 -m json.tool

echo 'import AH group-lines group-lines-741.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-741.json | python3 -m json.tool

echo 'import AH group-lines group-lines-742.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-742.json | python3 -m json.tool

echo 'import AH group-lines group-lines-743.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-743.json | python3 -m json.tool

echo 'import AH group-lines group-lines-744.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-744.json | python3 -m json.tool

echo 'import AH group-lines group-lines-745.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-745.json | python3 -m json.tool

echo 'import AH group-lines group-lines-746.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-746.json | python3 -m json.tool

echo 'import AH group-lines group-lines-747.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-747.json | python3 -m json.tool

echo 'import AH group-lines group-lines-748.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-748.json | python3 -m json.tool

echo 'import AH group-lines group-lines-749.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-749.json | python3 -m json.tool

echo 'import AH group-lines group-lines-750.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-750.json | python3 -m json.tool

echo 'import AH group-lines group-lines-751.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-751.json | python3 -m json.tool

echo 'import AH group-lines group-lines-752.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-752.json | python3 -m json.tool

echo 'import AH group-lines group-lines-753.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-753.json | python3 -m json.tool

echo 'import AH group-lines group-lines-754.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-754.json | python3 -m json.tool

echo 'import AH group-lines group-lines-755.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-755.json | python3 -m json.tool

echo 'import AH group-lines group-lines-756.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-756.json | python3 -m json.tool

echo 'import AH group-lines group-lines-757.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-757.json | python3 -m json.tool

echo 'import AH group-lines group-lines-758.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-758.json | python3 -m json.tool

echo 'import AH group-lines group-lines-759.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-759.json | python3 -m json.tool

echo 'import AH group-lines group-lines-760.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-760.json | python3 -m json.tool

echo 'import AH group-lines group-lines-761.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-761.json | python3 -m json.tool

echo 'import AH group-lines group-lines-762.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-762.json | python3 -m json.tool

echo 'import AH group-lines group-lines-763.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-763.json | python3 -m json.tool

echo 'import AH group-lines group-lines-764.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-764.json | python3 -m json.tool

echo 'import AH group-lines group-lines-765.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-765.json | python3 -m json.tool

echo 'import AH group-lines group-lines-766.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-766.json | python3 -m json.tool

echo 'import AH group-lines group-lines-767.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-767.json | python3 -m json.tool

echo 'import AH group-lines group-lines-768.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-768.json | python3 -m json.tool

echo 'import AH group-lines group-lines-769.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-769.json | python3 -m json.tool

echo 'import AH group-lines group-lines-770.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-770.json | python3 -m json.tool

echo 'import AH group-lines group-lines-771.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-771.json | python3 -m json.tool

echo 'import AH group-lines group-lines-772.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-772.json | python3 -m json.tool

echo 'import AH group-lines group-lines-773.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-773.json | python3 -m json.tool

echo 'import AH group-lines group-lines-774.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-774.json | python3 -m json.tool

echo 'import AH group-lines group-lines-775.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-775.json | python3 -m json.tool

echo 'import AH group-lines group-lines-776.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-776.json | python3 -m json.tool

echo 'import AH group-lines group-lines-777.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-777.json | python3 -m json.tool

echo 'import AH group-lines group-lines-778.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-778.json | python3 -m json.tool

echo 'import AH group-lines group-lines-779.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-779.json | python3 -m json.tool

echo 'import AH group-lines group-lines-780.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-780.json | python3 -m json.tool

echo 'import AH group-lines group-lines-781.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-781.json | python3 -m json.tool

echo 'import AH group-lines group-lines-782.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-782.json | python3 -m json.tool

echo 'import AH group-lines group-lines-783.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-783.json | python3 -m json.tool

echo 'import AH group-lines group-lines-784.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-784.json | python3 -m json.tool

echo 'import AH group-lines group-lines-785.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-785.json | python3 -m json.tool

echo 'import AH group-lines group-lines-786.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-786.json | python3 -m json.tool

echo 'import AH group-lines group-lines-787.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-787.json | python3 -m json.tool

echo 'import AH group-lines group-lines-788.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-788.json | python3 -m json.tool

echo 'import AH group-lines group-lines-789.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-789.json | python3 -m json.tool

echo 'import AH group-lines group-lines-790.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-790.json | python3 -m json.tool

echo 'import AH group-lines group-lines-791.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-791.json | python3 -m json.tool

echo 'import AH group-lines group-lines-792.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-792.json | python3 -m json.tool

echo 'import AH group-lines group-lines-793.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-793.json | python3 -m json.tool

echo 'import AH group-lines group-lines-794.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-794.json | python3 -m json.tool

echo 'import AH group-lines group-lines-795.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-795.json | python3 -m json.tool

echo 'import AH group-lines group-lines-796.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-796.json | python3 -m json.tool

echo 'import AH group-lines group-lines-797.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-797.json | python3 -m json.tool

echo 'import AH group-lines group-lines-798.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-798.json | python3 -m json.tool

echo 'import AH group-lines group-lines-799.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-799.json | python3 -m json.tool

echo 'import AH group-lines group-lines-800.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-800.json | python3 -m json.tool

echo 'import AH group-lines group-lines-801.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-801.json | python3 -m json.tool

echo 'import AH group-lines group-lines-802.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-802.json | python3 -m json.tool

echo 'import AH group-lines group-lines-803.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-803.json | python3 -m json.tool

echo 'import AH group-lines group-lines-804.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-804.json | python3 -m json.tool

echo 'import AH group-lines group-lines-805.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-805.json | python3 -m json.tool

echo 'import AH group-lines group-lines-806.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-806.json | python3 -m json.tool

echo 'import AH group-lines group-lines-807.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-807.json | python3 -m json.tool

echo 'import AH group-lines group-lines-808.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-808.json | python3 -m json.tool

echo 'import AH group-lines group-lines-809.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-809.json | python3 -m json.tool

echo 'import AH group-lines group-lines-810.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-810.json | python3 -m json.tool

echo 'import AH group-lines group-lines-811.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-811.json | python3 -m json.tool

echo 'import AH group-lines group-lines-812.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-812.json | python3 -m json.tool

echo 'import AH group-lines group-lines-813.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-813.json | python3 -m json.tool

echo 'import AH group-lines group-lines-814.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-814.json | python3 -m json.tool

echo 'import AH group-lines group-lines-815.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-815.json | python3 -m json.tool

echo 'import AH group-lines group-lines-816.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-816.json | python3 -m json.tool

echo 'import AH group-lines group-lines-817.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-817.json | python3 -m json.tool

echo 'import AH group-lines group-lines-818.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-818.json | python3 -m json.tool

echo 'import AH group-lines group-lines-819.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-819.json | python3 -m json.tool

echo 'import AH group-lines group-lines-820.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-820.json | python3 -m json.tool

echo 'import AH group-lines group-lines-821.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-821.json | python3 -m json.tool

echo 'import AH group-lines group-lines-822.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-822.json | python3 -m json.tool

echo 'import AH group-lines group-lines-823.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-823.json | python3 -m json.tool

echo 'import AH group-lines group-lines-824.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-824.json | python3 -m json.tool

echo 'import AH group-lines group-lines-825.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-825.json | python3 -m json.tool

echo 'import AH group-lines group-lines-826.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-826.json | python3 -m json.tool

echo 'import AH group-lines group-lines-827.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-827.json | python3 -m json.tool

echo 'import AH group-lines group-lines-828.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-828.json | python3 -m json.tool

echo 'import AH group-lines group-lines-829.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-829.json | python3 -m json.tool

echo 'import AH group-lines group-lines-830.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-830.json | python3 -m json.tool

echo 'import AH group-lines group-lines-831.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-831.json | python3 -m json.tool

echo 'import AH group-lines group-lines-832.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-832.json | python3 -m json.tool

echo 'import AH group-lines group-lines-833.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-833.json | python3 -m json.tool

echo 'import AH group-lines group-lines-834.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-834.json | python3 -m json.tool

echo 'import AH group-lines group-lines-835.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-835.json | python3 -m json.tool

echo 'import AH group-lines group-lines-836.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-836.json | python3 -m json.tool

echo 'import AH group-lines group-lines-837.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-837.json | python3 -m json.tool

echo 'import AH group-lines group-lines-838.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-838.json | python3 -m json.tool

echo 'import AH group-lines group-lines-839.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-839.json | python3 -m json.tool

echo 'import AH group-lines group-lines-840.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-840.json | python3 -m json.tool

echo 'import AH group-lines group-lines-841.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-841.json | python3 -m json.tool

echo 'import AH group-lines group-lines-842.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-842.json | python3 -m json.tool

echo 'import AH group-lines group-lines-843.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-843.json | python3 -m json.tool

echo 'import AH group-lines group-lines-844.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-844.json | python3 -m json.tool

echo 'import AH group-lines group-lines-845.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-845.json | python3 -m json.tool

echo 'import AH group-lines group-lines-846.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-846.json | python3 -m json.tool

echo 'import AH group-lines group-lines-847.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-847.json | python3 -m json.tool

echo 'import AH group-lines group-lines-848.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-848.json | python3 -m json.tool

echo 'import AH group-lines group-lines-849.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-849.json | python3 -m json.tool

echo 'import AH group-lines group-lines-850.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-850.json | python3 -m json.tool

echo 'import AH group-lines group-lines-851.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-851.json | python3 -m json.tool

echo 'import AH group-lines group-lines-852.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-852.json | python3 -m json.tool

echo 'import AH group-lines group-lines-853.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-853.json | python3 -m json.tool

echo 'import AH group-lines group-lines-854.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-854.json | python3 -m json.tool

echo 'import AH group-lines group-lines-855.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-855.json | python3 -m json.tool

echo 'import AH group-lines group-lines-856.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-856.json | python3 -m json.tool

echo 'import AH group-lines group-lines-857.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-857.json | python3 -m json.tool

echo 'import AH group-lines group-lines-858.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-858.json | python3 -m json.tool

echo 'import AH group-lines group-lines-859.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-859.json | python3 -m json.tool

echo 'import AH group-lines group-lines-860.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-860.json | python3 -m json.tool

echo 'import AH group-lines group-lines-861.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-861.json | python3 -m json.tool

echo 'import AH group-lines group-lines-862.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-862.json | python3 -m json.tool

echo 'import AH group-lines group-lines-863.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-863.json | python3 -m json.tool

echo 'import AH group-lines group-lines-864.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-864.json | python3 -m json.tool

echo 'import AH group-lines group-lines-865.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-865.json | python3 -m json.tool

echo 'import AH group-lines group-lines-866.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-866.json | python3 -m json.tool

echo 'import AH group-lines group-lines-867.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-867.json | python3 -m json.tool

echo 'import AH group-lines group-lines-868.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-868.json | python3 -m json.tool

echo 'import AH group-lines group-lines-869.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-869.json | python3 -m json.tool

echo 'import AH group-lines group-lines-870.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-870.json | python3 -m json.tool

echo 'import AH group-lines group-lines-871.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-871.json | python3 -m json.tool

echo 'import AH group-lines group-lines-872.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-872.json | python3 -m json.tool

echo 'import AH group-lines group-lines-873.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-873.json | python3 -m json.tool

echo 'import AH group-lines group-lines-874.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-874.json | python3 -m json.tool

echo 'import AH group-lines group-lines-875.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-875.json | python3 -m json.tool

echo 'import AH group-lines group-lines-876.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-876.json | python3 -m json.tool

echo 'import AH group-lines group-lines-877.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-877.json | python3 -m json.tool

echo 'import AH group-lines group-lines-878.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-878.json | python3 -m json.tool

echo 'import AH group-lines group-lines-879.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-879.json | python3 -m json.tool

echo 'import AH group-lines group-lines-880.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-880.json | python3 -m json.tool

echo 'import AH group-lines group-lines-881.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-881.json | python3 -m json.tool

echo 'import AH group-lines group-lines-882.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-882.json | python3 -m json.tool

echo 'import AH group-lines group-lines-883.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-883.json | python3 -m json.tool

echo 'import AH group-lines group-lines-884.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-884.json | python3 -m json.tool

echo 'import AH group-lines group-lines-885.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-885.json | python3 -m json.tool

echo 'import AH group-lines group-lines-886.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-886.json | python3 -m json.tool

echo 'import AH group-lines group-lines-887.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-887.json | python3 -m json.tool

echo 'import AH group-lines group-lines-888.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-888.json | python3 -m json.tool

echo 'import AH group-lines group-lines-889.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-889.json | python3 -m json.tool

echo 'import AH group-lines group-lines-890.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-890.json | python3 -m json.tool

echo 'import AH group-lines group-lines-891.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-891.json | python3 -m json.tool

echo 'import AH group-lines group-lines-892.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-892.json | python3 -m json.tool

echo 'import AH group-lines group-lines-893.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-893.json | python3 -m json.tool

echo 'import AH group-lines group-lines-894.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-894.json | python3 -m json.tool

echo 'import AH group-lines group-lines-895.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-895.json | python3 -m json.tool

echo 'import AH group-lines group-lines-896.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-896.json | python3 -m json.tool

echo 'import AH group-lines group-lines-897.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-897.json | python3 -m json.tool

echo 'import AH group-lines group-lines-898.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-898.json | python3 -m json.tool

echo 'import AH group-lines group-lines-899.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-899.json | python3 -m json.tool

echo 'import AH group-lines group-lines-900.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-900.json | python3 -m json.tool

echo 'import AH group-lines group-lines-901.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-901.json | python3 -m json.tool

echo 'import AH group-lines group-lines-902.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-902.json | python3 -m json.tool

echo 'import AH group-lines group-lines-903.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-903.json | python3 -m json.tool

echo 'import AH group-lines group-lines-904.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-904.json | python3 -m json.tool

echo 'import AH group-lines group-lines-905.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-905.json | python3 -m json.tool

echo 'import AH group-lines group-lines-906.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-906.json | python3 -m json.tool

echo 'import AH group-lines group-lines-907.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-907.json | python3 -m json.tool

echo 'import AH group-lines group-lines-908.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-908.json | python3 -m json.tool

echo 'import AH group-lines group-lines-909.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-909.json | python3 -m json.tool

echo 'import AH group-lines group-lines-910.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-910.json | python3 -m json.tool

echo 'import AH group-lines group-lines-911.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-911.json | python3 -m json.tool

echo 'import AH group-lines group-lines-912.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-912.json | python3 -m json.tool

echo 'import AH group-lines group-lines-913.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-913.json | python3 -m json.tool

echo 'import AH group-lines group-lines-914.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-914.json | python3 -m json.tool

echo 'import AH group-lines group-lines-915.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-915.json | python3 -m json.tool

echo 'import AH group-lines group-lines-916.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-916.json | python3 -m json.tool

echo 'import AH group-lines group-lines-917.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-917.json | python3 -m json.tool

echo 'import AH group-lines group-lines-918.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-918.json | python3 -m json.tool

echo 'import AH group-lines group-lines-919.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-919.json | python3 -m json.tool

echo 'import AH group-lines group-lines-920.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-920.json | python3 -m json.tool

echo 'import AH group-lines group-lines-921.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-921.json | python3 -m json.tool

echo 'import AH group-lines group-lines-922.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-922.json | python3 -m json.tool

echo 'import AH group-lines group-lines-923.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-923.json | python3 -m json.tool

echo 'import AH group-lines group-lines-924.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-924.json | python3 -m json.tool

echo 'import AH group-lines group-lines-925.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-925.json | python3 -m json.tool

echo 'import AH group-lines group-lines-926.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-926.json | python3 -m json.tool

echo 'import AH group-lines group-lines-927.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-927.json | python3 -m json.tool

echo 'import AH group-lines group-lines-928.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-928.json | python3 -m json.tool

echo 'import AH group-lines group-lines-929.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-929.json | python3 -m json.tool

echo 'import AH group-lines group-lines-930.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-930.json | python3 -m json.tool

echo 'import AH group-lines group-lines-931.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-931.json | python3 -m json.tool

echo 'import AH group-lines group-lines-932.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-932.json | python3 -m json.tool

echo 'import AH group-lines group-lines-933.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-933.json | python3 -m json.tool

echo 'import AH group-lines group-lines-934.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-934.json | python3 -m json.tool

echo 'import AH group-lines group-lines-935.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-935.json | python3 -m json.tool

echo 'import AH group-lines group-lines-936.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-936.json | python3 -m json.tool

echo 'import AH group-lines group-lines-937.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-937.json | python3 -m json.tool

echo 'import AH group-lines group-lines-938.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-938.json | python3 -m json.tool

echo 'import AH group-lines group-lines-939.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-939.json | python3 -m json.tool

echo 'import AH group-lines group-lines-940.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-940.json | python3 -m json.tool

echo 'import AH group-lines group-lines-941.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-941.json | python3 -m json.tool

echo 'import AH group-lines group-lines-942.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-942.json | python3 -m json.tool

echo 'import AH group-lines group-lines-943.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-943.json | python3 -m json.tool

echo 'import AH group-lines group-lines-944.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-944.json | python3 -m json.tool

echo 'import AH group-lines group-lines-945.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-945.json | python3 -m json.tool

echo 'import AH group-lines group-lines-946.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-946.json | python3 -m json.tool

echo 'import AH group-lines group-lines-947.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-947.json | python3 -m json.tool

echo 'import AH group-lines group-lines-948.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-948.json | python3 -m json.tool

echo 'import AH group-lines group-lines-949.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-949.json | python3 -m json.tool

echo 'import AH group-lines group-lines-950.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-950.json | python3 -m json.tool

echo 'import AH group-lines group-lines-951.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-951.json | python3 -m json.tool

echo 'import AH group-lines group-lines-952.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-952.json | python3 -m json.tool

echo 'import AH group-lines group-lines-953.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-953.json | python3 -m json.tool

echo 'import AH group-lines group-lines-954.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-954.json | python3 -m json.tool

echo 'import AH group-lines group-lines-955.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-955.json | python3 -m json.tool

echo 'import AH group-lines group-lines-956.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-956.json | python3 -m json.tool

echo 'import AH group-lines group-lines-957.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-957.json | python3 -m json.tool

echo 'import AH group-lines group-lines-958.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-958.json | python3 -m json.tool

echo 'import AH group-lines group-lines-959.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-959.json | python3 -m json.tool

echo 'import AH group-lines group-lines-960.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-960.json | python3 -m json.tool

echo 'import AH group-lines group-lines-961.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-961.json | python3 -m json.tool

echo 'import AH group-lines group-lines-962.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-962.json | python3 -m json.tool

echo 'import AH group-lines group-lines-963.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-963.json | python3 -m json.tool

echo 'import AH group-lines group-lines-964.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-964.json | python3 -m json.tool

echo 'import AH group-lines group-lines-965.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-965.json | python3 -m json.tool

echo 'import AH group-lines group-lines-966.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-966.json | python3 -m json.tool

echo 'import AH group-lines group-lines-967.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-967.json | python3 -m json.tool

echo 'import AH group-lines group-lines-968.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-968.json | python3 -m json.tool

echo 'import AH group-lines group-lines-969.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-969.json | python3 -m json.tool

echo 'import AH group-lines group-lines-970.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-970.json | python3 -m json.tool

echo 'import AH group-lines group-lines-971.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-971.json | python3 -m json.tool

echo 'import AH group-lines group-lines-972.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-972.json | python3 -m json.tool

echo 'import AH group-lines group-lines-973.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-973.json | python3 -m json.tool

echo 'import AH group-lines group-lines-974.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-974.json | python3 -m json.tool

echo 'import AH group-lines group-lines-975.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-975.json | python3 -m json.tool

echo 'import AH group-lines group-lines-976.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-976.json | python3 -m json.tool

echo 'import AH group-lines group-lines-977.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-977.json | python3 -m json.tool

echo 'import AH group-lines group-lines-978.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-978.json | python3 -m json.tool

echo 'import AH group-lines group-lines-979.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-979.json | python3 -m json.tool

echo 'import AH group-lines group-lines-980.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-980.json | python3 -m json.tool

echo 'import AH group-lines group-lines-981.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-981.json | python3 -m json.tool

echo 'import AH group-lines group-lines-982.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-982.json | python3 -m json.tool

echo 'import AH group-lines group-lines-983.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-983.json | python3 -m json.tool

echo 'import AH group-lines group-lines-984.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-984.json | python3 -m json.tool

echo 'import AH group-lines group-lines-985.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-985.json | python3 -m json.tool

echo 'import AH group-lines group-lines-986.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-986.json | python3 -m json.tool

echo 'import AH group-lines group-lines-987.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-987.json | python3 -m json.tool

echo 'import AH group-lines group-lines-988.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-988.json | python3 -m json.tool

echo 'import AH group-lines group-lines-989.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-989.json | python3 -m json.tool

echo 'import AH group-lines group-lines-990.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-990.json | python3 -m json.tool

echo 'import AH group-lines group-lines-991.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-991.json | python3 -m json.tool

echo 'import AH group-lines group-lines-992.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-992.json | python3 -m json.tool

echo 'import AH group-lines group-lines-993.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-993.json | python3 -m json.tool

echo 'import AH group-lines group-lines-994.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-994.json | python3 -m json.tool

echo 'import AH group-lines group-lines-995.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-995.json | python3 -m json.tool

echo 'import AH group-lines group-lines-996.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-996.json | python3 -m json.tool

echo 'import AH group-lines group-lines-997.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-997.json | python3 -m json.tool

echo 'import AH group-lines group-lines-998.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-998.json | python3 -m json.tool

echo 'import AH group-lines group-lines-999.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-999.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1000.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1000.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1001.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1001.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1002.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1002.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1003.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1003.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1004.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1004.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1005.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1005.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1006.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1006.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1007.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1007.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1008.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1008.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1009.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1009.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1010.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1010.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1011.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1011.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1012.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1012.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1013.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1013.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1014.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1014.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1015.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1015.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1016.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1016.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1017.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1017.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1018.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1018.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1019.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1019.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1020.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1020.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1021.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1021.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1022.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1022.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1023.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1023.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1024.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1024.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1025.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1025.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1026.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1026.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1027.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1027.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1028.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1028.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1029.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1029.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1030.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1030.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1031.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1031.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1032.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1032.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1033.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1033.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1034.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1034.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1035.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1035.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1036.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1036.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1037.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1037.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1038.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1038.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1039.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1039.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1040.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1040.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1041.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1041.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1042.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1042.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1043.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1043.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1044.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1044.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1045.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1045.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1046.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1046.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1047.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1047.json | python3 -m json.tool

echo 'import AH group-lines group-lines-1048.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=false" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/AH/group-lines-1048.json | python3 -m json.tool
