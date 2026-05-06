#!/usr/bin/env python3
"""Normalize draft Sichuan extraction files into reviewable CSVs.

Default behavior writes draft/normalized CSVs and empty reviewed templates.
Use --promote-reviewed only after manual inspection of the normalized output.
"""

import argparse
import shutil
from collections import defaultdict
from pathlib import Path
from typing import Any

from common import (
    DRAFT_DIR,
    GROUP_LINE_HEADERS,
    GROUP_PLAN_HEADERS,
    REPORTS_DIR,
    REVIEWED_DIR,
    SCORE_RANK_HEADERS,
    SOURCE_META_HEADERS,
    ensure_dirs,
    int_or_blank,
    read_csv,
    read_jsonl,
    write_csv,
)


def normalize_subject(value: Any) -> str:
    text = str(value or "").strip()
    if "历史" in text:
        return "历史类"
    if "物理" in text:
        return "物理类"
    return text


def normalize_batch(value: Any) -> str:
    text = str(value or "").strip()
    return text or "普通本科批B段"


def value(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in row and row[key] not in (None, ""):
            return row[key]
    return ""


def rows_from_jsonl(path: Path, key: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for item in read_jsonl(path):
        source = item.get("sourceMeta", {}) or {}
        source_page = source.get("sourcePageUrl") or source.get("url") or ""
        source_url = source.get("sourceUrl") or source.get("finalUrl") or ""
        source_hash = source.get("sourceHash") or ""
        source_level = source.get("sourceLevel") or ""
        for raw in item.get(key, []) or []:
            if not isinstance(raw, dict):
                continue
            row = dict(raw)
            row.setdefault("subjectType", item.get("subjectType") or source.get("subjectType") or "")
            row["sourcePageUrl"] = source_page
            row["sourceUrl"] = source_url
            row["sourceHash"] = source_hash
            row["sourceLevel"] = source_level
            rows.append(row)
    return rows


def normalize_score_rank_rows(rows: list[dict[str, Any]]) -> tuple[dict[str, list[dict[str, Any]]], list[dict[str, Any]]]:
    by_subject: dict[str, list[dict[str, Any]]] = defaultdict(list)
    rejects: list[dict[str, Any]] = []
    for idx, row in enumerate(rows, 1):
        subject = normalize_subject(value(row, "subjectType", "科类"))
        score = int_or_blank(value(row, "score", "分数"))
        segment = int_or_blank(value(row, "segmentCount", "segment_count", "本段人数", "人数"))
        cumulative = int_or_blank(value(row, "cumulativeCount", "cumulative_count", "累计人数", "累计"))
        errors: list[str] = []
        if subject not in {"物理类", "历史类"}:
            errors.append("科类缺失或非法")
        if not isinstance(score, int) or score < 0 or score > 750:
            errors.append("分数非法")
        if not isinstance(cumulative, int) or cumulative <= 0:
            errors.append("累计人数非法")
        if segment != "" and (not isinstance(segment, int) or segment < 0):
            errors.append("本段人数非法")
        normalized = {
            "score": score,
            "scoreLabel": value(row, "scoreLabel", "score_label", "分数段") or str(score),
            "segmentCount": segment,
            "cumulativeCount": cumulative,
            "subjectType": subject,
            "sourcePageUrl": row.get("sourcePageUrl", ""),
            "sourceUrl": row.get("sourceUrl", ""),
            "sourceHash": row.get("sourceHash", ""),
            "sourceLevel": row.get("sourceLevel", ""),
        }
        if errors:
            rejects.append({"lineNo": idx, "errors": ";".join(errors), **normalized})
        else:
            by_subject[subject].append(normalized)

    for subject, subject_rows in by_subject.items():
        subject_rows.sort(key=lambda item: int(item["score"]), reverse=True)
        previous_score = 751
        previous_cumulative = 0
        seen_scores: set[int] = set()
        for idx, row in enumerate(subject_rows, 1):
            errors = []
            score = int(row["score"])
            cumulative = int(row["cumulativeCount"])
            segment = row["segmentCount"]
            if score in seen_scores:
                errors.append("分数重复")
            if score >= previous_score:
                errors.append("分数未按递减排列")
            if cumulative <= previous_cumulative:
                errors.append("累计人数未递增")
            if previous_cumulative and isinstance(segment, int) and segment != cumulative - previous_cumulative:
                errors.append("本段人数与累计差值不一致")
            if errors:
                rejects.append({"lineNo": idx, "errors": ";".join(errors), **row})
            seen_scores.add(score)
            previous_score = score
            previous_cumulative = cumulative
    return by_subject, rejects


def normalize_group_lines(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    normalized: list[dict[str, Any]] = []
    rejects: list[dict[str, Any]] = []
    seen: set[tuple[str, str, str, str]] = set()
    for idx, row in enumerate(rows, 1):
        item = {
            "schoolId": str(value(row, "schoolId", "school_id", "院校代码", "院校ID")).strip(),
            "universityName": str(value(row, "universityName", "university_name", "院校名称")).strip(),
            "groupCode": str(value(row, "groupCode", "group_code", "专业组代码", "院校专业组代码")).strip(),
            "groupName": str(value(row, "groupName", "group_name", "专业组名称", "院校专业组名称")).strip(),
            "subjectType": normalize_subject(value(row, "subjectType", "subject_type", "科类")),
            "firstSubjectRequirement": str(value(row, "firstSubjectRequirement", "首选科目")).strip(),
            "resubjectRequirement": str(value(row, "resubjectRequirement", "再选科目")).strip(),
            "minScore": int_or_blank(value(row, "minScore", "min_score", "最低分", "调档分", "投档分")),
            "minRank": int_or_blank(value(row, "minRank", "min_rank", "最低位次", "调档位次", "投档位次")),
            "planCount": int_or_blank(value(row, "planCount", "plan_count", "计划数")),
            "batch": normalize_batch(value(row, "batch", "批次")),
            "sourcePageUrl": row.get("sourcePageUrl", ""),
            "sourceUrl": row.get("sourceUrl", ""),
            "sourceHash": row.get("sourceHash", ""),
            "sourceLevel": row.get("sourceLevel", ""),
        }
        errors: list[str] = []
        if not item["schoolId"] and not item["universityName"]:
            errors.append("院校代码或院校名称必填")
        if not item["groupCode"]:
            errors.append("专业组代码必填")
        if item["subjectType"] not in {"物理类", "历史类"}:
            errors.append("科类缺失或非法")
        if not isinstance(item["minScore"], int):
            errors.append("最低分必填")
        if not isinstance(item["minRank"], int):
            errors.append("最低位次必填")
        dedupe_key = (item["schoolId"], item["universityName"], item["groupCode"], item["subjectType"])
        if dedupe_key in seen:
            errors.append("专业组线重复")
        seen.add(dedupe_key)
        if errors:
            rejects.append({"lineNo": idx, "errors": ";".join(errors), **item})
        else:
            normalized.append(item)
    return normalized, rejects


def normalize_group_plans(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    normalized: list[dict[str, Any]] = []
    rejects: list[dict[str, Any]] = []
    seen: set[tuple[str, str, str, str]] = set()
    for idx, row in enumerate(rows, 1):
        item = {
            "schoolId": str(value(row, "schoolId", "school_id", "院校代码", "院校ID")).strip(),
            "universityName": str(value(row, "universityName", "university_name", "院校名称")).strip(),
            "groupCode": str(value(row, "groupCode", "group_code", "专业组代码", "院校专业组代码")).strip(),
            "groupName": str(value(row, "groupName", "group_name", "专业组名称", "院校专业组名称")).strip(),
            "majorCode": str(value(row, "majorCode", "major_code", "专业代码")).strip(),
            "majorName": str(value(row, "majorName", "major_name", "专业名称")).strip(),
            "subjectType": normalize_subject(value(row, "subjectType", "subject_type", "科类")),
            "firstSubjectRequirement": str(value(row, "firstSubjectRequirement", "首选科目")).strip(),
            "resubjectRequirement": str(value(row, "resubjectRequirement", "再选科目")).strip(),
            "planCount": int_or_blank(value(row, "planCount", "plan_count", "计划数")),
            "tuition": str(value(row, "tuition", "学费")).strip(),
            "studyYears": str(value(row, "studyYears", "study_years", "学制")).strip(),
            "batch": normalize_batch(value(row, "batch", "批次")),
            "sourcePageUrl": row.get("sourcePageUrl", ""),
            "sourceUrl": row.get("sourceUrl", ""),
            "sourceHash": row.get("sourceHash", ""),
            "sourceLevel": row.get("sourceLevel", ""),
        }
        errors: list[str] = []
        if not item["schoolId"] and not item["universityName"]:
            errors.append("院校代码或院校名称必填")
        if not item["groupCode"]:
            errors.append("专业组代码必填")
        if not item["majorName"]:
            errors.append("专业名称必填")
        if item["subjectType"] not in {"物理类", "历史类"}:
            errors.append("科类缺失或非法")
        if not isinstance(item["planCount"], int):
            errors.append("计划数必填")
        dedupe_key = (item["schoolId"], item["universityName"], item["groupCode"], item["majorCode"] or item["majorName"])
        if dedupe_key in seen:
            errors.append("专业计划重复")
        seen.add(dedupe_key)
        if errors:
            rejects.append({"lineNo": idx, "errors": ";".join(errors), **item})
        else:
            normalized.append(item)
    return normalized, rejects


def ensure_review_templates() -> None:
    REVIEWED_DIR.mkdir(parents=True, exist_ok=True)
    for subject in ("物理类", "历史类"):
        target = REVIEWED_DIR / f"score_rank_sc_2025_{subject}_reviewed.csv"
        if not target.exists():
            write_csv(target, SCORE_RANK_HEADERS, [])
    for name, headers in (
        ("group_lines_sc_2025_reviewed.csv", GROUP_LINE_HEADERS + SOURCE_META_HEADERS),
        ("group_plans_sc_2025_reviewed.csv", GROUP_PLAN_HEADERS + SOURCE_META_HEADERS),
    ):
        target = REVIEWED_DIR / name
        if not target.exists():
            write_csv(target, headers, [])


def maybe_promote(src: Path, dest: Path, promote: bool) -> None:
    if promote:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest)


def main() -> None:
    parser = argparse.ArgumentParser(description="Normalize Sichuan draft extraction files.")
    parser.add_argument("--vision-jsonl", default=str(DRAFT_DIR / "vision_extractions.jsonl"))
    parser.add_argument("--score-rank-draft", default=str(DRAFT_DIR / "score_rank_ocr_draft.csv"))
    parser.add_argument("--group-line-draft", default=str(DRAFT_DIR / "group_line_extraction_draft.csv"))
    parser.add_argument("--group-plan-draft", default=str(DRAFT_DIR / "group_plan_extraction_draft.csv"))
    parser.add_argument("--promote-reviewed", action="store_true", help="copy valid normalized drafts to reviewed CSVs")
    args = parser.parse_args()
    ensure_dirs()
    ensure_review_templates()
    normalized_dir = DRAFT_DIR / "normalized"
    normalized_dir.mkdir(parents=True, exist_ok=True)

    score_rows = read_csv(Path(args.score_rank_draft))
    score_rows.extend(rows_from_jsonl(Path(args.vision_jsonl), "scoreRanks"))
    by_subject, score_rejects = normalize_score_rank_rows(score_rows)
    for subject in ("物理类", "历史类"):
        rows = by_subject.get(subject, [])
        draft_path = normalized_dir / f"score_rank_sc_2025_{subject}_draft.csv"
        write_csv(draft_path, SCORE_RANK_HEADERS + SOURCE_META_HEADERS, rows)
        reviewed_path = REVIEWED_DIR / f"score_rank_sc_2025_{subject}_reviewed.csv"
        maybe_promote(draft_path, reviewed_path, args.promote_reviewed)
    write_csv(REPORTS_DIR / "score_rank_rejects.csv", ["lineNo", "errors", "score", "scoreLabel", "segmentCount", "cumulativeCount", "subjectType", *SOURCE_META_HEADERS], score_rejects)

    group_line_rows = read_csv(Path(args.group_line_draft))
    group_line_rows.extend(rows_from_jsonl(Path(args.vision_jsonl), "groupLines"))
    group_lines, group_line_rejects = normalize_group_lines(group_line_rows)
    group_line_path = normalized_dir / "group_lines_sc_2025_draft.csv"
    write_csv(group_line_path, GROUP_LINE_HEADERS + SOURCE_META_HEADERS, group_lines)
    maybe_promote(group_line_path, REVIEWED_DIR / "group_lines_sc_2025_reviewed.csv", args.promote_reviewed)
    write_csv(REPORTS_DIR / "group_line_rejects.csv", ["lineNo", "errors", *GROUP_LINE_HEADERS, *SOURCE_META_HEADERS], group_line_rejects)

    group_plan_rows = read_csv(Path(args.group_plan_draft))
    group_plan_rows.extend(rows_from_jsonl(Path(args.vision_jsonl), "groupPlans"))
    group_plans, group_plan_rejects = normalize_group_plans(group_plan_rows)
    group_plan_path = normalized_dir / "group_plans_sc_2025_draft.csv"
    write_csv(group_plan_path, GROUP_PLAN_HEADERS + SOURCE_META_HEADERS, group_plans)
    maybe_promote(group_plan_path, REVIEWED_DIR / "group_plans_sc_2025_reviewed.csv", args.promote_reviewed)
    write_csv(REPORTS_DIR / "group_plan_rejects.csv", ["lineNo", "errors", *GROUP_PLAN_HEADERS, *SOURCE_META_HEADERS], group_plan_rejects)

    print(f"normalized score subjects={','.join(sorted(by_subject.keys())) or 'none'}")
    print(f"normalized group_lines={len(group_lines)} rejects={len(group_line_rejects)}")
    print(f"normalized group_plans={len(group_plans)} rejects={len(group_plan_rejects)}")
    print(f"wrote {normalized_dir}")
    print(f"wrote reviewed templates in {REVIEWED_DIR}")


if __name__ == "__main__":
    main()
