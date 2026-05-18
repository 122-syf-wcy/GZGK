#!/usr/bin/env python3.11
"""
Pull per-major score data from zjzw special endpoint:
  apidata/api/gk/score/special?school_id={sid}&local_province_id={pid}&year={yr}

字段映射（写入 data_admission_group_plan 兼容格式 CSV）：
- school_id / sg_name (groupCode) / sg_info → 院校专业组
- sp_name / sp_scode (majorCode) → 专业
- min / max / average / min_section → 专业级分数
- local_batch_name → batch
- level2_name / level3_name → 学科大类（写 majorClassName）
"""
import argparse, csv, json, os, time, urllib.request

PROXY = "http://127.0.0.1:7891"
UA = "Mozilla/5.0"
REF = "https://www.gaokao.cn/"

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": REF})
    op = urllib.request.build_opener(urllib.request.ProxyHandler({"http": PROXY, "https": PROXY}))
    try:
        with op.open(req, timeout=12) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception as e:
        return {"err": str(e)}

def open_mysql():
    import pymysql
    pw = os.environ["MYSQL_PWD"]
    return pymysql.connect(host="127.0.0.1", user="root", password=pw, database="gzly", charset="utf8mb4")

def normalize_subject(t):
    if not t: return ""
    if "物理" in t or "理科" in t: return "物理类"
    if "历史" in t or "文科" in t: return "历史类"
    return t

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--province", required=True, choices=["SC","AH","HB"])
    ap.add_argument("--year", type=int, default=2025)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--sleep", type=float, default=1.2)
    ap.add_argument("--start-school-id", type=int, default=0)
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    PROV_MAP = {"SC": 51, "AH": 34, "HB": 42}
    pid = PROV_MAP[args.province]

    conn = open_mysql()
    with conn.cursor() as cur:
        cur.execute("""
            SELECT school_id, name FROM sys_university
            WHERE school_id REGEXP '^[0-9]+$' AND CAST(school_id AS UNSIGNED) >= %s
            ORDER BY CAST(school_id AS UNSIGNED) ASC
        """, (args.start_school_id,))
        schools = cur.fetchall()
    conn.close()
    if args.limit > 0:
        schools = schools[:args.limit]
    print(f"[zjzw-special-{args.province}] {len(schools)} schools to query")

    os.makedirs(os.path.dirname(args.output) or ".", exist_ok=True)
    out = open(args.output, "w", newline="", encoding="utf-8-sig")
    w = csv.writer(out)
    w.writerow([
        "schoolId","universityName","groupCode","groupName","subjectType",
        "majorCode","majorName","fullMajorName","level2Name","level3Name",
        "minScore","maxScore","avgScore","minRank",
        "firstSubjectInfo","subjectInfo","batch","year","spInfo","note"
    ])
    rows_written = 0
    api_ok = 0
    api_fail = 0
    rate_limit_hits = 0
    for idx, (sid, name) in enumerate(schools, 1):
        url = f"https://api.zjzw.cn/web/api/?uri=apidata/api/gk/score/special&school_id={sid}&local_province_id={pid}&year={args.year}"
        r = fetch(url)
        if r.get("code") == "0000":
            api_ok += 1
            items = (r.get("data") or {}).get("item") or []
            for it in items:
                if it.get("year") != args.year:
                    continue
                w.writerow([
                    sid, name,
                    (it.get("sg_name") or "").strip().strip("()（）"),
                    "",  # groupName 留空
                    normalize_subject(it.get("local_type_name", "")),
                    it.get("sp_scode") or "",
                    it.get("sp_name") or "",
                    it.get("spname") or "",
                    it.get("level2_name") or "",
                    it.get("level3_name") or "",
                    it.get("min") or "",
                    it.get("max") or "",
                    it.get("average") or "",
                    it.get("min_section") or "",
                    it.get("sg_info") or "",
                    it.get("sp_info") or "",
                    it.get("local_batch_name") or "",
                    it.get("year") or args.year,
                    it.get("info") or "",
                    it.get("remark") or ""
                ])
                rows_written += 1
        elif r.get("code") == "1069":
            rate_limit_hits += 1
            print(f"  {idx}/{len(schools)} {sid} RATE LIMITED, sleep 30s", flush=True)
            time.sleep(30)
            api_fail += 1
        else:
            api_fail += 1
        if idx % 20 == 0:
            print(f"  {idx}/{len(schools)} api_ok={api_ok} api_fail={api_fail} rows={rows_written}", flush=True)
        time.sleep(args.sleep)
    out.close()
    print(f"DONE {args.province}: api_ok={api_ok} api_fail={api_fail} rate_limited={rate_limit_hits} rows={rows_written}")
    print(f"OUTPUT: {args.output}")

if __name__ == "__main__":
    main()
