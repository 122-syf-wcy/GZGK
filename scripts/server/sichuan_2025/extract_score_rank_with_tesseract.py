#!/usr/bin/env python3
"""Extract Sichuan 2025 score-rank draft rows with local Tesseract OCR.

The output is still a draft. It must be reviewed before admin import.
"""

import argparse
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path
from typing import Any

from common import DRAFT_DIR, REPORTS_DIR, ensure_dirs, read_jsonl, sha256_file, write_csv, write_json


DRAFT_HEADERS = [
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


def load_official_score_images(source_audit: Path) -> list[dict[str, Any]]:
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
                    "subjectType": child.get("subjectType") or record.get("subjectType"),
                    "sourcePageUrl": record.get("url", ""),
                    "sourceUrl": source_url,
                    "sourceHash": child.get("sourceHash") or "",
                })
    return sorted(items, key=lambda item: (item.get("subjectType", ""), item.get("sourceUrl", "")))


def run_tesseract(path: Path, lang: str, psm: int, timeout: int) -> str:
    command = ["tesseract", str(path), "stdout", "-l", lang, "--psm", str(psm)]
    completed = subprocess.run(command, check=True, text=True, capture_output=True, timeout=timeout)
    return completed.stdout


def parse_score_line(line: str) -> dict[str, Any] | None:
    numbers = re.findall(r"\d+", line)
    if len(numbers) < 2:
        return None
    first = numbers[0]
    candidate_score: int | None = None
    if len(first) >= 3:
        candidate_score = int(first[:3])
        if candidate_score < 100 or candidate_score > 750:
            candidate_score = None
    cumulative = int(numbers[-1])
    if cumulative <= 0:
        return None
    segment = int(numbers[-2]) if len(numbers) >= 3 else None
    return {
        "candidateScore": candidate_score,
        "segmentCount": segment,
        "cumulativeCount": cumulative,
        "parserNotes": line.strip(),
    }


def parse_text(text: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen_scores: set[int] = set()
    for line in text.splitlines():
        row = parse_score_line(line)
        if not row:
            continue
        candidate = row.get("candidateScore")
        if not isinstance(candidate, int):
            rows.append(row)
            continue
        if candidate in seen_scores:
            continue
        seen_scores.add(candidate)
        rows.append(row)
    return rows


def next_candidate(rows: list[dict[str, Any]], start: int) -> int | None:
    for row in rows[start + 1:]:
        candidate = row.get("candidateScore")
        if isinstance(candidate, int):
            return candidate
    return None


def assign_sequence_scores(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    assigned: list[dict[str, Any]] = []
    previous_score: int | None = None
    for idx, row in enumerate(rows):
        candidate = row.get("candidateScore")
        expected = previous_score - 1 if previous_score is not None else None
        if expected is None:
            score = candidate if isinstance(candidate, int) else None
        elif not isinstance(candidate, int):
            score = expected
        elif candidate == expected:
            score = candidate
        elif candidate == expected - 1:
            score = candidate
        else:
            lookahead = next_candidate(rows, idx)
            if lookahead == expected - 1:
                score = expected
            elif candidate > expected and (lookahead == expected - 1 or lookahead is None):
                score = expected
            elif candidate < expected - 1 and candidate >= expected - 12 and lookahead == expected - 1:
                score = expected
            else:
                score = candidate
        if not isinstance(score, int) or score < 100 or score > 750:
            continue
        next_row = dict(row)
        next_row["score"] = score
        next_row["scoreLabel"] = f"{score}分"
        next_row.pop("candidateScore", None)
        assigned.append(next_row)
        previous_score = score
    return assigned


def normalize_current_row(row: dict[str, Any], previous_cumulative: int | None) -> dict[str, Any]:
    next_row = dict(row)
    segment = next_row.get("segmentCount")
    cumulative = int(next_row["cumulativeCount"])
    notes = str(next_row.get("parserNotes", ""))
    if previous_cumulative is None:
        if segment is None:
            next_row["segmentCount"] = cumulative
            next_row["parserNotes"] = notes + ";filled_first_segment_from_cumulative"
        return next_row

    if segment is None:
        diff = cumulative - previous_cumulative
        if diff >= 0:
            next_row["segmentCount"] = diff
            next_row["parserNotes"] = notes + ";filled_segment_from_cumulative"
        return next_row

    segment = int(segment)
    diff = cumulative - previous_cumulative
    if cumulative <= previous_cumulative:
        next_row["cumulativeCount"] = previous_cumulative + segment
        next_row["parserNotes"] = notes + ";corrected_cumulative_from_segment"
    elif diff != segment:
        if diff > 10_000 and segment < 5_000:
            next_row["cumulativeCount"] = previous_cumulative + segment
            next_row["parserNotes"] = notes + ";corrected_cumulative_from_segment_large_gap"
        else:
            next_row["segmentCount"] = diff
            next_row["parserNotes"] = notes + ";corrected_segment_from_cumulative"
    return next_row


def fill_missing_between(previous: dict[str, Any], current: dict[str, Any]) -> list[dict[str, Any]]:
    previous_score = int(previous["score"])
    current_score = int(current["score"])
    gap = previous_score - current_score
    if gap <= 1:
        return []
    missing_scores = list(range(previous_score - 1, current_score, -1))
    previous_cumulative = int(previous["cumulativeCount"])
    current_cumulative = int(current["cumulativeCount"])
    current_segment = int(current["segmentCount"]) if current.get("segmentCount") is not None else 0
    total_missing_segment = current_cumulative - current_segment - previous_cumulative
    if total_missing_segment < 0:
        return []
    if len(missing_scores) == 1:
        if total_missing_segment == 0:
            return []
        segments = [total_missing_segment]
    else:
        return []

    filled: list[dict[str, Any]] = []
    cumulative = previous_cumulative
    for score, segment in zip(missing_scores, segments):
        cumulative += segment
        filled.append({
            "score": score,
            "scoreLabel": f"{score}分",
            "segmentCount": segment,
            "cumulativeCount": cumulative,
            "subjectType": previous["subjectType"],
            "sourcePageUrl": previous["sourcePageUrl"],
            "sourceUrl": previous["sourceUrl"],
            "sourceHash": previous["sourceHash"],
            "rawPath": previous["rawPath"],
            "parserNotes": f"filled_missing_score_from_neighbor_cumulative:{score}",
        })
    return filled


def repair_subject_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    ordered = sorted(rows, key=lambda row: int(row["score"]), reverse=True)
    repaired: list[dict[str, Any]] = []
    previous: dict[str, Any] | None = None
    for row in ordered:
        if previous:
            for missing in fill_missing_between(previous, row):
                repaired.append(missing)
                previous = missing
        normalized = normalize_current_row(row, int(previous["cumulativeCount"]) if previous else None)
        repaired.append(normalized)
        previous = normalized
    return repaired


def merge_rows(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    by_subject: dict[str, dict[int, dict[str, Any]]] = defaultdict(dict)
    duplicates: list[dict[str, Any]] = []
    for row in rows:
        subject = row["subjectType"]
        score = int(row["score"])
        existing = by_subject[subject].get(score)
        if existing:
            duplicates.append({"subjectType": subject, "score": score, "firstRawPath": existing["rawPath"], "secondRawPath": row["rawPath"]})
            continue
        by_subject[subject][score] = row
    merged: list[dict[str, Any]] = []
    for subject, subject_rows in by_subject.items():
        merged.extend(subject_rows[score] for score in sorted(subject_rows.keys(), reverse=True))
    return merged, duplicates


def validate_rows(rows: list[dict[str, Any]]) -> dict[str, Any]:
    by_subject: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_subject[row["subjectType"]].append(row)
    report: dict[str, Any] = {"subjects": {}, "blockingIssues": []}
    for subject, subject_rows in by_subject.items():
        ordered = sorted(subject_rows, key=lambda row: int(row["score"]), reverse=True)
        previous_score = 751
        previous_cumulative = 0
        issues: list[dict[str, Any]] = []
        scores = [int(row["score"]) for row in ordered]
        omitted_zero_scores: list[int] = []
        if scores:
            score_set = set(scores)
            missing_scores_all = [score for score in range(max(scores), min(scores) - 1, -1) if score not in score_set]
            by_score = {int(row["score"]): row for row in ordered}
            for missing_score in missing_scores_all:
                previous = by_score.get(missing_score + 1)
                current = by_score.get(missing_score - 1)
                if previous and current:
                    previous_row_cumulative = int(previous["cumulativeCount"])
                    current_cumulative = int(current["cumulativeCount"])
                    current_segment = int(current["segmentCount"])
                    if current_cumulative - current_segment - previous_row_cumulative == 0:
                        omitted_zero_scores.append(missing_score)
            missing_scores = [score for score in missing_scores_all if score not in set(omitted_zero_scores)]
        else:
            missing_scores = []
            omitted_zero_scores = []
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
                issues.append({
                    "score": score,
                    "issue": "segment_mismatch",
                    "value": segment,
                    "expected": expected_segment,
                    "raw": row.get("parserNotes", ""),
                })
            previous_score = score
            previous_cumulative = cumulative
        subject_report = {
            "rowCount": len(ordered),
            "minScore": min(scores) if scores else None,
            "maxScore": max(scores) if scores else None,
            "missingScoreCount": len(missing_scores),
            "missingScoresSample": missing_scores[:50],
            "omittedZeroScoreCount": len(omitted_zero_scores),
            "omittedZeroScoresSample": omitted_zero_scores[:50],
            "issueCount": len(issues),
            "issuesSample": issues[:80],
        }
        report["subjects"][subject] = subject_report
        if subject_report["missingScoreCount"] or subject_report["issueCount"]:
            report["blockingIssues"].append({"subjectType": subject, **subject_report})
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-audit", default=str(REPORTS_DIR / "source_audit.json"))
    parser.add_argument("--lang", default="chi_sim+eng")
    parser.add_argument("--psm", type=int, default=6)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    ensure_dirs()
    text_dir = DRAFT_DIR / "tesseract_text"
    text_dir.mkdir(parents=True, exist_ok=True)
    items = load_official_score_images(Path(args.source_audit))
    if args.limit > 0:
        items = items[: args.limit]
    if not items:
        raise SystemExit("No official score-rank images found. Run official crawler first.")

    entries_by_subject: dict[str, list[dict[str, Any]]] = defaultdict(list)
    extraction_log: list[dict[str, Any]] = []
    for idx, item in enumerate(items, 1):
        path = Path(item["rawPath"])
        print(f"[{idx}/{len(items)}] tesseract {path.name}")
        try:
            text = run_tesseract(path, args.lang, args.psm, args.timeout)
            text_path = text_dir / f"{path.stem}.txt"
            text_path.write_text(text, encoding="utf-8")
            parsed_rows = parse_text(text)
            for row in parsed_rows:
                row.update({
                    "subjectType": item["subjectType"],
                    "sourcePageUrl": item["sourcePageUrl"],
                    "sourceUrl": item["sourceUrl"],
                    "sourceHash": item["sourceHash"] or sha256_file(path),
                    "rawPath": str(path),
                })
            entries_by_subject[item["subjectType"]].extend(parsed_rows)
            extraction_log.append({
                "rawPath": str(path),
                "subjectType": item["subjectType"],
                "sourceUrl": item["sourceUrl"],
                "textPath": str(text_path),
                "parsedRowCount": len(parsed_rows),
                "error": "",
            })
        except Exception as exc:
            extraction_log.append({
                "rawPath": str(path),
                "subjectType": item["subjectType"],
                "sourceUrl": item["sourceUrl"],
                "textPath": "",
                "parsedRowCount": 0,
                "error": str(exc),
            })

    all_rows: list[dict[str, Any]] = []
    for entries in entries_by_subject.values():
        assigned_rows = assign_sequence_scores(entries)
        all_rows.extend(repair_subject_rows(assigned_rows))

    merged_rows, duplicates = merge_rows(all_rows)
    merged_rows.sort(key=lambda row: (row["subjectType"], -int(row["score"])))
    write_csv(DRAFT_DIR / "score_rank_tesseract_draft.csv", DRAFT_HEADERS, merged_rows)
    # Keep the generic draft path compatible with normalize_sichuan_data.py.
    write_csv(DRAFT_DIR / "score_rank_ocr_draft.csv", DRAFT_HEADERS, merged_rows)
    report = validate_rows(merged_rows)
    report["imageCount"] = len(items)
    report["rawParsedRowCount"] = len(all_rows)
    report["mergedRowCount"] = len(merged_rows)
    report["duplicateCount"] = len(duplicates)
    report["duplicatesSample"] = duplicates[:80]
    report["extractionLog"] = extraction_log
    write_json(REPORTS_DIR / "score_rank_tesseract_validation.json", report)
    print(f"images={len(items)} raw_rows={len(all_rows)} merged_rows={len(merged_rows)} duplicates={len(duplicates)}")
    print(f"blocking_issues={len(report['blockingIssues'])}")
    print(f"wrote {DRAFT_DIR / 'score_rank_tesseract_draft.csv'}")
    print(f"wrote {REPORTS_DIR / 'score_rank_tesseract_validation.json'}")


if __name__ == "__main__":
    main()
