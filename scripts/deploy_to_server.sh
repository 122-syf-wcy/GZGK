#!/bin/bash
# 一键部署爬虫到服务器（并行版，5年全量）
SERVER="39.97.232.141"
USER="root"
REMOTE_DIR="/root/gzly_scraper"
LOCAL_DIR="$(cd "$(dirname "$0")" && pwd)"
SSH_STRICT_HOST_KEY_CHECKING="${SSH_STRICT_HOST_KEY_CHECKING:-accept-new}"

echo "=== 部署并行爬虫到 $SERVER ==="

# 1. 上传脚本
echo "[1] 上传文件..."
scp -o StrictHostKeyChecking="$SSH_STRICT_HOST_KEY_CHECKING" \
    "$LOCAL_DIR/scrape_parallel.py" \
    "$LOCAL_DIR/server/setup.sh" \
    "$USER@$SERVER:/tmp/"

# 2. 上传学校数据
scp -o StrictHostKeyChecking="$SSH_STRICT_HOST_KEY_CHECKING" \
    "$LOCAL_DIR/data/guizhou_schools.json" \
    "$USER@$SERVER:/tmp/guizhou_schools.json"

# 3. 远程执行部署
echo "[2] 远程安装环境..."
ssh -o StrictHostKeyChecking="$SSH_STRICT_HOST_KEY_CHECKING" "$USER@$SERVER" << 'REMOTE_SCRIPT'
set -e
WORK_DIR="/root/gzly_scraper"
mkdir -p "$WORK_DIR/data/score_lines" "$WORK_DIR/data/universities" "$WORK_DIR/data/export"

cp /tmp/scrape_parallel.py "$WORK_DIR/"
cp /tmp/setup.sh "$WORK_DIR/"
cp /tmp/guizhou_schools.json "$WORK_DIR/data/"
chmod +x "$WORK_DIR"/*.sh

bash "$WORK_DIR/setup.sh"

echo ""
echo "[3] 启动全量爬取（2021-2025，跳过已有的2025）..."
cd "$WORK_DIR"
source venv/bin/activate
nohup python3 -u scrape_parallel.py \
    --workers 4 --skip-years 2025 \
    --headless true --delay 1.5 \
    > data/scrape_5y_log.txt 2>&1 &

echo "PID: $!"
REMOTE_SCRIPT

echo ""
echo "部署完成!"
echo "查看进度: ssh $USER@$SERVER 'tail -30 $REMOTE_DIR/data/scrape_5y_log.txt'"
echo "拉回数据: scp -r $USER@$SERVER:$REMOTE_DIR/data/export/ $LOCAL_DIR/data_server/"
