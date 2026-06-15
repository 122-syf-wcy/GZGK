#!/usr/bin/env python3
"""Extract official score-rank images with a vision model and strict validation.

The model is only used for transcription. Rows are accepted only when the full
subject sequence passes arithmetic checks:

  cumulative(score) - cumulative(score + 1) == segmentCount(score)

The script never imports production data. Writing reviewed CSVs requires an
explicit confirmation flag.
"""

from __future__ import annotations

import argparse
import base64
import csv
import json
import re
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

from extract_group_lines_with_vision import api_config, guess_mime_type, parse_json_response
from extract_score_rank_grid import load_score_images


ROOT = Path(__file__).resolve().parent
DRAFT_DIR = ROOT / "draft"
REPORTS_DIR = ROOT / "reports"
REVIEWED_DIR = ROOT / "reviewed"

HEADERS = [
    "score",
    "scoreLabel",
    "segmentCount",
    "cumulativeCount",
    "sourcePageUrl",
    "sourceUrl",
    "sourceHash",
    "sourceLevel",
]

DRAFT_HEADERS = [
    "provinceCode",
    "subjectType",
    "imageIndex",
    *HEADERS,
    "rawPath",
    "reviewStatus",
    "parserNotes",
]

SYSTEM_PROMPT = """你是高考一分一段官方图片转录助手。只转录图片表格中明确出现的分数、人数、累计人数，不猜测。
输出必须是 JSON 对象，不要输出 Markdown。
JSON 结构：
{
  "rows": [
    {"score": 691, "segmentCount": 3, "cumulativeCount": 25}
  ],
  "notes": []
}
规则：
- 只转录图片中表格行；忽略标题、注释、水印、页眉页脚。
- 一张图里有多组“分数/人数/累计人数”并排小表时，按从左到右、从上到下全部输出。
- 分数必须是 0-750 的整数；人数和累计人数必须是非负整数。
- 看不清的单元格整行省略，不要猜。
"""


def text(value: Any) -> str:
    return str(value or "").strip()


def int_value(value: Any) -> int | None:
    raw = re.sub(r"\D+", "", text(value))
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def chat_completion(messages: list[dict[str, Any]], max_tokens: int, timeout: int) -> str:
    endpoint, api_key, model = api_config()
    payload = json.dumps({
        "model": model,
        "messages": messages,
        "temperature": 0,
        "max_tokens": max_tokens,
    }, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        result = json.loads(response.read().decode("utf-8"))
    return result["choices"][0]["message"]["content"]


def call_model(item: dict[str, Any], max_tokens: int, timeout: int) -> dict[str, Any]:
    path = Path(item["rawPath"])
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    instruction = (
        f"请转录{item['provinceCode']} 2025年{item['subjectType']}一分一段表图片。"
        f"\n来源页：{item['sourcePageUrl']}"
        f"\n图片：{item['sourceUrl']}"
        "\n只输出 JSON。"
    )
    raw = chat_completion(
        [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": instruction},
                    {"type": "image_url", "image_url": {"url": f"data:{guess_mime_type(path)};base64,{data}"}},
                ],
            },
        ],
        max_tokens=max_tokens,
        timeout=timeout,
    )
    parsed = parse_json_response(raw)
    parsed["sourceMeta"] = item
    parsed["rawResponse"] = raw
    return parsed


def normalize_rows(extractions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen: set[tuple[str, str, int]] = set()
    for extraction in extractions:
        source = extraction.get("sourceMeta") or {}
        for row in extraction.get("rows") or []:
            if not isinstance(row, dict):
                continue
            score = int_value(row.get("score"))
            segment = int_value(row.get("segmentCount"))
            cumulative = int_value(row.get("cumulativeCount"))
            if score is None or segment is None or cumulative is None:
                continue
            if score < 0 or score > 750 or segment < 0 or cumulative <= 0:
                continue
            key = (text(source.get("provinceCode")), text(source.get("subjectType")), score)
            if key in seen:
                continue
            seen.add(key)
            rows.append({
                "provinceCode": source.get("provinceCode", ""),
                "subjectType": source.get("subjectType", ""),
                "imageIndex": source.get("imageIndex", ""),
                "score": score,
                "scoreLabel": f"{score}分",
                "segmentCount": segment,
                "cumulativeCount": cumulative,
                "sourcePageUrl": source.get("sourcePageUrl", ""),
                "sourceUrl": source.get("sourceUrl", ""),
                "sourceHash": source.get("sourceHash", ""),
                "sourceLevel": "manual_verified",
                "rawPath": source.get("rawPath", ""),
                "reviewStatus": "vision_transcribed",
                "parserNotes": "",
            })
    rows.sort(key=lambda row: (row["provinceCode"], row["subjectType"], -int(row["score"])))
    return rows


def validate_rows(rows: list[dict[str, Any]]) -> dict[str, Any]:
    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[(row["provinceCode"], row["subjectType"])].append(row)
    report: dict[str, Any] = {"subjects": {}, "blockingIssues": []}
    for (province_code, subject), subject_rows in grouped.items():
        ordered = sorted(subject_rows, key=lambda row: int(row["score"]), reverse=True)
        scores = [int(row["score"]) for row in ordered]
        issues: list[dict[str, Any]] = []
        previous_score: int | None = None
        previous_cumulative: int | None = None
        seen_scores: set[int] = set()
        for row in ordered:
            score = int(row["score"])
            segment = int(row["segmentCount"])
            cumulative = int(row["cumulativeCount"])
            if score in seen_scores:
                issues.append({"score": score, "issue": "duplicate_score"})
            seen_scores.add(score)
            if previous_score is not None and score != previous_score - 1:
                issues.append({"score": score, "issue": "score_gap", "previousScore": previous_score})
            if previous_cumulative is not None:
                if cumulative <= previous_cumulative:
                    issues.append({"score": score, "issue": "cumulative_not_increasing", "value": cumulative, "previous": previous_cumulative})
                expected_segment = cumulative - previous_cumulative
                if expected_segment != segment:
                    issues.append({"score": score, "issue": "segment_mismatch", "value": segment, "expected": expected_segment})
            previous_score = score
            previous_cumulative = cumulative
        missing_scores: list[int] = []
        if scores:
            score_set = set(scores)
            missing_scores = [score for score in range(max(scores), min(scores) - 1, -1) if score not in score_set]
        item = {
            "rowCount": len(ordered),
            "minScore": min(scores) if scores else None,
            "maxScore": max(scores) if scores else None,
            "missingScoreCount": len(missing_scores),
            "missingScoresSample": missing_scores[:120],
            "issueCount": len(issues),
            "issuesSample": issues[:120],
        }
        key = f"{province_code}_{subject}"
        report["subjects"][key] = item
        if item["rowCount"] < 200 or item["missingScoreCount"] or item["issueCount"]:
            report["blockingIssues"].append({"provinceCode": province_code, "subjectType": subject, **item})
    return report


def write_csv(path: Path, headers: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in headers})


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def reviewed_path(province_code: str, subject: str) -> Path:
    return REVIEWED_DIR / province_code / f"score_rank_{province_code.lower()}_2025_{subject}_reviewed.csv"


def write_reviewed(rows: list[dict[str, Any]], province_code: str) -> dict[str, Any]:
    report: dict[str, Any] = {}
    for subject in sorted({row["subjectType"] for row in rows}):
        subject_rows = [row for row in rows if row["subjectType"] == subject]
        path = reviewed_path(province_code, subject)
        write_csv(path, HEADERS, subject_rows)
        report[subject] = {"path": str(path), "rows": len(subject_rows)}
    return report


def selected_items(province_code: str, limit_images: int) -> list[dict[str, Any]]:
    items = load_score_images(province_code)
    for idx, item in enumerate(items, 1):
        item["imageIndex"] = idx
    if limit_images > 0:
        items = items[:limit_images]
    return items


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--province", default="HB")
    parser.add_argument("--limit-images", type=int, default=0)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--max-tokens", type=int, default=32768)
    parser.add_argument("--write-reviewed", action="store_true")
    parser.add_argument("--confirm-reviewed-write", default="")
    args = parser.parse_args()

    province_code = args.province.upper()
    items = selected_items(province_code, args.limit_images)
    extractions: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    out_jsonl = DRAFT_DIR / f"score_rank_vision_extractions_{province_code.lower()}.jsonl"
    out_jsonl.parent.mkdir(parents=True, exist_ok=True)
    with out_jsonl.open("w", encoding="utf-8") as handle:
        for idx, item in enumerate(items, 1):
            print(f"[{idx}/{len(items)}] extracting {item['provinceCode']} {item['subjectType']} image {item['imageIndex']}: {item['sourceUrl']}")
            try:
                extraction = call_model(item, args.max_tokens, args.timeout)
            except (TimeoutError, urllib.error.URLError, OSError, KeyError, json.JSONDecodeError) as exc:
                errors.append({"sourceUrl": item.get("sourceUrl"), "error": str(exc)})
                continue
            handle.write(json.dumps(extraction, ensure_ascii=False) + "\n")
            extractions.append(extraction)

    rows = normalize_rows(extractions)
    write_csv(DRAFT_DIR / f"score_rank_vision_draft_{province_code.lower()}.csv", DRAFT_HEADERS, rows)
    report = validate_rows(rows)
    report.update({
        "provinceCode": province_code,
        "sourceImages": len(items),
        "draftRows": len(rows),
        "errors": errors,
        "reviewedWrite": None,
    })
    if args.write_reviewed:
        if args.confirm_reviewed_write != "OFFICIAL_SCORE_RANK_REVIEWED":
            raise SystemExit("Refuse reviewed write: set --confirm-reviewed-write OFFICIAL_SCORE_RANK_REVIEWED")
        if errors or report["blockingIssues"]:
            raise SystemExit("Refuse reviewed write: validation has errors or blockingIssues")
        report["reviewedWrite"] = write_reviewed(rows, province_code)
    write_json(REPORTS_DIR / province_code / "score_rank_vision_validation_report.json", report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 2 if errors or report["blockingIssues"] else 0


if __name__ == "__main__":
    sys.exit(main())
