#!/bin/bash
# 等待 SC/AH special pull 全部完成后自动 import + 后端 smoke
set -e
export MYSQL_PWD=$(grep '^GZLY_DB_PASSWORD=' /etc/gzly/gzly.env | cut -d= -f2-)
LOG=/root/gzly_scraper/logs/auto_import_$(date +%H%M%S).log
exec > "$LOG" 2>&1
echo "[auto-import] start $(date)"

# 等待两个 pull 进程都结束
while pgrep -f 'zjzw_pull_special_v1.py' > /dev/null; do
  ALIVE=$(pgrep -f 'zjzw_pull_special_v1.py' | wc -l)
  echo "[$(date +%H:%M:%S)] waiting, $ALIVE pull processes alive"
  sleep 120
done
echo "[auto-import] all pulls done $(date)"

# 找最新的两个 csv
SC_CSV=$(ls -t /root/gzly_scraper/sichuan_2025/draft/zjzw_special_sc_2025_*.csv | head -1)
AH_CSV=$(ls -t /root/gzly_scraper/sichuan_2025/draft/zjzw_special_ah_2025_*.csv | head -1)
echo "SC CSV: $SC_CSV ($(wc -l < "$SC_CSV") lines)"
echo "AH CSV: $AH_CSV ($(wc -l < "$AH_CSV") lines)"

echo "[auto-import] SC import..."
python3.11 /root/gzly_scraper/anhui_bootstrap/import_special_to_group_plan.py \
  --province SC --year 2025 --csv "$SC_CSV"
echo

echo "[auto-import] AH import..."
python3.11 /root/gzly_scraper/anhui_bootstrap/import_special_to_group_plan.py \
  --province AH --year 2025 --csv "$AH_CSV"
echo

# DB stats
echo "[auto-import] final DB stats:"
mysql -uroot gzly -e "SELECT province_code, year, batch, COUNT(*) c FROM data_admission_group_plan WHERE province_code IN ('SC','AH') AND year=2025 GROUP BY province_code, year, batch ORDER BY province_code, c DESC;"

# 后端 smoke 验证
echo "[auto-import] smoke recommend AH+AH_BENKE 580/物化生..."
sleep 3
curl -sS -X POST "http://127.0.0.1:8090/api/volunteer/recommend" \
  -H 'Content-Type: application/json' -H "X-Safety-Code: AUTOIMP$RANDOM" \
  --data '{"provinceCode":"AH","batchCode":"AH_BENKE","totalScore":580,"firstSubject":"物理","resubjects":["化学","生物"],"candidateType":"普通类","strategyMode":"均衡型","decisionPriority":"专业优先","careerGoal":"就业优先","tuitionBudget":"均衡预算","acceptPrivate":false,"acceptSinoForeign":false,"agreedDisclaimer":true,"disclaimerVersion":"2026-04-27-v1"}' \
  | python3 -c '
import sys, json
d = json.load(sys.stdin); dt = d.get("data") or {}; items = dt.get("items", [])
print("recommend code=", d.get("code"), "items=", len(items))
if items:
    f = items[0]
    print("first:", f.get("universityName"), "groupMajors=", len(f.get("groupMajors") or []))'

echo "[auto-import] DONE $(date)"
