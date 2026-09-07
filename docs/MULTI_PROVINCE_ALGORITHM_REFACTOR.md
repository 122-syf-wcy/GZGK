# 多省份推荐算法重构方案

> 编写时间：2026-08-12
> 更新记录：2026-08-12 v2 —— 补充各省录取规则联网核查结果、等效位次换算、录取概率模型、各省算法设计（第九至十一部分）
> 更新记录：2026-08-12 v3 —— **阶段 0 + 阶段 1 后端代码已实现**（见第十二部分状态标注），全量测试 210 个通过
> 更新记录：2026-08-12 v4 —— **本地 Docker 环境全链路验证完成**（见第十三部分验证记录）：回测 A/B 达标（log-rank Brier 优 28.6%）、8 省接口验证通过、生成热态 17s→3-4s；顺带修复 Redis List 缓存从未命中的序列化缺陷与选科要求 N+1
> 更新记录：2026-08-12 v5 —— **剩余优化全部落实并复验**：① 部署默认概率模型切 log-rank（yml 层，可 env 回退；Java 类内默认保持 sigmoid 锁单测基线）；② 贵州候选检索科类纪元隔离上线（2021-2023 旧文理不再进候选池，复验 refYears 仅剩新高考纪元且 96 条满额）；③ 专业组历史线/组内专业改 IN 批量装载（45 条约 90 次查询 → 2 次）。热态生成稳定 4.5-5s，225 测试全过
> 更新记录：2026-08-12 v6 —— **全地区优化闭环**：① 回测框架扩展到专业组省份（组级调档线口径 + `provinceCode` 请求参数 + 报告表省份列，全部 8 省拥有校准闭环，湖北组级回测单测锁定数学口径）；② `chance_params_json` 按省参数正式生效（σ 下限/退档先验/模型覆盖），河南保守参数 `{"sigmaMin":0.12}` 首例入种子；③ 海南 3+3 代码层支持（ThreeThreeSubjectMatcher 集合包含匹配 + 标准分 900 校验 + "综合"科类轨道贯通到门禁/一分一段/归一化）；④ 云南资格扩容（专项 +10 / 预科 +10，官方口径）；⑤ 就绪度统计与纪元隔离对齐。本地复验通过，236 测试全过
> 更新记录：2026-08-12 v7 —— **架构审计问题全面修复**：① 统一生成入口 `RecommendationOrchestrator.generateWithPolicy`（归一→门禁→政策→路由→ML→包装），旧 `/volunteer/generate` 与 `/volunteer/recommend` 同流程，旁路消除（本地复验：两接口共享同一结果缓存）；② 政策默认年动态化（当前自然年）+ 未显式传年时最近可用年回退并带口径警示；③ 专业组链路补指纹请求锁 + 结果缓存 120s（orchestrator 层，与贵州同款），移除包住全程的方法级事务；④ 概率定档偏离闭环：重分类条数入 `PlanMetrics.gradientReclassifiedCount` + 梯度说明文案标注；⑤ 冲稳保垫区间预设收敛 `GradientAllocationEngine.ratioPreset/offsetPreset` 单源（原三份人工同步副本改委派）；⑥ 特征/预测链路科类纪元隔离（`getRecentLinesSince`，贵州 2024+）且回测参考窗口同口径对齐——**新基准 hitRate 0.9269 / Brier 0.0167**（旧 0.0155 系用线上已不用的 2022-2023 文理数据算出，不可比）；⑦ 回测互斥防堆积（并发复验第二请求被拒）+ snapshot 增乐观口径警示字段；⑧ ml-service chance 模型弱监督标签泄漏止血：无真实 label 默认拒绝训练（`blocked_weak_label`），研究放行需显式 `--allow-weak-label` 且特征强制走与推理对齐的去泄漏白名单。248 测试全过，本地 Docker 全链路复验通过
> 更新记录：2026-09-07 v8 —— **阶段 0-4 全部完成并已入库 main**（commit 612b10e，v7 全部成果落盘），实测 260 用例全绿；阶段 5（扩省）转为数据运营线推进
> 状态：阶段 0-4 已完成并已入库 main（2026-09-07）；阶段 5（扩省）转为数据运营线
> 范围声明：**本方案只覆盖后端**（`gzly-server` 推荐链路与数据模型）。前端改动仅列出后端需要提供的接口契约，由前端另行实施。
> 部署顺序要求：**必须先在数据库执行 `db/20260812_province_profile_policy.sql`，再部署新版 JAR**——实体已包含新列，旧库结构跑新代码会导致政策查询报错。

---

## 〇、结论摘要

一句话：**当前系统对外宣称支持 8 个地区，实际只有贵州一个地区跑得通完整算法；其余地区要么后端不认，要么走的是一条没有概率模型的简化链路。**

问题不在算法参数调得好不好，而在于"多地区"只做了外壳。因此本次不是调参，是重构。

三条主要结论：

1. **算法只有一套，且绑死在贵州。** 硬规则过滤、位次特征、机会指数、梯度分配、排序打分、概率校准、方案诊断这七层，只在 `VolunteerService`（贵州）里跑。院校专业组省份走 `ProfessionalGroupVolunteerService`，一层都没有。
2. **志愿单位不是省的属性，是「省 × 批次」的属性。** 这是本次核查最重要的发现，直接否定了"按省写一套算法"的思路。河南本科提前批是"专业+院校"64 个，本科批却是院校专业组 48 个；贵州本科提前批 A/B 段是院校顺序志愿，本科批是专业平行志愿 96 个。同一个省内部就有两三种志愿单位。
3. **前端的省份参数与官方政策大面积不符。** 广西实际 40 个（前端写 45）、云南实际 40 个（前端写 45）、河南实际 48 个（前端写 45）。组内专业数广西是 20 个、云南是 10 个，代码里按 6 个处理。

重构的目标形态：**一条统一管线 + 按「省 × 批次」配置驱动的可插拔点**，而不是 8 套 service。

---

## 一、现状核对结论

以下每一条都在代码里核对过，标注了文件与行号。

### 1.1 前端开 8 个省，后端只认 4 个

`gzly-web/src/constants/provinces.ts:1` 定义了 8 个省份码，且 `PROVINCE_CONFIGS` 里 8 个省 `status` 全部是 `'open'`、全部带"进入 AI 志愿"按钮。`Home.vue:178` 直接全量渲染成可点击卡片。

后端 `ProvincePolicyService.java:21-30` 的 `POLICIES` 只有 `GZ / SC / HB / AH` 四项，`normalizeProvinceCode` 在 `:50-52` 对未知省份直接抛 `BizException("暂不支持该省份志愿生成")`。

**后果**：广西、海南、云南、河南四个专区，用户填完表提交后，后端第一步就报错。

### 1.2 每个地区首页都有一个必然 404 的请求

`gzly-web/src/api/volunteer.ts:196-217` 按省调用 `/volunteer/{gz|sc|ah|hb|gx|hi|yn|ha}/batch-support`。

在整个 `gzly-server` 中全文检索 `batch-support`：**零命中**。`VolunteerController` 实际只有 `/generate`、`/provinces`、`/plan`、`/history`、`/rank-check`、`/metrics`、`/ai-analysis` 等映射。

**后果**：`RegionHome.vue:163` 的 catch 分支每次都触发，页面常驻显示"数据状态暂时读取失败"；`VolunteerForm.vue` 顶部的批次支持面板永远是兜底文案。

### 1.3 院校专业组链路没有任何概率模型

`ProfessionalGroupVolunteerService` 与 `VolunteerService` 是两套完全独立的实现，前者**没有引用任何一个算法引擎**——没有 `FallbackRulePredictionEngine`、`FeatureBuildEngine`、`VolunteerSortEngine`、`CandidateFilterEngine`、`VolunteerDiagnosisEngine`。

它给每条推荐写死了这些值（`ProfessionalGroupVolunteerService.java:269-274`）：

```java
item.setRecommendationScore(Math.min(100, item.getDataConfidenceScore() + item.getPrecisionScore() / 5));
item.setProbLevel("参考匹配");
item.setAdmissionProb(0);
item.setRiskLevel("需复核");
item.setRiskColor("yellow");
item.setTrend("需结合后续年份复核");
```

全文检索该文件，`chanceScore` **一次都没有被赋值**，因此机会指数恒为 0。

`recommendationScore` 的构成是"数据可信度 + 精确度/5"，衡量的是**这条数据干不干净，不是这个学校适不适合考生**。用户反馈"推荐的院校没有依据"，在字面意义上成立。

排序只有一条规则：按历史调档位次升序（`:223`），去重后截断。

### 1.4 用户填的策略和意向被丢弃

梯度区间在 `ProfessionalGroupVolunteerService.java:390-393` 是四个写死的比例（0.65/0.95/1.20/1.80/3.00），`strategyMode` 只在 `:130` 被存进历史记录，从不进入任何计算。保守型和冲刺型产出完全相同的结果。

`preferredMajors` / `preferredRegions` 同样只在 `:143-144` 被序列化存库，没有任何过滤或加权逻辑读取它们。

### 1.5 规则兜底对非贵州省份失效

`MlPredictionService.java:63-68`：

```java
if ("off".equals(effectiveMode)) {
    // ML 关闭：已由 VolunteerService 主链路用 FallbackRulePredictionEngine 赋值，这里不重复
    result.setFallbackReason("ml_disabled");
    persistPlanItems(plan);
    return result;
}
```

注释里的假设"主链路已赋值"只对贵州成立。`application.yml:94` 默认 `GZLY_ML_ENABLED:false`，即线上默认就是 `off`。后面 `applyRuleFallback`（`:190`）本来专门处理 `chanceScore == 0` 的条目，因为提前 return 永远走不到。

### 1.6 政策配置只有贵州的种子数据

`policy_rule_config` 的两处 INSERT（`db/20260430_volunteer_closed_loop.sql:329-340`、`db/20260505_gz_policy_admin_ml_gap.sql:26-37`）**全部是 GZ 行**，没有 SC / HB / AH。

而 `VolunteerRecommendController.java:39-40` 每次请求都会 `requirePolicy`，`PolicyRuleService.java:43-45` 查不到就抛"当前年份政策未配置"。

**待确认**：生产库是否有人工插入过 SC/HB/AH 行。若没有，这三个省在进入生成逻辑之前就已经失败。

另有一处 bug：`VolunteerRecommendController.java:128` 对所有省份都写死 `req.setPolicyVolunteerUnitType(UNIT_MAJOR_96)`，专业组省份也被打上 MAJOR_96 标签。

### 1.7 非贵州省份的数据量达不到生成门槛

`ProfessionalGroupVolunteerService.java:96-101` 规定凑不满 45 个就抛异常、不补假数据（这个原则本身是对的）。本地已复核数据的实际量：

| 省份 | 一分一段 | 专业组调档线 | 专业组计划 | 能否生成 |
|---|---|---|---|---|
| 四川 | 1055 行 | 27 行（物理 18 组 / 历史 9 组） | 140 行 | 否，离 45 组差一半以上 |
| 湖北 | 0 | 0 | 0 | 否，卡在一分一段检查 |
| 安徽 | 0 | 0 | 0 | 否，卡在一分一段检查 |

来源：`scripts/server/province_group_2025/reports/{SC,HB,AH}/reviewed_validation_report.md`，报告自身也标注"未达到解锁门槛 45 组"。

湖北、安徽会更早地在 `ProfessionalGroupVolunteerService.java:75-78` 的 `rankYear == null` 检查处失败。

### 1.8 贵州链路自身的准确率问题

**新旧高考数据混用（最严重）**。贵州 2024 年才开始 3+1+2，之前是文科/理科。候选查询把两者用 OR 合并，且只要求 `year >= 2021`：

```java
// ScoreLineService.java:317-331
String legacyType = "物理类".equals(subjectType) ? "理科" : "文科";
wrapper.and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType)
                  .or().eq(MajorScoreGz::getSubjectType, legacyType))
       .between(MajorScoreGz::getMinRank, rankLow, rankHigh)
       .ge(MajorScoreGz::getYear, 2021)
```

2021 年理科第 5 万名与 2025 年物理类第 5 万名，考生总数、赋分方式、招生计划结构均不同，位次不可直接比较，代码中没有任何换算。系统只在 `VolunteerService.java:2280` 的 `resolveConfidence` 把它标为"中可信/需复核"，候选照样进列表、照样参与排序和机会指数计算。

**排序分里存在大量关键词猜测**（`VolunteerSortEngine.java`）：

- 城市匹配靠 `matchTag` 里是否包含"地区"两字（`:114`）
- 就业分靠专业名是否含"计算机/软件/医学/师范"等词，命中 80 否则 40（`:117-119`）
- 学费惩罚靠校名或专业名是否含"中外""民办"（`:127-130`）

`CandidateFilterEngine` 的 `maxTuition` 硬规则实际失效，因为 `VolunteerService.java:1497` 和 `:1517` 都写死 `p.setTuition(null)`。

**一批引擎是死代码或半接通**：

- `GradientAllocationEngine` 定义了完整的 96 志愿分配比例，但全项目除自身外零引用；`VolunteerService` 用的是内部的 `targetCounts()`，同一套梯度口径存在两份实现。
- `MlPredictionEngine`、`PolicyRuleEngine`、`ComplianceGuardEngine`、`SkillsRagEngine`、`ExportEngine`、`AiDeepAnalysisEngine` 六个类都是 500 字节左右的空壳，零引用。
- `ProbabilityCalibration` 只在 `VolunteerService.java:3440-3466` 的 `assessPortfolioSafety` 里给整表安全度用了，**前端展示给用户的单条机会指数是未校准的原始值**。

### 1.9 性能问题的根因

**候选查询无 LIMIT**。`ScoreLineService.findMajorCandidates` 按位次区间全量捞进内存再在 Java 里过滤。一个 6 万名的考生，"垫"档区间是 10.8 万到 18 万名，在 16 万行的 `data_major_score_gz` 上可能一次返回上万行，每次生成要跑 4 次（四个梯度）。

**N+1**。`VolunteerService.enrichWithAlgorithms`（`:3763`）对 96 条逐条调用 `calcProbability` + `assessRisk` + `predictScore` + `applyRulePrediction`，其中 `applyRulePrediction` 调用的 `AlgorithmService.getRecentLines` **没有 `@Cacheable`**，96 次查询一次不省。

**缓存键设计导致缓存失效**。`AlgorithmService.java:66-67` 的 `calcProbability` 键里含 `studentRank`，`ScoreLineService.java:314-316` 的 `findMajorCandidates` 键里含 `rankLow/rankHigh`。每个考生位次不同，几乎全是 miss；每次 miss 又把上万行候选 JSON 序列化写进 Redis（`RedisConfig.java:78`，TTL 20 分钟），既慢又占内存。

---

## 二、8 省官方政策核查表（2026 年口径）

以下为 2026 年各省官方招生实施规定/考试院问答的核查结果。**这是本次重构的事实基准**。

### 2.1 普通类本科批主表

| 省 | 选科模式 | 新高考首年 | 志愿单位 | 本科批志愿数 | 组内专业数 | 服从调剂 | 分数制 |
|---|---|---|---|---|---|---|---|
| 贵州 GZ | 3+1+2 | 2024 | 专业（类）+ 院校 | **96** | — | **无** | 原始分 750 |
| 四川 SC | 3+1+2 | 2025 | 院校专业组 | **45**（本科批B段） | 6 | 有 | 原始分 750 |
| 湖北 HB | 3+1+2 | 2021 | 院校专业组 | **45**（本科普通批） | 6 | 有 | 原始分 750 |
| 安徽 AH | 3+1+2 | 2024 | 院校专业组 | **45**（普通本科批） | 6 | 有 | 原始分 750 |
| 广西 GX | 3+1+2 | 2024 | 院校专业组 | **40**（本科普通批） | **20** | 有 | 原始分 750 |
| 海南 HI | **3+3** | 2020 | 院校专业组 | **30**（本科普通批） | 6 | 有 | **标准分 900** |
| 云南 YN | 3+1+2 | 2025 | 院校专业组 | **40**（本科批） | **10** | 有 | 原始分 750 |
| 河南 HA | 3+1+2 | 2025 | 院校专业组 | **48**（普通本科批） | 6 | 有 | 原始分 750 |

### 2.2 同省内部的批次差异（关键）

这一节说明了为什么"按省选算法"是错的。

**贵州**：本科批与高职专科批均为 96 个「专业（类）+ 院校」平行志愿，**不设服从调剂**。但本科提前批 A、B 段与高职专科提前批是**院校顺序志愿**——「1 个院校 + 6 个专业」为 1 个志愿，**设服从调剂**。本科提前批 C 段、艺术类 B 段、体育类本科为 60 个平行志愿。

**河南**：普通本科批与普通高职专科批是**院校专业组** 48 个。但普通本科提前批、普通高职专科提前批是**「专业+院校」64 个**，不设服从调剂；艺术本科批、体育本科批同样是「专业+院校」64 个。**同一个省两种志愿单位并存。**

**云南**：本科批 40 个组，但本科批 A 段只有 1 个组；提前本科批 B/D 段各 10 个组，C 段 1 个组；高本贯通批 20 个组且每组只有 1 个专业志愿。此外符合专项计划条件可增加 10 个、符合少数民族预科条件再增加 10 个，**同一批次内不同资格的考生志愿容量不同**。

**四川**：本科批 B 段 45 个；本科批 A 段（国家专项、地方专项）各 20 个；本科提前批 B 段 30 个；本科提前批 A 段为顺序志愿（1 个第一志愿 + 2 个平行第二志愿）；专科批 45 个。

**湖北**：本科普通批 45 个，高职高专普通批 20 个。专项计划、预科班等单独设置院校专业组，**混合填报在本科普通批志愿栏内**。

**广西**：本科普通批与高职高专普通批均 40 个，其他采用平行志愿的批次一律 20 个。

**安徽**：普通本科批与普通高职专科批均 45 个；本科提前批的军事、公费师范等 20 个；专项计划 20 个。

**海南**：本科普通批 30 个；本科艺术类统考、体育类、国家专项、高校专项、地方专项各 10 个；本科提前普通类 6 个；预科班 6 个；艺术校考、特殊类型各 1 个；高职专科批 10 个。

### 2.3 官方给出的冲稳保建议比例

这一节非常有价值——多个省的考试院官方问答直接给出了梯度建议，可以作为默认预设，比自行拍脑袋可靠。

| 省 | 来源性质 | 官方/权威建议比例 |
|---|---|---|
| 湖北 | 省招办《阳光招生政策暨志愿填报问答》 | 45 个中「冲 15 左右 + 稳 15 左右 + 保 15 左右」 |
| 海南 | 省考试局《政策及志愿填报问答》第 63 条 | 30 个中「冲 10 + 稳 10 + 保 10」 |
| 云南 | 省招生考试院平行志愿填报指南 | 冲 20% + 稳 40% + 保 40%（40 个即 8/16/16） |
| 广西 | 第三方权威解读（非考试院原文） | 冲 8-10 + 稳 20-22 + 保 8-10 |
| 贵州 | 第三方解读（非考试院原文） | 96 个中冲刺型 34/29/33 |
| 四川 / 安徽 / 河南 | 未见官方比例 | 需自行设定并回测校准 |

**注意**：官方比例只有"冲稳保"三档，没有"垫"档。现有系统的四档模型（冲/稳/保/垫）需要说明"垫"是"保"的下沿细分，或者考虑与官方口径对齐为三档。这是一个待决策项。

### 2.4 同分排序规则（各省不同）

平行志愿投档在总分相同时的排序规则各省有差异，直接影响位次口径：

- **广西**：语文+数学总成绩 → 语文或数学单科最高 → 外语 → 首选科目单科 → 再选科目单科最高 → 再选科目单科次高
- **河南**：语数两科之和 → 语文或数学单科最高 → 外语单科 → 首选科目单科 → 再选科目单科最高 → 再选科目单科次高
- **贵州 / 四川 / 湖北 / 安徽 / 云南 / 海南**：需逐省补充核查

系统目前用「一分一段表的 `rank_high`（同分保守位次）」作为统一口径，在同分密度大的省份（河南）会产生较大偏差。

### 2.5 核查来源

- 四川：`四川省2026年普通高校招生实施规定`（阳光高考平台转发）
- 湖北：`2026年湖北省普通高校阳光招生政策暨志愿填报问答（填报志愿须知篇）`（省招办）
- 安徽：`安徽省2026年普通高校招生工作实施办法`
- 贵州：`贵州省2026年高考志愿填报指南`、`贵州2026年高考工作新闻发布会实录`
- 广西：`广西：2026年普通高校招生政策百问百答（一）`、`广西2026年普通高校招生考试和录取工作方案`
- 海南：`2026年海南省普通高校招生本科批招生院校填报志愿有关问题的公告`、`2026年海南省普通高校招生政策及志愿填报问答`
- 云南：`云南省2026年普通高校招生网上填报志愿考生须知`、`云南省2026年高考志愿填报百问百答`
- 河南：`河南省2026高考志愿填报时间及填报要求`、`河南省2025年普通高考投档录取规则`

> 落地前必须由人工到各省考试院官网复核原文并留存链接，本表仅作设计输入，不得直接作为生产数据来源。

---

## 三、政策与现状的差异清单

| 项 | 官方口径 | 系统现状 | 位置 |
|---|---|---|---|
| 广西本科批志愿数 | 40 | 45 | `provinces.ts:198` |
| 云南本科批志愿数 | 40 | 45 | `provinces.ts:270` |
| 河南本科批志愿数 | 48 | 45 | `provinces.ts:306` |
| 广西组内专业数 | 20 | 未建模（按 6 处理） | `ProfessionalGroupVolunteerService:343` |
| 云南组内专业数 | 10 | 未建模（按 6 处理） | 同上 |
| 海南选科模式 | 3+3 | 后端只有 3+1+2 | `ProvincePolicyService:22-29` |
| 海南分数制 | 标准分 900 | 未建模 | 全局 |
| 河南本科提前批志愿单位 | 专业+院校 64 | 未建模 | 全局 |
| 贵州提前批 A/B 段 | 院校顺序志愿 + 服从调剂 | 未建模，且全局禁用服从调剂 | `HANDOVER.md` 业务规则表 |
| 服从调剂 | 除贵州平行志愿批次外，各省均有 | 全局禁止出现该选项 | 合规规则 |

**关于服从调剂**：`HANDOVER.md` 的业务规则表写着"禁止项：不得出现『是否服从专业调剂』选项"。这条规则是为贵州「专业（类）+ 院校」模式写的，在该模式下确实不设该选项。但对院校专业组省份，服从调剂是真实存在且极其重要的决策项——广西官方明确提示"99% 的考生应勾选服从调剂，1:1 投档下不服从等于拿前途赌运气"。**把贵州的合规规则当成全局规则，是另一处"牛头不对马嘴"，必须按省 × 批次区分。**

---

## 四、目标架构

### 4.1 设计原则

1. **不按省复制服务。** 8 套 service 意味着 8 倍的 bug 面和 8 份需要同步的算法。
2. **配置驱动，唯一事实源在数据库。** 当前省份参数散在 Java Map、DB 种子、前端 TS 常量三处且互相矛盾，只要还是三份就一定会再次漂移。
3. **变化点收敛到三处：候选来源、选科匹配、参数。** 其余七层算法全省共用。
4. **绞杀者模式，不重写 `VolunteerService`。** 该文件 203KB、约 4000 行，直接重写必然出事。做法是在外面套接口、保留其作为一个实现、用回测确认无回归后再逐步搬空。

### 4.2 分层结构

```
VolunteerRecommendController
  └── RecommendationOrchestrator            ← 唯一编排入口，替代现有两个 generate()
        │
        ├── PolicyResolver                  ← 解析「省 × 年 × 批次 × 考生类别」得到 BatchPolicy
        │
        ├── CandidateProvider               ← 变化点 1：按 volunteerUnitType 选实现
        │     ├── MajorPlusSchoolProvider       贵州本科批、河南提前批：专业级 + 院校级回退
        │     ├── ProfessionalGroupProvider     大部分省本科批：院校专业组
        │     └── SchoolSequentialProvider      贵州提前批 A/B 段：院校顺序志愿（后置实现）
        │
        ├── SubjectMatcher                  ← 变化点 2：按 subjectMode 选实现
        │     ├── ThreeOneTwoMatcher            首选科目分轨 + 再选科目包含
        │     └── ThreeThreeMatcher             海南：考生选科集合 ⊇ 专业要求集合
        │
        ├── RankResolver                    ← 已有 ProvinceRankService 基本可用，需补标准分
        │
        └── 共享算法层（所有省份必过，参数来自 BatchPolicy）   ← 变化点 3 仅为参数
              CandidateFilterEngine     硬规则过滤
              FeatureBuildEngine        位次特征
              FallbackRulePredictionEngine  机会指数
              GradientAllocationEngine  梯度分配
              VolunteerSortEngine       排序打分
              ProbabilityCalibration    概率校准
              VolunteerDiagnosisEngine  方案诊断
```

### 4.3 CandidateProvider 契约

```java
public interface CandidateProvider {

    /** 支持的志愿单位类型，用于路由。 */
    VolunteerUnitType supports();

    /**
     * 按位次区间取候选。
     * 必须支持 limit，禁止全量捞取。
     * 必须按 BatchPolicy.dataYearFrom 过滤，禁止混入新高考改革前的数据。
     */
    List<VolunteerItem> fetch(CandidateQuery query);

    /**
     * 批量取历史位次序列。
     * 必须一次查完所有候选，禁止逐条查库（现状是 96 次查询）。
     */
    Map<String, List<RankPoint>> loadHistories(List<VolunteerItem> items);
}
```

`CandidateQuery` 至少包含：`provinceCode`、`year`、`batchCode`、`subjectType`、`rankLow`、`rankHigh`、`limit`、`dataYearFrom`、`selectedSubjects`。

好消息是中间模型已经事实上统一了——`ProfessionalGroupVolunteerService` 现在填的就是 `VolunteerService.VolunteerItem`。这不是从零设计，是把已有的隐式约定显式化。

### 4.4 SubjectMatcher 契约

```java
public interface SubjectMatcher {
    SubjectMode supports();

    /** 考生选科是否满足该候选的选科要求。 */
    boolean matches(List<String> candidateSubjects, String requirement);

    /** 把首选科目映射为该省的科类标签；3+3 省份返回统一标签。 */
    String resolveSubjectType(String firstSubject, List<String> selectedSubjects);
}
```

3+1+2 实现即现有 `matchResubject` 逻辑的迁移。3+3 实现（海南）不分物理/历史轨道，判定为「考生选科集合 ⊇ 专业要求集合」。

---

## 五、数据模型

### 5.1 扩展 `policy_rule_config`（批次级）

现有表的唯一键 `(province, year, candidate_type, batch_code)` 粒度正确，无需新建，补字段即可：

| 新增字段 | 类型 | 说明 |
|---|---|---|
| `volunteer_unit_type` | VARCHAR(40) | `MAJOR_PLUS_SCHOOL` / `PROFESSIONAL_GROUP` / `SCHOOL_SEQUENTIAL` |
| `major_per_group_count` | INT | 组内专业志愿数（广西 20、云南 10、多数省 6、贵州本科批不适用） |
| `gradient_preset_json` | JSON | 冲稳保垫的目标数量与位次区间比例 |
| `chance_params_json` | JSON | 覆盖 `FallbackPredictionProperties` 的按批次参数 |
| `data_year_from` | SMALLINT | 该批次可用数据的最早年份（= 新高考首年） |

现有字段 `has_adjustment` 正好用来表达服从调剂，此前未被使用。

### 5.2 新建 `province_profile`（省级）

放真正属于省级、不随批次变化的属性：

| 字段 | 说明 |
|---|---|
| `province_code` / `province_name` | 主键 |
| `subject_mode` | `3+1+2` / `3+3` |
| `new_gaokao_first_year` | 决定哪些年份数据可进主排序 |
| `score_system` | `RAW_750` / `STANDARD_900`（海南） |
| `rank_tie_break_rule` | 同分排序规则说明文本 |
| `official_source_name` / `official_source_url` | 官方来源 |
| `enabled` | 是否对外开放 |

**`data_readiness` 不入表**，由 `ProvinceReadinessService` 实时计算，避免人工维护漂移。

### 5.3 前端改为后端驱动

`provinces.ts` 中的宣传文案（`heroTitle`、`heroDescription` 等）可以保留在前端。但以下字段必须改为从后端拉取：

- `status`（能否进入 AI 志愿）
- `targetCount`（志愿数）
- `volunteerUnitType`
- `subjectMode`
- `scorelineStatus`

否则改完还会再漂移一次。

---

## 六、数据就绪度门禁

新增 `ProvinceReadinessService`，在任何生成请求之前统一判定，返回四态：

| 状态 | 判定条件 | 前端表现 |
|---|---|---|
| `LOCKED` | 该省一分一段表行数为 0 | 灰掉入口，说明缺什么数据 |
| `QUERY_ONLY` | 有一分一段，但候选数据不足以凑满该批次志愿数 | 只开放院校查询与分数线，禁用生成 |
| `ESTIMATE` | 候选充足，但只有历史数据、无当年官方数据 | 可生成，全程标注"历史估算" |
| `FULL` | 当年官方数据齐备且回测达标 | 正常生成 |

判定必须基于**真实行数查询**（count `data_score_rank` / `data_admission_group_line` / `data_major_score_gz`），不依赖人工维护的枚举。这样湖北安徽 0 行会自动 `LOCKED`，四川 27 组自动停在 `QUERY_ONLY`，不会再出现"点进去才报错"。

**这个 service 同时就是缺失的 `/volunteer/{code}/batch-support` 接口的实现。**

---

## 七、准确率修复项

> 本部分是问题项级别的修复清单；其中位次口径、概率模型、按省参数三项在第九、十、十一部分展开为完整设计，以后者为准。

### 7.1 科类纪元隔离（最高优先级）

引入 `province_profile.new_gaokao_first_year`，低于该年份的记录：

- 不参与位次区间筛选
- 不参与机会指数计算
- 仅作为"历史参考"在详情中展示，并明确标注"改革前文理科，位次不可直接比较"

对贵州（首年 2024），候选池将从 5 年缩到 2 年。

**已决策**：采用"宁缺毋滥"——凑不满 96 条就给不满，并在结果页说明原因。理由是现在凑满 96 是靠混入不可比的旧数据，那 96 条里的可信度是虚的。

对云南、河南、四川（首年 2025），只有 1 年数据可用，必须强制标记 `ESTIMATE` 并采用更保守的梯度。

### 7.2 修复伪匹配维度

- **学费**：`toCandidatePlan` 不再写死 `tuition=null`，从 `admission_plan` / `data_admission_group_plan` 的 `tuition` 字段取真实值；取不到时明确标注"学费待核验"而不是按 0 处理。
- **城市**：不再靠 `matchTag` 是否含"地区"两字，改为用 `University.city` 与用户 `preferredRegions` 做真实匹配。
- **就业**：现有关键词表（计算机/软件/医学/师范…）不构成依据。短期方案是把该维度权重降为 0 并从解释文案中移除；中期接入真实就业数据源后再启用。

### 7.3 概率校准接到展示层

`ProbabilityCalibration` 目前只用于整表安全度。应把单条展示的机会指数也过一遍校准，使"62 分"大致对应"62% 的历史达线频率"。校准断点按省由回测产出，而非全省共用一套。

### 7.4 服从调剂建模

按 `policy_rule_config.has_adjustment` 决定是否展示该维度。对院校专业组省份，需要在风险说明中体现"不服从调剂的退档风险"，这是这些省份最重要的风险项之一。

---

## 八、性能修复项

| 项 | 现状 | 目标 |
|---|---|---|
| 候选查询 | 无 LIMIT，可能返回上万行 | 按梯度目标数的固定倍数加 LIMIT，DB 侧排序 |
| 历史位次 | 96 次单条查询，无缓存 | 1 次批量 `IN` 查询 |
| `calcProbability` 缓存 | 键含 `studentRank`，几乎全 miss | 改为缓存「学校+专业」的位次统计量，概率在内存中计算 |
| 候选列表缓存 | 键含精确位次区间，大对象写 Redis | 键改为按位次分桶（如每 1000 名一桶），或直接取消该层缓存 |
| 索引 | 需复核 | 确认 `(subject_type, year, min_rank)` 复合索引与查询谓词的最左前缀匹配 |

---

## 九、跨年位次口径：等效位次换算（通用组件，缺失能力）

### 9.1 现状缺陷

当前系统把不同年份的原始位次当成同一把尺子直接比较和求均值（`FeatureBuildEngine.buildRankFeatures` 直接对 `minRanks` 求均值/中位数，`BacktestService.loadReference` 同样）。这是错的：每年考生人数不同，同一位次在不同年份代表的竞争力不同。这是推荐"不精准"的第一个通用根因。

### 9.2 标准做法（业界与各省考试院口径一致）

河南省教育考试院在 2025 首年新高考时官方推荐的就是"同位分"法；业界通用的等效位次公式：

```
等效位次 E(r, y→t) = round( r / N(y) × N(t) )

r     ：y 年的位次
N(y)  ：该省该科类 y 年参与排名的考生总数
N(t)  ：目标年 t 的考生总数
```

`N(y)` 直接取该省该科类当年一分一段表的**最大累计人数**，不需要新数据源——`data_score_rank` / `data_score_rank_gz` 里已经有。

### 9.3 落地设计

新增 `RankNormalizationService`（后端通用组件）：

1. 启动时/按需从一分一段表计算并缓存 `N(province, year, subjectType)`。
2. 提供 `normalize(rank, fromYear, toYear, province, subjectType)`。
3. **所有跨年位次比较必须先归一到目标年口径**：`FeatureBuildEngine` 的均值/波动/趋势计算、`BacktestService` 的参考位次、梯度区间比对，全部改为先换算。
4. 换算过的位次在 `VolunteerItem.historyRecords` 里同时保留原始值与换算值，标注 `rankSourceType=converted`。

### 9.4 新旧高考跨制度换算（受限使用）

河南官方对"2025 首年新高考如何参考 2024 文理科数据"给出的口径就是上述比例法（理科→物理类、文科→历史类）。这为贵州 2021-2023 旧文理科数据提供了**有官方先例的用法**：按比例换算后作为"弱参考层"。

结合已定的"宁缺毋滥"原则，设计为两层：

- **主层**：仅新高考纪元内数据（贵州 2024 起），参与机会指数与排序。
- **弱参考层（可选，默认关）**：旧文理科数据经比例换算后，仅当主层候选不足时补充展示，强制标注"旧文理科按考生总数比例换算，存在制度性误差"，机会指数按置信度折减，**是否启用由回测结果决定**（见第十三部分）。

---

## 十、录取概率模型（通用组件，重写现有公式）

### 10.1 模型结构

录取概率拆成两段，对应平行志愿的真实流程：

```
P(录取) = P(投档) × (1 − P(退档 | 已投档))
```

**这是贵州与专业组省份最本质的算法差异所在**：

- 贵州「专业（类）+ 院校」：档案直接投到专业，不存在调剂与组内竞争，`P(退档|已投档) ≈ 0`（体检/单科/语种等硬性不符由 `CandidateFilterEngine` 前置排除，不进概率）。**贵州的录取概率 ≈ 投档概率**。
- 院校专业组省份：投档到组后还要参与组内专业分配。`P(退档)` 取决于是否服从调剂、投档比例、组内计划余量。

### 10.2 投档概率：对数位次空间的正态 CDF

现有 `AlgorithmService.calcProbability` 在线性位次空间做 CDF，且 sigmoid 兜底的 scale 拍脑袋。业界更合理的做法是在**对数位次空间**建模（位次是乘性量，头部 100 名的波动和 5 万名处的波动量级完全不同，取对数后正态假设才近似成立）：

```
输入：该志愿单位近 3 年录取最低位次 r₁..r₃（全部先经等效位次换算到目标年口径）
μ = Σ wᵢ · ln(E(rᵢ))          权重近年优先，默认 0.5 / 0.3 / 0.2
σ = max(σ_min, sd(ln E(rᵢ)))   样本 < 2 年时取省级先验 σ₀
P(投档) = Φ( (μ − ln R) / σ )   R 为考生位次
```

- `σ_min`、`σ₀` 按省配置（`chance_params_json`），由回测校准。**河南这类同分密度极高的大省（2025 物理类本科上线 34.8 万人）σ 先验必须显著大于贵州**。
- 首年新高考省份（四川/云南/河南只有 1 年同口径数据）：σ 取省级先验上界，且结果强制标注"单年数据估算"。
- 现有 `FallbackRulePredictionEngine` 的波动惩罚、缩招惩罚、置信度惩罚作为**后置修正项**保留，参数按省拆分。

### 10.3 退档折减（专业组省份专用）

按各省官方投档比例分档：

| 情形 | P(退档) 先验 | 依据 |
|---|---|---|
| 1:1 投档省（四川、广西已核实官方口径），服从调剂 | ≈ 0.01 | 1:1 下服从调剂"提档基本等同录取"（广西官方问答） |
| ≤105% 投档省（湖北等），服从调剂 | 0.02 ~ 0.05 | 差额投档必然有退档 |
| 任意省，不服从调剂 | 按组内填报专业的相对位置估计，显著升高 | 官方列明的第一大退档原因 |

先验值入 `chance_params_json`，回测校准。其余退档原因（体检/单科/语种/选科）由硬规则层前置排除，不重复进概率。

**投档比例待核验清单**：贵州（专业直投，视同 1:1）、安徽、海南、云南、河南的官方投档比例需人工到考试院文件确认后填入配置。

### 10.4 梯度归属改由校准概率决定

现状是"位次落在哪个比例区间就算哪个档"，概率只是展示。改为：**位次区间只做 DB 预筛选，档位归属由校准后的录取概率决定**：

| 档位 | 校准后 P(录取) | 说明 |
|---|---|---|
| 冲 | [0.20, 0.55) | 业界通行的冲档概率带 |
| 稳 | [0.55, 0.85) | |
| 保 | [0.85, 0.95) | |
| 垫 | ≥ 0.95 | 官方口径只有冲稳保三档，"垫"定义为保的加强档，对外展示可合并为"保" |

各档数量目标按省预设（见第十一部分），有官方建议比例的省直接采用官方比例。

### 10.5 校准闭环

`ProbabilityCalibration` 的等渗断点从"全局一套"改为**按省**（存 `chance_params_json`），由 `BacktestService` 的分桶校准输出拟合。单条志愿对外展示的机会指数必须是校准后的值（现状只有整表安全度用了校准）。

---

## 十一、各省算法与录取规则设计

以下每省列出：录取规则要点（已联网核查）→ 数据窗口 → 算法差异点 → 梯度预设 → 风险文案要求。共用逻辑不重复，只写差异。

### 11.1 贵州 GZ —— 专业（类）+ 院校 96

- **规则要点**：本科批 96 个专业平行志愿，无调剂、无退档折减；档案直投专业。本科提前批 A/B 段为院校顺序志愿（本次不做，接口留位）。同分排序：语数之和 → 语数单科最高 → 外语 → 首选 → 再选最高 → 再选次高。
- **数据窗口**：主层 2024-2025（新高考纪元）；2021-2023 理科/文科为弱参考层（默认关，见 9.4）。
- **算法差异点**：概率模型只算投档段；专业级数据缺失时回退院校级（现有机制保留），回退项置信度降档。
- **梯度预设**：无官方比例。默认 19/38/29/10（现行），回测后调整；权威第三方口径为冲 34 / 稳 29 / 保 33（冲刺型参考）。
- **风险文案**：无调剂选项属于省规，禁止出现"服从调剂"表述（现行合规规则在贵州平行志愿批次内继续有效）。

### 11.2 四川 SC —— 院校专业组 45（本科批 B 段）

- **规则要点**：官方 1:1 投档（省考试院按执行计划数 1:1 投放电子档案），组内 6 专业 + 服从调剂；调剂不跨组。同分排序与贵州一致。2025 首年新高考。
- **数据窗口**：仅 2025 组线一年；2026 目标年需将 2025 位次经等效位次换算。
- **算法差异点**：σ 取省级先验上界（单年数据）；退档折减用 1:1 + 服从调剂档（≈0.01）；不服从调剂需按组内位置显著上调并给出强提示。
- **梯度预设**：无官方比例。参考通行 45 志愿配比 2:5:3（冲 9 / 稳 22 / 保 14），回测校准。
- **风险文案**：必须体现"1:1 投档下服从调剂退档风险极低、不服从风险高"。

### 11.3 湖北 HB —— 院校专业组 45（本科普通批）

- **规则要点**：投档比例 ≤105%，多轮模拟投档后正式投档；组内 6 专业 + 服从调剂；专项/预科单设专业组**混合填报在本科普通批志愿栏内**（候选需含专项组并按资格过滤）。2021 首年新高考。
- **数据窗口**：**2021-2025 五年同口径组线可用——专业组省份里数据基础最好的省**，采数优先级应排第一。
- **算法差异点**：五年窗口可用完整的波动/趋势特征（与贵州同款 FeatureBuildEngine 逻辑）；退档折减用 105% 档（0.02-0.05）。
- **梯度预设**：**省招办官方建议**：45 个按"冲 15 左右 / 稳 15 左右 / 保 15 左右"。直接采用为默认。
- **风险文案**：需体现差额投档的客观退档风险与征集志愿机制。

### 11.4 安徽 AH —— 院校专业组 45（普通本科批）

- **规则要点**：组内 6 专业 + 专业服从志愿；同分排序同贵州，兜底按报名序号。2024 首年新高考。投档比例待核验。
- **数据窗口**：2024-2025 两年。
- **算法差异点**：两年数据可算初步波动；σ 先验中档。
- **梯度预设**：无官方比例，沿用 2:5:3 起步，回测校准。

### 11.5 广西 GX —— 院校专业组 40（本科普通批）

- **规则要点**：**40 个志愿（非 45）**；**组内 20 个专业志愿**（全表最多）+ 组内调剂；官方 1:1 投档；官方问答明确建议勾选调剂。2024 首年新高考。
- **数据窗口**：2024-2025 两年。
- **算法差异点**：`major_per_group_count=20`，组内专业展示与调剂风险计算按 20 计；组内可填专业多 → 服从调剂时退档概率进一步下调。
- **梯度预设**：权威解读口径"冲 8-10 / 稳 20-22 / 保 8-10"，取 9/21/10 起步。

### 11.6 海南 HI —— 院校专业组 30（本科普通批，3+3，标准分）

- **规则要点**：3+3 无物理/历史分轨，选科匹配为"考生 3 科 ⊇ 专业组要求"；**标准分 900 分制**（官方转换办法：单科 Ti=180+30Zi，综合 Ti=500+100Zi，语数英权重 1.5，区间 [100,900]，不公布原始分）；30 个组，组内 6 专业 + 服从调剂。2020 首年，**6 年同口径数据，全表数据一致性最好**。
- **数据窗口**：2020 起任选近 3-5 年。
- **算法差异点**：
  - `SubjectMatcher` 用 `ThreeThreeMatcher`（集合包含判定），科类维度全省单轨。
  - `RankResolver` 用海南标准分一分一段表，分数合法区间校验改为 [100, 900]（现有代码按 750 制假设需隔离）。
  - 标准分本身是排名的函数，跨年比较仍以位次为准（等效位次换算照常适用）。
- **梯度预设**：**省考试局官方问答第 63 条**：冲 10 / 稳 10 / 保 10。直接采用。

### 11.7 云南 YN —— 院校专业组 40（本科批）

- **规则要点**：**40 个志愿（非 45）**；**组内 10 个专业志愿**；符合专项计划资格 +10 个、少数民族预科资格 +10 个（普通组上限仍 40）；2025 首年新高考。
- **数据窗口**：仅 2025 一年。
- **算法差异点**：`major_per_group_count=10`；请求模型需接收资格标签（`qualificationTags`）以决定志愿容量 40/50/60；单年数据 σ 上界。
- **梯度预设**：**省招生考试院官方口径**：冲 20% / 稳 40% / 保 40%，即 8/16/16。直接采用。

### 11.8 河南 HA —— 院校专业组 48（普通本科批）

- **规则要点**：**48 个志愿（非 45）**；组内 6 专业 + 服从调剂；专项计划混合填报在本科批内；单设高水平运动队 1 个组；**违约考生限报 24 个**（边缘规则，先在文案提示，不进算法）；本科提前批为「专业+院校」64 个（复用贵州的 `MajorPlusSchoolProvider`，数据就绪后开放）。2025 首年新高考。
- **数据窗口**：仅 2025 一年。
- **算法差异点**：**同分密度全国最高**（2025 物理类本科上线 34.8 万人），同一位次段考生数量大 → σ 先验取全表最大值，梯度必须整体保守（压缩冲档、加厚保垫），概率展示需附"大省同分密度高，位次波动大于小省"提示。
- **梯度预设**：无官方比例。48 个按保守型 8/20/20 起步，回测校准。

### 11.9 同分排序规则的统一实现

已核查省份（贵州、四川、安徽、广西、河南）的普通类同分排序链完全一致：

```
语数两科之和 → 语文或数学单科最高 → 外语 → 首选科目 → 再选科目最高 → 再选科目次高
```

湖北、云南、海南待逐字核验（海南为标准分体系，规则不同）。实现为一个 `TieBreakRule` 配置项存 `province_profile.rank_tie_break_rule`，当前系统用一分一段 `rank_high`（同分保守位次）作为统一口径的做法**保留**——同分排序只影响同分段内的相对位置，用保守位次已覆盖最坏情况，不需要为每个考生真实计算同分链（系统拿不到考生单科分数时该链不可算；`GenerateRequest` 可选增加单科分数输入，有则精化，无则保守）。

### 11.10 数据采集优先级（按数据可得性与一致性排序）

| 优先级 | 省 | 理由 |
|---|---|---|
| 1 | 湖北 | 2021 起 5 年同口径组线，专业组省份中唯一能算完整波动特征的省 |
| 2 | 海南 | 2020 起 6 年同口径，但需先做 3+3 与标准分支持 |
| 3 | 安徽、广西 | 2 年数据，模型可用度中等 |
| 4 | 四川、云南、河南 | 仅 1 年数据，只能做"单年估算"档，且河南需要最保守参数 |

---

## 十二、分阶段实施计划

**已决策：阶段 0 与阶段 1 一起做。**

> **实施状态（2026-08-12）**：阶段 0 全部条目与阶段 1 全部条目已编码完成，`mvnw test` 210 个测试全部通过。
> 落地文件：迁移 `db/20260812_province_profile_policy.sql`；新增 `ProvinceReadinessService`、
> `ProvinceBatchSupportController`、`RecommendationOrchestrator`、`algorithm/core/*`（枚举/接口/ThreeOneTwoSubjectMatcher）、
> `algorithm/provider/*`（两个委派型 Provider）、`ProvinceProfile` 实体与 mapper；
> 改造 `ProvincePolicyService`（8 省 + 读库覆盖）、`PolicyRuleService`（宽松查询）、`VolunteerRecommendController`
>（编排入口 + 志愿单位修正）、两条旧链路增加取数缝合口。
> 阶段 1 的"回测前后一致"验收项需要有数据的环境，待生产/预发联调时执行。
> 备注：ThreeOneTwoSubjectMatcher 收敛时确认两条链路的再选匹配语义不一致（严格 vs 宽松），
> 已按原语义分别保留并在类注释中说明，统一语义留待阶段 2 回测护航后决策。

### 阶段 0 · 止血（不改算法）

目标：消除"点进去报错"和"数据状态读取失败"，让线上表现与真实能力一致。

| # | 改动 | 涉及文件 |
|---|---|---|
| 0.1 | 新增 `ProvinceReadinessService`，基于真实行数判定四态 | 新增 `service/ProvinceReadinessService.java` |
| 0.2 | 新增 `/volunteer/{provinceCode}/batch-support` 接口（即前端所需契约，响应结构对齐前端已定义的 `ProvinceBatchSupportResponse`） | `controller/VolunteerController.java` 或新增 controller |
| 0.3 | `policy_rule_config` 补 **全部 7 个非贵州省份** 的种子（志愿数按第二节核查表：SC 45 / HB 45 / AH 45 / GX 40 / HI 30 / YN 40 / HA 48），未接入省份 `enabled=0` 或 `policy_status=draft` | 新增 `db/2026xxxx_province_policy_seed.sql` |
| 0.4 | 修正 `VolunteerRecommendController:128` 写死 `UNIT_MAJOR_96` 的问题 | `controller/VolunteerRecommendController.java` |
| 0.5 | `ProvincePolicyService` 扩到 8 省并挂接就绪度门禁：未就绪省份返回结构化"筹备中"响应而非异常文案 | `service/ProvincePolicyService.java` |

前端配合项（不在本次后端范围，仅列契约）：省份卡片状态改为调 `batch-support` 后端驱动；修正广西 40 / 云南 40 / 河南 48 等硬编码参数。

验收：8 个省份的 `batch-support` 均返回 200 且状态真实；未就绪省份生成接口返回结构化禁用原因而非 500。

### 阶段 1 · 抽骨架（行为不变）

目标：建立可插拔结构，**一行算法都不改**，用回测锁定贵州输出不变。

| # | 改动 |
|---|---|
| 1.1 | 定义 `VolunteerUnitType`、`SubjectMode` 枚举与 `CandidateProvider`、`SubjectMatcher` 接口 |
| 1.2 | 把 `VolunteerService.pickGradient` 的取数逻辑原样搬入 `MajorPlusSchoolProvider` |
| 1.3 | 把 `ProfessionalGroupVolunteerService.pickGradient` 原样搬入 `ProfessionalGroupProvider` |
| 1.4 | 抽 `ThreeOneTwoMatcher`，收敛现有 `matchResubject` / `mapSubjectType` / `compatibleSubjectType` 三处重复实现 |
| 1.5 | 新建 `RecommendationOrchestrator` 骨架，暂时仍按原路由分发到两条旧链路 |
| 1.6 | 建 `province_profile` 表 + 扩展 `policy_rule_config` 字段，`ProvincePolicyService` 改为读库（保留 Java 常量作为降级兜底） |

验收：**重构前后对贵州跑同一组回测，命中率、Brier、梯度达线率必须一致。** 这是本阶段唯一的成功标准。

### 阶段 2 · 并线

让院校专业组省份接上共享算法层，第一次拥有机会指数、梯度、排序和诊断。同时消灭 `GradientAllocationEngine` 与 `VolunteerService.targetCounts()` 的双实现。

> **实施状态（2026-08-12）**：已完成，`mvnw test` 215 个测试通过。
>
> - 新增 `algorithm/ProfessionalGroupAlgorithmEnricher`：专业组条目接上机会指数（FeatureBuildEngine +
>   FallbackRulePredictionEngine，直接消费已加载的 historyRecords，零额外查询）、意向匹配（组内专业/组名/省市，
>   精确包含语义）、VolunteerSortEngine 策略化排序（recommendationScore 不再是数据质量分）、
>   VolunteerDiagnosisEngine 13 维诊断、ProbabilityCalibration + 蒙特卡洛整表安全度。
> - `ProfessionalGroupVolunteerService` 改为政策驱动：志愿总数取政策库值（30-48 各省不等，替代写死 45）；
>   梯度数量按 `gradient_preset_json`（湖北 15/15/11+4、海南 10/10/7+3、云南 8/16/11+5 官方比例已入种子）
>   → 缺省回退 `GradientAllocationEngine.allocateCounts` 按策略分配；位次比例区间随策略模式变化
>   （均衡型 = 重构前固定区间，向后兼容）；组内专业展示上限跟随省政策（广西 20/云南 10）。
> - 双实现消灭：`VolunteerService.targetCounts` 委派 `GradientAllocationEngine.allocateCounts`
>   （96/60 预设与通用分支逐值等价，通用分支统一为向下取整+余量给垫）。
> - 顺带修复 `VolunteerSortEngine.sort` 的潜在缺陷：finalScore 此前在比较器 thenComparing 内部计算，
>   梯度不同的条目走不到第二比较器导致 recommendationScore 不被写回；改为排序前显式逐条计算，排序结果不变。
> - 未动项：贵州主链路的候选/富集逻辑（仍在 VolunteerService 内，物理迁移待回测环境就绪后进行）；
>   梯度归属仍按位次区间（改为校准概率定档是阶段 3 的 3.4）。

### 阶段 3 · 准确率（对应第九、十部分的设计）

| # | 改动 | 状态（2026-08-12） |
|---|---|---|
| 3.1 | 新增 `RankNormalizationService`，所有跨年位次比较改为等效位次口径（第九部分） | **已完成**：贵州主链路、专业组富集、回测参考位次三处已接；考生总数缺失自动退化为原值 |
| 3.2 | 科类纪元隔离：主层仅新高考纪元数据；旧文理科弱参考层默认关闭，待回测评估 | **已完成**：候选检索按 `province_profile.new_gaokao_first_year` 过滤（贵州 2024+），本地复验候选参考年仅剩新高考纪元且 96 条满额；旧文理弱参考层保持默认关闭 |
| 3.3 | 概率模型重写为"对数位次 CDF × 退档折减"，参数按省入 `chance_params_json` | **已完成并切换**：本地回测 A/B 达标后部署默认已切 log-rank（application.yml，`GZLY_ALGO_CHANCE_MODEL=sigmoid` 可回退）；退档折减先验 `group-withdraw-prior` 已入专业组链路 |
| 3.4 | 梯度归属改由校准概率决定，位次区间仅做预筛（10.4） | **专业组链路已完成**（概率带：垫≥0.95/保≥0.85/稳≥0.55/其余冲）；贵州链路涉及数量配额重构，待回测护航 |
| 3.5 | 等渗校准断点按省拆分，单条机会指数对外展示改为校准值（10.5） | **展示校准已完成**：新增 `VolunteerItem.calibratedProbability`（贵州=校准值，专业组=校准×(1−退档先验)）；按省断点需回测拟合数据，待各省回测后填 `chance_params_json` |
| 3.6 | 修伪匹配维度 | **部分完成**：排序引擎"双匹配"不计分的缺陷已修；就业维度改为中性常量（关键词清单不再影响排序）；学费真实值受数据阻塞（贵州无专业学费数据源），待采数 |
| 3.7 | 服从调剂建模：按 `has_adjustment` 展示，退档风险进概率与文案（10.3） | **已完成**：专业组条目的校准概率含退档折减，riskReason 附组内调剂说明 |

验收：贵州 2025 回测的 Brier 与分桶校准曲线显著优于重构前基线；每项改动单独跑回测留档。log-rank 切换必须 `POST /admin/backtest/run {"chanceModel":"log-rank"}` 与 sigmoid 对比达标。

### 阶段 4 · 性能

| # | 改动 | 状态（2026-08-12） |
|---|---|---|
| 4.1 | 候选查询加 LIMIT | **已完成**：`findMajorCandidates` / `findCandidates` 加 LIMIT 5000 兜底（年份降序保证优先保留新数据） |
| 4.2 | 批量取历史 | **全部完成**：贵州链路每条 4 次查询合并为 1 次 + 三个 `*FromLines` 免查询重载；专业组链路 `selectRecentHistoryBatch`（窗口函数每组近 3 年）+ `selectGroupMajorsBatch` 两次 IN 批量替代逐条查询（45 条约 90 次 → 2 次），选科要求按学校集合预取 + 内存匹配（每梯度 1 次） |
| 4.3 | 缓存键去 `studentRank` | **已完成**：`calcProbability` 的无效缓存（键含考生位次，命中率≈0 且写放大 Redis）已移除；历史线缓存下沉到 `getRecentLines`（键不含位次，跨考生共享，TTL 6h） |
| 4.4 | 索引复核 | **已完成**：`data_major_score_gz`（16 万行）此前无匹配候选查询谓词的索引，迁移文件补 `(subject_type, min_rank)` 复合索引（守卫式，两张表） |

### 阶段 5 · 扩省（对应第十一部分的各省设计）

| # | 改动 | 状态（2026-08-12） |
|---|---|---|
| 5.1 | `ThreeThreeMatcher` + 标准分位次口径（[100,900] 区间校验），开放海南 | **代码层已完成**：ThreeThreeSubjectMatcher（要求集合 ⊆ 选科集合，"或"任一命中）、标准分 900 校验、"综合"轨道贯通门禁/一分一段/归一化/回测；待海南数据导入后自动解锁 |
| 5.2 | 广西 `major_per_group_count=20`、云南 `=10` 的组内专业参数化 | **已完成**（阶段 2 落地） |
| 5.3 | 云南资格扩容：`qualificationTags` 决定志愿容量 40/50/60 | **已完成**：专项资格 +10、少数民族预科资格 +10（官方口径 11.7） |
| 5.4 | 河南保守参数集（σ 上界、梯度压冲加保）+ 违约限报 24 的文案提示 | **σ 参数已完成**：`chance_params_json={"sigmaMin":0.12}` 入种子并经 Overrides 机制按批次生效；梯度压冲与限报提示待产品输入 |
| 5.5 | `SchoolSequentialProvider`（顺序志愿，贵州提前批 A/B、河南提前批 64 个专业+院校待数据） | 未开始（数据未采） |
| 5.6 | 各省按 11.10 的优先级逐个接数据，回测达标才从 `ESTIMATE` 升 `FULL` | **回测框架已支持全部省份**：`POST /admin/backtest/run {"provinceCode":"HB",...}` 走组级调档线口径；数据导入后即可跑校准闭环 |

---

## 十三、验收标准

### 13.0 本地 Docker 环境验证记录（2026-08-12）

环境：Docker `mysql:8.0` + `redis:7-alpine`（本机 3306/6379），导入仓库 `scripts/data/export/` 的贵州数据：
院校 2198 / 院校线 58,678 / 专业线 57,024（生产 16.3 万的子集，覆盖 2023-2025）/ 官方一分一段 2,435 / 官方选科要求 24,483；
schema + 全部迁移（含 20260812）跑通，服务端口 18082。

| 验证项 | 结果 |
|---|---|
| batch-support × 8 省 | 全部 200：GZ=ESTIMATE（本科批 ESTIMATE_RECOMMEND），其余 7 省 LOCKED 并给出"一分一段未导入"缺口说明 |
| 贵州生成（68000 名物理类） | code=0、96 条、梯度/诊断/整表安全度/校准概率（chance 33 → calibrated 25.7，数学核对一致）全部输出 |
| 宁缺毋滥 | 60000 名请求在本地子集数据下返回 67 条并说明原因，不凑数 |
| 未就绪省份拦截 | 广西请求返回"广西专区暂未开放智能生成：官方一分一段表尚未导入…"（门禁已提到政策解析之前） |
| **回测 A/B（70,205 评估对）** | **sigmoid Brier 0.0217 vs log-rank 0.0155（优 28.6%），命中率同 0.9269，覆盖率 1.0 —— 本地达标**；生产库复跑同一命令达标后经 `GZLY_ALGO_CHANCE_MODEL=log-rank` 切换 |
| 校准桶 | 两模型中段桶均偏保守（pred 0.5 档实际达线 ≈1.0），留档作为按省等渗断点拟合的输入 |
| 生成性能 | 96 条热态 **17s → 3-4s**（约 4-5 倍）；冷启动首请求 12.5s（JIT+缓存预热） |

验证过程中修复的两个存量缺陷：

1. **Redis List 缓存从未命中**：`RedisConfig` 的序列化器用 `DefaultTyping.NON_FINAL`，List 值写入不带类型标签，
   读取必然 `SerializationException` 回源——`recentScoreHistory` / `candidateMajorScores` 等列表缓存上线以来一直失效并刷警告。
   改为 `EVERYTHING`（白名单校验不变），全部列表缓存恢复可用。
2. **选科要求 N+1**：`pickGradient` 对每个进入区间的候选行做一次模糊查询（宽区间下数千次）。改为按学校集合
   一次 IN 预取 + 内存匹配（`pickOfficialRequirement` 逐条复刻 SQL 语义并有单测锁定），每梯度 1 次查询。
   同时移除两个键含精确位次区间、命中率趋近 0 的大对象缓存（`candidateScoreLines` / `candidateMajorScores`）。

复用已有的 `BacktestService`（截止年推荐、次年真实位次验证、命中率 + Brier + 梯度达线率），两个用途：

1. **重构安全网**：阶段 1 只搬代码不改算法，重构前后对贵州跑同一组回测，指标必须一致，否则说明搬错了。
2. **开省门禁**：每个省接入后必须跑回测，命中率与校准曲线达标才允许从 `ESTIMATE` 升到 `FULL`。

配套改动：`FallbackPredictionProperties` 从单例改为按「省 × 批次」的参数集，回测按批次产出各自的校准参数。

~~`BacktestService` 自身也需要适配——当前它的梯度区间预设是从 `VolunteerService` 复制的硬编码~~（v7 已解决：三处副本统一委派 `GradientAllocationEngine.ratioPreset/offsetPreset` 单源）。

### 13.1 v7 架构审计修复复验记录（2026-08-12）

| 验证项 | 结果 |
|---|---|
| 旧接口统一流程 | `/volunteer/generate`（贵州）返回 policy（maxCount=96）/modelInfo/warnings 完整包装；合规告知校验（`agreedDisclaimer` + 版本号）在新流程中继续生效 |
| 双接口同缓存 | `/volunteer/generate` 冷 16.1s 后，同请求打 `/volunteer/recommend` 343ms 直接命中同一指纹缓存——两接口确为同一条流程 |
| 动态政策年 | batch-support 与生成均按 2026 年解析（GZ 2026 行 pending_confirm → warning"政策待确认"随响应返回） |
| 门禁在旧接口生效 | 河南（本地无一分一段）打旧接口返回"河南专区暂未开放智能生成：官方一分一段表尚未导入，无法建立位次口径，生成入口锁定"；此前旧接口会绕过门禁直接进生成 |
| 回测互斥 | 并发两个 `/admin/backtest/run`：一个正常完成，另一个立即返回"已有一个回测任务在执行中" |
| **纪元隔离后的回测新基准** | **hitRate 0.9269 / Brier 0.0167**（评估对 70,205、覆盖率 1.0）。参考窗口与主链路同口径（2024+，不再含 2022-2023 文理数据）。注意：v4 记录的 0.0155 是旧口径（回测当时用了主链路已经不用的跨纪元特征），两个数字**不可直接比较**，以 0.0167 为今后 A/B 的基线 |
| snapshot 口径警示 | `paramsSnapshot.hitDefinitionCaveat` 落库：命中口径系统性偏乐观，仅适合相对 A/B，绝对校准需录取结果回流 |
| 测试 | 248 全过（新增：orchestrator 专业组锁/缓存 2 项、政策年 fallback 4 项） |

专业组省份的锁/缓存路径本地无法走 HTTP 复验（本地库仅贵州数据、7 省 LOCKED 属门禁如实反映），行为由单测锁定：缓存命中不再触发生成、首次生成后写入 `gzly:v7:pg:result:*`（TTL 120s）。

---

## 十四、风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| `VolunteerService` 4000 行，重构易引入回归 | 高 | 绞杀者模式 + 回测基线，不做大爆炸式重写 |
| 贵州候选池缩水后凑不满 96 | 中 | 已决策"宁缺毋滥"；等效位次换算的弱参考层可作缓冲（默认关，回测评估） |
| 生产库 `policy_rule_config` 实际内容未知 | 中 | 落地前需人工核对生产库；种子脚本使用幂等 upsert |
| 政策核查来自网络检索，非考试院原文留档 | 高 | 落地前必须人工到各省考试院官网复核并留存链接与截图；投档比例仍有 5 省待核验（10.3） |
| 非贵州省份数据缺口大 | 高 | 可用性取决于采数进度；按 11.10 优先级推进（湖北五年数据最优先）；门禁保证不给无依据结果 |
| 首年新高考省份（川滇豫）只有单年数据 | 高 | σ 取先验上界 + 强制"单年估算"标注 + 保守梯度；不伪造波动特征 |
| 概率模型重写引入新偏差 | 中 | 每项改动单独回测对比；校准断点由回测拟合而非手拍 |

---

## 十五、待决事项

1. **梯度档位**（已给推荐）：内部保留四档（冲/稳/保/垫）用概率带定档（10.4），对外展示"垫"并入"保"与官方三档口径对齐。待确认。
2. **服从调剂**：`HANDOVER.md` 的全局禁止规则需要改为按「省 × 批次」的 `has_adjustment` 判定，此改动涉及合规文案，需确认。
3. **贵州旧文理科数据**：默认按已决策"宁缺毋滥"关闭；等效位次换算提供了有官方先例的弱参考层用法（9.4），是否启用由回测结果决定。待回测后再定。
4. **生产库现状**：`policy_rule_config` 是否已有非贵州行？`data_admission_group_line` 生产实际行数？需要一次只读核对。
5. **投档比例核验**：贵州（专业直投）、安徽、海南、云南、河南五省的官方投档比例待人工到考试院文件确认（10.3），影响退档折减先验。
6. **同分排序核验**：湖北、云南、海南三省的同分排序规则待逐字核验（11.9）。

### 明确不并入 v7 的两项大重构（独立立项）

1. **`VolunteerService` 上帝类物理搬迁**（约 4000 行拆到 provider/enricher 层）：涉及 20+ 文件的行为等价搬迁，
   与本轮 8 项修复混做会导致回归无法定位。回测安全网已就绪（0.9269/0.0167 基线），
   但仍建议先补贵州 96 条输出的 golden 快照测试再动手。
2. **`VolunteerItem` 瘦身 / 列表-详情分离**（`plan_json` 落库约 300KB）：改响应结构破坏前端契约，
   需与前端排期协同；纯后端可先行的部分（`plan_json` 压缩存储）也牵扯历史方案回读兼容，一并立项。
