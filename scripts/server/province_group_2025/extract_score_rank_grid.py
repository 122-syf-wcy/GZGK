#!/usr/bin/env python3
"""Extract official score-rank image tables by table-grid cell OCR.

This is stricter than whole-image OCR: it detects table ruling lines, crops each
cell, OCRs digits only, and validates cumulative consistency. Output remains
draft material until manually reviewed.
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

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parent
DRAFT_DIR = ROOT / "draft"
REPORTS_DIR = ROOT / "reports"
HEADERS = [
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


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=HEADERS, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in HEADERS})


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def run_tesseract_cell(image: Image.Image, psm: int = 7) -> str:
    tmp = Path("/tmp/gzly_score_cell.png")
    image.save(tmp)
    completed = subprocess.run(
        ["tesseract", str(tmp), "stdout", "-l", "eng", "--psm", str(psm), "-c", "tessedit_char_whitelist=0123456789"],
        text=True,
        capture_output=True,
        timeout=20,
        check=False,
    )
    return completed.stdout.strip()


def detect_horizontal_lines(image: Image.Image, threshold: int = 220) -> list[int]:
    gray = image.convert("L")
    width, height = gray.size
    x0, x1 = max(0, int(width * 0.01)), min(width, int(width * 0.99))
    rows: list[int] = []
    for y in range(height):
        dark = sum(1 for x in range(x0, x1) if gray.getpixel((x, y)) < threshold)
        if dark > (x1 - x0) * 0.65:
            rows.append(y)
    return merge_positions(rows)


def detect_vertical_lines(image: Image.Image, y0: int, y1: int, threshold: int = 190) -> list[int]:
    gray = image.convert("L")
    width, _ = gray.size
    cols: list[int] = []
    for x in range(width):
        dark = sum(1 for y in range(y0, y1 + 1) if gray.getpixel((x, y)) < threshold)
        if dark > (y1 - y0) * 0.65:
            cols.append(x)
    return merge_positions(cols)


def merge_positions(values: list[int]) -> list[int]:
    if not values:
        return []
    merged: list[int] = []
    start = prev = values[0]
    for value in values[1:]:
        if value > prev + 1:
            merged.append((start + prev) // 2)
            start = value
        prev = value
    merged.append((start + prev) // 2)
    return merged


def crop_cell(image: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    x0, y0, x1, y1 = box
    pad_x = max(1, int((x1 - x0) * 0.08))
    pad_y = max(1, int((y1 - y0) * 0.08))
    crop = image.crop((x0 + pad_x, y0 + pad_y, x1 - pad_x, y1 - pad_y)).convert("L")
    crop = ImageOps.autocontrast(crop)
    scale = 8 if crop.height < 22 else 6
    crop = crop.resize((crop.width * scale, crop.height * scale))
    return crop.point(lambda value: 0 if value < 205 else 255)


def parse_score(text: str) -> tuple[int | None, str]:
    digits = re.sub(r"\D+", "", text or "")
    if not digits:
        return None, ""
    candidates = [int(match.group(0)) for match in re.finditer(r"\d{3}", digits) if 100 <= int(match.group(0)) <= 750]
    if not candidates:
        return None, digits
    score = candidates[-1]
    return score, f"{score}分"


def parse_count(text: str) -> int | None:
    tokens = re.findall(r"\d+", text or "")
    if not tokens:
        return None
    if len(tokens) > 1 and len(tokens[-1]) == 1 and len(tokens[0]) >= 3:
        return int(tokens[0])
    return int("".join(tokens))


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
    return sorted(items, key=lambda item: item["sourceUrl"])


def is_score_rank_image(province_code: str, source_url: str, raw_path: str) -> bool:
    lower = source_url.lower()
    size = Path(raw_path).stat().st_size if Path(raw_path).exists() else 0
    if province_code == "HB":
        return "/uploadfile/2025/0625/" in lower and size > 100_000
    if province_code == "AH":
        return "/kszx/gk/gzdt/202506/w020250625" in lower and size > 100_000
    return False


def infer_subject(province_code: str, item: dict[str, Any], image_index: int) -> str:
    subject = item.get("subjectType") or ""
    if subject in {"物理类", "历史类"}:
        return subject
    if province_code == "AH":
        return "历史类" if image_index <= 3 else "物理类"
    return ""


def row_pairs(lines: list[int], province_code: str) -> list[tuple[int, int]]:
    if len(lines) < 3:
        return []
    # First horizontal band is the title/header in both HB and AH official images.
    start_index = 1
    pairs = [(lines[i], lines[i + 1]) for i in range(start_index, len(lines) - 1)]
    if province_code == "HB":
        pairs = [pair for pair in pairs if 16 <= pair[1] - pair[0] <= 40]
    else:
        pairs = [pair for pair in pairs if 10 <= pair[1] - pair[0] <= 24]
    return pairs


def group_vertical_lines(lines: list[int], province_code: str) -> list[tuple[int, int, int, int]]:
    groups: list[tuple[int, int, int, int]] = []
    if len(lines) < 4:
        return groups

    # Hubei images render each repeated block as an independent mini table with
    # a visible gap: [0,1,2,3], [4,5,6,7], [8,9,10,11]. Anhui images share the
    # boundary line between neighboring repeated blocks: [0,1,2,3], [3,4,5,6].
    step = 4 if province_code == "HB" else 3
    for i in range(0, len(lines) - 3, step):
        x0, x1, x2, x3 = lines[i:i + 4]
        widths = [x1 - x0, x2 - x1, x3 - x2]
        if any(width < 35 or width > 160 for width in widths):
            continue
        groups.append((x0, x1, x2, x3))
    return groups


def extract_image(item: dict[str, Any], image_index: int) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    image = Image.open(item["rawPath"])
    horizontal = detect_horizontal_lines(image)
    if len(horizontal) < 3:
        return [], {"rawPath": item["rawPath"], "error": "no_horizontal_lines", "rowCount": 0}
    vertical = detect_vertical_lines(image, horizontal[0], horizontal[-1])
    groups = group_vertical_lines(vertical, item["provinceCode"])
    rows: list[dict[str, Any]] = []
    debug_samples: list[dict[str, Any]] = []
    subject = infer_subject(item["provinceCode"], item, image_index)
    for y0, y1 in row_pairs(horizontal, item["provinceCode"]):
        for group in groups:
            x0, x1, x2, x3 = group
            score_text = run_tesseract_cell(crop_cell(image, (x0, y0, x1, y1)))
            segment_text = run_tesseract_cell(crop_cell(image, (x1, y0, x2, y1)))
            cumulative_text = run_tesseract_cell(crop_cell(image, (x2, y0, x3, y1)))
            score, score_label = parse_score(score_text)
            segment = parse_count(segment_text)
            cumulative = parse_count(cumulative_text)
            if score is None or segment is None or cumulative is None:
                continue
            row = {
                "provinceCode": item["provinceCode"],
                "score": score,
                "scoreLabel": score_label,
                "segmentCount": segment,
                "cumulativeCount": cumulative,
                "subjectType": subject,
                "sourcePageUrl": item["sourcePageUrl"],
                "sourceUrl": item["sourceUrl"],
                "sourceHash": item["sourceHash"],
                "rawPath": item["rawPath"],
                "parserNotes": f"grid:{score_text}|{segment_text}|{cumulative_text}",
            }
            rows.append(row)
            if len(debug_samples) < 8:
                debug_samples.append(row)
    return rows, {
        "rawPath": item["rawPath"],
        "subjectType": subject,
        "horizontalLines": horizontal[:10] + ["..."] + horizontal[-5:],
        "verticalLines": vertical,
        "groups": groups,
        "rowCount": len(rows),
        "samples": debug_samples,
        "error": "",
    }


def merge_rows(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    by_key: dict[tuple[str, str, int], dict[str, Any]] = {}
    duplicates: list[dict[str, Any]] = []
    for row in rows:
        key = (row["provinceCode"], row["subjectType"], int(row["score"]))
        existing = by_key.get(key)
        if existing:
            # Prefer the row whose cumulative value is larger; page overlap and
            # header artifacts often create tiny cumulative false positives.
            if int(row["cumulativeCount"]) > int(existing["cumulativeCount"]):
                by_key[key] = row
            duplicates.append({"provinceCode": row["provinceCode"], "subjectType": row["subjectType"], "score": row["score"]})
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
        item = {
            "rowCount": len(ordered),
            "minScore": min(scores) if scores else None,
            "maxScore": max(scores) if scores else None,
            "missingScoreCount": len(missing_scores),
            "missingScoresSample": missing_scores[:100],
            "issueCount": len(issues),
            "issuesSample": issues[:100],
        }
        key = f"{province_code}_{subject}"
        report["subjects"][key] = item
        if item["rowCount"] < 200 or item["missingScoreCount"] or item["issueCount"]:
            report["blockingIssues"].append({"provinceCode": province_code, "subjectType": subject, **item})
    return report


def selected_provinces(value: str) -> list[str]:
    if value.upper() == "ALL":
        return ["HB", "AH"]
    return [item.strip().upper() for item in value.split(",") if item.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--province", default="HB,AH")
    args = parser.parse_args()
    all_rows: list[dict[str, Any]] = []
    extraction_log: list[dict[str, Any]] = []
    for province_code in selected_provinces(args.province):
        items = load_score_images(province_code)
        for image_index, item in enumerate(items, 1):
            rows, log_item = extract_image(item, image_index)
            all_rows.extend(rows)
            extraction_log.append(log_item)
    merged, duplicates = merge_rows(all_rows)
    write_csv(DRAFT_DIR / "score_rank_grid_draft.csv", merged)
    report = validate_rows(merged)
    report["imageCount"] = len(extraction_log)
    report["rawParsedRowCount"] = len(all_rows)
    report["mergedRowCount"] = len(merged)
    report["duplicateCount"] = len(duplicates)
    report["duplicatesSample"] = duplicates[:100]
    report["extractionLog"] = extraction_log
    write_json(REPORTS_DIR / "score_rank_grid_validation.json", report)
    print(f"images={len(extraction_log)} raw_rows={len(all_rows)} merged_rows={len(merged)} blocking_issues={len(report['blockingIssues'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
