#!/usr/bin/env python3
"""从四川农业大学官方招生数据 API 生成四川 2025 reviewed 草稿。"""

import json
import ssl
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

from common import (
    DRAFT_DIR,
    GROUP_LINE_HEADERS,
    GROUP_PLAN_HEADERS,
    SOURCE_META_HEADERS,
    ensure_dirs,
    sha256_text,
    write_csv,
)


BASE_URL = "https://zsdata.sicau.edu.cn/lqxx/s"
SOURCE_PAGE_URL = "https://zsdata.sicau.edu.cn/zsdata/lqxx/#/lnfs"
SOURCE_LEVEL = "school_verified"
UNIVERSITY_NAME = "四川农业大学"
YEAR = "2025"
PROVINCE = "四川"
ZSLB = "普通类"


def post_json(path: str, payload: Any) -> dict[str, Any]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + path,
        data=data,
        headers={
            "Content-Type": "application/json;charset=UTF-8",
            "User-Agent": "Mozilla/5.0",
        },
    )
    context = ssl._create_unverified_context()
    with urllib.request.urlopen(request, timeout=20, context=context) as response:
        return json.loads(response.read().decode("utf-8"))


def filter_payload(subject_type: str) -> list[dict[str, str]]:
    return [
        {"field": "sf", "value": PROVINCE},
        {"field": "nf", "value": YEAR},
        {"field": "klmc", "value": subject_type},
        {"field": "zslb", "value": ZSLB},
        {"field": "xkkm", "value": ""},
        {"field": "zygroup", "value": ""},
    ]


def fetch_plans(subject_type: str) -> list[dict[str, Any]]:
    payload = filter_payload(subject_type)
    result = post_json("/api/front/lqxx2/getList?type=zsjh", payload)
    if not result.get("success"):
        raise RuntimeError(f"四川农业大学招生计划接口失败: {result}")
    return [row for row in result.get("list", []) if row.get("pcmc") == "本科批"]


def fetch_scores(subject_type: str) -> list[dict[str, Any]]:
    payload = filter_payload(subject_type)[:4]
    result = post_json("/api/front/lqxx2/getList?type=lnfs", payload)
    if not result.get("success"):
        raise RuntimeError(f"四川农业大学录取分数接口失败: {result}")
    return [row for row in result.get("list", []) if row.get("zslb") == ZSLB]


def text(value: Any) -> str:
    return str(value or "").strip()


def int_text(value: Any) -> str:
    raw = text(value).replace(",", "")
    if not raw:
        return ""
    try:
        return str(int(float(raw)))
    except ValueError:
        return ""


def subject_requirement(value: str) -> str:
    text_value = text(value)
    if not text_value or text_value == "不提科目要求":
        return "不限"
    if "均须选考" in text_value or "必须选考" in text_value:
        return text_value.split("（", 1)[0].replace(",", "、")
    return text_value


def source_hash(kind: str, subject_type: str, rows: list[dict[str, Any]]) -> str:
    payload = {
        "kind": kind,
        "subjectType": subject_type,
        "rows": rows,
        "sourcePageUrl": SOURCE_PAGE_URL,
    }
    return sha256_text(json.dumps(payload, ensure_ascii=False, sort_keys=True))


def build_group_plans(subject_type: str, plans: list[dict[str, Any]], plan_hash: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for plan in plans:
        group_code = text(plan.get("zygroup"))
        major_name = text(plan.get("zymc"))
        if not group_code or not major_name:
            continue
        rows.append({
            "schoolId": "",
            "universityName": UNIVERSITY_NAME,
            "groupCode": group_code,
            "groupName": f"{UNIVERSITY_NAME}{subject_type}{group_code}组",
            "majorCode": text(plan.get("zydm")),
            "majorName": major_name,
            "subjectType": subject_type,
            "firstSubjectRequirement": "物理" if subject_type == "物理类" else "历史",
            "resubjectRequirement": subject_requirement(plan.get("xkkm", "")),
            "planCount": int_text(plan.get("jhrs")),
            "tuition": int_text(plan.get("zyxf")),
            "studyYears": text(plan.get("xzmc")),
            "batch": "普通本科批B段",
            "sourcePageUrl": SOURCE_PAGE_URL,
            "sourceUrl": SOURCE_PAGE_URL,
            "sourceHash": plan_hash,
            "sourceLevel": SOURCE_LEVEL,
        })
    return rows


def build_group_lines(subject_type: str, plans: list[dict[str, Any]], scores: list[dict[str, Any]], line_hash: str) -> list[dict[str, Any]]:
    groups: dict[str, dict[str, Any]] = {}
    major_to_groups: dict[str, set[str]] = defaultdict(set)
    plan_count_by_group: dict[str, int] = defaultdict(int)
    requirement_by_group: dict[str, str] = {}
    for plan in plans:
        group_code = text(plan.get("zygroup"))
        major_name = text(plan.get("zymc"))
        if not group_code or not major_name:
            continue
        major_to_groups[major_name].add(group_code)
        plan_count_by_group[group_code] += int(int_text(plan.get("jhrs")) or 0)
        requirement_by_group[group_code] = subject_requirement(plan.get("xkkm", ""))

    for score in scores:
        major_name = text(score.get("zymc"))
        group_codes = major_to_groups.get(major_name, set())
        if len(group_codes) != 1:
            continue
        group_code = next(iter(group_codes))
        min_score = int_text(score.get("zdf"))
        min_rank = int_text(score.get("zdfwc"))
        if not min_score or not min_rank:
            continue
        current = groups.get(group_code)
        if current is None or int(min_score) < int(current["minScore"]):
            groups[group_code] = {
                "schoolId": "",
                "universityName": UNIVERSITY_NAME,
                "groupCode": group_code,
                "groupName": f"{UNIVERSITY_NAME}{subject_type}{group_code}组",
                "subjectType": subject_type,
                "firstSubjectRequirement": "物理" if subject_type == "物理类" else "历史",
                "resubjectRequirement": requirement_by_group.get(group_code, "需复核"),
                "minScore": min_score,
                "minRank": min_rank,
                "planCount": str(plan_count_by_group.get(group_code, "")),
                "batch": "普通本科批B段",
                "sourcePageUrl": SOURCE_PAGE_URL,
                "sourceUrl": SOURCE_PAGE_URL,
                "sourceHash": line_hash,
                "sourceLevel": SOURCE_LEVEL,
            }
    return sorted(groups.values(), key=lambda row: (row["subjectType"], row["groupCode"]))


def main() -> int:
    ensure_dirs()
    line_rows: list[dict[str, Any]] = []
    plan_rows: list[dict[str, Any]] = []
    for subject_type in ("物理类", "历史类"):
        plans = fetch_plans(subject_type)
        scores = fetch_scores(subject_type)
        plan_hash = source_hash("group_plan", subject_type, plans)
        line_hash = source_hash("group_line", subject_type, scores)
        plan_rows.extend(build_group_plans(subject_type, plans, plan_hash))
        line_rows.extend(build_group_lines(subject_type, plans, scores, line_hash))

    write_csv(DRAFT_DIR / "sicau_group_lines_sc_2025_reviewed_draft.csv", GROUP_LINE_HEADERS + SOURCE_META_HEADERS, line_rows)
    write_csv(DRAFT_DIR / "sicau_group_plans_sc_2025_reviewed_draft.csv", GROUP_PLAN_HEADERS + SOURCE_META_HEADERS, plan_rows)
    print(f"sicau group_lines={len(line_rows)} group_plans={len(plan_rows)}")
    print(f"wrote {DRAFT_DIR / 'sicau_group_lines_sc_2025_reviewed_draft.csv'}")
    print(f"wrote {DRAFT_DIR / 'sicau_group_plans_sc_2025_reviewed_draft.csv'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
