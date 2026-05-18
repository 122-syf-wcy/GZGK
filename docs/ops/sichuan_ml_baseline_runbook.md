# 四川 ML 训练基地（baseline）操作手册

> 起草时间：2026-05-17 16:15（v7.45 SC 综合分上线 + ETL baseline 跑通后）
> 适用：四川 SC（同款手册适用湖北 HB / 安徽 AH，传 `--province-code HB/AH` 即可）
> 目标：把"四川院校专业组 ML 训练管线"流程跑通到 baseline 等待 2026 数据自动开训

## 一、为什么先建 baseline

用户原话："**先训练历年数据吧这样有个基地到时候**"——意图是建立 SC ML 训练流水线雏形，等 2026 官方数据出来后立即可重训，不用临时搭管线。

### 当前现实（2026-05-17）

| 维度 | 贵州 GZ 现状 | 四川 SC 现状 |
|---|---|---|
| 训练数据表 | `data_score_line_gz`（21789 行）| `data_admission_group_line`（27 行 SC 2025）|
| 训练样本年份 | 2020-2025 共 6 年 | 仅 2025 1 年 |
| Lag 特征 | 充足（≥3 年） | **0%**（单年数据无法做 lag） |
| Min-rows 门槛 | 50 | 10（baseline 临时降）|
| 主流程 ML 模型 | rank-prediction / chance-score-xgb 已 active | **未训练**（数据不足）|
| 一分一段 | 2024+2025 共 2435 行 | SC 2025 物理 541 + 历史 514 ✅ |

### 直接训练的风险

如果用 27 行 0% lag 数据强行训练 LightGBM rank-prediction：
- 模型会用 `current_plan_count`、`school_ranking_score`、`is_985` 等次要特征过拟合
- 在 27 行训练集上 RMSE 假性很低，对 SC 真实考生预测全乱
- **会把 GZLY 当前 active 模型挤掉**（如果误激活），影响贵州生产推荐

**结论**：当前阶段**不激活 SC 专属模型**；用 GZLY 21789 行 GZ 模型在 SC 走 fallback 计算位次趋势；同时建好 SC ETL + quality report，等 2026 数据齐全后立即可激活。

## 二、本轮已交付（v7.45）

### 1. SC ETL 脚本

**文件**：`ml-service/scripts/build_training_csv_sc.py`

**与贵州 ETL 的差异**：
- 数据源：`data_admission_group_line` × `sys_university` + `data_admission_group_plan` 取专业名
- 训练单元：`(school_id, group_code, subject_type)`（而非贵州的 `(school_id, major_name, subject_type)`）
- 批次代码：`SC_*` 前缀（与 SichuanBatchRuleRegistry 对齐）
- subject_regime：`new`(2025+) / `old`(2024-)
- `--allow-no-lag`：单年数据 baseline 模式
- `--min-rows 10`：低门槛（贵州默认 50）

**调用样例**（生产服务器）：
```bash
cd /opt/gzly/ml-service
export GZLY_DB_PASSWORD=*****
.venv/bin/python -m scripts.build_training_csv_sc \
    --output data/training_rank_sc_2025_baseline.csv \
    --province-code SC \
    --train-years 2025 \
    --min-rows 10 \
    --allow-no-lag \
    --quality-report reports/sc_training_quality_baseline.md \
    --strict
```

### 2. baseline ETL 跑出来的结果

```
[sc-etl] raw rows: 27
[sc-etl] after normalize: 27
[sc-etl] after lag: 27 (lag 全空)
[sc-etl] trainable rows (allowNoLag=True): 27
[sc-etl] saved -> data/training_rank_sc_2025_baseline.csv (rows=27, cols=31)
[sc-etl] quality report -> reports/sc_training_quality_baseline.md
```

质量报告 8 个关键列 completeness：
- year / school_code / major_code / subject_type / batch_code / min_rank / current_plan_count = 100%
- min_rank_lag_1 = 0%（单年数据预期）

### 3. data_year_readiness SC 行落库

| province_code | year | recommendation_phase | score_segment_ready | admission_plan_ready | ml_training_ready |
|---|---|---|---|---|---|
| SC | 2024 | MODEL_RETRAINED | 0 | 0 | 0 |
| SC | 2025 | PRE_OFFICIAL_DATA | 1 | 0 | 0 |
| SC | 2026 | PRE_OFFICIAL_DATA | 0 | 0 | 0 |

迁移 SQL：`db/20260517_sichuan_data_year_readiness.sql`，已入生产。

## 三、SC ML 训练分阶段路线

### Phase B0（已完成，v7.45）

- ✅ SC ETL 脚本可用
- ✅ baseline CSV + 质量报告生成
- ✅ data_year_readiness SC 行落库（前端能正确显示"训练数据准备中"）
- ✅ ProfessionalGroupVolunteerService 启发式打分（无 ML 介入，0% 风险）

### Phase B1（等 SC 2025 数据补到 ≥45 组）

操作清单：
1. 跑 SC 数据爬虫 + OCR + 人工复核（按 `/root/gzly_scraper/sichuan_2025/README.md`）
2. `reviewed/group_lines_sc_2025_reviewed.csv` 达到 ≥45 行（物理 + 历史合计）
3. 通过 admin import 流程导入 → `data_admission_group_line` ≥45 行
4. 跑 ETL：
   ```bash
   .venv/bin/python -m scripts.build_training_csv_sc \
       --train-years 2025 --min-rows 45 --allow-no-lag \
       --quality-report reports/sc_training_quality_2025_full.md
   ```
5. **仍不训练 model**（lag 0% 还是无法用）

### Phase B2（2026 官方数据出，约 6 月下旬）

操作清单：
1. 按 `docs/ops/2026_official_data_import_retrain_runbook.md` 同款流程导入 SC 2026 一分一段 / 招生计划 / 选科要求
2. 更新 SC 2026 行 `data_year_readiness.recommendation_phase = OFFICIAL_DATA_IMPORTED`
3. 跑 ETL（启用 lag）：
   ```bash
   .venv/bin/python -m scripts.build_training_csv_sc \
       --train-years 2025,2026 --min-rows 50 \
       --require-train-years \
       --quality-report reports/sc_training_quality_2025_2026.md \
       --strict
   ```
4. 训练 SC 专属 rank + chance 模型：
   ```bash
   .venv/bin/python -m scripts.train_models_on_server \
       --models rank chance \
       --csv data/training_rank_sc_2025_2026.csv \
       --output-dir /var/lib/gzly-ml/models/sc \
       --register --status draft \
       --train-year-range 2025,2026
   ```
5. 人工复核 `feature_quality_report.md` + 离线回测
6. 通过 `/api/admin/ml/models/{id}/activate` 激活
7. 更新 `data_year_readiness` SC 2026 行 `recommendation_phase = MODEL_RETRAINED`、`ml_training_ready = 1`
8. SC_BENKE_B `supportLevel` 自动从 `TRIAL_RECOMMEND` 升 `FULL_RECOMMEND`

### Phase B3（持续 - 每年新数据出加入训练）

每年高考结束后：
1. 导入当年 SC 官方数据
2. `--train-years 2025,2026,2027`（逐年累加）
3. 重训 + 激活

## 四、为什么不立即跑 train_rank_model

| 现状 | 风险 | 决策 |
|---|---|---|
| 27 行 SC 2025 数据 | LightGBM 在 27 行训练集会严重过拟合 | 不跑 |
| lag 特征 0% | rank prediction 的主要 signal 是 lag，没 lag 模型基本退化为常数预测 | 不跑 |
| GZLY 注册表共享 | 错误激活 SC 模型会挤掉 GZ active 模型，影响生产 | 不跑 |
| 用户期望 | "训练历年数据建基地"——pipeline 跑通即可，不强求出可用模型 | 跑 ETL，不训模型 |

## 五、与 GZLY 训练管线的关系

```
                  ┌─────────────────────────────────┐
                  │  data_score_line_gz (21789)     │
                  │  贵州 6 年训练样本               │
                  └────────────┬────────────────────┘
                               │
                               ▼
       ┌───────────────────────────────────────┐
       │  build_training_csv.py（贵州 ETL）    │
       │  --train-years 2024,2025（默认）      │
       └─────────────────┬─────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  train_models_on_     │
              │  server.py            │
              │  rank-prediction      │
              │  chance-score-xgb     │
              └───────┬───────────────┘
                      │ register
                      ▼
          ┌─────────────────────────┐
          │  ml_model_registry      │
          │  当前 active: rank-v…   │
          │  当前 active: chance-v… │
          └───────────┬─────────────┘
                      │ /ml/predict/batch
                      ▼
          ┌─────────────────────────┐
          │  ml-service (port 8092) │
          └───────────┬─────────────┘
                      │
          ┌───────────┴────────────┐
          ▼                        ▼
    GZ 主链路                  SC 主链路
    （已用 ML）                （v7.45 走启发式 +
                                 GZLY 模型 fallback）

    ┌─────────────────────────────────────────────┐
    │  data_admission_group_line (27 SC 2025)     │
    │  四川 1 年训练样本                          │
    └────────────────┬────────────────────────────┘
                     │
                     ▼
    ┌─────────────────────────────────────────────┐
    │  build_training_csv_sc.py（四川 ETL）✅ NEW  │
    │  --train-years 2025（baseline）             │
    │  --allow-no-lag                             │
    └────────────────┬────────────────────────────┘
                     │
                     ▼
        training_rank_sc_2025_baseline.csv
        sc_training_quality_baseline.md
                     │
                     ▼
               ⏳ Wait Phase B2
        （2025 数据补齐 + 2026 数据导入）
                     │
                     ▼
    train_models_on_server.py（同款，参数切 SC CSV）
                     │
                     ▼
       ml_model_registry 注册 sc-rank-v…
                     │
                     ▼
       /api/admin/ml/models/{id}/activate
                     │
                     ▼
       ml-service 同时服务 GZ + SC（按 features.province_code 路由）
```

## 六、当前生产 SC 推荐如何工作（无 ML 阶段）

1. 用户在前端选 SC + 普通本科批 B 段，填分数 / 位次 / 选科
2. `/api/volunteer/recommend` → `ProfessionalGroupVolunteerService.generate`
3. 算法走启发式：
   - `data_admission_group_line` 按位次区间 + 主流程关键词 LIKE 匹配候选
   - 冲 8 / 稳 19 / 保 13 / 垫 5 梯度（v7.44 后允许部分 < 45）
   - 启发式打分：`recommendationScore = dataConfidenceScore + precisionScore/5`
   - 概率口径：`参考匹配`（不出录取概率）
4. 数据缺口显式提示：`dataQualityWarning` + `warnings`

**ML 风险**：0（不调用 ML 服务）。
**用户感知**：能拿到 N≤45 条志愿草稿，但 confidence 标签为"中可信 / 需复核"，不是"高可信"。

等 Phase B2 后会自动切换为：
- `data_admission_group_line` 候选 → ML rank-prediction 预测 → chance-score-xgb 评估 → 高可信
- 主流程升 `FULL_RECOMMEND`

## 七、本轮调用 checklist

| 步骤 | 命令 | 状态 |
|---|---|---|
| SC data_year_readiness 落库 | `mysql gzly < db/20260517_sichuan_data_year_readiness.sql` | ✅ 已执行 |
| 上传 SC ETL 脚本到 ml-service | `scp build_training_csv_sc.py root@server:/opt/gzly/ml-service/scripts/` | ✅ 已执行 |
| 跑 SC baseline ETL | `python -m scripts.build_training_csv_sc --train-years 2025 --allow-no-lag --strict` | ✅ 已执行（27 行 → CSV + 质量报告）|
| 不训练 SC 模型（数据不足） | — | ✅ 决策 |
| 生产 SC_BENKE_B 主流程能用 | 用户调 `/api/volunteer/recommend` | ✅ v7.44 实测 12 条志愿 |

## 八、不要做的事

- **不要**在 27 行数据上训练并激活 SC 模型（会挤掉 GZ active 模型）。
- **不要**用 GZ 数据冒充 SC 训练样本（口径不同：贵州专业级 vs 四川院校专业组）。
- **不要**把 SC 2024 老高考数据导入 `data_admission_group_line`（与新高考"院校专业组"完全不同）。
- **不要**用 2025 数据冒充 2026 标签（HANDOVER 已禁止）。
- **不要**在 `data_year_readiness` SC 2026 行手动改 `ml_training_ready=1`（必须真实重训完成后由后台脚本写入）。
