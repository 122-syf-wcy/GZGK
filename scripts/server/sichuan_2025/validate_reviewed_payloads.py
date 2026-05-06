#!/usr/bin/env python3
"""校验四川 reviewed CSV 是否已经具备生成 admin payload 的条件。"""

import argparse
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from common import (
    GROUP_LINE_HEADERS,
    GROUP_PLAN_HEADERS,
    REPORTS_DIR,
    REVIEWED_DIR,
    SCORE_RANK_HEADERS,
    ensure_dirs,
    host_of,
    read_csv,
    write_json,
)


TARGET_GROUP_COUNT = 45
SUBJECT_TYPES = {"物理类", "历史类"}
ALLOWED_SOURCE_LEVELS = {"manual_verified", "school_verified"}
BLOCKED_HOST_HINTS = {
    "eol.cn",
    "gaokao.cn",
    "youzy.cn",
    "dxsbb.com",
    "zhiyuan",
    "sczjw.com.cn",
    "scedu.net",
}


def text(value: Any) -> str:
    return str(value or "").strip()


def int_value(value: Any) -> int | None:
    raw = text(value).replace(",", "")
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def is_official_url(url: str) -> bool:
    host = host_of(url)
    return host == "sceea.cn" or host.endswith(".sceea.cn")


def source_level(row: dict[str, str]) -> str:
    explicit = text(row.get("sourceLevel"))
    if explicit:
        return explicit
    return "manual_verified" if is_official_url(text(row.get("sourcePageUrl"))) else "school_verified"


def add_source_errors(errors: list[str], row: dict[str, str]) -> None:
    level = source_level(row)
    source_page = text(row.get("sourcePageUrl"))
    source_url = text(row.get("sourceUrl")) or source_page
    if level not in ALLOWED_SOURCE_LEVELS:
        errors.append("sourceLevel 只能是 manual_verified 或 school_verified")
    if not source_page:
        errors.append("sourcePageUrl 不能为空")
        return
    if level == "manual_verified":
        if not is_official_url(source_page):
            errors.append("manual_verified 的 sourcePageUrl 必须是四川省教育考试院链接")
        for url in source_url.splitlines():
            if text(url) and not is_official_url(text(url)):
                errors.append("manual_verified 的 sourceUrl 只能是四川省教育考试院链接")
        return
    for url in [source_page, *source_url.splitlines()]:
        value = text(url)
        if not value:
            continue
        host = host_of(value)
        if not host:
            errors.append("sourcePageUrl/sourceUrl 必须为有效 HTTP/HTTPS 链接")
            continue
        if any(hint in host for hint in BLOCKED_HOST_HINTS):
            errors.append("sourcePageUrl/sourceUrl 不允许使用第三方聚合或门户来源")


def non_empty_rows(path: Path) -> list[dict[str, str]]:
    return [row for row in read_csv(path) if any(text(value) for value in row.values())]


def validate_headers(path: Path, required_headers: list[str], errors: list[str]) -> None:
    rows = read_csv(path)
    if not rows:
        return
    missing = [header for header in required_headers if header not in rows[0]]
    if missing:
        errors.append(f"{path.name} 缺少表头: {','.join(missing)}")


def validate_score_rank() -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    counts: dict[str, int] = {}
    for subject in sorted(SUBJECT_TYPES):
        path = REVIEWED_DIR / f"score_rank_sc_2025_{subject}_reviewed.csv"
        validate_headers(path, SCORE_RANK_HEADERS, errors)
        rows = non_empty_rows(path)
        counts[subject] = len(rows)
        seen_scores: set[int] = set()
        previous_score = 751
        previous_cumulative = 0
        for idx, row in enumerate(rows, 2):
            row_errors: list[str] = []
            score = int_value(row.get("score"))
            cumulative = int_value(row.get("cumulativeCount"))
            segment = int_value(row.get("segmentCount"))
            add_source_errors(row_errors, {**row, "sourceLevel": "manual_verified"})
            if score is None or score < 0 or score > 750:
                row_errors.append("分数必须在0-750之间")
            elif score in seen_scores:
                row_errors.append("分数重复")
            elif score >= previous_score:
                row_errors.append("分数必须按递减顺序填写")
            if cumulative is None or cumulative <= previous_cumulative:
                row_errors.append("累计人数必须递增")
            if segment is not None and segment < 0:
                row_errors.append("本段人数不能为负数")
            if row_errors:
                errors.append(f"{path.name}:{idx}: {';'.join(row_errors)}")
            if score is not None:
                seen_scores.add(score)
                previous_score = score
            if cumulative is not None:
                previous_cumulative = cumulative
        if not rows:
            warnings.append(f"{path.name} 为空；如果生产已导入一分一段，可忽略本地 reviewed 空表。")
    return {"rowsBySubject": counts}, errors, warnings


def validate_group_lines() -> tuple[
    dict[str, Any],
    list[str],
    list[str],
    set[tuple[str, str, str]],
    dict[tuple[str, str, str], int],
]:
    errors: list[str] = []
    warnings: list[str] = []
    path = REVIEWED_DIR / "group_lines_sc_2025_reviewed.csv"
    validate_headers(path, GROUP_LINE_HEADERS, errors)
    rows = non_empty_rows(path)
    seen: set[tuple[str, str, str, str]] = set()
    line_keys: set[tuple[str, str, str]] = set()
    plan_count_by_line: dict[tuple[str, str, str], int] = {}
    groups_by_subject: dict[str, set[tuple[str, str, str]]] = defaultdict(set)
    missing_rank_count = 0
    for idx, row in enumerate(rows, 2):
        row_errors: list[str] = []
        school_id = text(row.get("schoolId"))
        university_name = text(row.get("universityName"))
        group_code = text(row.get("groupCode"))
        subject_type = text(row.get("subjectType"))
        min_score = int_value(row.get("minScore"))
        min_rank = int_value(row.get("minRank"))
        batch = text(row.get("batch")) or "普通本科批B段"
        add_source_errors(row_errors, row)
        if not school_id and not university_name:
            row_errors.append("院校代码或院校名称必填")
        if not group_code:
            row_errors.append("专业组代码必填")
        if subject_type not in SUBJECT_TYPES:
            row_errors.append("科类必须为物理类或历史类")
        if min_score is None or min_score <= 0 or min_score > 750:
            row_errors.append("最低分必须在1-750之间")
        if min_rank is None:
            missing_rank_count += 1
        elif min_rank <= 0:
            row_errors.append("最低位次填写时必须大于0")
        if "本科批" not in batch:
            row_errors.append("批次必须为普通本科批B段或本科批口径")
        dedupe_key = (school_id, university_name, group_code, subject_type)
        if dedupe_key in seen:
            row_errors.append("导入批次内院校专业组重复")
        seen.add(dedupe_key)
        if row_errors:
            errors.append(f"{path.name}:{idx}: {';'.join(row_errors)}")
            continue
        line_key = (school_id or university_name, group_code, subject_type)
        line_keys.add(line_key)
        plan_count = int_value(row.get("planCount"))
        if plan_count is not None:
            plan_count_by_line[line_key] = plan_count
        groups_by_subject[subject_type].add(line_key)
    counts = {subject: len(groups_by_subject.get(subject, set())) for subject in sorted(SUBJECT_TYPES)}
    for subject, count in counts.items():
        if count < TARGET_GROUP_COUNT:
            warnings.append(f"group_lines {subject} 当前 {count} 组，未达到解锁门槛 {TARGET_GROUP_COUNT} 组。")
    if missing_rank_count:
        warnings.append(f"group_lines 有 {missing_rank_count} 行未填最低位次，后端会尝试用官方一分一段按最低分换算。")
    return {"rows": len(rows), "groupsBySubject": counts}, errors, warnings, line_keys, plan_count_by_line


def validate_group_plans(
    line_keys: set[tuple[str, str, str]],
    expected_plan_counts: dict[tuple[str, str, str], int],
) -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    path = REVIEWED_DIR / "group_plans_sc_2025_reviewed.csv"
    validate_headers(path, GROUP_PLAN_HEADERS, errors)
    rows = non_empty_rows(path)
    seen: set[tuple[str, str, str, str]] = set()
    groups_by_subject: dict[str, set[tuple[str, str, str]]] = defaultdict(set)
    plan_count_by_group: dict[tuple[str, str, str], int] = defaultdict(int)
    for idx, row in enumerate(rows, 2):
        row_errors: list[str] = []
        school_id = text(row.get("schoolId"))
        university_name = text(row.get("universityName"))
        group_code = text(row.get("groupCode"))
        major_code = text(row.get("majorCode"))
        major_name = text(row.get("majorName"))
        subject_type = text(row.get("subjectType"))
        plan_count = int_value(row.get("planCount"))
        add_source_errors(row_errors, row)
        if not school_id and not university_name:
            row_errors.append("院校代码或院校名称必填")
        if not group_code:
            row_errors.append("专业组代码必填")
        if not major_name:
            row_errors.append("专业名称必填")
        if subject_type not in SUBJECT_TYPES:
            row_errors.append("科类必须为物理类或历史类")
        if plan_count is None or plan_count < 0:
            row_errors.append("计划数必须为非负整数")
        dedupe_key = (school_id, university_name, group_code, subject_type, major_code or major_name)
        if dedupe_key in seen:
            row_errors.append("导入批次内招生计划专业重复")
        seen.add(dedupe_key)
        line_key = (school_id or university_name, group_code, subject_type)
        if line_keys and line_key not in line_keys:
            row_errors.append("本地 reviewed 专业组线中找不到对应院校专业组")
        if row_errors:
            errors.append(f"{path.name}:{idx}: {';'.join(row_errors)}")
            continue
        groups_by_subject[subject_type].add(line_key)
        if plan_count is not None:
            plan_count_by_group[line_key] += plan_count
    counts = {subject: len(groups_by_subject.get(subject, set())) for subject in sorted(SUBJECT_TYPES)}
    for subject, count in counts.items():
        if count < TARGET_GROUP_COUNT:
            warnings.append(f"group_plans {subject} 当前 {count} 组，未达到解锁门槛 {TARGET_GROUP_COUNT} 组。")
    plan_keys = set().union(*groups_by_subject.values()) if groups_by_subject else set()
    lines_without_plans = sorted(line_keys - plan_keys)
    if lines_without_plans:
        preview = "；".join(f"{school} {group_code} {subject}" for school, group_code, subject in lines_without_plans[:10])
        suffix = "等" if len(lines_without_plans) > 10 else ""
        warnings.append(f"group_lines 有 {len(lines_without_plans)} 个专业组未在 group_plans 中找到招生计划：{preview}{suffix}。")
    mismatched_counts = [
        (key, expected, plan_count_by_group.get(key, 0))
        for key, expected in expected_plan_counts.items()
        if key in plan_count_by_group and expected != plan_count_by_group.get(key, 0)
    ]
    if mismatched_counts:
        preview = "；".join(
            f"{school} {group_code} {subject} 专业组线={expected} 计划合计={actual}"
            for (school, group_code, subject), expected, actual in mismatched_counts[:10]
        )
        suffix = "等" if len(mismatched_counts) > 10 else ""
        warnings.append(f"group_lines planCount 与 group_plans 专业计划合计不一致 {len(mismatched_counts)} 组：{preview}{suffix}。")
    return {"rows": len(rows), "groupsBySubject": counts}, errors, warnings


def write_markdown_report(report: dict[str, Any]) -> None:
    lines = [
        "# 四川 reviewed CSV 校验报告",
        "",
        f"- 错误数：{report['errorCount']}",
        f"- 警告数：{report['warningCount']}",
        "",
        "## 计数",
        "",
    ]
    for key, value in report["counts"].items():
        lines.append(f"- {key}: `{value}`")
    if report["errors"]:
        lines.extend(["", "## 错误", ""])
        lines.extend(f"- {item}" for item in report["errors"])
    if report["warnings"]:
        lines.extend(["", "## 警告", ""])
        lines.extend(f"- {item}" for item in report["warnings"])
    (REPORTS_DIR / "reviewed_validation_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-incomplete", action="store_true", help="有错误时仍返回0，只写报告。")
    args = parser.parse_args()
    ensure_dirs()

    score_counts, score_errors, score_warnings = validate_score_rank()
    line_counts, line_errors, line_warnings, line_keys, line_plan_counts = validate_group_lines()
    plan_counts, plan_errors, plan_warnings = validate_group_plans(line_keys, line_plan_counts)
    errors = [*score_errors, *line_errors, *plan_errors]
    warnings = [*score_warnings, *line_warnings, *plan_warnings]
    counts = {
        "score_rank": score_counts,
        "group_lines": line_counts,
        "group_plans": plan_counts,
    }
    source_levels = Counter()
    for name in ("group_lines_sc_2025_reviewed.csv", "group_plans_sc_2025_reviewed.csv"):
        for row in non_empty_rows(REVIEWED_DIR / name):
            source_levels[source_level(row)] += 1
    report = {
        "errorCount": len(errors),
        "warningCount": len(warnings),
        "counts": counts,
        "sourceLevelCounts": dict(source_levels),
        "errors": errors,
        "warnings": warnings,
    }
    write_json(REPORTS_DIR / "reviewed_validation_report.json", report)
    write_markdown_report(report)
    print(f"reviewed validation errors={len(errors)} warnings={len(warnings)}")
    print(f"wrote {REPORTS_DIR / 'reviewed_validation_report.json'}")
    print(f"wrote {REPORTS_DIR / 'reviewed_validation_report.md'}")
    if errors and not args.allow_incomplete:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
