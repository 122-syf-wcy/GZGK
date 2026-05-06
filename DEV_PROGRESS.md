# GZLY 开发进度交接

更新时间：2026-04-30 21:27

> 完整历史流水已归档到 `DEV_PROGRESS_ARCHIVE_20260426.md`。本文件只保留当前交接所需信息，后续每轮只追加高价值结论，避免继续膨胀。

## 最新一轮变更（v7.37，2026-04-30 21:27）

### 贵州推荐算法按研究报告优化并部署

- 参照《贵州省高考志愿辅助系统推荐算法与梯度规则优化研究报告》，贵州 `MAJOR_96` 生成链路继续保持“规则前置、概率后置、列表安全校验”：先按专业级候选、特殊类型排除、官方选科要求、民办/中外合作预算约束过滤，再做概率、计划、精度和排序增强。
- `AlgorithmService.calcProbability` 从单一位次分布概率改为“位次分布 + 经验命中率 Beta 平滑 + 波动惩罚”，降低 2024/2025 少样本下过度自信；概率等级新增 `兜底参考`，继续只展示“参考概率”。
- 贵州 96 志愿默认配额改为报告建议比例折算：均衡型 `冲/稳/保/垫=14/43/29/10`，保守型 `10/34/38/14`，冲刺型 `24/38/24/10`；前端文案把 `垫` 明确解释为兜底。
- `PlanMetrics` 新增整表安全度字段：`portfolioSafetyProbability/portfolioSafetyLevel/portfolioSafetyNote/safeTailCount`，基于保/垫区高可信条目的参考概率、风险色、计划趋势和数据置信度做保守组合估算；结果页展示“整表安全”，低于阈值时并入数据质量提醒。
- 验证：后端相关测试 `AlgorithmServiceTest,VolunteerServiceComplianceTest,VolunteerServiceManualReviewTest` 通过；后端全集 `./mvnw -q test` 通过；前端 `npm run build` 通过；相关文件 linter 无错误。
- 生产部署：后端通过 `deploy_backend_safe.sh` 部署成功，旧 JAR 备份为 `/opt/gzly/backend/backup/app.jar.20260430212130.bak`；前端 `dist` 已同步并 `nginx -t && systemctl reload nginx` 成功。
- 生产回归：`/`、`/region/GZ`、`/volunteer?provinceCode=GZ`、`/api/volunteer/metrics` 均返回 `200`；贵州物理 520 分、位次 39000 生成 `96` 条，梯度 `14/43/29/10`，整表安全度 `99.9%`；位次 39123 复测同样返回 `96` 条且配额正确。
- 部署后发现 `recentScoreHistory` 旧 Redis 缓存格式触发读取 WARN，但已由 `RedisConfig` 自动回源；本轮清理 `gzly:v7:recentScoreHistory::*` 旧缓存 `1099` 个，清理后复测最近日志未再出现 `读取缓存失败` / `ERROR` / `Exception`。

## 上一轮变更（v7.36，2026-04-30 20:09）

### 修复张雪峰.skills 对话 15 秒超时

- 线上现象：`/volunteer/ai?planId=112` 底部“张雪峰.skills 填报服务”发送问题后显示 `timeout of 15000ms exceeded`。
- 根因：skills 对话使用前端全局 Axios 15 秒超时；真实模型请求在当前配置下可超过 15 秒，本轮生产实测 planId=112 对话耗时 `62s`。
- 前端修复：`chatZxfSkill` 单独使用 `90s` timeout；全局超时错误改为中文兜底，页面保留用户问题，避免裸露英文错误。
- 后端修复：skills 对话改用压缩版志愿上下文，不再把完整 AI 长报告与完整 96 条重字段上下文一起塞给模型；AI 调用单独设置 `80s` call timeout，并把回复 token 控制在 `1200-1800`。
- 验证：`gzly-web npm run build` 通过；`gzly-server ./mvnw -q test` 通过；已部署后端与前端，Nginx reload 成功。
- 生产回归：服务器内网调用 `/api/volunteer/zhangxuefeng-skills-chat` 使用 planId=112 返回 `code=0`，来源 `Eric-Yibo-Shen/zhangxuefeng-skillset`，回复包含“北京林业大学的计算机类”建议；`/volunteer/ai?planId=112` 与 `/api/volunteer/metrics` 均 `200`，最近日志未见新的 skills 异常。

## 上一轮变更（v7.35，2026-04-30 20:00）

### 四川 2025 数据补齐全量轮次收尾

- 全国院校官网补齐首轮与四川专项学校官网发现均已跑完：`uni_official_link` 覆盖为 `2198/2182/1961/2162/2160/2153`（总数/官网/招生网/章程/专业目录/收费），四川 `school_discovery.csv` `17496` 行、high confidence `409` 行。
- 视觉抽取严格候选 `6` 条、`5` 所学校；多数为首页/查询页或无明确院校专业组结构，自动 normalized 仍为 `group_lines=0`、`group_plans=0`，没有把低置信草稿写入生产。
- 人工复核新余学院官网 `http://zb.xyc.edu.cn/info/1013/4761.htm` 后新增导入 5 条四川 2025 普通本科批B段专业组线；dry-run `validRows=5 rejected=0`，正式 import `inserted=5 rejected=0`。
- 新余学院只公布最低分、无最低位次；后端已用四川官方一分一段换算并显式标记 `rankSourceType=score_rank_converted`，当前 5 条位次为历史类 `528 -> 31015`、物理类 `491 -> 139538`、`507 -> 117870`、`506 -> 119161`。
- 已把新余学院 5 条沉淀回服务器 reviewed CSV：`/root/gzly_scraper/sichuan_2025/reviewed/group_lines_sc_2025_reviewed.csv` 与 `/root/gzly_scraper/province_group_2025/reviewed/SC/group_lines_sc_2025_reviewed.csv`，刷新后 payload manifest `44` 个。
- 当前生产四川 2025：一分一段 `1055` 行；专业组线 `27` 组（历史 `9`、物理 `18`），其中原始位次 `22`、一分一段换算位次 `5`；招生计划 `140` 行 / `21` 组（历史 `7`、物理 `14`）。
- 新余学院招生计划 API 只有专业、科类、计划数，缺院校专业组代码，无法可靠关联 `101/102/103`，继续不写 `data_admission_group_plan`。
- 管理状态仍为 `generationReady=false`：专业组线还缺 `63` 组，专业组计划还缺 `69` 组；四川智能生成继续锁定，不回退贵州。
- 本轮报告已落地：服务器 `/root/gzly_scraper/sichuan_2025/reports/full_round_final_summary_20260430195952.md|json`，本地备份 `tmp/full_round_final_summary_20260430195952.md|json`。

## 上一轮变更（v7.34，2026-04-30 12:53）

### 生产数据补齐全量首轮启动

- 服务器 `uni_official_link` 的 5 个 URL 字段已从 `varchar(500)` 扩为 `TEXT`，避免导入器继续因长 URL 报 `Data too long for column 'tuition_info_url'`；变更前备份：`/opt/gzly/backup/uni_official_link_before_url_text_20260430123944.sql`。
- 全国院校官网缺口补齐已启动全量首轮：`SCOPE=official-missing LIMIT=0 WORKERS=3`，seed `861` 所，爬虫 PID `2554469`，日志 `/root/gzly_scraper/data/official_links/data_gap_official-missing.log`。
- 官方链接导入器 `gzly-official-importer.service` 已恢复运行，长 URL 错误解除；启动后缺口指标先从 `4315` 降到 `4205`，后续随 crawler 导出继续自动导入。
- 四川专项学校官网发现已启动全量首轮：`--all --workers 4 --max-pages-per-school 8`，PID `2555628`，日志 `/root/gzly_scraper/sichuan_2025/reports/school_crawl_full_20260430124340.log`。
- 四川通用管线已刷新官方源：7 个省级来源，额外发现 60 条相关页面；旧四川 reviewed 已同步到 `province_group_2025/reviewed/SC`，校验 `errors=0 warnings=5`，payload manifest `43` 个。
- 使用临时 admin JWT 对 SC payload 做 live dry-run：`responses=43 totalRows=1217 validRows=1217 insertedDryRun=0 updatedDryRun=1217 rejected=0`，说明当前 reviewed 数据已全部落库，新增缺口需要等待学校官网发现后人工/模型复核进入 reviewed。
- 后台收尾器已挂：PID `2559445`，日志 `/root/gzly_scraper/data/official_links/full_round_watch_20260430125302.log`；会等待两个爬虫结束后自动运行四川 `review-queue`、`validate-reviewed`、`payloads`，并输出最终 DB snapshot。
- 生产回归：`/api/score-line/years?provinceCode=SC` 返回 `[2025]`；`/api/score-line/list?provinceCode=SC&year=2025&subjectType=物理类` 只返回四川专业组数据；`/api/volunteer/rank-check?provinceCode=SC&firstSubject=物理&totalScore=560` 使用四川官方一分一段返回 `54388-55395` 位次区间。

## 上一轮变更（v7.33，2026-04-30 12:36）

### 修复 AI 深度解读模板百分号异常

- 线上现象：`/volunteer/ai?planId=112` 显示 `[ERROR] AI 服务暂时不可用`。
- 根因：`AiService.streamAnalysis` 使用 `String.format(USER_TASK_TEMPLATE, ...)`，模板内含 `100%录取` 字样，Java 将 `%录` 当作格式占位符解析，抛出 `UnknownFormatConversionException: Conversion = '录'`，导致 SSE 直接返回错误。
- 修复：AI 分析主模板改为显式 token 替换 `{{PLAN_SUMMARY_JSON}}`，张雪峰.skills 对话模板也改为非 `String.format` 构造，避免中文百分号再次触发格式化异常。
- 回归测试：`AiServiceGuardrailTest.buildAnalysisUserPrompt_keepsLiteralPercentText` 覆盖 `100%录取` 模板文本；`gzly-server ./mvnw -q test` 通过。
- 生产部署：后端 `REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功，旧 JAR 备份到 `/opt/gzly/backend/backup/app.jar.20260430123503.bak`。
- 线上回归：重新获取 AI 解读 ticket 后调用 SSE，返回正文并包含 `[DONE]`，`hasError=false`；最近 5 分钟日志未再出现 `UnknownFormatConversionException` 或 `AI 流式分析异常`。

## 上一轮变更（v7.32，2026-04-30 11:45）

### 张雪峰.skills 服务收口到 AI 志愿分析

- 后端新增 `POST /api/volunteer/zhangxuefeng-skills-chat`：必须通过 `planId + accessKey` 校验后才能对话；提示词先注入 GitHub 公开 skills 摘要，再结合当前志愿方案 JSON、AI 深度解读正文、位次估算、扩招指数、院校招生指数、精度分和复核清单回答。
- AI 安全口径继续保留：不角色扮演、不冒充本人或机构、不输出录取承诺；输出必须带 AI 生成提示，触发违禁措辞时走安全兜底。
- 前端 `AiAnalysis.vue` 底部新增“张雪峰.skills 填报服务”聊天区，提供常用追问、Markdown 回复、错误恢复、来源链接；地区工作台移除独立“报考建议”卡片，避免变成脱离方案上下文的孤立功能。
- AI 解读摘要补充每条志愿的 `planExpansionIndex/schoolEnrollmentIndex/precisionScore` 等精度字段，skills 对话能围绕推荐精准度继续追问。
- 测试：`gzly-server ./mvnw -q test` 通过；`gzly-web npm run build` 通过；新增控制器测试覆盖 skills 对话前的方案密钥校验。
- 生产部署：后端 `REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功，旧 JAR 备份到 `/opt/gzly/backend/backup/app.jar.20260430114552.bak`；前端已同步 `/opt/gzly/frontend/dist/` 并通过 `nginx -t && systemctl reload nginx`。
- 线上回归：服务器内 `curl -k -H "Host: gzly.dongsiwei.com" https://127.0.0.1/region/GZ` 返回 200；新接口无效密钥返回 `方案不存在或访问密钥无效`；真实生成方案 `planId=115` 返回 96 条志愿，`zhangxuefeng-skills-chat` 返回 `code=0` 且来源为 `Eric-Yibo-Shen/zhangxuefeng-skillset`。Playwright 快照确认 `/region/GZ` 功能入口只剩院校查询、历年分数线、智能填报、特殊类型招生。

## 上一轮变更（v7.31，2026-04-30 11:26）

### 志愿推荐精度模型补强

- 后端生成链路新增“有效位次”解析：用户手填位次时仍按手填位次生成并做一致性校验；未手填位次时，使用对应省份官方一分一段表同分区间的保守位次生成，并在 `rankEstimate` 中标明 `rankEstimated=true`、来源、区间和提醒。
- 每条志愿新增并参与排序的可解释信号：`planExpansionIndex`（当年实际扩招指数，100=稳定）、`schoolEnrollmentIndex`（院校/专业组招生供给指数）、`precisionScore/precisionLabel/precisionNote`（推荐依据完整度）。有计划数时按真实扩招/缩招测算；计划数缺失时不伪造扩招，只给保守供给下限并在精度分扣权。
- 贵州 96 志愿与院校专业组 45 志愿共用同一响应字段；四川/湖北/安徽专业组链路保持数据门禁，不因位次估算绕过“45 组完整核验”规则。
- 前端 `VolunteerForm` 支持“手填位次优先，未填则官方一分一段保守估位”，并可一键写入估算位次；`VolunteerResult` 展示位次估算提醒、扩招指数、招生指数、精度分，Excel/人工核验草稿同步导出这些字段。
- 测试：`gzly-server ./mvnw -q test` 通过；`gzly-web npm run build` 通过。

## 上一轮变更（v7.30，2026-04-30 11:10）

### 张雪峰.skill 报考建议来源与入口补强

- 后端 `AdvisorAdvice` 增加 `sourceProjectName/sourceProjectUrl`，生成结果明确指向 GitHub 开源项目 `Eric-Yibo-Shen/zhangxuefeng-skillset`，同时保留 `alchaincyf/zhangxuefeng-skill` 的公开策略抽象说明；文案继续明确“不扮演本人、不代表本人或机构、不构成录取承诺”。
- 贵州 96 志愿与院校专业组省份的报考建议标题统一为 `张雪峰.skill 报考建议`，AI 深度解读 prompt 同步更新来源描述，避免只写单一仓库或被理解为角色扮演。
- 前端结果页建议卡右上角新增 GitHub 来源链接，地区工作台新增“报考建议”功能入口；四川等未完整开放地区仍显示待数据/锁定口径，不伪装完整建议。
- 测试：`gzly-server ./mvnw -q test` 通过；`gzly-web npm run build` 通过。
- 生产部署：后端 `REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功，旧 JAR 备份到 `/opt/gzly/backend/backup/app.jar.20260430110904.bak`；前端已同步 `/opt/gzly/frontend/dist/`，`nginx -t && systemctl reload nginx` 成功；`gzly/nginx` 均 active，`/` 与 `/region/GZ` 返回 200。

## 上一轮变更（v7.29，2026-04-29 23:27）

### 新增 GitHub 张雪峰.skill 报考建议口径

- 生成结果新增并收口 `advisorAdvice` 报考顾问建议：按 GitHub 开源项目 `alchaincyf/zhangxuefeng-skill` 的公开策略框架，把数据优先、就业倒推、中位数原则、城市优先、家庭成本分流等原则落到结构化方案中；只基于系统已生成的志愿数据输出，不扮演本人，不给录取承诺。
- 贵州 96 志愿和 SC/HB/AH 院校专业组 45 志愿均返回 `advisorAdvice`，内容覆盖定位、取舍、梯度、计划变化、城市、专业、风险检查和下一步执行清单。
- AI 深度解读 prompt 已要求优先引用 `advisorAdvice`，并显式禁止角色扮演、冒充本人或机构意见；前端结果页卡片标题更新为 `GitHub 张雪峰.skill · 公开策略参考`，并保留“非本人意见”标记。
- 测试：`gzly-server` `./mvnw test` 通过，**88 个测试 0 失败 0 错误**；`gzly-web` `npm run build` 通过。IDE 仍提示 `VolunteerService` 两个既有未使用私有方法警告，非本轮新增问题。

## 最新一轮变更（v7.28，2026-04-29 21:30）

### 首页新增"累计浏览量"

- 后端 `SiteStatsService` 在 v7.27 ZSET 活跃集合基础上扩展两个 String 计数器：
  - `gzly:visits:total`：累计浏览量（永不过期）。
  - `gzly:visits:daily:{yyyy-MM-dd}`：当日浏览量（首次写入时 `expire 35 天`，自动滚出旧日期）。
  - 计数语义：**仅当 `ZADD gzly:online:active` 返回新成员（即指纹在 5 分钟滑窗内首次出现）时 INCR**，30 秒前端轮询不会污染累计值；同一访客 5 分钟连续访问只算 1 次浏览，离开 5 分钟后重新访问算新一次（更接近"会话/独立访问"语义）。
  - 日期界限按 `Asia/Shanghai` 当日。
  - Redis 异常时降级到进程内 `AtomicLong` + `ConcurrentHashMap<LocalDate, AtomicLong>`，本地日历表保留最近 35 天，超量自动清理；本地路径保持"重复访客 5 分钟内只 +1"语义。
  - 接口 `GET /api/site-stats/online` 返回字段扩展为 `{activeUsers, windowSeconds, totalViews, todayViews}`，向后兼容旧前端（多余字段会被忽略）。
- 前端 `OnlineCounter.vue` 改为两 chip 横排：原"实时人数"chip 右侧追加蓝色 `BarChart3` chip，显示"累计 N 次访问"，并在第三个 hint 位附加"今日 +M"；当 `totalViews <= 0` 时该 chip 不渲染，避免冷启动时显示 `0`。
- `siteStats.ts` 的 `OnlineStats` 类型加 `totalViews?: number` / `todayViews?: number`，保持可选，兼容旧后端响应。
- 测试：`SiteStatsServiceTest` 在 v7.27 5 个用例基础上扩到 10 个，新增 5 个覆盖：
  - `touchAndCount_setsDailyKeyTtlOnFirstIncrementOnly`：首日首次 INCR 设 TTL，后续不重复设。
  - `touchAndCount_doesNotIncrementOnRepeatVisitorWithinWindow`：ZADD 返回 false（重复指纹）时不调用任何 INCR，并通过 `GET` 透传现有计数值。
  - `touchAndCount_localCounterCountsRepeatVisitorOnceWithinWindow`：Redis 全挂时同一访客两次请求 `totalViews/todayViews/activeUsers` 都保持 1。
  - `touchAndCount_handlesNonNumericCounterAsZeroFallback`：`gzly:visits:total` 被外部写脏值时不抛异常，返回 0。
  - `touchAndCount_localDailyCounterRollsOverByDate`：新一天本地计数从 1 起算，证明 `LocalDate.now(Asia/Shanghai)` key 隔离。
- `SiteStatsControllerTest.online_returnsActiveUsersFromService` 增加 `$.data.totalViews` / `$.data.todayViews` 透传断言。
- 后端全集 `./mvnw test` 现 **86 个测试 0 失败 0 错误**（v7.27 81 → v7.28 86）。
- 前端 `npm run build` 通过；`Home.js` 由 6.67 KB / gzip 3.57 升至 **7.42 KB / gzip 3.77**，新增第二 chip + `BarChart3` 图标成本可接受。

### 生产部署（2026-04-29 21:25–21:26）

- 后端：`REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功；旧 JAR 备份到 `/opt/gzly/backend/backup/app.jar.20260429212600.bak`，启动后健康检查 `/api/volunteer/metrics` 通过。
- 前端：`rsync -az --delete gzly-web/dist/ root@39.97.232.141:/opt/gzly/frontend/dist/` 完成；`nginx -t && systemctl reload nginx`；线上 `Home-D6ysoNi2.js` 200 / 8135B、`index.html` 指向新 hash `index-B9xjm0u0.js`。
- 生产实测：服务器内 `curl -k -H "Host: gzly.dongsiwei.com" https://127.0.0.1/api/site-stats/online` 连续 3 次不同 UA 返回 `{activeUsers, totalViews, todayViews}` 同步从 `1→2→3`，证明 INCR 链路与 ZADD 新成员判定一致。`gzly:visits:total` 与 `gzly:visits:daily:2026-04-29` 由本次新部署的应用首次写入，历史活跃指纹未回填到累计值（语义：累计从"v7.28 上线时刻"开始）。
- 服务状态：`gzly` / `nginx` 均 active；本轮 **未** 改动数据库、systemd unit、`/etc/gzly/gzly.env`、nginx 配置。

## 上一轮变更（v7.27，2026-04-29 15:50 / 部署 21:20）

### 公告弹窗收口到首页 + 首页实时浏览量

- `GlobalAnnouncement.vue` 仍挂在 `App.vue`，但弹出条件改为 `route.name === 'Home' || route.path === '/'`，并在 `route.fullPath` 变化时主动隐藏。结果：
  - `/region/{provinceCode}`、`/volunteer*`、`/score-line`、`/special-admissions`、`/admin/**` 等所有非首页路由不再弹公告。
  - 已读状态仍按 `gzly-announcement-dismissed:{id}:{updatedAt|publishedAt}` 写 `localStorage`；同一公告版本只在首页弹一次。
  - `fetchCurrentAnnouncement` 改为懒加载：进入非首页不会触发请求，进入首页才会调用一次。
- 首页新增 `OnlineCounter.vue` 实时浏览量组件，挂在 hero 区右上角：每 30 秒拉一次 `/api/site-stats/online`，显示"X 人正在浏览（近 5 分钟）"。组件失败时静默退出，不影响其他渲染。`prefers-reduced-motion` 下脉冲动画自动关闭。
- 后端新增 `SiteStatsService` + `SiteStatsController`：
  - `GET /api/site-stats/online` 返回 `{ activeUsers, windowSeconds }`。
  - 实现使用 Redis `ZSET gzly:online:active`：访客指纹（IP + UA → SHA-256 截 16 位）作为 member、当前时间戳作为 score；每次请求 ZADD + ZREMRANGEBYSCORE 移除 5 分钟以前的成员，再 ZCARD 取活跃数。`expire` 续 30 分钟，避免 Redis 长时间堆积。
  - Redis 异常时降级到进程内 `ConcurrentHashMap`，不抛异常给前端；本地桶超过 8192 条会清掉过期项。
  - IP 提取与 `PublicRateLimitInterceptor` 同口径：仅在 `RemoteAddr` 属于 `127.0.0.1`/`::1`/`10.0.0.0/8`/`192.168.0.0/16`/`172.16-31.0.0/12` 等可信代理段时采信 `X-Forwarded-For` / `X-Real-IP`，否则用 `RemoteAddr`，避免外网 XFF 伪造分裂活跃集合。
  - 新增限流规则 `site-stats-online`：单 IP 60 秒 30 次（默认值，可由 `gzly.rate-limit.site-stats-online-limit` / `gzly.rate-limit.site-stats-online-window-seconds` 调整），匹配前端 30 秒拉一次的节奏，留有应急余量。
- 后端新增 6 个测试，全集 `./mvnw test` 现 **81 个测试 0 失败 0 错误**：
  - `SiteStatsControllerTest.online_returnsActiveUsersFromService`：controller 透传 service 返回的 `activeUsers` / `windowSeconds`。
  - `SiteStatsServiceTest.touchAndCount_addsVisitorTrimsWindowAndReturnsCount`：验证 ZADD 写入指纹、ZREMRANGEBYSCORE 范围正确、`expire` 续 TTL、`zCard` 返回值被透传。
  - `SiteStatsServiceTest.touchAndCount_fallsBackToLocalCounterWhenRedisFails`：模拟 Redis 异常时切换本地集合，依次提交两个不同访客分别得到 `1 → 2` 的活跃数。
  - `SiteStatsServiceTest.visitorFingerprint_isStableForSameVisitor`：同 IP + 同 UA 的两次请求生成同一 16 位指纹。
  - `SiteStatsServiceTest.visitorFingerprint_distinguishesByIp`：同 UA 不同 IP 指纹必然不同。
  - `SiteStatsServiceTest.touchAndCount_ignoresTrustedProxyForwardedHeader_whenRemoteIsPublic`：`RemoteAddr=10.0.0.5` 经可信代理后采信 `XFF=203.0.113.100`，与外网 `RemoteAddr=203.0.113.100` 同 UA 的访客指纹一致。
- 前端 `npm run build` 通过；`Home.js` 包体由 5.42KB 升至 6.67KB（gzip 2.99→3.57），新增组件成本可接受。

### 生产部署（2026-04-29 21:17–21:18）

- 后端：`REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功；旧 JAR 备份到 `/opt/gzly/backend/backup/app.jar.20260429211722.bak`，`gzly.service` 启动 4.5s，脚本内置 `http://127.0.0.1:8090/api/volunteer/metrics` 健康检查通过。
- 前端：`rsync -az --delete gzly-web/dist/ root@39.97.232.141:/opt/gzly/frontend/dist/` 完成；`nginx -t` 通过并 `systemctl reload nginx`；线上 `index.html` 引用新 hash `index-6_GYQMrr.js`，`Home-B_SNKvKT.js` 命中 200（7358B / 路径 `/assets/`）。
- 生产实测：服务器内 `curl -k -H "Host: gzly.dongsiwei.com" https://127.0.0.1/api/site-stats/online` 连续 3 次不同 UA 返回 `activeUsers=2→3→4`、`windowSeconds=300`，验证 Redis ZSET 写入访客指纹与 5 分钟滑窗逻辑落地；`/api/volunteer/metrics` 仍返回空 `data`，与历史口径一致。
- 服务状态：`systemctl is-active gzly nginx` 双 active；JAR 时间戳 `Apr 29 21:17`、大小 `43,254,918 B`，与本地 `gzly-server-1.0.0.jar` 一致。

## 上一轮变更（v7.26，2026-04-29 14:55）

### 接手核查：vision 主线被 DNS 污染 + Deepseek V4 reasoning 双重阻断

- 服务器解析 `api.bilibilidaxue.xyz` 到 `198.18.20.51`（IETF benchmarking 黑洞段），TLS 握手发出 ClientHello 后被中断；`curl -k` 跳过验证仍失败，`SSL_ERROR_SYSCALL`。这是 DNS/网络层问题，不在 vision 脚本可修复范围。`/etc/gzly/gzly.env` 当前仍配置该不可达 base_url（`GZLY_AI_BASE_URL=https://api.bilibilidaxue.xyz/v1`、`GZLY_AI_MODEL=grok-3-mini`），下次维护应清空或改为可达 endpoint。
- 后台数据库表名实际为 `sys_ai_config`（不是 `ai_config`）。`sys_ai_config` (id=1) 当前配置：base_url=`https://api.deepseek.com`、chat/review/vision_model 均为 `deepseek-v4-flash`、enabled=1、updated_at=2026-04-26 15:58。`AiConfigService` 优先用数据库配置覆盖环境变量，所以后端 AI 调用走 Deepseek，没受到 bilibilidaxue 不可达影响。
- 验证 Deepseek key 可达，模型列表只有 `deepseek-v4-flash` 与 `deepseek-v4-pro`。但两个模型都是 reasoning model（`reasoning_content` 与 `content` 分开输出，且 reasoning_tokens 计入 `max_tokens`）。`reasoning.effort=none`、`enable_thinking=false`、`chat_template_kwargs.enable_thinking=false`、`max_thinking_tokens=0`、模型名 `*-nothink` 后缀全部不生效。AH 一张图 OCR 文本（约 6000 字符）+ JSON 结构化任务下，V4-pro `max_tokens=8000` 全部被 reasoning 吃光，`content` 为空，4 分钟无产出。
- Deepseek V4 同时不支持图片输入（`image_url` 类型 content 报 `unknown variant`）。后台 `sys_ai_config.vision_model=deepseek-v4-flash` 实际无视觉能力。
- 结论：vision 主线在当前 LLM/网络条件下不可推进。要继续推 HB/AH OCR 提质，必须引入非 reasoning 类 LLM（OpenAI gpt-4o-mini / 智谱 GLM-4-flash / 通义 qwen-turbo / Moonshot kimi 等）或本地 PaddleOCR PP-StructureV2，否则只能停留在当前 tesseract 噪声水平。本轮未引入新依赖，按用户决策"先跳过 OCR 主线，写进度文档"执行。

### 四川 reviewed 与生产完全同步，无新增可导入

- 用 admin token 对 `payloads/SC/group-lines-001..003.json` 与 `group-plans-001..003.json` 跑 dry-run（端点 `/api/admin/province-data/SC/group-lines/import?dryRun=true` 与 `/group-plans/import?dryRun=true`）：6 个 payload 全部 `rejected=0`，line `updated=22`、plan `updated=140`，与生产现状完全一致；当前 reviewed 中不存在尚未导入的存量。
- 生产 SC 状态：物理类 line 15/45、plan 14/45；历史类 line 7/45、plan 7/45；`generationReady=false` 持续锁定，`lockReason=四川2025官方一分一段、普通本科批B段院校专业组线或招生计划尚未完整核验`；`group_lines` 仍有 1 个专业组（成都信息工程大学 111 物理类）在 `group_plans` 中找不到对应招生计划。
- 解锁仅能通过持续采集更多高校官网 `school_verified` 数据并人工复核；不允许用低可信数据补齐。本轮未触发任何正式 import。

### 后端单测、前端构建、生产服务三项绿灯

- `gzly-server` `./mvnw test` 通过，**62 个测试 0 失败 0 错误**。HANDOVER.md 中记录的 `UniversityQaService/UniversityQa` 编译错误在 v7.18→v7.23 期间已修复，本轮再次确认；HANDOVER.md 中"31 个测试"的旧口径需要在下一轮维护时更新。
- `gzly-web` `npm run build` 通过；`UniversityDetail` 31.6 KB / gzip 12.6 KB、`VolunteerForm` 30.5 KB / gzip 11.8 KB、`VolunteerResult` 30.8 KB / gzip 11.9 KB，均未达必须拆分阈值。`xlsx` 与 `html2canvas` 已按需懒加载。
- 生产侧 `gzly`/`nginx`/`gzly-data-gap-supplement.timer` 均 active；`gzly-official-importer.service` 仍按既定策略保持 inactive。

### 服务器健康度与隐藏坑

- 根分区 40 GB 已用 30 GB，可用 8.1 GB（79% 占用）。当前不允许装 PaddleOCR PP-StructureV2 等大依赖（≈1 GB）前不做容量评估；下次维护建议先清理 `/root/gzly_scraper/province_group_2025/backups/` 与历史日志。
- 服务器 `python3` 默认仍是 3.6.8（`/usr/local/bin/python3.9`、`/usr/bin/python3.11` 同时存在）。`reviewed_payloads.py` 与 `extract_group_lines_with_vision.py` 顶部 `from __future__ import annotations` 在 3.6 下直接 SyntaxError，必须用 `python3.11`。当前脚本 shebang 仍是 `#!/usr/bin/env python3`，README 也未注明最低 Python 版本，下一轮维护应在脚本顶部与 README 明确，避免新人按文档跑出错。
- `gzly.env` 与 `sys_ai_config` 同时配置导致 vision/chat 模型不一致（前者 grok-3-mini，后者 deepseek-v4-flash），实际生效以数据库为准；下一轮应统一两边或去掉 `gzly.env` 中已被覆盖的旧值，避免误导。

### 按 HANDOVER 后续优化项补 controller 鉴权 + 400 / 业务校验测试

- HANDOVER `六、当前风险与下一步` 之 `后续优化项` 明确"新增接口必须同步补 鉴权 / 限流 / 400 响应测试"。本轮发现 `ProvinceDataAdminController` 只有 2 个 happy-path 用例，`WebMvcConfigAdminAuthTest` 没单独覆盖新增 `/admin/province-data/...` 路径前缀，并且 `EncouragementMessageController` 与 `FeedbackController` 这类公开接口完全没有专门的测试类（业务校验逻辑只能靠人工/集成发现）。
- 已新增 13 个测试，未改业务逻辑、未触动 service / controller 实现：
  - `ProvinceDataAdminControllerTest`（+3）：service 抛 `BizException` 时 controller 返回 `200 + code=-1`（与 `GlobalExceptionHandler` 现实约定一致）；`?year=abc` 与 `?dryRun=not-bool` 触发 `MethodArgumentTypeMismatchException` 返回 `400 + message 含字段名`。
  - `WebMvcConfigAdminAuthTest`（+1）：`AuthInterceptor` 对 `/admin/province-data/HB/status` 与 `/admin/province-data/SC/group-plans/import` 的无 token=401、user-token=403、admin-token=放行 验证全过。
  - 新增 `EncouragementMessageControllerTest`（+6）：覆盖 `list` 空数组、`submit` 内容过短/过长、含 URL、含 `包录取` 等敏感词分别返回业务错误，以及空 nickname 默认 `贵州考生` 的快乐路径。
  - 新增 `FeedbackControllerTest`（+3）：`content < 10`/`> 500` 返回业务错误；`sourcePage > 120` 自动截断；正常路径成功 insert。
- 全集 `./mvnw test` 现 **75 个测试 0 失败 0 错误**（较本轮初值 62 增加 13 个）。鉴权/业务校验路径覆盖更完整，新增公开接口的回归基线建立。

### 本轮未推进项（按用户决策跳过）

- HB/AH OCR draft 提质：等待非 reasoning LLM 接入或 PaddleOCR 授权后再推。
- 贵州历史类官方选科要求：与 OCR 主线同质，路径同上。
- 官方链接 4315 缺口补数：本轮未启动。
- 中长期权限模型集中化（F）、大页面拆分（G）：本轮未启动。

## 上一轮变更（v7.25，2026-04-29 12:59）

### 官方投档线图片抽取队列补齐但继续锁定未导入
- 新增 `scripts/server/province_group_2025/extract_group_lines_with_vision.py` 与 `vision-group-lines` 管线命令，固定抓取湖北省教育厅 2025 本科普通批首选历史/物理投档线图片、安徽教育在线标注来源为省考试院的普通本科批历史/物理投档线图片，输出 draft/review queue；默认不写 reviewed、不导入生产。
- 脚本支持 OpenAI 兼容视觉模型；因服务器 `api.bilibilidaxue.xyz` 仍有 TLS 失败，已补 `--backend tesseract` 降级路径，并对长图做分片 OCR，产物按省份写入 `draft/group_line_vision_draft_<province>.csv`、`reports/<province>/group_line_review_queue.csv`、`reports/<province>/group_line_vision_summary.json`。
- 服务器已备份旧文件到 `/root/gzly_scraper/province_group_2025/backups/20260429120934/` 并同步新脚本。安徽全量 `26` 张图片 OCR 完成，生成 draft `950` 行：历史类约 `305` 组、物理类约 `643` 组，全部标为 `needs_manual_review`；抽样发现院校名和个别分数存在 OCR 噪声，未提升为 reviewed。
- 湖北样本 `2` 张图片 tesseract 未产出可靠结构化行；需继续依赖视觉模型可用性或更强表格 OCR/人工复核。安徽图片中的 `投档人数` 已在 `parserNotes` 标注为不等同招生计划，未生成 `group_plans`。
- 服务器复跑 `validate-reviewed --province ALL` 与 `payloads --province ALL`：SC 仍 payload `43`，HB/AH reviewed 仍为空且 payload `0`；线上状态 SC/HB/AH 均 `generationReady=false`。未导入任何 OCR 草稿，也未伪造招生计划或一分一段。

## 上一轮变更（v7.24，2026-04-29 11:43）

### 通用三省管线上服务器并执行 SC 幂等导入
- 已备份并同步 `scripts/server/province_group_2025/reviewed_payloads.py`、`run_province_group_pipeline.sh`、`README.md` 到服务器 `/root/gzly_scraper/province_group_2025/`；远端旧文件备份时间戳为 `20260429113700`。
- 修复旧四川一分一段 reviewed CSV 缺 `sourceLevel` 表头的兼容问题：`sync-sichuan-reviewed` 复制旧 CSV 后会为缺失列补默认来源等级，一分一段为 `manual_verified`，专业组线/计划为 `school_verified`。
- 服务器已执行 `sync-sichuan-reviewed --overwrite`：复制 SC 一分一段历史类 `514` 行、物理类 `541` 行，专业组线 `22` 行，招生计划 `140` 行。
- 服务器复跑 `payloads --province ALL`：SC 校验 `errors=0`、`warnings=5`、payload `43` 个；HB/AH 仍为 reviewed 空模板，payload `count=0`。
- 已在服务器用生产 admin token 执行 SC `43` 个 payload dry-run，全部 `rejected=0`；随后按 `CONFIRM_PROVINCE_IMPORT=SC_2025_REVIEWED` 口径正式导入，全部成功且 `rejected=0`。本次为幂等更新：一分一段 `1055` 行、专业组线 `22` 行、招生计划 `140` 行均为 `updated`，未新增伪造数据。
- 导入后线上 `/api/admin/province-data/SC/status?year=2025`：一分一段 `ready`；专业组线 `22/90` 组、招生计划 `21/90` 组，`generationReady=false` 继续锁定。

## 上一轮变更（v7.23，2026-04-29 11:35）

### 通用三省管线迁移四川 reviewed 存量
- `scripts/server/province_group_2025/reviewed_payloads.py` 新增 `sync-sichuan-reviewed` 命令，可把旧四川专项管线 `scripts/server/sichuan_2025/reviewed/` 中已人工复核的 CSV 迁移到通用 `reviewed/SC/`；默认不覆盖已有非空 reviewed，显式 `--overwrite` 才覆盖。
- `run_province_group_pipeline.sh` 与 `README.md` 已补命令说明：迁移只复制人工复核 CSV，不从 OCR/draft 自动生成 reviewed，也不写生产表。
- 已执行 `sync-sichuan-reviewed --overwrite`，通用 SC reviewed 当前恢复为专业组线 `22` 行、招生计划 `140` 行；`payloads --province ALL` 后 SC manifest `count=6`，HB/AH 仍为 `count=0` 空模板。
- SC 当前校验 `errors=0`、`warnings=7`：仍未达到两科各 `45` 组门槛，且 `成都信息工程大学 111 物理类` 有专业组线但本地计划明细缺失；生成能力继续保持锁定。
- 验证：Python 脚本 `py_compile` 通过；通用三省 payload 生成通过；SC dry-run/import curl 与包装脚本 `bash -n` 通过；后端 `SichuanDataAdminServiceTest,ProvinceDataAdminControllerTest` 通过。

## 上一轮变更（v7.22，2026-04-29 10:48）

### 三省 reviewed 链路服务器落地与 OCR 队列刷新
- 已同步 `scripts/server/province_group_2025/reviewed_payloads.py`、`run_province_group_pipeline.sh`、`README.md` 到服务器 `/root/gzly_scraper/province_group_2025/`。
- 已部署包含 `/api/admin/province-data/{provinceCode}` 的后端到生产，旧 JAR 备份为 `/opt/gzly/backend/backup/app.jar.20260429104047.bak`，健康检查 `/api/volunteer/metrics` 通过；无 token 访问新 admin 状态接口返回 `401`，`gzly=active`。
- 服务器复跑 `init-reviewed --province ALL`、`validate-reviewed --province ALL`、`payloads --province ALL`，三省均为 `errors=0`、`warnings=6`、payload `count=0`，说明当前 reviewed 仍为空模板，未生成任何可导入 payload。
- 服务器复跑普通 OCR：`ocr-score-rank --province ALL` 产出 `images=54`、`raw_rows=1651`、`merged_rows=1606`；随后基于最新 tesseract 草稿刷新 `reports/score_rank_review_queue.*`。
- 网格 OCR 本次运行超过 10 分钟且未更新产物，已停止本次进程，保留服务器已有 `score_rank_grid_draft.csv/validation.json`；未把 OCR 草稿自动提升为 reviewed。
- 当前 OCR 结论：四川一分一段草稿质量较高但生产已导入；湖北、安徽普通 OCR 仍缺口较大，不能自动导入。湖北、安徽线上生成接口复测仍返回“官方一分一段表尚未导入或未通过核验”，继续锁定。

## 上一轮变更（v7.21，2026-04-29 10:36）

### 三省 reviewed → payload → admin dry-run 链路补齐
- 后端新增通用管理端点 `/api/admin/province-data/{provinceCode}`，支持 `SC/HB/AH` 的状态查询、官方来源刷新、一分一段导入、院校专业组线导入、招生计划导入；旧 `/api/admin/sichuan-data` 保留兼容。
- `SichuanDataAdminService` 已按省份上下文复用为三省通用导入服务：写入 `data_score_rank/data_admission_group_line/data_admission_group_plan/data_source_registry` 时使用对应省份、批次、官方来源与来源白名单，仍只允许 2025 普通本科批次院校专业组数据。
- `scripts/server/province_group_2025/` 新增 reviewed 出口：`init-reviewed` 生成三省 reviewed CSV 模板，`validate-reviewed` 校验来源、重复、两科 45 组门槛、计划与专业组线一致性，`payloads` 生成 per-province manifest 与 `admin_dry_run_curl.sh/admin_import_curl.sh`。
- 当前本地三省 reviewed 模板均为空，复跑结果为 `errors=0`、`warnings=6`、payload `count=0`；没有执行 admin dry-run 或正式导入，也没有把采集草稿直接写入生产表。
- 已补 `province_group_2025/README.md`，明确 reviewed 文件命名、来源字段、dry-run/import 顺序和 `CONFIRM_PROVINCE_IMPORT=<SC|HB|AH>_2025_REVIEWED` 确认保护。

## 上一轮变更（v7.20，2026-04-29 01:19）

### 三省缺失数据服务器采集启动并完成首轮样本
- 新增并同步 `scripts/server/province_group_2025/` 到生产 `/root/gzly_scraper/province_group_2025/`，用于四川、湖北、安徽 2025 院校专业组缺失数据采集；该管线只写 `raw/` 与 `reports/`，不生成 reviewed、payload，不写生产表。
- 已执行基线查询：生产仅四川已有数据（`data_score_rank` 历史类 `514`、物理类 `541`；`data_admission_group_line` 历史类 `7`、物理类 `15`；`data_admission_group_plan` 历史类 `32`、物理类 `108`），湖北、安徽仍无入库行。
- 已在服务器完成首轮采集：
  - 命令：`./run_province_group_pipeline.sh official --province ALL --download-children --discover-limit 60`。
  - 命令：`./run_province_group_pipeline.sh schools --province ALL --limit 120 --workers 4 --timeout 12 --max-pages-per-school 6 --max-bytes 1200000`。
  - 日志：`/root/gzly_scraper/province_group_2025/reports/three_province_collection_20260429010419.log`。
- 首轮产物汇总：
  - 四川：官方来源 `7` 个，站内候选 `60`；高校样本 `120` 所、扫描页面 `1154`，raw 文件 `333`，`needs_manual_check=70`。
  - 湖北：官方来源 `4` 个，站内候选 `60`；高校样本 `120` 所、扫描页面 `1003`，raw 文件 `262`，`needs_manual_check=22`。
  - 安徽：官方来源 `3` 个，站内候选 `60`；高校样本 `120` 所、扫描页面 `975`，raw 文件 `295`，`needs_manual_check=17`。
- 复核入口：`reports/<SC|HB|AH>/official_source_audit.*` 与 `reports/<SC|HB|AH>/school_candidate_review_queue.csv`。下一步必须先人工/模型抽取候选并人工复核到 reviewed CSV，再走 dry-run/import；当前生成能力继续锁定。

## 上一轮变更（v7.19，2026-04-29 01:04）

### 湖北、安徽代码上线但保持数据锁定
- 已按用户要求部署湖北、安徽地区代码到生产，但保持和四川一致的数据完整性门禁：未补齐官方一分一段、院校专业组调档线、招生计划并通过核验前，不开放生成。
- 后端发布：`REMOTE_HOST=39.97.232.141 RUN_TESTS=0 bash scripts/server/deploy_backend_safe.sh` 成功，旧 JAR 已备份到 `/opt/gzly/backend/backup/app.jar.20260429005939.bak`，`/api/volunteer/metrics` 健康检查通过。
- 前端发布：已同步 `gzly-web/dist/` 到 `/opt/gzly/frontend/dist/`，`nginx -t` 通过并已 reload。
- 线上验证：
  - `gzly`、`nginx` 均为 `active`。
  - Nginx 本机 HTTPS `200`，`/region?provinceCode=HB` 返回前端应用内容。
  - 直接调用湖北生成接口返回：`湖北官方一分一段表尚未导入或未通过核验...后再开放生成。`
  - 直接调用安徽生成接口返回：`安徽官方一分一段表尚未导入或未通过核验...后再开放生成。`
- 本机外网访问 `https://gzly.dongsiwei.com/` 出现一次 LibreSSL 握手失败（`SSL_ERROR_SYSCALL`），但服务器本机按 Host 头验证 Nginx HTTPS 正常；需后续如有用户侧访问异常再单独排查公网/证书链。

## 上一轮变更（v7.18，2026-04-29 00:58）

### 数据获取方式文档与湖北、安徽地区开发
- 新增独立文档 `DATA_ACQUISITION_METHOD.md`，单独记录“官方规则确认 -> 原始采集 -> draft 抽取 -> reviewed 人工复核 -> payload dry-run/import -> 状态锁定”的统一数据获取方法，并写清 `manual_verified`、`school_verified` 与第三方来源禁用边界。
- 已按官方规则确认湖北、安徽均为 2025 新高考 `3+1+2`、普通本科批 45 个院校专业组志愿：
  - 湖北依据：湖北省教育厅《2025 年湖北省普通高校阳光招生政策暨志愿填报问答》、湖北省教育考试院《湖北省 2025 年普通高考总分一分一段统计表—普通类》。
  - 安徽依据：中安在线转载且标注来源为安徽省教育招生考试院的《安徽省 2025 年普通高校招生工作实施办法》《2025 年安徽高考一分一段表发布》。
- 后端已新增 `HB`、`AH` 省份策略，并将原四川 45 专业组生成服务通用化为 `ProfessionalGroupVolunteerService`：生成入口按 `volunteerUnitType=PROFESSIONAL_GROUP_45` 分流，候选仍只读 `data_admission_group_line/plan/score_rank` 中可核验的省份数据，数据不足时继续阻断，不回退贵州算法。
- `ScoreLineService` 已从只认 `SC` 改为按省份策略查询专业组调档线；湖北、安徽未来导入 `data_admission_group_line` 后可走同一查询路径。
- 前端已加入湖北、安徽专区配置、志愿表单省份入口、分数线页、多省特殊招生锁定态与结果页展示适配；湖北、安徽当前状态为 `数据准备中`，不会主动发起生成。
- 验证：`gzly-web` 已通过 `npm run build`。后端 `mvn test` 当前被既有 `UniversityQaService/UniversityQa` 编译错误阻断（大量 `getSchoolId/getParentId/getStatus` 等 getter/setter 找不到），该阻断不在本轮湖北/安徽改造文件内；本轮已同步修正新增构造器依赖对应的单测实例化代码。

## 上一轮变更（v7.17，2026-04-29 00:41）

### 四川 reviewed 链路部署、迁移与生产导入
- 已同步 `scripts/server/sichuan_2025/` 到生产 `/root/gzly_scraper/sichuan_2025/`，并在服务器复跑 `validate-reviewed` 与 `payloads`：`errors=0`、`warnings=5`、payload `43` 个（一分一段 `37`、专业组线 `3`、招生计划 `3`）。
- 已安全部署后端两次并完成健康检查：补齐 `school_verified` 来源导入支持，并修正状态接口 `sourceCompleteness.importedRows/importedGroups/remainingGroups` 口径。
- 已执行生产幂等迁移并留 schema 备份：
  - `20260428_sichuan_group_line_rank_source.sql`：补 `data_admission_group_line.rank_source_*` 字段。
  - `20260429_sichuan_group_plan_subject_unique.sql`：将 `data_admission_group_plan.uk_group_plan_major` 调整为包含 `subject_type`，适配四川历史/物理复用专业组代码。
- 已按“专业组线 dry-run -> 专业组线 import -> 招生计划 dry-run -> 招生计划 import”分阶段导入 B 段 reviewed 数据，6 个 payload 均 `rejected=0`；阶段汇总写入服务器 `reports/admin_staged_group_import_summary.json`。
- 当前生产四川 2025 数据：一分一段 `1055` 行（历史类 `514`、物理类 `541`），B 段专业组线 `22` 行 / `22` 组，招生计划 `140` 行 / `21` 组。`generationReady=false` 仍保持锁定：历史类线/计划各 `7` 组，物理类线 `15` 组、计划 `14` 组，未达到两科各 `45` 组门槛。

## 上一轮变更（v7.16，2026-04-29 00:23）

### 四川 B 段 reviewed 交叉校验补强
- `validate_reviewed_payloads.py` 已补充反向一致性诊断：会提示专业组线在 `group_plans` 中缺招生计划，以及专业组线 `planCount` 与计划明细合计不一致的情况。
- 已复跑 `run_sichuan_pipeline.sh payloads`，当前仍为 `errors=0`，`warnings=7`；新增 warning 指向 `成都信息工程大学 111 物理类` 已有专业组线但缺对应招生计划明细。
- payload 仍为 `6` 个（`group-lines` 3 个、`group-plans` 3 个），未设置 `ADMIN_TOKEN`，未执行 admin dry-run 或正式导入。

## 上一轮变更（v7.15，2026-04-28 23:35）

### 四川 B 段 reviewed 校验与 payload 状态复核
- 已复跑四川 reviewed 链路：`validate-reviewed` 当前 `errors=0`、`warnings=6`，校验报告已更新到 `scripts/server/sichuan_2025/reports/reviewed_validation_report.json/md`。
- 当前本地 reviewed 已有四川农业大学、成都信息工程大学的人工复核数据：专业组线 `22` 行（历史类 `7` 组、物理类 `15` 组），招生计划 `140` 行（历史类 `7` 组、物理类 `14` 组），来源均为 `school_verified`。
- `run_sichuan_pipeline.sh payloads` 已生成 `6` 个 admin payload：`group-lines` 3 个、`group-plans` 3 个，并刷新 `admin_dry_run_curl.sh` 与带确认保护的 `admin_import_curl.sh`。
- 当前仍未达到两科各 `45` 组解锁门槛；一分一段本地 reviewed 为空是预期状态，因为生产已完成官方一分一段导入。未设置 `ADMIN_TOKEN`，未执行 admin dry-run 或正式导入。

## 上一轮变更（v7.14，2026-04-28 23:19）

### 四川 B 段 reviewed → payload 导入链路收口
- 新增 `scripts/server/sichuan_2025/validate_reviewed_payloads.py`：校验 reviewed CSV 的必填字段、来源边界、重复行、两科 45 组门槛、招生计划与专业组线对应关系，输出 `reports/reviewed_validation_report.json/md`。
- reviewed / normalized / extraction 草稿统一保留 `sourceLevel` 元数据；考试院结构化来源默认 `manual_verified`，高校官网人工复核来源默认 `school_verified`。
- `build_admin_payloads.py` 改为按 `sourcePageUrl + sourceUrl + sourceHash + sourceLevel` 分组生成 payload，缺来源会直接失败；同时生成 `reports/admin_dry_run_curl.sh` 与带 `CONFIRM_SICHUAN_IMPORT=SC_2025_REVIEWED` 保护的 `reports/admin_import_curl.sh`。
- `run_sichuan_pipeline.sh` 新增 `validate-reviewed` 子命令，`payloads` 会先跑 reviewed 校验，再生成 manifest 与 curl 脚本。
- 已本地验证：`python3 -m py_compile` 通过；`validate_reviewed_payloads.py --allow-incomplete` 通过；`run_sichuan_pipeline.sh payloads --allow-incomplete` 通过。当前本地 reviewed 仍为空，manifest `count=0`，未执行任何生产导入。

## 上一轮变更（v7.13，2026-04-28 21:54）

### 四川 2025 官方一分一段补齐入库
- 四川确认按 2025 新高考 `3+1+2` 口径处理：首选科目为 `物理类/历史类`，后续院校专业组计划继续保留 `firstSubjectRequirement/resubjectRequirement`。
- 服务器安装最小 OCR 组件：`tesseract` + `tesseract-langpack-chi_sim`，用于本地识别四川省教育考试院官方一分一段图片；当前视觉模型入口 `api.bilibilidaxue.xyz` 在服务器解析到 `198.18.*` 且 TLS 失败，未再依赖该入口。
- 新增并同步：
  - `extract_score_rank_with_tesseract.py`：只处理考试院 37 张官方一分一段图片，输出 `draft/score_rank_tesseract_draft.csv`、OCR 文本、校验报告。
  - `build_school_review_queue.py`：对 339 条高校官网候选做复核分层，复用 `sys_university.logo_url/localLogoPath` 作为院校识别元数据，不把 logo 或新闻配图当招生数据证据。
- OCR/校验结果：
  - 历史类：514 行，分数 663-150，最高累计 177978。
  - 物理类：541 行，分数 691-150，最高累计 284789；官方图片从 172 分直接跳到 170 分，`171` 为省略的 0 人分数段，报告标记为 `omittedZeroScores=[171]`，不造 0 人生产行。
  - 修复 OCR 异常：历史类 271 分累计曾被读成 `1715632`，脚本现对连续分数中的万级异常累计差按 `上一累计 + 本段人数` 修正。
- 后端已修复并部署：
  - `SichuanDataAdminService` 的一分一段导入不再假设首行 `人数=累计`，也不要求官方省略 0 人分数段时分数完全连续。
  - 位次区间改为按 `rankLow = cumulativeCount - segmentCount + 1`、`rankHigh = cumulativeCount` 计算，适配四川官方图片的省略行格式。
  - 新增单测覆盖首行累计大于本段人数、172 分后省略 171 分再到 170 分的场景；`./mvnw -q test` 通过。
- 生产导入：
  - dry-run：37 个 payload、1055 行、`rejected=0`。
  - 首次错误 OCR 行已备份到 `data_score_rank_sc_2025_bad_backup_20260428215316` 并删除后重导。
  - 正式导入：1055 行，`inserted=1055`，`rejected=0`。
  - 当前生产校验：`data_score_rank` 中 `SC/2025/历史类=514`、`SC/2025/物理类=541`；`rank-check` 四川物理 691、历史 150 均返回官方位次区间。
- 仍未解锁四川生成：
  - `/api/admin/sichuan-data/status?year=2025` 显示一分一段 `ready`，但 `groupLineGroups=0`、`groupPlanGroups=0`，`generationReady=false`。
  - `/api/score-line/years?provinceCode=SC` 仍返回空数组，不回退贵州。
  - 普通本科批B段完整专业组调档线和全量招生计划仍缺可核验结构化来源，不写生产表。

## 上一轮变更（v7.12，2026-04-28 19:28）

### 四川 2025 数据补齐服务器爬虫管线
- 新增 `scripts/server/sichuan_2025/`，并已同步到生产服务器 `/root/gzly_scraper/sichuan_2025/`。
- 管线默认只写本地工作目录，不直接写生产表：
  - `raw/`：考试院与高校官网原始 HTML、图片、PDF/附件。
  - `draft/`：视觉模型/OCR 草稿，不允许直接导入。
  - `reviewed/`：人工复核 CSV，唯一允许生成 admin import payload。
  - `reports/`：来源审计、缺口、拒绝行、未匹配院校。
  - `payloads/`：现有 `/api/admin/sichuan-data/*/import?dryRun=true` JSON payload。
- 脚本职责：
  - `crawl_sichuan_sources.py` 抓四川省教育考试院 7 个已知来源并下载官方图片/附件。
  - `crawl_school_sources.py` 只读生产库 `sys_university + uni_official_link`，按学校官网/招生网发现四川 2025 本科批B段线索；默认 `--limit 20` 小样本。
  - `extract_with_vision.py` 使用 OpenAI 兼容视觉/文本模型生成草稿 JSONL/CSV；需要显式配置 `VISION_BASE_URL/VISION_API_KEY/VISION_MODEL`。
  - `normalize_sichuan_data.py` 将草稿规范化为待复核 CSV，并输出 reject 报告；默认不自动写 reviewed。
  - `build_admin_payloads.py` 从 reviewed CSV 生成 dry-run payload 和 curl 脚本。
- 安全边界：
  - 一分一段只接受考试院来源；B段调档线/招生计划允许高校官网补充并标记 `sourceLevel=school_verified`。
  - 第三方聚合站不写生产表；视觉模型输出只作为草稿。
- 已在服务器执行：
  - `baseline`：确认四川 `data_score_rank/data_admission_group_line/data_admission_group_plan` 仍无 `SC` 结构化行，`uni_official_link` 覆盖 2198 所院校，其中招生网 1951、章程 2029、专业目录 1852。
  - `official`：抓取考试院 7 个已知来源，生成 `reports/source_audit.*`，下载并登记官方图片/附件；后续视觉抽取已过滤到 37 张一分一段官方图片。
  - `schools --all`：全量扫描 2198 所院校官网/招生网，生成 `reports/school_discovery.csv` 13812 条候选、`school_discovery_high_confidence.csv` 339 条高置信候选；报告复用 `sys_university.logo_url`，不重复下载 logo。
  - `extract`：对 37 张官方一分一段图片尝试视觉模型 OCR；当前服务器 AI 入口返回 TLS EOF，37 条均记录到 `draft/vision_extractions.jsonl` 的失败 notes。
  - `normalize/payloads`：生成 reviewed CSV 模板、reject 报告、`payloads/manifest.json`；由于无人工复核行，payload count 为 0，未执行 dry-run/import。
- 爬虫安全修正：
  - 高校官网抓取支持 `--workers` 并发、`--max-bytes` 单文件上限，避免大附件撑爆磁盘。
  - 跳过普通 logo/新闻配图，只在报告中复用既有 `logoUrl/localLogoPath` 辅助人工核验；不把图片资产当作四川招生数据证据。
  - 服务器最终产物约 `raw=585M`、`reports=51M`，生产库未写入，四川前端生成仍保持锁定。

## 上一轮变更（v7.11，2026-04-28 17:42）

### 四川官方数据缺口报告与状态增强
- 后端 `/api/admin/sichuan-data/status?year=2025` 增加 `sourceCompleteness` 与 `blockingReasons`：分别说明一分一段、普通本科批B段院校专业组调档线、招生计划的官方来源登记状态、已导入数量、阻塞原因。
- 四川生成门禁保持不变：只有两科一分一段、B段专业组线、专业组计划均满足核验状态后才可能 `generationReady=true`。
- `scripts/server/build_sichuan_score_rank_sources.py` 增加：
  - 官方图片下载：已下载四川 2025 一分一段官方图片 `37` 张到 `scripts/data/raw/sichuan_score_rank_2025/`。
  - reviewed CSV 模板：`scripts/data/reviewed/sichuan_score_rank_2025/score_rank_sc_2025_物理类_reviewed.csv`、`score_rank_sc_2025_历史类_reviewed.csv`。
  - 官方缺口报告：`scripts/data/reviewed/sichuan_official_gap_report_2025.md`，明确只用四川省教育考试院可核验来源，不用第三方，不把高校官网分散数据写入生产表。
- 前端可测性修正：
  - 全局公告按公告 `id + updatedAt/publishedAt` 记住已读，避免每个功能页重复遮挡。
  - 首页地区卡片改为语义化路由链接并增加稳定 `data-testid`，提升自动化点击与无障碍表现。
- 后端参数错误处理补强：
  - `MissingServletRequestParameterException` 与 `MethodArgumentTypeMismatchException` 统一返回 `400`，避免公开接口缺参时落入系统异常日志。
  - 已回归 `/api/volunteer/rank-check?provinceCode=SC&firstSubject=物理` 返回 `400` 与“缺少必要参数：totalScore”。
- 验证：
  - 后端 `./mvnw -q test` 通过。
  - 前端 `npm run build` 通过。
  - 已通过 `deploy_backend_safe.sh` 部署生产后端；已同步前端 `dist/` 到 `/opt/gzly/frontend/dist/` 并 `nginx -t && systemctl reload nginx`。
  - 生产验证：`/`、`/region/GZ`、`/region/SC` 均 `200`；无 token 访问 admin 状态接口返回 `401`；`/api/score-line/years?provinceCode=SC` 返回空数组且不回退贵州；四川 `rank-check` 返回未导入官方一分一段提示。

## 上一轮变更（v7.10，2026-04-28 17:03）

### 四川数据补全接口开发
- 后端新增 `/api/admin/sichuan-data/*` 管理接口：状态查询、官方来源刷新、一分一段复核导入、院校专业组线复核导入、招生计划复核导入。
- 新增 `SichuanDataAdminService`：只允许 `sceea.cn` 官方来源，导入前校验 `SC/2025/普通本科批B段/物理类|历史类`，支持 `dryRun`、`preserveNonEmpty=true`、未匹配院校/专业组 reject report。
- 数据库迁移新增 `20260428_sichuan_data_admin.sql`：对齐 `data_source_registry` 的 `batch/source_level/source_hash/last_checked_at` 字段，`source_url` 改为 `TEXT`，补来源唯一索引。
- 四川生成仍由现有门禁控制：两科一分一段、B段专业组线、招生计划均达到核验状态前不解锁。
- 验证与部署：
  - 后端 `./mvnw -q test` 通过；新增服务、控制器、admin 权限、SC rank-check 不回退贵州的回归测试。
  - 生产库已按顺序应用 `20260428_multi_province_sichuan.sql` 与 `20260428_sichuan_data_admin.sql`，并备份迁移前 schema。
  - 已通过 `deploy_backend_safe.sh` 部署生产后端，健康检查通过。
  - 生产验证：无 token 访问 `/api/admin/sichuan-data/status` 返回 `401`；admin token 下 status 正常，`sources/refresh` 登记 7 条官方来源且状态 `manual_review`；一分一段与专业组线 dry-run 正常；招生计划在缺少对应专业组线时进入 reject report；`/api/volunteer/rank-check?provinceCode=SC` 返回四川“未导入”提示，不再回退贵州；四川生成接口继续由门禁拦截。

## 上一轮变更（v7.00，2026-04-28 16:38）

### 多地区分区入口隔离上线
- 前端新增统一地区配置 `src/constants/provinces.ts`，集中维护 `GZ/SC` 的志愿单位、目标数量、官方来源、入口状态和锁定文案。
- 首页 `/` 已改为地区入口，只展示贵州专区与四川专区；新增地区工作台 `/region/GZ`、`/region/SC`，统一模板展示地区说明、数据状态、核心功能和官方来源提醒。
- 现有功能页接入地区上下文：
  - `/volunteer?provinceCode=SC` 进入四川口径，顶部明确显示“数据未完整核验，生成能力暂不开放”，生成按钮禁用，不主动调用生成接口。
  - `/score-line?provinceCode=SC` 默认选中四川，切换省份会同步 URL query；无数据时显示四川数据准备中空状态。
  - `/university?provinceCode=SC` 保持全国院校库，但增加四川招生数据复核提示。
  - `/special-admissions?provinceCode=SC` 不展示未核验结构化政策列表，仅显示四川特殊类型招生需复核说明。
  - 全局风险横幅从固定贵州口径改为按当前地区展示，首页保持中性“对应省级考试院”表达。
- 验证与部署：
  - 本地 `gzly-web npm run build` 通过。
  - 本地 preview 路由 `/`、`/region/GZ`、`/region/SC`、`/volunteer?provinceCode=SC`、`/score-line?provinceCode=SC`、`/university?provinceCode=SC`、`/special-admissions?provinceCode=SC` 均返回 `200`。
  - Playwright 430px 视口检查：首页地区卡片、四川工作台、四川表单锁定、四川分数线页面布局正常；四川表单生成按钮为 disabled。
  - 已同步生产 `/opt/gzly/frontend/dist/` 并 `nginx -t && systemctl reload nginx`。
  - 服务器侧生产验证：`/`、`/region/GZ`、`/region/SC`、`/volunteer?provinceCode=SC`、`/score-line?provinceCode=SC` 均 `200`；`/api/score-line/years?provinceCode=GZ` 与 `SC` 均返回 `code=0`。

## 上一轮变更（v6.99，2026-04-28 12:20）

### 贵州历年一分一段表接入
- 数据管道：
  - `scripts/server/build_score_rank_table.py` 改为读取 `scripts/data/score_rank_sources_gz.json`，按年份 / 科类 / 官方页面 / PDF / 解析方式配置导入。
  - 已接入贵州省招生考试院官方 `2025 物理类/历史类` 与 `2024 物理类/历史类` PDF。
  - 修复 2024 官方 PDF 跨页表头解析，只吃第一页的问题；本地生成行数：
    - `2025 物理类 626`、`2025 历史类 580`。
    - `2024 物理类 634`、`2024 历史类 595`。
  - `2023/2022/2021 文科/理科` 已在来源配置中保留禁用占位；未找到可核验的贵州省招生考试院普通类官方源前不导入第三方数据，不把旧文理科自动映射为新高考物理/历史。
- 后端：
  - `data_score_rank_gz` 增加 `source_page_url`、`parse_method` 元数据字段。
  - `rank-check` 支持 `referenceYear` 精确查官方一分一段；指定年份缺表时明确返回未导入，不跨年份 / 跨科类猜测。
  - 历年录取记录缺 `min_rank` 但有 `min_score` 时，会用同年同科类官方一分一段换算位次区间，并标记 `rankSourceType=score_rank_converted`；原始位次标记 `original`，缺失标记 `missing`。
- 前端：
  - `/volunteer/result` 近三年录取记录增加“原始录取位次 / 一分一段换算 / 缺位次需复核”标签和说明。
  - `/score-line` 院校历年详情增加位次来源列，移动端横向滚动避免挤压。
- 验证与部署：
  - 后端 `./mvnw -q test` 通过；前端 `npm run build` 通过。
  - 生产库已备份并导入官方一分一段：总计 `2435` 行。
  - 已部署后端 `/opt/gzly/backend/app.jar` 并通过健康检查；已同步前端 `/opt/gzly/frontend/dist/` 并 reload Nginx。
  - 生产验证：`/`、`/score-line` 均 `200`；`rank-check?referenceYear=2024` 返回官方区间和官方页面/PDF；`referenceYear=2023` 返回未导入且不猜测；缺原始位次记录可返回“一分一段换算”标识。
  - Playwright MCP 仍报 `Target page, context or browser has been closed`；本轮用 Chrome headless + 生产 API / 路由验证补充检查。

## 上一轮变更（v6.98，2026-04-28 09:30）

### 分数线查询页二次视觉优化
- 针对桌面端效果不佳的问题，调整 `/score-line`：
  - 桌面端不再弹出大号底部抽屉，改为左侧院校列表 + 右侧固定历年分数线面板。
  - 移动端仍保留底部弹层，适配窄屏操作。
  - 院校卡片从大块卡片改为更紧凑的数据行，突出最低位次、最低分和查看历年操作。
  - 历年分数线从大卡片改为紧凑表格，减少空白和遮挡。
- 验证与部署：
  - 前端 `npm run build` 通过。
  - 已同步生产 `/opt/gzly/frontend/dist/` 并 reload Nginx。
  - 生产 `/score-line` 返回 `200`，新资源 `ScoreLineQuery-CSP8J9KC.js` 已上线。

## 上一轮变更（v6.97，2026-04-28 08:45）

### 分数线查询页改为院校分聚合
- `/score-line` 已从“分数线明细列表”改为“按院校聚合的院校分列表”：
  - 每所学校只展示一张卡片，显示基准年份、科类、最低分、最低位次和已收录年份数。
  - 点击院校卡片后，从底部弹层查看该校历年院校级投档线。
  - 页面文案明确当前展示“院校级数据”，专业级数据仍在生成志愿或院校详情中复核。
- 新增后端公开接口：
  - `GET /api/score-line/schools`：按年份 / 科类 / 院校名分页返回院校级聚合卡片。
  - `GET /api/score-line/school-history`：按 `schoolId + subjectType` 返回该校历年院校级投档线。
  - 旧 `GET /api/score-line/list` 保持兼容不动。
- 验证与部署：
  - 后端 `./mvnw -q test` 通过；前端 `npm run build` 通过。
  - 已部署生产后端并重启 `gzly`；已同步生产前端并 reload Nginx。
  - 生产验证：`/score-line=200`；`/api/score-line/schools?year=2025&subjectType=物理类&page=1&pageSize=3=200`，返回 `2030` 所院校；第一所院校的 `/school-history` 返回历年记录。
  - Playwright MCP 当前仍报 `Target page, context or browser has been closed`，本轮以生产 API / 路由 / 构建验证为准。

## 上一轮变更（v6.96，2026-04-28 07:55）

### 志愿生成可解释性、梯度区间与近三年录取数据上线
- 已部署后端到生产 `/opt/gzly/backend/app.jar`，服务名 `gzly`；`deploy_backend_safe.sh` 完成旧 JAR 备份、停服替换、重启和健康检查。
- 已部署前端到生产 `/opt/gzly/frontend/dist/` 并 reload Nginx；补齐 `/favicon.ico`，避免浏览器默认图标请求继续 404。
- 已执行生产索引迁移：
  - `data_major_score_gz.idx_major_score_history(school_id, subject_type, major_name, year)`
  - `data_score_line_gz.idx_score_line_history(school_id, subject_type, year)`
- 生产回归：
  - `gzly=active`、`nginx=active`，根分区约 `10G` 可用。
  - `/`、`/volunteer`、`/volunteer/result?planId=105`、`/disclaimer` 均返回 `200`。
  - `rank-check` 正常返回 `officialDataReady=true` 和官方一分一段位次区间。
  - 带自定义 `gradientRanges` 的 `/api/volunteer/generate` 返回 `code=0`、`96` 条志愿；`96/96` 位于配置区间内，`96/96` 带近三年历史记录，区间统计为冲 `20`、稳 `40`、保 `26`、垫 `10`。
- 本机 Shell 访问 `gzly.dongsiwei.com` 时 DNS 被解析到 `198.18.0.175` 导致 TLS 握手失败；服务器侧访问域名 HTTPS 正常 `200`，判断为本机代理 / DNS 环境问题，不是生产 Nginx 或应用故障。

## 上一轮变更（v6.95，2026-04-27 17:56）

### 志愿生成合规确认与免责声明弹窗
- 前端志愿生成改为强制“生成前风险告知”流程：
  - 点击生成时若未确认当前版本 `2026-04-27-v1`，只打开弹窗，不调用 `/api/volunteer/generate`。
  - 弹窗必须滚动到底部后才能点击“我已阅读并确认”。
  - 生成按钮未确认前显示“阅读风险告知并生成”，确认后显示“生成 96 个志愿”。
- 新增统一合规文案常量，`/disclaimer` 与弹窗共用同一份内容：
  - 明确公益免费、非官方、数据来源优先官方公开渠道、AI/算法仅供参考。
  - 删除“任何损失概不负责”“最终解释权”“风险自担”等高风险格式条款。
  - 明确“冲/稳/保/垫”只是梯度整理思路，不代表确定性承诺。
- 后端 `GenerateRequest` 新增并强校验：
  - `agreedDisclaimer=true`
  - `disclaimerVersion=2026-04-27-v1`
  - 未确认或版本不匹配返回：“请先阅读并确认生成前风险告知”。
- `biz_plan_history` 增加确认留痕：
  - `disclaimer_version`
  - `disclaimer_confirmed_at`
  - 新记录不再无条件硬写确认状态，按真实请求版本和时间保存。
- AI guardrail 收紧：
  - AI 解读必须显式包含“本内容由 AI 生成，仅供参考”。
  - 禁止内部来源、录取承诺、成功率/录取率/上榜率等绝对化措辞。
  - `application.yml` 默认提示词将“防滑档建议”改为“风险复核建议”。
- 验证与部署：
  - 数据库迁移已在生产执行，确认两个新字段存在。
  - 后端 `./mvnw -q clean test` 通过，并已用 `deploy_backend_safe.sh` 部署生产，健康检查通过。
  - 前端 `npm run build` 通过，已同步生产 `/opt/gzly/frontend/dist/` 并 reload Nginx。
  - 生产验证：`/` 与 `/volunteer` 均 `200`；未带确认字段生成返回 `code=-1`；带 `2026-04-27-v1` 生成返回 `96` 条，历史表写入 `agreed_disclaimer=1`、版本和确认时间。
  - Playwright 手机视口验证线上 `/volunteer`：弹窗显示版本 `2026-04-27-v1`，未滚动到底部时确认按钮禁用。

## 上一轮变更（v6.94，2026-04-27 17:10）

### 数据缺口核查与官方链接安全补导
- 生产库当前核心覆盖：
  - 院校基础库 `2198` 所。
  - 院校级录取线缺 `2` 所：主要为军警类特殊院校，需以官方材料人工复核。
  - 专业级录取线缺 `8` 所：多为特殊院校 / 高职新设或数据源未覆盖院校。
  - 官方一分一段表 `1206` 行，覆盖 `2025 历史类 580` 行、`2025 物理类 626` 行。
  - 官方选科要求 `24483` 行，覆盖 `2025 物理类 2018` 所；历史类仍无可靠批量导入源。
- 官方链接补数：
  - 已修复 `scripts/scrape_official_links.py` URL 清洗：仅允许 `http/https`，`www.` 自动补 `https://`，拒绝 `javascript/mailto/tel/data/#`，超过字段长度的跟踪长链接会去参数或丢弃。
  - 已增强 `scripts/server/import_official_links.sh`：缺口指标纳入 `tuition_info_url` 与无效 URL；导入后清空仍无效的 URL 字段；保留“指标变差自动回滚”。
  - 基于服务器已有 `1305` 条补数结果重新导出 SQL，`javascript:` 数量为 `0`，并完成一次带备份的单次导入。
  - 导入指标从 `4449` 降到 `4315`，生产库官方链接无效 URL 行数从 `68` 降到 `0`。
- 导入后官方链接剩余缺口：
  - 招生网缺 `247` 所。
  - 招生章程缺 `169` 所。
  - 专业目录缺 `346` 所。
  - 收费入口缺 `580` 所。
  - 收费摘要缺 `1083` 所。
  - 结构化解析未完成 / 待复核 `230` 所。
- 生产志愿生成压测：
  - `rank-check` 物理 / 历史均返回 `officialDataReady=true`，来源为贵州省招生考试院。
  - 物理 520 分、位次 39000、化学+生物：生成 `96` 条，专业级 `96` 条，官方选科命中 `93/96`，人工复核 `20`，证据链无无效链接。
  - 物理 500 分、位次 52000、政治+地理：生成 `96` 条，专业级 `96` 条，官方选科命中 `88/96`，人工复核 `17`，证据链无无效链接。
  - 历史 520 分、位次 13000、政治+地理：生成 `96` 条，专业级 `96` 条；因历史类官方选科要求缺失，`96/96` 全部进入人工复核，符合当前可靠性口径。
- 验证：
  - `python3 -m py_compile scripts/scrape_official_links.py` 通过。
  - `bash -n scripts/server/import_official_links.sh` 通过。
  - 后端 `./mvnw -q clean test` 通过，`31` 个测试 `0` 失败。
  - 注意：直接 `./mvnw -q test` 曾因本地 `target` 旧编译产物报 `VolunteerController cannot be resolved`，清理后通过；后续遇到同类问题先执行 `clean test`。

## 上一轮变更（v6.93，2026-04-27 16:47）

### 生产生成志愿 500 修复
- 现象：线上 `POST /api/volunteer/generate` 返回 `500`，浏览器网络面板显示 `/api/volunteer/generate` 失败。
- 根因：MySQL 执行 `data_major_score_gz` 专业分数线查询时需要写 `/var/tmp` 临时文件，但服务器根分区已满；日志报错：
  `Error writing file '/var/tmp/MYfd=142' (OS errno 28 - No space left on device)`。
- 止血处理：
  - 清理 `/tmp` 临时安装包、pip 缓存、过大的 Docker JSON 日志、旧后端 JAR 备份。
  - 使用 MySQL `PURGE BINARY LOGS TO 'binlog.000008'` 清理旧 binlog，根分区从 `100%` 降到 `83%`，可用空间约 `6.6G`。
  - 将 MySQL `binlog_expire_logs_seconds` 持久化为 `86400`，避免 binlog 默认保留 30 天继续撑满磁盘。
  - 清理 `/opt/gzly/backup` 中 importer 循环生成的大量重复 SQL/JAR 备份，仅保留少量最新备份；最终根分区降到 `73%`，可用空间约 `11G`。
- 防复发处理：
  - 停止并禁用 `gzly-official-importer.service`，避免 importer 循环重复导入 SQL 写爆 binlog。
  - 同步新版 `run_data_gap_supplement.sh` / `build_gap_seed.py` / `import_official_links.sh` 到服务器。
  - 生产 `gzly-data-gap-supplement.service` 改为 `LIMIT=200`、`RUN_IMPORTER=0`；定时任务保留抓取能力，但不再自动启动 importer。
  - 本地模板 `scripts/server/gzly-data-gap-supplement.service` 与 `data-gap-supplement.env.example` 已同步该口径。
  - `import_official_links.sh` 增加 `MAX_BACKUPS`，默认只保留最近 `10` 个自动备份。
- 验证：
  - `gzly` / `nginx` / `gzly-data-gap-supplement.timer` 均为 `active`；`gzly-official-importer.service` 为 `disabled/inactive`。
  - 生产 `POST /api/volunteer/generate` 复测返回 `code=0`、`96` 条志愿。
  - `build_gap_seed.py --limit 1` 使用 `/etc/gzly/data-gap-supplement.env` 可正常连库并生成 seed。

## 上一轮变更（v6.92，2026-04-26 20:48）

### 安全、运维与线上 500 收口
- 后端安全：
  - `RedisConfig` 改为 Jackson 多态白名单，新增反序列化攻击回归测试，拒绝 `java.io.File` 等非白名单类型。
  - `PublicRateLimitInterceptor` 增加 Redis 异常本地降级限流；只信任受控代理来源的 `X-Forwarded-For` / `X-Real-IP`。
  - 管理后台支持 `GZLY_ADMIN_PASSWORD_HASH`，登录失败锁定，管理员 JWT 默认缩短为 8 小时。
  - 新增后台志愿相关缓存清理接口，官方材料导入 / 更新后可主动清掉算法缓存。
  - `AlgorithmController` 补参数校验，`GlobalExceptionHandler` 统一处理约束校验错误。
- accessKey 与 AI 解读：
  - 新前端恢复方案改为 `POST /api/volunteer/plan`，不再把长期 `accessKey` 放进 URL。
  - 新增 `POST /api/volunteer/ai-analysis-ticket`，SSE 只携带 120 秒短效一次性 ticket；旧链接仅兼容恢复，并会自动移除 query 中的 `accessKey`。
  - 新增 `VolunteerControllerAccessSecurityTest` 覆盖 POST 恢复方案、ticket 换发和密钥校验失败。
- 前端安全与体验：
  - 新增 `SafeExternalLink.vue`，志愿证据链、院校详情、校友内容、后台链接统一做 `http/https` 净化与 `noopener,noreferrer`。
  - `VolunteerResult.vue` 点击导出时动态加载 `xlsx`，结果页首屏不再静态打包 Excel 解析库。
  - `index.html` 放宽 `user-scalable=no`，留言墙加载失败显示可见错误态。
- 运维脚本：
  - `run_data_gap_supplement.sh` 增加 `flock` 防双开，默认前台可追踪运行，不再默认后台 `nohup` / 粗暴 kill importer。
  - 新增 `scripts/server/gzly.env.example` 与 `gzly.service.example`，主服务凭证统一走环境文件模板。
  - 旧部署 / 爬虫脚本去掉硬编码密码和关闭 HostKey 校验的旧写法，改为 `accept-new` 或可配置环境变量。
  - 重新扫描仓库文本，未发现已知生产密码、JWT、Redis/DB 密码、AI Key、sshpass 明文参数残留。
- 验证与部署：
  - 本地后端 `./mvnw test` 通过：`31` 个测试 `0` 失败。
  - 本地前端 `npm run build` 通过。
  - Python / Shell 脚本语法检查通过：补数脚本、部署脚本、官方链接 / 照片爬虫。
  - 后端已用 `deploy_backend_safe.sh` 部署生产，健康检查 `/api/volunteer/metrics` 通过。
  - 前端 `dist` 已同步到 `/opt/gzly/frontend/dist/`，`nginx -t` 与 reload 成功。
  - 生产服务验证：`gzly=active`、`nginx=active`、首页 HTTPS `200`、`/api/announcement/current=200`、`rank-check officialDataReady=true`、生成接口返回 `96` 条志愿且人工复核清单 `31` 条；Playwright 打开首页与 `/volunteer` 均不再出现 500。

## 上一轮变更（v6.91，2026-04-26 20:17）

### 全局公告与接续修复清单
- 新增全局公告组件 `GlobalAnnouncement.vue`，挂载到 `App.vue`，公告不再只依赖首页加载；打开首页或直接进入 `/volunteer` 都会显示当前系统公告。
- 移除首页旧公告弹窗与 `localStorage` dismissed 逻辑；当前公告会在每次重新打开 / 刷新站点时显示。
- 前端 `npm run build` 通过，并已同步生产 `/opt/gzly/frontend/dist/`、重载 Nginx。
- 生产验证：
  - `https://gzly.dongsiwei.com/` 可看到“致即将奔赴高考的你们”系统公告。
  - `/volunteer` 直接打开也可看到同一公告。
  - `/api/announcement/current` 返回当前公告。
- 已完成一次全局只读体检，并把后续不足写入 `HANDOVER.md` 的“当前风险与下一步”；v6.92 已完成其中安全 / 运维主线，历史类选科要求和官方材料补数转入长期数据治理。

## 上一轮变更（v6.90，2026-04-26 19:43）

### 留言墙独立页面重做
- 根据线上视觉反馈，移除首页内嵌留言墙大模块，改为首页只保留“考生留言”入口卡片。
- 新增独立页面 `EncouragementWall.vue`，路由 `/encouragement`：
  - 独立顶部返回栏。
  - 加油墙 Hero。
  - 昵称 + 留言提交表单。
  - 最新留言卡片列表。
  - 手机端单列、桌面端三列留言卡片。
- 前端 `npm run build` 通过，并已部署生产。
- 生产验证：`https://gzly.dongsiwei.com = 200`，`/encouragement = 200`。

## 上一轮变更（v6.89，2026-04-26 19:40）

### 考生加油留言墙
- 新增后端留言墙：
  - `EncouragementMessageController`
  - `EncouragementMessage`
  - `EncouragementMessageMapper`
  - `db/20260426_encouragement_message.sql`
- 新增公开接口：
  - `GET /api/encouragement-messages?size=12`：读取最新已展示留言，最大 `30` 条。
  - `POST /api/encouragement-messages`：提交昵称和鼓励内容，限制 `4-120` 字，拦截链接、联系方式和明显广告/代填词。
- 新增限流配置：`encouragement-limit=6`、`encouragement-window-seconds=600`。
- 首页新增“考生加油墙”，支持手机和桌面响应式布局；考生可匿名或填昵称，发布后即时展示最新留言。
- 生产已执行 `biz_encouragement_message` 建表迁移，并部署前后端。
- 生产验证：
  - 首页 `200`。
  - `GET /api/encouragement-messages` 返回 `200`。
  - `POST /api/encouragement-messages` 返回 `code=0`，已写入一条示例鼓励。
  - 再次列表读取可看到该留言。

## 上一轮变更（v6.88，2026-04-26 19:31）

### Web / 手机端页面适配复查
- 启动本地 Vite 开发服务并安装 Playwright Chromium，分别截取手机 `390x844` 与桌面
  `1440x900` 的首页、志愿填报页截图。
- 首页与志愿填报页在手机/桌面首屏渲染正常；初次截图空白是因为动态路由未等待渲染，延迟截图确认正常。
- 修复 `VolunteerResult.vue` 底部操作栏在新增 3 个按钮后的响应式细节：
  手机端保留两列布局，主导出按钮独占一行；桌面端三按钮横排，宽度扩到 `540px`。
- 前端 `npm run build` 通过，已同步生产 `/opt/gzly/frontend/dist/`；
  生产首页和 `/volunteer` 复测均返回 `200`。

## 上一轮变更（v6.87，2026-04-26 19:26）

### 全量补数与运维安全继续收口
- 已启动生产全量 `official-missing LIMIT=0` 补数任务，本轮 seed 为 `1446` 所；
  当前 `scrape_official_links.py` 正在运行，使用 seed
  `data/official_links/gap_seed_official-missing_20260426192356.json`。
- 发现手动补数任务和 systemd importer 同时运行会产生两个 `import_official_links.sh` 循环；
  已清理手动启动的重复 importer，只保留 `gzly-official-importer.service` 管理的单进程。
- `gzly.service` 已迁移到 `/etc/gzly/gzly.env` 环境文件，数据库、Redis、AI、JWT、后台密码不再写在
  systemd unit 的 `ExecStart` 或 `Environment=` 明文行中；迁移后重启并通过健康检查。
- `AlgorithmService.calcProbability` 和 `AlgorithmService.assessRisk` 增加 Redis 缓存；
  `RedisConfig` 增加 `admissionProbabilities` 与 `riskAssessments` 6 小时 TTL，减少生成接口在热门请求下的重复历史线查询。
- 已重新安全部署后端，生产复测：`gzly=active`、首页 `200`、`/volunteer=200`、`rank-check officialDataReady=true`。

## 上一轮变更（v6.86，2026-04-26 19:21）

### 上线体检与部署目录清理
- 生产体检通过：`gzly`、`nginx`、`gzly-data-gap-supplement.timer`、
  `gzly-official-importer.service` 均正常；首页、`/volunteer`、`/api/volunteer/metrics`
  均返回 `200`。
- 当前没有 `scrape_official_links.py` 运行，补数抓取已结束；仅保留一个 systemd 管理的
  `import_official_links.sh` 进程。
- 将误同步到 `/opt/gzly/frontend/` 父目录的一批静态文件归档到
  `/opt/gzly/frontend/misplaced_static_backup_20260426192105`，保留 Nginx 实际根目录
  `/opt/gzly/frontend/dist`；归档后首页和 `/volunteer` 复测仍为 `200`。

## 上一轮变更（v6.85，2026-04-26 19:20）

### 服务器补数爬虫状态复查
- 生产 `/root/gzly_scraper/data/official_links` 首轮 `official-missing LIMIT=200` 已完成：
  `official_links_results.json` 与 `official_links_checkpoint.json` 均为 `200` 条。
- 字段覆盖统计（200 所）：招生网 `167`、招生章程 `192`、专业目录 `192`、收费信息/摘要 `192`；
  全部缺失 `8` 所，样例包括上海旅游高等专科学校、海军军医大学、红河卫生职业学院、
  中国人民武装警察部队特种警察学院、空军航空大学等。
- 导入日志显示缺口指标持续下降，最近一轮从 `4230` 降到 `4108`。
- 当前 `scrape_official_links.py` 已不在运行，说明首轮抓取结束；`gzly-official-importer.service`
  仍由 systemd 保持运行。
- 清理首轮任务遗留的孤儿 importer 进程，仅保留 systemd 管理的
  `/root/gzly_scraper/server/import_official_links.sh`；同时同步本地无默认明文密码版
  `import_official_links.sh` 与 `run_data_gap_supplement.sh` 到服务器，并重启 importer 服务。

## 上一轮变更（v6.84，2026-04-26 19:18）

### 生产部署与官方数据复核
- 已通过 `scripts/server/deploy_backend_safe.sh` 部署后端到生产：本地测试通过后上传临时 JAR，
  服务器备份旧 `/opt/gzly/backend/app.jar`，停服替换，重启并通过
  `http://127.0.0.1:8090/api/volunteer/metrics` 健康检查。
- 已重新部署前端 `dist` 到 Nginx 实际根目录 `/opt/gzly/frontend/dist/`；
  修正一次误同步到 `/opt/gzly/frontend/` 导致首页 500 的路径问题，最终首页和 `/volunteer` 均返回 `200`。
- 已同步历史类选科人工 CSV 标准化脚本、模板和文档到服务器：
  `/root/gzly_scraper/server/build_major_requirement_from_manual_csv.py`、
  `/root/gzly_scraper/data/major_requirements_gz_history.template.csv`、
  `/root/gzly_scraper/docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md`。
- 已同步无明文密码版 systemd 模板，并在服务器创建
  `/etc/gzly/data-gap-supplement.env`（权限 `600`）；`gzly-data-gap-supplement.timer`
  已重新 `daemon-reload` 并启用，下一次触发 `2026-04-27 03:30 CST`。
- 生产库核验：`data_score_rank_gz` 覆盖 `2025 历史类 580 行`、`2025 物理类 626 行`；
  `data_major_requirement_gz` 覆盖 `2025 物理类 24483 行`。
- 生产接口核验：
  - `systemctl is-active gzly = active`。
  - `https://gzly.dongsiwei.com = 200`，`/volunteer = 200`。
  - `rank-check` 返回 `officialDataReady=true`，520 分物理类官方位次区间 `38678-39239`。
  - `generate` 返回 `code=0`、`96` 条志愿、人工复核清单 `31` 条。

## 上一轮变更（v6.83，2026-04-26 19:10）

### 高考志愿可信度闭环补强
- 新增 `scripts/server/build_major_requirement_from_manual_csv.py`：
  面向历史类专业目录 PDF 正文编码错乱场景，支持把 OCR/人工 CSV 标准化为
  `data_major_requirement_gz` 可导入 CSV；学校名无法匹配、专业名为空或选科要求不明确的行会写入
  reject CSV，避免把不可靠数据硬导入生产。
- 新增 `scripts/data/major_requirements_gz_history.template.csv` 与
  `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md`，固定历史类官方选科要求的人工复核、标准化、导入和抽样核验流程。
- `GET /api/admin/stats` 新增 `volunteerQuality`：返回运行期生成成功率、失败数、不足 96 条数、
  生成耗时、质量警告触发数、人工复核触发数、AI 解读失败率，以及历史方案中带质量警告/人工复核清单的数量。
- `Dashboard.vue` 新增“志愿生成质量监控”卡片，后台可直接看到成功率、耗时、质量警告方案、
  人工复核方案等关键指标，不再只靠用户反馈发现问题。
- `VolunteerResult.vue` 增强志愿草稿工作台：已标记“保留 / 待查”的志愿可单独导出
  “人工核验草稿” Excel，自动排除“淘汰”项，并附带草稿状态、复核原因和官方证据链接。

## 上一轮变更（v6.82，2026-04-26 19:02）

### 交接口径修正与凭证脱敏
- 重写 `HANDOVER.md`：同步为当前公益免费定位，明确“不要恢复卡密系统”，移除过期的
  `CardKeyController` / `CardKeyService` / 卡密管理页描述，并补齐当前真实风险、部署命令和接手提醒。
- 批量脱敏 `DEV_PROGRESS_ARCHIVE_20260426.md` 与 `.tmp` 下历史验证脚本里的明文生产/管理密码，
  统一替换为 `<DB_PASS>`、`<REDIS_PASS>`、`<ADMIN_PASSWORD>`、`<TMP_PASSWORD>` 占位符。
- 重新扫描确认当前项目文本文件中不再包含已知明文密码字符串。

## 上一轮变更（v6.81，2026-04-26 18:56）

### 部署与运维安全优化
- 新增 `scripts/server/deploy_backend_safe.sh`：后端发布默认先本地 `./mvnw -q clean package`，
  再上传临时 JAR 到服务器，远端备份当前 `/opt/gzly/backend/app.jar`，停 `gzly` 服务后原子替换，
  重启并用 `HEALTH_URL` 做健康检查；健康检查失败时会自动回滚备份 JAR 并输出最近服务日志。
- `gzly-data-gap-supplement.service` 与 `gzly-official-importer.service` 不再在 systemd 模板中写明文
  `DB_PASS`，统一读取 `/etc/gzly/data-gap-supplement.env`；新增
  `scripts/server/data-gap-supplement.env.example` 作为服务器环境文件模板，实际密码只保留在线上机器。
- `scripts/server/import_official_links.sh` 去掉默认数据库密码，启动时未注入 `DB_PASS` 会直接失败，
  避免线上补数导入脚本隐式使用仓库内密码。
- 这是脚本与模板层优化，未主动改动当前线上 systemd 实例；下次更新服务器 unit 时需要同步安装
  `/etc/gzly/data-gap-supplement.env` 后再 `systemctl daemon-reload`。

## 上一轮变更（v6.80，2026-04-26 18:55）

### 生产故障修复
- 已修复线上 `https://gzly.dongsiwei.com` 500：根因是生产库缺少/未导入官方一分一段与选科要求数据，
  触发后端查询异常；同时线上前端 `index.html` 已确认存在，Nginx 首页返回 `200`。
- 生产后端已重新部署 `/opt/gzly/backend/app.jar` 并重启 `gzly` 服务；当前 `systemctl is-active gzly = active`，
  8090 正常监听。
- 清理生产前端 `dist` 内 macOS `._*` 附属文件，剩余数量 `0`。
- 新增并启用生产定时器 `gzly-data-gap-supplement.timer`：每天 `03:30` 自动运行官方材料缺口补数，
  下一次触发时间 `2026-04-27 03:30 CST`。

### 官方一分一段
- 已将 `scripts/data/export/score_rank_gz.sql` 导入生产 `data_score_rank_gz`。
- 生产库当前覆盖 `2025` 年贵州官方一分一段：
  - 历史类 `580` 行，分数 `0-668`，最大累计 `90087`。
  - 物理类 `626` 行，分数 `0-683`，最大累计 `207171`。
- 线上验证：`/api/volunteer/rank-check?totalScore=520&provinceRank=39000&firstSubject=物理`
  返回 `officialDataReady=true`，520 分物理类官方区间约 `38678-39239`，并保留“用户手填官方位次”的提醒。

### 官方选科要求
- 新增 `scripts/server/build_major_requirement_from_catalog.py`：
  从贵州省招生考试院 2025 物理类招生专业目录 PDF 抽取专业再选科目要求，
  生成 `scripts/data/major_requirements_gz.csv`。
- 新增 `scripts/server/gzly-data-gap-supplement.service` 与
  `scripts/server/gzly-data-gap-supplement.timer`，用于长期化官方材料补数。
- 已生成并导入生产 `scripts/data/export/major_requirements_gz.sql`，生产
  `data_major_requirement_gz` 当前 `24483` 条，均为 `2025 物理类`：
  `不限 11959`、`化学 12131`、`地理 145`、`政治 89`、`生物 159`。
- `MajorRequirementGzMapper` / `VolunteerService` 增加“精确匹配优先 + 专业核心名兜底匹配”，
  解决“物理学（师范类）”与官方目录“物理学”这类名称不一致。
- 线上验证：新生成方案 `96` 条中 `official_requirement=90`、`missing=6`，
  `manualReview=55`；医学、军警、限制说明等仍会进入强制人工复核清单。
- 历史类官方 PDF 正文抽取存在字体编码错乱，暂未批量导入；历史类/未命中项继续显示人工复核提示，
  后续需要 OCR 或人工 CSV 数据源。

### 验证
- 本地后端：`./mvnw test` 通过，`20` 个测试 `0` 失败。
- 本地前端：`npm run build` 通过。
- 本地后端强制重编译：`./mvnw clean package -DskipTests` 通过。
- 生产验证：
  - 首页 HTTPS/Nginx 返回 `200`。
  - `rank-check` 返回官方一分一段来源与区间。
  - `POST /api/volunteer/generate` 返回 `code=0`、`96` 条志愿。
  - `biz_plan_history` 已保存 `plan_json=96`、`manual_review_json=55`、
    `request_snapshot_json` 与 `metrics_json`。

## 上一轮变更（v6.79，2026-04-26 17:55）

### 后端
- 新增迁移 `db/20260427_plan_history_snapshot.sql`：把 `biz_plan_history`
  扩为带完整偏好快照、复核清单、监控指标和 `request_snapshot_json`，老库平滑兼容。
- `PlanHistory.java` / `schema.sql` 同步新字段；`VolunteerService.generate` 写入
  完整快照 + `manualReviewItems` + `metrics`；`toPlanResult` 反向恢复偏好。
- 新增 `OfficialLinkService` 批量加载 `uni_official_link`，让 `VolunteerItem`
  携带招生章程 / 专业目录 / 收费标准 / 选科要求来源链接，并自动打上人工复核标签。
- 新增 `VolunteerMetricsRecorder`（生成成功率、耗时、复核率、AI 失败率等），
  Service/AiService 在关键流程统一打点；`GET /api/volunteer/metrics` 暴露快照。
- 新增 `GET /api/volunteer/rank-check`：基于官方一分一段表给位次区间提示，
  系统不会替考生写入位次，并显式返回 `reminder` 文案。
- AI 解读改为受控结构化 JSON 输入：`AiService` 加 `SYSTEM_GUARDRAIL` + 强制段落模板；
  `VolunteerController.buildPlanSummary` 输出仅含事实字段的 JSON，并附带
  `manualReview[]` 与 `referenceProbabilityNotice`，禁用承诺性措辞。
- 新单测 `VolunteerServiceManualReviewTest`，覆盖证据链、复核清单、监控指标，
  现网共 20 个测试 0 失败。

### 前端
- 类型补 `ManualReviewItem` / `PlanMetrics` / `RankCheckResponse`，`VolunteerItem`
  增加证据链与复核标签字段。
- `VolunteerForm.vue`：手填位次 + 总分变化时调用 `rankCheck`，给出官方区间提示，
  不一致时显示醒目红色警告，并强调"系统不会自动写入位次"。
- `VolunteerResult.vue`：新增"参考概率"免责横幅、强制人工复核清单、
  每条志愿的证据链（招生章程/专业目录/收费标准/选科要求来源），
  以及志愿草稿工作台（每条志愿可循环切换"保留 / 待查 / 淘汰"，本地持久化）。
- `AiAnalysis.vue`：在 hero 之后展示参考概率免责声明 + 强制人工复核清单，
  与后端 system prompt 中的硬性免责措辞双向锁死。
- Pinia store 暴露 `manualReviewItems` / `planMetrics` / `referenceProbabilityNotice`
  并提供 `setPlanFromResponse` 一次性恢复完整方案，老的 `setPlanResult` 仍兼容。

### 文档
- 新增 `docs/ANDROID_ARCHITECTURE.md`：Kotlin/Compose + 现有 REST/SSE 的安卓架构方案、
  模块划分、SSE 集成、离线/分享、安全合规和 6 周路线图。

## 当前状态

- 生产后端：`/opt/gzly/backend/app.jar`，服务名 `gzly`，端口 `8090`，当前已部署并运行。
- 生产前端：`/opt/gzly/frontend/dist`，已通过 Nginx 对外服务。
- 服务器爬虫目录：`/root/gzly_scraper`。
- 最新一次生产验证：`rank-check officialDataReady=true`；
  `POST /api/volunteer/generate` 返回 `code=0`，生成 `96` 个志愿，人工复核清单 `31` 条。
- 官方材料补数已改为 systemd / 脚本可追踪流程；后续任务状态以服务器
  `/root/gzly_scraper/data/official_links/` 日志和 importer 服务为准。

## 最近完成

### v6.78 高考场景二次可信度核对

- 前端所有用户可见“录取概率”统一改为“参考概率”，生产前端静态资源已确认不再包含“录取概率/极高”强措辞。
- 后端概率等级去掉“极高/较高”等确定性表达，改为 `参考较高`、`参考中等偏高`、`参考中等`、`参考偏低`、`参考较低`。
- 新增 `AlgorithmServiceTest`，覆盖：
  - 单年样本必须返回 `单年参考`，概率限制在 `20%~80%`。
  - 无有效历史数据返回 `数据不足` 和 `0`。
  - 多年样本才进入历史分布参考等级。
- 已重新部署生产，并用 `/api/volunteer/generate` 验证返回 `96` 个志愿、选科复核警告正常、服务状态 `active`。

### v6.77 志愿填报准确度收口

- 修复院校级回退项算法错配：院校级不再把“本科批/普通类”当成专业名计算概率、风险和预测。
- 单年历史样本降级为 `单年参考`，概率限制在 `20%~80%`，避免过度确定。
- 分数推位次只做提示，不再自动写入全省位次；要求用户按官方一分一段表手动填写。
- 生成结果新增选科复核警告：统计未拿到官方再选科目要求的志愿项数量。
- 热门专业统计改为当前科类最新年份，不再写死 `2024`。

### v6.76 服务器补数脚本

- 新增 `scripts/server/build_gap_seed.py`：从 MySQL 导出缺口院校 seed。
- 新增 `scripts/server/run_data_gap_supplement.sh`：编排 seed 生成、官方链接爬虫和导入循环。
- 服务器已部署到 `/root/gzly_scraper/server/`。
- `official-missing` 全量缺口 seed 当前约 `1605` 所，首轮先跑 `200` 所。

### v6.75 数据完整性复查

- 院校基础库：`2198` 所。
- 院校级贵州录取线：`21789` 条，覆盖 `2196` 所。
- 专业级贵州录取线：`163297` 条，覆盖 `2190` 所。
- 贵州本省院校：`79/79` 院校级、专业级录取线均覆盖。
- 最大缺口：`data_major_score_gz.resubject_requirement` 当前仍为空，选科合规必须依赖官方补数。

## 当前风险

- P0：历史类官方选科要求仍未可靠导入，原因是 2025 历史类专业目录 PDF 正文编码错乱；
  历史类必须继续强制人工复核，后续需要 OCR 或人工 CSV。
- P1：物理类官方选科要求已导入，但仍可能因专业名称差异、特殊班型、艺术体育等少数项未命中；
  未命中项和特殊限制项继续显示人工复核。
- P1：官方材料补数仍在进行，部分学校解析不到招生网或目录，需要人工复核。

## 常用命令

### 后端本地验证

```bash
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY/gzly-server
./mvnw -q test
./mvnw -q package
```

### 前端本地验证

```bash
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY/gzly-web
npm run build
```

### 生产服务检查

```bash
systemctl is-active gzly
systemctl status gzly --no-pager
```

### 生成接口验证

```bash
curl -sS -X POST http://127.0.0.1:8090/api/volunteer/generate \
  -H 'Content-Type: application/json' \
  --data '{"totalScore":520,"provinceRank":60000,"firstSubject":"物理","resubjects":["化学","生物"],"strategyMode":"均衡型","decisionPriority":"专业优先","careerGoal":"就业优先","tuitionBudget":"均衡预算","acceptPrivate":false,"acceptSinoForeign":false}'
```

### 补数任务启动

```bash
cd /root/gzly_scraper
set -a
source /etc/gzly/data-gap-supplement.env
set +a
LIMIT=200 SCOPE=official-missing WORKERS=3 DELAY=1.2 RUN_IMPORTER=1 CLEAR_CHECKPOINT=1 bash server/run_data_gap_supplement.sh
```

### 补数日志

```bash
cd /root/gzly_scraper
python3 - <<'PY'
from pathlib import Path
p = Path('data/official_links/data_gap_official-missing.log')
print(p.read_text(errors='replace')[-2000:])
PY
```

## 下一步建议

1. 为历史类专业目录建立 OCR/人工 CSV 流程，再导入 `data_major_requirement_gz`。
2. 用物理类官方目录继续抽样核验：医学、公安、军警、师范、工学高频专业优先。
3. 首轮 `200` 所补数完成后，复查成功率，再决定是否全量跑 `LIMIT=0`。
4. 后续部署脚本应先停服务再覆盖 JAR，避免运行中覆盖包导致旧进程关闭钩子卡住。
