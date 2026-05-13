#!/usr/bin/env python3
"""Generate a Markdown quality report from exported 2026 staging gate CSV files.

This helper does not connect to production and does not write any database table.
Expected input directory may contain CSV files exported from 10_quality_gate_templates.sql.
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Iterable


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        return list(csv.DictReader(fh))


def markdown_table(rows: list[dict[str, str]]) -> str:
    if not rows:
        return "_No rows._\n"
    headers = list(rows[0].keys())
    out = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    for row in rows:
        out.append("| " + " | ".join(str(row.get(h, "")).replace("\n", " ") for h in headers) + " |")
    return "\n".join(out) + "\n"


def iter_csv_files(input_dir: Path) -> Iterable[Path]:
    return sorted(p for p in input_dir.glob("*.csv") if p.is_file())


def main() -> int:
    parser = argparse.ArgumentParser(description="Build GZLY 2026 official import quality_report.md from CSV evidence.")
    parser.add_argument("--input-dir", required=True, help="Directory containing exported quality gate CSV files")
    parser.add_argument("--output", required=True, help="Markdown report path")
    parser.add_argument("--import-batch-id", required=True)
    parser.add_argument("--data-type", required=True)
    parser.add_argument("--source-manifest", default="", help="Optional source manifest path or URL")
    args = parser.parse_args()

    input_dir = Path(args.input_dir)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    lines: list[str] = [
        f"# GZLY 2026 {args.data_type} Quality Report",
        "",
        f"- **import_batch_id**: `{args.import_batch_id}`",
        f"- **source_manifest**: `{args.source_manifest or 'TODO'}`",
        "- **boundary**: staging quality report only; no formal table write; no readiness switch; no model activation.",
        "",
        "## Evidence Tables",
        "",
    ]

    files = list(iter_csv_files(input_dir))
    if not files:
        lines.extend(["_No CSV evidence files found._", ""])
    for path in files:
        rows = read_csv(path)
        lines.extend([f"### {path.name}", "", markdown_table(rows), ""])

    lines.extend([
        "## Manual Review Checklist",
        "",
        "- [ ] 官方来源 URL 可打开且与 source_file 对应。",
        "- [ ] raw_text 能追溯到官方原文。",
        "- [ ] 不含 fake/mock/2025 冒充 2026 数据。",
        "- [ ] 所有 failed_rows 为 0，或已列出人工复核结论。",
        "- [ ] rollback SQL 已生成并按 import_batch_id 限定。",
        "- [ ] 未执行正式表写入。",
        "",
    ])

    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
