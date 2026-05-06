#!/usr/bin/env python3
"""Discover school-site sources for Sichuan 2025 B-batch data.

This script is read-only against MySQL and writes local source candidates only.
It does not import rows into production tables.
"""

import argparse
import json
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

from common import (
    RAW_DIR,
    REPORTS_DIR,
    content_snippet,
    decode_body,
    ensure_dirs,
    extract_links,
    fetch_url,
    host_of,
    looks_like_download,
    mysql_query,
    save_fetch_result,
    sha256_bytes,
    sha256_file,
    strip_html_text,
    write_csv,
)


KEYWORDS = [
    "四川",
    "2025",
    "本科批B段",
    "本科批 B 段",
    "本科批",
    "院校专业组",
    "专业组",
    "调档线",
    "投档线",
    "录取分数",
    "录取分数线",
    "招生计划",
    "招生专业",
]

URL_HINTS = [
    "sichuan",
    "sc",
    "2025",
    "lqfs",
    "lnfs",
    "fsx",
    "zsjh",
    "zsplan",
    "plan",
    "admission",
    "bkzs",
    "benke",
    "zhaosheng",
]
IMAGE_DATA_HINTS = [
    "zsjh",
    "zsplan",
    "plan",
    "lqfs",
    "lnfs",
    "fsx",
    "score",
    "admission",
    "bkzs",
    "benke",
    "zhaosheng",
    "录取",
    "分数",
    "招生",
]

STATIC_ASSET_SUFFIXES = {".css", ".js", ".ico", ".gif", ".svg", ".woff", ".woff2", ".ttf"}
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}

BLOCKED_HOST_HINTS = [
    "eol.cn",
    "gaokao.cn",
    "youzy.cn",
    "dxsbb.com",
    "zhiyuan",
]


def load_school_seeds(env_file: str, limit: int, offset: int) -> list[dict[str, str]]:
    sql = f"""
SELECT
  u.school_id,
  u.name,
  COALESCE(u.logo_url, ''),
  COALESCE(l.school_site, ''),
  COALESCE(l.admission_site, ''),
  COALESCE(l.admission_brochure_url, ''),
  COALESCE(l.major_catalog_url, ''),
  COALESCE(l.tuition_info_url, '')
FROM sys_university u
LEFT JOIN uni_official_link l ON l.school_id = u.school_id
ORDER BY u.id
LIMIT {int(limit)} OFFSET {int(offset)}
"""
    rows = mysql_query(sql, env_file=env_file)
    seeds: list[dict[str, str]] = []
    for row in rows:
        while len(row) < 8:
            row.append("")
        seeds.append({
            "schoolId": row[0],
            "universityName": row[1],
            "logoUrl": row[2],
            "schoolSite": row[3],
            "admissionSite": row[4],
            "admissionBrochureUrl": row[5],
            "majorCatalogUrl": row[6],
            "tuitionInfoUrl": row[7],
        })
    return seeds


def is_blocked_candidate(url: str) -> bool:
    host = host_of(url)
    return any(hint in host for hint in BLOCKED_HOST_HINTS)


def url_score(url: str) -> int:
    lower = url.lower()
    suffix = Path(lower.split("?", 1)[0]).suffix
    if suffix in STATIC_ASSET_SUFFIXES:
        return -1
    score = 0
    for hint in URL_HINTS:
        if hint.lower() in lower:
            score += 1
    if suffix in IMAGE_SUFFIXES and score == 0:
        return -1
    if suffix in IMAGE_SUFFIXES and not any(hint.lower() in lower for hint in IMAGE_DATA_HINTS):
        return -1
    if looks_like_download(url) and score > 0:
        score += 2
    return score


def text_score(text: str) -> tuple[int, list[str]]:
    plain = strip_html_text(text)
    matched = [keyword for keyword in KEYWORDS if keyword in plain]
    score = len(matched)
    if "四川" in matched and "2025" in matched:
        score += 4
    if "本科批B段" in matched or "本科批 B 段" in matched:
        score += 6
    if "院校专业组" in matched:
        score += 3
    return score, matched


def candidate_urls(seed: dict[str, str]) -> list[str]:
    urls: list[str] = []
    for key in ("admissionSite", "majorCatalogUrl", "admissionBrochureUrl", "tuitionInfoUrl", "schoolSite"):
        value = seed.get(key, "").strip()
        if value and value.startswith(("http://", "https://")) and not is_blocked_candidate(value):
            if value not in urls:
                urls.append(value)
    return urls


def local_logo_path(seed: dict[str, str]) -> str:
    path = Path("/root/gzly_scraper/data/images") / f"{seed['schoolId']}.png"
    return str(path) if path.exists() else ""


def inspect_page(seed: dict[str, str], url: str, source_kind: str, timeout: int, retries: int,
                 max_bytes: int) -> tuple[dict[str, Any] | None, list[str]]:
    result = fetch_url(url, timeout=timeout, retries=retries, max_bytes=max_bytes)
    if not result.body:
        return {
            "schoolId": seed["schoolId"],
            "universityName": seed["universityName"],
            "logoUrl": seed.get("logoUrl", ""),
            "localLogoPath": local_logo_path(seed),
            "sourcePageUrl": url,
            "finalUrl": result.final_url,
            "sourceKind": source_kind,
            "status": result.status,
            "contentType": result.content_type,
            "sourceHash": "",
            "rawPath": "",
            "confidence": 0,
            "matchedKeywords": "",
            "evidenceSnippet": result.error,
            "sourceLevel": "school_candidate",
        }, []

    if "image/" in result.content_type.lower() and url_score(result.final_url or url) < 4:
        return {
            "schoolId": seed["schoolId"],
            "universityName": seed["universityName"],
            "logoUrl": seed.get("logoUrl", ""),
            "localLogoPath": local_logo_path(seed),
            "sourcePageUrl": url,
            "finalUrl": result.final_url,
            "sourceKind": source_kind,
            "status": result.status,
            "contentType": result.content_type,
            "sourceHash": "",
            "rawPath": "",
            "confidence": 0,
            "matchedKeywords": "",
            "evidenceSnippet": "跳过低价值图片资源",
            "sourceLevel": "school_candidate",
        }, []

    raw_path = save_fetch_result(result, RAW_DIR / "school_pages" / seed["schoolId"], source_kind)
    text = decode_body(result.body, result.content_type)
    confidence, matched = text_score(text)
    if looks_like_download(result.final_url, result.content_type):
        confidence += 2
    record = {
        "schoolId": seed["schoolId"],
        "universityName": seed["universityName"],
        "logoUrl": seed.get("logoUrl", ""),
        "localLogoPath": local_logo_path(seed),
        "sourcePageUrl": url,
        "finalUrl": result.final_url,
        "sourceKind": source_kind,
        "status": result.status,
        "contentType": result.content_type,
        "sourceHash": sha256_file(raw_path),
        "rawPath": str(raw_path),
        "confidence": confidence,
        "matchedKeywords": ",".join(matched),
        "evidenceSnippet": content_snippet(text, KEYWORDS),
        "sourceLevel": "school_candidate",
    }
    links = []
    if "html" in result.content_type.lower() or raw_path.suffix.lower() in {"", ".html", ".htm"}:
        links = extract_links(text, result.final_url or url)
    return record, links


def process_school(seed: dict[str, str], max_pages: int, sleep_seconds: float, timeout: int, retries: int,
                   max_bytes: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    initial_urls = candidate_urls(seed)
    link_scores: dict[str, int] = {}

    for url in initial_urls:
        if url in seen_urls:
            continue
        seen_urls.add(url)
        record, links = inspect_page(seed, url, "seed", timeout, retries, max_bytes)
        if record:
            rows.append(record)
        for link in links:
            if is_blocked_candidate(link):
                continue
            score = url_score(link)
            if score > 0:
                link_scores[link] = max(link_scores.get(link, 0), score)
        time.sleep(sleep_seconds)

    ranked_links = [
        url for url, _ in sorted(link_scores.items(), key=lambda item: (-item[1], item[0]))
        if url not in seen_urls
    ][:max_pages]
    for url in ranked_links:
        seen_urls.add(url)
        record, _ = inspect_page(seed, url, "linked", timeout, retries, max_bytes)
        if record:
            rows.append(record)
        time.sleep(sleep_seconds)

    return sorted(rows, key=lambda item: int(item.get("confidence") or 0), reverse=True)


def error_row(seed: dict[str, str], exc: Exception) -> dict[str, Any]:
    return {
        "schoolId": seed["schoolId"],
        "universityName": seed["universityName"],
        "logoUrl": seed.get("logoUrl", ""),
        "localLogoPath": local_logo_path(seed),
        "sourcePageUrl": "",
        "finalUrl": "",
        "sourceKind": "school_error",
        "status": 0,
        "contentType": "",
        "sourceHash": "",
        "rawPath": "",
        "confidence": 0,
        "matchedKeywords": "",
        "sourceLevel": "school_candidate",
        "evidenceSnippet": f"school crawl failed: {exc}",
    }


def crawl_one(args: tuple[int, int, dict[str, str], int, float, int, int, int]) -> tuple[int, int, dict[str, str], list[dict[str, Any]]]:
    index, total, seed, max_pages, sleep_seconds, timeout, retries, max_bytes = args
    try:
        rows = process_school(seed, max_pages, sleep_seconds, timeout, retries, max_bytes)
    except Exception as exc:
        rows = [error_row(seed, exc)]
    return index, total, seed, rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def append_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Discover Sichuan B-batch data candidates from school official sites.")
    parser.add_argument("--env-file", default="/etc/gzly/gzly.env")
    parser.add_argument("--limit", type=int, default=20, help="school seed limit; default is safe sample size")
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--all", action="store_true", help="crawl all schools with known links")
    parser.add_argument("--max-pages-per-school", type=int, default=8)
    parser.add_argument("--sleep", type=float, default=0.4)
    parser.add_argument("--timeout", type=int, default=12)
    parser.add_argument("--retries", type=int, default=1)
    parser.add_argument("--max-bytes", type=int, default=1200000, help="skip individual school pages/files larger than this")
    parser.add_argument("--workers", type=int, default=1, help="parallel school workers; keep modest for public sites")
    args = parser.parse_args()
    ensure_dirs()

    limit = 100000 if args.all else args.limit
    seeds = load_school_seeds(args.env_file, limit=limit, offset=args.offset)
    all_rows: list[dict[str, Any]] = []
    partial_jsonl = REPORTS_DIR / "school_discovery.partial.jsonl"
    if partial_jsonl.exists():
        partial_jsonl.unlink()
    jobs = [
        (index, len(seeds), seed, args.max_pages_per_school, args.sleep, args.timeout, args.retries, args.max_bytes)
        for index, seed in enumerate(seeds, 1)
    ]
    workers = max(1, min(args.workers, 16))
    if workers == 1:
        for job in jobs:
            index, total, seed, rows = crawl_one(job)
            all_rows.extend(rows)
            append_jsonl(partial_jsonl, rows)
            best = rows[0]["confidence"] if rows else 0
            print(f"[{index}/{total}] {seed['schoolId']} {seed['universityName']} candidates={len(rows)} best={best}", flush=True)
    else:
        with ThreadPoolExecutor(max_workers=workers) as executor:
            future_map = {executor.submit(crawl_one, job): job for job in jobs}
            for future in as_completed(future_map):
                index, total, seed, rows = future.result()
                all_rows.extend(rows)
                append_jsonl(partial_jsonl, rows)
                best = rows[0]["confidence"] if rows else 0
                print(f"[{index}/{total}] {seed['schoolId']} {seed['universityName']} candidates={len(rows)} best={best}", flush=True)

    headers = [
        "schoolId",
        "universityName",
        "logoUrl",
        "localLogoPath",
        "sourcePageUrl",
        "finalUrl",
        "sourceKind",
        "status",
        "contentType",
        "sourceHash",
        "rawPath",
        "confidence",
        "matchedKeywords",
        "sourceLevel",
        "evidenceSnippet",
    ]
    write_csv(REPORTS_DIR / "school_discovery.csv", headers, all_rows)
    write_jsonl(REPORTS_DIR / "school_discovery.jsonl", all_rows)

    high_confidence = [row for row in all_rows if int(row.get("confidence") or 0) >= 8]
    write_csv(REPORTS_DIR / "school_discovery_high_confidence.csv", headers, high_confidence)
    print(f"wrote {REPORTS_DIR / 'school_discovery.csv'} rows={len(all_rows)}")
    print(f"wrote {REPORTS_DIR / 'school_discovery_high_confidence.csv'} rows={len(high_confidence)}")


if __name__ == "__main__":
    main()
