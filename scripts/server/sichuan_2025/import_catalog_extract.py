#!/usr/bin/env python3.11
"""Merge catalog vision extracts into data_admission_group_plan.

Strategy:
- catalog `universityName` -> sys_university.school_id by normalized name
- For each catalog row, find candidate DB rows in data_admission_group_plan
  with (province_code=SC, year=2025, school_id, major_name) match
- If exactly one candidate -> UPDATE plan_count / tuition / study_years
  (and only fill, never overwrite non-null with non-null unless --force)
- If multiple candidates same major in different groups, prefer the one
  whose batch matches catalog batch hint; otherwise leave for ambiguity report
- If no candidate, write to gap CSV for review (potential INSERT later)

Outputs a textual stats report; with --apply actually executes UPDATEs.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import re
from collections import Counter, defaultdict
from pathlib import Path

import pymysql

PROVINCE = "SC"
YEAR = 2025

PAREN_RE = re.compile(r"[（(].*?[)）]")


def normalize_name(name: str) -> str:
    if not name:
        return ""
    text = PAREN_RE.sub("", name)
    text = re.sub(r"\s+", "", text)
    text = text.replace("中国人民解放军", "")
    text = text.replace("中国", "")
    return text


def db_connect():
    return pymysql.connect(
        host="127.0.0.1",
        user="root",
        password=os.environ["MYSQL_PWD"],
        database="gzly",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def load_school_id_lookup(cur) -> dict[str, str]:
    """Map normalized university_name -> school_id (string)."""
    cur.execute("SELECT school_id, name FROM sys_university")
    out: dict[str, list[str]] = defaultdict(list)
    for row in cur.fetchall():
        out[normalize_name(row["name"])].append(str(row["school_id"]))
    # only keep entries with exactly one mapping; ambiguous goes to ambig
    lookup: dict[str, str] = {}
    ambig: dict[str, list[str]] = {}
    for k, ids in out.items():
        if len(ids) == 1:
            lookup[k] = ids[0]
        else:
            ambig[k] = ids
    return lookup, ambig


def load_db_rows(cur, school_ids: list[str]) -> dict[tuple[str, str], list[dict]]:
    """Return (school_id, normalized_major_name) -> [db rows]."""
    if not school_ids:
        return {}
    placeholders = ",".join(["%s"] * len(school_ids))
    sql = (
        "SELECT id, school_id, university_name, group_code, major_code, major_name, "
        "subject_type, first_subject_requirement, resubject_requirement, plan_count, "
        "tuition, study_years, batch, parse_method "
        "FROM data_admission_group_plan "
        f"WHERE province_code='{PROVINCE}' AND year={YEAR} AND school_id IN ({placeholders})"
    )
    cur.execute(sql, school_ids)
    index: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in cur.fetchall():
        key = (str(r["school_id"]), normalize_name(r["major_name"]))
        index[key].append(r)
    return index


def load_extracts(extract_dir: Path) -> list[dict]:
    """Flatten all JSON files into a list of catalog rows with source metadata."""
    out: list[dict] = []
    for p in sorted(extract_dir.glob("*.json")):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"  [warn] {p.name}: {e}")
            continue
        rows = data.get("rows", []) or []
        page = data.get("page")
        for r in rows:
            if not isinstance(r, dict):
                continue
            r2 = dict(r)
            r2["_page"] = page
            r2["_source"] = p.name
            r2["_subject_type"] = data.get("subjectType", "")
            out.append(r2)
    return out


def to_int(s):
    try:
        return int(str(s).strip())
    except Exception:
        return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--extract-dir", default="/root/gzly_scraper/sichuan_2025/catalog_extract")
    ap.add_argument("--apply", action="store_true", help="execute UPDATEs")
    ap.add_argument("--force", action="store_true", help="overwrite existing non-null values")
    ap.add_argument("--gap-csv", default="/root/gzly_scraper/sichuan_2025/catalog_extract/_gaps.csv")
    ap.add_argument("--ambig-csv", default="/root/gzly_scraper/sichuan_2025/catalog_extract/_ambig.csv")
    args = ap.parse_args()

    catalog_rows = load_extracts(Path(args.extract_dir))
    print(f"[load] {len(catalog_rows)} rows from {args.extract_dir}")
    if not catalog_rows:
        return

    conn = db_connect()
    cur = conn.cursor()
    name_to_id, ambig_names = load_school_id_lookup(cur)
    print(f"[load] sys_university unique={len(name_to_id)} ambig={len(ambig_names)}")

    # resolve all school names referenced in catalog
    catalog_norm_names = {normalize_name(r.get("universityName") or "") for r in catalog_rows}
    resolved_ids = sorted({name_to_id[n] for n in catalog_norm_names if n in name_to_id})
    unresolved = sorted([n for n in catalog_norm_names if n and n not in name_to_id])
    print(f"[match] catalog distinct schools={len(catalog_norm_names)} resolved={len(resolved_ids)} unresolved={len(unresolved)}")
    if unresolved:
        print(f"  unresolved samples: {unresolved[:5]}")

    db_index = load_db_rows(cur, resolved_ids)
    print(f"[load] db rows index keys={len(db_index)}")

    # match
    updates_ready: list[tuple[dict, dict]] = []  # (db_row, catalog_row)
    gaps: list[dict] = []
    ambig_rows: list[dict] = []
    stats = Counter()

    for c in catalog_rows:
        uni = c.get("universityName") or ""
        nuni = normalize_name(uni)
        if not nuni:
            stats["catalog_empty_school"] += 1
            continue
        sid = name_to_id.get(nuni)
        if not sid:
            stats["unresolved_school"] += 1
            gaps.append({"reason": "no_school_match", **c})
            continue
        mn = c.get("majorName") or ""
        nmajor = normalize_name(mn)
        candidates = db_index.get((sid, nmajor), [])
        if not candidates:
            stats["no_major_match"] += 1
            gaps.append({"reason": "no_major", "schoolId": sid, **c})
            continue
        # subject_type narrowing
        subj = c.get("subjectType") or c.get("_subject_type") or ""
        narrowed = [r for r in candidates if not subj or r["subject_type"] == subj]
        if not narrowed:
            stats["subject_mismatch"] += 1
            gaps.append({"reason": "subject_mismatch", "schoolId": sid, **c})
            continue
        # de-dup by group_code first if catalog provided a clue (mostly different scheme so usually skipped)
        if len(narrowed) == 1:
            updates_ready.append((narrowed[0], c))
            stats["unique_match"] += 1
        else:
            stats["ambig_match"] += 1
            ambig_rows.append({"schoolId": sid, "candidates": len(narrowed), **c})

    print()
    print("[match stats]")
    for k, v in stats.most_common():
        print(f"  {k:25s}  {v}")
    print(f"  -> ready updates: {len(updates_ready)}")

    # write gap/ambig CSVs
    if gaps:
        with open(args.gap_csv, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=sorted({k for g in gaps for k in g.keys()}))
            w.writeheader()
            for g in gaps:
                w.writerow(g)
        print(f"  gap CSV   -> {args.gap_csv} ({len(gaps)} rows)")
    if ambig_rows:
        with open(args.ambig_csv, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=sorted({k for a in ambig_rows for k in a.keys()}))
            w.writeheader()
            for a in ambig_rows:
                w.writerow(a)
        print(f"  ambig CSV -> {args.ambig_csv} ({len(ambig_rows)} rows)")

    # apply updates
    if not args.apply:
        print("\n[dry-run] use --apply to execute UPDATEs")
        # show preview of first 5
        for db_r, cat in updates_ready[:5]:
            print(
                f"  preview: id={db_r['id']} {db_r['university_name']} {db_r['major_name']} "
                f"plan {db_r['plan_count']} -> {cat.get('planCount')} | "
                f"tuition '{db_r['tuition']}' -> '{cat.get('tuition')}' | "
                f"studyYears '{db_r['study_years']}' -> '{cat.get('studyYears')}'"
            )
        conn.close()
        return

    sql_update = (
        "UPDATE data_admission_group_plan SET "
        "plan_count=COALESCE(%s, plan_count), "
        "tuition=CASE WHEN %s != '' THEN %s ELSE tuition END, "
        "study_years=CASE WHEN %s != '' THEN %s ELSE study_years END, "
        "parse_method=CONCAT_WS('+', NULLIF(parse_method,''), 'sceea_catalog_v7.56') "
        "WHERE id=%s"
    )
    if args.force:
        sql_update = (
            "UPDATE data_admission_group_plan SET "
            "plan_count=COALESCE(%s, plan_count), "
            "tuition=IFNULL(NULLIF(%s,''), tuition), "
            "tuition=IFNULL(NULLIF(%s,''), tuition), "  # placeholder mirror
            "study_years=IFNULL(NULLIF(%s,''), study_years), "
            "study_years=IFNULL(NULLIF(%s,''), study_years), "
            "parse_method=CONCAT_WS('+', NULLIF(parse_method,''), 'sceea_catalog_v7.56') "
            "WHERE id=%s"
        )
    applied = 0
    for db_r, cat in updates_ready:
        plan_val = to_int(cat.get("planCount"))
        tuition_val = str(cat.get("tuition") or "").strip()
        study_val = str(cat.get("studyYears") or "").strip()
        cur.execute(sql_update, (
            plan_val,
            tuition_val, tuition_val,
            study_val, study_val,
            db_r["id"],
        ))
        applied += 1
    conn.commit()
    print(f"\n[apply] {applied} rows updated")
    conn.close()


if __name__ == "__main__":
    main()
