# GZLY 移动 App 重做方案（uni-app）

> 编写时间：2026-08-12
> 更新记录：2026-08-12 v2 —— 回填三项决策（打包路径、不用组件库、视觉独立），新增 D8 与 R9，排期由 7.5 周调整为 8 周
> 状态：**待评审**，评审通过后再开工
> 范围声明：本方案只覆盖**新建的 uni-app 客户端**（暂定工程名 `gzly-app`）。后端不做改造承诺，需要后端配合的项单独列在第九节；`gzly-web` 由另一条线负责，本方案不改它的代码。
> 前置事实：本仓库当前**没有任何 App 代码**。`docs/ANDROID_ARCHITECTURE.md`（v0.1，2026-04-26）是唯一的 App 相关产物，且已大面积失效，见第一节。

---

## 〇、结论摘要

一句话：**这不是"重构 App"，是"从零建 App"，而真正的工作量不在写页面，在于先把一份会动的后端契约钉死。**

三条主要判断：

1. **地基是空的。** 全仓库零 Gradle 工程、零 `AndroidManifest.xml`、零 `.kt/.dart/.swift`，也没有 uni-app 的 `manifest.json`。机器上 `D:\Android` 只是模拟器 AVD 目录。所以没有历史包袱，也没有任何可复用的客户端资产。
2. **唯一的设计文档有 9 处与现实相反**（第 1.2 节）。照它施工会主动引入两个已被修复的安全问题（accessKey 进 URL、卡密体系），并且做出一个物理上不可能的 UI（AI 解读打字机动画）。
3. **最大的复用杠杆是 `gzly-web` 的非 UI 层。** uni-app 走 Vue3 + Vite + TS + Pinia，与 `gzly-web` 同栈，`api/`、`types/`、`utils/`、`constants/` 四层可以高比例平移，省下的不只是工时，更是"两端算出来的方案顺序不一致"这类隐性 bug。UI 层则必须重写（Vant 不能用于 App 端，`div` 要换 `view`）。

---

## 一、现状盘点

### 1.1 为什么是"新建"而不是"重构"

| 检查项 | 结果 |
|---|---|
| 原生工程（`build.gradle` / `Podfile` / `pubspec.yaml` / `*.xcodeproj`） | 零命中 |
| uni-app 工程（`src/manifest.json` / `pages.json`） | 零命中（仓库里 4 个 `manifest.json` 全是省份数据导入载荷清单） |
| 客户端源码（`.kt` / `.swift` / `.dart`） | 零命中 |
| Hybrid 桥接残留（`capacitor` / `cordova` / `plus.` / `JSBridge` / `window.android`） | 零命中 |
| PWA（`manifest.webmanifest` / `sw.js` / `vite-plugin-pwa`） | 零命中 |

`gzly-web` 是一个纯浏览器 H5，没有任何为套壳预留的接口。

### 1.2 `ANDROID_ARCHITECTURE.md` 的失效清单

| 文档中的设计 | 当前事实 | 后果 |
|---|---|---|
| 卡密激活页 + `card-key/` 模块，卡密换 JWT | `/api/auth/card-key/*` 返回 HTTP 410，整套下线；C 端为**无账号匿名模型** | 整个模块作废 |
| deeplink `?planId=&accessKey=` 直达结果页 | 后端新增 `POST /volunteer/plan` 与 120 秒一次性 ticket，就是为把 accessKey 移出 URL；H5 拿到后立刻 `router.replace` 抹除 | 照做等于回退已修复的凭证泄漏 |
| 主接口 `/volunteer/generate` | 主链路是 `POST /volunteer/recommend`，只有它带就绪度门禁、政策校验和字段别名归一 | 用错接口会绕过门禁，在无数据省份拿到 500 |
| 单一贵州口径 | 8 省两种志愿单位：贵州 96 个「专业（类）+ 院校」，其余为 30/40/45/48 个「院校专业组」；海南是 3+3 且标准分 900 制 | 数据模型与表单、结果页全部要按省分支 |
| SSE 逐 token 流式，「5 秒未收到首字节」判繁忙 | 后端把上游响应**全部收完才发一个事件**，无增量 | 打字机动画不可实现；5 秒判超时会误杀（实际需等到 120 秒） |
| 错误走 `onFailure` | 错误也走 `data:`，靠 `[ERROR] ` 前缀识别，不存在 `event: error` | 错误态永远捕获不到 |
| Compose `captureToImage()` 出海报 | 后端已提供 `/export-long-image` 与 `/export-excel` 返回二进制 | 客户端出图应降为兜底 |
| 只提隐私弹窗 | 生成接口硬校验 `agreedDisclaimer=true` + `disclaimerVersion="2026-04-27-v1"` | 缺字段直接生成失败 |
| 未提合规词表 | 「录取概率」是 high 级违禁词，必须叫「机会指数」；`gzly-web` 有 CI 扫描脚本 | App 文案需受同一约束 |

**结论**：该文档评审通过后应标记为 `SUPERSEDED`，由本文件接替。

### 1.3 后端契约正处于变动中

`docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md` 的阶段 0 至阶段 4 已编码但**尚未提交**（工作区大量未跟踪文件，`git log` 最新提交停在 5 月）。新增的 `RecommendationOrchestrator`、`ProvinceReadinessService`、`ProvinceBatchSupportController` 都在这批未入库改动里。

**这是本方案最大的排期风险**，处理方式见第八节 M0 与第十节 R1。

---

## 二、技术选型与工程形态

### 2.1 选型

| 维度 | 选型 | 理由 |
|---|---|---|
| 框架 | uni-app（Vue 3 + Vite + TypeScript） | 已定。与 `gzly-web` 同栈，非 UI 层可平移 |
| 工程创建 | `npx degit dcloudio/uni-preset-vue#vite-ts gzly-app` | 官方 Vue3+TS 模板 |
| 状态 | Pinia | 与 `gzly-web` 同款，store 可直接移植 |
| 网络 | `uni.request` + 自建 `request.ts` 适配层 | 保持与 `gzly-web` 相同的函数签名，`api/*.ts` 近乎零改动 |
| 存储 | `uni.setStorageSync` + 方案缓存 JSON | 不引入 SQLite，缓存量级只有最近 5 份方案 |
| UI 组件 | **不引入第三方组件库，自建设计系统**（已决策） | 见 D8 |
| 首期目标端 | Android App | iOS 与小程序作为后续可选，见 Q3 |

### 2.2 与 `gzly-web` 的复用边界

这是本方案的核心杠杆，按可复用度分三档：

**A. 直接平移（改动 < 10%）**

| 来源 | 内容 | 备注 |
|---|---|---|
| `src/types/index.ts` | 域模型 TS 接口 | 需先做契约收敛，见 D3 |
| `src/api/*.ts` | 13 个 API 模块的函数与路径 | 仅换底层 request 实现 |
| `src/utils/volunteer-plan.ts` | **冲刺型/保守型排序算法** | 必须逐值一致，否则同一方案在 App 与 H5 上顺序不同 |
| `src/constants/provinces.ts` | 省份文案 | 结构化参数改为后端驱动，见 D4 |
| `src/utils/markdown.ts` 等 | 纯函数工具 | — |
| `scripts/check-compliance.mjs` | 合规词黑名单扫描 | 接进 App 的 CI |

**B. 逻辑复用、实现重写**

| 内容 | 原因 |
|---|---|
| `stores/volunteer.ts` 的三层兜底恢复机制 | 逻辑照搬，`localStorage` 换 `uni.setStorageSync` |
| 表单校验、位次防抖校验、梯度区间预设 | 逻辑照搬，UI 重画 |
| AI 解读双通道降级 | **简化为单通道**，见 D1 |

**C. 完全新写**

| 内容 | 原因 |
|---|---|
| 全部页面模板与样式 | Vant → 新组件库；`div/span` → `view/text`；固定 px → rpx |
| tabBar 与导航栈 | App 的形态差异，H5 没有 |
| 原生能力：文件保存、系统分享、deeplink、返回键拦截 | H5 没有 |
| 隐私合规双弹窗、未成年人提示 | 上架强制要求 |

**明确不做**：`views/admin/**`（13 页）与 `views/alumni/**`（3 页）不进 App，管理端继续走 Web。

### 2.3 目录结构

```
gzly-app/
├── src/
│   ├── pages/                  # 主包：首页 + 志愿主链路
│   ├── pages-query/            # 分包：院校/分数线/特殊招生
│   ├── pages-me/               # 分包：我的方案/免责/设置
│   ├── api/                    # 平移自 gzly-web
│   ├── types/                  # 收敛后的 DTO
│   ├── stores/                 # Pinia
│   ├── utils/
│   │   ├── request.ts          # uni.request 适配层
│   │   ├── volunteer-plan.ts   # 排序算法（与 web 保持逐值一致）
│   │   ├── compliance.ts       # 违禁词自检
│   │   └── native.ts           # 文件保存/分享/deeplink
│   ├── components/
│   ├── constants/
│   ├── static/
│   ├── pages.json              # 路由 + tabBar
│   └── manifest.json           # 应用配置、权限、签名、deeplink
└── package.json
```

分包是为了控制 App 首屏资源体积——`gzly-web` 有 8 个超过 1000 行的页面，全塞主包会显著拖慢冷启动。

---

## 三、关键技术决策

### D1 · AI 解读只走结构化 POST，不实现 SSE

**决策**：使用 `POST /volunteer/plans/{planId}/ai-analysis`（超时设 90 秒），**不实现 SSE 通道**。

**论证**（两条独立理由，任一条都足以成立）：

1. 后端根本没有增量流。它在内部消费上游的 `stream=true`，把所有 delta 拼进 `StringBuilder`，全部收完才一次性 `emitter.send()`，随后发 `[DONE]`。SSE 在这里带来的唯一差异是多一次 ticket 往返，用户体验完全相同。
2. uni-app 在 App 端的流式能力不可靠。`uni.request` 的 `enableChunked` + `onChunkReceived` 在 App 端长期未真正落地（社区反馈 `onChunkReceived` 为 `undefined`），通行绕法是把请求丢进 RenderJS 的 WebView 环境用 `XMLHttpRequest` 的 `readyState=3` 抓片段。为一个"本来就不流式"的接口引入 RenderJS，是纯负债。

**代价与兜底**：结构化接口失败时，H5 会降级到 SSE；App 端改为「重试 + 明确错误文案」。需要注意后端对 AI 解读有**单 IP 并发上限 2**，App 内要禁止同时打开多个解读任务。

### D2 · 网络层做成适配层，保住 `api/*.ts` 的原样

`gzly-web` 的 `api/request.ts` 只有 54 行，但有三条约定必须完整搬过来：

1. **响应形态是 `AxiosResponse`**，业务层取数一律 `res.data.data`（双层）。适配层要伪造同样的外壳，否则 13 个 api 模块全要改。
2. **`code !== 0` 转 reject**。这是本项目最主要的错误通道——业务失败是 **HTTP 200 + `code = -1`**，只看状态码会把失败当成功。
3. **Blob/二进制响应透传**，不走 code 校验。

App 端还要额外处理三件 H5 没有的事：

- `baseURL` 从相对 `/api` 改为**绝对域名**，且区分 dev/prod（H5 靠 Vite proxy，App 没有 proxy）。
- 后端配置了 `default-property-inclusion: non_null`，**所有 null 字段在 JSON 里直接消失**。所有 DTO 字段必须可空带默认值。
- 401/403/429 的响应体由拦截器裸写，**没有 `data` 字段**，形态与 `Result` 不同，需单独分支。

### D3 · 契约先收敛，不照抄前端 TS

`gzly-web` 的 `types/index.ts` 共 464 个字段、275 个可选（59.3%）；其中 `VolunteerItem` 75 个字段里 64 个可选（85%），并存 6 套语义重叠的评分字段（`recommendationScore` / `precisionScore` / `chanceScore` / `matchScore` / `dataConfidence` / `dataConfidenceScore`），另有 2 个已标注废弃。`HistoryRecord` 与 `AdvisorAdvice` 是 100% 可选，等于没有契约。

**做法**：M0 阶段对生产接口做一轮真实抓包，把字段分三类落进 `types/`：

- **必填层**：抓包中 100% 出现且 UI 主路径依赖的 → 声明为必填
- **展示层**：偶发出现、缺失时 UI 有兜底的 → 可选
- **废弃层**：不进 App 的 DTO

**理想解**：推动后端产出 OpenAPI，客户端 codegen。见第九节 B1。

### D4 · 省份能力全部后端驱动

前后端已经漂移：广西后端 40 前端 45、云南后端 40 前端 45、河南后端 48 前端 45，云南和广西的 `targetBatch` 名称也不一致。

**规则**：App **不硬编码任何结构化省份参数**。`targetCount`、`volunteerUnitType`、批次名、能否生成，一律取 `GET /volunteer/{provinceCode}/batch-support`。只有纯文案（省份名、hero 描述、官方来源名）留在本地常量。

入口置灰只看一个字段：`items[].generatorReady`。不要在客户端自己推 `LOCKED / QUERY_ONLY / ESTIMATE / FULL` 四态。另注意两条规则：只有 `batchCode == "NORMAL_UNDERGRADUATE"` 的批次可能 `generatorReady = true`；`LOCKED` 与 `QUERY_ONLY` 的 `supportLevel` 相同，要区分得读顶层 `readinessLevel`。

**当前 `FULL` 恒不返回**（后端注释「阶段 0 尚无判定来源」），所以最好的状态是 `ESTIMATE`，意味着**每一份方案都必须打「历史估算」角标**。

### D5 · 凭证存储与 deeplink

C 端无账号，`safetyCode` + `accessKey` 就是方案的全部所有权凭证。

- 存储：`uni.setStorageSync`，只存最近 5 份方案的 `{planId, safetyCode, accessKey, createdAt, 摘要}`。
- **凭证不进 URL、不进日志、不进分享内容**。所有方案接口优先用 `X-Plan-Safety-Code` / `X-Plan-Access-Key` 请求头，而不是 query 参数。
- deeplink 只接受 `planId`；打开后从本地存储取凭证，取不到则引导用户输入安全码认领。
- **换机即丢方案**是这个模型的固有缺陷。App 需要在生成成功后显式引导用户保存安全码（复制/截图/导出），并在「我的方案」页常驻找回入口。是否推动后端加轻量账号见 Q4。

### D6 · 导出走服务端，保存与分享走原生

优先调 `POST /volunteer/plans/{id}/export-long-image` 与 `/export-excel`，`responseType: 'arraybuffer'` 接收，再用原生能力落盘与分享。客户端出图（H5 的 `html2canvas` 路径）在 App 端不实现，服务端失败就提示重试。这样 App 不需要打包 `xlsx`（约 1.1 MB）和 `html2canvas`。

### D7 · 长列表性能是本项目 uni-app 的头号技术风险

贵州方案一次返回 **96 条** `VolunteerItem`，每条 75 个字段，结果页还要渲染梯度徽章、证据链、指标网格。uni-app App 端默认走 WebView 渲染，96 个复杂卡片一次性上屏在低端机上会明显卡顿。

**对策**：结果页必须做分页渲染或虚拟列表（按冲/稳/保/垫 tab 分片天然契合，每片 10–43 条），首屏只渲染当前 tab；卡片内的重内容（证据链、算法解释）折叠后按需展开。这一项在 M4 单独排期并做真机验证，不放到最后优化。

### D8 · 自建设计系统，视觉独立于 Web

**决策**：不引入 `wot-design-uni` / `uv-ui` 等第三方组件库，按 `C:\Users\ASUS\Desktop\UI` 参考图的方向（深色底 + 衬线标题 + 高对比编辑风）另立一套视觉，全部自绘。

**成本比直觉低的原因**：uni-app 已内置 `input` / `textarea` / `picker` / `switch` / `checkbox` / `radio` / `slider` / `scroll-view` / `swiper` 等表单与容器组件，`uni.showToast` / `showLoading` / `showModal` / `showActionSheet` 是原生 API。`gzly-web` 对 Vant 的依赖本就很浅——11 种组件、46 处使用，JS API 只用到 5 个。真正需要自建的是 `Popup` / `Overlay` / `Empty` / `Loading` / `Field` 包装这几件，一次做完全局复用。

**真正的增量在设计系统本身**：需要先定义 token（色板、字阶、间距、圆角、阴影、深色语义色）与一套基础组件，集中放在 M1 完成，因此 M1 从 1 周调整为 1.5 周，并在 M0 增加一次设计打样。

**必须提前解决的两个设计难题**：

1. **深色模式下的风险色可读性。** 结果页有 96 张密集数据卡，`riskColor` 用 `green` / `yellow` / `red` 三色编码，梯度还有冲/稳/保/垫四色。这套语义色在浅色底上是现成的，搬到深色底必须重新调明度与对比度，否则黄色在深底上几乎不可读。token 阶段就要把深色语义色定死，不能等到 M4 再补。
2. **衬线标题与中文的适配。** 参考图是英文衬线排版，中文衬线（宋体系）在移动端小字号下发虚。建议衬线只用于 hero 级大标题，正文与数据区一律无衬线。

**与 Web 的关系**：视觉刻意分叉，但**合规文案常量必须共用同一份**（第六节），这是不可分叉的部分。风险见 R8。

---

## 四、信息架构

App 相对 H5 最大的形态差异是**要有 tabBar**。H5 是「首页 → 逐级 push」的线性结构，App 需要给高频功能常驻入口。

```
tabBar（4 项）
├── 首页        省份入口 + 功能宫格 + 公告 + 在线人数
├── 志愿        表单入口 / 已有方案则直达结果页
├── 查询        院校查询 · 分数线 · 特殊类型招生（三合一）
└── 我的        我的方案 · 安全码找回 · 免责声明 · 设置 · 反馈
```

主链路仍是线性栈：`首页 → 地区工作台 → 志愿表单 → 志愿结果 → AI 解读 → 导出分享`。

---

## 五、页面清单

优先级：**P0** = 主链路不可缺；**P1** = 首个可用版本应有；**P2** = 可延后。

| # | 页面 | 路由 | 对应 H5 | 关键接口 | 优先级 |
|---|---|---|---|---|---|
| 1 | 首页 | `pages/index/index` | `Home.vue` (833) | `announcement/current`、`site-stats/online` | P0 |
| 2 | 地区工作台 | `pages/region/index` | `RegionHome.vue` (606) | `batch-support` | P0 |
| 3 | 志愿表单 | `pages/volunteer/form` | `VolunteerForm.vue` (2905) | `batch-support`、`rank-check`、`hot-majors`、`recommend` | P0 |
| 4 | 风险告知弹层 | 组件 | `DisclaimerDialog.vue` | — | P0 |
| 5 | 志愿结果 | `pages/volunteer/result` | `VolunteerResult.vue` (2455) | `volunteer/plan` | P0 |
| 6 | AI 深度解读 | `pages/volunteer/ai` | `AiAnalysis.vue` (1682) | `plans/{id}/ai-analysis`、`skills/ask` | P0 |
| 7 | 我的方案 | `pages-me/plans` | `MyPlans.vue` (535) | 本地存储为主 | P0 |
| 8 | 免责声明 | `pages-me/disclaimer` | `Disclaimer.vue` (175) | — | P0 |
| 9 | 院校查询 | `pages-query/university/list` | `UniversitySearch.vue` (740) | `university/list` | P1 |
| 10 | 院校详情 | `pages-query/university/detail` | `UniversityDetail.vue` (1699) | `university/{id}`、`official-links` | P1 |
| 11 | 分数线查询 | `pages-query/score-line` | `ScoreLineQuery.vue` (1094) | `score-line/schools`、`school-history`、`years` | P1 |
| 12 | 特殊类型招生 | `pages-query/special` | `SpecialAdmissions.vue` (839) | `special-admissions/*` | P1 |
| 13 | 院校对比 | `pages/volunteer/compare` | `VolunteerCompare.vue` (293) | `university/by-school-id` | P1 |
| 14 | 加油墙 | `pages-me/encouragement` | `EncouragementWall.vue` (554) | `encouragement-messages` | P2 |
| 15 | 反馈 | 组件 | 首页弹窗 | `feedback` | P2 |
| 16 | 隐私/权限首启弹窗 | 组件 | 无（App 新增） | — | P0 |
| 17 | 安全码找回 | `pages-me/claim` | `MyPlans` 内 | — | P0 |

**不做**：`PosterExport.vue`（改为服务端长图 + 原生分享）、`views/admin/**`、`views/alumni/**`。

**工作量提示**：H5 的 P0 主链路三页（表单 2905 + 结果 2455 + AI 1682）合计 7042 行，是全部工作量的重心。这三页要先做信息架构拆解再动手，不能照着 Vue 文件逐行翻译。

---

## 六、合规硬约束（App 必须内建）

这一节不是建议，是不满足就不能上线的清单。

1. **生成请求必须携带** `agreedDisclaimer: true` 与 `disclaimerVersion: "2026-04-27-v1"`。风险告知弹层必须**滚动到底部**才能确认（与 H5 一致）。版本号做成远端可配或跟随构建常量——后端一旦升版，硬编码旧值的 App 会全量失败，且失败形态是 HTTP 200 + `code=-1`，极易被误判成成功。
2. **禁止使用「录取概率」**，`chanceScore` 对外一律称「机会指数」。`calibratedProbability` 建议标为「参考概率（校准）」。
3. **禁止**「包过 / 保证录取 / 必上 / 稳了 / 零风险 / 闭眼报 / 命中率 / 上岸概率」等一切承诺性与绝对化措辞。
4. **每个方案页必须完整展示** `PlanResult.referenceProbabilityNotice`（后端下发，不要自己写）。
5. **AI 相关输出必须带**「本内容由 AI 生成，仅供参考。」
6. **张雪峰模块必须展示** `AdvisorAdvice.sourceNote` + 项目名 + 项目链接，明确「不扮演本人、不代表任何机构、不构成录取承诺」。
7. **位次必须用户手填**，系统不代填；`rank-check` 返回的 `reminder` 原样展示。
8. `manualReviewItems` 非空时必须显著提示，并提供 `evidenceLinks` 官方链接跳转。
9. 数据未就绪的省份入口必须置灰并说明缺什么，不得让用户点进去才报错。
10. 首启隐私政策 + 免责协议双确认，未同意不可使用；应用商店描述、推送文案、空状态文案同样受第 2–3 条约束（这些不经过服务端清洗）。
11. `scripts/check-compliance.mjs` 接入 App 的 CI，命中黑名单即构建失败。

---

## 七、打包与发版

### 7.1 当前环境

| 项 | 状态 |
|---|---|
| Node | v24.18.0 ✅ |
| npm | 11.16.0 ✅ |
| JDK | OpenJDK 21.0.11 LTS ✅ |
| Android SDK | `D:\android-sdk`，platforms `android-34` / `android-35`，build-tools `34.0.0` / `35.0.0` ✅ |
| **HBuilderX** | **未安装** ❌ |

### 7.2 两条路径

uni-app CLI **只能产出 H5 与 App 资源包（wgt），不能直接产出 APK**。可选：

**路径 A · HBuilderX 云打包**：安装 HBuilderX，导入工程，「发行 → 原生 App 云打包」。优点是简单、证书与图标配置可视化；代价是需要 DCloud 账号，且构建不在本地、无法进 CI。

**路径 B · 离线打包**：下载 uni-app Android 离线打包 SDK，用本机已有的 Android SDK + JDK 21 走 Gradle 出包。优点是可进 CI、可复现、不依赖第三方构建服务；代价是首次配置成本高（需申请离线打包 appkey、配置签名 keystore）。

**已决策：先 A 后 B。** M0 期间安装 HBuilderX，用云打包打通「能装到手机上」这件事，尽早暴露证书、图标、权限、包名这类配置问题；M6 前迁到路径 B，用本机 Android SDK + JDK 21 固化可进 CI 的发版链路。两条路径共用同一份 `manifest.json` 与签名 keystore，迁移成本主要在 Gradle 工程配置，不涉及业务代码。

### 7.3 上架前置

高考志愿类内容在应用商店属于教育敏感类目，各渠道普遍要求软著、ICP 备案、内容合规说明。这项与代码无关但会卡发版，需要尽早启动，见 R5。

---

## 八、里程碑

假设一人全职推进。每个里程碑都要求可运行、可验收，不接受"写完但没跑起来"。

| # | 里程碑 | 交付物 | 验收标准 | 预估 |
|---|---|---|---|---|
| **M0** | 契约冻结与骨架 | 工程初始化；生产接口抓包报告；收敛后的 `types/`；`request.ts` 适配层；HBuilderX 云打包跑通、能装到真机的空壳包；**3 张关键页面的高保真设计打样**（首页 / 志愿结果 / AI 解读） | 抓包覆盖主链路全部接口；空壳包在真机启动；设计打样确认视觉方向 | 1 周 |
| **M1** | 地基层与设计系统 | 设计 token（含深色语义色）、自建基础组件集、Pinia store、合规常量层、错误与限流统一处理、首页、地区工作台、首启隐私弹窗 | 8 省入口按 `generatorReady` 正确置灰；断网/429/500 有明确提示；基础组件在真机深色底下对比度达标 | 1.5 周 |
| **M2** | 查询三件套 | 院校查询与详情、分数线查询、特殊类型招生 | 三条链路在真机可用；分页与空态完整 | 1 周 |
| **M3** | 志愿表单 | 表单全字段、位次防抖校验、批次门禁、梯度区间、风险告知弹层 | 能成功调通 `/volunteer/recommend` 并拿到方案 | 1 周 |
| **M4** | 志愿结果 | 结果页（含长列表性能方案）、三种排序模式、草稿标记、院校对比 | **96 条在低端真机滚动无明显掉帧**；排序结果与 H5 逐条一致 | 1.5 周 |
| **M5** | AI 与导出 | AI 深度解读、skills 追问、服务端长图与 Excel 导出、原生保存与分享 | 解读全流程可用；导出文件能在系统文件管理器打开 | 1 周 |
| **M6** | 收口与发版 | 离线缓存、deeplink、我的方案与安全码找回、合规全量复检、打包链路固化、内测包 | 合规 CI 通过；内测包可分发安装 | 1 周 |

**合计约 8 周。** 原 `ANDROID_ARCHITECTURE.md` 的 6 周估算未包含契约收敛与长列表性能，且基于已失效的设计前提；本方案在其基础上增加了契约冻结（M0）与自建设计系统（M1 的 0.5 周增量）。

---

## 九、需要后端配合的事项

| # | 事项 | 必要性 | 说明 |
|---|---|---|---|
| B1 | 产出 OpenAPI / Swagger 文档 | 强烈建议 | 客户端 codegen，根除手写 DTO 漂移。当前 85% 可选字段的状态下，手工对齐必然出错 |
| B2 | 给 `POST /volunteer/recommend` 加限流 | 建议 | 主生成链路当前无 IP 限流，`/generate` 反而有。App 上线后请求量会上一个台阶 |
| B3 | 确认 `disclaimerVersion` 的升版通知机制 | 必须 | 或提供一个「当前有效版本」查询接口，避免 App 因硬编码旧版本全量失败 |
| B4 | 明确 `X-Plan-Safety-Code` / `X-Plan-Access-Key` 请求头长期支持 | 必须 | App 全程用请求头传凭证，不用 query |
| B5 | 补 `/api/score-lines/{province}/*`（复数）系列接口 | 视需求 | H5 在调但后端不存在，是死链。App 首期改用单数 `/api/score-line/*` 绕开 |
| B6 | 第三方院校资料代理（`gaokao.cn`） | 视需求 | H5 靠 Vite dev proxy 与 Nginx，App 直连会踩跨域与防盗链 |
| B7 | 就绪度 `FULL` 的判定来源 | 中期 | 当前恒不返回，App 只能全程标「历史估算」 |

---

## 十、风险登记册

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| **R1** | 后端多省重构未提交，契约在动 | 高 | M0 抓包基于**生产环境**而非本地工作区；契约变更走 B1 的 OpenAPI 而非口头同步；M0 之后每个里程碑开工前复核一次接口 diff |
| **R2** | 96 条长列表在低端机卡顿 | 高 | D7 的分片渲染方案；M4 里程碑单列并要求真机验收，不留到最后 |
| **R3** | 契约 85% 可选字段导致空指针与空白 UI | 中高 | D3 的三层收敛；所有字段可空带默认值；缺字段时 UI 必须有兜底而非空白 |
| **R4** | 打包链路未打通（无 HBuilderX） | 中 | M0 即打通最小可安装包，不拖到 M6 才发现出不了包 |
| **R5** | 应用商店上架资质（软著/备案/内容审核） | 中高 | 与开发并行启动，不阻塞编码但会阻塞发版 |
| **R6** | 无账号模型下换机丢方案 | 中 | D5 的安全码引导与找回入口；中期考虑 Q4 |
| **R7** | 排序算法在客户端，两端结果可能不一致 | 中 | `volunteer-plan.ts` 逐值平移并加单测；中期推动后端接管排序 |
| **R8** | 与 `gzly-web` 并行改造，文案分叉 | 中高 | 视觉已按 D8 刻意分叉，因此**合规文案常量必须共用同一份**，这是唯一不可分叉的部分。每个里程碑做一次与 Web 的文案一致性走查，`check-compliance` 用同一份黑名单 |
| **R9** | 深色视觉下密集数据可读性不达标 | 中高 | D8 的两个设计难题在 M0 打样阶段就要验证，不能等到 M4 才发现 96 张卡片的风险色在深底上分不清 |

---

## 十一、待决事项

### 已决策

| # | 问题 | 结论 |
|---|---|---|
| ~~Q1~~ | 打包方式 | **先 HBuilderX 云打包，M6 前迁离线打包**（第 7.2 节） |
| ~~Q2~~ | UI 组件库 | **不用组件库，自建设计系统**（D8） |
| ~~Q6~~ | 视觉方向 | **另立一套：深色 + 衬线标题 + 高对比**，不跟随 Web（D8） |

### 待决

| # | 问题 | 备选 |
|---|---|---|
| **Q3** | 是否同时出 H5 与小程序 | uni-app 天然多端，但小程序有域名备案与内容审核约束，且 H5 已有 `gzly-web`。建议首期只做 Android |
| **Q4** | 是否推动后端加轻量账号 | 当前无账号，换机即丢方案。若做，涉及后端改造与新的隐私合规面 |
| **Q5** | App 首期开放几个省 | A. 只开贵州（唯一 `ESTIMATE` 可生成的省）／B. 8 省全开但按 `generatorReady` 置灰（与 Web 一致，但大部分是灰的） |
