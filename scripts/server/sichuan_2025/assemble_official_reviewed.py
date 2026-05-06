#!/usr/bin/env python3
"""合并已确认的高校官方抽取结果到 reviewed CSV。"""

from pathlib import Path

from common import DRAFT_DIR, GROUP_LINE_HEADERS, GROUP_PLAN_HEADERS, REVIEWED_DIR, SOURCE_META_HEADERS, ensure_dirs, read_csv, write_csv


OFFICIAL_DRAFTS = [
    ("sicau_group_lines_sc_2025_reviewed_draft.csv", "sicau_group_plans_sc_2025_reviewed_draft.csv"),
    ("cuit_group_lines_sc_2025_reviewed_draft.csv", "cuit_group_plans_sc_2025_reviewed_draft.csv"),
]


def read_non_empty(path: Path) -> list[dict[str, str]]:
    return [row for row in read_csv(path) if any((value or "").strip() for value in row.values())]


def main() -> int:
    ensure_dirs()
    line_rows: list[dict[str, str]] = []
    plan_rows: list[dict[str, str]] = []
    for line_name, plan_name in OFFICIAL_DRAFTS:
        line_path = DRAFT_DIR / line_name
        plan_path = DRAFT_DIR / plan_name
        if line_path.exists():
            line_rows.extend(read_non_empty(line_path))
        if plan_path.exists():
            plan_rows.extend(read_non_empty(plan_path))
    write_csv(REVIEWED_DIR / "group_lines_sc_2025_reviewed.csv", GROUP_LINE_HEADERS + SOURCE_META_HEADERS, line_rows)
    write_csv(REVIEWED_DIR / "group_plans_sc_2025_reviewed.csv", GROUP_PLAN_HEADERS + SOURCE_META_HEADERS, plan_rows)
    print(f"assembled group_lines={len(line_rows)} group_plans={len(plan_rows)}")
    print(f"wrote {REVIEWED_DIR / 'group_lines_sc_2025_reviewed.csv'}")
    print(f"wrote {REVIEWED_DIR / 'group_plans_sc_2025_reviewed.csv'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
