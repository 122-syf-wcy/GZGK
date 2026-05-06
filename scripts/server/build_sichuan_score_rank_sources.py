#!/usr/bin/env python3
"""Build Sichuan score-rank source registry SQL.

This script intentionally does not OCR official images into rank rows. Sichuan
2025 one-score-one-rank tables are published as images by the official site, so
the safe first step is to register every official image and optionally download
it for OCR/manual verification. Verified rows should then be imported into
data_score_rank.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_JSON = ROOT / "data" / "score_rank_sources_sc.json"
EXPORT_SQL = ROOT / "data" / "export" / "score_rank_sc_sources.sql"
RAW_DIR = ROOT / "data" / "raw" / "sichuan_score_rank_2025"
REVIEWED_DIR = ROOT / "data" / "reviewed" / "sichuan_score_rank_2025"
GAP_REPORT = ROOT / "data" / "reviewed" / "sichuan_official_gap_report_2025.md"
REVIEW_CSV_HEADER = "score,scoreLabel,segmentCount,cumulativeCount\n"


def esc(value: object) -> str:
    text = "" if value is None else str(value)
    return text.replace("\\", "\\\\").replace("'", "''")


def build_sql(sources: list[dict]) -> str:
    lines = [
        "-- 四川官方一分一段来源登记；图片需 OCR + 人工核验后再导入 data_score_rank。",
        "DELETE FROM data_source_registry WHERE province_code='SC' AND data_type='score_rank' AND year=2025;",
    ]
    for source in sources:
        image_urls = source.get("imageUrls") or []
        notes = f"{source.get('notes', '')} 图片数：{len(image_urls)}"
        source_url_blob = "\n".join(image_urls)
        lines.append(
            "INSERT INTO data_source_registry "
            "(province_code, province_name, year, subject_type, data_type, source_name, "
            "source_page_url, source_url, parse_method, status, row_count, notes) VALUES "
            f"('{esc(source.get('provinceCode'))}', '{esc(source.get('provinceName'))}', "
            f"{int(source.get('year'))}, '{esc(source.get('subjectType'))}', 'score_rank', "
            f"'{esc(source.get('sourceName'))}', '{esc(source.get('sourcePageUrl'))}', "
            f"'{esc(source_url_blob)}', '{esc(source.get('parseMethod'))}', "
            f"'{esc(source.get('status'))}', 0, '{esc(notes)}');"
        )
    return "\n".join(lines) + "\n"


def download_images(sources: list[dict]) -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for source in sources:
        subject = str(source.get("subjectType", "")).replace("/", "_")
        for idx, url in enumerate(source.get("imageUrls") or [], 1):
            suffix = pathlib.Path(url).suffix or ".jpg"
            target = RAW_DIR / f"sc_2025_{subject}_{idx:02d}{suffix}"
            if target.exists() and target.stat().st_size > 0:
                continue
            urllib.request.urlretrieve(url, target)


def write_review_templates(sources: list[dict]) -> None:
    REVIEWED_DIR.mkdir(parents=True, exist_ok=True)
    for source in sources:
        subject = str(source.get("subjectType", "")).replace("/", "_")
        target = REVIEWED_DIR / f"score_rank_sc_2025_{subject}_reviewed.csv"
        if not target.exists():
            target.write_text(REVIEW_CSV_HEADER, encoding="utf-8")

    readme = REVIEWED_DIR / "README.md"
    if not readme.exists():
        readme.write_text(
            "\n".join([
                "# 四川 2025 官方一分一段人工复核模板",
                "",
                "- 来源仅限四川省教育考试院历史类、物理类成绩分段统计表图片。",
                "- OCR 结果只能作为草稿，导入前必须人工逐页复核。",
                "- CSV 字段固定为 `score,scoreLabel,segmentCount,cumulativeCount`。",
                "- 分数按从高到低填写；后端导入会校验分数不重复、累计人数递增、本段人数与累计差值一致。",
                "- 复核完成后通过 `/api/admin/sichuan-data/score-rank/import?dryRun=true` 先 dry-run。",
                "",
            ]),
            encoding="utf-8",
        )


def write_gap_report(sources: list[dict]) -> None:
    GAP_REPORT.parent.mkdir(parents=True, exist_ok=True)
    score_rank_lines = []
    for source in sources:
        score_rank_lines.append(
            f"- {source.get('subjectType')}：{source.get('sourcePageUrl')}，"
            f"图片 {len(source.get('imageUrls') or [])} 张，状态：待 OCR 草稿 + 人工复核。"
        )
    GAP_REPORT.write_text(
        "\n".join([
            "# 四川 2025 普通本科批B段官方数据缺口报告",
            "",
            "更新时间：2026-04-28",
            "",
            "## 结论",
            "",
            "- 本轮仅登记和使用四川省教育考试院可核验来源，不使用第三方数据，不把高校官网分散信息写入生产表。",
            "- 一分一段官方来源可抓取，但以图片发布，必须经过 OCR 草稿和人工复核后才能导入 `data_score_rank`。",
            "- 普通本科批B段调档线公开页为投档汇总新闻，未发现完整院校专业组调档线明细；当前不写 `data_admission_group_line`。",
            "- 招生计划公开页当前为招生计划更正通知，未发现普通本科批B段全量结构化招生计划；当前不写 `data_admission_group_plan`。",
            "- 四川生成能力继续锁定，直到两科一分一段、B段专业组线、专业组计划均达到核验状态。",
            "",
            "## 官方来源登记",
            "",
            "- 2025录取方案：https://www.sceea.cn/Html/202501/Newsdetail_4130.html",
            "- 2025招生实施规定：https://www.sceea.cn/Html/202505/Newsdetail_4261.html",
            *score_rank_lines,
            "- 普通本科批B段投档汇总：https://www.sceea.cn/Html/202507/Newsdetail_4405.html",
            "- 招生计划更正一：https://www.sceea.cn/Html/202506/Newsdetail_4330.html",
            "- 招生计划更正二：https://www.sceea.cn/Html/202506/Newsdetail_4338.html",
            "",
            "## 下一步",
            "",
            "1. 下载官方一分一段图片并生成 OCR 草稿。",
            "2. 将 OCR 草稿整理为 reviewed CSV 模板字段。",
            "3. 使用 admin dry-run 接口复核错误行，全部通过后再正式导入。",
            "4. 若四川省教育考试院后续公开完整 B 段专业组调档线或计划表，再登记 hash 并进入同一复核导入流程。",
            "",
        ]),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true", help="download official images into scripts/data/raw")
    parser.add_argument("--review-templates", action="store_true", help="write reviewed CSV templates")
    parser.add_argument("--gap-report", action="store_true", help="write official data gap report")
    args = parser.parse_args()

    sources = json.loads(SOURCE_JSON.read_text(encoding="utf-8"))
    EXPORT_SQL.parent.mkdir(parents=True, exist_ok=True)
    EXPORT_SQL.write_text(build_sql(sources), encoding="utf-8")
    if args.download:
        download_images(sources)
    if args.review_templates:
        write_review_templates(sources)
    if args.gap_report:
        write_gap_report(sources)
    print(f"wrote {EXPORT_SQL}")
    if args.download:
        print(f"downloaded images into {RAW_DIR}")
    if args.review_templates:
        print(f"wrote review templates into {REVIEWED_DIR}")
    if args.gap_report:
        print(f"wrote gap report {GAP_REPORT}")


if __name__ == "__main__":
    main()
