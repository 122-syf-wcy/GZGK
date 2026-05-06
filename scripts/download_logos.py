#!/usr/bin/env python3
"""
批量下载院校校徽 Logo
数据源: static-data.gaokao.cn CDN

用法:
    python download_logos.py
    python download_logos.py --format png   # PNG格式(更清晰,更大)
    python download_logos.py --format jpg   # JPG格式(更小)
"""

import argparse, json, os, sys, time
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
IMAGE_DIR = os.path.join(DATA_DIR, "images")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
os.makedirs(IMAGE_DIR, exist_ok=True)
os.makedirs(EXPORT_DIR, exist_ok=True)

LOGO_CDN = "https://static-data.gaokao.cn/upload/logo"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Referer": "https://www.gaokao.cn/",
}


def download_one(school_id: str, fmt: str) -> tuple[str, bool, str]:
    save_path = os.path.join(IMAGE_DIR, f"{school_id}.{fmt}")
    if os.path.exists(save_path) and os.path.getsize(save_path) > 500:
        return school_id, True, "cached"

    url = f"{LOGO_CDN}/{school_id}.{fmt}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=10, stream=True)
        if r.status_code == 200 and int(r.headers.get("content-length", 0)) > 500:
            with open(save_path, "wb") as f:
                for chunk in r.iter_content(8192):
                    f.write(chunk)
            return school_id, True, "ok"
        return school_id, False, f"status={r.status_code}"
    except Exception as e:
        return school_id, False, str(e)[:50]


def main():
    ap = argparse.ArgumentParser(description="批量下载院校校徽")
    ap.add_argument("--format", default="png", choices=["png", "jpg"])
    ap.add_argument("--workers", type=int, default=5)
    args = ap.parse_args()

    cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
    if not os.path.exists(cache_path):
        print(f"错误: {cache_path} 不存在，请先运行 prefilter_guizhou.py")
        sys.exit(1)

    with open(cache_path) as f:
        schools = json.load(f)
    print(f"共 {len(schools)} 所院校, 格式: {args.format}, 并发: {args.workers}")

    success = 0
    fail = 0
    cached = 0
    failed_ids = []

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {}
        for s in schools:
            sid = str(s.get("school_id", ""))
            if not sid:
                continue
            fut = pool.submit(download_one, sid, args.format)
            futures[fut] = sid

        for i, fut in enumerate(as_completed(futures)):
            sid, ok, msg = fut.result()
            if ok:
                if msg == "cached":
                    cached += 1
                else:
                    success += 1
            else:
                fail += 1
                failed_ids.append(sid)

            total_done = success + fail + cached
            if total_done % 100 == 0 or total_done == len(schools):
                print(f"  进度: {total_done}/{len(schools)} "
                      f"(新下载:{success} 缓存:{cached} 失败:{fail})")

    print(f"\n下载完成: 成功 {success+cached} / 失败 {fail} / 总计 {len(schools)}")
    if failed_ids[:20]:
        print(f"  失败ID(前20): {failed_ids[:20]}")

    sql_path = os.path.join(EXPORT_DIR, "update_logos.sql")
    with open(sql_path, "w", encoding="utf-8") as f:
        f.write("-- 更新院校校徽URL\n")
        f.write("SET NAMES utf8mb4;\n\n")
        for s in schools:
            sid = str(s.get("school_id", ""))
            img_file = f"{sid}.{args.format}"
            img_path = os.path.join(IMAGE_DIR, img_file)
            if os.path.exists(img_path) and os.path.getsize(img_path) > 500:
                cdn_url = f"{LOGO_CDN}/{sid}.{args.format}"
                f.write(f"UPDATE `sys_university` SET `logo_url` = '{cdn_url}' "
                        f"WHERE `school_id` = '{sid}';\n")
    print(f"SQL更新脚本: {sql_path}")


if __name__ == "__main__":
    main()
