# 四川 2025 数据补齐爬虫管线

该目录用于在服务器 `/root/gzly_scraper/sichuan_2025/` 运行四川专项数据补齐。默认只生成 `raw/`、`draft/`、`reviewed/`、`reports/`、`payloads/`，不会直接写生产表。

## 目录

- `raw/`：考试院和高校官网原始 HTML、图片、PDF/附件。
- `draft/`：OCR/视觉模型抽取草稿，不允许直接导入。
- `reviewed/`：人工复核 CSV，唯一允许生成 admin payload。
- `reports/`：来源审计、缺口、拒绝行、未匹配院校。
- `payloads/`：现有 admin import 接口的 JSON payload，必须先 dry-run 再正式导入。

## 推荐顺序

```bash
cd /root/gzly_scraper/sichuan_2025

./run_sichuan_pipeline.sh baseline
./run_sichuan_pipeline.sh official
./run_sichuan_pipeline.sh schools --limit 20 --timeout 12 --max-pages-per-school 6 --max-bytes 1200000
./run_sichuan_pipeline.sh review-queue

# 本地 OCR 只生成一分一段草稿与校验报告，不自动写 reviewed
./run_sichuan_pipeline.sh ocr-score-rank

# 视觉模型抽取需要 VISION_BASE_URL / VISION_API_KEY / VISION_MODEL
./run_sichuan_pipeline.sh extract --limit 10
./run_sichuan_pipeline.sh normalize
```

服务器默认 `python3` 可能较老，包装脚本会优先选择 `python3.11` 或 `/root/gzly_scraper/venv/bin/python`；也可以显式设置 `PYTHON_BIN=/root/gzly_scraper/venv/bin/python`。

人工复核 `draft/normalized/` 后，把确认过的数据填入：

- `reviewed/score_rank_sc_2025_物理类_reviewed.csv`
- `reviewed/score_rank_sc_2025_历史类_reviewed.csv`
- `reviewed/group_lines_sc_2025_reviewed.csv`
- `reviewed/group_plans_sc_2025_reviewed.csv`

`group_lines` 和 `group_plans` 的 reviewed CSV 需要保留来源字段：

- `sourcePageUrl`：本行数据所在的高校官网或考试院页面。
- `sourceUrl`：附件 / 图片 / PDF 等直接来源；没有附件时可与 `sourcePageUrl` 一致。
- `sourceHash`：来源文件或页面 hash，缺失时 payload 脚本会按 payload 内容生成 hash。
- `sourceLevel`：考试院完整结构化来源填 `manual_verified`；高校官网人工复核来源填 `school_verified`。

高校官网候选先看 `reports/school_candidate_review_queue.csv`：

- `ready_for_manual_extraction`：字段和批次都较完整，可以进入人工/模型二次抽取。
- `needs_manual_check`：可能有四川 2025 线索，但批次、专业组或字段不完整，需要人工判断。
- `reject_or_low_priority`：大概率不是目标批次、不是四川口径，或只是章程/聚合入口。

生成 dry-run payload：

```bash
./run_sichuan_pipeline.sh validate-reviewed
./run_sichuan_pipeline.sh payloads
```

执行 `reports/admin_dry_run_curl.sh` 前必须设置 `ADMIN_TOKEN`。所有接口先用 `dryRun=true`，只有 `rejected=0`、来源可核验、未匹配报告为空时才允许执行 `reports/admin_import_curl.sh`。

正式导入额外要求：

```bash
export CONFIRM_SICHUAN_IMPORT=SC_2025_REVIEWED
./reports/admin_import_curl.sh
```

导入顺序由 payload manifest 和 curl 脚本保证：一分一段、专业组调档线、招生计划。招生计划导入前必须已经存在对应专业组线，否则 admin 接口会拒绝。

## 来源边界

- 一分一段只接受四川省教育考试院来源。
- 普通本科批B段调档线和招生计划允许高校官网补充，`sourceLevel=school_verified`。
- 第三方聚合站不进入生产表。
- 视觉模型输出只能作为草稿，不能直接写入 `reviewed/`。
