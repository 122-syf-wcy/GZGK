#!/usr/bin/env python3
"""
Normalize the large parallel_scores.sql export into a production-safe import file.

The historical export contains a `notes` column with subject requirements. This
script preserves the rows, derives `resubject_requirement`, and emits INSERT
statements that match the current data_major_score_gz schema.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
from pathlib import Path


INSERT_RE = re.compile(r"VALUES\s*\((.*)\);$")


def sql_quote(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def parse_values(line: str) -> list[str] | None:
    match = INSERT_RE.search(line)
    if not match:
        return None
    payload = match.group(1).replace("\\'", "''")
    reader = csv.reader(io.StringIO(payload), delimiter=",", quotechar="'", escapechar="\\")
    row = next(reader)
    return [None if value == "NULL" else value.replace("''", "'") for value in row]


def infer_requirement(notes: str) -> str:
    text = notes or ""
    if "再选不限" in text or "再选科目不限" in text or "再选：不限" in text:
        return "不限"
    if "化学和生物" in text or "化学、生物" in text or "化学+生物" in text:
        return "化学和生物"
    if "化学或生物" in text:
        return "化学或生物"
    for subject in ("化学", "生物", "政治", "思想政治", "地理"):
        if f"再选{subject}" in text or f"再选科目：{subject}" in text or f"再选 {subject}" in text:
            return "政治" if subject == "思想政治" else subject
    return ""


def normalize(input_path: Path, output_path: Path) -> tuple[int, int]:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    with_requirement = 0
    with output_path.open("w", encoding="utf-8") as out:
        out.write("-- Normalized major score import generated from parallel_scores.sql\n")
        out.write("SET NAMES utf8mb4;\n\n")
        out.write(
            "CREATE TABLE IF NOT EXISTS `data_major_score_gz` (\n"
            "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,\n"
            "  `school_id` VARCHAR(20) NOT NULL,\n"
            "  `university_name` VARCHAR(100) DEFAULT '',\n"
            "  `major_name` VARCHAR(200) NOT NULL,\n"
            "  `major_id` VARCHAR(20) DEFAULT '',\n"
            "  `year` SMALLINT NOT NULL,\n"
            "  `subject_type` VARCHAR(10) NOT NULL,\n"
            "  `batch` VARCHAR(50) DEFAULT '',\n"
            "  `resubject_requirement` VARCHAR(100) DEFAULT '',\n"
            "  `min_score` SMALLINT DEFAULT NULL,\n"
            "  `max_score` SMALLINT DEFAULT NULL,\n"
            "  `avg_score` SMALLINT DEFAULT NULL,\n"
            "  `min_rank` INT DEFAULT NULL,\n"
            "  `plan_count` SMALLINT DEFAULT NULL,\n"
            "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
            "  KEY `idx_school_year` (`school_id`, `year`),\n"
            "  KEY `idx_year_subject` (`year`, `subject_type`),\n"
            "  KEY `idx_year_subject_resub` (`year`, `subject_type`, `resubject_requirement`),\n"
            "  UNIQUE KEY `uk_record` (`school_id`, `major_name`(100), `year`, `subject_type`, `batch`(30))\n"
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州专业投档线';\n\n"
        )
        for line in input_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if not line.startswith("INSERT IGNORE INTO `data_major_score_gz`"):
                continue
            row = parse_values(line)
            if not row or len(row) != 8:
                continue
            school_id, university_name, major_name, year, subject_type, min_score, min_rank, notes = row
            requirement = infer_requirement(notes or "")
            if requirement:
                with_requirement += 1
            total += 1
            values = [
                sql_quote(school_id),
                sql_quote(university_name),
                sql_quote(major_name),
                "''",
                year,
                sql_quote(subject_type),
                "'本科批'",
                sql_quote(requirement),
                min_score or "NULL",
                "NULL",
                "NULL",
                min_rank or "NULL",
                "NULL",
            ]
            out.write(
                "INSERT IGNORE INTO `data_major_score_gz` "
                "(`school_id`,`university_name`,`major_name`,`major_id`,`year`,`subject_type`,`batch`,"
                "`resubject_requirement`,`min_score`,`max_score`,`avg_score`,`min_rank`,`plan_count`) VALUES "
                f"({','.join(values)});\n"
            )
    return total, with_requirement


def main() -> None:
    base_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="Normalize parallel major score export")
    parser.add_argument("--input", type=Path, default=base_dir / "data/export/parallel_scores.sql")
    parser.add_argument("--output", type=Path, default=base_dir / "data/export/major_scores_normalized.sql")
    args = parser.parse_args()
    total, with_requirement = normalize(args.input, args.output)
    print(f"wrote {args.output}")
    print(f"rows={total}, rows_with_resubject_requirement={with_requirement}")


if __name__ == "__main__":
    main()
