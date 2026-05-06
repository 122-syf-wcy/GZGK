# 四川 2025 官方一分一段人工复核模板

- 来源仅限四川省教育考试院历史类、物理类成绩分段统计表图片。
- OCR 结果只能作为草稿，导入前必须人工逐页复核。
- CSV 字段固定为 `score,scoreLabel,segmentCount,cumulativeCount`。
- 分数按从高到低填写；后端导入会校验分数不重复、累计人数递增、本段人数与累计差值一致。
- 复核完成后通过 `/api/admin/sichuan-data/score-rank/import?dryRun=true` 先 dry-run。
