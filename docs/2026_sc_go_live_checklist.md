# 四川板块 2026 正式上线 Go-Live Checklist

> 起草时间：2026-05-17 16:30（v7.46 SC 17 非主流程批次 listing 兜底上线后）
> 适用：四川 SC 在 `https://gzly.dongsiwei.com/region/SC` 或 `http://39.97.232.141/region/SC` 正式对外开放
> 目标：把"代码已交付 + 数据未补齐 + 域名未备案"三件事拉齐到"普通考生可以放心使用"

## 一、当前已具备能力（v7.46 状态）

| 能力 | 已具备 | 备注 |
|---|---|---|
| 首页 4 省份卡片 SC 入口 | ✅ | `/region/SC` |
| RegionHome SC 4 功能卡 已开放 | ✅ | 院校查询 / 历年分数线 / 智能填报 / 特殊类型招生 |
| VolunteerForm SC 18 批次选择器 | ✅ | 默认 SC_BENKE_B（普通本科批 B 段） |
| 考生类别选择（普通 / 艺术 / 体育） | ✅ | SC 默认普通类 |
| 艺术 11 个统考类别下拉 + 综合分实时预览 | ✅ | `/api/volunteer/sc/composite-score` 端点 |
| SC 主流程生成（45 院校专业组） | ✅ | 当前数据 27 组 → 580/35000 命中 12 条 |
| 17 个非主流程批次 listing 兜底 | ✅ | dataQualityWarning 含公式 / 规则 / 资格说明 |
| AI 深度解读（SSE） | ✅ | 与贵州共用 mimo-v2.5-pro |
| 张雪峰.skill 对话 | ✅ | 与贵州共用 |
| Excel / 海报导出 | ✅ | 与贵州共用 |
| ProvincePolicyService.SC 多省配置 | ✅ | SC / HB / AH 同款 PROFESSIONAL_GROUP_45 |
| `policy_rule_config` SC 54 行 | ✅ | 18 批次 × 3 年 |
| `data_year_readiness` SC 3 行 | ✅ | 2024/2025/2026 |
| ML 训练 baseline 管线（ETL + 质量报告） | ✅ | `build_training_csv_sc.py` 跑通 |

## 二、上线前必须做的 P0（阻塞 Go-Live）

### P0-1 域名 ICP 备案修复

- **现象**：`https://gzly.dongsiwei.com` 80 端口被阿里云拦截返 `Non-compliance ICP Filing` 403；443 端口被 TCP RST
- **影响**：用户只能用 IP `http://39.97.232.141/` 访问，体验差（无 HTTPS、无品牌信任度）
- **修复路径**：阿里云控制台重新提交 ICP 备案 / 迁出大陆机房
- **代码层不可修复**

### P0-2 SC 主流程数据补齐到 ≥45 组

- **现状**：reviewed CSV 已 28 unique groups 全消化（生产 27 组）
- **目标**：补齐到物理类 45 + 历史类 45 = 90 unique groups
- **阻塞**：reviewed 已为空，需新一轮数据源
- **数据源 3 选 1**：
  - **A**：vision 模型抽取（需 VISION_BASE_URL/API_KEY/MODEL，按 `/root/gzly_scraper/sichuan_2025/README.md` 第 4-5 步）
  - **B**：人工复核 410 个高置信学校 review queue（耗时 ~10 小时 / 人）
  - **C**：等 2026 年 6 月下旬四川考试院官方数据（最稳妥）
- **建议**：上线前选 A 或 B 把数据推到 ≥45 组；C 是 fallback

### P0-3 SC 选科要求 OCR / CSV 导入

- **现状**：`data_major_requirement` SC 2025 行数 = 0
- **影响**：用户匹配组内 6 专业选科时全部进"人工复核"清单
- **修复**：按 `docs/HISTORY_MAJOR_REQUIREMENT_IMPORT.md` 同款流程
- **目标行数**：≥3000（覆盖主流程院校专业组的选科要求）

## 三、上线前推荐做的 P1（不阻塞，但提升体验）

### P1-1 SC 非主流程批次数据点采集

| 批次 | 数据源建议 | 优先级 |
|---|---|---|
| SC_ART_BENKE / SC_ART_ZHUANKE | 四川考试院艺术类公告 + 院校招生章程 | 中 |
| SC_SPORTS_BENKE / SC_SPORTS_ZHUANKE | 四川考试院体育类公告 + 院校 | 中 |
| SC_TIQIAN_B（公费师范 / 优师 / 免医 / 定向） | 教育部专项公告 + 院校招生章程 | 高 |
| SC_BENKE_A_NATIONAL / LOCAL / GAOXIAO_SPECIAL | 国家专项 / 地方专项 / 高校专项官方文件 | 中 |
| SC_BENKE_REGION_BALANCE / MINORITY_PRE | 四川教育考试院专项公告 | 低 |
| SC_TIQIAN_A（军事 / 公安 / 空军） | 各军兵种招生公告 | 低 |
| SC_BENKE_SPORTS_TEAM（高水平运动队） | 教育部高水平运动队认定名单 | 低 |
| SC_GAOXIAO_SPECIAL_PRE_B / SC_ZHUANKE_EARLY | 四川考试院公告 | 低 |

补齐后 `SichuanBatchListingService` 自动返回 N 条候选（已就绪）。

### P1-2 SC 院校官方链接补齐

- 当前 `uni_official_link` 与贵州共用全国 2198 所院校库（含 99% 主页 / 章程 / 专业目录）
- 推荐针对四川招生院校重点核对：四川大学 / 电子科技大学 / 西南交通大学 / 四川农业大学 / 西南财经大学 / 成都信息工程大学 等
- 工具：`scripts/server/run_data_gap_supplement.sh SCOPE=official-missing`

### P1-3 SC 特殊类型招生政策补齐

- 当前 `special_admission_policy` 共 8 条（与贵州共用）
- 推荐为 SC 单独补 ≥4 条覆盖艺术 / 体育 / 专项 / 综合分公式
- 工具：`SpecialAdmissionController POST /admin/special-admissions`

### P1-4 顺序志愿 / 艺体综合分 engine 真实实现

- 当前 `SichuanBatchListingService` 是统一 listing（按 group_line LIKE 关键词 + 公式说明）
- 等数据补齐后，可考虑拆分为：
  - `SichuanArtCompositeEngine`：按 `SichuanCompositeScoreCalculator` 计算每个考生的综合分 + 在 group_line 中按综合分 desc 排序候选
  - `SichuanSportsCompositeEngine`：同上
  - `SichuanSequentialCollegeEngine`：1+2 顺序志愿专属算法（按文化分 desc + 院校招生计划）
- 工程量：每个 200-500 行
- 优先级：数据齐前不做（无意义）

## 四、上线前推荐做的 P2（远期）

### P2-1 6 月下旬 2026 官方数据导入 + ML 重训

按 `docs/ops/sichuan_ml_baseline_runbook.md` Phase B2 全套命令操作：

1. 导入 2026 SC 官方招生计划 / 一分一段表 / 选科要求
2. `UPDATE data_year_readiness SET recommendation_phase='OFFICIAL_DATA_IMPORTED' WHERE province_code='SC' AND year=2026`
3. ETL：`build_training_csv_sc.py --train-years 2025,2026 --min-rows 50 --require-train-years --strict`
4. 训练：`train_models_on_server.py --models rank chance --csv ... --register --status draft`
5. 人工复核 quality report + 离线回测
6. `/api/admin/ml/models/{id}/activate` 激活
7. 更新 `data_year_readiness` SC 2026 `ml_training_ready=1` `recommendation_phase='MODEL_RETRAINED'`
8. SC_BENKE_B `supportLevel` 自动从 TRIAL_RECOMMEND 升 FULL_RECOMMEND

### P2-2 持续年份累加训练

每年高考结束后导入当年 SC 数据，`--train-years 2025,2026,2027`（累加），重训 + 激活。

## 五、上线前 smoke 测试清单

按下面顺序在生产实测，确保所有路径正常：

### 5.1 前端路径

- [ ] `/` 首页能看到 4 个省份卡片
- [ ] `/region/SC` 看到四川区域 4 功能卡（已开放）
- [ ] `/region/SC` → "智能填报" → `/volunteer?provinceCode=SC` 进入表单
- [ ] 表单显示考生类别 3 选 1（默认普通类）
- [ ] 表单显示 18 批次选择器（默认 SC_BENKE_B）
- [ ] 切到艺术类，显示艺术统考类别 11 个下拉
- [ ] 填文化 480 + 统考 280 + 美术与设计类 → 综合分实时显示 590.00
- [ ] 阅读风险告知 → 生成 → 进入 `/volunteer/result`
- [ ] 结果页显示志愿列表 + 数据缺口提示
- [ ] 切换批次（SC_ART_BENKE / SC_TIQIAN_A）重新生成，看到公式 / 规则说明
- [ ] AI 解读能正常调起（SSE 流式输出）
- [ ] Excel / 海报导出能下载

### 5.2 后端 API

- [ ] `GET /api/volunteer/sc/batch-support?year=2026` 返回 18 批次
- [ ] `GET /api/volunteer/sc/composite-score?candidateType=艺术类&...` 返回 590
- [ ] `POST /api/volunteer/recommend` SC + SC_BENKE_B 返回 ≥1 条志愿
- [ ] `POST /api/volunteer/recommend` SC + SC_ART_BENKE 返回 code=0（items 可能 0 + dataQualityWarning 完整）
- [ ] `POST /api/volunteer/recommend` SC + SC_TIQIAN_A 返回 code=0
- [ ] `POST /api/volunteer/recommend` SC + 任意 18 批次返回 code=0（无 500 / BizException）

### 5.3 数据库

- [ ] `SELECT COUNT(*) FROM policy_rule_config WHERE province='SC'` = 54
- [ ] `SELECT COUNT(*) FROM data_year_readiness WHERE province_code='SC'` = 3
- [ ] `SELECT COUNT(*) FROM data_admission_group_line WHERE province_code='SC'` ≥ 27（理想 ≥45）
- [ ] `SELECT COUNT(*) FROM data_score_rank WHERE province_code='SC'` ≥ 1055

### 5.4 运维

- [ ] `systemctl is-active gzly mysqld redis nginx` 全 active
- [ ] `/actuator/prometheus` 暴露 SC 缓存命中指标
- [ ] `/api/volunteer/metrics` 健康检查 200

## 六、上线公告草案

发布给用户的"四川板块开放公告"建议文案：

```
四川 2026 高考志愿辅助系统正式上线。

🎯 当前能力
• 18 批次完整支持：本科批 A/B 段、提前批、艺术 / 体育 / 8 类专项
• 主流程：普通本科批 B 段 45 个平行院校专业组志愿草稿
• 艺术 / 体育：综合分实时估算（按四川考试院 2026 公告公式）
• AI 深度解读 + 张雪峰.skill 对话
• Excel / 海报导出

⚠️ 当前状态
• 2026 官方数据尚未发布（约 6 月下旬），主流程基于 2024+2025 历史数据
• 部分艺术 / 体育 / 专项批次仅展示规则和数据缺口，等 6 月底数据齐后开放完整生成
• 数据持续补齐中，遇到任何缺口请反馈

📌 重要提示
• 本系统是公益辅助工具，所有数据仅供参考
• 最终志愿请以四川省教育考试院和高校招生章程为准
• 不提供录取保证，不替代考生本人决策

访问：https://gzly.dongsiwei.com/region/SC
（如域名暂不可访问，可用 http://39.97.232.141/region/SC 直接访问）
```

## 七、责任清单

| 项 | 负责人 | 截止 |
|---|---|---|
| ICP 备案修复 | 运维 | 上线前 |
| SC 数据补齐 27→45 | 数据团队 / 自动化 | 上线前（推荐） |
| SC 选科要求 OCR/CSV | 数据团队 | 上线前（推荐） |
| 17 非主流程批次数据采集 | 数据团队 | 持续 |
| 6 月底 2026 官方数据导入 + ML 重训 | 数据团队 + 运维 | 6 月下旬 |
| 上线 smoke 测试 | QA / 用户测试 | 上线日 |
| 上线公告发布 | 产品 / 运营 | 上线日 |

## 八、回滚预案

若上线后出现严重问题：

```bash
# 后端回滚（v7.46 → v7.43，回到只支持贵州的版本）
ssh root@39.97.232.141
ls /opt/gzly/backend/backup/ | tail -10
cp /opt/gzly/backend/backup/app.jar.<v7.43_timestamp>.bak /opt/gzly/backend/app.jar
systemctl restart gzly

# 前端回滚
ls /opt/gzly/frontend/ | grep dist.prev | tail -5
rm -rf /opt/gzly/frontend/dist
cp -r /opt/gzly/frontend/dist.prev.<timestamp> /opt/gzly/frontend/dist
nginx -s reload

# SC policy_rule_config 撤销（如有数据冲突，谨慎使用）
mysql gzly -e "DELETE FROM policy_rule_config WHERE province='SC'"
mysql gzly -e "DELETE FROM data_year_readiness WHERE province_code='SC'"
```

## 九、本文件版本

| 版本 | 时间 | 变更 |
|---|---|---|
| v1 | 2026-05-17 16:30 | 初版（v7.46 状态） |
