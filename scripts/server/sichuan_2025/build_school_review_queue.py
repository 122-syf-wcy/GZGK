#!/usr/bin/env python3
"""Build a human review queue from Sichuan school-site source candidates.

This script does not import data. It classifies already crawled school pages so
reviewers can focus on sources that may contain 2025 Sichuan B-batch group lines
or group plans, while keeping logos as school identity metadata only.
"""

import argparse
import re
import urllib.parse
from collections import Counter
from pathlib import Path
from typing import Any

from common import REPORTS_DIR, decode_body, ensure_dirs, host_of, read_csv, strip_html_text, write_csv, write_json


QUEUE_HEADERS = [
    "reviewStatus",
    "candidateType",
    "priority",
    "blockingReason",
    "schoolId",
    "universityName",
    "logoUrl",
    "localLogoPath",
    "sourcePageUrl",
    "finalUrl",
    "host",
    "confidence",
    "matchedKeywords",
    "has2025",
    "hasSichuan",
    "hasBatchB",
    "hasGroupPattern",
    "hasLinePattern",
    "hasPlanPattern",
    "contentType",
    "sourceHash",
    "suggestedSourceLevel",
    "rawPath",
    "evidenceSnippet",
]

BLOCKED_HOST_HINTS = [
    "eol.cn",
    "gaokao.cn",
    "youzy.cn",
    "dxsbb.com",
    "zhiyuan",
    "sczjw.com.cn",
    "scedu.net",
]

NEGATIVE_TERMS = [
    "分类考试",
    "单独招生",
    "单招",
    "高职单招",
    "专升本",
    "成人高考",
    "成人高校",
    "自学考试",
    "中职",
    "对口招生",
    "艺术体育类",
    "艺术类",
    "体育类",
    "专科批",
    "高职（专科）",
    "高职专科",
]

BATCH_B_TERMS = [
    "普通本科批B段",
    "普通本科批 B 段",
    "本科批B段",
    "本科批 B 段",
    "普通类本科批次B段",
    "普通类本科批次 B 段",
]

PLAN_TERMS = ["招生计划", "招生专业", "分专业计划", "招生专业及计划"]
LINE_TERMS = ["调档线", "投档线", "录取分数线", "录取分数", "最低分", "最低位次"]


def read_candidate_text(row: dict[str, str], max_bytes: int) -> str:
    pieces = [
        row.get("evidenceSnippet", ""),
        row.get("matchedKeywords", ""),
        row.get("sourcePageUrl", ""),
        row.get("finalUrl", ""),
    ]
    raw_path = Path(row.get("rawPath", ""))
    if raw_path.exists() and raw_path.is_file():
        try:
            body = raw_path.read_bytes()[:max_bytes]
            pieces.append(decode_body(body, row.get("contentType", "")))
        except OSError as exc:
            pieces.append(f"raw read failed: {exc}")
    return strip_html_text(" ".join(pieces))


def has_any(text: str, terms: list[str]) -> bool:
    return any(term in text for term in terms)


def host_is_blocked(url: str) -> bool:
    host = host_of(url)
    if any(hint in host for hint in BLOCKED_HOST_HINTS):
        return True
    if host.endswith(".gov.cn"):
        return True
    return False


def is_general_official_policy(url: str) -> bool:
    host = host_of(url)
    return host == "sceea.cn" or host.endswith(".sceea.cn")


def looks_like_2025_target(text: str, url: str) -> bool:
    if "2025" in text:
        return True
    return "2025" in urllib.parse.unquote(url)


def line_pattern(text: str) -> bool:
    if has_any(text, LINE_TERMS) and re.search(r"(最低|投档|调档).{0,30}(位次|排名)", text):
        return True
    if re.search(r"(历史类|物理类).{0,80}[4-7]\d{2}.{0,40}(位次|排名)", text):
        return True
    return False


def plan_pattern(text: str) -> bool:
    if has_any(text, PLAN_TERMS) and re.search(r"(招生计划|分专业|招生专业).{0,120}\d{1,4}", text):
        return True
    if re.search(r"(专业名称|专业代码|专业组).{0,80}(计划数|招生计划).{0,80}\d{1,4}", text):
        return True
    return False


def group_pattern(text: str) -> bool:
    if "院校专业组" in text or "专业组" in text:
        return True
    return bool(re.search(r"(专业组代码|组号|院校代码|专业组).{0,20}[A-Z0-9]{2,6}", text, re.I))


def classify_candidate(row: dict[str, str], max_bytes: int) -> dict[str, Any]:
    text = read_candidate_text(row, max_bytes=max_bytes)
    url = row.get("finalUrl") or row.get("sourcePageUrl") or ""
    host = host_of(url)

    has_2025 = looks_like_2025_target(text, url)
    has_sichuan = "四川" in text or "在川" in text or "川招生" in text
    has_batch_b = has_any(text, BATCH_B_TERMS)
    has_group = group_pattern(text)
    has_line = line_pattern(text)
    has_plan = plan_pattern(text)
    has_negative = has_any(text, NEGATIVE_TERMS)

    candidate_types: list[str] = []
    if has_line:
        candidate_types.append("group_line")
    if has_plan:
        candidate_types.append("group_plan")
    candidate_type = "+".join(candidate_types) or "source_only"

    blockers: list[str] = []
    if is_general_official_policy(url):
        blockers.append("考试院通用政策/新闻页，不是高校官网结构化明细")
    if host_is_blocked(url):
        blockers.append("非高校官网或聚合/门户来源")
    if not has_2025:
        blockers.append("未确认2025")
    if not has_sichuan:
        blockers.append("未确认四川口径")
    if not has_batch_b:
        blockers.append("未确认普通本科批B段")
    if not has_group:
        blockers.append("未发现院校专业组结构")
    if candidate_type == "source_only":
        blockers.append("未发现可抽取的线/计划字段")
    if has_negative and not has_batch_b:
        blockers.append("疑似非目标批次或非普通本科")

    confidence = int(row.get("confidence") or 0)
    priority = confidence
    if has_2025:
        priority += 2
    if has_sichuan:
        priority += 2
    if has_batch_b:
        priority += 4
    if has_group:
        priority += 3
    if has_line:
        priority += 3
    if has_plan:
        priority += 2
    if host_is_blocked(url):
        priority -= 8
    if has_negative and not has_batch_b:
        priority -= 4

    if not blockers:
        review_status = "ready_for_manual_extraction"
    elif has_2025 and has_sichuan and (has_line or has_plan) and not host_is_blocked(url):
        review_status = "needs_manual_check"
    else:
        review_status = "reject_or_low_priority"

    return {
        "reviewStatus": review_status,
        "candidateType": candidate_type,
        "priority": max(priority, 0),
        "blockingReason": ";".join(blockers),
        "schoolId": row.get("schoolId", ""),
        "universityName": row.get("universityName", ""),
        "logoUrl": row.get("logoUrl", ""),
        "localLogoPath": row.get("localLogoPath", ""),
        "sourcePageUrl": row.get("sourcePageUrl", ""),
        "finalUrl": row.get("finalUrl", ""),
        "host": host,
        "confidence": confidence,
        "matchedKeywords": row.get("matchedKeywords", ""),
        "has2025": int(has_2025),
        "hasSichuan": int(has_sichuan),
        "hasBatchB": int(has_batch_b),
        "hasGroupPattern": int(has_group),
        "hasLinePattern": int(has_line),
        "hasPlanPattern": int(has_plan),
        "contentType": row.get("contentType", ""),
        "sourceHash": row.get("sourceHash", ""),
        "suggestedSourceLevel": "school_verified" if not host_is_blocked(url) and not is_general_official_policy(url) else "",
        "rawPath": row.get("rawPath", ""),
        "evidenceSnippet": row.get("evidenceSnippet", "")[:500],
    }


def build_report(rows: list[dict[str, Any]]) -> dict[str, Any]:
    status_counts = Counter(row["reviewStatus"] for row in rows)
    type_counts = Counter(row["candidateType"] for row in rows)
    blockers = Counter()
    for row in rows:
        for blocker in str(row.get("blockingReason", "")).split(";"):
            if blocker:
                blockers[blocker] += 1
    return {
        "total": len(rows),
        "statusCounts": dict(status_counts),
        "candidateTypeCounts": dict(type_counts),
        "blockingReasonCounts": dict(blockers),
        "topCandidates": [
            {
                "schoolId": row["schoolId"],
                "universityName": row["universityName"],
                "candidateType": row["candidateType"],
                "priority": row["priority"],
                "sourcePageUrl": row["sourcePageUrl"],
                "blockingReason": row["blockingReason"],
            }
            for row in rows[:20]
        ],
    }


def write_markdown_report(path: Path, report: dict[str, Any]) -> None:
    lines = [
        "# 四川 2025 高校官网候选复核队列报告",
        "",
        f"- 候选总数：{report['total']}",
        "- 该报告只用于复核排队，不代表数据已核验或可入库。",
        "- `logoUrl/localLogoPath` 仅用于识别院校，不作为招生数据来源证据。",
        "",
        "## 状态统计",
        "",
    ]
    for key, value in report["statusCounts"].items():
        lines.append(f"- {key}: {value}")
    lines.extend(["", "## 候选类型统计", ""])
    for key, value in report["candidateTypeCounts"].items():
        lines.append(f"- {key}: {value}")
    lines.extend(["", "## 主要阻塞原因", ""])
    for key, value in report["blockingReasonCounts"].items():
        lines.append(f"- {key}: {value}")
    lines.extend(["", "## 优先复核候选 Top 20", ""])
    for item in report["topCandidates"]:
        reason = item["blockingReason"] or "无"
        lines.append(
            f"- {item['universityName']} [{item['candidateType']}] "
            f"priority={item['priority']} reason={reason} {item['sourcePageUrl']}"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default=str(REPORTS_DIR / "school_discovery_high_confidence.csv"),
        help="Input school discovery CSV.",
    )
    parser.add_argument(
        "--output",
        default=str(REPORTS_DIR / "school_candidate_review_queue.csv"),
        help="Output review queue CSV.",
    )
    parser.add_argument("--max-bytes", type=int, default=2_000_000, help="Max bytes to inspect per raw page.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    ensure_dirs()
    rows = read_csv(Path(args.input))
    queue = [classify_candidate(row, max_bytes=args.max_bytes) for row in rows]
    queue.sort(
        key=lambda row: (
            {"ready_for_manual_extraction": 0, "needs_manual_check": 1, "reject_or_low_priority": 2}.get(
                row["reviewStatus"], 3
            ),
            -int(row["priority"]),
            row["universityName"],
        )
    )

    output_path = Path(args.output)
    write_csv(output_path, QUEUE_HEADERS, queue)
    report = build_report(queue)
    write_json(REPORTS_DIR / "school_candidate_review_summary.json", report)
    write_markdown_report(REPORTS_DIR / "school_candidate_review_report.md", report)
    print(f"review queue rows={len(queue)} output={output_path}")


if __name__ == "__main__":
    main()
