#!/bin/bash
# 服务器端安装 + 启动爬取
set -e

echo "=== GZLY 爬虫服务器部署 ==="
WORK_DIR="/root/gzly_scraper"
mkdir -p "$WORK_DIR/data/score_lines" "$WORK_DIR/data/universities" "$WORK_DIR/data/export"

# 1. 安装 Python3 + pip（如果没有）
if ! command -v python3 &>/dev/null; then
    echo "[1] 安装 Python3..."
    if command -v apt &>/dev/null; then
        apt update && apt install -y python3 python3-pip python3-venv
    elif command -v yum &>/dev/null; then
        yum install -y python3 python3-pip
    fi
else
    echo "[1] Python3 已安装: $(python3 --version)"
fi

# 2. 创建虚拟环境
echo "[2] 创建虚拟环境..."
cd "$WORK_DIR"
python3 -m venv venv 2>/dev/null || python3 -m venv --without-pip venv
source venv/bin/activate

# 3. 安装依赖
echo "[3] 安装依赖..."
pip install --upgrade pip 2>/dev/null || true
pip install playwright requests openpyxl beautifulsoup4 pypdf

# 4. 安装 Chromium（Playwright 需要）
echo "[4] 安装 Chromium 浏览器..."
playwright install chromium
playwright install-deps chromium 2>/dev/null || true

echo ""
echo "✅ 部署完成!"
echo "   工作目录: $WORK_DIR"
echo "   虚拟环境: $WORK_DIR/venv"
