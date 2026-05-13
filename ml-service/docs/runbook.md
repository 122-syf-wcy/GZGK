# GZLY ML Service Runbook

本 runbook 描述如何在生产服务器上构建训练数据、训练 rank-prediction / chance-score 双模型并把结果注册到 `ml_model_registry`。2026 官方数据发布后的流程默认只注册 `draft`，plan trend 先输出规则评估报告，不直接激活模型或更新 readiness。

## 前提

- ml-service 镜像或源码部署在服务器上，能跑 `python -m scripts.train_models_on_server`；
- gzly-server 后台暴露 `/api/admin/ml/models/register` 与 `/api/admin/ml/models/{id}/activate`；
- MySQL 有 `data_score_line_gz` / `data_major_score_gz` / `sys_university` 三张表的数据；
- 训练机器有 LightGBM 依赖（`pip install lightgbm pandas scikit-learn pymysql joblib`）。

## 常规流水线

```bash
export GZLY_DB_PASSWORD=*****
export GZLY_API_BASE=http://127.0.0.1:8090
export GZLY_API_TOKEN=*****             # 如启用了 X-Admin-Token 鉴权

# 1. 重新构建训练 CSV（带强校验和质量报告）
python -m scripts.build_training_csv \
    --output data/training_rank_2024_2026.csv \
    --train-years 2024,2025,2026 \
    --require-train-years \
    --quality-report reports/feature_quality_report.md \
    --min-rows 50 --strict

# 2. 训练 + 注册（status=draft，避免覆盖现网模型），plan-trend 只生成评估报告
python -m scripts.train_models_on_server \
    --models rank chance plan-trend \
    --csv data/training_rank_2024_2026.csv \
    --output-dir /var/lib/gzly-ml/models \
    --plan-trend-report reports/plan_trend_evaluation_report.md \
    --register --status draft \
    --train-year-range 2024,2025,2026
```

或者先使用 2026 预案脚本生成 dry-run 命令和质量报告骨架；默认不训练、不注册、不激活：

```bash
bash scripts/data/2026_official_import/retrain_2026_plan.sh
```

正式训练前必须确认 `reports/feature_quality_report.md` 中 2024/2025/2026 均有真实样本。不得用 2025 数据填充 2026，也不得把 2026 招生计划或一分一段表伪造成录取位次标签。

## 激活模型

注册后默认 `status=draft`，**不会**被 ml-service 加载。运维确认特征质量报告、离线回测报告、plan trend 评估报告均不退化后，才能单独激活：

```bash
curl -X POST \
    -H "X-Admin-Token: $GZLY_API_TOKEN" \
    "$GZLY_API_BASE/api/admin/ml/models/$MODEL_ID/activate"
```

`/activate` 端点会：

1. 把同名模型的所有现存 `active` 版本改回 `archived`；
2. 把目标版本设为 `active`；
3. 写入 `activated_at` 时间戳。

## 关键配置

- `gzly.ml.enabled`（gzly-server）：必须 `true` 才会真正调用 ml-service `/ml/predict/batch`；为 `false` 时主链路用 `FallbackRulePredictionEngine` 兜底，不会请求 ML 服务。
- `gzly.ml.base-url`：默认 `http://127.0.0.1:8091`。
- `gzly.ml.timeout-ms`：单次预测超时；MlPredictionService 取 `max(500, timeoutMs)` 作为下限。

## 常见故障

| 现象 | 原因 | 排查 |
| --- | --- | --- |
| `[etl][ERROR] 训练样本不足` | data_score_line_gz 行数过少或 lag 计算被过滤掉 | 调小 `--min-rows`，并审核 ETL log；若是临时跑 demo 可加 `--limit` 控制样本数 |
| 训练完成但前端机会指数仍为 0 | `gzly.ml.enabled=false` 且主链路 FallbackRulePredictionEngine 缺历史 | 把 `gzly.ml.enabled=true` 后重启；或检查 `data_major_score_gz` 是否有目标专业 |
| `register HTTP 401` | X-Admin-Token 无效 | 检查 `GZLY_API_TOKEN` 与后端配置是否一致；空 token 时确保后端关闭了鉴权 |
| `lightgbm unavailable` | 训练机缺 lightgbm | `pip install lightgbm`，必要时 `apt install libgomp1` |
| `训练样本缺少年份 [2026]` | `--require-train-years` 要求 2026 真实带标签样本，但数据库尚无该年份标签 | 停止重训；不得用其他年份冒充 2026；等待官方/真实可训练数据补齐 |
| plan trend 只有报告没有模型文件 | 当前预案只做规则评估骨架 | 人工确认报告，不调用激活；后续再补正式 trend 模型或规则消费链路 |

## 校验（部署后）

```bash
# 注册表查询
curl -s "$GZLY_API_BASE/api/admin/ml/models?modelName=rank-prediction" | jq

# 端到端调用
curl -s -X POST -H 'Content-Type: application/json' \
    -d '{"totalScore":562,"provinceRank":18000,"firstSubject":"物理","resubjects":["化学","生物"]}' \
    "$GZLY_API_BASE/api/volunteer/generate" | jq '.data.modelInfo'
```

`modelInfo.modelEnabled=true` 且 `modelInfo.fallbackUsed=false` 表示 ML 链路正在被使用。
