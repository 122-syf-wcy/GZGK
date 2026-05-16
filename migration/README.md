# GZLY 换服务器迁移工具包

> 生成时间：2026-05-16  
> 范围：仅含**公开院校数据 + 部署模板**，不含任何用户数据、密钥、证书。

## 一、本目录内容

```
migration/
├── README.md                          ← 本文件
├── data/
│   ├── gzly_university_public_*.sql.gz       公开院校数据 dump（11 MB gzipped）
│   └── gzly_university_public_*.sql.gz.sha256
├── schema/
│   └── gzly_schema.sql                       全库结构（63 张表的 CREATE TABLE，约 2000 行）
├── nginx/
│   └── gzly.conf.example                     Nginx vhost 模板（已替换域名为 <YOUR_DOMAIN>）
└── systemd/
    └── （直接复用项目根 `scripts/server/` 下已有的 .example）
```

### `data/` 包含的 27 张表（全部为公开来源数据）

| 表 | 内容 |
|---|---|
| `sys_university` | 院校基础库 2063 行 |
| `uni_official_link` | 院校官方链接（招生网/章程/专业目录/收费） |
| `sys_announcement` | 系统公告 |
| `data_score_line_gz` | 贵州院校级历年录取分数线 |
| `data_major_score_gz` | 贵州专业级历年录取分数线 162004 行 |
| `data_score_rank_gz` / `data_score_rank` | 贵州 + 多省官方一分一段表 |
| `data_major_meta_gz` / `major_info` | 专业元数据、专业目录 |
| `data_major_requirement_gz` / `data_major_requirement` | 选科要求 |
| `data_admission_plan_gz` | 贵州招生计划 |
| `data_admission_group_line` / `data_admission_group_plan` | 多省院校专业组线 / 计划 |
| `data_source_registry` | 数据源登记 |
| `data_year_readiness` / `data_year_readiness_batch` | 数据年份就绪度 |
| `major_requirement_match_gz` | 选科匹配 |
| `school_info` | 学校信息扩展 |
| `score_rank_segment` | 一分一段段位 |
| `skills_source` / `skills_document` / `skills_chunk` | 张雪峰 skills 公开摘要 |
| `policy_rule_config` | 政策规则配置 |
| `compliance_sensitive_word` | 合规敏感词词表 |
| `special_admission_policy` | 特殊招生政策 |
| `ml_model_registry` | ML 模型注册元数据 |

### **不在**本目录里的（敏感数据，需要从老服务器手动迁移）

| 类别 | 表 / 文件 | 迁移方式 |
|---|---|---|
| 用户生成方案 | `biz_plan_history`、`volunteer_plan*`、`volunteer_ai_analysis` | 老服务器 `mysqldump` 单独导出 |
| 用户账号 | `biz_user`、`biz_card_key`、`biz_university_qa`、`biz_user_feedback`、`biz_encouragement_message` | 同上 |
| 安全/审计 | `safety_code_identity`、`ai_analysis_compliance_log`、`ai_chat_message`、`skills_query_log` | 同上，可选迁移 |
| 配置 | `sys_ai_config`（**含 API key**）、`sys_alumni_admin` | 同上，必须迁 |
| 密钥 | `/etc/gzly/*.env`、`/etc/nginx/ssl/*` | scp + chmod 600，不入库 |

老服务器上还保留了完整 migration-kit：`/root/gzly-migration-kit-20260516_230335.tar.gz`，含**全量** dump 和**所有密钥**。换服务器时直接 scp 这一份 tar.gz 过去即可（**不要推 GitHub**）。

## 二、新服务器恢复步骤

> 前提：已经 `git clone` 本仓库到新服务器，并下载到对应目录。

### 1. 系统依赖

```bash
yum install -y java-17-openjdk-devel nginx mysql-server mysql redis python3.11 python3.11-devel
systemctl enable --now mysqld redis nginx
```

### 2. 部署目录骨架

```bash
mkdir -p /opt/gzly/{backend,backend/backup,backend/heapdumps,backend/uploads,frontend/dist,ml-service,logs}
mkdir -p /etc/gzly /etc/nginx/ssl
chmod 700 /etc/gzly
```

### 3. 数据库恢复

```bash
# 3.1 创建库 + 用户
mysql -uroot -p<NEW_ROOT_PW> -e "CREATE DATABASE gzly DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 3.2 建表结构（63 张表）
mysql -uroot -p<NEW_ROOT_PW> gzly < migration/schema/gzly_schema.sql

# 3.3 导入公开院校数据
gunzip -c migration/data/gzly_university_public_*.sql.gz | mysql -uroot -p<NEW_ROOT_PW> gzly

# 3.4 校验：应该看到 sys_university=2063, data_major_score_gz=162004
mysql -uroot -p<NEW_ROOT_PW> -D gzly -e "select 
  (select count(*) from sys_university) as universities,
  (select count(*) from data_major_score_gz) as major_scores,
  (select count(*) from data_score_rank_gz) as score_rank_gz;"
```

### 4. 还原密钥与用户数据（来自老服务器 migration-kit tar.gz）

```bash
# 4.1 scp tar.gz 过来（不走 git）
scp old_server:/root/gzly-migration-kit-20260516_230335.tar.gz /tmp/
tar xzf /tmp/gzly-migration-kit-20260516_230335.tar.gz -C /tmp/
KIT=/tmp/gzly-migration-kit-20260516_230335

# 4.2 env 文件
cp $KIT/etc/* /etc/gzly/
chmod 600 /etc/gzly/*

# 4.3 SSL 证书
cp $KIT/nginx/*.pem $KIT/nginx/*.key /etc/nginx/ssl/
chmod 600 /etc/nginx/ssl/*.key

# 4.4 systemd unit
cp $KIT/systemd/*.service /etc/systemd/system/
cp $KIT/systemd/*.timer /etc/systemd/system/ 2>/dev/null
systemctl daemon-reload

# 4.5 上传文件
rsync -a $KIT/uploads/ /opt/gzly/backend/uploads/

# 4.6 ML 模型（如果迁旧模型；新模型可服务器重新训练）
rsync -a $KIT/ml-models/ /opt/gzly/ml-service/

# 4.7 完整用户数据库 dump（覆盖第 3 步的空表）
gunzip -c $KIT/db/gzly_full_*.sql.gz | mysql -uroot -p<NEW_ROOT_PW>
```

### 5. Nginx 配置

```bash
# 复制并按本机域名修改
sed "s|<YOUR_DOMAIN>|gzly.dongsiwei.com|g" migration/nginx/gzly.conf.example > /etc/nginx/conf.d/gzly.conf
nginx -t && systemctl reload nginx
```

### 6. 启动应用

```bash
# 6.1 复制 JAR（从老服务器或 CI 出 release）
scp old_server:/opt/gzly/backend/app.jar /opt/gzly/backend/app.jar

# 6.2 启动后端 + ML
systemctl enable --now gzly gzly-ml

# 6.3 前端 dist
scp -r old_server:/opt/gzly/frontend/dist/ /opt/gzly/frontend/
```

### 7. 校验

```bash
# 健康检查
curl -fsS http://127.0.0.1:8090/api/volunteer/metrics

# DB checksum 复算
set -a; source /etc/gzly/gzly.env; set +a
mysql -uroot -p"$GZLY_DB_PASSWORD" -D gzly -N -e \
  "checksum table biz_plan_history, sys_university, data_score_rank_gz, data_major_requirement_gz, biz_encouragement_message"

# 对比 migration-kit/docs/db_checksum.txt 应一致
```

## 三、为什么数据切成两份

- `migration/data/`（推 GitHub）：100% 公开来源数据，可以版本化追踪；新服务器克隆仓库 + `gunzip + mysql` 即可还原。
- `migration-kit-*.tar.gz`（**绝不上 GitHub**）：含 `/etc/gzly/*.env`（明文 DB / Redis / JWT / AI API key / admin 密码）、SSL 私钥、用户生成方案、AI 解读、留言反馈等。这些只能走 `scp` 或加密信道传输。

如果想方便：可以把 tar.gz **GPG 加密**后丢 GitHub Release 私有附件，但不要明文推 main 分支。

## 四、Q&A

**Q: 为什么不直接把全库 dump 推上 GitHub？**  
A: 用户生成的方案里有 `safety_code`（恢复方案的访问令牌），AI 解读里有具体考生的分数/位次/院校组合，留言里有用户昵称。这些都属于用户数据，公开后会破坏既有用户的隐私和方案安全。

**Q: 这份公开数据多久过期？**  
A: 院校基础库、分数线、招生计划、一分一段都是**当年招生季公开数据**。等下一年新数据出来后这份可以归档。建议每招生季完成一轮入库后重新生成一份替换。

**Q: 新服务器没有老服务器 migration-kit 怎么办？**  
A: 公开院校数据可以从本目录还原；用户生成的历史方案、留言、反馈丢失（不可恢复）；admin 账号需重新创建；AI 配置需重新填写。
