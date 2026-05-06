#!/usr/bin/env python3
"""Build Guizhou major subject requirements from the official admission catalog PDF.

The 2025 physics catalog published by Guizhou Admission Examination Institute has
extractable text. This parser keeps the import conservative: it only emits rows
when a school can be matched to the local school library and a clear re-subject
requirement token is found.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from dataclasses import dataclass
from pathlib import Path

import fitz


SOURCE_NAME = "贵州省招生考试院"
DEFAULT_SOURCE_URLS = {
    "物理类": (
        "https://iip.oss-cn-guiyang-gzdata-d01-a.res.gzdata.com.cn/2025/06/23/"
        "W0202506231327%E8%B4%B5%E5%B7%9E%E7%9C%812025%E5%B9%B4%E6%99%AE"
        "%E9%80%9A%E9%AB%98%E6%A0%A1%E6%8B%9B%E7%94%9F%E4%B8%93%E4%B8%9A"
        "%E7%9B%AE%E5%BD%95%EF%BC%88%E7%89%A9%E7%90%86%E7%B1%BB%EF%BC%89.pdf"
    ),
}

REQUIREMENTS = ("化学和生物", "化学或生物", "思想政治", "化学", "生物", "地理", "不限")
LANGUAGE_RE = re.compile(r"^(不限|英语|英日|英俄|英德|英法|日语|俄语|德语|法语|西班牙语|韩语)\d?$")
SCHOOL_RE = re.compile(r"^(\d{4})\s+(.+)$")
MAJOR_CODE_RE = re.compile(r"^(?:[A-Z]\d{2}|\d{3,4})$")


@dataclass(frozen=True)
class School:
    school_id: str
    name: str
    normalized: str


@dataclass
class Row:
    year: int
    school_id: str
    university_name: str
    catalog_school_code: str
    major_id: str
    major_name: str
    subject_type: str
    first_subject_requirement: str
    resubject_requirement: str
    requirement_text: str
    source_name: str
    source_url: str
    source_file: str


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
    contains = [
        s for s in schools
        if normalized in s.normalized or s.normalized in normalized
    ]
    if not contains:
        return None
    contains.sort(key=lambda s: abs(len(s.normalized) - len(normalized)))
    return contains[0]


def is_noise(line: str) -> bool:
    text = line.strip()
    if not text:
        return True
    if text.startswith("[") or text.endswith("]"):
        return True
    if text in {"本科", "高职（专科）", "普通类", "军队院校", "公安（武警）类", "其他院校"}:
        return True
    if text in {"代码", "院校·专业", "再选科目计划语种学", "制", "学费", "元/年"}:
        return True
    if re.match(r"^\d+\s*/\s*\d+$", text):
        return True
    if re.match(r"^[A-Z]$", text):
        return True
    if text.startswith("│") or text.startswith("贵州省"):
        return True
    return False


def split_requirement_from_major(line: str, next_line: str | None) -> tuple[str, str | None]:
    text = line.strip()
    if text in REQUIREMENTS:
        return "", text
    if next_line and re.match(r"^\d+$", next_line.strip()):
        for req in REQUIREMENTS:
            if text.endswith(req) and len(text) > len(req):
                return text[: -len(req)].strip(), req
    return text, None


def parse_catalog(pdf_path: Path, subject_type: str, year: int, schools: list[School], source_url: str) -> list[Row]:
    doc = fitz.open(pdf_path)
    rows: list[Row] = []
    current_school: School | None = None
    current_school_code = ""
    current_catalog_name = ""
    active_major_code: str | None = None
    major_parts: list[str] = []

    def flush(requirement: str) -> None:
        nonlocal active_major_code, major_parts
        if not current_school or not active_major_code:
            active_major_code = None
            major_parts = []
            return
        major_name = "".join(major_parts).strip()
        major_name = re.sub(r"\s+", "", major_name)
        if not major_name:
            active_major_code = None
            major_parts = []
            return
        rows.append(Row(
            year=year,
            school_id=current_school.school_id,
            university_name=current_school.name,
            catalog_school_code=current_school_code,
            major_id=active_major_code,
            major_name=major_name,
            subject_type=subject_type,
            first_subject_requirement="物理" if subject_type == "物理类" else "历史",
            resubject_requirement=requirement,
            requirement_text=f"{subject_type}招生专业目录：{major_name}，再选科目要求：{requirement}",
            source_name=SOURCE_NAME,
            source_url=source_url,
            source_file=pdf_path.name,
        ))
        active_major_code = None
        major_parts = []

    for page in doc:
        lines = [line.strip() for line in page.get_text("text").splitlines()]
        for idx, raw in enumerate(lines):
            line = raw.strip()
            if is_noise(line):
                continue

            school_match = SCHOOL_RE.match(line)
            if school_match and not LANGUAGE_RE.match(line):
                current_school_code = school_match.group(1)
                current_catalog_name = school_match.group(2).strip()
                current_school = resolve_school(current_catalog_name, schools)
                active_major_code = None
                major_parts = []
                continue

            if MAJOR_CODE_RE.match(line):
                active_major_code = line
                major_parts = []
                continue

            if active_major_code is None:
                continue
            if line in REQUIREMENTS:
                flush(line)
                continue

            if LANGUAGE_RE.match(line) or re.match(r"^\d+$", line) or re.match(r"^\d{3,6}$", line):
                continue

            next_line = lines[idx + 1].strip() if idx + 1 < len(lines) else None
            maybe_name, requirement = split_requirement_from_major(line, next_line)
            if maybe_name:
                major_parts.append(maybe_name)
            if requirement:
                flush(requirement)

    return rows


def write_csv(rows: list[Row], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "year", "school_id", "university_name", "catalog_school_code", "major_id",
        "major_name", "subject_type", "first_subject_requirement", "resubject_requirement",
        "requirement_text", "source_name", "source_url", "source_file",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row.__dict__)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--year", type=int, default=2025)
    parser.add_argument("--subject-type", default="物理类", choices=sorted(DEFAULT_SOURCE_URLS))
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--schools-json", type=Path, default=Path("data/guizhou_schools.json"))
    parser.add_argument("--output-csv", type=Path, default=Path("data/major_requirements_gz.csv"))
    parser.add_argument("--source-url", default="")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    schools = load_schools(args.schools_json)
    source_url = args.source_url or DEFAULT_SOURCE_URLS[args.subject_type]
    rows = parse_catalog(args.pdf, args.subject_type, args.year, schools, source_url)
    # Deduplicate by the production unique key.
    deduped: dict[tuple[int, str, str, str], Row] = {}
    for row in rows:
        deduped[(row.year, row.school_id, row.major_name, row.subject_type)] = row
    final_rows = sorted(deduped.values(), key=lambda r: (r.university_name, r.major_name, r.major_id))
    write_csv(final_rows, args.output_csv)
    counts: dict[str, int] = {}
    for row in final_rows:
        counts[row.resubject_requirement] = counts.get(row.resubject_requirement, 0) + 1
    print(f"rows={len(final_rows)} csv={args.output_csv}")
    print("requirements=" + ", ".join(f"{k}:{v}" for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
