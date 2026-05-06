#!/usr/bin/env python3
"""Validate reviewed CSVs and build admin payloads for SC/HB/AH.

The script never imports data by itself. It writes JSON payloads and curl
scripts that must be dry-run first, then manually confirmed for import.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parent
REVIEWED_DIR = ROOT / "reviewed"
REPORTS_DIR = ROOT / "reports"
PAYLOADS_DIR = ROOT / "payloads"
LEGACY_SICHUAN_REVIEWED_DIR = ROOT.parent / "sichuan_2025" / "reviewed"
ADMIN_BASE = "http://127.0.0.1:8090/api/admin/province-data"

SUBJECT_TYPES = ("历史类", "物理类")
TARGET_GROUP_COUNT = 45
ALLOWED_SOURCE_LEVELS = {"manual_verified", "school_verified"}
BLOCKED_HOST_HINTS = {
    "gaokao.cn",
    "youzy.cn",
    "dxsbb.com",
    "gk100.com",
    "gaosan.com",
    "hfplg.com",
    "zhiyuan",
}

SCORE_RANK_HEADERS = ["score", "scoreLabel", "segmentCount", "cumulativeCount"]
GROUP_LINE_HEADERS = [
    "schoolId",
    "universityName",
    "groupCode",
    "groupName",
    "subjectType",
    "firstSubjectRequirement",
    "resubjectRequirement",
    "minScore",
    "minRank",
    "planCount",
    "batch",
]
GROUP_PLAN_HEADERS = [
    "schoolId",
    "universityName",
    "groupCode",
    "groupName",
    "majorCode",
    "majorName",
    "subjectType",
    "firstSubjectRequirement",
    "resubjectRequirement",
    "planCount",
    "tuition",
    "studyYears",
    "batch",
]
SOURCE_META_HEADERS = ["sourcePageUrl", "sourceUrl", "sourceHash", "sourceLevel"]


@dataclass(frozen=True)
class ProvinceConfig:
    code: str
    name: str
    target_batch: str
    official_hosts: tuple[str, ...]
    score_rank_pages: dict[str, str]


PROVINCES = {
    "SC": ProvinceConfig(
        "SC",
        "四川",
        "普通本科批B段",
        ("sceea.cn",),
        {
            "历史类": "https://www.sceea.cn/Html/202506/Newsdetail_4334.html",
            "物理类": "https://www.sceea.cn/Html/202506/Newsdetail_4335.html",
        },
    ),
    "HB": ProvinceConfig(
        "HB",
        "湖北",
        "本科普通批",
        ("hbccks.cn", "hubei.gov.cn", "jyt.hubei.gov.cn"),
        {
            "历史类": "http://www.hbccks.cn/html/yfyd/2025-06/142617.html",
            "物理类": "http://www.hbccks.cn/html/yfyd/2025-06/142616.html",
        },
    ),
    "AH": ProvinceConfig(
        "AH",
        "安徽",
        "普通本科批次",
        ("ahzsks.cn", "anhuinews.com", "chsi.com.cn", "eol.cn"),
        {
            "历史类": "http://edu.anhuinews.com/kszx/gk/gzdt/202506/t20250625_8581781.html",
            "物理类": "http://edu.anhuinews.com/kszx/gk/gzdt/202506/t20250625_8581781.html",
        },
    ),
}


def ensure_dirs(province: ProvinceConfig) -> None:
    (REVIEWED_DIR / province.code).mkdir(parents=True, exist_ok=True)
    (REPORTS_DIR / province.code).mkdir(parents=True, exist_ok=True)
    (PAYLOADS_DIR / province.code).mkdir(parents=True, exist_ok=True)


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


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def host_of(url: str) -> str:
    parsed = urlparse(text(url))
    if parsed.scheme not in {"http", "https"}:
        return ""
    return (parsed.hostname or "").lower()


def is_official_url(province: ProvinceConfig, url: str) -> bool:
    host = host_of(url)
    return any(host == item or host.endswith("." + item) for item in province.official_hosts)


def reviewed_file(province: ProvinceConfig, name: str) -> Path:
    lower = province.code.lower()
    if name == "group_lines":
        return REVIEWED_DIR / province.code / f"group_lines_{lower}_2025_reviewed.csv"
    if name == "group_plans":
        return REVIEWED_DIR / province.code / f"group_plans_{lower}_2025_reviewed.csv"
    raise ValueError(f"unknown reviewed file kind: {name}")


def score_rank_file(province: ProvinceConfig, subject: str) -> Path:
    lower = province.code.lower()
    return REVIEWED_DIR / province.code / f"score_rank_{lower}_2025_{subject}_reviewed.csv"


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, headers: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in headers})


def ensure_source_level_column(path: Path, default_source_level: str) -> None:
    rows = read_csv(path)
    if not rows:
        return
    headers = list(rows[0].keys())
    if "sourceLevel" not in headers:
        headers.append("sourceLevel")
    changed = False
    for row in rows:
        if not text(row.get("sourceLevel")):
            row["sourceLevel"] = default_source_level
            changed = True
    if changed:
        write_csv(path, headers, rows)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def non_empty_rows(path: Path) -> list[dict[str, str]]:
    return [row for row in read_csv(path) if any(text(value) for value in row.values())]


def validate_headers(path: Path, required_headers: list[str], errors: list[str]) -> None:
    rows = read_csv(path)
    if not rows:
        return
    missing = [header for header in required_headers if header not in rows[0]]
    if missing:
        errors.append(f"{path.name} 缺少表头: {','.join(missing)}")


def source_level(province: ProvinceConfig, row: dict[str, str]) -> str:
    explicit = text(row.get("sourceLevel"))
    if explicit:
        return explicit
    return "manual_verified" if is_official_url(province, text(row.get("sourcePageUrl"))) else "school_verified"


def add_source_errors(province: ProvinceConfig, errors: list[str], row: dict[str, str]) -> None:
    level = source_level(province, row)
    source_page = text(row.get("sourcePageUrl"))
    source_url = text(row.get("sourceUrl")) or source_page
    if level not in ALLOWED_SOURCE_LEVELS:
        errors.append("sourceLevel 只能是 manual_verified 或 school_verified")
    if not source_page:
        errors.append("sourcePageUrl 不能为空")
        return
    if level == "manual_verified":
        if not is_official_url(province, source_page):
            errors.append(f"manual_verified 的 sourcePageUrl 必须是{province.name}官方或授权来源")
        for url in source_url.splitlines():
            if text(url) and not is_official_url(province, text(url)):
                errors.append(f"manual_verified 的 sourceUrl 只能是{province.name}官方或授权来源")
        return
    for url in [source_page, *source_url.splitlines()]:
        value = text(url)
        if not value:
            continue
        host = host_of(value)
        if not host:
            errors.append("sourcePageUrl/sourceUrl 必须为有效 HTTP/HTTPS 链接")
        elif any(hint in host for hint in BLOCKED_HOST_HINTS):
            errors.append("sourcePageUrl/sourceUrl 不允许使用第三方聚合或门户来源")


def validate_score_rank(province: ProvinceConfig) -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    counts: dict[str, int] = {}
    for subject in SUBJECT_TYPES:
        path = score_rank_file(province, subject)
        validate_headers(path, SCORE_RANK_HEADERS + SOURCE_META_HEADERS, errors)
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
            add_source_errors(province, row_errors, {**row, "sourceLevel": "manual_verified"})
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


def validate_group_lines(province: ProvinceConfig) -> tuple[dict[str, Any], list[str], list[str], set[tuple[str, str, str]], dict[tuple[str, str, str], int]]:
    errors: list[str] = []
    warnings: list[str] = []
    path = reviewed_file(province, "group_lines")
    validate_headers(path, GROUP_LINE_HEADERS + SOURCE_META_HEADERS, errors)
    rows = non_empty_rows(path)
    seen: set[tuple[str, str, str, str]] = set()
    line_keys: set[tuple[str, str, str]] = set()
    plan_count_by_line: dict[tuple[str, str, str], int] = {}
    groups_by_subject: dict[str, set[tuple[str, str, str]]] = defaultdict(set)
    for idx, row in enumerate(rows, 2):
        row_errors: list[str] = []
        school_id = text(row.get("schoolId"))
        university_name = text(row.get("universityName"))
        group_code = text(row.get("groupCode"))
        subject_type = text(row.get("subjectType"))
        min_score = int_value(row.get("minScore"))
        min_rank = int_value(row.get("minRank"))
        batch = text(row.get("batch")) or province.target_batch
        add_source_errors(province, row_errors, row)
        if not school_id and not university_name:
            row_errors.append("院校代码或院校名称必填")
        if not group_code:
            row_errors.append("专业组代码必填")
        if subject_type not in SUBJECT_TYPES:
            row_errors.append("科类必须为物理类或历史类")
        if min_score is None or min_score <= 0 or min_score > 750:
            row_errors.append("最低分必须在1-750之间")
        if min_rank is not None and min_rank <= 0:
            row_errors.append("最低位次填写时必须大于0")
        if "本科" not in batch or "批" not in batch:
            row_errors.append("批次必须为普通本科批次口径")
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
    counts = {subject: len(groups_by_subject.get(subject, set())) for subject in SUBJECT_TYPES}
    for subject, count in counts.items():
        if count < TARGET_GROUP_COUNT:
            warnings.append(f"group_lines {subject} 当前 {count} 组，未达到解锁门槛 {TARGET_GROUP_COUNT} 组。")
    return {"rows": len(rows), "groupsBySubject": counts}, errors, warnings, line_keys, plan_count_by_line


def validate_group_plans(
    province: ProvinceConfig,
    line_keys: set[tuple[str, str, str]],
    expected_plan_counts: dict[tuple[str, str, str], int],
) -> tuple[dict[str, Any], list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    path = reviewed_file(province, "group_plans")
    validate_headers(path, GROUP_PLAN_HEADERS + SOURCE_META_HEADERS, errors)
    rows = non_empty_rows(path)
    seen: set[tuple[str, str, str, str, str]] = set()
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
        add_source_errors(province, row_errors, row)
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
    counts = {subject: len(groups_by_subject.get(subject, set())) for subject in SUBJECT_TYPES}
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


def validate_reviewed(province: ProvinceConfig) -> dict[str, Any]:
    ensure_dirs(province)
    score_counts, score_errors, score_warnings = validate_score_rank(province)
    line_counts, line_errors, line_warnings, line_keys, line_plan_counts = validate_group_lines(province)
    plan_counts, plan_errors, plan_warnings = validate_group_plans(province, line_keys, line_plan_counts)
    errors = [*score_errors, *line_errors, *plan_errors]
    warnings = [*score_warnings, *line_warnings, *plan_warnings]
    source_levels = Counter()
    for path in (reviewed_file(province, "group_lines"), reviewed_file(province, "group_plans")):
        for row in non_empty_rows(path):
            source_levels[source_level(province, row)] += 1
    report = {
        "provinceCode": province.code,
        "provinceName": province.name,
        "year": 2025,
        "errorCount": len(errors),
        "warningCount": len(warnings),
        "counts": {
            "score_rank": score_counts,
            "group_lines": line_counts,
            "group_plans": plan_counts,
        },
        "sourceLevelCounts": dict(source_levels),
        "errors": errors,
        "warnings": warnings,
    }
    report_dir = REPORTS_DIR / province.code
    write_json(report_dir / "reviewed_validation_report.json", report)
    write_markdown_report(province, report)
    return report


def write_markdown_report(province: ProvinceConfig, report: dict[str, Any]) -> None:
    lines = [
        f"# {province.name} reviewed CSV 校验报告",
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
    (REPORTS_DIR / province.code / "reviewed_validation_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def csv_text(headers: list[str], rows: list[dict[str, Any]]) -> str:
    lines = [",".join(headers)]
    for row in rows:
        values = []
        for header in headers:
            value = str(row.get(header, "") or "")
            if any(ch in value for ch in [",", '"', "\n", "\r"]):
                value = '"' + value.replace('"', '""') + '"'
            values.append(value)
        lines.append(",".join(values))
    return "\n".join(lines) + "\n"


def normalize_source_level(province: ProvinceConfig, row: dict[str, str], default: str) -> str:
    value = source_level(province, row) or default
    if value not in ALLOWED_SOURCE_LEVELS:
        raise ValueError(f"sourceLevel 只能是 manual_verified 或 school_verified: {value}")
    return value


def group_by_source(province: ProvinceConfig, rows: list[dict[str, str]], fallback_page: str = "") -> dict[tuple[str, str, str, str], list[dict[str, str]]]:
    grouped: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        source_page = text(row.get("sourcePageUrl")) or fallback_page
        source_url = text(row.get("sourceUrl")) or source_page
        source_hash = text(row.get("sourceHash"))
        if not source_page:
            raise ValueError("reviewed CSV 每行必须填写 sourcePageUrl")
        if not source_url:
            raise ValueError("reviewed CSV 每行必须填写 sourceUrl")
        level = normalize_source_level(province, row, "school_verified")
        grouped[(source_page, source_url, source_hash, level)].append(row)
    return grouped


def write_payload(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def build_score_rank_payloads(province: ProvinceConfig) -> list[dict[str, str]]:
    written: list[dict[str, str]] = []
    for subject in SUBJECT_TYPES:
        csv_path = score_rank_file(province, subject)
        rows = non_empty_rows(csv_path)
        if not rows:
            continue
        grouped = group_by_source(province, rows, fallback_page=province.score_rank_pages.get(subject, ""))
        for idx, ((source_page_url, source_url, source_hash, _source_level), group_rows) in enumerate(grouped.items(), 1):
            text_body = csv_text(SCORE_RANK_HEADERS, group_rows)
            payload = {
                "year": 2025,
                "subjectType": subject,
                "sourcePageUrl": source_page_url,
                "sourceUrl": source_url,
                "sourceFile": csv_path.name,
                "sourceHash": source_hash or sha256_text(text_body),
                "parseMethod": "manual_verified_csv",
                "preserveNonEmpty": True,
                "csvText": text_body,
            }
            out = PAYLOADS_DIR / province.code / f"score-rank-{subject}-{idx:02d}.json"
            write_payload(out, payload)
            written.append({"kind": "score_rank", "path": str(out), "endpoint": "/score-rank/import"})
    return written


def build_group_payloads(province: ProvinceConfig, kind: str, csv_path: Path, headers: list[str], endpoint: str) -> list[dict[str, str]]:
    rows = non_empty_rows(csv_path)
    if not rows:
        return []
    written: list[dict[str, str]] = []
    grouped = group_by_source(province, rows)
    for idx, ((source_page_url, source_url, source_hash, source_level), group_rows) in enumerate(grouped.items(), 1):
        text_body = csv_text(headers, group_rows)
        payload = {
            "year": 2025,
            "sourcePageUrl": source_page_url,
            "sourceUrl": source_url,
            "sourceHash": source_hash or sha256_text(text_body),
            "sourceLevel": source_level,
            "parseMethod": "manual_verified_csv",
            "preserveNonEmpty": True,
            "csvText": text_body,
        }
        out = PAYLOADS_DIR / province.code / f"{kind}-{idx:03d}.json"
        write_payload(out, payload)
        written.append({"kind": kind, "path": str(out), "endpoint": endpoint})
    return written


def write_curl_script(province: ProvinceConfig, items: list[dict[str, str]], dry_run: bool) -> None:
    script = REPORTS_DIR / province.code / ("admin_dry_run_curl.sh" if dry_run else "admin_import_curl.sh")
    dry_run_value = "true" if dry_run else "false"
    confirm_value = f"{province.code}_2025_REVIEWED"
    lines = [
        "#!/usr/bin/env bash",
        "set -Eeuo pipefail",
        ': "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"',
        f'ADMIN_BASE="${{ADMIN_BASE:-{ADMIN_BASE}/{province.code}}}"',
        "",
    ]
    if not dry_run:
        lines.extend([
            f': "${{CONFIRM_PROVINCE_IMPORT:?Set CONFIRM_PROVINCE_IMPORT={confirm_value} after dry-run rejected=0}}"',
            f'if [[ "$CONFIRM_PROVINCE_IMPORT" != "{confirm_value}" ]]; then',
            f'  echo "Refuse import: CONFIRM_PROVINCE_IMPORT must be {confirm_value}" >&2',
            "  exit 1",
            "fi",
            "",
        ])
    for item in items:
        lines.extend([
            f"echo '{'dry-run' if dry_run else 'import'} {province.code} {item['kind']} {Path(item['path']).name}'",
            "curl -sS -X POST "
            f"\"$ADMIN_BASE{item['endpoint']}?dryRun={dry_run_value}\" "
            "-H \"Authorization: Bearer $ADMIN_TOKEN\" "
            "-H 'Content-Type: application/json' "
            f"--data-binary @{item['path']} | python3 -m json.tool",
            "",
        ])
    script.write_text("\n".join(lines), encoding="utf-8")
    script.chmod(0o700)


def build_payloads(province: ProvinceConfig) -> dict[str, Any]:
    ensure_dirs(province)
    items: list[dict[str, str]] = []
    items.extend(build_score_rank_payloads(province))
    items.extend(build_group_payloads(province, "group-lines", reviewed_file(province, "group_lines"), GROUP_LINE_HEADERS, "/group-lines/import"))
    items.extend(build_group_payloads(province, "group-plans", reviewed_file(province, "group_plans"), GROUP_PLAN_HEADERS, "/group-plans/import"))
    manifest = {
        "provinceCode": province.code,
        "provinceName": province.name,
        "year": 2025,
        "count": len(items),
        "items": items,
        "note": "Payloads are for dry-run first. Do not import until dry-run rejected=0 and CONFIRM_PROVINCE_IMPORT is set.",
    }
    write_payload(PAYLOADS_DIR / province.code / "manifest.json", manifest)
    write_curl_script(province, items, dry_run=True)
    write_curl_script(province, items, dry_run=False)
    return manifest


def init_reviewed_templates(province: ProvinceConfig) -> None:
    ensure_dirs(province)
    for subject in SUBJECT_TYPES:
        path = score_rank_file(province, subject)
        if not path.exists():
            write_csv(path, SCORE_RANK_HEADERS + SOURCE_META_HEADERS, [])
    for path, headers in (
        (reviewed_file(province, "group_lines"), GROUP_LINE_HEADERS + SOURCE_META_HEADERS),
        (reviewed_file(province, "group_plans"), GROUP_PLAN_HEADERS + SOURCE_META_HEADERS),
    ):
        if not path.exists():
            write_csv(path, headers, [])


def sync_legacy_sichuan_reviewed(overwrite: bool) -> dict[str, Any]:
    province = PROVINCES["SC"]
    ensure_dirs(province)
    files = [
        (LEGACY_SICHUAN_REVIEWED_DIR / "score_rank_sc_2025_历史类_reviewed.csv", score_rank_file(province, "历史类"), "manual_verified"),
        (LEGACY_SICHUAN_REVIEWED_DIR / "score_rank_sc_2025_物理类_reviewed.csv", score_rank_file(province, "物理类"), "manual_verified"),
        (LEGACY_SICHUAN_REVIEWED_DIR / "group_lines_sc_2025_reviewed.csv", reviewed_file(province, "group_lines"), "school_verified"),
        (LEGACY_SICHUAN_REVIEWED_DIR / "group_plans_sc_2025_reviewed.csv", reviewed_file(province, "group_plans"), "school_verified"),
    ]
    report: dict[str, Any] = {
        "provinceCode": province.code,
        "sourceDir": str(LEGACY_SICHUAN_REVIEWED_DIR),
        "overwrite": overwrite,
        "copied": [],
        "skipped": [],
        "missing": [],
    }
    for source, target, default_source_level in files:
        if not source.exists():
            report["missing"].append(str(source))
            continue
        if target.exists() and non_empty_rows(target) and not overwrite:
            report["skipped"].append({
                "path": str(target),
                "reason": "target_has_reviewed_rows",
            })
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        ensure_source_level_column(target, default_source_level)
        report["copied"].append({
            "source": str(source),
            "target": str(target),
            "rows": len(non_empty_rows(target)),
            "defaultSourceLevel": default_source_level,
        })
    write_json(REPORTS_DIR / province.code / "legacy_sichuan_reviewed_sync_report.json", report)
    return report


def selected_provinces(raw: str) -> list[ProvinceConfig]:
    value = (raw or "ALL").upper()
    codes = list(PROVINCES) if value == "ALL" else [item.strip() for item in value.split(",") if item.strip()]
    unknown = [code for code in codes if code not in PROVINCES]
    if unknown:
        raise SystemExit(f"Unsupported province code: {','.join(unknown)}")
    return [PROVINCES[code] for code in codes]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["init-reviewed", "sync-sichuan-reviewed", "validate-reviewed", "payloads"])
    parser.add_argument("--province", default="ALL", help="SC|HB|AH|ALL|SC,HB")
    parser.add_argument("--allow-incomplete", action="store_true", help="validation errors still return 0")
    parser.add_argument("--overwrite", action="store_true", help="overwrite existing SC reviewed CSVs when syncing legacy Sichuan reviewed data")
    args = parser.parse_args()

    if args.command == "sync-sichuan-reviewed":
        report = sync_legacy_sichuan_reviewed(args.overwrite)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0

    failed = False
    for province in selected_provinces(args.province):
        if args.command == "init-reviewed":
            init_reviewed_templates(province)
            print(f"{province.code}: initialized reviewed templates in {REVIEWED_DIR / province.code}")
            continue
        report = validate_reviewed(province)
        print(f"{province.code}: reviewed validation errors={report['errorCount']} warnings={report['warningCount']}")
        if report["errorCount"] and not args.allow_incomplete:
            failed = True
            continue
        if args.command == "payloads":
            manifest = build_payloads(province)
            print(f"{province.code}: wrote payload manifest count={manifest['count']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
