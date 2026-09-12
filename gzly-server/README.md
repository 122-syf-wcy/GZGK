# GZLY Server - 贵州高考志愿公益辅助系统后端

> 工程口径以仓库根目录的 `HANDOVER.md` 与 `DEV_PROGRESS.md` 为准。本 README 仅做项目入口性概览。

## 项目定位

GZLY 是面向贵州、四川、湖北、安徽 2025 新高考考生的公益志愿辅助系统。**已不再使用卡密付费体系**，主流程对所有用户免费开放：院校查询、分数线查询、96/45 志愿生成、AI 解读、Excel/海报导出。

不要再按"卡密付费"理解，也不要恢复旧的 `CardKey*` 控制器、服务和管理页。

## 技术栈

- Java 17 + Spring Boot 3.2
- MyBatis-Plus 3.5
- MySQL 8.0 + Redis
- OpenAI 兼容 API（OkHttp）+ SSE 流式 AI 解读
- JWT（仅管理端 / AI SSE ticket）

## 快速启动（本地）

```bash
cd gzly-server

# 1) 初始化数据库（生产已存在，本地开发可跳过）
mysql -u root -p < src/main/resources/db/schema.sql

# 2) 配置环境变量或 application-local.yml
#   关键配置：spring.datasource.* / spring.data.redis.* / gzly.ai.*
#   AI 配置可由后台 sys_ai_config 表覆盖，环境变量为 fallback

# 3) 启动
./mvnw spring-boot:run

# 4) 单测
./mvnw -q test     # 当前 62 个测试，0 失败 0 错误
```

## 常见 API 路由

完整 API 以源码 `controller/` 为准。常用入口：

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET  | `/api/university/list` | 院校查询 | 公开 |
| GET  | `/api/university/{id}` | 院校详情 | 公开 |
| GET  | `/api/score-line/list` | 分数线查询 | 公开 |
| POST | `/api/volunteer/rank-check` | 位次区间提示 | 公开 |
| POST | `/api/volunteer/generate` | 生成 96/45 志愿 | 公开（强制 disclaimer） |
| GET  | `/api/volunteer/plan` | 通过短期 ticket 获取已生成方案 | ticket |
| GET  | `/api/volunteer/ai-analysis` | AI 解读（SSE） | ticket |
| GET  | `/api/volunteer/metrics` | 生成质量监控 | 管理员 JWT |
| GET  | `/api/special-admission/...` | 特殊招生（强基/综合评价等） | 公开 |
| POST | `/api/feedback/...` | 反馈/留言 | 公开 + 限流 |
| ALL  | `/api/admin/...` | 管理后台（含 `/api/admin/province-data/{code}/...` 等） | 管理员 JWT |
| POST | `/api/admin/login` | 管理员登录 | 公开 |

合规口径请参考根目录 `HANDOVER.md` 第五节"核心业务规则"。

## 项目结构

```
src/main/java/com/gzly/
├── GzlyApplication.java           # 启动类
├── common/                        # 统一响应/分页/异常/合规常量
├── config/                        # CORS、Redis、MyBatis、限流、鉴权拦截
├── controller/                    # 控制器层（含 ProvinceDataAdminController 等）
├── entity/                        # 实体类（PlanHistory 已扩为完整快照）
├── mapper/                        # MyBatis-Plus Mapper
├── service/                       # 业务逻辑（VolunteerService、ProfessionalGroupVolunteerService、AlgorithmService 等）
└── util/                          # JWT、签名、格式化工具
```

## AI 配置优先级

`AiConfigService` 优先读数据库 `sys_ai_config`（id=1，由后台管理页维护），缺失时回退到 `application.yml`/环境变量 `gzly.ai.*` / `GZLY_AI_*`。后台配置项包括：

- `provider_name`、`base_url`、`api_key`
- `chat_model` / `review_model` / `vision_model`
- `max_tokens`、`temperature`、`system_prompt`、`enabled`

> 提示：写入数据库时请确认 `vision_model` 真实支持图像输入（部分 reasoning 模型不支持 `image_url` content）。`gzly.env` 中的 `GZLY_AI_*` 仅在数据库未配置时生效。

## 运维

- 生产服务名：`gzly`，systemd 管理；端口 8090；健康检查 `/api/health`（公开存活探针，部署脚本 `HEALTH_URL` 默认指向它）。
- 部署脚本：仓库根 `scripts/server/deploy_backend_safe.sh`（含本地构建、上传、备份、健康检查、失败回滚）。
- 数据补数与官方材料治理：`scripts/server/run_data_gap_supplement.sh` 与 `scripts/server/province_group_2025/`。

## 提交前检查

```bash
./mvnw -q test          # 全集 62 测试
./mvnw -q package       # 强制重编译（必要时 -DskipTests）
```

新增接口或后台写操作时，先确认认证、授权、限流和审计口径；至少补一项控制器层 200/400/401 测试。
