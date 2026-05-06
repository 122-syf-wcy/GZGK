#!/bin/bash
# 查看爬取进度
LOG="/Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts/data/scrape_log.txt"
TOTAL=2198

if [ ! -f "$LOG" ]; then
    echo "日志文件不存在"
    exit 1
fi

DONE=$(grep -c '^\  \[' "$LOG" 2>/dev/null || echo 0)
FAIL=$(grep -c '0 条' "$LOG" 2>/dev/null || echo 0)
RUNNING=$(ps aux | grep scrape_production | grep -v grep | wc -l | tr -d ' ')
PCT=$((DONE * 100 / TOTAL))

echo "━━━ 爬取进度 ━━━"
echo "  完成: $DONE / $TOTAL ($PCT%)"
echo "  失败: $FAIL"
echo "  进程: $([ "$RUNNING" -gt 0 ] && echo '运行中 ✅' || echo '已停止 ❌')"
echo ""
echo "最近 5 条:"
tail -5 "$LOG"
