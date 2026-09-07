# GZLY 整体重构方案

> 编写时间：2026-09-06
> 仓库根：`F:/GZGK`（四端 monorepo：gzly-server / gzly-web / gzly-app / ml-service + scripts）
> 性质：只读分析产出的重构方案（本方案本身未改动任何业务代码）
> 证据来源：6 个子系统深读（后端核心 / 后端 API / 前端 / ML 与脚本 / APP / 文档与版本管理）+ 5 项对抗性核查 + 3 份独立视角方案（架构 / 风险 / 增量）交叉综合。关键论断均绑定 file:line，对抗核查带修正的结论已采用修正后事实。

---

## 〇、结论摘要

**一句话：功能与算法的重构方向正确、完成度约八成，但整个 8 月的 v7 重构成果一分都还没入库；当前最大风险不是"架构怎么改"，而是"版本控制、安全网与正确性止血"。任何结构性拆分必须等 main 恢复一致性之后动工。**

三份独立视角方案（架构整洁优先 / 风险安全优先 / 增量交付优先）在分期骨架上完全收敛为同一结论：

| 期 | 名称 | 核心内容 | 规模 |
|---|---|---|---|
| 期 0 | 版本基线落盘 | 87 修改 + 208 未跟踪按主题入库、gzly-app 入库、CI 建立、分支处置 | 1-2 周 |
| 期 1 | 安全网 + 致命缺陷修复 | golden 快照、PG 直接单测、鉴权误拦/指纹缺失/ML 断裂/JWT 守卫/Redis 原子化 | 1-2 周 |
| 期 2 | 生成链路绞杀 + 两链路统一 | VolunteerService 3983 行绞杀分解、共享组件、行为对齐 | 4-6 周 |
| 期 3 | Web/App 收敛 + 管理端拆分 | 省份口径后端驱动、共享包、AdminController 拆分、注解式鉴权、巨型视图减重 | 3-4 周 |
| 期 4 | 运维 / 数据 / 文档治理 | Flyway 化迁移、nginx 入库、降权、FULL 就绪态、文档口径对齐 | 持续 |

---

## 一、总体判断（六条）

1. **版本管理是第一问题。** main HEAD（`79ef7a3`，2026-06-02）与工作树相差 87 个已修改文件（+5730/-2305）+ 208 个未跟踪文件，跨度约三个月，全部成果只在单台开发机。**已提交的 HEAD 本身前后端契约是断的**：前端调用 8 省 `/volunteer/{code}/batch-support`（`gzly-web/src/api/volunteer.ts:199-213`）与 `/score-lines/{province}/*` 复数系列，HEAD 后端零对应路由（batch-support 实现是工作树未跟踪的 `ProvinceBatchSupportController.java`；score-lines 唯一实现在未合并分支 `snapshot-main-wip-20260515` 的提交 703c456）——main 当前不可独立部署。`gzly-app` 整目录（约 1 万行，14 页面）零版本保护。

2. **推荐链路"逻辑收敛"已完成、"物理分解"缺位。** v7 重构已落地统一入口 `RecommendationOrchestrator.generateWithPolicy`（无旁路）、梯度预设单源、共享富化器、回测基线 hitRate 0.9269 / Brier 0.0167。但 `VolunteerService.java` 仍是 3983 行上帝服务（十类职责），`AdminController.java` 1275 行十大职责 + 14 个 mapper 直注。**双链路系统性契约漂移**：专业组省份用户声明的 acceptPrivate/dislikedMajors/maxTuition 只落库不过滤（`ProfessionalGroupVolunteerService.java:144-145`），偏好权重四字段被丢弃（`ProfessionalGroupAlgorithmEnricher.java:82-86` 全传 null）。

3. **鉴权是系统性最薄弱环节，且已产生一个已证实的生产功能失效。** 路径枚举式拦截器把公开端点 `/alumni/media/list` 误拦 401（`WebMvcConfig.java:120-121`），公开大学详情页 UGC 媒体对非校友访客静默消失（`UniversityDetail.vue:115` try/catch 吞掉）。校友超管 role>=9 登录签发 role="admin" JWT（`AlumniController.java:118-119`），等同全部系统管理权限；三套用户 ID 空间在审计字段互污。getClientIp 六处实现、四处盲信 X-Forwarded-For，限流可被绕过。

4. **核心生成链路存在"把错误结果当正确结果返回"级别的缺陷。** (a) 贵州请求指纹缺 16 项硬规则输入字段（指纹清单 `VolunteerService.java:2838-2856` vs `buildFilterCriteria` 实际消费 `:1435-1465`），120s 缓存窗口内不同过滤条件请求共享缓存返回错误结果；(b) ML rank 模型 camel/snake 特征映射方向写反（`ml-service/app/api/predict.py:89`），Java 路径上静默输出常量预测，训练闭环三处断裂（X-Admin-Token 头后端不认、activate 不生效、训练目录≠serving 目录），`MlAdminController.callMl` 吞异常伪造假模型版本写入注册表；(c) `@Transactional` 包住生成全程含最长 4s 锁等待（`VolunteerService.java:595`，核查修正：事务内实为 biz_user UPDATE + plan_history INSERT 两次写），Hikari 池仅 20 连接，高并发耗尽风险。

5. **四端共享层不存在且已真实漂移。** 省份志愿数 web 前端 45 vs 后端 GX/YN 40、HA 48（`provinces.ts:198/270/306` vs `ProvincePolicyService.java:164/168/170`）；DISCLAIMER_VERSION 三端硬编码、后端强校验不一致即拒（升版会使存量客户端全量失败）；合规违禁词表 web 27 词 vs app 21 词分叉（监管红线）。app 的架构决策（后端驱动省份参数、两段式类型）优于 web，共享层应以 app 模式为基准反向输出。

6. **工程基建与代码同权重债。** 全仓库无 CI + `RUN_TESTS=0` 直发生产习惯；35 个手工 SQL 无版本管理；JWT 密钥样例置空且 `JwtUtil` 无空值守卫；仓库 316MB；卡密系统约 600 行死代码与 HANDOVER 下线声明矛盾共存；双就绪判定体系并存；7 条未合并远端分支（含三套平行就绪实现）。

---

## 二、与既有文档的关系

| 文档 | 关系 |
|---|---|
| `docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md`（v7） | **继承其全部已编码成果**（阶段 0-4、统一入口、回测基线），本方案期 0 将其提交固化；其"明确不并入 v7"的第 1 项（VolunteerService 物理搬迁）由本方案期 2 正式接管；阶段 5（扩省）转为数据运营线，不再作为代码工程主轴；其状态行失真（`:10` "待联调" vs changelog "已完成"）在期 0 一并修正 |
| `docs/BACKEND_ARCHITECTURE_DECISION.md`（ADR） | **全部继承**：不做微服务、模块化单体、A1 删空壳引擎 / A2 对外两个数字 / A3 排序回后端 / A4 FULL 判定分别映射到期 2/3/4 |
| `HANDOVER.md` / `DEV_PROGRESS.md` / `README.md` | 口径系统性失真（单省 vs 八省、"62 测试" vs 实际约 260 @Test），期 4 统一重写，在此之前不作为事实源 |

**本方案不改任何算法数值口径**（σ、校准断点、梯度比例、概率带）。算法演进继续走 v7 文档的回测门禁流程，与结构重构分离——这是保证回归可定位的前提。

---

## 三、目标形态

### 3.1 仓库级结构

```
F:/GZGK（pnpm workspace + Maven 单模块 + 独立 Python 服务）
├── gzly-server/          Java 17 模块化单体（ADR 包边界，分批归位）
├── gzly-web/             Vue3 web（消费 packages/shared；admin/alumni/SSE 专属逻辑留本地）
├── gzly-app/             uniapp（消费 packages/shared；uni.request 适配层留本地）
├── ml-service/           FastAPI（特征契约与 Java 共享同一 JSON schema）
├── packages/             前端共享层（新增，切分序：compliance → province → protocol → api）
├── scripts/              数据脚本（爬虫演进残留归档分类）
├── .github/workflows/    最小 CI 门禁（新增）
└── docs/                 文档（口径与代码对齐）
```

### 3.2 统一推荐管线（消解双链路漂移的终态）

```
VolunteerController /generate ──┐
VolunteerRecommendController /recommend ─┴→ RecommendationOrchestrator.generateWithPolicy
    ├─ normalizePublicRequest（已有）
    ├─ ProvinceReadinessService.requireGenerationReady（已有）
    ├─ PolicyRuleService.requirePolicy（已有）
    ├─ GenerationContext 组装（新：替代 GenerateRequest 的 policy* 过渡字段）
    ├─ GenerateIdempotency.execute（新：指纹+锁+缓存 单源，Lua compare-and-delete 释放，
    │                               合并 VolunteerService.java:2793-2836 与 Orchestrator:139-229 两份实现）
    └─ GenerationPipeline.run(ctx)（新：阶段化）
         1 CandidateRetrievalStage → CandidateProvider（MajorPlusSchool / ProfessionalGroup 真实实现）
         2 HardFilterStage        → CandidateFilterEngine（16 硬规则两链路统一）
         3 HydrationStage         → 批量装载（PG hydrateGroupDetails 90次→2次模式推广到 GZ）
         4 FeatureStage           → FeatureBuildEngine + RankNormalizationService
         5 PredictionStage        → PredictionProvider（rule=FallbackRulePredictionEngine / ml=MlPredictionService）
         6 SortStage              → VolunteerSortEngine（ML 值参与排序，消除"事后覆写"）
         7 GradientStage          → GradientAllocationEngine + 概率定档统一
         8 ReliabilityStage       → ItemReliabilityEnricher（新共享组件，权重差异参数化）
         9 DiagnosisStage         → VolunteerDiagnosisEngine + MonteCarlo
        10 PersistStage           → PlanPersistenceService（窄事务：biz_user UPDATE + plan_history INSERT）
        11 AssembleStage          → PlanResultAssembler（policy/modelInfo/warnings）
```

此设计逐条消解已确认的漂移：ML 事后覆写（5 前置于 6）、幂等两份（GenerateIdempotency 单源）、置信度两套公式（ReliabilityStage 共享）、硬规则 PG 缺失（HardFilterStage 统一）、梯度语义两套（GradientStage 统一）、GZ N+1（HydrationStage 批量化）。

### 3.3 可量化验收指标

| 指标 | 现状 | 目标 |
|---|---|---|
| 未提交变更 | 87 M + 208 ??（约 3 个月） | 0；提交间隔 ≤ 1 天 |
| CI | 无 | PR 全绿门禁，四端 job |
| GZ golden 快照 | 无 | 迁移类重构前后逐值一致 |
| 回测基线 | hitRate 0.9269 / Brier 0.0167（本地） | 期 2 完成后不低于基线 |
| VolunteerService 行数 | 3983 | < 1500（编排壳 + 贵州特化） |
| PGVS 直接测试 | 0 | YN 配额 / HI 校验 / preset 校验全覆盖 |
| 生成事务占用连接 | 全程（含 4s 锁等待） | 仅末尾两次写 |
| 前端测试 | web 2 spec、app 0 | 契约 + store + normalize 覆盖 |

---

## 四、分期计划

### 期 0 · 版本基线落盘（1-2 周，硬前置，不改代码行为）

**提交序列**（每批提交前对应端测试全绿）：

1. `chore(repo)`：删 `vs_methods.txt`（零引用）、`gzly-web/vite.config.ts.timestamp-*.mjs`；`.gitignore` 追加 timestamp 模式。**注意：`gzly-web/public/school-photos/` 64 个 bucket JSON 是 `school-photos.ts:18` 运行时按需加载的必要资产，随前端入库，不是垃圾**（对抗核查修正项）。
2. `feat(backend)` v7 多省算法重构主体：45 个未跟踪 gzly-server 文件（RecommendationOrchestrator、ProvinceReadinessService、**ProvinceBatchSupportController（HEAD 契约断裂修复，最优先）**、algorithm/core+provider、3 个 SQL 迁移、15 个测试）与 39 个已修改文件**原子提交**（已跟踪文件引用未跟踪类，拆开中间 commit 不可编译）。
3. `feat(backend)` 邮箱登录与账号（EmailAuthController 等）。
4. `feat(web)` 八省前端 + zod 契约 + school-photos 资产。
5. `feat(app)` gzly-app 整目录（73 文件，自带 .gitignore 已排除 node_modules/dist）。
6. `feat(ml)` 弱标签防泄漏等。
7. `docs`：修正 MULTI_PROVINCE 状态行；新增 `BRANCH_DISPOSITIONS.md` 记录 7 条远端分支处置（`snapshot-main-wip-20260515` 的 703c456 含 ScoreLinesController 供期 1 移植；两条 pre-official 分支的平行就绪实现已被 ProvinceReadinessService 取代，标记 superseded）。
8. 最小 CI（`.github/workflows/ci.yml` 四 job：mvnw test / web vitest+build:safe / app type-check / ml pytest）。
9. 部署护栏：`deploy_backend_safe.sh` 的 `RUN_TESTS=0` 需显式 `CONFIRM_SKIP_TESTS=1`。

**验证**：提交后 `git status` 干净；全新 clone 四端构建全绿；比对生产 JAR 与部署记录确认工作树等价性。

### 期 1 · 安全网 + 致命缺陷修复（1-2 周，不改变算法输出）

**A. 测试安全网**：GZ golden 快照（固定请求断言 96 条输出稳定子集，CI 用 mock 版 + Docker 全量版双轨）；PGVS 首批直接单测（YN 配额 `:509-522`、HI 3+3/900 `:759-791`、preset JSON 总和 `:546-575`）；拦截路径 × controller 映射对齐测试（正是 blocker 漏网原因）；省份口径对齐快照测试；反射测试迁公共入口。

**B. 缺陷修复**（每项独立 commit）：

| # | 修复 | 位置 |
|---|---|---|
| 1 | `/alumni/media/list` 从拦截 exclude；删 pending/review 重复注册 | WebMvcConfig.java:112-121 |
| 2 | ML camel/snake 映射方向修正 + 特征命中率告警 | predict.py:89 |
| 3 | 训练脚本鉴权头 X-Admin-Token → Bearer JWT | train_models_on_server.py |
| 4 | callMl 失败抛 BizException，不再伪造假版本入注册表 | MlAdminController.java:95-153 |
| 5 | GZ 指纹补全 16 项硬规则字段（或对过滤条件对象整体哈希） | VolunteerService.java:2838-2856 |
| 6 | JWT 启动期断言 secret 非空 ≥32 字符 fail-fast | JwtUtil.java:16-26 |
| 7 | 限流 INCR+EXPIRE 改 Lua 原子；ai-ticket 改 GETDEL；AI 并发槽 finally 回滚 | PublicRateLimitInterceptor.java:44-52、VolunteerController.java:384-397,676-691 |
| 8 | 校友超管 role 改 `alumni_admin`，`/admin/**` 只认系统 admin；JWT 加 subjectType | AlumniController.java:118-119 |
| 9 | ClientIpResolver 统一 6 处（4 处盲信 XFF）+ 本地桶 LRU | 新组件 |
| 10 | score-lines 复数系列从 703c456 移植（前端 ScoreLineQuery.vue:121/148 的唯一补齐来源） | 分支移植 |
| 11 | 合规词单源（AiService.BANNED_TERMS 为唯一事实源 + 生成脚本）——监管红线 | shared/compliance-terms.json |
| 12 | 公开端点收敛：/alumni/application/status 限流、/skills/sources 迁 /admin、/volunteer/metrics 加保护 | AlumniController 等 |
| 13 | 死代码第一批（零风险）：6 个空壳引擎 + 重复 sha256Hex | algorithm/ |

### 期 2 · 生成链路绞杀 + 两链路统一（4-6 周，后端核心期）

**前置硬门禁**：golden 快照入库；回测基线在目标环境复跑通过；生产库只读核对完成（policy_rule_config 行数、data_admission_group_line 行数——v7 文档待决事项 4）。

1. `GenerateIdempotency` 共享组件（合并两份指纹+锁+缓存+轮询实现；锁释放改 Lua compare-and-delete；异常策略统一降级不阻断）。
2. 事务收窄：移除 generate() 方法级 `@Transactional`（`:595`），biz_user UPDATE（`:627-632`）+ plan_history INSERT（`:753`）收敛进窄事务；锁等待移出（对齐 PGVS `:54-55` 既有决策；显式决策计数器语义——建议保留 UPDATE 并接受"生成失败计数不回滚"）。
3. `ItemReliabilityEnricher`：合并两套置信度/精确度/供给公式（GZ ×0.45 vs PG ×0.55 权重差异参数化）。
4. GZ 富化 N+1 批量化：`:1680-1699` 与 `:3844-3903` 仿 PGVS hydrateGroupDetails 改 IN 批量；`ScoreLineService.java:359` 回退查询加 LIMIT。
5. Provider 真激活（绞杀核心）：GZ pickGradient 取数原样迁入 `MajorPlusSchoolProvider`（现为无调用方的委派壳），按"候选检索→富化→评分→落库"逐步掏空，每步 golden 快照一致才继续。
6. 行为对齐三项（各配独立回测留档）：PG 接入 CandidateFilterEngine 16 硬规则；enricher 权重透传四字段；ML active 介入点前移到排序前（消除 `MlPredictionService.java:93-109` 事后覆写；缓存命中跳过重复 persistPlanItems）。
7. 梯度定档统一：GZ 接入 reclassifyGradientByProbability 概率带（v7 3.4 遗留项），保留位次区间代码路径 + 配置开关并存一个招生季。
8. 死代码第二批：compareByPreference 死簇（~90 行）；CardKey 僵尸链约 600 行（HANDOVER 已宣布下线）。
9. 缓存名常量登记；清 3 个死缓存 TTL；手写 key 分层命名。
10. VolunteerService 门面化（终态 < 1500 行）+ legacy-route 开关（默认新路径，保留一个招生季后删）。

**验证**：golden 快照逐 Stage diff 为空（迁移类）；回测 hitRate ≥0.9269 / Brier ≤0.0167；并发生成 Hikari active 峰值压测对比。

**回滚**：绞杀迁移均委派式，revert 单 commit 即回旧路径；行为对齐项有配置开关分省灰度。

### 期 3 · Web/App 收敛 + 管理端拆分（3-4 周，可与期 2 后半并行）

1. web 省份口径后端驱动（照抄 app 已趟平的 primaryBatch 模式）；修复 GX/YN/HA 45 vs 40/48；8 分支 switch 改模板串。
2. DISCLAIMER_VERSION 运行时下发 + 专用错误码 + 引导（替代三端硬编码）。
3. pnpm workspace 共享包四层切分：compliance → province → protocol（以 app 两段式类型为基准，web 676 行大杂烩淘汰）→ api（请求适配层各自保留，`gzly-app/src/utils/request.ts` 已证明可同构）。
4. AdminController 拆分（按 MlAdminController 既有样板）：AdminAuth / AdminStats / OfficialLinks（逻辑下沉 service + 内存分页 SQL 化）/ Announcement / AdminQa；旧路由委派一版；mapper 直注全下沉。
5. 注解式鉴权 `@RequireRole` + 端点×角色矩阵测试（扫描路由断言注解存在，杜绝新增端点裸奔）。
6. 巨型视图减重：六视图 scoped 样式外移（VolunteerForm 3323 行中 style 占 2061 行）；composable 抽取（useBatchSupport / useRankCheck / parseSseData 三处协议解析合一）；useAdminListPage 消灭 8 处分页三写法。
7. 前端卫生：路由守卫补 /admin/** 与 /alumni/manage；删死 store（user.ts/admin.ts）；AdminLayout 裸 axios 收敛；UniversityDetail 的 dev-only /cdn-proxy 改后端代理；Dashboard 假趋势图处理；app ai.vue 硬编码三问接 suggested-questions。

### 期 4 · 运维 / 数据 / 文档治理（持续）

1. 迁移版本化：Flyway（baseline 纳管 35 个手工 SQL）或 schema_version 表 + deploy 前置检查——把"SQL 先于 JAR"从文档纪律变脚本闸门。
2. nginx 配置入库（当前 `git ls-files | grep nginx` = 0）；systemd 降权（User=root → 专用用户）。
3. 仓库止血：55MB SQL 快照出库；316MB 历史重写（filter-repo）单独评估不执行。
4. FULL 就绪态接真实来源（官方数据完整度 + 回测阈值，`ProvinceReadinessService.java:231` 不可达分支），收敛双就绪体系。
5. 文档终态：HANDOVER 八省口径、README 测试数、DEV_PROGRESS 归档、ANDROID_ARCHITECTURE 加 SUPERSEDED、APP_UNIAPP_PLAN 状态改"已实施（部分）"。
6. 分支处置终局（按期 0 的 BRANCH_DISPOSITIONS.md 执行）；生产 IP 参数化、sshpass 改 SSH key。

---

## 五、风险与前置条件

**前置条件**：

- 期 0：工作区四端测试全绿（不绿先修红，修复单独成 commit）；远端 origin 可用。
- 期 2：golden 快照就位；本地 Docker 回测环境复跑基线通过；生产库只读核对完成。
- 期 3：期 2 共享组件落地；产品确认省份参数口径与排序模式去留。
- 期 4：团队协调窗口。

**主要风险**：

| 风险 | 等级 | 缓解 |
|---|---|---|
| 提交把坏状态固化进 main | 高 | 主题化分批 + 每批测试门禁 + CI 即刻上岗 |
| VolunteerService 分解回归 | 高 | golden 快照逐 Stage diff + 回测基线 + legacy-route 开关 + 每 Stage 独立 commit |
| 行为对齐项改变线上输出（PG 接硬规则后结果会变） | 高 | 这是有意的契约修复：差异逐条列出 + 回测确认方向 + 配置开关分省灰度 |
| alumni 角色变更后存量 token 仍有效（8h 过期） | 中 | 评估 JWT secret 一次性轮换配合上线窗口 |
| golden 快照对数据敏感 | 中 | 双轨：CI mock 版 + Docker 种子全量版手动门禁 |
| 落盘期间并行开发冲突 | 高 | 落盘窗口冻结其他改动 1-2 周；此后每日提交纪律 |
| 单人/小团队产能 | 高 | 每期自成闭环可暂停；期 1 后任意时点停下系统均可发布 |

---

## 六、明确不做的事

1. **不做微服务、不拆库、不引入消息队列/独立网关**——ADR 已决策（单机 40GB 根分区、无运维底座、季节性个位数在线），触发条件见 ADR 2.4。
2. **不做大爆炸重写 VolunteerService**——坚持绞杀者模式（v7 文档 §4.1.4 同一结论）。
3. **不做 VolunteerItem 列表-详情分离与 /api/app/v1 契约层**——破坏性契约变更需与 App 发版协同，期 3 共享包落地后再评估。
4. **不动任何算法数值口径**——算法演进一律走 v7 文档的回测门禁流程，与结构重构分离。
5. **不做 git 历史重写**——只做"新数据不再入库"的增量止血。
6. **不冻结、不合并 gzly-app 到 web**——app 的架构决策（后端驱动省份参数、请求头凭证、两段式类型）优于 web，方向是反向输出。
7. **不在期 0 提交时顺手修 bug**——期 0 提交已验证状态；修复从期 1 开始逐项独立 commit（保证可单独 revert 与归因）。
8. **不在重构期间扩新省**——扩省走数据线（湖北五年数据最优先），回测达标才 ESTIMATE→FULL。
9. **不把 school-photos 当垃圾删除**——对抗核查已证伪，运行时必要资产。
10. **不做 UI 视觉重构**——拆视图只动结构与逻辑，不动视觉。

---

## 附：证据索引（关键 blocker）

| 结论 | 证据 | 核查状态 |
|---|---|---|
| 整月重构未提交 + 三套平行就绪实现 | git diff = 87 files +5730/-2305；208 untracked；`origin/feat/pre-official-readiness-deploy` 另有 BatchSupportService/DataYearReadinessService | 对抗核查 confirmed |
| main HEAD 前后端契约断裂 | volunteer.ts:199-213 调 batch-support；scoreLine.ts:103-108 调 score-lines 系列；HEAD 后端零命中 | 对抗核查 confirmed（补正：score-lines 工作树亦无实现，唯一来源在未合并分支 703c456） |
| gzly-app 零版本保护 | `git ls-files gzly-app` = 0；旧分支有同名 capacitor 废弃实现 | 对抗核查 confirmed |
| 公开页 UGC 媒体被误拦 401 | WebMvcConfig.java:120-121 拦 `/alumni/media/**`；AlumniController.java:202-215 公开设计；UniversityDetail.vue:115 吞 401 | 对抗核查 confirmed |
| GZ 事务包全程 | VolunteerService.java:595 @Transactional；607-620 锁等待在事务内；实际两次 DB 写（627-632 UPDATE + 753 INSERT） | 对抗核查 partial（修正：两次写非一次；无 LazyConnectionDataSourceProxy，doBegin 即占连接，Hikari 20） |
| GZ 指纹缺硬规则字段 | 指纹清单 :2838-2856 vs buildFilterCriteria 消费 :1435-1465（dislikedMajors 等 16 项缺失） | 深读证据，设计稿亲验 |
| ML 特征映射方向写反 | predict.py:89 双跳都查 snake 名；训练侧 snake_case（rank_prediction_model.py:16-49）；Java 发 camelCase（MlPredictionService.java:244-272） | 深读证据，风险稿亲验（核查 agent 因 API 流错误未完成，此条以两份独立深读+设计稿亲验为据） |
| school-photos 是必要资产 | school-photos.ts:18 运行时按需加载；UniversityDetail.vue / alumni/Manage.vue 引用 | 对抗核查修正（原"垃圾剔除"建议被推翻） |
