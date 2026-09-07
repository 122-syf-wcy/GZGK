# GZLY 远端分支处置决策（期 0 落盘后清理）

> 生成时间 2026-09-07，基于 main 落盘后状态（基准 commit `b67d301`，"ci+docs: 最小 CI 四端门禁 + v7 文档状态行对齐落盘事实"）。
> 基准说明：本地 main（`b67d301`）领先 `origin/main`（`79ef7a3`）9 个提交，包含 v7 多省算法重构全部成果（RecommendationOrchestrator 统一入口、ProvinceReadinessService 四态就绪门禁、ProvinceBatchSupportController、回测闭环）。本文所有 ahead/behind/diff 数字均以本地 main `b67d301` 为基准，命令在 2026-09-07 于 F:\GZGK 仓库执行。
>
> 实际核查发现远端未合并分支共 **9 条**（任务清单列了 8 条，`git branch -r` 额外发现 `origin/fix/exception-handler-noise`，与 3 条 pre-official 分支同源，一并纳入处置）。
>
> 处置分类：**merged**（已并入，可删）/ **superseded**（被新实现取代，可删）/ **keep-for-reference**（含可移植代码，保留并注明提取项）。本文档只做决策，不执行删除。

---

## 1. origin/chore/snapshot-main-wip-20260515

| 项目 | 数据 |
|---|---|
| 分支 tip | `e48b037`（2026-07-17，最晚活跃的分支） |
| 领先 main | 48 commits |
| 落后 main | 12 commits |
| 差异规模 | 493 files changed, +115,488 / -28,828 |
| 独有新增文件 | 376 个（gzly-server 126、gzly-app 107、scripts 45+37、gzly-web 31、migration 6、docs 7、ml-service 3、tmp 图片若干） |

**证据命令**：`git log main..origin/chore/snapshot-main-wip-20260515 --oneline | wc -l` = 48；`git diff main...origin/chore/snapshot-main-wip-20260515 --stat | tail -1` = 493 files。

**分支内容**：2026-05-15 起从 `eca5569` 分叉的完整平行主线，一路迭代到 v7.58（6 月中旬），提交含 v7.40–v7.58 全系列（R1–R7 修复、engine-matrix 50 批次路由、SC/AH 多省扩展、AI 问答、专业规划师、多省分数线、运维后台）。tip 三个提交（`00df760`/`a545e38`/`e48b037`）是把当时生产环境工作区整体快照上传。

**与 main 现状的关系（逐项验证）**：

- **架构整体已被 main 取代**：snapshot 没有 `RecommendationOrchestrator` / `ProvinceReadinessService`（`git ls-tree` 确认为空），其 `VolunteerService` 为旧单体（+1198 行大改 vs main），readiness 用的是已被取代的 `DataYearReadinessService`/`BatchSupportService`/`AdmissionYearService` 那一套。**但大量功能 main 尚未实现**，见提取清单。
- **先验信息验证结果**：提交 `703c456` 确认含 `ScoreLinesController.java`（93 行，路由 `/score-lines/{provinceCode}` + `/capability` + `/control-lines` + `/score-rank` + `/admission-lines` + `/major-group-lines` + `/major-score-lines` + `/art-sport-lines`）及 `service/scoreline/` 4 文件（ProvinceScoreLineAdapter / AdapterRegistry / ProvinceScoreLineService / ScoreLineModels）。main 现状：`gzly-web/src/api/scoreLine.ts:103/108` 调用 `/score-lines/{provinceCode}/capability` 与 `/score-lines/{provinceCode}/{type}`，`gzly-web/src/views/ScoreLineQuery.vue:123/150` 是唯一消费方；main 的 `ScoreLineController` 只有 `/score-line`（单数）系列 6 个端点，全仓 grep `score-lines` 在 server 端仅命中 `AdminController.java:822` 的 `/admin/score-lines`（管理端，非该前端路由）。**确认：main 前端存在调用、后端无实现，snapshot 是唯一实现来源，先验成立。** （注：任务给的行号 121/148，实测当前为 123/150，内容一致。）
- **先验信息验证结果（capacitor app）**：确认。snapshot 的 `gzly-app/` 是 uni-app + Capacitor 混合实现（`package.json` 同时含 `@capacitor/android` 和 uni-app 依赖，另有 `android/` 目录 + `capacitor.config.json`，107 个独有文件）；main 的 `gzly-app/` 是纯 uni-app 重写（v0.1.0，无 capacitor 依赖）。capacitor 部分判定废弃，不提取。
- main 已有的部分：`RecommendationOrchestrator`、`ProvinceReadinessService`、`ProvinceBatchSupportController`（180 行精简版）、`RecommendationOrchestrator` 调用链（VolunteerController:69 `generateWithPolicy`）、CI 门禁。

**处置建议：keep-for-reference**（期 1 移植来源，体量最大）

**理由**：这是 2026 年 5-6 月实际跑在生产上的完整旧主线，main 的期 0 基线只落了贵州单省 + 架构骨架，snapshot 里的多省引擎矩阵、AI 问答、专业规划师等整块功能 main 均无对应实现（`ls` main 的 `service/recommend/` 目录为空、无 AiQa* 控制器、web 无 AiChat/MajorPlanner 视图）。删除即永久丢失 48 个提交的功能代码。

**期 1 提取清单**（按优先级）：

1. **P0 分数线多省查询（前端已在调用，属断链修复）**：`gzly-server/src/main/java/com/gzly/controller/ScoreLinesController.java` + `gzly-server/src/main/java/com/gzly/service/scoreline/`（4 文件：ProvinceScoreLineAdapter、ProvinceScoreLineAdapterRegistry、ProvinceScoreLineService、ScoreLineModels）——main 的 `gzly-web/src/views/ScoreLineQuery.vue` + `api/scoreLine.ts` 依赖的 `/score-lines/{provinceCode}/*` 端点唯一实现。
2. **P1 多省推荐引擎矩阵**：`service/recommend/` 整目录（18 文件：RecommendEngine、RecommendEngineRouter、ProvinceBatchEngineMatrix、Anhui/Sichuan 系列引擎、EarlyCParallelMajorEngine 等）+ `service/{Anhui,Sichuan,Hubei,NextProvince}BatchSupportService` + `AnhuiCompositeScoreCalculator`/`SichuanCompositeScoreCalculator` + `BatchRuleRegistry` 系列 + `ProvincePlanDecorator`。移植时需适配 main 的 RecommendationOrchestrator 架构（snapshot 是旧 VolunteerService 单体路由）。
3. **P1 AI 问答 + 专业规划师**：`AiQaController` + `service/AiQa*.java`（829 行主服务 + ChatClient/ContentParser/RegionRegistry/SessionCode/CallLog）+ `MajorPlannerController` + `MajorPlannerService`/`MajorPlannerCodeService` + web 端 `AiChat.vue`/`AiChatDrafts.vue`/`MajorPlanner.vue`/`MajorPlannerResult.vue` + 管理端 `AiQaSessions.vue`。
4. **P2 四段推荐理由（R6）**：snapshot `VolunteerService.java:2888` 的 `buildRecommendReason`（梯度定位/适配理由/风险信号/可调节项四段式，static 方法便于提取）+ `buildFitSegment`/`buildRiskSegment`。main 现有的 `buildRecommendReason`（VolunteerService:2502）是旧版多条件拼接，非四段结构。
5. **P2 合规/安全运维后台**：`AdminOpsController`/`AdminAiLockController`/`AdminAiOpsController`/`AdminDataReviewController`/`AdminFeedbackOpsController`/`AdminServerSecurityController` + `CredentialAttemptLimiter`/`SecurityAuditCounterService`/`DataReviewWorkbenchService` + web 管理端 `Ops.vue`/`ServerSecurity.vue`/`CqGsXjDataReview.vue`/`AiQaSessions.vue`。
6. **P2 文档与运维手册**：`docs/ops/anhui_baseline_runbook.md`、`docs/ops/sichuan_ml_baseline_runbook.md`、`docs/2026_gz_pre_launch_audit.md`、`docs/2026_sc_go_live_checklist.md`、`docs/2026_sc_onboarding_plan.md`、`docs/2026_sc_pre_launch_audit.md`，以及 `DEV_PROGRESS.md` 的 v7.40–v7.58 段落（main 的 DEV_PROGRESS 停在 2026-04-30，snapshot 版多出 +1135 行 5-6 月过程记录）与 `HANDOVER.md`（+155 行）。
7. **P2 ml-service**：`scripts/build_training_csv_sc.py`、`scripts/build_training_csv_ah.py`（川/皖训练集构建管线，main 只有贵州的 `build_training_csv.py`）+ `tests/test_build_training_csv_ah.py`。
8. **不提取（废弃）**：`gzly-app/android/` + `capacitor.config.json`（旧 capacitor 容器，main 已用纯 uni-app 重写）、`tmp/`/`tmp_sichuan_ocr/` 图片素材（四川 OCR 原始图，无代码价值，如需可另行归档）、旧版 `AdmissionYearService`/`BatchSupportService`/`DataYearReadinessService`（见第 6/7/9 节，均被 main 取代）。

---

## 2. origin/codex/gzly-pre-release-archive-20260607

| 项目 | 数据 |
|---|---|
| 分支 tip | `d5968f3`（2026-06-09） |
| 领先 main | 2 commits（`2d9f6a1` + `d5968f3`） |
| 落后 main | 10 commits |
| 差异规模 | 51 files changed, +6,642 / -4,057 |
| 独有新增文件 | 16 个 |

**证据命令**：`git log main..origin/codex/gzly-pre-release-archive-20260607 --oneline` = 2 行；`git diff main...origin/... --stat | tail -1` = 51 files。

**分支内容**：在 `2d9f6a1`（=分支 3 的同一提交，见下节）之上追加 `d5968f3`"archive pre-release guidance and ops dashboards"——`UserFeedbackDialog.vue`（333 行用户反馈弹窗组件）、VolunteerForm 大改（+1045 行，含 SC 11 类/AH 7 类艺术统考类别、综合分实时计算接入）、Dashboard/ImportJobs 管理端增强、volunteer store/types 扩展。

**与 main 现状的关系**：

- `2d9f6a1` 部分同分支 3（平行实现，见下）。
- `d5968f3` 独有内容 main 均无：`UserFeedbackDialog.vue`（main `git ls-tree` grep 计数 0）、SC/AH 综合分接口接入（main 的 `api/volunteer.ts` grep `composite` 为空、server 端 controller grep `composite` 为空）、艺术统考类别选择（main VolunteerForm grep 艺术/统考 仅 1 处文案命中）。这些依赖 `2d9f6a1` 的平行后端（SC/AH composite 端点），单独移植价值受限。
- `git branch -r --contains d5968f3` 仅本分支，未进 main。

**处置建议：keep-for-reference**（低优先级，或作为分支 3 的附属一并保留）

**理由**：主体（`2d9f6a1`）是平行实现，但 `UserFeedbackDialog.vue` 是纯前端组件（仅依赖 feedback 提交接口，main 已有 `FeedbackController`）可直接移植；VolunteerForm 的预官方引导文案/批次支持矩阵渲染若期 1 做多省批次支持，可作 UI 参考。若期 1 明确不做 SC/AH 艺术类，可降级为 superseded 删。

**提取清单**：

1. `gzly-web/src/components/UserFeedbackDialog.vue`（333 行，配套 main 已有的 `submitFeedback` API 即可工作）。
2. `d5968f3` 中 VolunteerForm 的批次支持矩阵渲染段（若期 1 实现多省 batch-support UI 时参考）。
3. 不提取：`DataReadinessService`/`ProvinceAlgorithmPolicyService`/`AdminImportJobService`（平行实现，main 已有 ProvinceReadinessService 体系）、艺术统考类别与综合分前端接入（依赖分支 3 的平行后端端点，main 无 SC/AH composite 服务）。

---

## 3. origin/codex/gzly-professional-volunteer-table-export-20260529

| 项目 | 数据 |
|---|---|
| 分支 tip | `2d9f6a1`（2026-05-29） |
| 领先 main | 1 commit（`2d9f6a1`） |
| 落后 main | 10 commits |
| 差异规模 | 46 files changed, +4,781 / -4,061 |
| 独有新增文件 | 15 个 |

**证据命令**：`git log main..origin/... --oneline` = 1 行；`git show 2d9f6a1 --stat`（46 文件：server 15 + test 14 + web 17）。

**分支内容**："professional volunteer table and export support"——`ProvinceBatchSupportController`（380 行版，含 `/batch-support/{provinceCode}` 双路由与 supportReason/missingData 输出）、`AdminImportJobService`（401 行，真实文件导入：sha256 逐文件校验 + staging manifest 生成）、`DataReadinessService`（188 行，基于 `data_year_readiness` 表的 SQL 就绪查询）、`ProvinceAlgorithmPolicyService`（225 行）、VolunteerService 大改（-590 行简化）、web 端专业志愿表导出（VolunteerResult 126 处 professional 标记 vs main 9 处）、5 个 VolunteerResult* 数据防伪测试。

**与 main 现状的关系（平行实现验证）**：

- `ProvinceBatchSupportController`：main 已有同名同路径文件（180 行 vs 分支 380 行），main 版是 v7 重构精简版，路由 `/volunteer/{provinceCode}/batch-support`。分支版多一条 `/volunteer/batch-support/{provinceCode}` 别名路由和 supportReason/missingData 字段。**整体取代，局部字段可选移植。**
- `AdminImportJobService`：与 main 的 `OfficialImportJobService`（575 行）职责相同。关键差异：main 版是 dry-run 骨架（grep sha256/Files/InputStream 为空），分支版有真实文件操作（Files.list/sha256/staging_manifest.json 落盘）。但注意 main 期 0 的定位就是 dry-run 门禁（runStaging 直接置 STAGING_READY 不碰文件），真实导入属期 1+ 范围。
- `DataReadinessService` / `ProvinceAlgorithmPolicyService`：main 的 `ProvinceReadinessService`（四态 LOCKED/QUERY_ONLY/ESTIMATE/FULL，按真实行数实时判定 + 60s 缓存）与 `ProvincePolicyService`/`ProvinceReadinessService` 体系已覆盖职责。**取代。**
- 5 个 VolunteerResult* 防伪测试（NoFake2026/HistoricalScore/MajorCode/ProfessionalFields）：main 测试目录无对应文件，但这些测试断言的是分支版 VolunteerService 的行为，需适配后才有价值。
- web 专业志愿表导出：main 的 VolunteerResult 已有基础导出（exportExcel/export-long-image，VolunteerExportController），分支版多"专业志愿表命名导出 + 人工核验草稿"。已部分覆盖。

**处置建议：keep-for-reference**（窄保留，仅 2 项提取价值）

**理由**：架构上已被 main 的 v7 体系取代（直接合并会回退重构），但含两块 main 缺失的实用代码。若期 1 不做真实文件导入，可降级 superseded。

**提取清单**：

1. `AdminImportJobService.java`（分支版）+ `AdminImportJobController` + 4 个 entity/mapper + `db/20260515_admin_import_job_mvp.sql`——真实文件导入（sha256 校验、staging manifest、短事务模式防行锁），main 的 OfficialImportJobService 是 dry-run 骨架，期 1 做真实导入时这是现成底子（与第 9 节 fix 分支的版本二选一，见汇总）。
2. 5 个 `VolunteerResult*Test`（NoFake2026 防伪造 2026 数据断言、HistoricalScore、MajorCode、ProfessionalFields）——防伪测试思路可适配 main 的 VolunteerService。
3. 不提取：DataReadinessService、ProvinceAlgorithmPolicyService（main 已有更优实现）、380 行版 ProvinceBatchSupportController 整体（main 已有，若需要 supportReason/missingData 字段可做小增量）。

---

## 4. origin/codex/gzly-ui-polish-20260528

| 项目 | 数据 |
|---|---|
| 分支 tip | `cedb9ab`（2026-05-28） |
| 领先 main | 0 commits |
| 落后 main | 9 commits |
| 差异规模 | 0 files（`git diff main...origin/... --stat` 输出为空） |
| 独有新增文件 | 0 个 |

**证据命令**：`git log main..origin/codex/gzly-ui-polish-20260528 --oneline | wc -l` = 0；`git diff main...origin/... --name-only | wc -l` = 0；`git branch -r --contains cedb9ab` 含 origin/main；main 历史 `79ef7a3 Merge pull request #1 from 122-syf-wcy/codex/gzly-ui-polish-20260528`。

**与 main 现状的关系**：先验信息验证成立——已通过 PR #1 完整合并进 main（merge commit `79ef7a3` 就在 main 历史上），tip `cedb9ab` 是 main 祖先，三点 diff 为空，无任何独有内容。

**处置建议：merged（可删）**

**理由**：tip 是 main 的直接祖先（behind=9 即 main 在其上前进了 9 个提交），无领先提交、无独有文件。远端删除后仍可从 main 历史追溯（`git log --grep="polish GZLY"` 可达）。**第一批删除。**

---

## 5. origin/copilot/merge-branch

| 项目 | 数据 |
|---|---|
| 分支 tip | `79ef7a3`（2026-06-02） |
| 领先 main | 0 commits |
| 落后 main | 8 commits |
| 差异规模 | 0 files（`git diff main...origin/copilot/merge-branch --stat` 为空；注意 `git diff main origin/copilot/merge-branch` 两点比较显示 296 files 差异是方向反了——是 main 比 branch 多出的期 0 内容） |
| 独有新增文件 | 0 个 |

**证据命令**：`git rev-parse origin/copilot/merge-branch` = `79ef7a3...`，与 `origin/main` 完全同 SHA；`git log main..origin/copilot/merge-branch | wc -l` = 0。

**与 main 现状的关系**：tip 就是 `origin/main` 的当前 tip（PR #1 的 merge commit 本身），是 GitHub 上对 main 的一个别名引用（典型成因：用 copilot 机器人在 web 界面点了 merge，产生同名分支）。零独有内容。

**处置建议：merged（可删）**

**理由**：与 origin/main 指向同一 commit，删除无任何损失。**第一批删除。**

---

## 6. origin/feat/admin-2026-import-job

| 项目 | 数据 |
|---|---|
| 分支 tip | `b419b37`（2026-05-28，GitHub 创建的 merge commit） |
| 领先 main | 1 commit（即 `b419b37` 本身） |
| 落后 main | 9 commits |
| 差异规模 | 0 files（`git diff main...origin/feat/admin-2026-import-job --name-only | wc -l` = 0） |
| 独有新增文件 | 0 个 |

**证据命令**：`git cat-file -p b419b37` 显示双 parent（`f84f28f` + `cedb9ab`），即把 ui-polish 的 `cedb9ab` 合入 `f84f28f`；`git diff cedb9ab b419b37 --stat` 为空（merge 结果与 `cedb9ab` 树完全一致，无冲突解决内容）；`git diff main b419b37 --name-only --diff-filter=A` 为空。

**与 main 现状的关系**：`b419b37` 是"Merge PR #2 from codex/gzly-ui-polish-20260528 into feat/admin-2026-import-job"，但合并没引入任何新内容（树等于被合分支），而 `cedb9ab` 又已通过 PR #1 进了 main。分支名暗示的 admin import job 主体工作（`f84f28f` "snapshot OfficialImportJob WIP"）本身就在 main 历史里（main 的 `OfficialImportJobService`/`OfficialImportJobAdminController` 即其后续演化）。零独有内容。

**处置建议：merged（可删）**

**理由**：唯一"领先"的 commit 是空 merge（树无变化），全部实质内容已在 main。**第一批删除。**

---

## 7. origin/feat/pre-official-data-baseline

| 项目 | 数据 |
|---|---|
| 分支 tip | `8e5f848`（2026-05-16） |
| 领先 main | 1 commit |
| 落后 main | 12 commits |
| 差异规模 | 10 files changed, +1,683 |
| 独有新增文件 | 6 个 |

**证据命令**：`git show 8e5f848 --stat`；独有文件列表：`AdmissionYearService.java`（244 行）、`BatchRuleRegistry.java`、`BatchSupportService.java`、`db/20260512_data_year_readiness.sql`（60 行）、`AdmissionYearServiceTest.java`、`BatchSupportServiceTest.java`（347 行）。

**与 main 现状的关系**：先验信息验证成立——提交信息自述"本 worktree 是 feat/pre-official-data-baseline 分支的未提交快照……这些文件在 fix/exception-handler-noise 主线上已以 BatchSupportService.java、DataYearReadinessService.java 等名称落地并上线；本快照只是保留参考原型，同样没打算合入 main"。这是**第三套**平行就绪实现（AdmissionYearService），main 已有 `ProvinceReadinessService`（真实行数实时判定 + 四态），且该分支的 `BatchSupportService`/`BatchRuleRegistry` 在 fix 分支和 snapshot 分支上都有后续演化版本（本分支反而是最旧的原型）。

**处置建议：superseded（可删）**

**理由**：被 main 的 ProvinceReadinessService 体系取代，且同内容在其后所有分支（readiness-deploy / fix / snapshot）中均有更新版本，本分支是最老的一版原型。其 347 行 BatchSupportServiceTest 的最终演化版在 fix 分支上也有一份（更新的）。删除无损失。**第二批删除（建议在确认 snapshot 分支提取清单后再删，因其测试与 fix 分支版本同源）。**

---

## 8. origin/feat/pre-official-readiness-deploy

| 项目 | 数据 |
|---|---|
| 分支 tip | `2384e8d`（2026-05-15） |
| 领先 main | 10 commits |
| 落后 main | 12 commits |
| 差异规模 | 43 files changed, +6,046 / -34 |
| 独有新增文件 | 26 个 |

**证据命令**：`git log main..origin/feat/pre-official-readiness-deploy --oneline` = 10 行；独有文件含 `BatchSupportService.java`、`DataYearReadinessService.java`、`DataYearReadinessAdminController.java`、`AdmissionYearService.java`、`BatchRuleRegistry.java`、web 管理端 `DataYearReadiness.vue`（656 行）/`ImportJobs.vue`（358 行）等；tip 提交 `2384e8d` 自述"snapshot AdminImportJob MVP that is already deployed…byte-for-byte identical to the classes inside /opt/gzly/backend/app.jar"。

**与 main 现状的关系**：先验信息验证成立——`BatchSupportService`/`DataYearReadinessService` 是平行就绪实现（基于 `data_year_readiness` 人工维护表 + SQL 查询），main 的 `ProvinceReadinessService`（真实行数实时判定、四态、60s 缓存）已取代。AdminImportJob MVP 与 main 的 OfficialImportJob* 体系职责重叠（main 为 dry-run 骨架，但那是期 0 定位）。该分支 tip 被 `fix/exception-handler-noise` 完全包含（`git merge-base --is-ancestor` 确认 ancestor），即其全部内容的最新版都在 fix 分支上。

**处置建议：superseded（可删）**

**理由**：全部内容被 fix 分支包含（fix = 本分支 + 5 个后续修复提交），且核心就绪服务被 main 的 ProvinceReadinessService 取代。删除后若需考古，fix 分支上有一模一样的文件。**第二批删除（需与 fix 分支联动决策，见下节）。**

---

## 9. origin/fix/exception-handler-noise（任务清单外发现，一并处置）

| 项目 | 数据 |
|---|---|
| 分支 tip | `3a7cb12`（2026-05-15，即 2026-05-15 20:09 部署的那版生产代码） |
| 领先 main | 15 commits |
| 落后 main | 12 commits |
| 差异规模 | 46 files changed, +6,425 / -35 |
| 独有新增文件 | 27 个（= readiness-deploy 的 26 个 + `GlobalExceptionHandlerTest.java`） |

**证据命令**：`git log main..origin/fix/exception-handler-noise --oneline` = 15 行；`git merge-base --is-ancestor origin/feat/pre-official-readiness-deploy origin/fix/exception-handler-noise` → ancestor 成立。

**与 main 现状的关系**：本分支 = readiness-deploy 的 10 个提交 + 5 个生产修复提交：

1. `419d45c` **GlobalExceptionHandler 降噪**（NoResourceFoundException 扫描器探测记 INFO、ClientAbortException/AsyncRequestNotUsableException 客户端断开静默）——**main 未包含**（main 的 GlobalExceptionHandler 72 行版 grep NoResourceFound/ClientAbort/AsyncRequestNotUsable 计数 0，仍是全走 handleGeneral 记 ERROR 的旧版）。**这是 main 缺失的、可直接移植的独立小修复。**
2. `1f1f9d1` + `bf3caa7` **AdminImportJob 事务边界修复**（generateStagingDryRun/runQualityCheck/generateFormalSql/generateRollbackPlan 去掉类级 @Transactional 防 sha256 持锁超时）——main 的 `OfficialImportJobService` 仍是方法级 `@Transactional`（grep 确认 runQualityCheck/generateFormalSql/generateRollbackPlan 均 @Transactional），但 main 版不做文件 IO（dry-run 骨架），锁风险不存在；该修复应随期 1 真实导入移植（含在 AdminImportJobService 分支版的注释与结构里）。
3. `454c9e6` `/admin/data-year-readiness/refresh` 自动校准——依赖 `data_year_readiness` 表（人工维护体系），main 的 ProvinceReadinessService 本身就是实时判行数、无表可刷新，**已被架构性取代**。
4. `3a7cb12` ml chance_score 弱监督泄漏列剔除——main 的 `chance_score_model.py` 已有**更强版本**：`_WEAK_LABEL_LEAKAGE_COLS` + `_WEAK_LABEL_SAFE_FEATURES` 白名单 + 默认 `blocked_weak_label` 拒绝训练（83a8ddf "弱标签防泄漏门禁"已在 main）。**取代。**

**处置建议：keep-for-reference**（单一提取项：GlobalExceptionHandler 降噪补丁）

**理由**：作为 readiness-deploy 的超集 + 生产修复，其 95% 内容被 main 取代或被 snapshot 分支更好的版本取代；但 `419d45c` 的异常降噪是 main 至今缺失的独立小修复（约 20 行 + 测试 `GlobalExceptionHandlerTest.java`），删除会丢。若决定把该补丁先行移植进 main，则本分支即可转 superseded 删除。

**提取清单**：

1. `419d45c` 补丁：`GlobalExceptionHandler.java` 的 `handleNoResourceFound`（NoResourceFoundException→404+INFO）与 `handleClientAbort`（ClientAbort/AsyncRequestNotUsable→静默 DEBUG）两个方法 + `gzly-server/src/test/java/com/gzly/common/exception/GlobalExceptionHandlerTest.java`（main 的 `test/java/com/gzly/common/` 目录当前为空，测试可直接落位）。
2. 随期 1 真实导入参考：本分支版 `AdminImportJobService.java`（673 行，含短事务注释）——与分支 3 的 401 行版对比，本版含 `454c9e6` 前的全部生产修复，**二选一时取本版**（分支 3 的 401 行版是其被 archive 分支精简后的变体，少了 tx 修复；但 archive 分支版本另含 8 处 jdbcTemplate 风格差异，未验证哪个更好，标注未验证）。
3. 不提取：DataYearReadinessAdminController/BatchSupportService/AdmissionYearService/DataYearReadiness.vue/ImportJobs.vue（readiness 平行体系，main 已有 ProvinceReadinessService + OfficialImportJobAdminController + web admin ImportJobs.vue）、`454c9e6` refresh 端点（架构性取代）、`3a7cb12` ml 修复（main 有更强门禁版）。

---

## 建议删除顺序汇总表

| 顺序 | 分支 | 处置 | 领先/落后 main | 关键依据 | 前置条件 |
|---|---|---|---|---|---|
| 1 | origin/codex/gzly-ui-polish-20260528 | **merged，立即可删** | 0 / 9 | PR #1（`79ef7a3`）已合并，三点 diff 为空 | 无 |
| 2 | origin/copilot/merge-branch | **merged，立即可删** | 0 / 8 | tip 与 origin/main 同 SHA（`79ef7a3`），纯别名引用 | 无 |
| 3 | origin/feat/admin-2026-import-job | **merged，立即可删** | 1 / 9 | 唯一领先 commit `b419b37` 是空 merge（树=cedb9ab），实质内容均已在 main | 无 |
| 4 | origin/feat/pre-official-data-baseline | **superseded，第二批删** | 1 / 12 | AdmissionYearService 第三套平行就绪原型，被 ProvinceReadinessService 取代；后续版本存在于 fix/snapshot 分支 | 无（其测试演化版在 fix 分支有副本） |
| 5 | origin/feat/pre-official-readiness-deploy | **superseded，第二批删** | 10 / 12 | 全部内容被 fix/exception-handler-noise 包含（ancestor 验证成立），就绪服务被 main 取代 | 无（考古可用 fix 分支） |
| 6 | origin/fix/exception-handler-noise | **keep-for-reference（条件可删）** | 15 / 12 | 含 main 缺失的 GlobalExceptionHandler 降噪补丁（`419d45c` + 测试）；其余被取代 | 提取清单第 1 项移植进 main 后可转 superseded 删除 |
| 7 | origin/codex/gzly-professional-volunteer-table-export-20260529 | **keep-for-reference** | 1 / 10 | 真实文件导入 AdminImportJobService 底子 + 5 个防伪测试，main 的导入是 dry-run 骨架 | 期 1 决定导入方案后处置（与 fix 分支的 AdminImportJobService 二选一） |
| 8 | origin/codex/gzly-pre-release-archive-20260607 | **keep-for-reference** | 2 / 10 | UserFeedbackDialog 组件可独立移植；其余依赖平行后端 | 期 1 决定 SC/AH 艺术类范围后可降级 superseded |
| 9 | origin/chore/snapshot-main-wip-20260515 | **keep-for-reference（最高优先保留）** | 48 / 12 | ScoreLinesController 是 main 前端断链的唯一修复来源；多省引擎/AI 问答/专业规划师等整块功能仅存于此 | 期 1 提取清单全部移植完并验证后，方可评估删除 |

**执行提醒**：

- 删除命令模板（本文档不执行）：`git push origin --delete <branch-name>`。
- 第 1-3 批删除零风险（已验证零独有内容）；第 4-5 批建议在 snapshot 分支提取清单至少完成 P0（分数线多省查询）移植后再执行，避免考古链断裂。
- snapshot 分支建议同时打 tag 固定（如 `archive/snapshot-v7.58`）再删远端分支，48 个提交的生产遗产值得 tag 级保护（建议，未执行）。

## 验证方式备注

本文所有数字与断言的取证命令（在 F:\GZGK、main=`b67d301` 下执行）：

- 领先/落后：`git log main..origin/<b> --oneline | wc -l` / `git log origin/<b>..main --oneline | wc -l`
- 差异规模：`git diff main...origin/<b> --stat | tail -1`；独有文件：`git diff main...origin/<b> --name-only --diff-filter=A`
- 祖先关系：`git merge-base --is-ancestor`、`git branch -r --contains <sha>`
- main 覆盖度抽查：`git ls-tree main --name-only | grep <pattern>`、工作区 `grep -rn <pattern> gzly-server/`
- 分数线断链：`gzly-web/src/api/scoreLine.ts:103/108` 调 `/score-lines/*`；main server 端 `grep -rn "score-lines" gzly-server/src/main/java/` 仅 `AdminController.java:822`（`/admin/score-lines`，管理端路由，非前端消费的端点）。

**未验证事项**：分支 3（table-export）的 `AdminImportJobService`（401 行）与 fix 分支版本（673 行）的优劣未做逐行对比（两者同源不同代，见第 9 节第 2 项）；archive 分支 VolunteerForm 的"预官方引导"具体文案未逐条审读（该分支标记为 0 处 guidance 关键词命中，功能定性依据提交信息与结构 diff）。
