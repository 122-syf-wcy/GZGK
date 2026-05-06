#!/usr/bin/env bash
# 服务器端 ML 训练 + 注册一键脚本。建议挂在 systemd timer 或 cron 调起。
#
# 默认行为：
#   1. 重新构建训练 CSV（带严格校验）；
#   2. 训练 rank-prediction + chance-score 双模型；
#   3. POST /api/admin/ml/models/register 写注册表（不调 ml-service /train）；
#   4. 注册状态 draft；运维确认效果后再 POST /admin/ml/models/{id}/activate 激活。
#
# 必填环境变量：
#   GZLY_DB_PASSWORD   连 MySQL 取训练数据
#   GZLY_API_TOKEN     如果后端开启了 X-Admin-Token 鉴权
#
# 可选：
#   GZLY_API_BASE      默认 http://127.0.0.1:8090
#   ML_OUTPUT_DIR      默认 /var/lib/gzly-ml/models
#   ML_TRAIN_YEAR_RANGE 默认 2019-2024

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
SERVICE_DIR="$(cd -- "${SCRIPT_DIR}/.." >/dev/null 2>&1 && pwd)"

OUTPUT_DIR="${ML_OUTPUT_DIR:-/var/lib/gzly-ml/models}"
TRAIN_YEAR_RANGE="${ML_TRAIN_YEAR_RANGE:-2019-2024}"
CSV_PATH="${ML_TRAINING_CSV:-${SERVICE_DIR}/data/training_rank.csv}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ -z "${GZLY_DB_PASSWORD:-}" ]]; then
  echo "[train.sh][ERROR] GZLY_DB_PASSWORD 必须设置" >&2
  exit 2
fi

mkdir -p "${OUTPUT_DIR}"

cd "${SERVICE_DIR}"

echo "[train.sh] start at $(date -Iseconds), output=${OUTPUT_DIR}, csv=${CSV_PATH}"
"${PYTHON_BIN}" -m scripts.train_models_on_server \
  --models rank chance \
  --csv "${CSV_PATH}" \
  --output-dir "${OUTPUT_DIR}" \
  --regenerate-csv \
  --min-rows 50 \
  --register \
  --status draft \
  --train-year-range "${TRAIN_YEAR_RANGE}"

echo "[train.sh] done at $(date -Iseconds)"
