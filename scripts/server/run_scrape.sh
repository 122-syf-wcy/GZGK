#!/bin/bash
# 启动爬取（后台运行）
WORK_DIR="/root/gzly_scraper"
cd "$WORK_DIR"
source venv/bin/activate

echo "启动爬取..."
nohup python3 -u scrape_production.py \
    --from-cache --skip-filter --headless true --delay 1.0 \
    > "$WORK_DIR/data/scrape_log.txt" 2>&1 &

echo "PID: $!"
echo "日志: $WORK_DIR/data/scrape_log.txt"
echo ""
echo "查看进度: bash $WORK_DIR/check_progress.sh"
echo "拉回数据: scp -r root@39.97.232.141:$WORK_DIR/data/ ./data/"
