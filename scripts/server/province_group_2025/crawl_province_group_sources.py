#!/usr/bin/env python3
"""Collect 2025 professional-group source materials for SC/HB/AH.

The script only writes local raw/report artifacts. It never imports reviewed
rows into production tables.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import logging
import os
import re
import shlex
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parent
RAW_DIR = ROOT / "raw"
REPORTS_DIR = ROOT / "reports"
DEFAULT_ENV_FILE = "/etc/gzly/gzly.env"
USER_AGENT = "Mozilla/5.0 (compatible; GZLY-ProvinceGroupCrawler/1.0; +https://gzly.dongsiwei.com)"

LOGGER = logging.getLogger("province-group-crawler")


@dataclass(frozen=True)
class FetchResult:
    url: str
    final_url: str
    status: int
    content_type: str
    body: bytes
    error: str = ""


PROVINCES: dict[str, dict[str, Any]] = {
    "SC": {
        "name": "四川",
        "target_batch": "普通本科批B段",
        "official_hosts": ("sceea.cn",),
        "keywords": ("四川", "2025", "本科批B段", "本科批 B 段", "院校专业组", "调档线", "投档线", "招生计划", "一分一段"),
        "url_hints": ("sichuan", "sc", "2025", "lqfs", "lnfs", "fsx", "zsjh", "zsplan", "plan", "admission", "bkzs"),
        "official_sources": [
            {"dataType": "requirement", "subjectType": "", "batch": "", "title": "2025录取方案", "url": "https://www.sceea.cn/Html/202501/Newsdetail_4130.html"},
            {"dataType": "requirement", "subjectType": "", "batch": "", "title": "2025招生实施规定", "url": "https://www.sceea.cn/Html/202505/Newsdetail_4261.html"},
            {"dataType": "score_rank", "subjectType": "历史类", "batch": "", "title": "2025四川普通高考历史类一分一段", "url": "https://www.sceea.cn/Html/202506/Newsdetail_4334.html"},
            {"dataType": "score_rank", "subjectType": "物理类", "batch": "", "title": "2025四川普通高考物理类一分一段", "url": "https://www.sceea.cn/Html/202506/Newsdetail_4335.html"},
            {"dataType": "group_line", "subjectType": "", "batch": "普通本科批B段", "title": "2025普通本科批B段投档汇总新闻", "url": "https://www.sceea.cn/Html/202507/Newsdetail_4405.html"},
            {"dataType": "group_plan", "subjectType": "", "batch": "普通本科批B段", "title": "2025招生计划更正一", "url": "https://www.sceea.cn/Html/202506/Newsdetail_4330.html"},
            {"dataType": "group_plan", "subjectType": "", "batch": "普通本科批B段", "title": "2025招生计划更正二", "url": "https://www.sceea.cn/Html/202506/Newsdetail_4338.html"},
        ],
    },
    "HB": {
        "name": "湖北",
        "target_batch": "本科普通批",
        "official_hosts": ("hubei.gov.cn", "hbccks.cn", "jyt.hubei.gov.cn"),
        "keywords": ("湖北", "2025", "本科普通批", "普通本科批", "院校专业组", "投档线", "录取分数", "招生计划", "一分一段"),
        "url_hints": ("hubei", "hb", "2025", "yfyd", "lqfs", "fsx", "zsjh", "plan", "admission", "bkzs"),
        "official_sources": [
            {"dataType": "requirement", "subjectType": "", "batch": "本科普通批", "title": "2025年湖北省普通高校阳光招生政策暨志愿填报问答", "url": "http://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202506/t20250618_5697087.shtml"},
            {"dataType": "requirement", "subjectType": "", "batch": "本科普通批", "title": "湖北阳光招生政策问答PDF", "url": "https://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202506/P020250620581412805028.pdf"},
            {"dataType": "score_rank", "subjectType": "", "batch": "", "title": "湖北教育考试网一分一段列表", "url": "http://www.hbccks.cn/html/gkgzzt/yfyd/"},
            {"dataType": "score_rank", "subjectType": "物理类", "batch": "", "title": "湖北2025普通高考一分一段首选物理", "url": "http://www.hbccks.cn/html/yfyd/2025-06/142616.html"},
            {"dataType": "score_rank", "subjectType": "历史类", "batch": "", "title": "湖北2025普通高考一分一段首选历史", "url": "http://www.hbccks.cn/html/yfyd/2025-06/142617.html"},
        ],
    },
    "AH": {
        "name": "安徽",
        "target_batch": "普通本科批次",
        "official_hosts": ("ahzsks.cn", "anhuinews.com", "chsi.com.cn", "eol.cn"),
        "keywords": ("安徽", "2025", "普通本科批次", "普通本科批", "院校专业组", "投档线", "招生计划", "分档表", "一分一段"),
        "url_hints": ("anhui", "ah", "2025", "yfyd", "lqfs", "fsx", "zsjh", "plan", "admission", "bkzs"),
        "official_sources": [
            {"dataType": "requirement", "subjectType": "", "batch": "普通本科批次", "title": "安徽2025普通高校招生工作实施办法（阳光高考标注来源省考试院）", "url": "https://gaokao.chsi.com.cn/gkxx/zc/ss/202505/20250512/2293378850-8.html"},
            {"dataType": "requirement", "subjectType": "", "batch": "普通本科批次", "title": "安徽省2025年普通高校招生工作实施办法（教育在线转载）", "url": "https://gaokao.eol.cn/an_hui/dongtai/202505/t20250513_2668022.shtml"},
            {"dataType": "score_rank", "subjectType": "", "batch": "", "title": "2025年安徽高考一分一段表发布（中安在线标注来源省考试院）", "url": "http://edu.anhuinews.com/kszx/gk/gzdt/202506/t20250625_8581781.html"},
        ],
    },
}

STATIC_ASSET_SUFFIXES = {".css", ".js", ".ico", ".gif", ".svg", ".woff", ".woff2", ".ttf"}
DOWNLOAD_SUFFIXES = {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".jpg", ".jpeg", ".png", ".webp"}
BLOCKED_HOST_HINTS = ("eol.cn", "gaokao.cn", "youzy.cn", "dxsbb.com", "gk100.com", "hfplg.com", "gaosan.com", "zhiyuan")


def ensure_dirs() -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def safe_name(value: str, fallback: str = "item") -> str:
    text = urllib.parse.unquote(value or "").strip()
    text = re.sub(r"https?://", "", text, flags=re.I)
    text = re.sub(r"[^0-9A-Za-z._-]+", "_", text).strip("._-")
    return f"{(text or fallback)[:80]}_{sha256_text(value)[:10]}"


def normalize_url(base_url: str, href: str) -> str:
    href = html.unescape((href or "").strip())
    if not href or href.startswith(("javascript:", "mailto:", "tel:", "#")):
        return ""
    return urllib.parse.urljoin(base_url, href)


def host_of(url: str) -> str:
    return urllib.parse.urlparse(url).netloc.lower()


def iri_to_uri(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    path = urllib.parse.quote(parsed.path, safe="/%:@")
    query = urllib.parse.quote(parsed.query, safe="=&%:@/?+,;")
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, path, query, parsed.fragment))


def fetch_url(url: str, timeout: int = 18, retries: int = 2, max_bytes: int | None = None) -> FetchResult:
    request_url = iri_to_uri(url)
    last_error = ""
    for attempt in range(retries + 1):
        try:
            request = urllib.request.Request(request_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=timeout) as response:
                if max_bytes and max_bytes > 0:
                    body = response.read(max_bytes + 1)
                    if len(body) > max_bytes:
                        return FetchResult(url, response.geturl(), getattr(response, "status", 200), response.headers.get("Content-Type", ""), b"", f"content too large: >{max_bytes}")
                else:
                    body = response.read()
                return FetchResult(url, response.geturl(), getattr(response, "status", 200), response.headers.get("Content-Type", ""), body)
        except (urllib.error.URLError, TimeoutError, OSError, UnicodeError, ValueError) as exc:
            last_error = str(exc)
            if attempt < retries:
                time.sleep(0.6 * (attempt + 1))
    return FetchResult(url, url, 0, "", b"", last_error)


def decode_body(body: bytes, content_type: str = "") -> str:
    candidates = ["utf-8"]
    match = re.search(r"charset=([A-Za-z0-9_-]+)", content_type or "", re.I)
    if match:
        candidates.insert(0, match.group(1))
    candidates.extend(["gb18030", "gbk"])
    for encoding in candidates:
        try:
            return body.decode(encoding)
        except UnicodeDecodeError:
            continue
    return body.decode("utf-8", errors="replace")


def strip_html_text(text: str) -> str:
    text = re.sub(r"(?is)<script.*?</script>|<style.*?</style>", " ", text or "")
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def extract_links(text: str, base_url: str) -> list[str]:
    links: list[str] = []
    for pattern in (r"""(?i)(?:href|src)\s*=\s*['"]([^'"]+)['"]""", r"""(?i)url\(['"]?([^)'"]+)['"]?\)"""):
        for raw in re.findall(pattern, text or ""):
            url = normalize_url(base_url, raw)
            if url and url not in links:
                links.append(url)
    return links


def content_snippet(text: str, keywords: Iterable[str], window: int = 160) -> str:
    plain = strip_html_text(text)
    lower = plain.lower()
    for keyword in keywords:
        idx = lower.find(keyword.lower())
        if idx >= 0:
            return plain[max(0, idx - window): min(len(plain), idx + len(keyword) + window)]
    return plain[: window * 2]


def looks_like_download(url: str, content_type: str = "") -> bool:
    suffix = Path(urllib.parse.urlparse(url).path).suffix.lower()
    return suffix in DOWNLOAD_SUFFIXES or any(token in content_type.lower() for token in ("pdf", "image/", "msword", "spreadsheet"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def write_csv(path: Path, headers: list[str], rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in headers})


def save_fetch_result(result: FetchResult, directory: Path, prefix: str) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    suffix = Path(urllib.parse.urlparse(result.final_url or result.url).path).suffix
    if not suffix:
        suffix = ".html" if "html" in result.content_type.lower() or "text" in result.content_type.lower() else ".bin"
    path = directory / f"{prefix}_{safe_name(result.final_url or result.url)}{suffix}"
    path.write_bytes(result.body)
    return path


def allowed_official_link(province: dict[str, Any], url: str) -> bool:
    host = host_of(url)
    return any(host == item or host.endswith(f".{item}") for item in province["official_hosts"])


def text_score(province: dict[str, Any], text: str) -> tuple[int, list[str]]:
    plain = strip_html_text(text)
    matched = [keyword for keyword in province["keywords"] if keyword in plain]
    score = len(matched)
    if province["name"] in matched and "2025" in matched:
        score += 4
    if province["target_batch"] in matched:
        score += 6
    if "院校专业组" in matched:
        score += 3
    return score, matched


def url_score(province: dict[str, Any], url: str) -> int:
    lower = url.lower()
    suffix = Path(urllib.parse.urlparse(lower).path).suffix
    if suffix in STATIC_ASSET_SUFFIXES:
        return -1
    score = sum(1 for hint in province["url_hints"] if hint.lower() in lower)
    if looks_like_download(url) and score > 0:
        score += 2
    return score


def run_official(province_code: str, download_children: bool, discover_limit: int) -> None:
    province = PROVINCES[province_code]
    records: list[dict[str, Any]] = []
    discovered: list[dict[str, Any]] = []
    for source in province["official_sources"]:
        result = fetch_url(source["url"])
        record: dict[str, Any] = {
            **source,
            "provinceCode": province_code,
            "provinceName": province["name"],
            "status": result.status,
            "finalUrl": result.final_url,
            "contentType": result.content_type,
            "error": result.error,
            "sourceHash": sha256_bytes(result.body) if result.body else "",
            "rawPath": "",
            "children": [],
        }
        if result.body:
            raw_path = save_fetch_result(result, RAW_DIR / province_code / "official_pages" / source["dataType"], source["dataType"])
            record["rawPath"] = str(raw_path)
            text = decode_body(result.body, result.content_type)
            record["snippet"] = content_snippet(text, province["keywords"])
            for link in extract_links(text, result.final_url or source["url"]):
                if not allowed_official_link(province, link):
                    continue
                child = {"url": link, "dataType": source["dataType"], "downloaded": False, "status": "", "error": "", "rawPath": "", "sourceHash": ""}
                if download_children and looks_like_download(link):
                    child_result = fetch_url(link)
                    child["status"] = child_result.status
                    child["error"] = child_result.error
                    if child_result.body:
                        child_path = save_fetch_result(child_result, RAW_DIR / province_code / "official_downloads" / source["dataType"], source["dataType"])
                        child["downloaded"] = True
                        child["rawPath"] = str(child_path)
                        child["sourceHash"] = sha256_bytes(child_result.body)
                record["children"].append(child)
        records.append(record)

    seen_urls: set[str] = set()
    for record in records:
        for child in record.get("children", []):
            url = child.get("url", "")
            if url and url not in seen_urls and url_score(province, url) > 0:
                seen_urls.add(url)
    for url in list(seen_urls)[:discover_limit]:
        result = fetch_url(url, retries=1, max_bytes=1_500_000)
        item: dict[str, Any] = {"provinceCode": province_code, "url": url, "status": result.status, "error": result.error, "matched": False}
        if result.body:
            text = decode_body(result.body, result.content_type)
            score, matched = text_score(province, text)
            item.update({"contentType": result.content_type, "sourceHash": sha256_bytes(result.body), "matched": score >= 5, "score": score, "matchedKeywords": matched, "snippet": content_snippet(text, province["keywords"])})
        discovered.append(item)

    report_dir = REPORTS_DIR / province_code
    write_json(report_dir / "official_source_audit.json", {"officialSources": records, "discoveredPages": discovered})
    flat_rows = []
    for record in records:
        flat_rows.append({key: record.get(key, "") for key in ("provinceCode", "dataType", "subjectType", "batch", "title", "url", "status", "contentType", "sourceHash", "rawPath", "error")})
    write_csv(report_dir / "official_source_audit.csv", ["provinceCode", "dataType", "subjectType", "batch", "title", "url", "status", "contentType", "sourceHash", "rawPath", "error"], flat_rows)
    markdown = [f"# {province['name']} 2025 官方来源抓取报告", ""]
    for record in records:
        markdown.append(f"- {record['title']}：HTTP {record.get('status')}，子链接 {len(record.get('children', []))}，hash `{record.get('sourceHash', '')[:16]}`")
    matched_count = sum(1 for item in discovered if item.get("matched"))
    markdown.extend(["", f"站内关联候选：{matched_count} 条高相关。", ""])
    (report_dir / "official_source_audit.md").write_text("\n".join(markdown), encoding="utf-8")
    LOGGER.info("%s official done: sources=%d discovered=%d", province_code, len(records), len(discovered))


def mysql_query(sql: str, env_file: str) -> list[list[str]]:
    command = f"""
set -a
source {shlex.quote(env_file)}
set +a
export MYSQL_PWD="${{GZLY_DB_PASSWORD:-}}"
mysql -N -B -h"${{GZLY_DB_HOST:-127.0.0.1}}" -P"${{GZLY_DB_PORT:-3306}}" -u"${{GZLY_DB_USERNAME:-root}}" "${{GZLY_DB_NAME:-gzly}}" -e {shlex.quote(sql)}
"""
    completed = subprocess.run(["bash", "-lc", command], text=True, capture_output=True, check=True, timeout=60)
    return [line.split("\t") for line in completed.stdout.splitlines() if line.strip()]


def load_school_seeds(env_file: str, limit: int, offset: int) -> list[dict[str, str]]:
    sql = f"""
SELECT
  u.school_id,
  u.name,
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
    seeds = []
    for row in mysql_query(sql, env_file):
        while len(row) < 7:
            row.append("")
        seeds.append({
            "schoolId": row[0],
            "universityName": row[1],
            "schoolSite": row[2],
            "admissionSite": row[3],
            "admissionBrochureUrl": row[4],
            "majorCatalogUrl": row[5],
            "tuitionInfoUrl": row[6],
        })
    return seeds


def is_blocked_candidate(url: str) -> bool:
    host = host_of(url)
    return any(hint in host for hint in BLOCKED_HOST_HINTS)


def seed_urls(seed: dict[str, str]) -> list[str]:
    urls: list[str] = []
    for key in ("admissionSite", "majorCatalogUrl", "admissionBrochureUrl", "tuitionInfoUrl", "schoolSite"):
        value = seed.get(key, "").strip()
        if value.startswith(("http://", "https://")) and not is_blocked_candidate(value) and value not in urls:
            urls.append(value)
    return urls


def inspect_school_page(province_code: str, seed: dict[str, str], url: str, source_kind: str, max_pages: int, timeout: int, max_bytes: int) -> list[dict[str, Any]]:
    province = PROVINCES[province_code]
    rows: list[dict[str, Any]] = []
    queue = [(url, source_kind)]
    visited: set[str] = set()
    while queue and len(visited) < max_pages:
        page_url, page_kind = queue.pop(0)
        if page_url in visited or is_blocked_candidate(page_url):
            continue
        visited.add(page_url)
        result = fetch_url(page_url, timeout=timeout, retries=1, max_bytes=max_bytes)
        row: dict[str, Any] = {
            "provinceCode": province_code,
            "schoolId": seed["schoolId"],
            "universityName": seed["universityName"],
            "url": page_url,
            "sourceKind": page_kind,
            "status": result.status,
            "error": result.error,
            "score": 0,
            "matchedKeywords": "",
            "rawPath": "",
            "sourceHash": "",
            "snippet": "",
        }
        if result.body:
            row["sourceHash"] = sha256_bytes(result.body)
            text = decode_body(result.body, result.content_type)
            score, matched = text_score(province, text)
            row["score"] = score
            row["matchedKeywords"] = "|".join(matched)
            row["snippet"] = content_snippet(text, province["keywords"])
            if score >= 5 or looks_like_download(page_url, result.content_type):
                raw_path = save_fetch_result(result, RAW_DIR / province_code / "school_pages" / seed["schoolId"], page_kind)
                row["rawPath"] = str(raw_path)
            for link in extract_links(text, result.final_url or page_url):
                if len(visited) + len(queue) >= max_pages:
                    break
                if is_blocked_candidate(link):
                    continue
                same_host = host_of(link) == host_of(page_url)
                if same_host and url_score(province, link) > 0 and link not in visited:
                    queue.append((link, "linked_page"))
        rows.append(row)
    return rows


def classify_queue_row(row: dict[str, Any]) -> str:
    score = int(row.get("score") or 0)
    keywords = row.get("matchedKeywords", "")
    if score >= 12 and ("院校专业组" in keywords or "招生计划" in keywords or "投档线" in keywords):
        return "ready_for_manual_extraction"
    if score >= 6:
        return "needs_manual_check"
    return "reject_or_low_priority"


def run_schools(province_code: str, env_file: str, limit: int, offset: int, workers: int, max_pages: int, timeout: int, max_bytes: int) -> None:
    seeds = load_school_seeds(env_file, limit, offset)
    tasks = []
    rows: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=max(1, workers)) as executor:
        for seed in seeds:
            for url in seed_urls(seed):
                tasks.append(executor.submit(inspect_school_page, province_code, seed, url, "seed_page", max_pages, timeout, max_bytes))
        for future in as_completed(tasks):
            rows.extend(future.result())
    rows.sort(key=lambda item: (-int(item.get("score") or 0), item.get("universityName", ""), item.get("url", "")))
    for row in rows:
        row["reviewClass"] = classify_queue_row(row)
    report_dir = REPORTS_DIR / province_code
    headers = ["provinceCode", "schoolId", "universityName", "reviewClass", "score", "matchedKeywords", "url", "sourceKind", "status", "sourceHash", "rawPath", "snippet", "error"]
    write_csv(report_dir / "school_candidate_review_queue.csv", headers, rows)
    write_json(report_dir / "school_candidate_summary.json", {
        "provinceCode": province_code,
        "provinceName": PROVINCES[province_code]["name"],
        "seedCount": len(seeds),
        "pageCount": len(rows),
        "reviewClassCounts": {name: sum(1 for row in rows if row.get("reviewClass") == name) for name in ("ready_for_manual_extraction", "needs_manual_check", "reject_or_low_priority")},
    })
    LOGGER.info("%s schools done: seeds=%d pages=%d", province_code, len(seeds), len(rows))


def selected_provinces(value: str) -> list[str]:
    if value.upper() == "ALL":
        return ["SC", "HB", "AH"]
    codes = [item.strip().upper() for item in value.split(",") if item.strip()]
    unsupported = [code for code in codes if code not in PROVINCES]
    if unsupported:
        raise ValueError(f"Unsupported province: {','.join(unsupported)}")
    return codes


def main() -> int:
    parser = argparse.ArgumentParser(description="Collect SC/HB/AH 2025 professional-group sources.")
    parser.add_argument("command", choices=("official", "schools", "all"))
    parser.add_argument("--province", default="ALL", help="SC, HB, AH, comma-separated list, or ALL")
    parser.add_argument("--env-file", default=os.environ.get("GZLY_ENV_FILE", DEFAULT_ENV_FILE))
    parser.add_argument("--download-children", action="store_true")
    parser.add_argument("--discover-limit", type=int, default=40)
    parser.add_argument("--limit", type=int, default=80)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--max-pages-per-school", type=int, default=6)
    parser.add_argument("--timeout", type=int, default=12)
    parser.add_argument("--max-bytes", type=int, default=1_200_000)
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    ensure_dirs()
    for province_code in selected_provinces(args.province):
        if args.command in ("official", "all"):
            run_official(province_code, args.download_children, args.discover_limit)
        if args.command in ("schools", "all"):
            run_schools(province_code, args.env_file, args.limit, args.offset, args.workers, args.max_pages_per_school, args.timeout, args.max_bytes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
