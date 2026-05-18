# 安徽 ML 训练基地（baseline）操作手册

> 起草时间：2026-05-18 14:55（v7.50 ProvinceBatchEngineMatrix + AH 14 批次专用 engine 全部上线后）
> 适用：安徽 AH（与 `sichuan_ml_baseline_runbook.md` 同款，传 `--province-code AH` 即可）
> 目标：把"安徽院校专业组 ML 训练管线"流程跑通到 baseline，等 2026 官方数据出来后立即可重训

## 一、为什么先建 baseline

与 SC runbook 同思路：先把 ETL / quality report / DAG 跑通，等 2026 安徽实施办法、专业目录、一分一段、招生计划全部落地后立即可激活 AH 专属 ML 模型，不用 6 月底再临时搭管线。

### 当前现实（2026-05-18）

| 维度 | 贵州 GZ 现状 | 四川 SC 现状 | 安徽 AH 现状 |
|---|---|---|---|
| 训练数据表 | `data_score_line_gz`（21929 行）| `data_admission_group_line`（4994 行 SC 2025） | `data_admission_group_line`（4282 行 AH 2025） |
| 训练样本年份 | 2020-2025 共 6 年 | 仅 2025 1 年 | **仅 2025 1 年** |
| Lag 特征 | 充足（≥3 年） | 0%（单年数据无法做 lag） | **0%**（同 SC 限制） |
| 一分一段 | 2024+2025 共 2435 行 | 物 541 + 历 514 ✅ | **0 行**（OCR draft 31 行因不单调被退回）|
| 招生计划 | 65729 行 ✅ | 140 行（zjzw planCount 100% 空，需 PDF 抓） | **0 行**（同 SC 限制） |
| 选科要求 | 65879 行 ✅ | 0 行（需 PDF） | **0 行**（需 PDF） |
| 主流程 ML 模型 | rank-prediction / chance-score-xgb active | 未训练（runbook 明确不激活） | **未训练**（同 SC 决策） |
| 推荐引擎 | 7 个 GZ 专用 + Query | 5 个 SC 专用 + Query（v7.50） | **5 个 AH 专用 + Query**（v7.50） |

### 直接训练的风险（与 SC 同源）

如果用 4282 行 0% lag 数据强行训练 LightGBM rank-prediction：
- 模型会用 `school_ranking_score` / `is_985` 等次要特征过拟合
- 在 4282 行训练集上 RMSE 假性很低，对 AH 真实考生预测全乱
- 误激活会挤掉 GZLY 当前 GZ active 模型

**结论**：当前阶段**不激活 AH 专属模型**；用 GZ 21929 行模型在 AH 走 fallback；同时建好 AH ETL + quality report，等 2026 数据齐全后激活。

## 二、Phase 蓝图

| Phase | 时间窗 | 触发条件 | 动作 |
|---|---|---|---|
| **B0** | 持续 | 任意时间 | AH ETL baseline 流水线已就绪（本文件即 baseline 凭据，等 build_training_csv_ah.py 创建） |
| **B1** | 6 月初 | AH 一分一段 OCR 可入库 | 跑一遍 AH 2025 score_rank ETL；激活 `data_year_readiness.score_segment_ready=1` |
| **B2** | 6 月下旬 | 安徽考试院发布 2026 实施办法 + 一分一段 + 招生计划 | 跑全量 AH 2026 ETL；用 GZ 模板训练 AH rank-prediction 模型；激活到 `ml_model_registry` |
| **B3** | 7 月 | AH 模型在线 + 用户反馈 | 调整 AH `BatchSupportService` supportLevel 阈值；对外暴露"AH 全推荐"FULL_RECOMMEND |

## 三、AH ETL 建设（待办）

新增 `ml-service/scripts/build_training_csv_ah.py`（沿用 SC 同款 200 行结构）：

- 数据源：`data_admission_group_line × data_admission_group_plan × sys_university WHERE province_code='AH'`
- 训练单元：`(school_id, group_code, subject_type)`（与 SC 一致）
- 批次代码映射：`AH_*` 前缀（与 `AnhuiBatchRuleRegistry` 严格对齐）
- `subject_regime`：new(2025+) / old(2024-)
- `--allow-no-lag`：允许单年数据 baseline 跑通流程
- `--min-rows 10`：低门槛（与 SC 一致）
- 输出 quality report：`reports/ah_training_quality_baseline.md`

### Phase B1 一键命令（待一分一段补齐后跑）

```bash
cd /opt/gzly/ml-service
source venv/bin/activate
python -m scripts.build_training_csv_ah \
  --province-code AH \
  --train-years 2025 \
  --allow-no-lag \
  --strict \
  --min-rows 10 \
  --output reports/training_rank_ah_2025_baseline.csv
```

### Phase B2 完整命令（待 2026 数据齐全后跑）

```bash
# 1. 重新 ETL
python -m scripts.build_training_csv_ah \
  --province-code AH \
  --train-years 2025,2026 \
  --target-year 2026 \
  --strict \
  --min-rows 50 \
  --output reports/training_rank_ah_2026.csv

# 2. 训练
python -m scripts.train_models_on_server \
  --province-code AH \
  --training-csv reports/training_rank_ah_2026.csv \
  --output-dir /opt/gzly/ml-service/models/ah_2026

# 3. 激活
mysql -uroot gzly -e "UPDATE ml_model_registry SET status='active' WHERE model_name='ah_rank_prediction_2026';"
mysql -uroot gzly -e "UPDATE data_year_readiness SET ml_training_ready=1, recommendation_phase='MODEL_RETRAINED' WHERE province_code='AH' AND year=2026;"

# 4. 验证
curl -sS 'http://39.97.232.141/api/volunteer/ah/batch-support?year=2026' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["summary"])'
# 应当显示 {FULL_RECOMMEND: >=1, TRIAL_RECOMMEND: ..., QUERY_ONLY: ...}
```

## 四、与贵州训练管线的关系

```
            +----------------------+
            |   GZ 21929 行 active  |   <-- 生产兜底（所有省份）
            +----------------------+
                       ^
                       |
                       fallback
                       |
        +--------------+--------------+
        |              |              |
   +----v-----+   +----v-----+   +----v-----+
   | SC 模型   |   | AH 模型   |   | HB 模型   |
   | (未训练)  |   | (未训练)  |   | (未建模)  |
   +-----------+   +-----------+   +-----------+
   等 SC 数据    等 AH 数据    等 HB 数据
   齐全          齐全          落地
```

## 五、不要做的事（防呆）

1. **不要在 4282 行 0% lag 数据上训练 AH 模型并 active**：必然过拟合 + 误用 GZ 资源
2. **不要把 SC 模型直接套到 AH**：批次结构不同（SC 18 批次 vs AH 14 批次）、综合分公式不同（SC 5050/3070 vs AH 5050/7030 + 体育文化控线 65%）
3. **不要在 AH `data_year_readiness.score_segment_ready=0` 时手填强制 `=1`**：会让 `ProfessionalGroupVolunteerService` 跳过位次估算回退、对没填位次的考生直接报错
4. **不要在 AH 选科要求 0 行时声称组内 6 专业匹配可靠**：dataQualityWarning 现在已经显式提示「45 个院校专业组缺计划数或未结构化」

## 六、AH engine 矩阵（v7.50 已完整上线）

参考 `ProvinceBatchEngineMatrix.java`：

| 批次代码 | engineName | 状态 |
|---|---|---|
| AH_BENKE | AnhuiProfessionalGroup45Engine | ✅ items=45/45 TRIAL_RECOMMEND |
| AH_ZHUANKE | AnhuiProfessionalGroup45Engine | ⚠️ items=0（zjzw 仅有本科批数据） |
| AH_TIQIAN_BENKE_PARALLEL | AnhuiSpecialPlanEligibilityEngine | ⚠️ items=0 + 规则文案 |
| AH_TIQIAN_BENKE_SEQUENTIAL | AnhuiSequentialCollegeEngine | ⚠️ items=0 + 规则文案 |
| AH_TIQIAN_ZHUANKE_PARALLEL | AnhuiSpecialPlanEligibilityEngine | ⚠️ items=0 + 规则文案 |
| AH_TIQIAN_ZHUANKE_SEQUENTIAL | AnhuiSequentialCollegeEngine | ⚠️ items=0 + 规则文案 |
| AH_NATIONAL_SPECIAL | AnhuiSpecialPlanEligibilityEngine | ⚠️ items=0 + 资格审核要点 |
| AH_LOCAL_SPECIAL | AnhuiSpecialPlanEligibilityEngine | ⚠️ items=0 + 资格审核要点 |
| AH_UNIVERSITY_SPECIAL | AnhuiSequentialCollegeEngine | ⚠️ items=0 + 资格审核要点 |
| AH_ART_XIAOKAO_BENKE | AnhuiSequentialCollegeEngine | ⚠️ items=0 + 校考说明 |
| AH_ART_TONGKAO_BENKE | AnhuiArtCompositeEngine | ⚠️ items=0 + 综合分公式（综合分 1 5050 / 综合分 2 7030 播音） |
| AH_ART_TONGKAO_ZHUANKE | AnhuiArtCompositeEngine | ⚠️ items=0 + 综合分公式 |
| AH_SPORTS_BENKE | AnhuiSportsCompositeEngine | ⚠️ items=0 + 体育综合分公式（1.2×专业 + 0.8×线性映射）|
| AH_SPORTS_ZHUANKE | AnhuiSportsCompositeEngine | ⚠️ items=0 + 体育综合分公式 |

带 ✅ 已就位，带 ⚠️ 代码就位+等数据。

## 七、补数依赖（外部）

| 数据项 | 依赖 | 状态 |
|---|---|---|
| AH 2025 一分一段 OCR | vision 模型 API key | **缺**（待运维提供 OpenAI 或国内豆包 / qwen-vl 凭据）|
| AH 2025 招生计划 | 安徽考试院专业目录 PDF | **缺**（zjzw 实测 planCount 100% 空，需另寻数据源）|
| AH 2025 选科要求 | 安徽专业目录 PDF（同上）| **缺** |
| AH 非主流程 13 批次 group_line | 安徽考试院艺术/体育/专项专题页爬虫 | **缺**（zjzw 仅返回"普通本科批"4282 行）|
| 2026 全量数据 | 6 月下旬安徽实施办法发布 | **等** |
