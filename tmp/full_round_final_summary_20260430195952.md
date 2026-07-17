# 四川 2025 数据补齐全量轮次收尾报告

- 生成时间：2026-04-30T19:59:52
- 省份/年份：SC / 2025
- 生成门禁：generationReady=False
- 官方链接覆盖：2198 所；官网 2182，招生网 1961，章程 2162，专业目录 2160，收费 2153。
- 学校官网发现：school_discovery=17496，high_confidence=409，review_queue=409。
- reviewed 校验：errors=0，warnings=6，payload=44。
- 生产专业组线：[{'subjectType': '历史类', 'rows': 9, 'groups': 9}, {'subjectType': '物理类', 'rows': 18, 'groups': 18}]；位次来源：[{'rankSourceType': 'original', 'rows': 22}, {'rankSourceType': 'score_rank_converted', 'rows': 5}]。
- 生产招生计划：[{'subjectType': '历史类', 'rows': 32, 'groups': 7}, {'subjectType': '物理类', 'rows': 108, 'groups': 14}]。
- 本轮新增导入：新余学院 5 条普通本科批B段专业组线，来源 http://zb.xyc.edu.cn/info/1013/4761.htm；无原始位次，按四川官方一分一段换算并标记 `score_rank_converted`。
- 未导入：新余学院招生计划 API 只有专业/科类/计划数，无院校专业组代码；无法可靠关联 101/102/103，继续进入缺口而不写生产。
- 剩余缺口：专业组线 63 组，专业组计划 69 组；四川智能生成继续锁定。

## 官方依据
- 2025 方案：https://www.sceea.cn/Html/202501/Newsdetail_4130.html
- 历史类一分一段：https://www.sceea.cn/Html/202506/Newsdetail_4334.html
- 物理类一分一段：https://www.sceea.cn/Html/202506/Newsdetail_4335.html
- 本科批B段投档新闻：https://www.sceea.cn/Html/202507/Newsdetail_4405.html
