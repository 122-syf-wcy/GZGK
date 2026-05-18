#!/usr/bin/env python3.11
"""
v2: 扩展 merge —— 把 zjzw 草稿里所有"本科批*"批次都按对应的 batch_name 写入 reviewed CSV。
原 v1 只保留 "本科批B段" → 本轮新增国家专项 / 地方专项 / 高校专项 / 本科批A段 路径。
其它策略（URL 替换、school_verified、去重）与 v1 一致。
"""
import argparse, csv, json, os
from collections import OrderedDict
from pathlib import Path
from urllib.parse import urlparse
import pymysql

ROOT = Path(__file__).resolve().parent
REVIEWED_DIR = ROOT / "reviewed"
REPORTS_DIR = ROOT / "reports"

OUT_HEADERS = [
    "schoolId","universityName","groupCode","groupName","subjectType",
    "firstSubjectRequirement","resubjectRequirement","minScore","minRank","planCount","batch",
    "sourcePageUrl","sourceUrl","sourceHash","sourceLevel",
]

BLOCKED_HINTS = {"eol.cn","gaokao.cn","youzy.cn","dxsbb.com","zhiyuan","sczjw.com.cn","scedu.net"}

# zjzw local_batch_name → 入库 batch
BATCH_NORM = {
    "本科批B段": "普通本科批B段",
    "本科批A段": "普通本科批A段",
    "本科批A段（国家专项）": "本科批A段国家专项",
    "本科批A段（地方专项）": "本科批A段地方专项",
    "本科批（高校专项）": "本科批高校专项",
}

def host_of(u):
    try: return (urlparse(u).hostname or "").lower()
    except Exception: return ""

def is_blocked(u):
    h = host_of(u)
    return any(x in h for x in BLOCKED_HINTS) or not h

def db():
    pw = os.environ.get("MYSQL_PWD") or os.environ.get("GZLY_DB_PASSWORD")
    return pymysql.connect(host="127.0.0.1", user="root", password=pw, database="gzly", charset="utf8mb4")

def load_school_urls():
    out = {}
    with db() as c, c.cursor() as cur:
        cur.execute("SELECT school_id, admission_brochure_url, school_site, admission_site FROM uni_official_link")
        for sid, br, site, admin in cur.fetchall():
            for cand in (br, admin, site):
                u = (cand or "").strip().rstrip("#").strip()
                if u and not is_blocked(u):
                    out[str(sid)] = u
                    break
    return out

def normalize_code(c):
    return str(c or "").strip().strip("()（）")

def load_existing(path):
    rows = []
    if not Path(path).exists(): return rows
    with open(path, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            if any((v or "").strip() for v in r.values()):
                rows.append(dict(r))
    return rows

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--draft", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--report", required=True)
    ap.add_argument("--existing", required=True)
    args = ap.parse_args()

    print(f"[merge-v2] uni_official_link load…")
    urls = load_school_urls()
    print(f"[merge-v2]   policy-compliant schools: {len(urls)}")

    existing = load_existing(args.existing)
    ekeys = {(r.get("universityName","").strip(), normalize_code(r.get("groupCode")),
              r.get("subjectType","").strip(), r.get("batch","").strip()) for r in existing}
    print(f"[merge-v2] existing rows: {len(existing)} ({len(ekeys)} unique)")

    new = OrderedDict()
    stats = dict(total=0, batch_skip=0, dup_existing=0, dup_new=0, no_url=0, blocked_url=0,
                 missing_fields=0, kept=0, by_batch={})
    with open(args.draft, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            stats["total"] += 1
            raw_batch = (r.get("batch") or "").strip()
            target_batch = BATCH_NORM.get(raw_batch)
            if not target_batch:
                stats["batch_skip"] += 1; continue
            sid = (r.get("schoolId") or "").strip()
            name = (r.get("universityName") or "").strip()
            code = normalize_code(r.get("groupCode"))
            subj = (r.get("subjectType") or "").strip()
            if not (sid and name and code and subj):
                stats["missing_fields"] += 1; continue
            key = (name, code, subj, target_batch)
            if key in ekeys:
                stats["dup_existing"] += 1; continue
            if key in new:
                stats["dup_new"] += 1; continue
            url = urls.get(sid)
            if not url:
                stats["no_url"] += 1; continue
            if is_blocked(url):
                stats["blocked_url"] += 1; continue
            new[key] = {
                "schoolId": sid, "universityName": name, "groupCode": code, "groupName": "",
                "subjectType": subj,
                "firstSubjectRequirement": (r.get("firstSubjectRequirement") or "").strip(),
                "resubjectRequirement": (r.get("resubjectRequirement") or "").strip(),
                "minScore": (r.get("minScore") or "").strip(),
                "minRank": (r.get("minRank") or "").strip(),
                "planCount": "", "batch": target_batch,
                "sourcePageUrl": url, "sourceUrl": url, "sourceHash": "",
                "sourceLevel": "school_verified",
            }
            stats["kept"] += 1
            stats["by_batch"][target_batch] = stats["by_batch"].get(target_batch, 0) + 1

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    all_rows = existing + list(new.values())
    with open(out_path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=OUT_HEADERS)
        w.writeheader()
        for r in all_rows:
            w.writerow({h: r.get(h, "") for h in OUT_HEADERS})

    report = {
        "draft": args.draft, "output": str(out_path),
        "existing_rows": len(existing), "new_rows": len(new),
        "total_after_merge": len(all_rows),
        "input_stats": stats,
    }
    Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
