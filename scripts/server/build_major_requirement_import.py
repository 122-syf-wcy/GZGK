#!/usr/bin/env python3
"""Build MySQL import SQL for Guizhou official major subject requirements."""

from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path


TABLE_DDL = """CREATE TABLE IF NOT EXISTS `data_major_requirement_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `year` SMALLINT NOT NULL COMMENT '年份',
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_id` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '专业/专业组代码',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `first_subject_requirement` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '再选科目要求(不限/化学/化学和生物等)',
  `requirement_text` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方原文或解析备注',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_file` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_year_school_major_subject` (`year`, `school_id`, `major_name`, `subject_type`),
  KEY `idx_school_major_subject` (`school_id`, `major_name`, `subject_type`),
  KEY `idx_year_subject_requirement` (`year`, `subject_type`, `resubject_requirement`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州官方专业选科要求库';"""


REQUIRED_COLUMNS = {"year", "school_id", "major_name", "subject_type", "resubject_requirement"}


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def normalize_subject_type(value: str) -> str:
    text = value.strip()
    if text in {"物理", "物理类", "首选物理"}:
        return "物理类"
    if text in {"历史", "历史类", "首选历史"}:
        return "历史类"
    return text


def normalize_resubject_requirement(value: str) -> str:
    text = value.strip().replace("思想政治", "政治")
    if not text or text in {"无", "不限", "不提科目要求", "再选不限", "科目不限"}:
        return "不限"
    if "化学" in text and "生物" in text and re.search(r"和|及|且|\+", text):
        return "化学和生物"
    if "化学" in text and "生物" in text and "或" in text:
        return "化学或生物"
    for subject in ("化学", "生物", "政治", "地理"):
        if subject in text:
            return subject
    return text[:100]


def read_rows(input_csv: Path) -> list[dict[str, str]]:
    with input_csv.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"CSV缺少必要列: {', '.join(sorted(missing))}")
        rows: list[dict[str, str]] = []
        for raw in reader:
            major_name = (raw.get("major_name") or "").strip()
            school_id = (raw.get("school_id") or "").strip()
            if not major_name or not school_id:
                continue
            row = {
                "year": str(int((raw.get("year") or "0").strip())),
                "school_id": school_id,
                "university_name": (raw.get("university_name") or "").strip(),
                "major_id": (raw.get("major_id") or "").strip(),
                "major_name": major_name,
                "subject_type": normalize_subject_type(raw.get("subject_type") or ""),
                "first_subject_requirement": (raw.get("first_subject_requirement") or "").strip(),
                "resubject_requirement": normalize_resubject_requirement(raw.get("resubject_requirement") or ""),
                "requirement_text": (raw.get("requirement_text") or "").strip(),
                "source_name": (raw.get("source_name") or "贵州省招生考试院").strip(),
                "source_url": (raw.get("source_url") or "").strip(),
                "source_file": (raw.get("source_file") or input_csv.name).strip(),
            }
            rows.append(row)
        return rows


def write_sql(rows: list[dict[str, str]], output_sql: Path) -> None:
    output_sql.parent.mkdir(parents=True, exist_ok=True)
    with output_sql.open("w", encoding="utf-8") as handle:
        handle.write("-- Generated official major subject requirement import. Re-runnable by unique key.\n")
        handle.write(TABLE_DDL + "\n\n")
        if not rows:
            handle.write("-- No rows to import.\n")
            return
        handle.write("INSERT INTO `data_major_requirement_gz` (\n")
        handle.write("  `year`, `school_id`, `university_name`, `major_id`, `major_name`,\n")
        handle.write("  `subject_type`, `first_subject_requirement`, `resubject_requirement`,\n")
        handle.write("  `requirement_text`, `source_name`, `source_url`, `source_file`\n")
        handle.write(") VALUES\n")
        values = []
        for row in rows:
            values.append(
                "("
                f"{row['year']}, {sql_quote(row['school_id'])}, {sql_quote(row['university_name'])}, "
                f"{sql_quote(row['major_id'])}, {sql_quote(row['major_name'])}, {sql_quote(row['subject_type'])}, "
                f"{sql_quote(row['first_subject_requirement'])}, {sql_quote(row['resubject_requirement'])}, "
                f"{sql_quote(row['requirement_text'])}, {sql_quote(row['source_name'])}, "
                f"{sql_quote(row['source_url'])}, {sql_quote(row['source_file'])}"
                ")"
            )
        handle.write(",\n".join(values))
        handle.write("\nON DUPLICATE KEY UPDATE\n")
        handle.write("  `university_name` = VALUES(`university_name`),\n")
        handle.write("  `major_id` = VALUES(`major_id`),\n")
        handle.write("  `first_subject_requirement` = VALUES(`first_subject_requirement`),\n")
        handle.write("  `resubject_requirement` = VALUES(`resubject_requirement`),\n")
        handle.write("  `requirement_text` = VALUES(`requirement_text`),\n")
        handle.write("  `source_name` = VALUES(`source_name`),\n")
        handle.write("  `source_url` = VALUES(`source_url`),\n")
        handle.write("  `source_file` = VALUES(`source_file`);\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-csv", type=Path, required=True)
    parser.add_argument("--output-sql", type=Path, default=Path("data/export/major_requirements_gz.sql"))
    return parser


def main() -> None:
    args = build_parser().parse_args()
    rows = read_rows(args.input_csv)
    write_sql(rows, args.output_sql)
    print(f"rows={len(rows)} sql={args.output_sql}")


if __name__ == "__main__":
    main()
