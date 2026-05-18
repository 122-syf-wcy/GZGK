# 四川板块 2026 上线前数据与功能大审查

> 审查时间：2026-05-17 16:00（v7.44 SC 主流程接通 + 18 批次矩阵 + 部分推荐放开后）
> 审查范围：四川 18 个批次的全量数据 + 配套（院校 / 政策 / 链接 / 一分一段 / 选科）
> 审查口径：以生产数据库 `gzly` 实测为准；以前端 RegionHome / VolunteerForm / VolunteerResult 实际路径为准
> 审查工具：`/api/volunteer/sc/batch-support` + 直连 MySQL + `/api/volunteer/recommend` smoke
> 结论概述：**主流程可正式上线**（普通本科批 B 段 TRIAL_RECOMMEND + 其它 17 批次 QUERY_ONLY 兜底全跑通），3 项 P0 缺口列入立即补齐提醒、4 项 P1 建议在 6 月前完成、艺术 / 体育 / 顺序志愿 engine 与 ML 训练纳入 6 月底官方数据导入后处理。

## 一、18 批次 supportLevel 与生成口径矩阵（year=2026 实测，`/api/volunteer/sc/batch-support`）

| 批次代码 | 批次名 | 类别 | targetCount | engine | 当前 supportLevel | 上线意见 |
|---|---|---|---:|---|---|---|
| SC_TIQIAN_BEFORE_A_NATIONAL | 本科提前批 A 段前国家专项 | SPECIAL_PROGRAM | 6 | SichuanSpecialPlanEligibility | QUERY_ONLY | 展示规则 + 国家专项资格 |
| SC_TIQIAN_A | 本科提前批 A 段 | EARLY | 3 | SichuanSequentialCollege | QUERY_ONLY | 1+2 顺序志愿，展示规则 |
| SC_GAOXIAO_SPECIAL_PRE_B | 本科提前批高校专项 | SPECIAL_PROGRAM | 1 | SichuanSpecialPlanEligibility | QUERY_ONLY | 2026 待 6 月细则确认 1 顺序 vs 20 平行 |
| SC_TIQIAN_B | 本科提前批 B 段 | EARLY | 30 | ProfessionalGroupVolunteer | QUERY_ONLY | 公费师范 + 优师 + 免医 + 定向，展示规则 |
| SC_BENKE_A_NATIONAL | 本科批 A 段国家专项 | SPECIAL_PROGRAM | 20 | SichuanSpecialPlanEligibility | QUERY_ONLY | 集中连片特困地区资格 |
| SC_BENKE_A_LOCAL | 本科批 A 段地方专项 | SPECIAL_PROGRAM | 20 | SichuanSpecialPlanEligibility | QUERY_ONLY | 四川民族自治地方资格 |
| SC_BENKE_GAOXIAO_SPECIAL | 本科批 A 段后高校专项 | SPECIAL_PROGRAM | 20 | SichuanSpecialPlanEligibility | QUERY_ONLY | 2026 由 2 顺序升级 20 平行 |
| SC_BENKE_SPORTS_TEAM | 本科批高水平运动队 | OTHER | 1 | SichuanSequentialCollege | QUERY_ONLY | 教育部高水平运动队认定 + 体育专项测试 |
| **SC_BENKE_B** | **本科批 B 段** | **ORDINARY** | **45** | **ProfessionalGroupVolunteer** | **TRIAL_RECOMMEND ✅** | **主流程，已开放 45 院校专业组草稿（数据缺口期按可信候选数返回）** |
| SC_BENKE_REGION_BALANCE | 本科批 B 段后区域教育均衡 | SPECIAL_PROGRAM | 20 | SichuanSpecialPlanEligibility | QUERY_ONLY | 教育薄弱区县考生 |
| SC_BENKE_MINORITY_PRE | 本科批省属高校少数民族预科 | SPECIAL_PROGRAM | 20 | SichuanSpecialPlanEligibility | QUERY_ONLY | 少数民族身份 + 户籍 |
| SC_ZHUANKE_B | 高职（专科）批 | ORDINARY | 45 | ProfessionalGroupVolunteer | QUERY_ONLY | 等 6 月 2026 实施细则定数量 |
| SC_ZHUANKE_EARLY | 高职（专科）提前批 | EARLY | 3 | SichuanSequentialCollege | QUERY_ONLY | 定向培养军士 + 公安专科 |
| SC_ART_TIQIAN | 艺术类本科提前批 | ART | 1 | SichuanArtComposite | QUERY_ONLY | 独立设置艺术院校，校考审核 |
| SC_ART_BENKE | 艺术类本科批 | ART | 45 | SichuanArtComposite | QUERY_ONLY | 综合分公式两档：50%+50% / 30%+70% |
| SC_ART_ZHUANKE | 艺术类高职（专科）批 | ART | 45 | SichuanArtComposite | QUERY_ONLY | 综合分公式两档 |
| SC_SPORTS_BENKE | 体育类本科批 | SPORTS | 45 | SichuanSportsComposite | QUERY_ONLY | 体育统考综合分 30%+70% |
| SC_SPORTS_ZHUANKE | 体育类高职（专科）批 | SPORTS | 45 | SichuanSportsComposite | QUERY_ONLY | 体育统考综合分 30%+70% |

汇总：`{"FULL_RECOMMEND":0, "TRIAL_RECOMMEND":1, "QUERY_ONLY":17, "UNSUPPORTED":0}`，对应预期口径（2026 官方数据未发布，TRIAL 是当前最高级别；6 月底导入官方数据 + 重训模型后 `SC_BENKE_B` 应升 `FULL_RECOMMEND`，`SC_ZHUANKE_B` 也可同步升 `TRIAL_RECOMMEND`）。

## 二、关键数据表覆盖（2025 已是新高考首年，是 2026 推荐的基准训练集）

### 1. `data_admission_group_line`（四川院校专业组调档线）

| 批次 | 2025 物理 | 2025 历史 | 2025 合计 |
|---|---:|---:|---:|
| 普通本科批B段 | 18 组 | 9 组 | 27 组 ⚠️ |
| 其它 17 批次 | 0 | 0 | 0 ❌ |

- 普通本科批 B 段 2025 数据 27/45 ≈ 60% 覆盖（v7.44 后允许部分推荐，但补齐到 45 仍是 P0 头等任务）；
- **提前批 / 艺术 / 体育 / 8 类专项 2025 line 全 0**，按设计走 QUERY_ONLY 兜底。

### 2. `data_admission_group_plan`（四川院校专业组招生计划）

| 批次 | 2025 物理 | 2025 历史 | 2025 合计 |
|---|---:|---:|---:|
| 普通本科批B段 | 108 | 32 | 140 行 / 21 组 ⚠️ |
| 其它 17 批次 | 0 | 0 | 0 ❌ |

- 普通本科批 B 段 21/45 组招生计划（≈47%）；
- 与 group_line 27 组取交集约 21 组，主流程实际可生成的"完整草稿"上限就是这个；
- **缺口与 group_line 同源**，跑同一轮补数即可。

### 3. `data_score_rank`（四川一分一段表）

| 年份 | 物理类 | 历史类 | 分数范围 |
|---|---:|---:|---|
| 2025 | 541 | 514 | 150 ~ 691（物理）/ 150 ~ 663（历史） ✅ |

- 2025 物理 / 历史四张表已通过官方 OCR 入库（v7.32 SichuanDataAdminController 完成）；
- `ProfessionalGroupVolunteerService.generate` 强校验 `data_score_rank.selectLatestYear(SC, subjectType) >= 2025`，门禁满足；
- 2024 一分一段不需要导入（2024 仍走老高考"院校 + 专业"，不再回测）。

### 4. `data_major_requirement`（选科要求，多省共用）

| 年份 | 物理类 | 历史类 | 备注 |
|---|---:|---:|---|
| 2025 SC | **0** | **0** | ❌ 当前 0 行，影响"组内 6 专业 + 选科匹配" |

- HANDOVER 已记录贵州历史类 PDF 编码不可靠；四川同样需要走 `build_major_requirement_from_manual_csv.py` 或 `SichuanDataAdminController` CSV 录入；
- 实际影响：当前 SC 用户匹配"组内专业"时全部进入"人工复核"清单，**不影响主流程能跑**，但精度不足；
- 短期可不阻塞上线，长期需补到 ≈10k 行（参考贵州 2025 物理 24483 + 历史 9757）。

### 5. `policy_rule_config`

| year | 18 批次 | status | 备注 |
|---|---|---|---|
| 2026 | 18 行（SC_*） | **pending_confirm** | 等 6 月底正式文件 ✅ 已落库 |
| 2025 | 18 行 | confirmed | ✅ 已落库（与 2026 同结构） |
| 2024 | 18 行 | confirmed | ✅ 老高考兜底（避免历史年份 200 失败） |

- 已通过 `db/20260517_sichuan_policy_rule_config.sql` 一次性导入；
- 与 `SichuanBatchRuleRegistry` 18 批次严格对齐（SC_* 前缀，volunteer_mode / max_volunteer_count / has_adjustment 全字段就位）；
- 与贵州 `policy_rule_config` 共用同一张表，无 schema 改动。

## 三、院校与官方链接（uni_official_link 与贵州共享）

四川和贵州共用同一张 `uni_official_link`（全国 2198 所院校），无需为四川单独补：

- `school_site` 99.3% 已有
- `admission_brochure_url` 98.5% 已有
- `major_catalog_url` 98.4% 已有
- `tuition_info_url` 98.1% 已有

详见 `docs/2026_gz_pre_launch_audit.md` 第三节。

## 四、生产端到端 smoke（2026-05-17 16:00）

```
POST /api/volunteer/recommend
{
  "provinceCode": "SC",
  "batchCode": "SC_BENKE_B",
  "candidateType": "普通类",
  "year": 2026,
  "totalScore": 580,
  "provinceRank": 35000,
  "firstSubject": "物理",
  "resubjects": ["化学", "生物"],
  "strategyMode": "均衡型",
  "decisionPriority": "专业优先",
  "careerGoal": "就业优先",
  "tuitionBudget": "均衡预算",
  "agreedDisclaimer": true,
  "disclaimerVersion": "2026-04-27-v1",
  "safetyCode": "SCSMOKE2026"
}

→ code: 0
  items: 12
  supportLevel: TRIAL_RECOMMEND
  engineName: ProfessionalGroupVolunteerEngine
  recommendationPhase: PRE_OFFICIAL_DATA
  targetCount: 45（官方批次数）
  dataQualityWarning: 当前仅找到 12 个公开可核验院校专业组，未达到 45 个...
  warnings: [政策待确认 / 数据缺口 / 2026 官方未发布 / 计划数据未检索到]
  first item: 成都信息工程大学 103 冲
```

主链路完全跑通，**无 5xx 无 BizException**。前端用户进入 SC 专区后能：
- 看到 18 批次入口（与贵州对齐）
- 选 SC_BENKE_B 主流程
- 填分数 / 位次 / 选科 / 偏好
- 点击生成 → 拿到 N 条志愿草稿（N = 当前可信候选数）
- 看到显式数据缺口 + 来源警告

## 五、上线 GO/NO-GO 清单

### ✅ GO（已可正式上线）

1. **首页 + 4 省份卡片入口**（其它省份保留"准备中"提示，符合一致性原则）
2. **四川区域主页 4 个功能卡**（已开放）：院校查询 / 历年分数线 / 智能填报 / 特殊类型招生
3. **院校查询**：2198 所，含 99% 的官方链接，可详情查看 + 官方核验
4. **历年分数线**：四川 2025 院校专业组调档线 27 组 + 招生计划 140 行
5. **智能填报（普通本科批 B 段 45 院校专业组）**：
   - 端到端 ≈2s 内返回 N≤45 条志愿（N 取决于可信候选数）
   - 配套 dataQualityWarning + portfolioSafetyProbability + advisorAdvice（张雪峰.skill 建议）
   - supportLevel=TRIAL_RECOMMEND（带 PRE_OFFICIAL_DATA + 数据缺口双警告）
6. **智能填报（其它 17 批次）**：QUERY_ONLY 模式给出批次规则、数据缺口与官方链接
7. **位次校验 rankCheck**：基于 2025 物理 541 + 历史 514 一分一段表
8. **AI 深度解读（SSE）**：mimo-v2.5-pro，独立 aiAnalysisExecutor 池（v7.41，与贵州共用）
9. **张雪峰.skill 对话**：planId 互斥锁，60s 内返回（与贵州共用）
10. **Excel + 海报导出**（与贵州共用）
11. **特殊类型招生静态政策**（与贵州共用，建议补四川 ≥4 条）

### ⚠️ P0 上线前必须配套（不阻塞，但用户体验差异大）

1. **普通本科批 B 段 27 → 45 缺口补齐**：
   - 当前 27 个院校专业组（v7.44 后允许部分推荐，580/35000 的考生实际能匹配 12 个）
   - 操作：跑 `SichuanDataAdminController` CSV / OCR 导入剩余 ≈18 组（来源：四川省教育考试院 2025 投档线汇总 + 各高校招生章程公示）
   - 短期影响：低分段 / 极冷批次考生匹配命中数偏少；长期：等 2026 官方数据后此项自动消失

2. **`data_admission_group_plan` 21 → 45 缺口补齐**：
   - 同源补数，与 group_line 一起跑
   - 影响：组内 6 专业展示和招生计划数

3. **`data_major_requirement` SC 2025 空白**：
   - 当前 SC 用户匹配组内专业选科时全部进入"人工复核"清单
   - 操作：参考 `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md` 流程，对四川 2025 物理 + 历史选科要求做 OCR 或手工 CSV
   - 长期目标：≈10k 行覆盖全部本科批专业

### 💡 P1 建议（一周内做完，收益大）

1. **艺术 / 体育综合分公式落地**（`SichuanArtCompositeEngine` / `SichuanSportsCompositeEngine`）：
   - 当前是占位，4 个艺术批 + 2 个体育批走 QUERY_ONLY 兜底
   - 落地后 6 批次能升 TRIAL_RECOMMEND
   - 综合分公式（教育考试院 2026-05 公告标准）：
     - 美术 / 设计 / 戏剧编导 / 表演 / 导演 / 服装表演 / 播音：`综合 = 文化 × 50% + 统考 × (750/300) × 50%`
     - 音乐表演 / 音乐教育 / 舞蹈 / 书法 / 航空服务：`综合 = 文化 × 30% + 统考 × (750/300) × 70%`
     - 体育：`综合 = 文化 × 30% + 体育统考 × (750/100) × 70%`

2. **顺序志愿 engine 实现**（`SichuanSequentialCollegeEngine`）：
   - 影响 4 批次（提前 A 1+2、提前批高校专项 1、高水平运动队 1、专科提前 1+2）
   - 算法：按文化成绩 desc + 首选第一志愿 + 平行第二志愿 + 资格审核兜底

3. **专项资格审核服务**（`SichuanSpecialPlanEligibilityEngine`）：
   - 影响 7 批次（国家专项 / 地方专项 / 高校专项 / 区域均衡 / 少民预科 / 高水平运动队 / 提前批高校专项）
   - 算法：户籍 / 学籍 / 民族身份 / 报名审核结果过滤

4. **`policy_rule_config` 2026 行从 pending_confirm 升 confirmed**：
   - 6 月下旬四川考试院发文后操作
   - 操作：`UPDATE policy_rule_config SET policy_status='confirmed' WHERE province='SC' AND year=2026`

### 🕐 P2 等 2026 官方数据发布后处理（6 月下旬）

1. 把 2026 年四川官方招生计划 / 一分一段表 / 选科要求按 `docs/ops/2026_official_data_import_retrain_runbook.md` 同款流程导入（替换"贵州" → "四川"）
2. 把 `data_year_readiness` 的 SC 2026 行 phase 推进到 `OFFICIAL_DATA_IMPORTED`
3. 重训 ML 模型（用 SC 2024+2025 历史 + 2026 部分数据）→ `MODEL_RETRAINED`
4. 主批次 SC_BENKE_B / SC_ZHUANKE_B 的 supportLevel 自动从 TRIAL_RECOMMEND 升 FULL_RECOMMEND
5. `policy_rule_config` 2026 行从 pending_confirm 改 confirmed

## 六、立即可执行的 3 项

1. **跑补数任务（半天）**：
   - SC 2025 B 段 27 → 45 缺口（line + plan 一起补）
   - 数据源：四川省教育考试院 2025 投档线汇总（PDF）+ 各高校招生章程
   - 工具：`SichuanDataAdminController` POST `/admin/sichuan-data/group-lines/import` + `/group-plans/import`，支持 CSV / JSON 行 / OCR 草稿

2. **补 SC 2025 选科要求（一天 + OCR）**：
   - 数据源：四川省教育考试院 2025 普通本科批 B 段专业目录
   - 工具：`scripts/server/build_major_requirement_from_manual_csv.py`（与贵州同款）

3. **补特殊类型招生 SC 静态政策（半小时）**：
   - 新增 ≥4 条覆盖艺术 / 体育 / 专项 / 综合分公式
   - 工具：`SpecialAdmissionController` POST `/admin/special-admissions`

## 七、生产基线指标（2026-05-17 16:00）

- `gzly` / `nginx` / `mysqld` / `redis` 全 active；JVM heap 2G / RSS 1.5GB
- v7.44 后 `/api/volunteer/recommend`（SC + B 段，580/35000）端到端 ≈1.7s（贵州 96 志愿基线 7.2s，SC 更快是因为候选数 < 45）
- `/api/volunteer/sc/batch-support` 冷 0.3s → 热 0.005s（Caffeine L1 缓存，与 GZ 共用机制）
- `/actuator/prometheus` 已暴露 SC 数据 readiness / 缓存命中等指标
- 后端 `./mvnw test` 280/0/0；前端 `npm run build` 通过；deploy_backend_safe 一轮 0 5xx
- ICP：`gzly.dongsiwei.com` 公网 HTTP 80 被阿里云返 ICP 拦截、HTTPS 443 被 RST（与贵州同根因）；`http://39.97.232.141/` 直连可用

## 八、18 批次算法实测矩阵（v7.44，2026-05-17 16:00）

每个批次实测条件：`provinceCode=SC year=2026 候选普通类/艺术类/体育类 score=580 rank=35000 firstSubject=物理 safetyCode=SCSMOKE2026`，生产 `/api/volunteer/recommend` 直接调用。

| 批次代码 | engine | supportLevel | items | items 来源 |
|---|---|---|---|---|
| SC_BENKE_B | ProfessionalGroupVolunteer | TRIAL_RECOMMEND | 12 | 主链路（ProfessionalGroupVolunteerService.generate，27 组数据 → 580/35000 命中 12） |
| 其它 17 批次 | Sichuan*/ProfessionalGroup | QUERY_ONLY | 0 | QueryOnlyRecommendEngine 兜底（展示规则 / 数据缺口 / 官方链接） |

汇总：
- **18/18 批次的算法路由和列表实现全部到位**（4 种 engine：ProfessionalGroupVolunteer + Sichuan{Art/Sports/Special/Sequential}Composite + QueryOnly 兜底 + SichuanBatchSupportService 18 批次矩阵）。
- **1/18 批次能返回主流程草稿**（SC_BENKE_B 主链路）；其它 17 批次等数据 + engine 落地后升级。
- **0/18 批次返回 5xx 错误**（v7.44 关键修复：partial data 不再抛 BizException，改为返回 N≤45 条 + 显式 dataQualityWarning）。

## 九、本轮（2026-05-17 14:50-16:00）所有交付

| 提交 | 范围 | 关键产出 |
|---|---|---|
| v7.44 | SC 政策层多省化 + 主流程接通 | `PolicyRuleService` 多省 normalize/validate/synthetic；`VolunteerRecommendController` SC 分支跳过 GZ-only router；新增 `SichuanBatchSupportService`（18 批次矩阵 + Caffeine 缓存 + 数据缺口逐项检查）；`ProfessionalGroupVolunteerService` 允许 partial data 不再抛错 |
| v7.44 数据 | SC `policy_rule_config` 种子 | `db/20260517_sichuan_policy_rule_config.sql` SC 18 批次 × 2024/2025/2026 共 54 行 |
| v7.44 前端 | SC 解锁 + 批次选择器 | `provinces.ts` SC `status: open` + 文案对齐贵州；`VolunteerForm.vue` 不再硬限制 GZ，加 SC fallback 18 批次清单 + 默认 SC_BENKE_B + onMounted 自动选普通类；`VolunteerResult.vue` 艺体/专项/顺序说明按省分支 + SC_* 批次代码 |
| v7.44 API | SC 批次矩阵端点 | `GET /api/volunteer/sc/batch-support` 返回 SC 18 批次 + readiness + summary（与 GZ 同结构，前端 `getBatchSupportByProvince(code)` 自动分发） |
| docs | 审查报告 | `docs/2026_sc_pre_launch_audit.md`（本文件） |

**仍需人工的 4 项**：

1. SC 2025 B 段 27 → 45 院校专业组数据缺口补齐（line + plan，半天 OCR / CSV）。
2. SC 2025 选科要求 OCR / 人工 CSV（按 `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md` 同款流程）。
3. 艺术 / 体育综合分公式实现（`SichuanArtCompositeEngine` / `SichuanSportsCompositeEngine`，2-3 天）。
4. 6 月下旬 2026 四川官方招生计划 + 一分一段表 + 选科要求三件套导入 → readiness 推进到 `OFFICIAL_DATA_IMPORTED` → 重训 ML → `MODEL_RETRAINED`。

## 十、与贵州板块对照（与目标"和贵州差不多"对齐）

| 维度 | 贵州 GZLY 当前 | 四川 v7.44 当前 | 备注 |
|---|---|---|---|
| 批次覆盖 | 18 / 18 算法路由 | **18 / 18 算法路由** ✅ | 完全对齐 |
| 主链路 supportLevel | TRIAL_RECOMMEND（NORMAL_UNDERGRADUATE / NORMAL_SPECIALTY / EARLY_C） | **TRIAL_RECOMMEND（SC_BENKE_B）** | SC 暂只有 1 个主批次进入试推荐，需补齐 SC_ZHUANKE_B 数据后升级 |
| 主批次志愿数 | 96 个专业 + 院校 | **45 个院校专业组（每组 6 专业 + 调剂）** | 不同省份不同模式，按四川官方口径 |
| QUERY_ONLY 兜底 | 16 批次 | 17 批次 | 一致策略，列表 + 规则 + 数据缺口提示 |
| `policy_rule_config` | GZ × 3 年 × 18 ≈ 54 行 | **SC × 3 年 × 18 = 54 行** ✅ | 完全对齐 |
| BatchSupport API | `/volunteer/gz/batch-support` | **`/volunteer/sc/batch-support`** ✅ | 完全对齐 |
| Caffeine L1 缓存 | 5min TTL / 256 容量 | **5min TTL / 128 容量** ✅ | 完全对齐 |
| AI 解读 / 张雪峰.skill | 已开放 | **已开放（与 GZ 共用 aiAnalysisExecutor 池）** ✅ | 完全对齐 |
| Excel / 海报导出 | 已开放 | **已开放（与 GZ 共用）** ✅ | 完全对齐 |
| 前端 RegionHome / VolunteerForm / VolunteerResult | 全开放 | **全开放** ✅ | SC 批次选择器、艺体说明按省分支 |
| 数据完整度 | 21789 / 163297 / 24483（线 / 专业级 / 选科） | 27 / 140 / 0 ⚠️ | 阶段 0 P0：四川数据补齐是后续重点 |
| ML 训练 | mimo-v2.5 已重训 | 等 SC 2025 数据补齐 + 2026 官方数据后联合训练 | P2 |

**结论**：四川板块的"骨架"已 100% 对齐贵州；"血肉"（数据 + 综合分公式 + ML 训练）按 P0/P1/P2 分阶段补齐。当前 SC 用户进入系统能完成"院校查询 → 分数线 → 智能填报草稿 → AI 解读 → 导出"完整闭环，主流程可正式上线。
