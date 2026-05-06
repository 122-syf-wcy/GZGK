# GZLY 贵州高考志愿公益辅助系统

> Git 仓库：`https://github.com/122-syf-wcy/-`

本仓库包含 GZLY 项目的前端、后端、ML 服务、数据治理脚本和交接文档。项目面向新高考考生提供公益志愿辅助能力，包括院校查询、分数线查询、志愿方案生成、AI 解读和导出。

## 目录

| 路径 | 说明 |
|---|---|
| `gzly-server/` | Java 17 + Spring Boot 后端服务 |
| `gzly-web/` | Vue 3 + Vite + TypeScript 前端 |
| `ml-service/` | Python ML 预测服务 |
| `scripts/` | 数据导入、补数、部署和运维脚本 |
| `docs/` | 专项技术文档 |
| `HANDOVER.md` | 交接主文档 |
| `DEV_PROGRESS.md` | 当前进度与生产口径 |

## 交接入口

新同学接手时建议按顺序阅读：

1. `HANDOVER.md`
2. `DEV_PROGRESS.md`
3. `gzly-server/README.md`
4. `scripts/README.md`
5. `ml-service/docs/runbook.md`

## 提交范围说明

仓库只提交源代码、配置样例、数据库迁移、脚本、文档和必要静态资产；不提交 `.env`、密钥、构建产物、依赖目录、虚拟环境、缓存、临时文件、模型产物和原始大体积数据。
