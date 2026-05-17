# 四川 2026 高考志愿接入计划（草案）

> 起草时间：2026-05-17 14:50  
> 数据来源：四川省教育考试院 2025-06 公告、四川省教育厅"志愿填报 100 问"（2025-06-23）、四川 2026 实施规定（2026-04-29）  
> 草案口径：先列规则现状 / 现有 GZLY 支持 / 缺口 / 分阶段建议，不直接动代码

## 一、四川 2026 官方批次结构（来源：四川教育考试院）

### 1. 平行志愿批次（11 个，按投档顺序）

| # | 批次完整名 | 志愿单位 | 数量 | 备注 |
|---|---|---|---|---|
| 1 | 普通类本科提前批次 A 段前 - **国家专项计划** | 院校专业组 | **6** | 2026 由 2 增加到 6 |
| 2 | 普通类本科提前批次 B 段 | 院校专业组 | **30** | 公费师范生等 |
| 3 | 本科批次 A 段 - **国家专项** | 院校专业组 | **20** | |
| 4 | 本科批次 A 段 - **地方专项** | 院校专业组 | **20** | |
| 5 | **本科批次 B 段** | 院校专业组 | **45** | 主流程 |
| 6 | 本科批次 B 段后 - **区域教育均衡发展专项计划** | 院校专业组 | **20** | |
| 7 | 本科批次 B 段后 - **省属高校少数民族预科** | 院校专业组 | **20** | |
| 8 | 高职（专科）批次 | 院校专业组 | （TBD，需 6 月细则）| |
| 9 | **艺术类本科批次** | 院校专业组 | **45** | 综合分平行志愿 |
| 10 | **艺术类高职（专科）批次** | 院校专业组 | **45** | |
| 11 | **体育类本科批次** + 体育类高职（专科）批次 | 院校专业组 | 各 **45** | |

每个院校专业组志愿内均设置 **6 个专业志愿** + 是否服从专业调剂选项。

### 2. 顺序志愿批次（5 个）

| # | 批次完整名 | 志愿单位 | 数量 | 备注 |
|---|---|---|---|---|
| 1 | 普通类本科提前批次 A 段 | 院校 | **1+2**（第一+平行第二）| 军事 / 公安 / 空军等 |
| 2 | 本科批次 A 段后 B 段前 - **高校专项计划** | 院校专业组 | **20**（2026 升级，原顺序志愿改平行）| 注意：2026 高校专项有官方说法不一致，一处说"20 平行"，另一处仍按 1 顺序志愿。需要 6 月底实施细则确认 |
| 3 | 本科批次 A 段后 B 段前 - **高水平运动队** | 院校 | **1** 顺序 | |
| 4 | 高职（专科）提前批次 | 院校 | **1+2** | |
| 5 | 艺术类本科提前批次 | 院校 | （TBD，需统考成绩门槛）| |

### 3. 同分排序规则（关键算法点）

普通类：高考文化成绩（含照顾加分）→ 语数两科和 → 语数单科最高 → 外语单科 → 首选科目单科 → 再选科目单科最高 → 再选科目单科次高。

艺术类综合成绩公式（教育考试院 2026-05 公告标准）：
- 美术与设计 / 戏剧影视编导 / 戏剧影视表演 / 戏剧影视导演 / 服装表演 / 播音与主持类：
  `综合 = 高考文化成绩 × 50% + 省级专业统考成绩 × (750/300) × 50%`
- 音乐表演 / 音乐教育 / 舞蹈 / 书法 / 航空服务艺术类：
  `综合 = 高考文化成绩 × 30% + 省级专业统考成绩 × (750/300) × 70%`

体育类：
- 平行志愿：按体育专业统考成绩排序（文化、专业双达线后）。
- 综合分排序：`综合 = 高考文化成绩 × 30% + 体育统考成绩 × (750/100) × 70%`（实际比例以 2026 实施规定为准）

### 4. 一分一段表

按 **首选物理类 / 首选历史类** 分别公布。艺术 / 体育按综合分另出榜。

## 二、GZLY 现有四川支持现状

### 1. 已实现部分

| 项 | 状态 | 备注 |
|---|---|---|
| `ProvincePolicyService.SC` | ✅ | `UNIT_PROFESSIONAL_GROUP_45` / "普通本科批B段" / 45 |
| `ProfessionalGroupVolunteerService` | ✅ 814 行 | 主算法已写：45 院校专业组 / 8+19+13+5 梯度 / 6 专业志愿 |
| 数据表 `data_admission_group_line` | ✅ | 包含 `province_code / year / school_id / group_code / group_name / subject_type / first_subject_requirement / resubject_requirement / min_score / min_rank / batch ...` |
| 数据表 `data_admission_group_plan` | ✅ | 字段 `province_code / year / school_id / group_code / major_code / major_name / subject_type / plan_count / tuition / study_years / batch ...` |
| `data_score_rank` 多省一分一段 | ✅ | 物理 541 + 历史 514 行 / 2025 SC |
| `SichuanDataAdminController/Service` | ✅ | 后台数据导入与质检 |
| 前端 `provinces.ts` SC 区域入口 | ✅ | RegionHome 已有 SC 卡片 |
| 前端 `VolunteerForm` SC 模式分支 | ✅（部分）| 走 ProfessionalGroupVolunteerService |
| 前端 `VolunteerResult` 院校专业组渲染 | ✅（部分）| 已有 `isProfessionalGroupPlan` 渲染分支 |

### 2. 数据现状（2026-05-17 实测）

| 表 | SC 2025 数据 | 完整度 |
|---|---|---|
| `data_admission_group_line` 普通本科批B段 | 物理 18 组 + 历史 9 组 = 27 组 | 仅占 45/45 主批次约 30%，缺 ~18 组 |
| `data_admission_group_plan` 普通本科批B段 | 物理 108 + 历史 32 = 140 行 / 21 组 | 缺 ~24 组 |
| `data_score_rank`（一分一段）| 物理 541 + 历史 514 = 1055 行 | ✅ |
| 其他批次（提前批 / 艺术 / 体育 / 专项）| **全 0** | ❌ |

按 v7.35 HANDOVER 口径，SC `generationReady=false`、`lockReason=四川2025官方一分一段、普通本科批B段院校专业组线或招生计划尚未完整核验`。

### 3. 缺口清单

| 维度 | 缺口 | 严重度 |
|---|---|---|
| **批次定义** | 当前 SC 只有 1 个批次（B 段 45 院校专业组）。要做完整 2026 接入需 11 个平行 + 5 个顺序 ≈ 16 个批次定义。 | P0 |
| **批次 engine 路由** | `ProfessionalGroupVolunteerService.generate()` 内 `TARGET_TOTAL=45` 是常量，没按 batchCode 动态切换为 6 / 20 / 30 / 45。 | P0 |
| **policy_rule_config** | SC 在 `policy_rule_config` 一行都没有，需要 16 行 / 年 × 至少 2024+2025+2026 共 ~48 行。 | P0 |
| **B 段数据** | 27/45 组线 + 21/45 组计划，缺 ~18 组线 + ~24 组计划。 | P0 |
| **提前批 + 专项 + 艺术 + 体育数据** | 全 0。SC 2025 这些批次未导入。 | P1 |
| **2026 高校专项升级**（顺序→平行）| 官方说法不一致，需 6 月细则定。 | P2 等官方 |
| **艺术 / 体育综合分公式** | 当前 GZLY 主算法走"位次"，艺术体育要走"综合分排序"，不能复用主算法。需要 `ArtCompositeSichuanEngine` / `SportsCompositeSichuanEngine` 单独实现。 | P1 |
| **前端 SC 批次入口** | 当前 RegionHome / VolunteerForm 都默认走 "普通本科批B段"，没有批次选择器；2026 接入后需要类似贵州的 batchSupport 矩阵。 | P0 |
| **专项资格审核** | 国家专项 / 地方专项 / 区域均衡 / 少数民族预科都需要"户籍/学籍/资格"过滤，当前未实现。 | P1 |
| **官方链接 + 招生章程** | 已有 `uni_official_link` 共 2198 所全国院校链接（与贵州共享），无需单独补。 | OK |
| **选科要求** | 当前 GZLY 选科要求表 `data_major_requirement_gz` 只覆盖贵州，需要扩展 `data_major_requirement_sc`（或拆 `data_major_requirement` 多省视图）。 | P1 |

## 三、四川接入分阶段建议（**强烈建议走 MVP**）

### 阶段 0：先把"普通本科批 B 段 45 院校专业组"做完整（4 月～6 月公测期）

最小可发布 MVP，专注 1 个主批次：

1. **数据补齐**：把 SC 2025 B 段组线 / 组计划缺口（18+24 组）通过 `SichuanDataAdminController` 后台补完，达成 generationReady=true。
2. **`policy_rule_config` 落库**：SC + NORMAL_UNDERGRADUATE_B / 普通类 / 2025+2026 共 2 行；2026 行 status=pending_confirm。
3. **前端**：SC 区域工作台 + 智能填报 + 结果页 + AI 解读全部走 `ProfessionalGroupVolunteerService.generate()` 已有路径。
4. **审计**：把审查报告 `docs/2026_sc_pre_launch_audit.md` 出一份，列出 2025 数据现状与 2026 数据缺口。

**预计工程量**：仅数据补齐 + 1 行 policy + 0 行新代码（如果 B 段算法当前能跑）。预计 2-3 天 + 数据采集若干小时。

### 阶段 1：把艺术 / 体育 / 高职专科批接进来（6 月 2026 数据出之后）

按 4 个批次扩展 ProfessionalGroupVolunteerService：

1. **新增 `SichuanArtCompositeEngine` / `SichuanSportsCompositeEngine`**：实现艺术 / 体育综合分公式 + 按综合分排序的 45 平行算法。
2. **复用 `BatchListingRecommendationService`**：先用 QUERY_ONLY 兜底列表（按 `data_admission_group_line` 历史录取）。
3. **`policy_rule_config` + 数据导入**：SC + ART_UNDERGRADUATE_45 / ART_SPECIALTY_45 / SPORTS_UNDERGRADUATE_45 / SPORTS_SPECIALTY_45 / NORMAL_SPECIALTY 各 1 行 + 数据采集。

**预计工程量**：4 个新 engine 类 × 200 行 + 1 个综合分公式 service + 5 行 policy + 数据采集若干天。预计 5-7 天。

### 阶段 2：把提前批 + 5 类专项 + 顺序志愿接进来（6 月底 / 7 月）

11 平行 + 5 顺序 = 16 批次完整覆盖：

1. **新增 6 个 engine 类**（国家专项 6 / 提前 B 段 30 / A 段国家专项 20 / A 段地方专项 20 / B 段后区域均衡 20 / B 段后少民预科 20）+ 顺序志愿 engine（高水平运动队 1 / 提前 A 段 1+2 / 专科提前 1+2 等）。
2. **`SichuanBatchRuleRegistry`**：模仿贵州的 `BatchRuleRegistry`，硬编码 16 批次规则 + 关键词。
3. **`SichuanBatchSupportService`**：模仿贵州的 `BatchSupportService`，按 SC 数据表 + readiness 算 supportLevel。
4. **专项资格审核 service**：扩展 `BatchListingRecommendationService.listSpecialProgramBatch` 支持 SC。
5. **前端**：批次选择器 + 4 卡片入口（与贵州 RegionHome 对齐）。

**预计工程量**：16 个新 engine 类 × 150 行 + 1 个 BatchRuleRegistry × 400 行 + 1 个 BatchSupportService × 500 行 + 前端 4 卡片 × 100 行 + 数据采集若干周。预计 2-3 周。

### 阶段 3：跨省抽象（湖北 / 安徽对齐）

把 GZ / SC / HB / AH 4 省的 BatchRuleRegistry + BatchSupportService 抽象为 `ProvinceBatchRegistry<P>` 通用机制，未来加省份只改配置。预计 1-2 周。

## 四、本轮建议立刻动手的事

**只做阶段 0**，因为：

- 阶段 1 / 2 / 3 工程量大、风险高、要等 6 月底 2026 官方数据出来；
- 用户的优先级是"上线"，MVP 满足"主批次 45 平行"已经覆盖四川 ≥80% 用户；
- 其他批次按 QUERY_ONLY 兜底（与贵州非主批策略对齐），不阻塞上线。

**立即可做的代码层 / 数据层动作**（按优先级）：

1. 后台跑 `SichuanDataAdminController` 完成 2025 B 段缺口补齐（27→45 组线、21→45 组计划）。
2. `policy_rule_config` 加 SC + NORMAL_UNDERGRADUATE_B 2025/2026 两行。
3. 前端 `RegionHome` 把 SC 入口的 "数据准备中" 状态切到 "已开放"（前提：generationReady=true）。
4. 前端 `VolunteerForm` 把 SC 的批次选择默认 / 锁定到 "普通本科批 B 段"。
5. 出一份 `docs/2026_sc_pre_launch_audit.md`，与贵州审查报告口径对齐。

## 五、不建议立刻动手的事

**不要做**：

- 不要按四川 2026 规则一次性新建 16 个 engine 类（工程量太大、规则尚未稳定）。
- 不要在没拿到 2026 官方数据的情况下硬填假数据（HANDOVER 已禁止）。
- 不要直接复用贵州的 96 主链路算法到四川（贵州=专业+院校 vs 四川=院校专业组，逻辑完全不同）。
- 不要把 2026 高校专项按一种猜测（顺序 vs 平行）写死，等 6 月底实施细则。

## 六、决策点 - 等用户确认

**A.** 按阶段 0 执行：补 B 段数据 → 落 policy → 前端开放 SC 入口。先把"四川主流程能用"上线。

**B.** 按阶段 0 + 阶段 1 执行：再花 5-7 天加上艺术 / 体育 / 高职专科 4 个批次。

**C.** 全阶段一次性走完：3-4 周完整覆盖 16 批次，等 2026 官方数据出再 retrain。

**D.** 不动 SC，先收口贵州 + 等 6 月底官方数据，再统一上线 SC。
