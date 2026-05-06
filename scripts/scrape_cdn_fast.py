#!/usr/bin/env python3
"""
Fast CDN-based scraper for Guizhou province admission scores.
Uses static-data.gaokao.cn JSON API - no browser needed.
~100x faster than Playwright approach.

Usage:
    python scrape_cdn_fast.py                    # full run
    python scrape_cdn_fast.py --limit 100        # first 100 schools
    python scrape_cdn_fast.py --workers 8        # 8 concurrent threads
"""

import argparse, json, os, sys, time, re
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
os.makedirs(EXPORT_DIR, exist_ok=True)

CDN_BASE = "https://static-data.gaokao.cn/www/2.0"
PROVINCE_ID = "52"  # 贵州
HEADERS = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"}
SESSION = requests.Session()
SESSION.headers.update(HEADERS)

all_school_scores = []
all_special_scores = []
completed = 0
total = 0
lock = __import__("threading").Lock()


def fetch_school(school: dict) -> dict:
    global completed
    sid = str(school.get("school_id", ""))
    sname = school.get("name", f"school_{sid}")
    result = {"school": [], "special": []}

    try:
        r = SESSION.get(f"{CDN_BASE}/school/{sid}/provincescore/{PROVINCE_ID}.json", timeout=10)
        if r.status_code != 200:
            return result
        data = r.json().get("data", {})

        for year_str, types in data.items():
            year = int(year_str)
            for type_code, records in types.items():
                for rec in records:
                    result["school"].append({
                        "school_id": sid,
                        "university_name": sname,
                        "year": year,
                        "subject_type": rec.get("type_name", ""),
                        "batch": rec.get("batch_name", ""),
                        "min_score": int(rec["min"]) if rec.get("min") else None,
                        "max_score": int(rec["max"]) if rec.get("max") else None,
                        "min_rank": int(rec["min_section"]) if rec.get("min_section") else None,
                    })
    except Exception:
        pass

    try:
        r2 = SESSION.get(f"{CDN_BASE}/school/{sid}/dic/specialscore.json", timeout=10)
        if r2.status_code == 200:
            dic = r2.json().get("data", {}).get("newsdata", {})
            avail_years = dic.get("year", {}).get(PROVINCE_ID, [])
            avail_types = dic.get("type", {}).get(PROVINCE_ID, [])

            for year in avail_years:
                for type_code in avail_types:
                    try:
                        url = f"{CDN_BASE}/schoolspecialscore/{sid}/{PROVINCE_ID}/{type_code}/{year}/1.json"
                        r3 = SESSION.get(url, timeout=10)
                        if r3.status_code != 200:
                            continue
                        items = r3.json().get("data", {}).get("item", [])
                        for item in items:
                            name = item.get("spname", "")
                            for rm_str in ["（外语语种要求：不限）", "（外语语种要求：英语）"]:
                                name = name.replace(rm_str, "")
                            name = re.sub(r'\s+', ' ', name).strip()
                            if not name:
                                continue
                            result["special"].append({
                                "school_id": sid,
                                "university_name": sname,
                                "major_name": name,
                                "major_id": item.get("special_id", ""),
                                "year": int(year),
                                "subject_type": item.get("local_type_name", ""),
                                "batch": item.get("local_batch_name", ""),
                                "min_score": int(item["min"]) if item.get("min") else None,
                                "max_score": int(item["max"]) if item.get("max") else None,
                                "avg_score": int(item["average"]) if item.get("average") else None,
                                "min_rank": int(item["min_section"]) if item.get("min_section") else None,
                                "plan_count": int(item["num"]) if item.get("num") else None,
                            })
                    except Exception:
                        continue
    except Exception:
        pass

    with lock:
        completed += 1
        n = completed
        all_school_scores.extend(result["school"])
        all_special_scores.extend(result["special"])

    if result["school"] or result["special"]:
        yrs = sorted(set(r["year"] for r in result["school"]))
        print(f"  [{n}/{total}] {sname}: 院校{len(result['school'])}条 专业{len(result['special'])}条 年份{yrs}", flush=True)
    elif n % 100 == 0:
        print(f"  [{n}/{total}] ...", flush=True)

    return result


def export_sql(school_scores, special_scores, path):
    def esc(v):
        if v is None:
            return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(path, "w", encoding="utf-8") as f:
        f.write(f"-- GZLY CDN fast scrape {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        if school_scores:
            f.write("-- 院校投档线\n")
            for r in school_scores:
                f.write(
                    f"INSERT IGNORE INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,"
                    f"`batch`,`min_score`,`max_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{r['min_score'] if r['min_score'] else 'NULL'},"
                    f"{r['max_score'] if r.get('max_score') else 'NULL'},"
                    f"{r['min_rank'] if r['min_rank'] else 'NULL'});\n"
                )

        if special_scores:
            f.write("\n-- 专业分数线\n")
            f.write("""CREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `major_name` VARCHAR(200) NOT NULL,
  `major_id` VARCHAR(20) DEFAULT '',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL,
  `batch` VARCHAR(30) DEFAULT '',
  `min_score` SMALLINT DEFAULT NULL,
  `max_score` SMALLINT DEFAULT NULL,
  `avg_score` SMALLINT DEFAULT NULL,
  `min_rank` INT DEFAULT NULL,
  `plan_count` SMALLINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  UNIQUE KEY `uk_record` (`school_id`, `major_name`(100), `year`, `subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n""")
            for r in special_scores:
                f.write(
                    f"INSERT IGNORE INTO `data_major_score_gz` "
                    f"(`school_id`,`university_name`,`major_name`,`major_id`,"
                    f"`year`,`subject_type`,`batch`,`min_score`,`max_score`,"
                    f"`avg_score`,`min_rank`,`plan_count`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},"
                    f"{esc(r['major_name'])},{esc(r.get('major_id',''))},"
                    f"{r['year']},{esc(r['subject_type'])},"
                    f"{esc(r.get('batch',''))},"
                    f"{r['min_score'] if r['min_score'] else 'NULL'},"
                    f"{r['max_score'] if r.get('max_score') else 'NULL'},"
                    f"{r['avg_score'] if r.get('avg_score') else 'NULL'},"
                    f"{r['min_rank'] if r['min_rank'] else 'NULL'},"
                    f"{r['plan_count'] if r.get('plan_count') else 'NULL'});\n"
                )


def main():
    global total

    ap = argparse.ArgumentParser(description="CDN快速爬取贵州投档线")
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--resume", type=int, default=0)
    args = ap.parse_args()

    cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
    if not os.path.exists(cache_path):
        print(f"错误: {cache_path} 不存在")
        sys.exit(1)

    with open(cache_path) as f:
        schools = json.load(f)

    if args.resume > 0:
        schools = schools[args.resume:]
    if args.limit > 0:
        schools = schools[: args.limit]

    total = len(schools)
    print("=" * 60)
    print(f"  CDN 快速爬取贵州投档线（无需浏览器）")
    print(f"  学校: {total} 所 | 线程: {args.workers}")
    print(f"  数据源: static-data.gaokao.cn")
    print("=" * 60)

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(fetch_school, s): s for s in schools}
        for f in as_completed(futures):
            f.result()

    elapsed = time.time() - t0
    sql_path = os.path.join(EXPORT_DIR, "cdn_scores.sql")
    export_sql(all_school_scores, all_special_scores, sql_path)

    json.dump(all_school_scores, open(os.path.join(DATA_DIR, "cdn_school_scores.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    json.dump(all_special_scores, open(os.path.join(DATA_DIR, "cdn_special_scores.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    by_year = {}
    for r in all_school_scores:
        by_year[r["year"]] = by_year.get(r["year"], 0) + 1

    by_year_s = {}
    for r in all_special_scores:
        by_year_s[r["year"]] = by_year_s.get(r["year"], 0) + 1

    print(f"\n{'=' * 60}")
    print(f"  爬取完成! 耗时 {elapsed:.1f}s")
    print(f"  院校分数线: {len(all_school_scores)} 条 {dict(sorted(by_year.items()))}")
    print(f"  专业分数线: {len(all_special_scores)} 条 {dict(sorted(by_year_s.items()))}")
    print(f"  SQL: {sql_path}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
