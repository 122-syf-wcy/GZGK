# GZGK（志愿辅助）— 高并发新高考志愿决策引擎 × LangGraph ReAct + MCP 智能体平台

> Git 仓库：`https://github.com/122-syf-wcy/GZGK`

本仓库包含 **GZGK v2.0 全面重构架构**（Java 21 高并发引擎 + PostgreSQL 16 一库多模 + Python 3.12 LangGraph ReAct/MCP 智能体中台 + Vue 3 AI-Native 工作台）以及保留对照的 v1 生产基线目录。项目面向新高考考生提供公益志愿辅助服务，涵盖 8 省批次规则适配、毫秒级一分一段定位、96 志愿智能生成、2000 次蒙特卡洛沙箱仿真、招生章程 RAG 溯源与多专家并行深度诊断。

---

## v2.0 新架构目录总览

| 路径 | 技术栈与定位 |
|---|---|
| `contracts/` | 跨语言单一事实源：`core_tools.proto`、`ml_predict.proto`、`sse-protocol.schema.json`、`compliance-terms.json` |
| `backend-java/` | **Java 21 (Virtual Threads) + Spring Boot 3.3**：`gzgk-common`（算法内核/合规/ULID）、`gzgk-gateway`（WebFlux 零缓冲 SSE + Redis Lua 限流）、`gzgk-core`（多级缓存 + 11 阶段无状态 Pipeline + 分布式 SingleFlight + Resilience4j 熔断 + Kafka 异步削峰 + 乐观锁沙箱 + gRPC 服务） |
| `backend-python/gzgk-agent/` | **Python 3.12 + FastAPI + LangGraph**：自主多轮 ReAct `ToolNode`、动态 MCP Client、双层记忆（Checkpointer + 跨会话长期 Store）、三层 `tiktoken` 上下文压缩（`RemoveMessage` + 工具边界保护）、Hybrid RAG（PGVector HNSW + FTS RRF）、HITL `interrupt/resume`、Map-Reduce 三专家并行、滑动窗口流式合规护栏 |
| `backend-python/gzgk-mcp/` | **官方 MCP SDK 2.x Server**：暴露省份政策、专业就业/读研画像、体检与学费红线核查 Tools / Resources / Prompts |
| `backend-python/gzgk-ml/` | **独立 CPU 隔离 gRPC 服务**：LightGBM / `log-rank` 批量位次与机会指数推理 |
| `frontend/` | **pnpm Monorepo**：`packages/shared`（共享类型/合规/SSE Client）+ `gzgk-web`（Vue 3 + TS + TailwindCSS 暖纸白×深森绿 AI-Native 双栏工作台） |
| `infra/` | 远端服务器 Docker Compose（PG 16 `pgvector` + Redis 7 + Kafka 3.8 KRaft + Nacos + OTel/Prometheus/Grafana）与 k6 压测脚本 |
| `scripts/pg_etl/` | 真实数据集高速 `COPY` 导入 PG 16 与知识库语义切分入库脚本 |
| `docs/v2/` | 最新 v2.0 开发文档：[架构蓝图](docs/v2/ARCHITECTURE_BLUEPRINT.md) / [实施计划](docs/v2/IMPLEMENTATION_PLAN.md) / [UI 设计规范](docs/v2/UI_DESIGN_SPEC.md) |

> 旧版 v1 基线目录（`gzly-server/`、`gzly-web/`、`gzly-app/`、`ml-service/`、`HANDOVER.md`）继续保留作为算法与压测对比参照。

---

## 快速验证与启动

### 1. 自动化测试基线
```bash
# Java 21 多模块测试（算法 Golden + 64 虚拟线程 SingleFlight + 真实数据 96 志愿沙箱 + ArchUnit）
source scripts/dev/env.sh && (cd backend-java && mvn -B test)

# Python ML + LangGraph ReAct/MCP/记忆/压缩/HITL/红队测试
(cd backend-python/gzgk-ml && .venv/bin/pytest -v)
(cd backend-python/gzgk-agent && .venv/bin/pytest -v)

# 前端 39 项合规扫描 + TypeScript 类型检查 + Vite 生产构建
(cd frontend && pnpm check:compliance && pnpm typecheck && pnpm build)
```

### 2. 本机零容器轻量预览
```bash
bash scripts/dev/start_local_preview.sh
# 打开 http://127.0.0.1:5173
```

### 3. 远端服务器全栈底座部署与安全隧道
```bash
REMOTE_HOST=<ssh-host> bash scripts/deploy/deploy_all.sh
REMOTE_HOST=<ssh-host> bash scripts/dev/tunnel.sh
```

---

## 提交范围说明

仓库只提交源代码、配置样例（`.env.example`）、数据库迁移、脚本、文档和必要静态资产；严禁提交 `.env`、明文密码/API Key、构建产物（`target/`、`dist/`）、依赖目录（`node_modules/`、`.venv/`、`*.egg-info/`）及模型产物。
