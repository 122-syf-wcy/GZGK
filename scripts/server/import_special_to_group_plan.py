#!/usr/bin/env python3.11
"""
将 zjzw special endpoint 的 per-major 草稿入库到 data_admission_group_plan。

输入：zjzw_pull_special_v1.py 输出的 CSV
输出：直接 SQL upsert 到 data_admission_group_plan（按 unique key 去重）

特别说明：
- plan_count / tuition / study_years 全部留空（zjzw special 端点不返回）
- source_name 标 "zjzw_special_endpoint"，source_level=school_verified（后端策略允许）
- batch 按 zjzw 返回的 local_batch_name 直接入库（与 group_line 表 batch 字段对齐）
- 同 (province, year, school, group, major, subject) 唯一键 → REPLACE
"""
import argparse, csv, os
from urllib.parse import urlparse
import pymysql

BLOCKED_HINTS = {"eol.cn","gaokao.cn","youzy.cn","dxsbb.com","zhiyuan","sczjw.com.cn","scedu.net"}

PROV_NAME = {"SC": "四川", "AH": "安徽", "HB": "湖北", "GZ": "贵州"}

# zjzw local_batch_name → 入库 batch (与 merge_zjzw_to_reviewed_v2.py 一致)
BATCH_NORM_MAP = {
    "本科批B段": "普通本科批B段",
    "本科批A段": "普通本科批A段",
    "本科批A段（国家专项）": "本科批A段国家专项",
    "本科批A段（地方专项）": "本科批A段地方专项",
    "本科批（高校专项）": "本科批高校专项",
    "本科批": "普通本科批",
    "国家专项计划批": "国家专项计划",
    "国家专项计划本科批": "国家专项计划",
    "本科一批": "普通本科批",  # 老高考兜底
}

def host_of(u):
    try: return (urlparse(u).hostname or "").lower()
    except Exception: return ""

def is_blocked(u):
    h = host_of(u)
    return any(x in h for x in BLOCKED_HINTS) or not h

def db():
    return pymysql.connect(host="127.0.0.1", user="root", password=os.environ["MYSQL_PWD"],
                            database="gzly", charset="utf8mb4")

def load_school_urls(cur):
    out = {}
    cur.execute("SELECT school_id, admission_brochure_url, school_site, admission_site FROM uni_official_link")
    for sid, br, site, admin in cur.fetchall():
        for cand in (br, admin, site):
            u = (cand or "").strip().rstrip("#").strip()
            if u and not is_blocked(u):
                out[str(sid)] = u; break
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--province", required=True, choices=["SC","AH","HB"])
    ap.add_argument("--year", type=int, default=2025)
    ap.add_argument("--csv", required=True)
    args = ap.parse_args()

    province = args.province
    province_name = PROV_NAME[province]
    print(f"[import-special-{province}] loading {args.csv}")

    conn = db()
    cur = conn.cursor()
    school_urls = load_school_urls(cur)
    print(f"  policy-compliant school URLs: {len(school_urls)}")

    # 先按 (school, group, major, subject) 去重，再 upsert
    seen = set()
    rows = []
    skip_no_url = 0; skip_no_batch = 0; skip_missing = 0; skip_dup = 0
    with open(args.csv, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            sid = (r.get("schoolId") or "").strip()
            name = (r.get("universityName") or "").strip()
            grp = (r.get("groupCode") or "").strip().strip("()（）")
            subj = (r.get("subjectType") or "").strip()
            mc = (r.get("majorCode") or "").strip()
            mn = (r.get("majorName") or "").strip()
            batch_raw = (r.get("batch") or "").strip()
            if not (sid and name and grp and subj and mn):
                skip_missing += 1; continue
            batch_norm = BATCH_NORM_MAP.get(batch_raw)
            if not batch_norm:
                skip_no_batch += 1; continue
            url = school_urls.get(sid)
            if not url:
                skip_no_url += 1; continue
            # 唯一键：(school_id, group_code, major_code or major_name, subject_type, batch)
            key = (sid, grp, mc or mn, subj, batch_norm)
            if key in seen:
                skip_dup += 1; continue
            seen.add(key)
            rows.append({
                "school_id": sid, "name": name, "group_code": grp,
                "major_code": mc, "major_name": mn,
                "subject_type": subj,
                "first_sub": (r.get("firstSubjectInfo") or "").split("，")[0].replace("首选","").strip() if r.get("firstSubjectInfo") else "",
                "resub": "",  # 不分解 sg_info 二级
                "min_score": (r.get("minScore") or "").strip(),
                "batch_norm": batch_norm,
                "url": url,
            })
    print(f"  parsed={len(rows)}  skip_no_url={skip_no_url} skip_no_batch={skip_no_batch} skip_missing={skip_missing} skip_dup={skip_dup}")

    sql = """
INSERT INTO data_admission_group_plan
(province_code, province_name, year, school_id, university_name, group_code, group_name,
 major_code, major_name, subject_type, first_subject_requirement, resubject_requirement,
 plan_count, tuition, study_years, batch,
 source_name, source_url, source_page_url, source_level, parse_method)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
ON DUPLICATE KEY UPDATE
 major_name=VALUES(major_name), first_subject_requirement=VALUES(first_subject_requirement),
 source_url=VALUES(source_url), source_page_url=VALUES(source_page_url),
 parse_method=VALUES(parse_method), updated_at=NOW()
"""
    inserted = 0
    for r in rows:
        cur.execute(sql, (
            province, province_name, args.year,
            r["school_id"], r["name"], r["group_code"], "",
            r["major_code"], r["major_name"], r["subject_type"],
            r["first_sub"], r["resub"],
            None, "", "", r["batch_norm"],
            "zjzw_special_endpoint", r["url"], r["url"],
            "school_verified", "zjzw_special_api_2026-05-18",
        ))
        inserted += 1
    conn.commit()
    print(f"  upserted={inserted}")
    # 统计 by batch
    cur.execute("""SELECT batch, COUNT(*) FROM data_admission_group_plan
                   WHERE province_code=%s AND year=%s GROUP BY batch ORDER BY 2 DESC""",
                (province, args.year))
    for batch, c in cur.fetchall():
        print(f"    {batch}: {c}")
    conn.close()

if __name__ == "__main__":
    main()
