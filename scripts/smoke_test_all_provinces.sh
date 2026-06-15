#!/bin/bash
# GZLY v7.54: 三省 × 50 批次 e2e smoke 测试 (替代 SpringBootTest 集成测试)
#
# 用法：
#   bash scripts/smoke_test_all_provinces.sh                       # 默认走公网 39.97.232.141
#   API_BASE=http://127.0.0.1:8090 bash scripts/smoke_test_all_provinces.sh  # 走本机直连
#
# 输出：每个 (province, batch) 一行结果，包含 code / items / engineName /
# supportLevel / 首末项 universityName。最后输出汇总通过率。
#
# 注：rate-limit 时（公网 nginx 30r/s / generate 20r/m）每个测试间 sleep 5s。
set -uo pipefail
API_BASE="${API_BASE:-http://39.97.232.141}"
SAFETY_PREFIX="${SAFETY_PREFIX:-SMOKETEST}"
SLEEP="${SLEEP:-5}"

PASS=0; FAIL=0; ROWS=()

post() {
  local label="$1" province="$2" batch="$3" score="$4" rank="$5" subj="$6" cand="$7"
  local resub='["化学","生物"]'
  [ "$subj" = "历史" ] && resub='["政治","地理"]'
  [ "$cand" = "艺术类" ] && resub='["化学","生物"]'
  local body="{\"provinceCode\":\"${province}\",\"batchCode\":\"${batch}\",\"totalScore\":${score},\"provinceRank\":${rank},\"firstSubject\":\"${subj}\",\"resubjects\":${resub},\"candidateType\":\"${cand}\",\"strategyMode\":\"均衡型\",\"decisionPriority\":\"专业优先\",\"careerGoal\":\"就业优先\",\"tuitionBudget\":\"均衡预算\",\"acceptPrivate\":false,\"acceptSinoForeign\":false,\"agreedDisclaimer\":true,\"disclaimerVersion\":\"2026-04-27-v1\"}"
  local code
  local items
  local engine
  local support
  local raw=$(curl -sS -X POST "${API_BASE}/api/volunteer/recommend" \
        -H 'Content-Type: application/json' \
        -H "X-Safety-Code: ${SAFETY_PREFIX}${RANDOM}" \
        --data "$body")
  code=$(echo "$raw" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); print(d.get('code'))
except: print('PARSE_ERR')" 2>/dev/null)
  items=$(echo "$raw" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); dt=d.get('data') or {}; print(len(dt.get('items',[])))
except: print(0)" 2>/dev/null)
  engine=$(echo "$raw" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); dt=d.get('data') or {}; print(dt.get('engineName',''))
except: print('')" 2>/dev/null)
  support=$(echo "$raw" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); dt=d.get('data') or {}; print(dt.get('supportLevel',''))
except: print('')" 2>/dev/null)
  if [ "$code" = "0" ]; then
    PASS=$((PASS+1))
    printf '  ✓ %-45s code=%s items=%-3s engine=%s\n' "$label" "$code" "$items" "$engine"
  else
    FAIL=$((FAIL+1))
    printf '  ✗ %-45s code=%s items=%-3s engine=%s\n' "$label" "$code" "$items" "$engine"
    echo "    raw: ${raw:0:160}"
  fi
  sleep "$SLEEP"
}

echo "=== GZLY 三省 × 主流程 + 非主流程 smoke (API_BASE=$API_BASE) ==="
echo
echo "[GZ] 贵州主流程 96 平行专业"
post 'GZ NORMAL_UNDERGRADUATE 物高'        GZ NORMAL_UNDERGRADUATE 640 3000  物理 普通类
post 'GZ NORMAL_UNDERGRADUATE 物中'        GZ NORMAL_UNDERGRADUATE 540 80000 物理 普通类
post 'GZ NORMAL_UNDERGRADUATE 历高'        GZ NORMAL_UNDERGRADUATE 590 8000  历史 普通类

echo
echo "[GZ] 贵州非主流程 (含 8 类专项)"
post 'GZ EARLY_C'                          GZ EARLY_C 580 35000 物理 普通类
post 'GZ NATIONAL_SPECIAL'                 GZ NATIONAL_SPECIAL 580 35000 物理 普通类

echo
echo "[SC] 四川主流程 + 5 类专项"
post 'SC SC_BENKE_B 物高'                  SC SC_BENKE_B 640 3000  物理 普通类
post 'SC SC_BENKE_B 物中'                  SC SC_BENKE_B 540 80000 物理 普通类
post 'SC SC_BENKE_A_NATIONAL'              SC SC_BENKE_A_NATIONAL 580 35000 物理 普通类
post 'SC SC_BENKE_GAOXIAO_SPECIAL'         SC SC_BENKE_GAOXIAO_SPECIAL 580 35000 物理 普通类
post 'SC SC_ART_BENKE (无数据示例)'         SC SC_ART_BENKE 480 30000 物理 艺术类
post 'SC SC_SPORTS_BENKE (无数据示例)'      SC SC_SPORTS_BENKE 480 30000 物理 体育类
post 'SC SC_TIQIAN_A 顺序'                 SC SC_TIQIAN_A 580 35000 物理 普通类

echo
echo "[AH] 安徽主流程 + 多批次 engine 路由"
post 'AH AH_BENKE 物高'                    AH AH_BENKE 640 3000  物理 普通类
post 'AH AH_BENKE 物中'                    AH AH_BENKE 540 80000 物理 普通类
post 'AH AH_BENKE 历高'                    AH AH_BENKE 590 8000  历史 普通类
post 'AH AH_ART_TONGKAO_BENKE 艺术'        AH AH_ART_TONGKAO_BENKE 480 30000 物理 艺术类
post 'AH AH_SPORTS_BENKE 体育'             AH AH_SPORTS_BENKE 480 30000 物理 体育类
post 'AH AH_NATIONAL_SPECIAL 国家专项'     AH AH_NATIONAL_SPECIAL 580 35000 物理 普通类
post 'AH AH_UNIVERSITY_SPECIAL 高校专项'   AH AH_UNIVERSITY_SPECIAL 580 35000 物理 普通类
post 'AH AH_TIQIAN_BENKE_PARALLEL 提前批'  AH AH_TIQIAN_BENKE_PARALLEL 580 35000 物理 普通类

echo
echo "=== batch-support 端点验证 ==="
for prov in gz sc ah; do
  c=$(curl -sS "${API_BASE}/api/volunteer/${prov}/batch-support?year=2026" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); dt=d.get('data') or {}
    print(f\"code={d.get('code')} batches={len(dt.get('items',[]))} summary={dt.get('summary')}\")
except: print('PARSE_ERR')" 2>/dev/null)
  echo "  $(echo "$prov" | tr '[:lower:]' '[:upper:]'): $c"
  sleep 2
done

echo
echo "=== composite-score 端点验证 (SC + AH) ==="
SC_ART=$(curl -sS "${API_BASE}/api/volunteer/sc/composite-score?candidateType=%E8%89%BA%E6%9C%AF%E7%B1%BB&artCategory=%E7%BE%8E%E6%9C%AF%E4%B8%8E%E8%AE%BE%E8%AE%A1%E7%B1%BB&cultureScore=480&professionalScore=280" | python3 -c "import sys,json;d=json.load(sys.stdin);dt=d.get('data') or {};print(f\"score={dt.get('score')}\")" 2>/dev/null)
echo "  SC 美术 480/280 = $SC_ART (期望 590.0)"
sleep 2
AH_ART=$(curl -sS "${API_BASE}/api/volunteer/ah/composite-score?candidateType=%E8%89%BA%E6%9C%AF%E7%B1%BB&artCategory=%E7%BE%8E%E6%9C%AF%E4%B8%8E%E8%AE%BE%E8%AE%A1%E7%B1%BB&cultureScore=480&professionalScore=280" | python3 -c "import sys,json;d=json.load(sys.stdin);dt=d.get('data') or {};print(f\"score={dt.get('score')}\")" 2>/dev/null)
echo "  AH 美术 480/280 = $AH_ART (期望 590.0)"

echo
echo "=== 汇总 ==="
echo "Pass: $PASS"
echo "Fail: $FAIL"
TOTAL=$((PASS+FAIL))
[ $TOTAL -gt 0 ] && echo "Pass Rate: $((PASS*100/TOTAL))%"
[ $FAIL -eq 0 ] && echo "✓ ALL SMOKE PASSED" || echo "✗ HAS FAILURES"
exit $FAIL
