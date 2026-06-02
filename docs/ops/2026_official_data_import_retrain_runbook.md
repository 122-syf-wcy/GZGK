# GZLY 2026 官方数据快速导入、质检、切换、重训预案

## 边界声明

- **本轮只交付方案和脚本骨架**，不执行生产导入。
- **不导入假 2026 数据**。
- **不使用 2025 数据冒充 2026 官方数据**。
- **不打开 2026 `FULL_RECOMMEND`**。
- **不删除 2024/2025 历史数据**。
- **不直接生产重训**。
- **不改安全码**。
- **不改 ML 模型文件**。
- **不重启生产服务**。
- **不执行正式表写入**，除非后续单独确认。

## 当前生产基线

| 项 | 当前值 | 结论 |
|---|---:|---|
| `activeAdmissionYear` | 2026 | 正确 |
| `latestOfficialDataYear` | 2025 | 正确 |
| `trainingYears` | `[2024,2025]` | 正确，只用于训练、回测、趋势分析 |
| `recommendationPhase` | `PRE_OFFICIAL_DATA` | 正确 |
| `officialDataReady` | `false` | 正确 |
| `batch-support` | 全部 `QUERY_ONLY` | 正确 |
| 普通用户 2024/2025 正式志愿 | 不允许 | 正确 |
| 无 2026 官方数据暴露 `FULL_RECOMMEND` | 不允许 | 正确 |

## 一、2026 正式推荐上线必须导入的数据

| 数据类型 | 表名 | 来源 | 是否必须 | 缺失时影响 | 导入脚本状态 |
|---|---|---|---|---|---|
| 2026 贵州一分一段表 | `data_score_rank_gz`，可同步 `score_rank_segment` | 贵州省招生考试院官方发布页、PDF/Excel/网页原文 | 必须 | 无法把分数转换为位次区间，无法做 2026 主链路风险判断；继续 `QUERY_ONLY` | 已提供 staging/质检模板，正式解析脚本待官方文件样式确定 |
| 2026 贵州招生计划 | `admission_plan` 标准化当年计划表；兼容 `data_admission_plan_gz` 历史导入表 | 贵州省 2026 招生专业目录、考试院文件、官方 PDF/Excel | 必须 | 普通本科/专科不能进入正式候选池；不能开放 `FULL_RECOMMEND` | 已提供 staging/质检/晋级模板，正式解析脚本待官方文件样式确定 |
| 2026 批次政策 | `policy_rule_config` | 贵州省招生工作规定、志愿填报办法、考试院公告 | 必须 | 批次数量、志愿模式、调剂、投档规则不可信；仍只能 `QUERY_ONLY` 或 `TRIAL_RECOMMEND` | 已提供 readiness 更新模板；政策解析需人工复核 |
| 2026 选科要求 | `data_major_requirement_gz` | 官方选科要求库、招生专业目录 | 必须 | 主链路硬规则无法过滤选科；普通本科/专科最多 `QUERY_ONLY/TRIAL_RECOMMEND` | 已提供 staging/质检模板；需补 2026 专项解析脚本 |
| 2026 专业备注/体检/单科/语种/性别限制 | `admission_plan.remarks`、`admission_plan.special_limit`；兼容 `data_admission_plan_gz.remarks/special_limit/raw_text` | 招生计划原文、院校章程、考试院目录脚注 | 必须 | 不能做限制项硬过滤和人工复核提示，存在误报风险；不得 `FULL_RECOMMEND` | 已提供字段检查清单；正式抽取规则待实现 |
| 2026 批次线 | 建议新增/复用 `data_batch_line_gz`；当前主链路未发现专门实体 | 贵州省招生考试院批次线公告 | 必须 | 无法判断达线/批次资格；普通推荐风险不可控 | **缺字段/缺表**，需新增迁移或确认现有表 |
| 2026 艺术/体育综合分规则 | 建议新增 `art_sports_composite_rule_gz` 或纳入 `policy_rule_config.official_source_text` | 考试院艺术/体育类招生办法 | 条件必须 | 艺术/体育不能从 `QUERY_ONLY` 升级；仍只展示缺口/规则说明 | **缺专表/缺规则引擎消费确认** |
| 2026 特殊计划资格规则 | 建议新增 `special_plan_eligibility_rule_gz` 或纳入政策配置扩展表 | 国家专项、地方专项、免费医学、优师、定向等官方文件 | 条件必须 | 特殊计划不能正式生成，只能资格说明/缺口展示 | **缺结构化资格规则表/缺完整消费链路确认** |

## 二、staging 表与 `import_batch_id` 规范

### 总原则

1. 2026 官方数据先进入 `stg_*` staging 表。
2. 每类 staging 数据必须有 `import_batch_id`。
3. 每类数据必须生成 `quality_report.md`。
4. 每类数据必须生成 rollback SQL。
5. 每行必须保留 `source_file`、`source_url`、`raw_text`，必要时保留 `source_page`、`source_page_url`、`page_segment_evidence`。
6. staging 阶段不得直接写正式表。
7. 禁止 fake/mock 2026 数据。
8. 禁止使用 2025 数据伪装 2026 官方数据。
9. 正式表导入 SQL 必须拆分为：准备临时表、body-only insert、post-check、rollback。
10. body-only insert 中禁止 DDL、`DELETE`、`START TRANSACTION`、`COMMIT`、`ROLLBACK`。

### `import_batch_id` 格式

| 数据类型 | 格式 | 示例 |
|---|---|---|
| 一分一段表 | `gz_2026_score_segment_v{N}_YYYYMMDD` | `gz_2026_score_segment_v1_20260625` |
| 招生计划 | `gz_2026_admission_plan_v{N}_YYYYMMDD` | `gz_2026_admission_plan_v1_20260625` |
| 批次政策 | `gz_2026_policy_rule_v{N}_YYYYMMDD` | `gz_2026_policy_rule_v1_20260625` |
| 选科要求 | `gz_2026_major_requirement_v{N}_YYYYMMDD` | `gz_2026_major_requirement_v1_20260625` |
| 专业备注/限制 | `gz_2026_major_meta_v{N}_YYYYMMDD` | `gz_2026_major_meta_v1_20260625` |
| 批次线 | `gz_2026_batch_line_v{N}_YYYYMMDD` | `gz_2026_batch_line_v1_20260625` |
| 艺术/体育规则 | `gz_2026_art_sports_rule_v{N}_YYYYMMDD` | `gz_2026_art_sports_rule_v1_20260625` |
| 特殊计划规则 | `gz_2026_special_plan_rule_v{N}_YYYYMMDD` | `gz_2026_special_plan_rule_v1_20260625` |

### source manifest 与操作记录格式

每个 `import_batch_id` 必须有 source manifest，并同步登记到 `data_year_readiness_batch`。

| 字段 | 说明 | 是否必须 |
|---|---|---|
| `province_code` | 固定 `GZ` | 必须 |
| `year` | 固定 `2026` | 必须 |
| `data_type` | `score_segment/admission_plan/policy_rule/major_requirement/major_meta/batch_line/art_sports_rule/special_plan_rule` | 必须 |
| `import_batch_id` | 本批导入批次号 | 必须 |
| `official_title` | 官方公告或文件标题 | 必须 |
| `source_url` | 官方发布 URL | 必须 |
| `source_file` | 下载后的官方文件名 | 必须 |
| `downloaded_at` | 下载时间 | 必须 |
| `file_hash` | SHA-256 或等价 hash | 必须 |
| `downloaded_by` | 下载人/执行人 | 必须 |
| `reviewed_by` | 复核人 | 晋级前必须 |
| `quality_report_path` | `quality_report.md` 路径 | 质检后必须 |
| `rollback_sql_path` | rollback SQL 路径 | 晋级前必须 |
| `hard_gate_report_path` | hard gate CSV 路径 | 质检后必须 |
| `notes` | 异常行、人工豁免、待补证据 | 可选 |

### staging 字段检查清单

| staging 表 | 必须字段 | 关键唯一性 | 质检重点 |
|---|---|---|---|
| `stg_gz_2026_score_segment` | `import_batch_id, year, subject_type, score, segment_count, cumulative_count, rank_low, rank_high, source_file, source_url, raw_text` | `import_batch_id + year + subject_type + score` | 分数连续性、累计人数单调递增、物理/历史覆盖、空来源、同分位次计算 |
| `stg_gz_2026_admission_plan` | `import_batch_id, year, batch_code, candidate_type, subject_type, school_code, school_name, major_code, major_name, plan_count, source_file, source_url, raw_text` | `import_batch_id + year + batch_code + subject_type + school_code + major_code + major_name` | 计划数正数、批次分类、普通/提前/专项/艺术/体育不串批、学校匹配、重复自然键 |
| `stg_gz_2026_policy_rule` | `import_batch_id, province, year, candidate_type, batch_code, batch_name, volunteer_mode, max_volunteer_count, official_source_url, raw_text` | `import_batch_id + province + year + candidate_type + batch_code` | 官方来源、志愿数量、投档原则、调剂规则、政策状态 |
| `stg_gz_2026_major_requirement` | `import_batch_id, year, school_code, school_name, major_code, major_name, subject_type, first_subject_requirement, resubject_requirement, source_file, source_url, raw_text` | `import_batch_id + year + school_code + major_code + subject_type` | 选科枚举合法、专业匹配、空要求、冲突要求 |
| `stg_gz_2026_major_meta` | `import_batch_id, year, school_code, major_code, major_name, tuition, duration, remarks, special_limit, source_file, source_url, raw_text` | `import_batch_id + year + school_code + major_code + major_name` | 体检、语种、单科、性别、中外合作、高收费、校区限制抽取 |
| `stg_gz_2026_batch_line` | `import_batch_id, year, candidate_type, subject_type, batch_code, batch_name, control_score, source_file, source_url, raw_text` | `import_batch_id + year + candidate_type + subject_type + batch_code` | 分数合法、批次覆盖、与政策批次一致 |
| `stg_gz_2026_art_sports_rule` | `import_batch_id, year, category, batch_code, formula_text, score_components_json, source_file, source_url, raw_text` | `import_batch_id + year + category + batch_code` | 公式可解释、字段完整、官方来源 |
| `stg_gz_2026_special_plan_rule` | `import_batch_id, year, plan_type, batch_code, eligibility_text, eligibility_fields_json, source_file, source_url, raw_text` | `import_batch_id + year + plan_type + batch_code` | 资格条件结构化、区域/户籍/协议/体检字段完整 |

### 正式表追溯审计

- `data_year_readiness_batch` 记录每类数据的批次状态、来源 manifest、质检报告和 rollback SQL。
- `admission_plan_import_audit` 记录招生计划从 staging 晋级到 `admission_plan` 的自然键、来源和原文片段。
- `admission_plan` 主表仍不直接保存 `raw_text`；正式表回滚必须优先依赖 `admission_plan_import_audit`，audit 缺失时不得自动删除正式计划。

## 三、`data_year_readiness` 状态机

### 状态定义

| 阶段 | 含义 | 推荐能力 | UI 能力 |
|---|---|---|---|
| `PRE_OFFICIAL_DATA` | 2026 官方数据未发布或未导入 | 全部 `QUERY_ONLY` | 只显示趋势、缺口、预估参考 |
| `OFFICIAL_DATA_PARTIAL` | 部分 2026 官方数据进入 staging 或部分正式表，但关键数据未齐 | 仍禁止 `FULL_RECOMMEND`；可展示准备进度 | 显示数据准备进度和缺失项 |
| `OFFICIAL_DATA_IMPORTED` | 一分一段、招生计划、政策、选科、限制字段关键数据已全部导入并质检通过 | 普通本科/专科可进入 `TRIAL_RECOMMEND`；仍需模型重训或规则校验 | 显示“2026 官方数据已导入”，不再显示未发布警示 |
| `MODEL_RETRAINED` | 2026 数据参与训练，离线回测不退化，模型版本激活 | 普通本科/专科可按质检结果升级 `FULL_RECOMMEND` | 正常生成正式方案，同时保留合规提示 |

### 阶段切换条件

| 从 | 到 | 切换条件 | 禁止事项 |
|---|---|---|---|
| `PRE_OFFICIAL_DATA` | `OFFICIAL_DATA_PARTIAL` | 至少一个 2026 官方数据源完成 staging 并通过基础来源追溯检查 | 不开放 `FULL_RECOMMEND`；不把部分数据当完整数据 |
| `OFFICIAL_DATA_PARTIAL` | `OFFICIAL_DATA_IMPORTED` | `policy_ready=1`、`score_segment_ready=1`、`admission_plan_ready=1`、`major_requirement_ready=1`、`major_meta_ready=1`，且普通本科/专科自然键、计划数、批次、来源、选科和限制字段硬门禁全过 | 不自动启用模型；不跳过质检报告 |
| `OFFICIAL_DATA_IMPORTED` | `MODEL_RETRAINED` | 训练样本包含 2024/2025/2026；rank/chance/plan trend 离线指标不退化；模型 draft 注册、人工确认、再激活 | 不直接生产重训；不覆盖旧模型文件；不无评估激活 |
| 任意 | 回退上一阶段 | 发现来源错误、串批、重复、模型退化、线上异常或误开推荐 | 回退必须先停正式入口，再回滚数据/模型 |

### SQL 更新模板位置

- `scripts/data/2026_official_import/20_readiness_update_templates.sql`

所有模板默认 `@confirmed = 0`，直接执行不会更新。

## 四、2026 数据导入后重训流程

| 步骤 | 输入 | 输出 | 质量门禁 | 失败回滚 |
|---|---|---|---|---|
| 1. 抽取训练数据 | `data_score_line_gz`、`data_major_score_gz`、`admission_plan/data_admission_plan_gz`、`data_score_rank_gz`，年份 2024/2025/2026 | 原始训练集快照 | 年份必须只包含 2024/2025/2026；2026 来源必须官方 | 删除本次训练快照，不改模型注册表 |
| 2. 生成训练样本 | 原始训练集 | `training_rank_2024_2026.csv` | 行数不低于历史基线；关键特征完整率达标；标签 `min_rank` 正数 | 丢弃 CSV，保持旧 `trainingYears=[2024,2025]` |
| 3. 特征完整率校验 | CSV | `feature_quality_report.md` | `school_code/major_code/subject_type/batch_code/min_rank` 完整；缺失率超阈值则失败 | 不训练 |
| 4. 训练 rank 模型 | CSV | rank 模型 draft 文件 | MAE/RMSE 不劣于当前 active 基线阈值 | 删除 draft 模型文件或保留 archived |
| 5. 训练 chance 模型 | CSV | chance 模型 draft 文件 | AUC/校准度/分桶命中率不退化 | 删除 draft 模型文件或保留 archived |
| 6. 训练 plan trend 模型 | CSV/计划变更样本 | plan trend draft 文件或规则评估报告 | 计划趋势准确率/召回不退化；低样本批次降级规则说明 | 不激活 trend 模型 |
| 7. 注册模型版本 | draft 模型文件、metrics | `ml_model_registry` draft 记录 | `status=draft`，不覆盖 active | 删除 draft 注册记录或标记 archived |
| 8. 离线回测 | 2024/2025 回测集、2026 抽样校验集 | `offline_backtest_report.md` | 对比旧模型不退化；高风险院校/低计划数样本不过度乐观 | 不激活 |
| 9. 激活模型 | 人工确认、draft 版本 | active 模型版本 | 激活前备份当前 active；同一模型只允许一个 active | 恢复旧 active，draft 标记 archived |
| 10. 更新 readiness | 模型激活证据 | `ml_training_ready=1`、`recommendation_phase=MODEL_RETRAINED` | batch-support 普通本科/专科 smoke 通过 | 更新回 `OFFICIAL_DATA_IMPORTED`，`ml_training_ready=0` |

## 五、2026 官方数据上线前前端状态确认

当前前端满足上线前要求：

| 要求 | 当前状态 |
|---|---|
| 页面显示 2026 目标年份 | 满足，读取 `activeAdmissionYear=2026` |
| 显示官方数据未发布 | 满足，`PRE_OFFICIAL_DATA` 文案展示未发布 |
| 显示 2024/2025 为历史训练数据 | 满足，读取 `trainingYears=[2024,2025]` |
| 不显示 `FULL_RECOMMEND` | 源码门禁满足；生产 smoke 需上线前复验 |
| 按钮文案不能是“正式生成志愿方案” | 满足，当前为预估/缺口语义 |
| 可显示“查看趋势分析 / 预估参考 / 数据准备进度” | 前两项满足；`OFFICIAL_DATA_PARTIAL` 已补数据准备进度文案，仍需上线前 smoke |

## 六、2026 数据导入后前端状态

| readiness | 前端期望 | 当前缺口 |
|---|---|---|
| `OFFICIAL_DATA_IMPORTED` | batch-support 按后端 supportLevel 自动显示；显示“2026 官方数据已导入”；结果页不再显示 `PRE_OFFICIAL_DATA` 警示 | 基本满足；按钮正式语义需随 supportLevel 验证 |
| `MODEL_RETRAINED` | 普通本科/专科可生成正式方案；保留“以考试院和高校章程为准”合规提示 | 基本满足；需上线前 E2E smoke |
| `OFFICIAL_DATA_PARTIAL` | 显示数据准备进度；仍不开放 `FULL_RECOMMEND` | 已补专门文案后，前端应展示部分导入/准备进度语义 |

## 七、回滚策略

### staging 回滚

- 删除同一 `import_batch_id` 的 staging 行。
- 保留原始文件和质量报告，不删除证据。
- 回滚前执行 `SELECT COUNT(*)` 和分布统计。

### 正式表回滚

- 只允许按本批 `import_batch_id` 回滚。
- 正式表缺少 `import_batch_id` 的，不允许自动回滚，必须先建立映射或人工确认自然键。
- body-only 导入与 rollback SQL 必须一一对应。
- 回滚后重新执行 batch-support smoke，确认 2026 回到 `QUERY_ONLY` 或上一阶段。

### readiness 回滚

- `MODEL_RETRAINED` 回退到 `OFFICIAL_DATA_IMPORTED`：`ml_training_ready=0`。
- `OFFICIAL_DATA_IMPORTED` 回退到 `OFFICIAL_DATA_PARTIAL`：对应缺陷数据 ready 标记置 0。
- `OFFICIAL_DATA_PARTIAL` 回退到 `PRE_OFFICIAL_DATA`：全部官方 ready 标记置 0。

### 模型回滚

- 保留旧 active 模型版本。
- 新模型先注册 `draft`。
- 激活失败或效果退化时：新版本标记 `archived`，旧版本保持/恢复 `active`。
- readiness 不得进入 `MODEL_RETRAINED`。

## 八、管理员操作步骤

1. 下载官方文件，记录官方发布 URL、文件名、下载时间、hash。
2. 选择数据类型并生成 `import_batch_id`。
3. 解析到对应 `stg_gz_2026_*` 表。
4. 运行 staging quality gates。
5. 生成 `quality_report.md`、`hard_gate_report.csv`、样本 CSV、rollback SQL。
6. 人工审核来源、批次、样本、异常行。
7. 若只完成部分数据，更新到 `OFFICIAL_DATA_PARTIAL`。
8. 全部关键数据通过后，准备正式表 body-only import SQL。
9. 单独确认后才执行正式表导入。
10. 正式表导入后运行 post-check。
11. 更新 `OFFICIAL_DATA_IMPORTED`，只允许普通本科/专科 `TRIAL_RECOMMEND` 级别验证。
12. 准备离线重训，不直接生产激活。
13. 回测不退化后注册 draft 模型。
14. 单独确认后激活模型。
15. 更新 `MODEL_RETRAINED`。
16. 做 API smoke、前端 smoke、日志检查。
17. 出正式上线报告。

## 九、正式上线检查清单

| 检查项 | 通过标准 | 证据 |
|---|---|---|
| 官方来源 | 每类数据有官方 URL、文件、hash、raw_text | source manifest |
| staging 完整 | 所有数据先进入 `stg_*`，有 `import_batch_id` | staging count SQL |
| 质量报告 | 每类数据有 `quality_report.md` | report 文件 |
| rollback | 每类数据有按 `import_batch_id` 的 rollback | rollback SQL |
| 批次不串 | 普通/提前/专项/艺术/体育语义无混入 | hard gate report |
| 计划数合法 | `plan_count > 0`，异常阈值人工复核 | quality SQL |
| 选科完整 | 普通本科/专科选科要求覆盖达标 | coverage report |
| 限制字段 | 体检/语种/单科/性别/中外合作等进入备注或限制字段 | field extraction report |
| readiness | 阶段与 ready flags 匹配 | `data_year_readiness` 查询 |
| batch-support | `OFFICIAL_DATA_IMPORTED` 前无 `FULL_RECOMMEND` | API smoke |
| 重训 | 2024/2025/2026 训练样本，离线不退化 | ML backtest report |
| 前端 | 阶段文案和按钮正确 | Playwright/screenshot |
| 日志 | 无 5xx、SQL 错误、未脱敏敏感数据 | journal/nginx logs |

## 十、当前还缺哪些脚本或字段

| 缺口 | 影响 | 建议 |
|---|---|---|
| `OFFICIAL_DATA_PARTIAL` 后端常量与前端文案 | DB 可返回该阶段，需后端/前端共同识别 | 已补实现与测试后再以验证结果收口 |
| `data_year_readiness` 只有单个 `latest_import_batch_id` | 多类数据批次无法结构化追踪 | 已新增 `data_year_readiness_batch` 迁移骨架 |
| 批次线正式表未明确 | 达线判断缺少结构化数据 | 新增/确认 `data_batch_line_gz` |
| 艺术/体育综合分规则缺结构化表 | 艺术/体育不能正式推荐 | 新增规则表和规则引擎消费链路 |
| 特殊计划资格规则缺结构化表 | 专项/定向/免费医学等不能正式推荐 | 新增资格规则表和资格校验服务 |
| `admission_plan` 与 `data_admission_plan_gz` 双路径并存 | 计数可兼容，但候选池主要读 `admission_plan` | 2026 正式计划优先落 `admission_plan`，必要时同步兼容表 |
| `admission_plan` 当前实体无 `import_batch_id/source_file/source_url/raw_text` | 正式表追溯与回滚不足 | 已新增 `admission_plan_import_audit` 映射表骨架 |
| `data_score_line_gz/data_major_score_gz` 来源字段不足 | 训练/历史分数追溯不完整 | 后续扩展 source 字段或建立 source registry |
| ML 训练 CSV 当前主体仍从 `data_score_line_gz` 抽取 | 2026 招生计划和 score rank 已进入质量报告盘点，但监督训练仍需要真实标签 | 已补 `--train-years`、`--quality-report`、`--require-train-years`；后续再扩展正式特征合并和真实 2026 标签来源 |

## 脚本骨架索引

- `scripts/data/2026_official_import/00_staging_schema_templates.sql`
- `scripts/data/2026_official_import/10_quality_gate_templates.sql`
- `scripts/data/2026_official_import/20_readiness_update_templates.sql`
- `scripts/data/2026_official_import/30_formal_promote_templates.sql`
- `scripts/data/2026_official_import/40_rollback_templates.sql`
- `scripts/data/2026_official_import/build_quality_report.py`
- `scripts/data/2026_official_import/retrain_2026_plan.sh`
- `gzly-server/src/main/resources/db/20260513_2026_import_audit_tables.sql`
