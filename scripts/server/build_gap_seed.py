#!/usr/bin/env python3
"""Build crawler seed files from production data gaps.

This script is intended to run on the scraper host. It reads the MySQL database
in read-only mode, finds universities whose public admission metadata is still
incomplete, and writes a seed JSON file accepted by ``scrape_official_links.py``.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


DEFAULT_QUERY_BY_SCOPE = {
    "official-missing": """
        SELECT
            u.school_id,
            u.name,
            COALESCE(u.school_site, l.school_site, '') AS school_site,
            COALESCE(u.province, '') AS province,
            COALESCE(u.city, '') AS city
        FROM sys_university u
        JOIN uni_official_link l ON l.school_id = u.school_id
        WHERE l.parse_status <> 1
           OR l.admission_site IS NULL OR l.admission_site = ''
           OR l.admission_brochure_url IS NULL OR l.admission_brochure_url = ''
           OR l.major_catalog_url IS NULL OR l.major_catalog_url = ''
           OR l.tuition_info_url IS NULL OR l.tuition_info_url = ''
        ORDER BY u.province, u.name
    """,
    "guizhou": """
        SELECT
            u.school_id,
            u.name,
            COALESCE(u.school_site, l.school_site, '') AS school_site,
            COALESCE(u.province, '') AS province,
            COALESCE(u.city, '') AS city
        FROM sys_university u
        JOIN uni_official_link l ON l.school_id = u.school_id
        WHERE u.province = '贵州'
        ORDER BY u.name
    """,
    "no-score": """
        SELECT
            u.school_id,
            u.name,
            COALESCE(u.school_site, l.school_site, '') AS school_site,
            COALESCE(u.province, '') AS province,
            COALESCE(u.city, '') AS city
        FROM sys_university u
        LEFT JOIN uni_official_link l ON l.school_id = u.school_id
        LEFT JOIN (SELECT DISTINCT school_id FROM data_score_line_gz) s
            ON s.school_id = u.school_id
        LEFT JOIN (SELECT DISTINCT school_id FROM data_major_score_gz) m
            ON m.school_id = u.school_id
        WHERE s.school_id IS NULL OR m.school_id IS NULL
        ORDER BY u.province, u.name
    """,
}


@dataclass(frozen=True)
class DbConfig:
    user: str
    password: str
    name: str
    host: str
    port: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build GZLY gap crawler seed JSON.")
    parser.add_argument(
        "--scope",
        choices=sorted(DEFAULT_QUERY_BY_SCOPE),
        default="official-missing",
        help="Which data gap to export as crawler seeds.",
    )
    parser.add_argument("--output", type=Path, required=True, help="Output seed JSON path.")
    parser.add_argument("--limit", type=int, default=0, help="Limit number of schools.")
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--db-pass", default=os.getenv("DB_PASS", ""))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "gzly"))
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--dry-run", action="store_true", help="Print summary without writing file.")
    return parser.parse_args()


def run_mysql_query(config: DbConfig, query: str) -> list[dict[str, str]]:
    env = os.environ.copy()
    env["MYSQL_PWD"] = config.password
    command = [
        "mysql",
        "-N",
        "-B",
        "-u",
        config.user,
        "-h",
        config.host,
        "-P",
        str(config.port),
        config.name,
        "-e",
        query,
    ]
    completed = subprocess.run(
        command,
        env=env,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=60,
    )
    reader = csv.reader(completed.stdout.splitlines(), delimiter="\t")
    rows: list[dict[str, str]] = []
    for row in reader:
        if len(row) < 5:
            continue
        school_id, name, school_site, province, city = row[:5]
        rows.append(
            {
                "school_id": school_id,
                "name": name,
                "school_site": school_site,
                "province": province,
                "city": city,
            }
        )
    return rows


def main() -> int:
    args = parse_args()
    if not args.db_pass:
        print("ERROR: DB password is required via --db-pass or DB_PASS.", file=sys.stderr)
        return 2

    query = DEFAULT_QUERY_BY_SCOPE[args.scope]
    if args.limit > 0:
        query = f"{query.rstrip().rstrip(';')} LIMIT {args.limit}"

    config = DbConfig(
        user=args.db_user,
        password=args.db_pass,
        name=args.db_name,
        host=args.db_host,
        port=args.db_port,
    )
    rows = run_mysql_query(config, query)

    print(f"scope={args.scope} rows={len(rows)} output={args.output}")
    if rows[:5]:
        for row in rows[:5]:
            print(f"  - {row['school_id']} {row['name']} {row['province']} {row['city']}")

    if args.dry_run:
        return 0

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as file:
        json.dump(rows, file, ensure_ascii=False, indent=2)
        file.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
