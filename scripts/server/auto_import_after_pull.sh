#!/bin/bash
# v3: pull \u5b8c\u6210\u540e \u2192 backup data_admission_group_plan \u2192 import \u2192 smoke
set -e
export MYSQL_PWD=$(grep '^GZLY_DB_PASSWORD=' /etc/gzly/gzly.env | cut -d= -f2-)
LOG=/root/gzly_scraper/logs/auto_import_$(date +%H%M%S).log
exec > "$LOG" 2>&1
TS=$(date +%Y%m%d%H%M%S)
echo "[auto-import v3] start $(date) ts=$TS"

while pgrep -f 'zjzw_pull_special_v1.py' > /dev/null; do
  ALIVE=$(pgrep -f 'zjzw_pull_special_v1.py' | wc -l)
  echo "[$(date +%H:%M:%S)] waiting, $ALIVE pull processes alive"
  sleep 120
done
echo "[auto-import v3] all pulls done $(date)"

SC_CSV=$(ls -t /root/gzly_scraper/sichuan_2025/draft/zjzw_special_sc_2025_*.csv | head -1)
AH_CSV=$(ls -t /root/gzly_scraper/sichuan_2025/draft/zjzw_special_ah_2025_*.csv | head -1)
SC_LOG=$(ls -t /root/gzly_scraper/logs/special_sc_*.log | head -1)
AH_LOG=$(ls -t /root/gzly_scraper/logs/special_ah_*.log | head -1)

SC_OK=0; AH_OK=0
[ -f "$SC_LOG" ] && grep -q "^DONE SC:" "$SC_LOG" && SC_OK=1
[ -f "$AH_LOG" ] && grep -q "^DONE AH:" "$AH_LOG" && AH_OK=1
echo "SC CSV: $SC_CSV ($(wc -l < "$SC_CSV") lines) OK=$SC_OK"
echo "AH CSV: $AH_CSV ($(wc -l < "$AH_CSV") lines) OK=$AH_OK"

mkdir -p /opt/gzly/backend/backup/data
BACKUP=/opt/gzly/backend/backup/data/group_plan_before_special_import_${TS}.sql
echo "[auto-import v3] backup before import \u2192 $BACKUP"
mysqldump --no-tablespaces --extended-insert=false gzly data_admission_group_plan \
  --where="province_code IN ('SC','AH') AND year=2025" > "$BACKUP" 2>/dev/null
echo "backup size: $(wc -l < "$BACKUP") lines"

if [ "$SC_OK" -eq 1 ]; then
  echo "[auto-import v3] SC import..."
  python3.11 /root/gzly_scraper/anhui_bootstrap/import_special_to_group_plan.py \
    --province SC --year 2025 --csv "$SC_CSV"
else
  echo "[auto-import v3] SC pull NOT clean, SKIP import"
  tail -10 "$SC_LOG"
fi

if [ "$AH_OK" -eq 1 ]; then
  echo "[auto-import v3] AH import..."
  python3.11 /root/gzly_scraper/anhui_bootstrap/import_special_to_group_plan.py \
    --province AH --year 2025 --csv "$AH_CSV"
else
  echo "[auto-import v3] AH pull NOT clean, SKIP import"
  tail -10 "$AH_LOG"
fi

echo "[auto-import v3] final DB stats:"
mysql -uroot gzly -e "SELECT province_code, year, batch, COUNT(*) c FROM data_admission_group_plan WHERE province_code IN ('SC','AH') AND year=2025 GROUP BY province_code, year, batch ORDER BY province_code, c DESC;"

# Smoke \u53ea\u8981 AH \u6ee1\u8db3 OK \u5c31\u9a8c
if [ "$AH_OK" -eq 1 ]; then
  echo "[auto-import v3] smoke AH+AH_BENKE 580 \u7269\u5316\u751f..."
  sleep 3
  curl -sS -X POST "http://127.0.0.1:8090/api/volunteer/recommend" \
    -H 'Content-Type: application/json' -H "X-Safety-Code: AUTOV3$RANDOM" \
    --data '{"provinceCode":"AH","batchCode":"AH_BENKE","totalScore":580,"firstSubject":"\u7269\u7406","resubjects":["\u5316\u5b66","\u751f\u7269"],"candidateType":"\u666e\u901a\u7c7b","strategyMode":"\u5747\u8861\u578b","decisionPriority":"\u4e13\u4e1a\u4f18\u5148","careerGoal":"\u5c31\u4e1a\u4f18\u5148","tuitionBudget":"\u5747\u8861\u9884\u7b97","acceptPrivate":false,"acceptSinoForeign":false,"agreedDisclaimer":true,"disclaimerVersion":"2026-04-27-v1"}' \
    | python3 -c '
import sys, json
d = json.load(sys.stdin); dt = d.get("data") or {}; items = dt.get("items", [])
print("smoke code=", d.get("code"), "items=", len(items))
if items:
    f = items[0]
    gms = f.get("groupMajors") or []
    print("first:", f.get("universityName"), f.get("groupCode"), "groupMajors=", len(gms))
    if gms: print("  majors[:3]:", gms[:3])'
fi
echo "[auto-import v3] DONE $(date)"
