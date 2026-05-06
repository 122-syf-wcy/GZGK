#!/usr/bin/env python3
"""Build review queues from score-rank OCR drafts.

This script does not create production-ready reviewed CSVs. It separates rows
that are part of internally consistent contiguous OCR segments from noisy rows,
so manual review can focus on the gaps and suspicious cells.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
DRAFT_DIR = ROOT / "draft"
REPORTS_DIR = ROOT / "reports"

HEADERS = [
    "provinceCode",
    "subjectType",
    "score",
    "scoreLabel",
    "segmentCount",
    "cumulativeCount",
    "reviewClass",
    "chunkId",
    "chunkLength",
    "sourcePageUrl",
    "sourceUrl",
    "sourceHash",
    "rawPath",
    "parserNotes",
]


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


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


def row_values(row: dict[str, str]) -> tuple[int, int, int] | None:
    try:
        return int(row["score"]), int(row["segmentCount"]), int(row["cumulativeCount"])
    except (KeyError, TypeError, ValueError):
        return None


def candidates_by_score(rows: list[dict[str, str]], score_low: int, score_high: int) -> dict[int, list[dict[str, str]]]:
    result: dict[int, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        values = row_values(row)
        if not values:
            continue
        score, segment, cumulative = values
        if score_low <= score <= score_high and 0 <= segment <= 20_000 and 0 < cumulative <= 400_000:
            result[score].append(row)
    return result


def build_chunks(candidates: dict[int, list[dict[str, str]]]) -> list[list[dict[str, str]]]:
    states: dict[tuple[int, int], int] = {}
    parent: dict[tuple[int, int], tuple[int, int] | None] = {}
    for score in sorted(candidates.keys(), reverse=True):
        for idx, row in enumerate(candidates[score]):
            state = (score, idx)
            states[state] = 1
            parent[state] = None
            values = row_values(row)
            if not values:
                continue
            _, segment, cumulative = values
            for prev_idx, prev_row in enumerate(candidates.get(score + 1, [])):
                prev_state = (score + 1, prev_idx)
                prev_values = row_values(prev_row)
                if prev_state not in states or not prev_values:
                    continue
                _, _, prev_cumulative = prev_values
                if cumulative > prev_cumulative and cumulative - prev_cumulative == segment:
                    length = states[prev_state] + 1
                    if length > states[state]:
                        states[state] = length
                        parent[state] = prev_state

    used: set[tuple[int, int]] = set()
    chunks: list[list[dict[str, str]]] = []
    for state in sorted(states, key=lambda item: states[item], reverse=True):
        if state in used or states[state] < 8:
            continue
        chain_states: list[tuple[int, int]] = []
        current: tuple[int, int] | None = state
        while current and current not in used:
            chain_states.append(current)
            current = parent[current]
        if len(chain_states) < 8:
            continue
        for chain_state in chain_states:
            used.add(chain_state)
        chunk = [candidates[score][idx] for score, idx in reversed(chain_states)]
        chunks.append(chunk)
    return chunks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", default=str(DRAFT_DIR / "score_rank_grid_draft.csv"))
    args = parser.parse_args()
    rows = read_rows(Path(args.input))
    grouped: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[(row.get("provinceCode", ""), row.get("subjectType", ""))].append(row)

    review_rows: list[dict[str, Any]] = []
    summary: dict[str, Any] = {"groups": {}}
    for (province_code, subject_type), group_rows in grouped.items():
        values = [row_values(row) for row in group_rows]
        scores = [value[0] for value in values if value]
        if not scores:
            continue
        score_low = 150
        score_high = 691 if (province_code, subject_type) == ("HB", "物理类") else max(scores)
        if (province_code, subject_type) == ("HB", "历史类"):
            score_high = 673
        candidates = candidates_by_score(group_rows, score_low, score_high)
        chunks = build_chunks(candidates)
        chunk_score_set: set[int] = set()
        for idx, chunk in enumerate(chunks, 1):
            for row in chunk:
                score, _, _ = row_values(row) or (0, 0, 0)
                chunk_score_set.add(score)
                next_row = dict(row)
                next_row["reviewClass"] = "consistent_segment"
                next_row["chunkId"] = f"{province_code}_{subject_type}_{idx}"
                next_row["chunkLength"] = len(chunk)
                review_rows.append(next_row)
        for score, score_rows in candidates.items():
            if score in chunk_score_set:
                continue
            for row in score_rows:
                next_row = dict(row)
                next_row["reviewClass"] = "needs_manual_cell_check"
                next_row["chunkId"] = ""
                next_row["chunkLength"] = ""
                review_rows.append(next_row)
        expected_scores = set(range(score_high, score_low - 1, -1))
        summary_key = f"{province_code}_{subject_type}"
        summary["groups"][summary_key] = {
            "rawRows": len(group_rows),
            "candidateScores": len(candidates),
            "expectedScoreCount": len(expected_scores),
            "consistentChunkCount": len(chunks),
            "consistentChunkLengths": [len(chunk) for chunk in chunks],
            "consistentScoreCount": len(chunk_score_set),
            "missingScoresSample": sorted(expected_scores - set(candidates.keys()), reverse=True)[:120],
            "manualCellScoreCount": len(set(candidates.keys()) - chunk_score_set),
        }

    review_rows.sort(key=lambda row: (
        row.get("provinceCode", ""),
        row.get("subjectType", ""),
        row.get("reviewClass", ""),
        -(int(row.get("chunkLength") or 0)),
        -int(row.get("score") or 0),
    ))
    write_csv(REPORTS_DIR / "score_rank_review_queue.csv", review_rows)
    write_json(REPORTS_DIR / "score_rank_review_queue_summary.json", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
