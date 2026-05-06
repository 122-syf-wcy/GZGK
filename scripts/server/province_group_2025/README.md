# SC/HB/AH 2025 院校专业组采集管线

该目录用于服务器 `/root/gzly_scraper/province_group_2025/` 采集四川、湖北、安徽 2025 年院校专业组相关缺口材料。

采集阶段只生成：

- `raw/`：官方来源页、高校官网候选页、附件、图片等原始材料。
- `reports/`：官方来源审计、学校官网候选复核队列、采集摘要。

不会把采集结果直接写入生产表。正式入库必须走人工复核后的 `reviewed/` CSV、`payloads/` dry-run、再 import。

## 使用

> 服务器 `python3` 默认是 3.6.8，无法直接执行本目录脚本（`from __future__ import annotations` 等会 SyntaxError）。请通过 `run_province_group_pipeline.sh` 包装命令；脚本会按 `PYTHON_BIN` → `python3.11` → `/root/gzly_scraper/venv/bin/python` → `python3` 的顺序自动选择可用 Python。
> 如果需要直接调用 `.py`，请用 `python3.11 reviewed_payloads.py ...` 或 `PYTHON_BIN=/usr/bin/python3.11 ./run_province_group_pipeline.sh ...`。

```bash
cd /root/gzly_scraper/province_group_2025

./run_province_group_pipeline.sh baseline
./run_province_group_pipeline.sh official --province ALL --download-children --discover-limit 60
./run_province_group_pipeline.sh schools --province ALL --limit 80 --workers 4 --timeout 12 --max-pages-per-school 6 --max-bytes 1200000
./run_province_group_pipeline.sh vision-group-lines --province HB,AH --limit-images 4
./run_province_group_pipeline.sh init-reviewed --province ALL
```

也可以按省份单独采集：

```bash
./run_province_group_pipeline.sh all --province HB --limit 120 --workers 4
./run_province_group_pipeline.sh all --province AH --limit 120 --workers 4
```

## 复核产物

每个省份会生成：

- `reports/<省份>/official_source_audit.md`
- `reports/<省份>/official_source_audit.csv`
- `reports/<省份>/school_candidate_review_queue.csv`
- `reports/<省份>/school_candidate_summary.json`
- `draft/group_line_vision_draft.csv`
- `reports/group_line_review_queue.csv`
- `reports/group_line_vision_summary.json`

人工/模型抽取后，把人工确认过的数据填入：

- `reviewed/<省份>/score_rank_<sc|hb|ah>_2025_物理类_reviewed.csv`
- `reviewed/<省份>/score_rank_<sc|hb|ah>_2025_历史类_reviewed.csv`
- `reviewed/<省份>/group_lines_<sc|hb|ah>_2025_reviewed.csv`
- `reviewed/<省份>/group_plans_<sc|hb|ah>_2025_reviewed.csv`

`group_lines` 和 `group_plans` 的 reviewed CSV 必须保留来源字段：

- `sourcePageUrl`：本行数据所在的考试院、教育部门、授权发布页或高校官网页面。
- `sourceUrl`：附件 / 图片 / PDF 等直接来源；没有附件时可与 `sourcePageUrl` 一致。
- `sourceHash`：来源文件或页面 hash，缺失时 payload 脚本会按 payload 内容生成 hash。
- `sourceLevel`：省级官方/授权完整结构化来源填 `manual_verified`；高校官网人工复核来源填 `school_verified`。

`school_candidate_review_queue.csv` 的 `reviewClass`：

- `ready_for_manual_extraction`：高置信候选，可优先人工/模型抽取。
- `needs_manual_check`：有省份或批次线索，需要人工判断。
- `reject_or_low_priority`：低优先级或不符合目标批次。

湖北、安徽省级官方/授权发布页中的投档线图片可用视觉模型先生成草稿：

```bash
./run_province_group_pipeline.sh vision-group-lines --province HB,AH
```

该命令默认只写 draft 和 review queue，不会写 reviewed CSV，也不会导入生产表。若人工已经逐行复核 `reports/group_line_review_queue.csv`，可显式写入 reviewed：

```bash
./run_province_group_pipeline.sh vision-group-lines --province HB,AH \
  --write-reviewed \
  --confirm-reviewed-write OFFICIAL_GROUP_LINE_REVIEWED
```

注意：安徽图片中的 `投档人数` 只代表实际投档人数，不等同于招生计划。脚本会把相关说明写入 `parserNotes`，不得据此生成 `group_plans`。

旧四川专项管线 `scripts/server/sichuan_2025/reviewed/` 已存在人工复核数据时，可迁移到通用 SC reviewed 目录：

```bash
./run_province_group_pipeline.sh sync-sichuan-reviewed --overwrite
```

该命令只复制已人工复核的旧四川 CSV，不会从 draft/OCR 草稿自动生成 reviewed，也不会写生产表。

## 来源边界

- 一分一段优先使用省级考试院、教育厅或明确标注来源为考试院的授权发布页。
- 院校专业组调档线和招生计划允许高校招生官网补充，但必须人工复核后标记为 `school_verified`。
- 第三方聚合站只可作为线索，不进入生产表。
- 数据未补齐前，线上生成能力继续锁定。

## reviewed 校验与 payload

```bash
./run_province_group_pipeline.sh validate-reviewed --province ALL
./run_province_group_pipeline.sh payloads --province ALL
```

每个省份会输出：

- `reports/<省份>/reviewed_validation_report.json`
- `reports/<省份>/reviewed_validation_report.md`
- `payloads/<省份>/manifest.json`
- `reports/<省份>/admin_dry_run_curl.sh`
- `reports/<省份>/admin_import_curl.sh`

执行 dry-run 前必须设置 `ADMIN_TOKEN`。所有 payload 先用 `dryRun=true`，只有全部 `rejected=0`、来源可核验、未匹配院校为空时，才允许执行正式导入。

正式导入额外要求：

```bash
export CONFIRM_PROVINCE_IMPORT=SC_2025_REVIEWED  # HB/AH 分别替换为 HB_2025_REVIEWED、AH_2025_REVIEWED
./reports/SC/admin_import_curl.sh
```

导入接口走后端通用管理端点 `/api/admin/province-data/{provinceCode}`；旧四川 `/api/admin/sichuan-data` 仍保留兼容。
