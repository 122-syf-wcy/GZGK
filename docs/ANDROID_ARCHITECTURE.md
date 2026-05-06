# GZLY 安卓端架构设计 v0.1

> 适用范围：基于现有 H5 与后端的扩展，不替换 H5。
> 编写日期：2026-04-26
> 状态：待评审，等待确定上线时间表与品牌资产。

## 1. 目标与范围

- 复用现有 `gzly-server` REST + SSE 接口，最大限度避免后端二次重构。
- 与 H5 同一份品牌、同一份免责声明，强调"参考概率"口径，禁止"录取预测"措辞。
- 关键页面：首页 / 院校查询 / 分数线查询 / 志愿生成 / 志愿结果 / AI 解读 / 海报导出 / 卡密激活。
- 必须支持：① 离线缓存最近一份志愿方案；② 分享链接 deeplink 直达 `/volunteer/result?planId=...&accessKey=...`。
- 暂不计划：原生支付、实时聊天、IM；这些场景仍优先走 H5 内嵌或后期 v2。

## 2. 技术栈与版本

| 维度 | 选型 | 备注 |
|------|------|------|
| 语言 | Kotlin 1.9+ | 全 Kotlin，无 Java 代码 |
| UI | Jetpack Compose 1.7+ | Material3 + 自定义主题 |
| 编译 | Android Gradle Plugin 8.5+ | minSdk 24，targetSdk 35 |
| 架构 | MVI + Repository | 配合 ViewModel + StateFlow |
| 异步 | Kotlin Coroutines + Flow | SSE 解析使用 `OkHttp` + `Source` |
| 网络 | OkHttp 4.12 + Retrofit 2.11 + Moshi | JSON 与后端 `Result<T>` 对齐 |
| 持久化 | DataStore（首选）+ Room（方案缓存） | 不引入 SQLDelight |
| 依赖注入 | Hilt | 与 Compose 兼容良好 |
| 路由 | Compose Navigation | 通过 deeplink 复用 H5 链接结构 |
| 构建 | Gradle Kotlin DSL | 与 GitLab CI/Github Actions 集成 |

## 3. 模块划分

```
gzly-android/
├── app/                      # 应用入口、Theme、Navigation
├── core/
│   ├── network/              # OkHttp/Retrofit/SSE 封装
│   ├── domain/               # UseCase 与 Result 包装
│   ├── data/                 # Repository、本地缓存、错误码映射
│   ├── ui/                   # 通用组件（按钮/卡片/Banner）
│   └── analytics/            # 监控埋点（接现有 metrics 端点）
├── feature/
│   ├── volunteer-form/       # 表单页 ViewModel/Composable
│   ├── volunteer-result/     # 结果页 + 草稿工作台
│   ├── ai-analysis/          # SSE 流式 + 复核清单
│   ├── score-line/           # 分数线查询
│   ├── university/           # 院校列表/详情
│   ├── poster/               # 海报导出（基于 Compose 截屏）
│   └── card-key/             # 卡密兑换
└── buildSrc/                 # 版本目录与依赖收敛
```

## 4. 数据契约

- 端点：直接复用 H5 调用的全部端点（`/volunteer/generate`、`/volunteer/plan`、
  `/volunteer/history`、`/volunteer/ai-analysis`、`/volunteer/rank-check`、
  `/algorithm/*`、`/score-line/*`、`/university/*`、`/card-key/*`）。
- 鉴权：与 H5 一致，使用 `Authorization: Bearer <jwt>`。卡密激活后由后端签发 JWT。
- 错误码：`Result.code != 0` 一律走统一错误处理 → Snackbar 提示 + Sentry 上报。

### 4.1 关键 Schema 对齐

- `VolunteerPlan`、`VolunteerItem`、`ManualReviewItem`、`PlanMetrics`、`RankCheckResponse`
  全部通过 Moshi 生成 `data class`，命名与字段一一对应。
- 由于 `accessKey` 是分享链接的核心，必须与后端 `buildPlanAccessKey` 的算法保持兼容
  （仅做反序列化，不在客户端推算）。

## 5. SSE 集成方案

- AI 解读使用 SSE 流式接口：`GET /api/volunteer/ai-analysis?planId&accessKey&profile`。
- Android 实现：基于 OkHttp 的 `EventSource` 接口（`okhttp-sse` 模块），
  推荐通过 `callbackFlow { ... }` 把回调转换为 Kotlin Flow。
- UI：在 ViewModel 维护 `MutableStateFlow<AiState>`，组件通过 `collectAsStateWithLifecycle` 渲染。
- 失败兜底：监听 `onFailure`，若 5 秒内未收到首字节，提示"AI 服务繁忙"，
  并允许用户点击"重新分析"重试。
- 限流：与后端 `gzly.stability.ai-analysis-active-*` 字段一致，状态机：
  `IDLE → LOADING → STREAMING → DONE/ERROR`。

## 6. 离线与分享

- Room 中存最近 5 份方案：`(planId, accessKey, generatedAt, jsonBlob, manualReviewBlob)`。
- 启动时优先读取本地最新方案，再尝试调用 `/volunteer/plan` 同步最新版本。
- 分享：复用 H5 链接结构 `https://<host>/volunteer/result?planId=&accessKey=`，
  应用注册 deeplink，能从短信/微信/抖音直接打开 App 并跳到结果页。
- 海报导出：基于 Compose `captureToImage()`，输出 PNG，水印固定为
  "AI 生成 仅供参考，不构成录取预测"。

## 7. 安全与合规

- 关键数据（卡密、JWT）只放 EncryptedDataStore，不写明文 SharedPreferences。
- 网络请求强制 HTTPS，OkHttp `CertificatePinner` 钉住生产域名证书指纹。
- 关闭 WebView 远程调试（`setWebContentsDebuggingEnabled(false)` for release）。
- 隐私合规：首次启动展示《免责协议》+《隐私政策》二次确认弹窗，未同意不允许使用。
- 未成年模式：检测设备账号是否标记为未成年时，强制隐藏付费入口（与 H5 行为一致）。

## 8. 监控与稳定性

- 引入 Sentry Android SDK 收集崩溃、ANR、JS 错误（WebView 内嵌部分）。
- 接入后端 `/api/volunteer/metrics`：每次启动拉取一次，将
  `volunteer.generate.failure / volunteer.ai.failure` 等指标在管理后台可视化。
- 网络监控：OkHttp `EventListener` 记录请求耗时、失败原因、TLS 信息，按需上报。
- 慢日志：志愿生成 SSE 时长 > 5s 自动上报，复用现有
  `volunteer.generate.cost_ms_total` 指标的客户端版本 `volunteer.client.generate.cost_ms`。

## 9. 路线图（建议 6 周内）

| 周次 | 目标 |
|------|------|
| W1 | 项目骨架、主题、Navigation、网络层 |
| W2 | 院校查询 + 分数线查询 + 卡密激活 |
| W3 | 志愿表单（含官方位次校验）+ 数据持久化 |
| W4 | 志愿结果 + 复核清单 + 草稿工作台 |
| W5 | AI 解读 SSE + 海报导出 + 监控接入 |
| W6 | 性能/兼容/合规打磨 + 内测打包 |

## 10. 待评审事项

1. **是否同时上架华为、应用宝、小米**？影响合规弹窗与隐私政策配置。
2. **是否需要绑定手机号登录**？现阶段卡密体系已可使用，可暂缓。
3. **是否要内嵌 H5 兜底页**？建议保留 WebView 作为发布前 fallback。
4. **海报样式**：是否要新增"机构定制"模板？需要 UI 团队提供。
5. **AI 内容缓存策略**：是否在客户端持久化分析报告（涉及隐私，需法务确认）。
