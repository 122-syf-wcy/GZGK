#!/usr/bin/env bash
set -Eeuo pipefail
: "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"
ADMIN_BASE="${ADMIN_BASE:-http://127.0.0.1:8090/api/admin/province-data/SC}"

echo 'dry-run SC score_rank score-rank-历史类-01.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-01.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-02.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-02.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-03.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-03.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-04.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-04.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-05.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-05.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-06.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-06.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-07.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-07.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-08.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-08.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-09.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-09.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-10.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-10.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-11.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-11.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-12.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-12.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-13.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-13.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-14.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-14.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-15.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-15.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-16.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-16.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-17.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-17.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-历史类-18.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-历史类-18.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-01.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-01.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-02.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-02.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-03.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-03.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-04.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-04.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-05.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-05.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-06.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-06.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-07.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-07.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-08.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-08.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-09.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-09.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-10.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-10.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-11.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-11.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-12.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-12.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-13.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-13.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-14.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-14.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-15.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-15.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-16.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-16.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-17.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-17.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-18.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-18.json | python3 -m json.tool

echo 'dry-run SC score_rank score-rank-物理类-19.json'
curl -sS -X POST "$ADMIN_BASE/score-rank/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/score-rank-物理类-19.json | python3 -m json.tool

echo 'dry-run SC group-lines group-lines-001.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-lines-001.json | python3 -m json.tool

echo 'dry-run SC group-lines group-lines-002.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-lines-002.json | python3 -m json.tool

echo 'dry-run SC group-lines group-lines-003.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-lines-003.json | python3 -m json.tool

echo 'dry-run SC group-lines group-lines-004.json'
curl -sS -X POST "$ADMIN_BASE/group-lines/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-lines-004.json | python3 -m json.tool

echo 'dry-run SC group-plans group-plans-001.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-plans-001.json | python3 -m json.tool

echo 'dry-run SC group-plans group-plans-002.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-plans-002.json | python3 -m json.tool

echo 'dry-run SC group-plans group-plans-003.json'
curl -sS -X POST "$ADMIN_BASE/group-plans/import?dryRun=true" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data-binary @/Users/dongsiwei/Desktop/skills/projects/GZLY/scripts/server/province_group_2025/payloads/SC/group-plans-003.json | python3 -m json.tool
