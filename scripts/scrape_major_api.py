#!/usr/bin/env python3
"""
专业分数线快速爬取 - 基于动态API签名 + 代理轮换 + 断点续传

Usage:
    python scrape_major_api.py --proxy http://127.0.0.1:7897 --delay 2
    python scrape_major_api.py --limit 50 --proxy http://127.0.0.1:7897
    python scrape_major_api.py --school 935
"""

import argparse, json, os, sys, time, re, hmac, hashlib, base64, threading, random, socket
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import urlparse
import requests

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
os.makedirs(EXPORT_DIR, exist_ok=True)

SIGN_KEY = "D23ABC@#56"
API_BASE = "https://api.zjzw.cn/web/api/"
API_PATH = "api.zjzw.cn/web/api/"
API_URI = "apidata/api/gk/score/special"
PROVINCE_ID = "52"
PAGE_SIZE = "20"

YEAR_TYPES = {
    "2025": ["2073", "2074"],
    "2024": ["2073", "2074"],
    "2023": ["1", "2"],
    "2022": ["1", "2"],
    "2021": ["1", "2"],
}

TYPE_NAMES = {
    "2073": "物理类", "2074": "历史类",
    "1": "理科", "2": "文科",
}

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
    "Referer": "https://www.gaokao.cn/",
}

PROXY = None
CLASH_API = "http://127.0.0.1:9090"
CLASH_SECRET = "gzly2026"
PROXY_GROUP = "MESL"
proxy_nodes = []
current_proxy_idx = 0

CDN_BASE = "https://static-data.gaokao.cn/www/2.0"

CLEAN_PATTERNS = [
    "（外语语种要求：不限）",
    "（外语语种要求：英语）",
    "（外语语种要求：英语/日语）",
    "（外语语种要求：不提科目要求）",
]

REQUEST_DELAY = 2.0
MAX_RETRIES = 5
RETRY_WAIT = [10, 30, 60, 120, 180]

lock = threading.Lock()
rate_lock = threading.Lock()
all_records = []
done_school_ids = set()
completed = 0
total = 0
errors = 0
api_calls = 0
rate_limit_hits = 0
last_request_time = time.time()
cooldown_until = 0


def make_signsafe(params: dict) -> str:
    sorted_qs = "&".join(
        f"{k}={v if v is not None else ''}" for k, v in sorted(params.items())
    )
    raw = f"{API_PATH}?{sorted_qs}" if sorted_qs else API_PATH
    mac = hmac.new(SIGN_KEY.encode(), raw.encode(), hashlib.sha1).digest()
    b64 = base64.b64encode(mac).decode()
    return hashlib.md5(b64.encode()).hexdigest()


def clean_major_name(name: str) -> str:
    for pat in CLEAN_PATTERNS:
        name = name.replace(pat, "")
    name = re.sub(r"（外语语种要求：[^）]*）", "", name)
    return re.sub(r"\s+", " ", name).strip()


def can_connect(host: str, port: int, timeout: float = 2.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def resolve_proxy(proxy: str) -> str:
    raw = proxy if "://" in proxy else f"http://{proxy}"
    parsed = urlparse(raw)
    scheme = parsed.scheme or "http"
    host = parsed.hostname
    port = parsed.port

    if not host or not port:
        print(f"错误: 代理地址格式无效: {proxy}", flush=True)
        return ""

    candidate = f"{scheme}://{host}:{port}"
    if can_connect(host, port):
        return candidate

    print(f"  代理不可达: {candidate}", flush=True)
    if host in ("127.0.0.1", "localhost"):
        for fallback_port in (7890, 7897):
            if fallback_port == port:
                continue
            if can_connect(host, fallback_port):
                fallback = f"{scheme}://{host}:{fallback_port}"
                print(f"  自动切换到可用代理: {fallback}", flush=True)
                return fallback

    return ""


def init_proxy_nodes():
    global proxy_nodes
    if not PROXY:
        return
    try:
        headers = {"Authorization": f"Bearer {CLASH_SECRET}"}
        r = requests.get(f"{CLASH_API}/proxies/Scraper-LB", headers=headers, timeout=5)
        if r.status_code == 200:
            proxy_nodes = r.json().get("all", [])
            if proxy_nodes:
                print(f"  代理节点: {len(proxy_nodes)} 个", flush=True)
                switch_proxy(random.randint(0, len(proxy_nodes) - 1))
    except Exception:
        print("  代理节点获取失败，使用默认代理", flush=True)


def switch_proxy(idx: int):
    global current_proxy_idx
    if not proxy_nodes:
        return
    current_proxy_idx = idx % len(proxy_nodes)
    node = proxy_nodes[current_proxy_idx]
    try:
        headers = {
            "Authorization": f"Bearer {CLASH_SECRET}",
            "Content-Type": "application/json",
        }
        requests.put(
            f"{CLASH_API}/proxies/{PROXY_GROUP}",
            headers=headers,
            json={"name": node},
            timeout=5,
        )
    except Exception:
        pass


def throttled_get(session: requests.Session, params: dict) -> dict:
    global api_calls, last_request_time, cooldown_until, current_proxy_idx, rate_limit_hits

    with rate_lock:
        now = time.time()
        if now < cooldown_until:
            wait = cooldown_until - now
            if wait > 1:
                print(f"    冷却中 {wait:.0f}s...", flush=True)
            time.sleep(wait)
        elapsed = time.time() - last_request_time
        jitter = random.uniform(0, REQUEST_DELAY * 0.5)
        needed = REQUEST_DELAY + jitter - elapsed
        if needed > 0:
            time.sleep(needed)
        last_request_time = time.time()
        api_calls += 1

    if proxy_nodes:
        with rate_lock:
            switch_proxy(current_proxy_idx + 1)

    for attempt in range(MAX_RETRIES):
        try:
            proxies = {"http": PROXY, "https": PROXY} if PROXY else None
            r = requests.get(
                API_BASE, params=params, timeout=15,
                headers=HEADERS, proxies=proxies,
            )
            data = r.json()
        except Exception as e:
            if attempt in (0, MAX_RETRIES - 1):
                print(
                    f"    请求异常 sid={params.get('school_id')} year={params.get('year')} "
                    f"type={params.get('local_type_id')} page={params.get('page')}: "
                    f"{e.__class__.__name__}: {e}",
                    flush=True,
                )
            if proxy_nodes:
                with rate_lock:
                    switch_proxy(current_proxy_idx + 1)
            time.sleep(5 + random.uniform(0, 3))
            continue

        code = data.get("code", "")
        if code == "0000":
            return data

        if code == "1069":
            with rate_lock:
                rate_limit_hits += 1
            if proxy_nodes:
                with rate_lock:
                    switch_proxy(current_proxy_idx + 1)
                wait = 3 + random.uniform(0, 5)
                time.sleep(wait)
            else:
                wait = RETRY_WAIT[min(attempt, len(RETRY_WAIT) - 1)]
                with rate_lock:
                    cooldown_until = time.time() + wait
                print(f"    限速! 暂停{wait}s (attempt {attempt+1})", flush=True)
                time.sleep(wait)
            continue

        print(
            f"    接口返回异常 code={code or 'EMPTY'} sid={params.get('school_id')} "
            f"year={params.get('year')} type={params.get('local_type_id')} "
            f"page={params.get('page')}",
            flush=True,
        )
        return data

    return {"code": "FAIL", "data": {}}


def fetch_page(session: requests.Session, sid: str, year: str, type_id: str, page: int) -> tuple:
    params = {
        "local_province_id": PROVINCE_ID,
        "local_type_id": type_id,
        "page": str(page),
        "school_id": sid,
        "size": PAGE_SIZE,
        "uri": API_URI,
        "year": year,
    }
    data = throttled_get(session, params)
    if data.get("code") != "0000":
        return [], 0
    result = data.get("data", {})
    if isinstance(result, dict):
        return result.get("item", []), result.get("numFound", 0)
    return [], 0


def get_school_year_types(session: requests.Session, sid: str) -> dict:
    try:
        time.sleep(0.3 + random.uniform(0, 0.5))
        proxies = {"http": PROXY, "https": PROXY} if PROXY else None
        r = session.get(
            f"{CDN_BASE}/school/{sid}/dic/specialscore.json",
            timeout=8,
            proxies=proxies,
        )
        if r.status_code != 200:
            return {}
        data = r.json().get("data", {}).get("newsdata", {})
        years = data.get("year", {}).get(PROVINCE_ID, [])
        if not years:
            return {}
        result = {}
        type_dict = data.get("type", {})
        for year in years:
            key = f"{PROVINCE_ID}_{year}"
            types = type_dict.get(key, [])
            if types:
                result[str(year)] = [str(t) for t in types]
        return result
    except Exception as e:
        print(f"    年份科类元数据获取失败 sid={sid}: {e.__class__.__name__}: {e}", flush=True)
        return YEAR_TYPES


def fetch_school(school: dict) -> list:
    global completed, errors
    sid = str(school.get("school_id", ""))
    sname = school.get("name", f"school_{sid}")

    if sid in done_school_ids:
        with lock:
            completed += 1
        return []

    records = []
    session = requests.Session()
    session.headers.update(HEADERS)

    school_yt = get_school_year_types(session, sid)
    if not school_yt:
        with lock:
            completed += 1
            n = completed
        if n % 200 == 0:
            print(f"  [{n}/{total}] ...", flush=True)
        return records

    for year, type_ids in school_yt.items():
        for type_id in type_ids:
            try:
                items, num_found = fetch_page(session, sid, year, type_id, 1)
                all_items = list(items)

                page = 2
                while len(all_items) < num_found:
                    more, _ = fetch_page(session, sid, year, type_id, page)
                    if not more:
                        break
                    all_items.extend(more)
                    page += 1

                for item in all_items:
                    raw_name = item.get("spname", "")
                    name = clean_major_name(raw_name)
                    if not name:
                        continue
                    rec = {
                        "school_id": sid,
                        "university_name": sname,
                        "major_name": name,
                        "major_id": str(item.get("special_id", "")),
                        "year": int(year),
                        "subject_type": item.get("local_type_name", TYPE_NAMES.get(type_id, "")),
                        "batch": item.get("local_batch_name", ""),
                    }
                    for field, key in [("min_score", "min"), ("max_score", "max"), ("avg_score", "average"), ("min_rank", "min_section")]:
                        val = item.get(key)
                        rec[field] = int(val) if val and str(val).strip("-") else None
                    records.append(rec)
            except Exception:
                with lock:
                    errors += 1

    with lock:
        completed += 1
        n = completed
        all_records.extend(records)
        done_school_ids.add(sid)

    if records:
        yrs = sorted(set(r["year"] for r in records))
        print(f"  [{n}/{total}] {sname}: {len(records)}条 年份{yrs}", flush=True)
    elif n % 100 == 0:
        print(f"  [{n}/{total}] ... (总计{len(all_records)}条)", flush=True)

    return records


def save_checkpoint():
    with lock:
        cp_path = os.path.join(DATA_DIR, "major_api_checkpoint.json")
        with open(cp_path, "w", encoding="utf-8") as f:
            json.dump(all_records, f, ensure_ascii=False)
        json_path = os.path.join(DATA_DIR, "major_scores_api.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(all_records, f, ensure_ascii=False, indent=2)
        print(f"  [checkpoint] {len(all_records)}条 / {len(done_school_ids)}校已保存 | API:{api_calls} 限速:{rate_limit_hits}", flush=True)


def export_sql(records: list, path: str):
    def esc(v):
        if v is None:
            return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(path, "w", encoding="utf-8") as f:
        f.write(f"-- 专业分数线 API爬取 {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")
        f.write("""CREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `major_name` VARCHAR(200) NOT NULL,
  `major_id` VARCHAR(20) DEFAULT '',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL,
  `batch` VARCHAR(50) DEFAULT '',
  `min_score` SMALLINT DEFAULT NULL,
  `max_score` SMALLINT DEFAULT NULL,
  `avg_score` SMALLINT DEFAULT NULL,
  `min_rank` INT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  KEY `idx_major` (`major_name`(100)),
  UNIQUE KEY `uk_record` (`school_id`, `major_name`(100), `year`, `subject_type`, `batch`(30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州专业投档线';\n\n""")

        for r in records:
            f.write(
                f"INSERT IGNORE INTO `data_major_score_gz` "
                f"(`school_id`,`university_name`,`major_name`,`major_id`,"
                f"`year`,`subject_type`,`batch`,`min_score`,`max_score`,"
                f"`avg_score`,`min_rank`) VALUES "
                f"({esc(r['school_id'])},{esc(r['university_name'])},"
                f"{esc(r['major_name'])},{esc(r.get('major_id',''))},"
                f"{r['year']},{esc(r['subject_type'])},"
                f"{esc(r.get('batch',''))},"
                f"{r['min_score'] if r['min_score'] else 'NULL'},"
                f"{r['max_score'] if r.get('max_score') else 'NULL'},"
                f"{r['avg_score'] if r.get('avg_score') else 'NULL'},"
                f"{r['min_rank'] if r['min_rank'] else 'NULL'});\n"
            )


def main():
    global total, completed

    ap = argparse.ArgumentParser(description="专业分数线API快速爬取")
    ap.add_argument("--workers", type=int, default=1)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--resume", type=int, default=0)
    ap.add_argument("--school", type=str, default="")
    ap.add_argument("--delay", type=float, default=2.0)
    ap.add_argument("--proxy", type=str, default="")
    args = ap.parse_args()

    global REQUEST_DELAY, PROXY
    REQUEST_DELAY = args.delay
    if args.proxy:
        PROXY = resolve_proxy(args.proxy)
        if not PROXY:
            print(f"错误: 指定代理不可用: {args.proxy}", flush=True)
            sys.exit(2)

    json_path = os.path.join(DATA_DIR, "major_scores_api.json")
    if os.path.exists(json_path) and not args.school:
        try:
            with open(json_path) as f:
                existing = json.load(f)
            all_records.extend(existing)
            for r in existing:
                done_school_ids.add(str(r["school_id"]))
            print(f"  已加载 {len(existing)} 条历史记录 ({len(done_school_ids)} 校)", flush=True)
        except Exception:
            pass

    if args.school:
        schools = [{"school_id": args.school, "name": f"school_{args.school}"}]
    else:
        cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
        if not os.path.exists(cache_path):
            print(f"错误: {cache_path} 不存在")
            sys.exit(1)
        with open(cache_path) as f:
            schools = json.load(f)

    if args.resume > 0:
        schools = schools[args.resume:]
    if args.limit > 0:
        schools = schools[:args.limit]

    remaining = [s for s in schools if str(s.get("school_id", "")) not in done_school_ids]
    total = len(schools)
    completed = total - len(remaining)

    print("=" * 60)
    print(f"  专业分数线 API 快速爬取")
    print(f"  学校: {total} 所 | 待爬: {len(remaining)} 所 | 已完成: {completed} 所")
    print(f"  线程: {args.workers} | 间隔: {REQUEST_DELAY}s (+随机抖动)")
    print(f"  年份: {list(YEAR_TYPES.keys())}")
    print(f"  代理: {PROXY or '无'}")
    print("=" * 60)

    init_proxy_nodes()

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(fetch_school, s): s for s in remaining}
        done_count = 0
        for f in as_completed(futures):
            f.result()
            done_count += 1
            if done_count % 50 == 0:
                save_checkpoint()

    save_checkpoint()
    elapsed = time.time() - t0

    sql_path = os.path.join(EXPORT_DIR, "major_scores_api.sql")
    export_sql(all_records, sql_path)

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(all_records, f, ensure_ascii=False, indent=2)

    by_year = {}
    by_school = set()
    for r in all_records:
        by_year[r["year"]] = by_year.get(r["year"], 0) + 1
        by_school.add(r["university_name"])

    print(f"\n{'=' * 60}")
    print(f"  爬取完成! 耗时 {elapsed:.1f}s")
    print(f"  专业分数线: {len(all_records)} 条 / {len(by_school)} 校")
    print(f"  按年份: {dict(sorted(by_year.items()))}")
    print(f"  API调用: {api_calls} 次 | 限速: {rate_limit_hits} 次")
    print(f"  错误数: {errors}")
    print(f"  SQL: {sql_path}")
    print(f"  JSON: {json_path}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
