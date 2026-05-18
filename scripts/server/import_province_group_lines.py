#!/usr/bin/env python3.11
"""
Generic province group_lines import via /admin/province-data/{province}/group-lines/import.

Generalization of import_ah_group_lines.py — works for SC / AH / HB and future provinces.

Usage:
  python3.11 import_province_group_lines.py \
    --province SC \
    --csv /root/gzly_scraper/sichuan_2025/reviewed/group_lines_sc_2025_reviewed.merged.csv \
    --token <ADMIN_JWT> \
    --no-dry-run

Groups by (sourcePageUrl, sourceUrl, sourceHash, sourceLevel) and posts one CSV per group.
"""
import argparse
import csv
import hashlib
import json
import sys
from collections import defaultdict
from pathlib import Path
import urllib.request
import urllib.parse
import urllib.error

ADMIN_BASE_TMPL = "http://127.0.0.1:8090/api/admin/province-data/{province}"
GROUP_LINE_HEADERS = [
    "schoolId","universityName","groupCode","groupName","subjectType",
    "firstSubjectRequirement","resubjectRequirement","minScore","minRank","planCount","batch",
]


def sha256_text(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def csv_text(headers, rows):
    out = [",".join(headers)]
    for r in rows:
        vals = []
        for h in headers:
            v = str(r.get(h, "") or "")
            if any(ch in v for ch in [",", '"', "\n", "\r"]):
                v = '"' + v.replace('"', '""') + '"'
            vals.append(v)
        out.append(",".join(vals))
    return "\n".join(out) + "\n"


def http_post_json(url, payload, token):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            body = e.read().decode("utf-8")
        except Exception:
            body = ""
        return e.code, {"error": body}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--province", required=True, help="Province code (SC / AH / HB / ...)")
    ap.add_argument("--csv", required=True, help="Reviewed CSV path")
    ap.add_argument("--token", required=True)
    ap.add_argument("--year", type=int, default=2025)
    ap.add_argument("--dry-run", action="store_true", default=True)
    ap.add_argument("--no-dry-run", dest="dry_run", action="store_false")
    args = ap.parse_args()

    csv_path = Path(args.csv)
    if not csv_path.exists():
        print(f"[import] csv not found: {csv_path}", file=sys.stderr)
        sys.exit(2)

    with open(csv_path, encoding="utf-8-sig") as f:
        rows = [r for r in csv.DictReader(f) if any((v or "").strip() for v in r.values())]
    print(f"[import-{args.province}] loaded {len(rows)} reviewed rows from {csv_path}")

    groups = defaultdict(list)
    for r in rows:
        key = (
            (r.get("sourcePageUrl") or "").strip(),
            (r.get("sourceUrl") or "").strip(),
            (r.get("sourceHash") or "").strip(),
            (r.get("sourceLevel") or "school_verified").strip(),
            # v2: 同校多批次必须分 payload，避免 admin API 报“院校专业组重复”
            (r.get("batch") or "").strip(),
        )
        groups[key].append(r)
    print(f"[import-{args.province}] grouped into {len(groups)} payloads")

    admin_base = ADMIN_BASE_TMPL.format(province=args.province)
    total_inserted = 0
    total_updated = 0
    total_rejected = 0
    errors_payload = []
    for idx, ((spage, surl, shash, slevel, batch_norm), grows) in enumerate(groups.items(), 1):
        text = csv_text(GROUP_LINE_HEADERS, grows)
        payload = {
            "year": args.year,
            "sourcePageUrl": spage,
            "sourceUrl": surl or spage,
            "sourceHash": shash or sha256_text(text),
            "sourceLevel": slevel,
            "parseMethod": "manual_verified_csv",
            "preserveNonEmpty": True,
            "csvText": text,
        }
        dry_param = "true" if args.dry_run else "false"
        url = f"{admin_base}/group-lines/import?dryRun={dry_param}"
        status, body = http_post_json(url, payload, args.token)
        data = (body.get("data") or {}) if isinstance(body, dict) else {}
        ins = data.get("inserted", 0)
        upd = data.get("updated", 0)
        rej = data.get("rejected", 0)
        total_inserted += ins
        total_updated += upd
        total_rejected += rej
        bad = rej or status != 200 or (isinstance(body, dict) and body.get("code") not in (0, None))
        if bad:
            print(f"  payload {idx}/{len(groups)} ({len(grows)} rows) STATUS={status} code={body.get('code')} ins={ins} upd={upd} rej={rej}")
            if rej:
                errs = data.get("errors") or []
                print(f"    errors: {errs[:3]}{'...' if len(errs)>3 else ''}")
                errors_payload.append({"payload": idx, "spage": spage, "errors": errs})
        elif idx % 50 == 0 or idx == 1:
            print(f"  payload {idx}/{len(groups)} ({len(grows)} rows) OK ins={ins} upd={upd}")

    summary = {
        "province": args.province,
        "year": args.year,
        "csv": str(csv_path),
        "dry_run": args.dry_run,
        "total_inserted": total_inserted,
        "total_updated": total_updated,
        "total_rejected": total_rejected,
        "payloads": len(groups),
        "rows": len(rows),
    }
    print(f"[import-{args.province}] DONE {json.dumps(summary, ensure_ascii=False)}")
    if errors_payload:
        print(f"[import-{args.province}] ERROR payloads: {len(errors_payload)} (first sample:)")
        print(json.dumps(errors_payload[:1], ensure_ascii=False, indent=2))
        sys.exit(1)


if __name__ == "__main__":
    main()
