#!/usr/bin/env python3
"""
High-speed Real Data ETL: MySQL export dumps & official CSVs -> PostgreSQL 16 (COPY).

Reads actual datasets stored in `scripts/data/`:
  - scripts/data/export/01_universities.sql       (2,198 universities)
  - scripts/data/export/major_scores_normalized.sql (major admission score lines)
  - scripts/data/export/score_rank_gz.csv         (2,435 official score-rank segments)
  - scripts/data/major_requirements_gz.csv        (24,483 official subject requirements)
  - contracts/compliance-terms.json               (regulatory compliance lexicon)

Usage:
  # Local parse validation without connecting to DB:
  python3.12 scripts/pg_etl/import_real_data_to_pg.py --dry-run

  # Import into remote/tunneled PostgreSQL 16:
  PG_DSN="postgresql://gzgk:PASSWORD@127.0.0.1:5432/gzgk" \
    python3.12 scripts/pg_etl/import_real_data_to_pg.py
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import sys
import time
from pathlib import Path
from typing import Any, Iterator

ROOT_DIR = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT_DIR / "scripts" / "data"
EXPORT_DIR = DATA_DIR / "export"
COMPLIANCE_PATH = ROOT_DIR / "contracts" / "compliance-terms.json"


def parse_sql_tuple(tuple_body: str) -> list[Any]:
    """Parses a single SQL VALUES(...) inner string respecting MySQL string escapes."""
    fields: list[Any] = []
    n = len(tuple_body)
    i = 0
    while i < n:
        while i < n and tuple_body[i] in " \t\r\n":
            i += 1
        if i >= n:
            break
        if tuple_body[i] == "'":
            i += 1
            buf: list[str] = []
            while i < n:
                ch = tuple_body[i]
                if ch == "\\" and i + 1 < n:
                    nxt = tuple_body[i + 1]
                    if nxt == "n":
                        buf.append("\n")
                    elif nxt == "r":
                        buf.append("\r")
                    elif nxt == "t":
                        buf.append("\t")
                    else:
                        buf.append(nxt)
                    i += 2
                elif ch == "'":
                    if i + 1 < n and tuple_body[i + 1] == "'":
                        buf.append("'")
                        i += 2
                    else:
                        i += 1
                        break
                else:
                    buf.append(ch)
                    i += 1
            fields.append("".join(buf))
            while i < n and tuple_body[i] != ",":
                i += 1
            if i < n and tuple_body[i] == ",":
                i += 1
        else:
            start = i
            while i < n and tuple_body[i] != ",":
                i += 1
            raw = tuple_body[start:i].strip()
            if raw.upper() == "NULL" or raw == "":
                fields.append(None)
            else:
                fields.append(raw)
            if i < n and tuple_body[i] == ",":
                i += 1
    return fields


def iter_universities(sql_path: Path) -> Iterator[tuple[Any, ...]]:
    """Parses multiline INSERT INTO `sys_university` rows."""
    seen: set[str] = set()
    with sql_path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            stripped = line.strip()
            if not stripped.startswith("("):
                continue
            body = stripped[1:]
            if body.endswith("),") or body.endswith(");"):
                body = body[:-2]
            elif body.endswith(")"):
                body = body[:-1]
            cols = parse_sql_tuple(body)
            if len(cols) < 18:
                continue
            school_id = str(cols[0] or "").strip()
            if not school_id or school_id in seen:
                continue
            seen.add(school_id)
            tags_raw = cols[6] or "[]"
            try:
                json.loads(tags_raw)
            except Exception:
                tags_raw = "[]"
            yield (
                school_id,
                str(cols[1] or "")[:120],
                str(cols[2] or "")[:32],
                str(cols[3] or "")[:64],
                str(cols[4] or "")[:32],
                str(cols[5] or "")[:32],
                tags_raw,
                str(cols[7]) == "1",
                str(cols[8]) == "1",
                str(cols[9]) == "1",
                str(cols[10] or "")[:32],
                str(cols[11] or "")[:120],
                str(cols[12] or "")[:500],
                str(cols[13] or "")[:300],
                str(cols[14] or "")[:120],
                str(cols[15] or "")[:120],
                str(cols[16] or "")[:300],
                str(cols[17] or ""),
            )


def _to_int(val: Any) -> int | None:
    if val is None:
        return None
    s = str(val).strip()
    if not s or s.upper() == "NULL":
        return None
    try:
        return int(float(s))
    except ValueError:
        return None


def iter_major_scores(sql_path: Path, province_code: str = "GZ") -> Iterator[tuple[Any, ...]]:
    """Parses single-line INSERT IGNORE INTO `data_major_score_gz` VALUES (...)."""
    marker = "VALUES ("
    with sql_path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            idx = line.find(marker)
            if idx < 0:
                continue
            body = line[idx + len(marker) :].strip()
            if body.endswith(");"):
                body = body[:-2]
            elif body.endswith(")"):
                body = body[:-1]
            cols = parse_sql_tuple(body)
            if len(cols) < 13:
                continue
            year = _to_int(cols[4])
            if not year:
                continue
            yield (
                province_code,
                str(cols[0] or "")[:32],
                str(cols[1] or "")[:120],
                str(cols[3] or "")[:40],
                str(cols[2] or "")[:220],
                year,
                str(cols[5] or "")[:20],
                str(cols[6] or "本科批")[:60],
                str(cols[7] or "")[:100],
                _to_int(cols[8]),
                _to_int(cols[9]),
                _to_int(cols[10]),
                _to_int(cols[11]),
                _to_int(cols[12]),
                "",
            )


def iter_score_rank_csv(csv_path: Path, province_code: str = "GZ") -> Iterator[tuple[Any, ...]]:
    seen: set[tuple[str, int, str, int]] = set()
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            year = _to_int(row.get("year"))
            score = _to_int(row.get("score"))
            subject_type = (row.get("subject_type") or "").strip()
            if not year or score is None or not subject_type:
                continue
            key = (province_code, year, subject_type, score)
            if key in seen:
                continue
            seen.add(key)
            rate_raw = (row.get("cumulative_rate") or "").strip()
            rate = float(rate_raw) if rate_raw else None
            yield (
                province_code,
                (row.get("province") or "贵州").strip()[:32],
                year,
                subject_type[:20],
                score,
                (row.get("score_label") or str(score)).strip()[:32],
                _to_int(row.get("segment_count")) or 0,
                _to_int(row.get("cumulative_count")) or 0,
                rate,
                _to_int(row.get("rank_low")) or 0,
                _to_int(row.get("rank_high")) or 0,
                (row.get("source_name") or "").strip()[:120],
                (row.get("source_url") or "").strip()[:500],
            )


def iter_major_requirements_csv(csv_path: Path, province_code: str = "GZ") -> Iterator[tuple[Any, ...]]:
    seen: set[tuple[str, int, str, str, str, str]] = set()
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            year = _to_int(row.get("year"))
            school_id = (row.get("school_id") or "").strip()[:32]
            major_name = (row.get("major_name") or "").strip()[:220]
            subject_type = (row.get("subject_type") or "").strip()[:20]
            if not year or not school_id or not major_name or not subject_type:
                continue
            group_code = ""
            key = (province_code, year, school_id, group_code, major_name, subject_type)
            if key in seen:
                continue
            seen.add(key)
            yield (
                province_code,
                year,
                school_id,
                (row.get("university_name") or "").strip()[:120],
                group_code,
                (row.get("major_id") or "").strip()[:48],
                major_name,
                subject_type,
                (row.get("first_subject_requirement") or "").strip()[:60],
                (row.get("resubject_requirement") or "").strip()[:100],
                (row.get("requirement_text") or "").strip()[:500],
                (row.get("source_name") or "").strip()[:120],
                (row.get("source_url") or "").strip()[:500],
            )


def load_compliance_terms() -> list[tuple[str, str, str]]:
    data = json.loads(COMPLIANCE_PATH.read_text(encoding="utf-8"))
    rows: list[tuple[str, str, str]] = []
    for word in data.get("hard", []):
        rows.append((word, "hard", "参考评估"))
    for word in data.get("soft", []):
        rows.append((word, "soft", "参考关注"))
    return rows


def copy_into_pg(conn: Any, table: str, columns: list[str], rows: list[tuple[Any, ...]], truncate: bool = True) -> int:
    with conn.cursor() as cur:
        if truncate:
            cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE")
        col_sql = ", ".join(columns)
        with cur.copy(f"COPY {table} ({col_sql}) FROM STDIN") as copy:
            for r in rows:
                copy.write_row(r)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="Import real GZGK datasets into PostgreSQL 16 via COPY.")
    parser.add_argument("--dry-run", action="store_true", help="Parse and validate local datasets without DB writes.")
    parser.add_argument("--dsn", default=os.environ.get("PG_DSN", ""), help="PostgreSQL DSN (or PG_DSN env var).")
    args = parser.parse_args()

    t0 = time.perf_counter()
    uni_rows = list(iter_universities(EXPORT_DIR / "01_universities.sql"))
    score_rows = list(iter_major_scores(EXPORT_DIR / "major_scores_normalized.sql", "GZ"))
    rank_rows = list(iter_score_rank_csv(EXPORT_DIR / "score_rank_gz.csv", "GZ"))
    req_rows = list(iter_major_requirements_csv(DATA_DIR / "major_requirements_gz.csv", "GZ"))
    compliance_rows = load_compliance_terms()
    parse_sec = time.perf_counter() - t0

    print(
        f"[etl] Parsed in {parse_sec:.2f}s | "
        f"universities={len(uni_rows)}, major_scores={len(score_rows)}, "
        f"score_ranks={len(rank_rows)}, requirements={len(req_rows)}, "
        f"compliance_terms={len(compliance_rows)}"
    )

    if args.dry_run:
        assert len(uni_rows) >= 2000, f"Unexpected university count: {len(uni_rows)}"
        assert len(score_rows) >= 25000, f"Unexpected major score count: {len(score_rows)}"
        assert len(rank_rows) >= 2000, f"Unexpected score rank count: {len(rank_rows)}"
        assert len(req_rows) >= 20000, f"Unexpected major requirement count: {len(req_rows)}"
        print("[etl] DRY-RUN VALIDATION PASSED.")
        return 0

    if not args.dsn:
        print("[etl] ERROR: Provide --dsn or set PG_DSN.", file=sys.stderr)
        return 1

    import psycopg

    t1 = time.perf_counter()
    with psycopg.connect(args.dsn, autocommit=False) as conn:
        copy_into_pg(
            conn,
            "sys_university",
            [
                "school_id", "name", "province", "city", "level", "type_name", "tags",
                "is_985", "is_211", "is_dual_class", "nature_name", "belong", "logo_url",
                "school_site", "phone", "email", "address", "summary",
            ],
            uni_rows,
        )
        copy_into_pg(
            conn,
            "data_major_score_line",
            [
                "province_code", "school_id", "university_name", "major_code", "major_name",
                "year", "subject_type", "batch", "resubject_requirement", "min_score",
                "max_score", "avg_score", "min_rank", "plan_count", "tuition",
            ],
            score_rows,
        )
        copy_into_pg(
            conn,
            "data_score_rank",
            [
                "province_code", "province_name", "year", "subject_type", "score",
                "score_label", "segment_count", "cumulative_count", "cumulative_rate",
                "rank_low", "rank_high", "source_name", "source_url",
            ],
            rank_rows,
        )
        copy_into_pg(
            conn,
            "data_major_requirement",
            [
                "province_code", "year", "school_id", "university_name", "group_code",
                "major_id", "major_name", "subject_type", "first_subject_requirement",
                "resubject_requirement", "requirement_text", "source_name", "source_url",
            ],
            req_rows,
        )
        with conn.cursor() as cur:
            for word, sev, repl in compliance_rows:
                cur.execute(
                    """
                    INSERT INTO compliance_sensitive_word (word, severity, replacement, enabled)
                    VALUES (%s, %s, %s, TRUE)
                    ON CONFLICT (word) DO UPDATE SET severity = EXCLUDED.severity
                    """,
                    (word, sev, repl),
                )
        conn.commit()
    print(f"[etl] PostgreSQL 16 COPY import completed in {time.perf_counter() - t1:.2f}s.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
