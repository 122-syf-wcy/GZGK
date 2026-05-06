#!/usr/bin/env python3
"""Use an OpenAI-compatible vision/text model to create draft extraction rows.

Outputs are drafts only. They must be manually reviewed before admin import.
"""

import argparse
import base64
import json
import os
import re
import urllib.request
from pathlib import Path
from typing import Any

from common import (
    DRAFT_DIR,
    REPORTS_DIR,
    decode_body,
    ensure_dirs,
    guess_mime_type,
    read_csv,
    read_jsonl,
    strip_html_text,
    write_csv,
)


SYSTEM_PROMPT = """你是高考公开数据抽取助手。只抽取页面或图片中明确出现的数据，不猜测。
输出必须是 JSON 对象，不要输出 Markdown。
可用字段：
{
  "dataType": "score_rank|group_line|group_plan|unknown",
  "subjectType": "物理类|历史类|",
  "groupLines": [],
  "groupPlans": [],
  "scoreRanks": [],
  "notes": []
}
scoreRanks 行字段：score, scoreLabel, segmentCount, cumulativeCount。
groupLines 行字段：schoolId, universityName, groupCode, groupName, subjectType, firstSubjectRequirement, resubjectRequirement, minScore, minRank, planCount, batch。
groupPlans 行字段：schoolId, universityName, groupCode, groupName, majorCode, majorName, subjectType, firstSubjectRequirement, resubjectRequirement, planCount, tuition, studyYears, batch。
如果来源不是四川 2025 普通本科批B段或一分一段，请返回 dataType=unknown 并在 notes 说明。
"""


def api_config() -> tuple[str, str, str]:
    base_url = (
        os.environ.get("VISION_BASE_URL")
        or os.environ.get("GZLY_VISION_BASE_URL")
        or os.environ.get("GZLY_AI_BASE_URL")
        or ""
    )
    api_key = (
        os.environ.get("VISION_API_KEY")
        or os.environ.get("GZLY_VISION_API_KEY")
        or os.environ.get("GZLY_AI_API_KEY")
        or ""
    )
    model = (
        os.environ.get("VISION_MODEL")
        or os.environ.get("GZLY_VISION_MODEL")
        or os.environ.get("GZLY_AI_MODEL")
        or "gpt-4o-mini"
    )
    if not base_url or not api_key:
        raise SystemExit(
            "Missing VISION_BASE_URL/GZLY_VISION_BASE_URL/GZLY_AI_BASE_URL "
            "or VISION_API_KEY/GZLY_VISION_API_KEY/GZLY_AI_API_KEY"
        )
    return base_url.rstrip("/"), api_key, model


def chat_completion(messages: list[dict[str, Any]], max_tokens: int) -> str:
    base_url, api_key, model = api_config()
    timeout = int(os.environ.get("VISION_TIMEOUT_SECONDS", "30"))
    endpoint = base_url
    if not endpoint.endswith("/chat/completions"):
        endpoint = endpoint.rstrip("/") + "/chat/completions"
    payload = json.dumps({
        "model": model,
        "messages": messages,
        "temperature": 0,
        "max_tokens": max_tokens,
    }).encode("utf-8")
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


def extract_json(text: str) -> dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?", "", text, flags=re.I).strip()
        text = re.sub(r"```$", "", text).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.S)
        if not match:
            return {"dataType": "unknown", "notes": [text[:1000]], "rawResponse": text}
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return {"dataType": "unknown", "notes": [text[:1000]], "rawResponse": text}


def image_message(path: Path, instruction: str) -> list[dict[str, Any]]:
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    mime = guess_mime_type(path)
    return [
        {"type": "text", "text": instruction},
        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{data}"}},
    ]


def text_message(path: Path, instruction: str) -> list[dict[str, Any]]:
    text = decode_body(path.read_bytes())
    plain = strip_html_text(text)
    if len(plain) > 14000:
        plain = plain[:14000]
    return [{"type": "text", "text": instruction + "\n\n来源文本：\n" + plain}]


def call_model_for_file(path: Path, metadata: dict[str, Any], max_tokens: int) -> dict[str, Any]:
    suffix = path.suffix.lower()
    instruction = (
        "请从该公开来源中抽取四川 2025 普通高考数据草稿。"
        f"\n来源URL：{metadata.get('sourcePageUrl') or metadata.get('url') or metadata.get('finalUrl') or ''}"
        f"\n院校：{metadata.get('universityName', '')}"
        f"\n科类：{metadata.get('subjectType', '')}"
        "\n只输出 JSON。"
    )
    if suffix in {".jpg", ".jpeg", ".png", ".webp"}:
        user_content = image_message(path, instruction)
    else:
        user_content = text_message(path, instruction)
    response = chat_completion(
        [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ],
        max_tokens=max_tokens,
    )
    parsed = extract_json(response)
    parsed["sourceMeta"] = metadata
    parsed["rawPath"] = str(path)
    parsed["rawResponse"] = response
    return parsed


def load_official_items(source_audit: Path) -> list[dict[str, Any]]:
    if not source_audit.exists():
        return []
    payload = json.loads(source_audit.read_text(encoding="utf-8"))
    items: list[dict[str, Any]] = []
    for record in payload.get("officialSources", []):
        for child in record.get("children", []):
            raw_path = child.get("rawPath", "")
            source_url = child.get("url", "")
            is_score_rank_image = (
                (child.get("dataType") or record.get("dataType")) == "score_rank"
                and "/Upload/image/20250625/" in source_url
                and Path(raw_path).suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
            )
            if raw_path and is_score_rank_image:
                items.append({
                    "rawPath": raw_path,
                    "dataType": child.get("dataType") or record.get("dataType"),
                    "subjectType": child.get("subjectType") or record.get("subjectType"),
                    "sourcePageUrl": record.get("url"),
                    "sourceUrl": source_url,
                    "sourceHash": child.get("sourceHash"),
                    "sourceLevel": "official",
                })
    return items


def load_school_items(discovery_jsonl: Path, min_confidence: int) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for row in read_jsonl(discovery_jsonl):
        raw_path = row.get("rawPath", "")
        if not raw_path or not Path(raw_path).exists():
            continue
        if int(row.get("confidence") or 0) < min_confidence:
            continue
        items.append(row)
    return items


def load_review_queue_items(
    review_queue: Path,
    statuses: set[str],
    min_priority: int,
    candidate_types: set[str],
    require_batch_b: bool,
    exclude_blocked_sources: bool,
) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for row in read_csv(review_queue):
        raw_path = row.get("rawPath", "")
        if not raw_path or not Path(raw_path).exists():
            continue
        if row.get("reviewStatus", "") not in statuses:
            continue
        try:
            priority = int(row.get("priority") or 0)
        except ValueError:
            priority = 0
        if priority < min_priority:
            continue
        row_candidate_types = {item.strip() for item in row.get("candidateType", "").split("+") if item.strip()}
        if candidate_types and not (row_candidate_types & candidate_types):
            continue
        if require_batch_b and str(row.get("hasBatchB", "")).strip() != "1":
            continue
        blocking_reason = row.get("blockingReason", "")
        if exclude_blocked_sources and (
            "考试院通用政策/新闻页" in blocking_reason
            or "非高校官网或聚合/门户来源" in blocking_reason
        ):
            continue
        next_row = dict(row)
        next_row["sourceLevel"] = row.get("suggestedSourceLevel") or "school_verified"
        next_row["sourceUrl"] = row.get("finalUrl") or row.get("sourcePageUrl") or ""
        items.append(next_row)
    items.sort(key=lambda item: int(item.get("priority") or 0), reverse=True)
    return items


def rows_from_extractions(extractions: list[dict[str, Any]], key: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for item in extractions:
        source = item.get("sourceMeta", {})
        for row in item.get(key, []) or []:
            if not isinstance(row, dict):
                continue
            next_row = dict(row)
            next_row["sourcePageUrl"] = source.get("sourcePageUrl") or source.get("url") or ""
            next_row["sourceUrl"] = source.get("sourceUrl") or source.get("finalUrl") or ""
            next_row["sourceHash"] = source.get("sourceHash") or ""
            next_row["sourceLevel"] = source.get("sourceLevel") or ""
            rows.append(next_row)
    return rows


def write_draft_csvs(extractions: list[dict[str, Any]]) -> None:
    score_rows = rows_from_extractions(extractions, "scoreRanks")
    group_lines = rows_from_extractions(extractions, "groupLines")
    group_plans = rows_from_extractions(extractions, "groupPlans")
    write_csv(
        DRAFT_DIR / "score_rank_ocr_draft.csv",
        ["score", "scoreLabel", "segmentCount", "cumulativeCount", "subjectType", "sourcePageUrl", "sourceUrl", "sourceHash", "sourceLevel"],
        score_rows,
    )
    write_csv(
        DRAFT_DIR / "group_line_extraction_draft.csv",
        ["schoolId", "universityName", "groupCode", "groupName", "subjectType", "firstSubjectRequirement", "resubjectRequirement", "minScore", "minRank", "planCount", "batch", "sourcePageUrl", "sourceUrl", "sourceHash", "sourceLevel"],
        group_lines,
    )
    write_csv(
        DRAFT_DIR / "group_plan_extraction_draft.csv",
        ["schoolId", "universityName", "groupCode", "groupName", "majorCode", "majorName", "subjectType", "firstSubjectRequirement", "resubjectRequirement", "planCount", "tuition", "studyYears", "batch", "sourcePageUrl", "sourceUrl", "sourceHash", "sourceLevel"],
        group_plans,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Create OCR/model extraction drafts.")
    parser.add_argument("--source-audit", default=str(REPORTS_DIR / "source_audit.json"))
    parser.add_argument("--school-discovery", default=str(REPORTS_DIR / "school_discovery.jsonl"))
    parser.add_argument("--include-school-pages", action="store_true")
    parser.add_argument("--skip-official", action="store_true")
    parser.add_argument("--review-queue", default="")
    parser.add_argument("--review-status", default="ready_for_manual_extraction,needs_manual_check")
    parser.add_argument("--candidate-type", default="group_line,group_plan")
    parser.add_argument("--require-batch-b", action="store_true")
    parser.add_argument("--exclude-blocked-sources", action="store_true")
    parser.add_argument("--min-confidence", type=int, default=8)
    parser.add_argument("--min-priority", type=int, default=18)
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--max-tokens", type=int, default=8192)
    args = parser.parse_args()
    ensure_dirs()

    items: list[dict[str, Any]] = []
    if not args.skip_official:
        items.extend(load_official_items(Path(args.source_audit)))
    if args.review_queue:
        statuses = {item.strip() for item in args.review_status.split(",") if item.strip()}
        candidate_types = {item.strip() for item in args.candidate_type.split(",") if item.strip()}
        items.extend(
            load_review_queue_items(
                Path(args.review_queue),
                statuses,
                args.min_priority,
                candidate_types,
                args.require_batch_b,
                args.exclude_blocked_sources,
            )
        )
    if args.include_school_pages:
        items.extend(load_school_items(Path(args.school_discovery), args.min_confidence))
    items = items[: args.limit]
    if not items:
        raise SystemExit("No input files found. Run source/school crawlers first.")

    out_jsonl = DRAFT_DIR / "vision_extractions.jsonl"
    extractions: list[dict[str, Any]] = []
    with out_jsonl.open("w", encoding="utf-8") as handle:
        for idx, item in enumerate(items, 1):
            path = Path(item["rawPath"])
            print(f"[{idx}/{len(items)}] extracting {path}")
            try:
                extracted = call_model_for_file(path, item, args.max_tokens)
            except Exception as exc:
                extracted = {
                    "dataType": "unknown",
                    "scoreRanks": [],
                    "groupLines": [],
                    "groupPlans": [],
                    "notes": [f"vision extraction failed: {exc}"],
                    "sourceMeta": item,
                    "rawPath": str(path),
                    "rawResponse": "",
                }
            extractions.append(extracted)
            handle.write(json.dumps(extracted, ensure_ascii=False, sort_keys=True) + "\n")
    write_draft_csvs(extractions)
    print(f"wrote {out_jsonl}")
    print(f"wrote {DRAFT_DIR / 'score_rank_ocr_draft.csv'}")
    print(f"wrote {DRAFT_DIR / 'group_line_extraction_draft.csv'}")
    print(f"wrote {DRAFT_DIR / 'group_plan_extraction_draft.csv'}")


if __name__ == "__main__":
    main()
