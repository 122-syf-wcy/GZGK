#!/usr/bin/env python3
"""Build Guizhou official score-rank table import files from configured sources."""

from __future__ import annotations

import argparse
import csv
import json
import re
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Iterable

import pdfplumber
import requests


SOURCE_NAME = "贵州省招生考试院"
DEFAULT_SOURCE_CONFIG = Path("data/score_rank_sources_gz.json")
TABLE_DDL = """CREATE TABLE IF NOT EXISTS `data_score_rank_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '贵州' COMMENT '省份',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `score` SMALLINT NOT NULL COMMENT '分数',
  `score_label` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '原始分数段标签，如683及以上',
  `segment_count` INT NOT NULL DEFAULT 0 COMMENT '本段人数',
  `cumulative_count` INT NOT NULL COMMENT '累计人数(该分及以上)',
  `cumulative_rate` DECIMAL(7,3) DEFAULT NULL COMMENT '累计比例%',
  `rank_low` INT NOT NULL COMMENT '同分最好位次，累计人数-本段人数+1',
  `rank_high` INT NOT NULL COMMENT '同分保守位次，累计人数',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_file` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_year_subject_score` (`year`, `subject_type`, `score`),
  KEY `idx_year_subject_score` (`year`, `subject_type`, `score`),
  KEY `idx_year_subject_rank` (`year`, `subject_type`, `rank_low`, `rank_high`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州官方一分一段表';"""

@dataclass(frozen=True)
class ScoreRankSource:
    year: int
    subject_type: str
    source_url: str
    source_page_url: str
    source_file: str
    parse_method: str
    source_name: str
    enabled: bool = True


@dataclass(frozen=True)
class ScoreRankRow:
    year: int
    subject_type: str
    score: int
    score_label: str
    segment_count: int
    cumulative_count: int
    cumulative_rate: Decimal
    rank_low: int
    rank_high: int
    source_url: str
    source_page_url: str
    source_file: str
    parse_method: str
    source_name: str


def parse_score_label(value: str) -> tuple[int, str]:
    label = value.strip()
    match = re.search(r"\d+", label)
    if match is None:
        raise ValueError(f"Invalid score label: {value!r}")
    return int(match.group()), label


def parse_metric_cell(value: str | None) -> tuple[int, int, Decimal]:
    parts = [part.strip() for part in (value or "").splitlines() if part.strip()]
    if len(parts) != 3:
        raise ValueError(f"Invalid metric cell: {value!r}")
    return int(parts[0]), int(parts[1]), Decimal(parts[2])


def download_pdf(url: str, output_path: Path) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    response = requests.get(url, timeout=30, headers={"User-Agent": "Mozilla/5.0"})
    response.raise_for_status()
    if not response.content.startswith(b"%PDF"):
        raise ValueError(f"URL did not return a PDF: {url}")
    output_path.write_bytes(response.content)
    return output_path


def extract_rows(pdf_path: Path, source: ScoreRankSource) -> list[ScoreRankRow]:
    if source.parse_method != "pdf_table":
        raise NotImplementedError(
            f"Unsupported parse_method={source.parse_method!r} for {source.year} {source.subject_type}. "
            "Please convert/verify the source and use pdf_table or manual_verified imports."
        )
    rows: list[ScoreRankRow] = []
    pending_header: list[str | None] | None = None
    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            for table in page.extract_tables():
                for row in table:
                    if not row:
                        continue
                    first_cell = (row[0] or "").strip()
                    if first_cell == "分数":
                        pending_header = row
                        continue
                    if pending_header is None or "本段人数" not in first_cell:
                        continue
                    header = pending_header
                    metrics = row
                    pending_header = None
                    for score_cell, metric_cell in zip(header[1:], metrics[1:]):
                        if not (score_cell or "").strip() or not (metric_cell or "").strip():
                            continue
                        score, score_label = parse_score_label(score_cell)
                        segment_count, cumulative_count, cumulative_rate = parse_metric_cell(metric_cell)
                        rank_low = max(1, cumulative_count - segment_count + 1)
                        rows.append(
                            ScoreRankRow(
                                year=source.year,
                                subject_type=source.subject_type,
                                score=score,
                                score_label=score_label,
                                segment_count=segment_count,
                                cumulative_count=cumulative_count,
                                cumulative_rate=cumulative_rate,
                                rank_low=rank_low,
                                rank_high=cumulative_count,
                                source_url=source.source_url,
                                source_page_url=source.source_page_url,
                                source_file=pdf_path.name,
                                parse_method=source.parse_method,
                                source_name=source.source_name or SOURCE_NAME,
                            )
                        )
    return sorted(rows, key=lambda row: (row.year, row.subject_type, -row.score))


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def write_csv(rows: Iterable[ScoreRankRow], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "year",
        "province",
        "subject_type",
        "score",
        "score_label",
        "segment_count",
        "cumulative_count",
        "cumulative_rate",
        "rank_low",
        "rank_high",
        "source_name",
        "source_url",
        "source_page_url",
        "source_file",
        "parse_method",
    ]
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "year": row.year,
                    "province": "贵州",
                    "subject_type": row.subject_type,
                    "score": row.score,
                    "score_label": row.score_label,
                    "segment_count": row.segment_count,
                    "cumulative_count": row.cumulative_count,
                    "cumulative_rate": str(row.cumulative_rate),
                    "rank_low": row.rank_low,
                    "rank_high": row.rank_high,
                    "source_name": row.source_name,
                    "source_url": row.source_url,
                    "source_page_url": row.source_page_url,
                    "source_file": row.source_file,
                    "parse_method": row.parse_method,
                }
            )


def write_sql(rows: Iterable[ScoreRankRow], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        handle.write("-- Generated from official Guizhou score-rank PDFs. Re-runnable by unique key.\n")
        handle.write(TABLE_DDL + "\n\n")
        handle.write("INSERT INTO `data_score_rank_gz` (\n")
        handle.write("  `year`, `province`, `subject_type`, `score`, `score_label`,\n")
        handle.write("  `segment_count`, `cumulative_count`, `cumulative_rate`, `rank_low`, `rank_high`,\n")
        handle.write("  `source_name`, `source_url`, `source_page_url`, `source_file`, `parse_method`\n")
        handle.write(") VALUES\n")
        values: list[str] = []
        for row in rows:
            values.append(
                "("
                f"{row.year}, '贵州', {sql_quote(row.subject_type)}, {row.score}, {sql_quote(row.score_label)}, "
                f"{row.segment_count}, {row.cumulative_count}, {row.cumulative_rate}, {row.rank_low}, {row.rank_high}, "
                f"{sql_quote(row.source_name)}, {sql_quote(row.source_url)}, {sql_quote(row.source_page_url)}, "
                f"{sql_quote(row.source_file)}, {sql_quote(row.parse_method)}"
                ")"
            )
        handle.write(",\n".join(values))
        handle.write("\nON DUPLICATE KEY UPDATE\n")
        handle.write("  `score_label` = VALUES(`score_label`),\n")
        handle.write("  `segment_count` = VALUES(`segment_count`),\n")
        handle.write("  `cumulative_count` = VALUES(`cumulative_count`),\n")
        handle.write("  `cumulative_rate` = VALUES(`cumulative_rate`),\n")
        handle.write("  `rank_low` = VALUES(`rank_low`),\n")
        handle.write("  `rank_high` = VALUES(`rank_high`),\n")
        handle.write("  `source_name` = VALUES(`source_name`),\n")
        handle.write("  `source_url` = VALUES(`source_url`),\n")
        handle.write("  `source_page_url` = VALUES(`source_page_url`),\n")
        handle.write("  `source_file` = VALUES(`source_file`),\n")
        handle.write("  `parse_method` = VALUES(`parse_method`);\n")


def load_sources(config_path: Path, years: set[int] | None) -> list[ScoreRankSource]:
    with config_path.open("r", encoding="utf-8") as handle:
        raw_items = json.load(handle)
    sources: list[ScoreRankSource] = []
    for item in raw_items:
        enabled = bool(item.get("enabled", True))
        year = int(item["year"])
        if years is not None and year not in years:
            continue
        if not enabled:
            continue
        sources.append(
            ScoreRankSource(
                year=year,
                subject_type=str(item["subjectType"]),
                source_url=str(item.get("sourceUrl", "")),
                source_page_url=str(item.get("sourcePageUrl", "")),
                source_file=str(item.get("sourceFile", "")),
                parse_method=str(item.get("parseMethod", "pdf_table")),
                source_name=str(item.get("sourceName", SOURCE_NAME)),
                enabled=enabled,
            )
        )
    if not sources:
        year_text = ",".join(str(year) for year in sorted(years)) if years else "all enabled years"
        raise RuntimeError(f"No enabled score-rank sources found for {year_text} in {config_path}")
    return sources


def parse_years(value: str | None, fallback_year: int | None) -> set[int] | None:
    if value:
        return {int(part.strip()) for part in value.split(",") if part.strip()}
    if fallback_year is not None:
        return {fallback_year}
    return None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--year", type=int, default=None, help="Backward compatible single-year filter.")
    parser.add_argument("--years", default=None, help="Comma-separated year filter, e.g. 2025,2024.")
    parser.add_argument("--sources", type=Path, default=DEFAULT_SOURCE_CONFIG)
    parser.add_argument("--raw-dir", type=Path, default=Path("data/raw"))
    parser.add_argument("--output-csv", type=Path, default=Path("data/export/score_rank_gz.csv"))
    parser.add_argument("--output-sql", type=Path, default=Path("data/export/score_rank_gz.sql"))
    parser.add_argument("--skip-download", action="store_true")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    years = parse_years(args.years, args.year)
    sources = load_sources(args.sources, years)
    all_rows: list[ScoreRankRow] = []
    for source in sources:
        if not source.source_url:
            raise RuntimeError(f"Missing sourceUrl for {source.year} {source.subject_type}")
        source_file = source.source_file or f"guizhou_{source.year}_{source.subject_type}_score_rank.pdf"
        pdf_path = args.raw_dir / source_file
        if not args.skip_download:
            download_pdf(source.source_url, pdf_path)
        rows = extract_rows(pdf_path, source)
        if not rows:
            raise RuntimeError(f"No rows extracted from {pdf_path}")
        all_rows.extend(rows)

    write_csv(all_rows, args.output_csv)
    write_sql(all_rows, args.output_sql)
    print(f"rows={len(all_rows)} csv={args.output_csv} sql={args.output_sql}")


if __name__ == "__main__":
    main()
