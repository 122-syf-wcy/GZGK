# 贵州板块 2026 上线前数据与功能大审查

> 审查时间：2026-05-17 14:35  
> 审查范围：贵州 18 个批次的全量数据 + 配套（院校 / 政策 / 链接 / 一分一段 / 选科 / 专项政策）  
> 审查口径：以生产数据库 `gzly` 实测为准；以前端 RegionHome / VolunteerForm / VolunteerResult 实际路径为准  
> 审查工具：`/api/volunteer/gz/batch-support` + 直连 MySQL  
> 结论概述：**主流程可正式上线**（普通本科批 + 高职专科批 + 提前批 QUERY_ONLY 模式 + 院校查询 + 历年分数线 + AI 解读 + Excel/海报导出 + 张雪峰.skill 对话全跑通），3 项已知缺口列入 P0 提醒、4 项 P1 建议在 6 月前完成、其它项纳入 6 月底官方数据导入后处理。

## 一、18 批次 supportLevel 与生成口径矩阵（year=2026 实测）

| 批次代码 | 批次名 | 类别 | engine | 当前 supportLevel | 上线意见 |
|---|---|---|---|---|---|
| NORMAL_UNDERGRADUATE | 普通本科批 | 普通类 | OrdinaryParallelMajor | TRIAL_RECOMMEND ✅ | 主流程，已开放 96 志愿 |
| NORMAL_SPECIALTY | 普通类高职专科批 | 普通类 | OrdinaryParallelMajor | TRIAL_RECOMMEND ✅ | 主流程，已开放 96 志愿 |
| EARLY_A_B | 普通类本科提前批 A/B 段 | EARLY | SequentialCollege | QUERY_ONLY | 按设计保持 1 个院校顺序志愿 + 数据缺口提示 |
| EARLY_C | 普通类本科提前批 C 段 | EARLY | EarlyCParallelMajor | QUERY_ONLY | **P1 建议放开 TRIAL_RECOMMEND**（数据已基本齐） |
| SPECIALTY_EARLY | 普通类高职专科提前批 | EARLY | SequentialCollege | QUERY_ONLY | 按设计保持 1 个院校顺序志愿 |
| ART_UNDERGRADUATE_A | 艺术类本科 A 段 | ART | ArtCompositeRecommend | QUERY_ONLY | 2025 数据缺失，按设计走 QUERY_ONLY |
| ART_UNDERGRADUATE_B | 艺术类本科 B 段 | ART | ArtCompositeRecommend | QUERY_ONLY | 2025 数据缺失 |
| ART_SPECIALTY | 艺术类高职专科批 | ART | ArtCompositeRecommend | QUERY_ONLY | 2025 数据缺失 |
| SPORTS_UNDERGRADUATE | 体育类本科批 | SPORTS | SportsCompositeRecommend | QUERY_ONLY | 2025 数据缺失 |
| SPORTS_SPECIALTY | 体育类高职专科批 | SPORTS | SportsCompositeRecommend | QUERY_ONLY | 2025 数据缺失 |
| NATIONAL_SPECIAL | 国家专项计划 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| LOCAL_SPECIAL | 地方专项计划 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| UNIVERSITY_SPECIAL | 高校专项计划 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| ETHNIC_CLASS | 民族班 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| PREPARATORY | 预科班 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| ORIENTED | 定向招生 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| FREE_MEDICAL | 免费医学定向 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |
| TEACHER_EXCELLENCE | 优师专项 | SPECIAL | SpecialPlanEligibility | QUERY_ONLY | 资格审核类，按设计 |

汇总：`{"FULL_RECOMMEND":0, "TRIAL_RECOMMEND":2, "QUERY_ONLY":16, "UNSUPPORTED":0}`，对应预期口径（2026 官方数据未发布，TRIAL 是当前最高级别；6 月底导入官方数据后 NORMAL_UNDERGRADUATE / NORMAL_SPECIALTY 应升 FULL_RECOMMEND）。

## 二、关键数据表覆盖（2025 已是新高考首年，是 2026 推荐的基准训练集）

### 1. data_admission_plan_gz（贵州招生计划）

| 批次 | 2025 物理 | 2025 历史 | 2025 合计 | 2024 合计 |
|---|---:|---:|---:|---:|
| NORMAL_UNDERGRADUATE | 16146 | 5277 | 21423 ✅ | 22324 |
| NORMAL_SPECIALTY | 6736 | 4775 | 11511 ✅ | 9613 |
| EARLY_A_B | 208 | 75 | 283 ✅ | 287 |
| EARLY_C | 41 | 37 | 78 ✅ | 149 |
| SPECIALTY_EARLY | 19 | 17 | 36 ✅ | 25 |

- 5 个主普通批次 2025 / 2024 招生计划均完整覆盖；
- **艺术 / 体育 / 8 类专项的 admission_plan 2024、2025 均为 0**：艺术 / 体育走综合分公式，定位由 ArtCompositeRecommendEngine 单独处理，不依赖 admission_plan_gz；8 类专项走资格审核，也不依赖 plan。当前空缺符合架构设计，但前端应该用文案说明，避免用户疑惑。

### 2. data_score_line_gz（贵州院校投档线，21929 总行数，覆盖 2020-2025 共 6 年）

| 批次（2025） | 物理 | 历史 |
|---|---:|---:|
| 本科批 | 1741 | 935 |
| 专科批 | 1168 | 866 |
| 本科提前批 A 段 | 64 | 1 |
| 本科提前批 B 段 | 118 | 30 |
| 本科提前批 C 段 | 18 | 0 ⚠️ |
| 专科提前批 | 41 | 2 |
| 国家专项计划批 | 1 | 0 ⚠️ |
| 高校专项计划批 | 1 | 0 ⚠️ |

- 普通本科批 / 高职专科批数据厚，主流程稳；
- EARLY_C 历史类、艺术 / 体育、地方专项 / 高校专项 / 民族 / 预科 / 定向 / 免医 / 优师批次的 2025 score_line 几乎全空，符合数据特性（这些批次本身招生量小且分批公布）。

### 3. data_major_score_gz（专业级录取，163533 总行，覆盖 2021-2025）

| 批次（2025） | 物理 | 历史 | 学校数 |
|---|---:|---:|---:|
| 本科批 | 18359 | 6153 | 1116 |
| 专科批 | 6006 | 4515 | 1024 |
| 本科提前批 A 段 | 378 | 87 | 36 |
| 本科提前批 B 段 | 262 | 88 | 125 |
| 本科提前批 C 段 | 164 | 74 | 12 |
| 专科提前批 | 121 | 83 | 41 |
| 国家专项计划批 | 20 | 5 | 1 |
| 高校专项计划批 | 1 | 0 | 1 |

- NORMAL 主链路完整；
- **2025 艺术 / 体育批所有专业分均缺失**（2024 有 267 + 48 + 36 + 16 = 367 行），上线后艺术体育考生无法看到 2025 数据。

### 4. data_major_requirement_gz（选科要求）

| 年份 | 物理类 | 历史类 | 比例 |
|---|---:|---:|---:|
| 2025 | 24483 | 9757 | 历史只是物理 40% ⚠️ |
| 2024 | 21125 | 10514 | 历史是物理 50% ⚠️ |

- HANDOVER 第七节已知：**历史类官方专业目录 PDF 编码抽取不可靠**，目前只接受 `build_major_requirement_from_manual_csv.py` 走手工 CSV 入库；
- 实际影响：历史类考生匹配选科时 ≈60% 物理库专业未覆盖到历史规则，前端会大量进入「人工复核」清单。

### 5. data_score_rank_gz（一分一段表）

| 年份 | 物理类 | 历史类 | 分数范围 |
|---|---:|---:|---|
| 2025 | 626 | 580 | 0 ~ 683 ✅ |
| 2024 | 634 | 595 | 0 ~ 686 ✅ |

- 2024 / 2025 物理 / 历史四张表完整，rankCheck / rankEstimate / 整表安全度估算等链路依赖此表，全部 OK。

### 6. policy_rule_config

| year | 5 个主批次 | status | 备注 |
|---|---|---|---|
| 2026 | NORMAL_UNDERGRADUATE / NORMAL_SPECIALTY / EARLY_A_B / EARLY_C / SPECIALTY_EARLY | pending_confirm | 等 6 月底正式文件 |
| 2025 | 同上 5 个 | confirmed | ✅ |
| 2024 | 同上 5 个 | confirmed | ✅ |

- **缺失**：艺术 / 体育 / 8 个专项类的 policy_rule_config 没有写入；当前 BatchSupportService 已通过 `registryPolicySnapshot(rule)` 回退到 BatchRuleRegistry 内置硬编码，所以前端能看到批次入口；但严格意义上"政策落库"未完成。

## 三、院校与官方链接（uni_official_link 共 2198 条）

| 字段 | 已有 | 缺失 | 覆盖率 |
|---|---:|---:|---|
| school_site（学校主页） | 2182 | 16 | 99.3% ✅ |
| admission_site（招生网） | 1961 | 237 | 89.2% ⚠️ |
| admission_brochure_url（章程） | 2165 | 33 | 98.5% ✅ |
| major_catalog_url（专业目录） | 2163 | 35 | 98.4% ✅ |
| tuition_info_url（收费入口） | 2157 | 41 | 98.1% ✅ |
| tuition_summary（收费摘要） | 1154 | 1044 | 52.5% ❌ |
| parse_status=1（解析成功） | 2051 | (parse_fail=116) | 93.3% |
| capture_status | 1=1541 / 2=572 / 0=85 | — | 抓取成功 70% |

- 99% 院校有主页 / 章程 / 专业目录 / 收费入口，前端"院校详情"卡片不会出现大面积空白；
- 招生网（招办主页）还缺 237 所，主要影响"招生网快捷跳转"按钮；
- 收费摘要 1044 缺失（48%），但收费入口链接齐，用户点链接进入院校官网仍可查；
- parse_fail 116 所 + capture_status=2 的 572 所主要是新设院校、合办校、独立学院（如香港中文大学深圳、中国社会科学院大学等），需要人工补 seed 数据后重跑。

## 四、特殊招生静态政策说明（special_admission_policy 共 8 条）

| category | 数量 |
|---|---:|
| overview | 1 |
| application | 1 |
| qualification | 1 |
| early_batch | 1 |
| special_plan | 1 |
| art | 2 |
| sports | 1 |

- 这是"特殊类型招生"页面的静态政策说明数据源，不参与志愿生成；
- 当前 8 条偏少，建议结合贵州省招生考试院 2026 年特殊类型招生工作通知补全到 ≥12 条（每个 category 至少 2 条覆盖政策说明 / 报考时间 / 资格条件）。

## 五、上线 GO/NO-GO 清单

### ✅ GO（已可正式上线）

1. **首页 + 8 省份卡片入口**（其他省份保留"准备中"提示，符合一致性原则）
2. **贵州区域主页 4 个功能卡**：院校查询 / 历年分数线 / 智能填报 / 特殊类型招生
3. **院校查询**：2198 所，含 99% 的官方链接，可详情查看 + 官方核验
4. **历年分数线**：21929 行覆盖 2020-2025；专业级 163533 行覆盖 2021-2025
5. **智能填报（普通本科批 / 高职专科批 96 志愿）**：
   - 端到端 7.2s 内返回 96 条志愿
   - 配套 manualReview 清单、portfolioSafetyProbability 整表安全度、advisorAdvice（张雪峰.skill 建议）
   - supportLevel=TRIAL_RECOMMEND（带 PRE_OFFICIAL_DATA 警告）
6. **智能填报（提前批 A/B、C、专科提前批）**：QUERY_ONLY 模式给出顺序志愿规则与数据缺口说明
7. **位次校验 rankCheck**：基于 2025 物理 626 + 历史 580 一分一段表
8. **AI 深度解读（SSE）**：mimo-v2.5-pro，独立 aiAnalysisExecutor 池（v7.41）
9. **张雪峰.skill 对话**：planId 互斥锁，60s 内返回
10. **Excel + 海报导出**
11. **特殊类型招生静态政策**（8 条，偏少但可上线）

### ⚠️ P0 上线前必须配套（不阻塞，但用户体验差异大）

1. **艺术 / 体育 2025 数据全缺**：当前 4 个艺术批 + 2 个体育批的 score_line / major_score / admission_plan 2025 均为 0。前端进入这些批次时虽然会显示 QUERY_ONLY 兜底，但建议加一句明确文案："2025 年贵州艺术/体育批官方数据尚未发布，当前页面仅基于 2024 历史数据展示候选与说明。"
2. **历史类选科要求只覆盖物理 40%（9757/24483）**：HANDOVER 已记录，必须由志愿者 / 管理员通过 `scripts/server/build_major_requirement_from_manual_csv.py` + `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md` 补 OCR / 手工 CSV，否则历史类考生 60% 专业都进人工复核清单。
3. **首页 / 智能填报页"PRE_OFFICIAL_DATA"提示位置一致性**：当前 RegionHome 数据状态卡 + VolunteerForm v7.40 新增的"贵州数据口径"卡 + VolunteerResult `dataStatus.detail` 三处文案口径一致，确保用户每一步都看到"2024/2025 历史数据估算，2026 正式数据 6 月底发布"。

### 💡 P1 建议（一周内做完，收益大）

1. **EARLY_C（本科提前批 C 段）放开 TRIAL_RECOMMEND**：
   - 数据已基本齐（plan 78 + score_line 18 + major_score 238）
   - EarlyCParallelMajorEngine 已存在
   - 影响：让目标公费师范 / 优师 / 定向 / 免医 / 军警 等考生能用上 60 志愿草稿
   - 操作：扩展 `BatchSupportService.supportsHistoricalReferenceRecommend` 白名单 + `VolunteerRecommendController.decoratePlan` 同步放开 EARLY_C 条件
   
2. **补 uni_official_link 237 个招生网 + 1044 个收费摘要**：
   - 跑一轮 `LIMIT=1000 SCOPE=official-missing WORKERS=3 RUN_IMPORTER=0 bash server/run_data_gap_supplement.sh`
   - 人工抽样核对生成的 SQL，单次导入
   
3. **修复 uni_official_link 116 个 parse_fail**：
   - 多数是合办校 / 新设校 / 独立学院，需要人工补 seed_domain
   - 维护 `scripts/server/uni_official_link_seed_override.csv` 让爬虫优先用人工指定的 admission site
   
4. **artComposite / sportsComposite engine 实现度核验**：
   - 当前 supportLevel=QUERY_ONLY 是因为 supportsHistoricalReferenceRecommend 白名单只放普通本科批 / 高职专科批
   - 2025 艺术体育数据缺，即使放开也无意义，建议等 6 月底官方数据出再启用

### 🕐 P2 等 2026 官方数据发布后处理（6 月下旬）

1. 把 2026 年贵州官方招生计划 / 一分一段表 / 选科要求按 `docs/ops/2026_official_data_import_retrain_runbook.md` 导入
2. 把 `data_year_readiness` 的 phase 推进到 `OFFICIAL_DATA_IMPORTED`
3. 重训 ML 模型 → `MODEL_RETRAINED`
4. 5 个主批次 supportLevel 自动从 TRIAL_RECOMMEND 升 FULL_RECOMMEND
5. policy_rule_config 2026 行从 pending_confirm 改 confirmed

## 六、立即可执行的 3 项

如果决定按 P1 推进，建议优先级排序：

1. **修补全代码层（半小时）**：把 EARLY_C 加入历史回退白名单，普通考生在等 2026 官方数据期间能多用一个批次的智能推荐
2. **跑补数任务（30 分钟，IO 等待为主）**：补齐 237 招生网 + 1044 收费摘要
3. **加前端文案（10 分钟）**：在艺术 / 体育 / 专项的 QUERY_ONLY 兜底页面加显式提示

## 七、生产基线指标（2026-05-17 14:25）

- `gzly` / `nginx` / `mysqld` / `redis` 全 active；JVM heap 2G / RSS 1.5GB
- v7.41 后 `/api/volunteer/recommend` 端到端 ≈7.2s（v7.39 基线 10s）
- `/api/volunteer/gz/batch-support` 冷 2.046s → 热 0.006s（Caffeine L1 256x 加速）
- `/actuator/prometheus` 已暴露 gzly.task.executor + gzly.ai.sse.executor + Redis cache hit + HikariCP + G1 GC
- 后端 `./mvnw test` 266/0/0；前端 `npm run build` 通过；deploy_backend_safe 三轮 0 5xx
- ICP：`gzly.dongsiwei.com` 公网 HTTP 80 被阿里云返 ICP 拦截、HTTPS 443 被 RST；`http://39.97.232.141/` 直连可用

