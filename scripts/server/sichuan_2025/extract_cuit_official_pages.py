#!/usr/bin/env python3
"""从成都信息工程大学官方页面生成四川 2025 reviewed 草稿。"""

import html
import json
import re
import ssl
import sys
import urllib.request
from collections import defaultdict
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


UNIVERSITY_NAME = "成都信息工程大学"
SCORE_URL = "https://zs.cuit.edu.cn/info/1022/1579.htm"
PLAN_URL = "https://zs.cuit.edu.cn/info/1096/1498.htm"
SOURCE_LEVEL = "school_verified"


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    context = ssl._create_unverified_context()
    with urllib.request.urlopen(request, timeout=20, context=context) as response:
        return response.read().decode("utf-8", "ignore")


def clean_cell(value: str) -> str:
    text = re.sub(r"<[^>]+>", " ", value)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def table_rows(text: str) -> list[list[str]]:
    rows: list[list[str]] = []
    for raw_row in re.findall(r"<tr[^>]*>(.*?)</tr>", text, flags=re.S | re.I):
        cells = [clean_cell(cell) for cell in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", raw_row, flags=re.S | re.I)]
        if cells:
            rows.append(cells)
    return rows


def subject_from_selection(selection: str) -> str:
    if selection.startswith("历史"):
        return "历史类"
    if selection.startswith("物理"):
        return "物理类"
    return ""


def first_subject(subject_type: str) -> str:
    return "历史" if subject_type == "历史类" else "物理"


def resubject(selection: str) -> str:
    if "不限" in selection:
        return "不限"
    if "化学" in selection:
        return "化学"
    if "地理" in selection:
        return "地理"
    return "需复核"


def score_int(value: str) -> str:
    match = re.match(r"(\d+)", value)
    return match.group(1) if match else ""


def score_float(value: str) -> float | None:
    try:
        return float(value)
    except ValueError:
        return None


def major_key(value: str) -> str:
    return (
        value.replace(" ", "")
        .replace("（", "(")
        .replace("）", ")")
        .replace("；", ";")
        .strip()
    )


def parse_score_rows(text: str) -> list[dict[str, Any]]:
    parsed: list[dict[str, Any]] = []
    current_subject = ""
    current_group = ""
    current_selection = ""
    for cells in table_rows(text):
        if "科类" in cells[0] or "录取情况" in cells[0]:
            continue
        if cells[0] in {"历史类", "物理类"} and len(cells) >= 8:
            current_subject = cells[0]
            current_group = cells[1]
            current_selection = cells[2]
            major_name, max_score, avg_score, min_score, min_rank = cells[3:8]
        elif cells[0].isdigit() and len(cells) >= 7:
            current_group = cells[0]
            current_selection = cells[1]
            major_name, max_score, avg_score, min_score, min_rank = cells[2:7]
        elif current_subject and current_group and len(cells) >= 5:
            major_name, max_score, avg_score, min_score, min_rank = cells[:5]
        else:
            continue
        subject_type = current_subject
        parsed.append({
            "subjectType": subject_type,
            "groupCode": current_group,
            "selection": current_selection,
            "majorName": major_key(major_name),
            "maxScore": max_score,
            "avgScore": avg_score,
            "minScoreRaw": min_score,
            "minScore": score_int(min_score),
            "minScoreFloat": score_float(min_score),
            "minRank": re.sub(r"\D", "", min_rank),
        })
    return [row for row in parsed if row["subjectType"] in {"历史类", "物理类"} and row["groupCode"].isdigit()]


def parse_plan_rows(text: str) -> list[dict[str, Any]]:
    rows = table_rows(text)
    if not rows:
        return []
    header = rows[0]
    indexes = {name: idx for idx, name in enumerate(header)}
    required = ["专业代码", "专业名称", "选考科目", "四川", "学费"]
    if any(name not in indexes for name in required):
        raise RuntimeError("成都信息工程大学计划表表头不完整")
    parsed: list[dict[str, Any]] = []
    for cells in rows[1:]:
        if len(cells) <= max(indexes.values()) or not cells[indexes["专业名称"]]:
            continue
        plan_count = re.sub(r"\D", "", cells[indexes["四川"]])
        if not plan_count:
            continue
        selection = cells[indexes["选考科目"]].replace(" ", "")
        subject_type = subject_from_selection(selection)
        if subject_type not in {"历史类", "物理类"}:
            continue
        parsed.append({
            "majorCode": cells[indexes["专业代码"]].replace(" ", ""),
            "majorName": major_key(cells[indexes["专业名称"]]),
            "selection": selection,
            "subjectType": subject_type,
            "planCount": plan_count,
            "tuition": re.sub(r"\D", "", cells[indexes["学费"]]),
        })
    return parsed


def source_hash(kind: str, rows: list[dict[str, Any]]) -> str:
    return sha256_text(json.dumps({"kind": kind, "rows": rows}, ensure_ascii=False, sort_keys=True))


def build_rows(score_rows: list[dict[str, Any]], plan_rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    score_by_major: dict[tuple[str, str], dict[str, Any]] = {}
    groups: dict[tuple[str, str], dict[str, Any]] = {}
    for row in score_rows:
        score_by_major[(row["subjectType"], major_key(row["majorName"]))] = row
        key = (row["subjectType"], row["groupCode"])
        current = groups.get(key)
        if row["minScoreFloat"] is None or not row["minRank"]:
            continue
        if current is None or row["minScoreFloat"] < current["minScoreFloat"]:
            groups[key] = row

    plan_count_by_group: dict[tuple[str, str], int] = defaultdict(int)
    plan_hash = source_hash("cuit_group_plan", plan_rows)
    line_hash = source_hash("cuit_group_line", score_rows)
    group_plan_rows: list[dict[str, Any]] = []
    for plan in plan_rows:
        score = score_by_major.get((plan["subjectType"], plan["majorName"]))
        if score is None:
            continue
        group_code = score["groupCode"]
        group_key = (plan["subjectType"], group_code)
        plan_count_by_group[group_key] += int(plan["planCount"])
        group_plan_rows.append({
            "schoolId": "",
            "universityName": UNIVERSITY_NAME,
            "groupCode": group_code,
            "groupName": f"{UNIVERSITY_NAME}{plan['subjectType']}{group_code}组",
            "majorCode": plan["majorCode"],
            "majorName": plan["majorName"],
            "subjectType": plan["subjectType"],
            "firstSubjectRequirement": first_subject(plan["subjectType"]),
            "resubjectRequirement": resubject(score["selection"]),
            "planCount": plan["planCount"],
            "tuition": plan["tuition"],
            "studyYears": "",
            "batch": "普通本科批B段",
            "sourcePageUrl": PLAN_URL,
            "sourceUrl": f"{PLAN_URL}\n{SCORE_URL}",
            "sourceHash": plan_hash,
            "sourceLevel": SOURCE_LEVEL,
        })

    group_line_rows: list[dict[str, Any]] = []
    for (subject_type, group_code), row in sorted(groups.items()):
        group_line_rows.append({
            "schoolId": "",
            "universityName": UNIVERSITY_NAME,
            "groupCode": group_code,
            "groupName": f"{UNIVERSITY_NAME}{subject_type}{group_code}组",
            "subjectType": subject_type,
            "firstSubjectRequirement": first_subject(subject_type),
            "resubjectRequirement": resubject(row["selection"]),
            "minScore": row["minScore"],
            "minRank": row["minRank"],
            "planCount": str(plan_count_by_group.get((subject_type, group_code), "")),
            "batch": "普通本科批B段",
            "sourcePageUrl": SCORE_URL,
            "sourceUrl": f"{SCORE_URL}\n{PLAN_URL}",
            "sourceHash": line_hash,
            "sourceLevel": SOURCE_LEVEL,
        })
    return group_line_rows, group_plan_rows


def main() -> int:
    ensure_dirs()
    score_text = fetch_text(SCORE_URL)
    plan_text = fetch_text(PLAN_URL)
    line_rows, plan_rows = build_rows(parse_score_rows(score_text), parse_plan_rows(plan_text))
    write_csv(DRAFT_DIR / "cuit_group_lines_sc_2025_reviewed_draft.csv", GROUP_LINE_HEADERS + SOURCE_META_HEADERS, line_rows)
    write_csv(DRAFT_DIR / "cuit_group_plans_sc_2025_reviewed_draft.csv", GROUP_PLAN_HEADERS + SOURCE_META_HEADERS, plan_rows)
    print(f"cuit group_lines={len(line_rows)} group_plans={len(plan_rows)}")
    print(f"wrote {DRAFT_DIR / 'cuit_group_lines_sc_2025_reviewed_draft.csv'}")
    print(f"wrote {DRAFT_DIR / 'cuit_group_plans_sc_2025_reviewed_draft.csv'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
