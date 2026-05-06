# 四川 2025 普通本科批B段官方数据缺口报告

更新时间：2026-04-28

## 结论

- 本轮仅登记和使用四川省教育考试院可核验来源，不使用第三方数据，不把高校官网分散信息写入生产表。
- 一分一段官方来源可抓取，但以图片发布，必须经过 OCR 草稿和人工复核后才能导入 `data_score_rank`。
- 普通本科批B段调档线公开页为投档汇总新闻，未发现完整院校专业组调档线明细；当前不写 `data_admission_group_line`。
- 招生计划公开页当前为招生计划更正通知，未发现普通本科批B段全量结构化招生计划；当前不写 `data_admission_group_plan`。
- 四川生成能力继续锁定，直到两科一分一段、B段专业组线、专业组计划均达到核验状态。

## 官方来源登记

- 2025录取方案：https://www.sceea.cn/Html/202501/Newsdetail_4130.html
- 2025招生实施规定：https://www.sceea.cn/Html/202505/Newsdetail_4261.html
- 历史类：https://www.sceea.cn/Html/202506/Newsdetail_4334.html，图片 18 张，状态：待 OCR 草稿 + 人工复核。
- 物理类：https://www.sceea.cn/Html/202506/Newsdetail_4335.html，图片 19 张，状态：待 OCR 草稿 + 人工复核。
- 普通本科批B段投档汇总：https://www.sceea.cn/Html/202507/Newsdetail_4405.html
- 招生计划更正一：https://www.sceea.cn/Html/202506/Newsdetail_4330.html
- 招生计划更正二：https://www.sceea.cn/Html/202506/Newsdetail_4338.html

## 下一步

1. 下载官方一分一段图片并生成 OCR 草稿。
2. 将 OCR 草稿整理为 reviewed CSV 模板字段。
3. 使用 admin dry-run 接口复核错误行，全部通过后再正式导入。
4. 若四川省教育考试院后续公开完整 B 段专业组调档线或计划表，再登记 hash 并进入同一复核导入流程。
