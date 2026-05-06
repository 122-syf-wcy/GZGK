# GZLY 贵州高考志愿公益辅助系统交接文档

> 更新时间: 2026-05-06 19:49  
> 项目路径: `/Users/dongsiwei/Desktop/skills/projects/GZLY/`
> Git 仓库: `https://github.com/122-syf-wcy/-`

## 一、项目定位

GZLY 是面向贵州新高考考生的公益志愿辅助系统，核心目标是帮助考生完成：

1. 查院校。
2. 查历年分数线。
3. 生成 96 个“专业（类）+ 院校”平行志愿参考方案。
4. 查看 AI 解读。
5. 导出 Excel / 海报。
6. 回到院校详情页做官方材料核验。

当前项目已去掉卡密系统，主流程免费开放。不要再按“卡密付费系统”理解，也不要恢复旧的 `CardKeyController` / `CardKeyService` / 卡密管理页。

## 二、技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Vant 4 + Pinia + Vue Router |
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis |
| AI | OpenAI 兼容 API + SSE 流式输出 |
| 数据补充 | Python 3 + requests / Playwright / PyMuPDF |
| 部署 | Nginx + systemd，生产服务名 `gzly` |

生产口径以 `DEV_PROGRESS.md` 为准。完整历史流水在 `DEV_PROGRESS_ARCHIVE_20260426.md`。

## 三、Git 仓库与交接说明

- **远端仓库**: `https://github.com/122-syf-wcy/-`
- **本地根目录**: `/Users/dongsiwei/Desktop/skills/projects/GZLY/`
- **提交范围**: 仅提交源代码、配置样例、数据库迁移、脚本、文档和必要静态资产。
- **排除范围**: 不提交 `.env`、密钥、构建产物、依赖目录、虚拟环境、缓存、临时文件、模型产物和原始大体积数据。
- **交接口径**: 新同学克隆仓库后，优先阅读本文件、`DEV_PROGRESS.md`、`gzly-server/README.md`、`scripts/README.md` 和 `ml-service/docs/runbook.md`。

## 四、当前真实状态

### 主流程

主流程已经稳定可用：

- 院校查询。
- 分数线查询。
- 96 志愿生成。
- AI 解读。
- Excel / 海报导出。
- 院校详情页核验入口。

生成引擎采用“专业级优先 + 院校级回退”策略，不应简单理解为“缺一点专业数据就不可用”。

### 当前数据底座

- 院校基础库：`2198` 所。
- 院校级贵州录取线：`21789` 条，覆盖 `2196` 所。
- 专业级贵州录取线：`163297` 条，覆盖 `2190` 所。
- 贵州本省院校：`79/79` 院校级、专业级录取线均覆盖。
- 官方一分一段：已导入贵州省招生考试院官方 `2025 物理类/历史类` 与 `2024 物理类/历史类`，共 `2435` 行；`2023/2022/2021 文科/理科` 仅保留待核验来源配置，未用第三方数据入库。
- 官方选科要求：已导入 2025 物理类 `24483` 条，覆盖 `2018` 所；历史类仍未可靠导入。
- 官方链接证据链：`2198` 所均有记录；当前缺招生网 `247`、招生章程 `169`、专业目录 `346`、收费入口 `580`、收费摘要 `1083`、结构化解析待复核 `230`，无效 URL 行数为 `0`。

## 五、关键目录

### 后端 `gzly-server/`

```text
src/main/java/com/gzly/
├── GzlyApplication.java
├── common/                       # 统一响应、分页、异常
├── config/                       # CORS、Redis、MyBatis、限流、鉴权拦截器
├── controller/
│   ├── AdminController.java      # 管理后台 API
│   ├── AlumniController.java     # 校友共建 / 内容维护
│   ├── AlgorithmController.java  # 算法 API
│   ├── AnnouncementController.java
│   ├── FeedbackController.java
│   ├── ScoreLineController.java
│   ├── SpecialAdmissionController.java
│   ├── UniversityController.java
│   ├── UniversityQaController.java
│   └── VolunteerController.java  # 志愿生成 / AI 解读 / 位次校验
├── entity/
├── mapper/
├── service/
│   ├── AiConfigService.java
│   ├── AiService.java
│   ├── AlgorithmService.java
│   ├── OfficialLinkService.java
│   ├── ScoreLineService.java
│   ├── UniversityQaService.java
│   ├── UniversityService.java
│   ├── VolunteerMetricsRecorder.java
│   └── VolunteerService.java
└── util/
```

### 前端 `gzly-web/`

```text
src/
├── api/                          # Axios API 封装
├── components/
├── router/
├── stores/
├── styles/
├── types/
├── utils/
└── views/
    ├── Home.vue
    ├── UniversitySearch.vue
    ├── UniversityDetail.vue
    ├── ScoreLineQuery.vue
    ├── VolunteerForm.vue
    ├── VolunteerResult.vue
    ├── VolunteerCompare.vue
    ├── AiAnalysis.vue
    ├── PosterExport.vue
    ├── SpecialAdmissions.vue
    ├── admin/
    └── alumni/
```

### 脚本 `scripts/`

```text
scripts/
├── data/                         # 导入导出数据
├── server/                       # 服务器补数、导入、systemd 模板、部署脚本
├── scrape_*.py                   # 历史爬虫与数据补充脚本
└── requirements.txt
```

## 六、核心业务规则

| 规则 | 当前口径 |
|---|---|
| 高考模式 | 2024 起贵州新高考 “3+1+2” |
| 志愿模式 | “专业（类）+ 院校”，每个志愿是一个专业和一所学校 |
| 志愿数量 | 普通本科批 96 个平行志愿 |
| 禁止项 | 不得出现“是否服从专业调剂”选项 |
| 系统定位 | 公益免费辅助工具 |
| 概率口径 | 只能写“参考概率”，不能写“录取概率” |
| 位次口径 | 系统只提示一分一段区间，不自动替考生填写位次 |
| 生成确认 | `/api/volunteer/generate` 必须携带 `agreedDisclaimer=true` 与当前 `disclaimerVersion=2026-04-27-v1` |
| 结果口径 | 必须提示结合贵州省招生考试院与高校官方材料自主决策 |

## 七、当前风险与下一步

本节记录当前真实状态。v6.92 已把上一版列出的安全 / 运维主线全部落地并部署生产；剩余项主要是官方数据治理和中长期架构增强，不能靠猜测或硬导入假数据完成。

### 已完成的 P0 / P1 安全主线

1. **Redis 反序列化安全债已收口**  
   `RedisConfig` 使用严格白名单多态校验，新增测试确认非白名单类型不能反序列化。生产 Redis 仍应保持本机 / 内网访问、强密码、无外部写入面。

2. **前端外链净化已收口**  
   新增 `SafeExternalLink.vue`，志愿证据链、院校详情、校友内容、后台官方链接均做 `http/https` 净化；无效外链不再直接渲染，`window.open` 统一带 `noopener,noreferrer`。

3. **凭证治理已重新扫描**  
   历史归档和旧脚本中的已知生产密码 / JWT / Redis / DB / AI Key 已改为占位符；部署脚本不再硬编码 SSH 密码或使用 sshpass 明文参数。

4. **限流降级已完成**  
   Redis 异常时进入本地限流桶，不再直接放行；客户端 IP 只在可信代理来源下读取转发头。

5. **管理端认证已加强**  
   支持 `GZLY_ADMIN_PASSWORD_HASH`，保留旧明文环境变量兼容；登录失败会锁定，管理员 JWT 默认 8 小时。

6. **accessKey URL 暴露已收口**  
   新前端恢复方案走 `POST /api/volunteer/plan`；AI SSE 先换发 120 秒一次性 ticket，旧 query 链接仅兼容并会自动清理。

7. **补数和部署脚本已收口**  
   补数脚本使用 `flock` 防双开，默认前台可追踪运行；主应用补齐 `gzly.env.example` 与 `gzly.service.example`。

8. **志愿生成合规确认已强制后端校验**  
   前端点击生成会先弹出“生成前风险告知”，阅读到底部并确认后才请求接口；后端同步校验确认状态和版本，并在 `biz_plan_history` 留存 `disclaimer_version` 与 `disclaimer_confirmed_at`。当前版本为 `2026-04-27-v1`，后续修改文案必须递增版本。

### 仍需长期推进的数据项

1. **历史类官方选科要求**  
   历史类专业目录 PDF 正文编码抽取不可靠，不能把解析错误的数据硬导入。当前做法是继续显示强制人工复核，并使用 `scripts/server/build_major_requirement_from_manual_csv.py` + `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md` 接收 OCR / 人工 CSV。

2. **官方材料补数人工复核**  
   物理类官方选科已导入，但特殊班型、艺术体育、军警医学、专业名称差异仍可能漏命中。后台已补缓存清理接口，后续重点是补官方来源和人工抽样核验。

3. **中长期权限模型**  
   当前管理端已加哈希、锁定和短 JWT；中期仍建议迁移到集中权限策略，避免手写路径拦截持续膨胀。

4. **补数导入与磁盘容量**  
   2026-04-27 生成志愿 500 的根因是根分区满，MySQL 在 `/var/tmp` 写临时文件失败。已清理旧 binlog / 缓存 / 重复备份并把 `binlog_expire_logs_seconds` 设为 86400；`gzly-official-importer.service` 已禁用，`gzly-data-gap-supplement.service` 当前为 `RUN_IMPORTER=0`、`LIMIT=200`。后续如需导入官方链接 SQL，建议人工确认磁盘空间和 SQL 变化后单次导入，不要恢复 30 秒循环 importer。

5. **官方链接补数剩余缺口**  
   2026-04-27 已完成一次清洗后单次导入，缺口指标从 `4449` 降到 `4315`，并把历史脏链接 `javascript:` 等清到 `0`。剩余链接缺口需要继续跑 `official-missing` 补数，但必须保持 `RUN_IMPORTER=0`，补完后人工确认 SQL 再单次导入。

### 后续优化项

- 超大前端页面已先抽出安全外链组件并做包体优化；后续可继续拆 `UniversityDetail.vue`、`VolunteerForm.vue`、`AiAnalysis.vue` 的业务区块。
- `VolunteerResult.vue` 已改为点击导出时动态加载 `xlsx`。
- `index.html` 已放宽 `user-scalable=no`。
- `EncouragementWall.vue` 已补可见错误态；其他页面继续按新增功能逐步补空态。
- 后端已补 `AlgorithmController` 参数校验和接口层测试；后续新增接口必须同步补鉴权 / 限流 / 400 响应测试。

## 八、常用命令

### 后端验证

```bash
cd /Users/dongsiwei/Desktop/skills/projects/GZLY/gzly-server
./mvnw -q test
./mvnw -q package
```

### 前端验证

```bash
cd /Users/dongsiwei/Desktop/skills/projects/GZLY/gzly-web
npm run build
```

### 生产服务检查

```bash
systemctl is-active gzly
systemctl status gzly --no-pager
```

### 生成接口本机验证

```bash
curl -sS -X POST http://127.0.0.1:8090/api/volunteer/generate \
  -H 'Content-Type: application/json' \
  --data '{"totalScore":520,"provinceRank":60000,"firstSubject":"物理","resubjects":["化学","生物"],"strategyMode":"均衡型","decisionPriority":"专业优先","careerGoal":"就业优先","tuitionBudget":"均衡预算","acceptPrivate":false,"acceptSinoForeign":false,"agreedDisclaimer":true,"disclaimerVersion":"2026-04-27-v1"}'
```

### 补数任务手动启动

```bash
cd /root/gzly_scraper
set -a
source /etc/gzly/data-gap-supplement.env
set +a
LIMIT=200 SCOPE=official-missing WORKERS=3 DELAY=1.2 RUN_IMPORTER=0 CLEAR_CHECKPOINT=1 bash server/run_data_gap_supplement.sh
```

补数完成后先人工检查 `data/export/official_links.sql`，确认磁盘和指标，再用 `server/import_official_links.sh` 单次导入；不要恢复循环 importer。

### 后端安全部署脚本

```bash
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY
REMOTE_HOST=<server-host> bash scripts/server/deploy_backend_safe.sh
```

脚本会构建后端、上传临时 JAR、备份旧 JAR、停服务后替换、重启并健康检查。健康检查失败时会回滚旧 JAR。

## 八、验证基线

最近一次验证：

- 后端 `./mvnw test` 通过，62 个测试 0 失败 0 错误。
- 前端 `npm run build` 通过。
- 脚本语法检查通过：后端部署、补数、旧爬虫部署脚本。
- `scripts/scrape_photos.py`、`scripts/scrape_official_links.py` 可编译。
- 生产后端安全部署成功，`/api/volunteer/metrics` 健康检查通过。
- 生产前端已同步 `/opt/gzly/frontend/dist/` 并 reload Nginx。
- 生产生成验证：`rank-check officialDataReady=true`；`generate` 返回 `96` 条志愿、人工复核清单 `31` 条。
- 2026-04-27 复测：`generate` 返回 `code=0`、`96` 条志愿；根分区可用空间约 `11G`。
- 2026-04-27 17:10 复测：物理两组、历史一组生产生成均返回 `96` 条；物理官方选科命中 `88/96` 到 `93/96`，历史类因官方选科库缺失全部进入人工复核；证据链无无效链接。

## 九、接手提醒

- 不要恢复卡密系统。
- 不要把启发式参考概率包装成官方录取预测。
- 不要把历史类选科要求硬导入不可靠解析结果。
- 不要在文档、脚本、systemd unit 中写明文生产密码。
- 新增接口或后台写操作时，先确认认证、授权、限流和审计口径。
