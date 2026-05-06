#!/usr/bin/env python3
"""OCR official score-rank images for HB/AH/SC into draft CSVs.

Output remains draft material. Reviewed CSVs and imports require manual
verification of the validation report.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
DRAFT_DIR = ROOT / "draft"
REPORTS_DIR = ROOT / "reports"

DRAFT_HEADERS = [
    "provinceCode",
    "score",
    "scoreLabel",
    "segmentCount",
    "cumulativeCount",
    "subjectType",
    "sourcePageUrl",
    "sourceUrl",
    "sourceHash",
    "rawPath",
    "parserNotes",
]


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


def load_score_images(province_code: str) -> list[dict[str, Any]]:
    audit_path = REPORTS_DIR / province_code / "official_source_audit.json"
    payload = json.loads(audit_path.read_text(encoding="utf-8"))
    items: list[dict[str, Any]] = []
    for record in payload.get("officialSources", []):
        if record.get("dataType") != "score_rank":
            continue
        for child in record.get("children", []):
            raw_path = child.get("rawPath", "")
            source_url = child.get("url", "")
            if not raw_path or Path(raw_path).suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
                continue
            if not is_score_rank_image(province_code, source_url, raw_path):
                continue
            items.append({
                "provinceCode": province_code,
                "rawPath": raw_path,
                "subjectType": child.get("subjectType") or record.get("subjectType") or "",
                "sourcePageUrl": record.get("url", ""),
                "sourceUrl": source_url,
                "sourceHash": child.get("sourceHash") or "",
            })
    return sorted(items, key=lambda item: (item["provinceCode"], item.get("sourcePageUrl", ""), item.get("sourceUrl", "")))


def is_score_rank_image(province_code: str, source_url: str, raw_path: str) -> bool:
    lower = source_url.lower()
    size = Path(raw_path).stat().st_size if Path(raw_path).exists() else 0
    if province_code == "HB":
        return "/uploadfile/2025/0625/" in lower and size > 100_000
    if province_code == "AH":
        return "/kszx/gk/gzdt/202506/w020250625" in lower and size > 100_000
    if province_code == "SC":
        return "202506" in lower and size > 100_000
    return False


def run_tesseract(path: Path, lang: str, psm: int, timeout: int) -> str:
    command = ["tesseract", str(path), "stdout", "-l", lang, "--psm", str(psm)]
    completed = subprocess.run(command, check=True, text=True, capture_output=True, timeout=timeout)
    return completed.stdout


def infer_subject(existing: str, text: str, source_url: str, image_index: int) -> str:
    if existing in {"物理类", "历史类"}:
        return existing
    normalized = re.sub(r"\s+", "", text)
    if any(token in normalized for token in ("首选物理", "物理科目", "物理类")):
        return "物理类"
    if any(token in normalized for token in ("首选历史", "历史科目", "历史类")):
        return "历史类"
    # 安徽发布页一般先历史后物理，各 3 张图。该分支只作为草稿标注，
    # reviewed 前必须人工核对。
    if "anhuinews.com" in source_url:
        return "历史类" if image_index <= 3 else "物理类"
    return ""


def parse_score_line(line: str) -> list[dict[str, Any]]:
    numbers = re.findall(r"\d+", line)
    if len(numbers) < 3:
        return []
    rows: list[dict[str, Any]] = []
    triples = [numbers[i:i + 3] for i in range(0, len(numbers) - 2, 3)]
    for triple in triples:
        score_text, segment_text, cumulative_text = triple
        if len(score_text) < 3:
            continue
        score = int(score_text[:3])
        if score < 100 or score > 750:
            continue
        segment = int(segment_text)
        cumulative = int(cumulative_text)
        if segment < 0 or cumulative <= 0:
            continue
        rows.append({
            "score": score,
            "scoreLabel": f"{score}分",
            "segmentCount": segment,
            "cumulativeCount": cumulative,
            "parserNotes": line.strip(),
        })
    return rows


def parse_text(text: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen_scores: set[int] = set()
    for line in text.splitlines():
        for row in parse_score_line(line):
            score = int(row["score"])
            if score in seen_scores:
                continue
            seen_scores.add(score)
            rows.append(row)
    return rows


def merge_rows(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    by_key: dict[tuple[str, str, int], dict[str, Any]] = {}
    duplicates: list[dict[str, Any]] = []
    for row in rows:
        key = (row["provinceCode"], row["subjectType"], int(row["score"]))
        existing = by_key.get(key)
        if existing:
            duplicates.append({
                "provinceCode": row["provinceCode"],
                "subjectType": row["subjectType"],
                "score": row["score"],
                "firstRawPath": existing.get("rawPath", ""),
                "secondRawPath": row.get("rawPath", ""),
            })
            continue
        by_key[key] = row
    merged = list(by_key.values())
    merged.sort(key=lambda row: (row["provinceCode"], row["subjectType"], -int(row["score"])))
    return merged, duplicates


def validate_rows(rows: list[dict[str, Any]]) -> dict[str, Any]:
    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[(row["provinceCode"], row["subjectType"])].append(row)
    report: dict[str, Any] = {"subjects": {}, "blockingIssues": []}
    for (province_code, subject), subject_rows in grouped.items():
        ordered = sorted(subject_rows, key=lambda row: int(row["score"]), reverse=True)
        scores = [int(row["score"]) for row in ordered]
        issues: list[dict[str, Any]] = []
        previous_score = 751
        previous_cumulative = 0
        for row in ordered:
            score = int(row["score"])
            cumulative = int(row["cumulativeCount"])
            segment = int(row["segmentCount"])
            if score >= previous_score:
                issues.append({"score": score, "issue": "score_not_descending"})
            if cumulative <= previous_cumulative:
                issues.append({"score": score, "issue": "cumulative_not_increasing", "value": cumulative, "previous": previous_cumulative})
            expected_segment = cumulative - previous_cumulative if previous_cumulative else segment
            if previous_cumulative and expected_segment != segment:
                issues.append({"score": score, "issue": "segment_mismatch", "value": segment, "expected": expected_segment})
            previous_score = score
            previous_cumulative = cumulative
        missing_scores = []
        if scores:
            score_set = set(scores)
            missing_scores = [score for score in range(max(scores), min(scores) - 1, -1) if score not in score_set]
        key = f"{province_code}_{subject}"
        item = {
            "rowCount": len(ordered),
            "minScore": min(scores) if scores else None,
            "maxScore": max(scores) if scores else None,
            "missingScoreCount": len(missing_scores),
            "missingScoresSample": missing_scores[:80],
            "issueCount": len(issues),
            "issuesSample": issues[:80],
        }
        report["subjects"][key] = item
        if item["rowCount"] < 200 or item["missingScoreCount"] or item["issueCount"]:
            report["blockingIssues"].append({"provinceCode": province_code, "subjectType": subject, **item})
    return report


def selected_provinces(value: str) -> list[str]:
    if value.upper() == "ALL":
        return ["SC", "HB", "AH"]
    return [item.strip().upper() for item in value.split(",") if item.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--province", default="ALL")
    parser.add_argument("--lang", default="chi_sim+eng")
    parser.add_argument("--psm", type=int, default=6)
    parser.add_argument("--timeout", type=int, default=90)
    args = parser.parse_args()

    all_rows: list[dict[str, Any]] = []
    extraction_log: list[dict[str, Any]] = []
    text_dir = DRAFT_DIR / "score_rank_tesseract_text"
    text_dir.mkdir(parents=True, exist_ok=True)
    for province_code in selected_provinces(args.province):
        items = load_score_images(province_code)
        for image_index, item in enumerate(items, 1):
            path = Path(item["rawPath"])
            try:
                text = run_tesseract(path, args.lang, args.psm, args.timeout)
                text_path = text_dir / province_code / f"{path.stem}.txt"
                text_path.parent.mkdir(parents=True, exist_ok=True)
                text_path.write_text(text, encoding="utf-8")
                subject = infer_subject(item.get("subjectType", ""), text, item.get("sourceUrl", ""), image_index)
                parsed = parse_text(text)
                for row in parsed:
                    row.update({
                        "provinceCode": province_code,
                        "subjectType": subject,
                        "sourcePageUrl": item["sourcePageUrl"],
                        "sourceUrl": item["sourceUrl"],
                        "sourceHash": item["sourceHash"],
                        "rawPath": item["rawPath"],
                    })
                all_rows.extend(parsed)
                extraction_log.append({"provinceCode": province_code, "rawPath": str(path), "subjectType": subject, "parsedRowCount": len(parsed), "textPath": str(text_path), "error": ""})
            except Exception as exc:
                extraction_log.append({"provinceCode": province_code, "rawPath": str(path), "subjectType": item.get("subjectType", ""), "parsedRowCount": 0, "textPath": "", "error": str(exc)})

    merged_rows, duplicates = merge_rows(all_rows)
    write_csv(DRAFT_DIR / "score_rank_tesseract_draft.csv", DRAFT_HEADERS, merged_rows)
    report = validate_rows(merged_rows)
    report["imageCount"] = len(extraction_log)
    report["rawParsedRowCount"] = len(all_rows)
    report["mergedRowCount"] = len(merged_rows)
    report["duplicateCount"] = len(duplicates)
    report["duplicatesSample"] = duplicates[:80]
    report["extractionLog"] = extraction_log
    write_json(REPORTS_DIR / "score_rank_tesseract_validation.json", report)
    print(f"images={len(extraction_log)} raw_rows={len(all_rows)} merged_rows={len(merged_rows)} blocking_issues={len(report['blockingIssues'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
