#!/usr/bin/env python3
"""Build admin dry-run payloads from manually reviewed Sichuan CSVs."""

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from common import (
    GROUP_LINE_HEADERS,
    GROUP_PLAN_HEADERS,
    PAYLOADS_DIR,
    REPORTS_DIR,
    REVIEWED_DIR,
    SCORE_RANK_HEADERS,
    ensure_dirs,
    read_csv,
    sha256_text,
)


ADMIN_BASE = "http://127.0.0.1:8090/api/admin/sichuan-data"
DEFAULT_GROUP_SOURCE_LEVEL = "school_verified"
ALLOWED_GROUP_SOURCE_LEVELS = {"manual_verified", "school_verified"}
SCORE_SOURCE_PAGE = {
    "历史类": "https://www.sceea.cn/Html/202506/Newsdetail_4334.html",
    "物理类": "https://www.sceea.cn/Html/202506/Newsdetail_4335.html",
}


def csv_text(headers: list[str], rows: list[dict[str, Any]]) -> str:
    lines = [",".join(headers)]
    for row in rows:
        values = []
        for header in headers:
            value = str(row.get(header, "") or "")
            if any(ch in value for ch in [",", '"', "\n", "\r"]):
                value = '"' + value.replace('"', '""') + '"'
            values.append(value)
        lines.append(",".join(values))
    return "\n".join(lines) + "\n"


def non_empty_rows(path: Path) -> list[dict[str, str]]:
    return [row for row in read_csv(path) if any((value or "").strip() for value in row.values())]


def normalize_source_level(value: str, default: str) -> str:
    source_level = (value or "").strip() or default
    if source_level not in ALLOWED_GROUP_SOURCE_LEVELS:
        raise ValueError(f"sourceLevel 只能是 manual_verified 或 school_verified: {source_level}")
    return source_level


def is_official_source(url: str) -> bool:
    host = urlparse(url).hostname or ""
    host = host.lower()
    return host == "sceea.cn" or host.endswith(".sceea.cn")


def group_by_source(
    rows: list[dict[str, str]],
    fallback_page: str = "",
    fallback_url: str = "",
    fallback_source_level: str = "",
) -> dict[tuple[str, str, str, str], list[dict[str, str]]]:
    grouped: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        source_page = (row.get("sourcePageUrl") or fallback_page).strip()
        source_url = (row.get("sourceUrl") or fallback_url or source_page).strip()
        source_hash = (row.get("sourceHash") or "").strip()
        default_source_level = "manual_verified" if is_official_source(source_page) else fallback_source_level
        source_level = (row.get("sourceLevel") or default_source_level).strip()
        if not source_page:
            raise ValueError("reviewed CSV 每行必须填写 sourcePageUrl")
        if not source_url:
            raise ValueError("reviewed CSV 每行必须填写 sourceUrl")
        grouped[(source_page, source_url, source_hash, source_level)].append(row)
    return grouped


def write_payload(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def build_score_rank_payloads() -> list[dict[str, str]]:
    written: list[dict[str, str]] = []
    for subject, source_page in SCORE_SOURCE_PAGE.items():
        csv_path = REVIEWED_DIR / f"score_rank_sc_2025_{subject}_reviewed.csv"
        rows = non_empty_rows(csv_path)
        if not rows:
            continue
        grouped = group_by_source(rows, fallback_page=source_page)
        for idx, ((source_page_url, source_url, source_hash, _source_level), group_rows) in enumerate(grouped.items(), 1):
            text = csv_text(SCORE_RANK_HEADERS, group_rows)
            payload = {
                "year": 2025,
                "subjectType": subject,
                "sourcePageUrl": source_page_url,
                "sourceUrl": source_url,
                "sourceFile": csv_path.name,
                "sourceHash": source_hash or sha256_text(text),
                "parseMethod": "manual_verified_csv",
                "preserveNonEmpty": True,
                "csvText": text,
            }
            out = PAYLOADS_DIR / f"score-rank-{subject}-{idx:02d}.json"
            write_payload(out, payload)
            written.append({"kind": "score_rank", "path": str(out), "endpoint": "/score-rank/import"})
    return written


def build_group_payloads(kind: str, csv_name: str, headers: list[str], endpoint: str) -> list[dict[str, str]]:
    csv_path = REVIEWED_DIR / csv_name
    rows = non_empty_rows(csv_path)
    if not rows:
        return []
    written: list[dict[str, str]] = []
    grouped = group_by_source(rows, fallback_source_level=DEFAULT_GROUP_SOURCE_LEVEL)
    for idx, ((source_page_url, source_url, source_hash, source_level), group_rows) in enumerate(grouped.items(), 1):
        source_level = normalize_source_level(source_level, DEFAULT_GROUP_SOURCE_LEVEL)
        text = csv_text(headers, group_rows)
        payload = {
            "year": 2025,
            "sourcePageUrl": source_page_url,
            "sourceUrl": source_url,
            "sourceHash": source_hash or sha256_text(text),
            "sourceLevel": source_level,
            "parseMethod": "manual_verified_csv",
            "preserveNonEmpty": True,
            "csvText": text,
        }
        out = PAYLOADS_DIR / f"{kind}-{idx:03d}.json"
        write_payload(out, payload)
        written.append({"kind": kind, "path": str(out), "endpoint": endpoint})
    return written


def write_curl_script(items: list[dict[str, str]], dry_run: bool) -> None:
    script = REPORTS_DIR / ("admin_dry_run_curl.sh" if dry_run else "admin_import_curl.sh")
    dry_run_value = "true" if dry_run else "false"
    lines = [
        "#!/usr/bin/env bash",
        "set -Eeuo pipefail",
        ': "${ADMIN_TOKEN:?Set ADMIN_TOKEN to a valid admin JWT before running}"',
        f'ADMIN_BASE="${{ADMIN_BASE:-{ADMIN_BASE}}}"',
        "",
    ]
    if not dry_run:
        lines.extend([
            ': "${CONFIRM_SICHUAN_IMPORT:?Set CONFIRM_SICHUAN_IMPORT=SC_2025_REVIEWED after dry-run rejected=0}"',
            'if [[ "$CONFIRM_SICHUAN_IMPORT" != "SC_2025_REVIEWED" ]]; then',
            '  echo "Refuse import: CONFIRM_SICHUAN_IMPORT must be SC_2025_REVIEWED" >&2',
            "  exit 1",
            "fi",
            "",
        ])
    for item in items:
        lines.extend([
            f"echo '{'dry-run' if dry_run else 'import'} {item['kind']} {Path(item['path']).name}'",
            "curl -sS -X POST "
            f"\"$ADMIN_BASE{item['endpoint']}?dryRun={dry_run_value}\" "
            "-H \"Authorization: Bearer $ADMIN_TOKEN\" "
            "-H 'Content-Type: application/json' "
            f"--data-binary @{item['path']} | python3 -m json.tool",
            "",
        ])
    script.write_text("\n".join(lines), encoding="utf-8")
    script.chmod(0o700)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build admin import dry-run payloads from reviewed CSVs.")
    parser.add_argument("--write-curl-script", action="store_true")
    args = parser.parse_args()
    ensure_dirs()

    items: list[dict[str, str]] = []
    items.extend(build_score_rank_payloads())
    items.extend(build_group_payloads("group-lines", "group_lines_sc_2025_reviewed.csv", GROUP_LINE_HEADERS, "/group-lines/import"))
    items.extend(build_group_payloads("group-plans", "group_plans_sc_2025_reviewed.csv", GROUP_PLAN_HEADERS, "/group-plans/import"))

    manifest = {
        "count": len(items),
        "items": items,
        "note": "Payloads are for dry-run first. Do not import until dry-run rejected=0, sources are manually verified, and CONFIRM_SICHUAN_IMPORT is set.",
    }
    manifest_path = PAYLOADS_DIR / "manifest.json"
    write_payload(manifest_path, manifest)
    if args.write_curl_script:
        write_curl_script(items, dry_run=True)
        write_curl_script(items, dry_run=False)
    print(f"wrote {manifest_path} count={len(items)}")
    if args.write_curl_script:
        print(f"wrote {REPORTS_DIR / 'admin_dry_run_curl.sh'}")
        print(f"wrote {REPORTS_DIR / 'admin_import_curl.sh'}")


if __name__ == "__main__":
    main()
