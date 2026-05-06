#!/usr/bin/env python3
"""Normalize manual/OCR Guizhou major requirement rows into import-ready CSV.

Use this for the 2025 history catalog when PDF text extraction is unreliable.
The script is intentionally conservative: rows with missing major names,
unresolved schools, or unclear re-subject requirements are written to a reject
CSV instead of being imported.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from dataclasses import dataclass
from pathlib import Path


SOURCE_NAME = "贵州省招生考试院"
FIELDNAMES = [
    "year",
    "school_id",
    "university_name",
    "major_id",
    "major_name",
    "subject_type",
    "first_subject_requirement",
    "resubject_requirement",
    "requirement_text",
    "source_name",
    "source_url",
    "source_file",
]


@dataclass(frozen=True)
class School:
    school_id: str
    name: str
    normalized: str


def normalize_name(value: str) -> str:
    text = re.sub(r"[（(].*?[）)]", "", value or "")
    text = re.sub(r"\s+", "", text)
    text = text.replace("中国人民解放军", "")
    text = text.replace("中国", "")
    return text


def load_schools(path: Path) -> list[School]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    schools: list[School] = []
    for item in raw:
        school_id = str(item.get("school_id") or "").strip()
        name = str(item.get("name") or "").strip()
        if school_id and name:
            schools.append(School(school_id=school_id, name=name, normalized=normalize_name(name)))
    return schools


def resolve_school(raw_name: str, schools: list[School]) -> School | None:
    normalized = normalize_name(raw_name)
    if not normalized:
        return None
    exact = [s for s in schools if s.normalized == normalized]
    if exact:
        return exact[0]
    contains = [s for s in schools if normalized in s.normalized or s.normalized in normalized]
    if not contains:
        return None
    contains.sort(key=lambda s: abs(len(s.normalized) - len(normalized)))
    return contains[0]


def first_non_empty(row: dict[str, str], *names: str) -> str:
    for name in names:
        value = (row.get(name) or "").strip()
        if value:
            return value
    return ""


def normalize_subject_type(value: str, default: str) -> str:
    text = (value or default).strip()
    if text in {"物理", "物理类", "首选物理"}:
        return "物理类"
    if text in {"历史", "历史类", "首选历史"}:
        return "历史类"
    return text or default


def normalize_requirement(value: str) -> str:
    text = (value or "").strip()
    text = text.replace("思想政治", "政治")
    text = re.sub(r"\s+", "", text)
    if not text or text in {"无", "不限", "不提科目要求", "再选不限", "科目不限", "不限科目"}:
        return "不限"
    if "化学" in text and "生物" in text and re.search(r"和|及|且|、|,|，|\\+", text):
        return "化学和生物"
    if "化学" in text and "生物" in text and "或" in text:
        return "化学或生物"
    for subject in ("化学", "生物", "政治", "地理"):
        if subject in text:
            return subject
    return ""


def normalize_row(
    raw: dict[str, str],
    schools: list[School],
    *,
    default_year: int,
    default_subject_type: str,
    source_url: str,
    source_file: str,
) -> tuple[dict[str, str] | None, str]:
    school_id = first_non_empty(raw, "school_id", "院校ID", "学校ID")
    school_name = first_non_empty(raw, "university_name", "school_name", "院校名称", "学校名称")
    school = None
    if school_id:
        school = next((s for s in schools if s.school_id == school_id), None)
    if school is None:
        school = resolve_school(school_name, schools)
    if school is None:
        return None, "学校未匹配"

    major_name = first_non_empty(raw, "major_name", "专业名称", "专业")
    major_name = re.sub(r"\s+", "", major_name)
    if not major_name:
        return None, "专业名称为空"

    requirement = normalize_requirement(first_non_empty(raw, "resubject_requirement", "再选科目要求", "再选科目", "选科要求"))
    if not requirement:
        return None, "再选科目要求不明确"

    year_text = first_non_empty(raw, "year", "年份")
    year = int(year_text) if year_text else default_year
    subject_type = normalize_subject_type(first_non_empty(raw, "subject_type", "首选科目类别", "科类"), default_subject_type)
    first_subject = first_non_empty(raw, "first_subject_requirement", "首选科目要求") or (
        "物理" if subject_type == "物理类" else "历史"
    )
    major_id = first_non_empty(raw, "major_id", "专业代码", "专业组代码")
    requirement_text = first_non_empty(raw, "requirement_text", "官方原文", "备注")
    if not requirement_text:
        requirement_text = f"{subject_type}招生专业目录：{major_name}，再选科目要求：{requirement}"

    return {
        "year": str(year),
        "school_id": school.school_id,
        "university_name": school.name,
        "major_id": major_id,
        "major_name": major_name,
        "subject_type": subject_type,
        "first_subject_requirement": first_subject,
        "resubject_requirement": requirement,
        "requirement_text": requirement_text,
        "source_name": first_non_empty(raw, "source_name", "来源名称") or SOURCE_NAME,
        "source_url": first_non_empty(raw, "source_url", "来源链接") or source_url,
        "source_file": first_non_empty(raw, "source_file", "来源文件") or source_file,
    }, ""


def write_csv(rows: list[dict[str, str]], path: Path, fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-csv", type=Path, required=True)
    parser.add_argument("--output-csv", type=Path, default=Path("data/major_requirements_gz_history.csv"))
    parser.add_argument("--reject-csv", type=Path, default=Path("data/major_requirements_gz_history_rejects.csv"))
    parser.add_argument("--schools-json", type=Path, default=Path("data/guizhou_schools.json"))
    parser.add_argument("--year", type=int, default=2025)
    parser.add_argument("--subject-type", default="历史类", choices=("物理类", "历史类"))
    parser.add_argument("--source-url", default="")
    parser.add_argument("--source-file", default="")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    schools = load_schools(args.schools_json)
    rows: list[dict[str, str]] = []
    rejects: list[dict[str, str]] = []

    with args.input_csv.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        raw_fields = reader.fieldnames or []
        for line_no, raw in enumerate(reader, start=2):
            normalized, reason = normalize_row(
                raw,
                schools,
                default_year=args.year,
                default_subject_type=args.subject_type,
                source_url=args.source_url,
                source_file=args.source_file or args.input_csv.name,
            )
            if normalized is None:
                rejected = {"line_no": str(line_no), "reason": reason}
                rejected.update(raw)
                rejects.append(rejected)
                continue
            rows.append(normalized)

    deduped: dict[tuple[str, str, str, str], dict[str, str]] = {}
    for row in rows:
        key = (row["year"], row["school_id"], row["major_name"], row["subject_type"])
        deduped[key] = row
    final_rows = sorted(deduped.values(), key=lambda r: (r["subject_type"], r["university_name"], r["major_name"]))

    write_csv(final_rows, args.output_csv, FIELDNAMES)
    reject_fields = ["line_no", "reason", *raw_fields]
    write_csv(rejects, args.reject_csv, reject_fields)

    counts: dict[str, int] = {}
    for row in final_rows:
        counts[row["resubject_requirement"]] = counts.get(row["resubject_requirement"], 0) + 1
    print(f"rows={len(final_rows)} csv={args.output_csv}")
    print(f"rejects={len(rejects)} csv={args.reject_csv}")
    print("requirements=" + ", ".join(f"{k}:{v}" for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
