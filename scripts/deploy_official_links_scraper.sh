#!/bin/bash
# 部署官方入口采集脚本到采集服务器
set -e

SERVER="${SERVER:-39.97.232.141}"
USER="${USER:-root}"
REMOTE_DIR="/root/gzly_scraper"
LOCAL_DIR="$(cd "$(dirname "$0")" && pwd)"
PROXY_LIST="${1:-${SCRAPER_PROXY_LIST:-}}"

echo "=== 部署官方入口采集脚本到 $SERVER ==="

if [[ -n "${SSHPASS:-}" ]]; then
  SSH_PREFIX=(sshpass -e)
else
  SSH_PREFIX=()
fi

echo "[1] 上传脚本..."
"${SSH_PREFIX[@]}" scp -o StrictHostKeyChecking=accept-new \
  "$LOCAL_DIR/scrape_official_links.py" \
  "$LOCAL_DIR/server/setup.sh" \
  "$LOCAL_DIR/server/import_official_links.sh" \
  "$LOCAL_DIR/server/run_official_links_pipeline.sh" \
  "$USER@$SERVER:/tmp/"

echo "[2] 安装依赖并发布..."
"${SSH_PREFIX[@]}" ssh -o StrictHostKeyChecking=accept-new "$USER@$SERVER" <<'REMOTE_SCRIPT'
set -e
WORK_DIR="/root/gzly_scraper"
mkdir -p "$WORK_DIR/data/official_links" "$WORK_DIR/data/export" "$WORK_DIR/server"
cp /tmp/scrape_official_links.py "$WORK_DIR/"
cp /tmp/setup.sh "$WORK_DIR/"
cp /tmp/import_official_links.sh "$WORK_DIR/server/"
cp /tmp/run_official_links_pipeline.sh "$WORK_DIR/server/"
chmod +x "$WORK_DIR"/*.sh
chmod +x "$WORK_DIR/server/"*.sh
bash "$WORK_DIR/setup.sh"
echo "部署完成: $WORK_DIR/scrape_official_links.py"
REMOTE_SCRIPT

echo "[3] 启动采集..."
"${SSH_PREFIX[@]}" ssh -o StrictHostKeyChecking=accept-new "$USER@$SERVER" \
  "export SCRAPER_PROXY_LIST='$PROXY_LIST'; cd /root/gzly_scraper && bash server/run_official_links_pipeline.sh"

echo ""
echo "查看进度: ssh root@$SERVER 'tail -40 $REMOTE_DIR/data/official_links/official_links.log'"
echo "导入日志: ssh root@$SERVER 'tail -40 $REMOTE_DIR/data/official_links/official_links_import.log'"
echo "拉回结果: scp root@$SERVER:$REMOTE_DIR/data/official_links/official_links_results.json ./scripts/data/official_links/"
echo "拉回SQL: scp root@$SERVER:$REMOTE_DIR/data/export/official_links.sql ./scripts/data/export/"
