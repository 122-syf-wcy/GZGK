# GZGK v2.0 全面重构实施计划与交付进度

> **开发与运维分工**：本地轻量编码与自动化单测（零重容器内存占用） + 远端服务器承载 PostgreSQL 16 / Redis 7 / Kafka 3.8 / 全栈压测（中间件仅绑定 `127.0.0.1`，通过 `scripts/dev/tunnel.sh` SSH 隧道安全联调）。  
> **当前范围**：优先交付 Web 端（`frontend/gzgk-web`），App 端暂缓（共享契约 `packages/shared` 与视觉稿已预留）。

---

## 阶段实施总览

| 阶段 | 核心交付内容 | 状态 | 实测验收基线 |
|---|---|---|---|
| **Phase 0 · 契约与远端基建** | JDK 21 环境脚本、`contracts/`（`core_tools.proto` / `ml_predict.proto` / `sse-protocol.schema.json` / `compliance-terms.json`）、`infra/docker-compose.yml`（分级 profile）、`infra/k6/` 压测脚本、SSH 隧道与同步脚本 | ✅ 已完成 | `protoc` 校验通过、Java/Python 桩代码生成通过、`docker compose config` 校验通过 |
| **Phase 1 · PostgreSQL 16 与真实数据 ETL** | Flyway `V1`–`V4`（`LIST` 省份分区、`RANGE` 按月分区、`JSONB/GIN`、`pgvector` HNSW + `pg_trgm`、`langgraph` Schema）；真实数据 `COPY` 导入脚本与种子知识库切片脚本 | ✅ 已完成 | 0.26s 干跑解析 2,198 院校、28,920 专业线、2,435 一分一段、24,483 选科要求、5 篇政策/章程 → 13 语义切片 |
| **Phase 2 · Java 21 网关与高并发引擎** | `gzgk-common`（算法内核 + 合规 + ULID）、`gzgk-gateway`（WebFlux + Lua 限流 + SSE Ticket）、`gzgk-core`（11 阶段无状态 Pipeline + 分布式 SingleFlight + Resilience4j ML 熔断 + Kafka 幂等消费 + 乐观锁沙箱与撤销 + gRPC 服务端） | ✅ 已完成 | `mvn test` **12/12 通过**：64 虚拟线程 SingleFlight 64:1 合并、真实数据生成 96 志愿、跨考生指纹缓存命中、409 乐观锁冲突与回滚、4 项 ArchUnit 边界测试 |
| **Phase 3 · Python ReAct + MCP Agent & ML** | `gzgk-ml` CPU 隔离 gRPC 服务；`gzgk-mcp` 官方 `mcp 2.3` MCP Server；`gzgk-agent` 自主多轮 ReAct `ToolNode`、动态 MCP Client、双层记忆（Checkpointer + 跨会话长期 Store）、三层 `tiktoken` 上下文压缩（`RemoveMessage` + 工具边界保护）、Hybrid RAG、HITL `interrupt/resume`、Map-Reduce 三专家并行与流式合规护栏 | ✅ 已完成 | `pytest` **9/9 通过**（`gzgk-ml` 1/1 + `gzgk-agent` 8/8）：覆盖 MCP 发现调用、跨会话记忆召回、工具边界防孤立压缩、自动与手动瘦身、2 轮 ReAct + HITL 确认、3 专家并行、跨分片违禁词 100% 拦截与语义缓存 |
| **Phase 4 · Vue 3 AI-Native Web 工作台** | `frontend/packages/shared` + `frontend/gzgk-web`：暖纸白 × 深森绿统一侧边栏壳体、AI 对话首页、新建方案与实时一分一段曲线、双栏 96 志愿虚拟滚动工作台（含 Copilot Token 用量条、一键压缩、Native+MCP 工具清单、长期记忆管理、HITL Diff 卡片、Excel 导出）、多专家深度诊断与位次阶梯图 + RAG 溯源抽屉、院校/分数线/政策查询 | ✅ 已完成 | `check:compliance` 39 项硬词 0 违规、`pnpm typecheck` 0 错误、`vite build` 1.26s 通过 |
| **Phase 5 · 远端全栈部署、压测与混沌演练** | 远端服务器巡检与容器拉起、执行 PG16 Flyway + 真实数据 `COPY` ETL + RAG 入库、运行 k6 读洪峰/同分热点穿透/SSE 并发压测、演练 ML 断连熔断 / LLM 限流隔离 / Redis 降级、沉淀 Grafana/Langfuse 截图与压测对比报告 | ⏳ 待远端服务器就绪 | 提供服务器 SSH 信息后立即执行 |

---

## 快速启动与部署命令

### 1. 本机零容器轻量预览（约占 300 MB 内存）
```bash
bash scripts/dev/start_local_preview.sh
# 启动 gzgk-core(:8082, 直读 scripts/data/ 真实数据) + gzgk-agent(:8001) + gzgk-web(http://127.0.0.1:5173)
```

### 2. 远端服务器部署全栈底座与安全隧道
```bash
# 同步配置与构建产物到远端 /opt/gzgk-v2
REMOTE_HOST=<your-ssh-host> bash scripts/deploy/deploy_all.sh

# 在远端服务器启动 PG16(pgvector) + Redis7 + Kafka3.8
cd /opt/gzgk-v2/infra && cp .env.example .env && docker compose --env-file .env --profile core up -d

# 本机建立安全 SSH 隧道（数据库端口仅监听远端 127.0.0.1，不裸奔公网）
REMOTE_HOST=<your-ssh-host> bash scripts/dev/tunnel.sh
```
