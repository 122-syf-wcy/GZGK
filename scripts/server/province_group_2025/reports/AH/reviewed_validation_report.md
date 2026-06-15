# 安徽 reviewed CSV 校验报告

- 错误数：0
- 警告数：5

## 计数

- score_rank: `{'rowsBySubject': {'历史类': 0, '物理类': 0}}`
- group_lines: `{'rows': 4282, 'groupsBySubject': {'历史类': 1324, '物理类': 2958}}`
- group_plans: `{'rows': 0, 'groupsBySubject': {'历史类': 0, '物理类': 0}}`

## 警告

- score_rank_ah_2025_历史类_reviewed.csv 为空；如果生产已导入一分一段，可忽略本地 reviewed 空表。
- score_rank_ah_2025_物理类_reviewed.csv 为空；如果生产已导入一分一段，可忽略本地 reviewed 空表。
- group_plans 历史类 当前 0 组，未达到解锁门槛 45 组。
- group_plans 物理类 当前 0 组，未达到解锁门槛 45 组。
- group_lines 有 4282 个专业组未在 group_plans 中找到招生计划：100 001 物理类；100 002 物理类；100 051 物理类；1006 001 历史类；1006 005 物理类；1006 006 物理类；1007 001 历史类；1007 001 物理类；1007 002 物理类；1007 003 物理类等。
