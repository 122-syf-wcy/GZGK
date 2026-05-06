#!/usr/bin/env python3
"""
Scrape official admission entry links for universities.

Target fields:
  - school_site
  - admission_site
  - admission_brochure_url
  - major_catalog_url
  - tuition_info_url

Design:
  - Fetch university seeds from public GZLY API
  - Use Playwright with optional proxy rotation
  - Search official homepage first, then admission page
  - Export JSON + SQL for `uni_official_link`

Usage:
  python scrape_official_links.py
  python scrape_official_links.py --workers 4 --limit 200
  python scrape_official_links.py --resume 100
  python scrape_official_links.py --proxy http://127.0.0.1:7890,http://127.0.0.1:7891
  python scrape_official_links.py --base-url https://gzly.dongsiwei.com
"""

import argparse
import asyncio
import json
import os
import re
import subprocess
import time
from io import BytesIO
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup
from playwright.async_api import async_playwright, Browser, Page
from pypdf import PdfReader

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
CHECKPOINT_DIR = os.path.join(DATA_DIR, "official_links")
os.makedirs(EXPORT_DIR, exist_ok=True)
os.makedirs(CHECKPOINT_DIR, exist_ok=True)

CHECKPOINT_JSON = os.path.join(CHECKPOINT_DIR, "official_links_checkpoint.json")
RESULT_JSON = os.path.join(CHECKPOINT_DIR, "official_links_results.json")
RESULT_SQL = os.path.join(EXPORT_DIR, "official_links.sql")
SEED_JSON = os.path.join(CHECKPOINT_DIR, "university_seeds.json")
SYNC_FILES = [
    ("official_links_checkpoint.json", CHECKPOINT_JSON),
    ("official_links_results.json", RESULT_JSON),
    ("official_links.sql", RESULT_SQL),
]

KEYWORDS = {
    "admission_site": [
        "招生网", "招生信息网", "本科招生", "招生信息", "招生就业", "admission", "admissions", "zsb"
    ],
    "admission_brochure_url": [
        "招生章程", "章程", "招生简章", "admission brochure"
    ],
    "major_catalog_url": [
        "专业目录", "招生专业", "本科专业", "专业介绍", "专业设置", "major"
    ],
    "tuition_info_url": [
        "收费标准", "收费", "学费", "收费公示", "tuition", "fee"
    ],
}

NEGATIVE_KEYWORDS = {
    "tuition_info_url": ["奖学金", "scholarship", "资助", "助学金", "奖助", "助学"],
    "major_catalog_url": ["新闻", "动态", "公告", "学院新闻"],
}

TUITION_SUMMARY_KEYWORDS = ["学费", "收费标准", "收费", "住宿费", "元/学年", "元/年", "每学年", "每年"]
MAJOR_HINT_KEYWORDS = ["专业目录", "招生专业", "本科专业", "专业设置", "开设专业", "专业列表", "招生专业一览"]
MAJOR_STOPWORDS = {
    "招生专业", "本科专业", "专业目录", "专业设置", "专业介绍", "招生章程", "学校简介",
    "招生信息", "专业概览", "专业列表", "学院设置", "本科招生", "招生计划", "专业备注",
}
MAJOR_SUFFIXES = (
    "科学与技术", "数据科学", "工程", "技术", "医学", "管理", "设计", "法学", "文学",
    "理学", "教育", "统计学", "建筑学", "护理学", "药学", "农学", "经济学", "艺术学",
    "传播学", "自动化", "信息工程", "会计学", "金融学", "语言文学", "商务英语",
    "汉语言文学", "数学与应用数学", "物理学", "化学", "生物科学", "智能科学", "网络工程",
)

USER_AGENTS = [
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
]

URL_FIELDS = (
    "school_site",
    "admission_site",
    "admission_brochure_url",
    "major_catalog_url",
    "tuition_info_url",
)
MAX_URL_LENGTH = 500
UNSAFE_URL_PREFIXES = ("javascript:", "mailto:", "tel:", "sms:", "data:", "#")

lock = asyncio.Lock()
completed = 0
results = []
checkpoint_every = 20
requests.packages.urllib3.disable_warnings()


def load_checkpoint():
    if not os.path.exists(CHECKPOINT_JSON):
        return []
    try:
        with open(CHECKPOINT_JSON, "r", encoding="utf-8") as f:
            data = json.load(f)
        rows = data if isinstance(data, list) else []
        return dedupe_rows(rows)
    except Exception:
        return []


def dedupe_rows(rows: list[dict]):
    if not rows:
        return []
    deduped = {}
    for row in rows:
        school_id = str(row.get("school_id") or "")
        if school_id:
            deduped[school_id] = row
    return list(deduped.values())


def normalize_url(url: str) -> str:
    return sanitize_http_url(url)


def sanitize_http_url(url: str) -> str:
    if not url:
        return ""
    value = str(url).strip().strip("\"'")
    if not value:
        return ""

    lower = value.lower()
    if lower.startswith(UNSAFE_URL_PREFIXES):
        return ""
    if value.startswith("//"):
        value = "https:" + value
    elif lower.startswith("www."):
        value = "https://" + value
    elif not lower.startswith(("http://", "https://")):
        if value.startswith("/"):
            return ""
        if re.match(r"^[a-z0-9][a-z0-9.-]*\.[a-z]{2,}([/:?#].*)?$", value, re.I):
            value = "https://" + value
        else:
            return ""

    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return ""
    if len(value) > MAX_URL_LENGTH:
        compact = parsed._replace(query="", fragment="").geturl()
        compact_parsed = urlparse(compact)
        if compact_parsed.path and compact_parsed.path != "/" and len(compact) <= MAX_URL_LENGTH:
            return compact
        return ""
    return value


def same_domain(base: str, target: str) -> bool:
    try:
        base = sanitize_http_url(base)
        target = sanitize_http_url(target)
        b = urlparse(base).netloc.replace("www.", "")
        t = urlparse(target).netloc.replace("www.", "")
        return bool(b and t) and (b == t or t.endswith("." + b) or b.endswith("." + t))
    except Exception:
        return False


def keyword_score(text: str, kind: str) -> int:
    value = text.lower()
    score = 0
    for idx, kw in enumerate(KEYWORDS[kind]):
        if kw.lower() in value:
            score += 100 - idx * 7
    return score


def negative_score(text: str, kind: str) -> int:
    value = text.lower()
    penalties = 0
    for kw in NEGATIVE_KEYWORDS.get(kind, []):
        if kw.lower() in value:
            penalties += 80
    return penalties


def esc_sql(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"


def url_update_clause(field: str) -> str:
    return (
        f"`{field}`=CASE "
        f"WHEN COALESCE(NULLIF(VALUES(`{field}`),''),NULL) IS NOT NULL "
        f"AND (`{field}` IS NULL OR `{field}`='' OR `{field}` NOT REGEXP '^https?://') "
        f"THEN VALUES(`{field}`) "
        f"ELSE COALESCE(NULLIF(VALUES(`{field}`),''),`{field}`) END,"
    )


def sync_outputs_to_remote():
    host = os.getenv("OFFICIAL_LINK_SYNC_HOST", "").strip()
    user = os.getenv("OFFICIAL_LINK_SYNC_USER", "root").strip()
    port = os.getenv("OFFICIAL_LINK_SYNC_PORT", "22").strip()
    key = os.getenv("OFFICIAL_LINK_SYNC_KEY", "").strip()
    remote_dir = os.getenv("OFFICIAL_LINK_SYNC_REMOTE_DIR", "").strip()
    if not host or not remote_dir:
        return

    strict_host_key_checking = os.getenv("OFFICIAL_LINK_SYNC_STRICT_HOST_KEY_CHECKING", "accept-new").strip() or "accept-new"
    base_cmd = ["scp", "-q", "-o", f"StrictHostKeyChecking={strict_host_key_checking}", "-P", port]
    if key:
        base_cmd.extend(["-i", key])

    for _, local_path in SYNC_FILES:
        if not os.path.exists(local_path):
            continue
        remote_path = f"{user}@{host}:{remote_dir.rstrip('/')}/{os.path.basename(local_path)}"
        try:
            subprocess.run(base_cmd + [local_path, remote_path], check=True, timeout=60)
        except Exception as exc:
            print(f"[sync] failed to upload {local_path} -> {remote_path}: {exc}", flush=True)


def split_sentences(text: str) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text or "").strip()
    if not cleaned:
        return []
    return [part.strip() for part in re.split(r"[。！？；\n\r]+", cleaned) if len(part.strip()) >= 6]


def extract_text_fragments(text: str, min_len: int = 6, max_len: int = 160) -> list[str]:
    cleaned = (text or "").replace("\u3000", " ").replace("\xa0", " ")
    fragments = []
    for raw in re.split(r"[\n\r]+", cleaned):
        line = re.sub(r"\s+", " ", raw).strip(" \t-•·▪●◆■*|")
        if not line:
            continue
        if re.search(r"[。！？；]", line):
            sentence_parts = [part.strip() for part in re.split(r"[。！？；]", line) if part.strip()]
            added = False
            for part in sentence_parts:
                if min_len <= len(part) <= max_len:
                    fragments.append(part)
                    added = True
            if added:
                continue
        if min_len <= len(line) <= max_len:
            fragments.append(line)
            continue
        if len(line) > max_len:
            for part in re.split(r"[。！？；]", line):
                part = part.strip()
                if min_len <= len(part) <= max_len:
                    fragments.append(part)
    for sentence in split_sentences(cleaned):
        if min_len <= len(sentence) <= max_len:
            fragments.append(sentence)

    result = []
    seen = set()
    for fragment in fragments:
        if fragment in seen:
            continue
        seen.add(fragment)
        result.append(fragment)
    return result


def pick_sentences(text: str, keywords: list[str], limit: int = 2, max_len: int = 260) -> str:
    seen = []
    for fragment in extract_text_fragments(text, min_len=6, max_len=140):
        if any(keyword in fragment for keyword in keywords):
            if fragment not in seen:
                seen.append(fragment)
        if len(seen) >= limit:
            break
    result = "；".join(seen)
    return result[:max_len]


def looks_like_major_name(token: str) -> bool:
    value = token.strip("：:、，,；;（）() ")
    if len(value) < 2 or len(value) > 24:
        return False
    if value in MAJOR_STOPWORDS:
        return False
    if any(keyword in value for keyword in ["学校", "学院", "招生", "目录", "章程", "培养", "学费", "备注", "条件", "要求"]):
        return False
    if re.search(r"\d", value):
        return False
    return value.endswith(MAJOR_SUFFIXES) or ("与" in value and len(value) <= 14)


def split_major_candidates(raw: str) -> list[str]:
    normalized = re.sub(r"^\d+[.、]\s*", "", raw.strip())
    normalized = re.sub(r"[：:]\s*", "：", normalized)
    if "：" in normalized:
        normalized = normalized.split("：", 1)[1]
    parts = re.split(r"[、,，；;/｜|]", normalized)
    result = []
    for part in parts:
        candidate = re.sub(r"\s+", "", part).strip("（）() ")
        if looks_like_major_name(candidate):
            result.append(candidate)
    return result


def extract_tuition_summary(text: str) -> str:
    if not text:
        return ""
    amount_pattern = re.compile(r"(?:人民币)?\s?\d{3,6}(?:\.\d{1,2})?\s*元(?:/学年|/年|/学期|每学年|每年)?")
    picked = []
    seen = set()
    for fragment in extract_text_fragments(text, min_len=4, max_len=140):
        score = 0
        if any(keyword in fragment for keyword in TUITION_SUMMARY_KEYWORDS):
            score += 2
        if amount_pattern.search(fragment):
            score += 3
        if any(keyword in fragment for keyword in NEGATIVE_KEYWORDS.get("tuition_info_url", [])):
            score -= 3
        if score <= 0:
            continue
        if fragment not in seen:
            seen.add(fragment)
            picked.append(fragment)
        if len(picked) >= 3:
            break
    if picked:
        return "；".join(picked)[:260]
    return pick_sentences(text, TUITION_SUMMARY_KEYWORDS, limit=3)


def extract_major_summary(text: str) -> str:
    if not text:
        return ""
    majors = []
    seen = set()

    for fragment in extract_text_fragments(text, min_len=4, max_len=140):
        if not any(keyword in fragment for keyword in MAJOR_HINT_KEYWORDS):
            continue
        for candidate in split_major_candidates(fragment):
            if candidate not in seen:
                seen.add(candidate)
                majors.append(candidate)
            if len(majors) >= 8:
                break
        if len(majors) >= 8:
            break

    if not majors:
        pattern = r"[\u4e00-\u9fa5A-Za-z（）()·]{2,24}(?:" + "|".join(map(re.escape, MAJOR_SUFFIXES)) + r")"
        candidates = re.findall(pattern, text)
        for candidate in candidates:
            candidate = candidate.strip("：:、，, ")
            if not looks_like_major_name(candidate):
                continue
            if candidate not in seen:
                seen.add(candidate)
                majors.append(candidate)
            if len(majors) >= 8:
                break
    if majors:
        return "、".join(majors)
    return pick_sentences(text, MAJOR_HINT_KEYWORDS, limit=2)


def fetch_document_text(url: str):
    url = sanitize_http_url(url)
    if not url:
        return "", ""
    try:
        response = requests.get(
            url,
            headers={"User-Agent": USER_AGENTS[0]},
            timeout=20,
            verify=False,
            allow_redirects=True,
        )
        response.raise_for_status()
        content_type = (response.headers.get("Content-Type") or "").lower()
        if "pdf" in content_type or url.lower().endswith(".pdf"):
            reader = PdfReader(BytesIO(response.content))
            text = "\n".join(page.extract_text() or "" for page in reader.pages[:10])
            return text, "pdf"

        response.encoding = response.apparent_encoding or response.encoding
        soup = BeautifulSoup(response.text, "html.parser")
        for tag in soup(["script", "style", "noscript"]):
            tag.decompose()
        text = soup.get_text("\n")
        return text, "html"
    except Exception:
        return "", ""


def looks_article_like(url: str) -> bool:
    path = (urlparse(url).path or "").lower()
    return bool(
        re.search(r"/\d+\.html?$", path)
        or re.search(r"/info/\d+", path)
        or re.search(r"/content/.+\.(html|htm)$", path)
        or re.search(r"/article/.+\.(html|htm)$", path)
        or re.search(r"[?&](id|dataid|articleid|newsid)=\w+", url.lower())
    )


def looks_listing_text(text: str) -> bool:
    if not text:
        return False
    sample = text[:1600]
    date_hits = len(re.findall(r"20\d{2}[-/.年]\d{1,2}", sample))
    nav_hits = sum(1 for keyword in ["当前位置", "栏目导航", "上一页", "下一页", "招生简章", "通知公告"] if keyword in sample)
    return date_hits >= 3 or nav_hits >= 3


async def fetch_page_text(page: Page, url: str):
    url = sanitize_http_url(url)
    if not url:
        return "", ""
    try:
        if url.lower().endswith(".pdf"):
            return fetch_document_text(url)
        await page.goto(url, wait_until="domcontentloaded", timeout=15000)
        await page.wait_for_timeout(1200)
        text = await page.evaluate(
            """() => {
                const body = document.body;
                return body ? (body.innerText || body.textContent || '') : '';
            }"""
        )
        if text and text.strip():
            return text, "playwright-html"
        return fetch_document_text(url)
    except Exception:
        return fetch_document_text(url)


async def resolve_detail_url(page: Page, current_url: str, kind: str):
    if not current_url:
        return "", "", ""
    text, source = await fetch_page_text(page, current_url)
    if kind == "admission_site":
        return current_url, text, source

    links = await collect_links(page, current_url)
    best_inner = pick_best_link(current_url, links, kind)
    if not best_inner or best_inner == current_url:
        return current_url, text, source

    current_score = keyword_score(f"{current_url} {text}", kind) - negative_score(f"{current_url} {text}", kind)
    inner_score = keyword_score(best_inner, kind) - negative_score(best_inner, kind)
    if looks_article_like(best_inner):
        inner_score += 40

    should_follow = looks_listing_text(text) or current_score <= 0 or inner_score > current_score + 10
    if not should_follow:
        return current_url, text, source

    inner_text, inner_source = await fetch_page_text(page, best_inner)
    if not inner_text.strip():
        return current_url, text, source
    return best_inner, inner_text, inner_source


async def enrich_parsed_fields(page: Page, result: dict):
    cache = {}

    async def load(url: str):
        if not url:
            return "", ""
        if url in cache:
            return cache[url]
        cache[url] = await fetch_page_text(page, url)
        return cache[url]

    brochure_url, brochure_text, brochure_source = await resolve_detail_url(
        page,
        result.get("admission_brochure_url") or result.get("admission_site") or result.get("school_site"),
        "admission_brochure_url",
    )
    catalog_url, catalog_text, catalog_source = await resolve_detail_url(
        page,
        result.get("major_catalog_url") or result.get("admission_site") or result.get("school_site"),
        "major_catalog_url",
    )
    tuition_url, tuition_text, tuition_source = await resolve_detail_url(
        page,
        result.get("tuition_info_url") or result.get("admission_brochure_url") or result.get("admission_site") or result.get("school_site"),
        "tuition_info_url",
    )

    if brochure_url:
        result["admission_brochure_url"] = brochure_url
    if catalog_url:
        result["major_catalog_url"] = catalog_url
    if tuition_url:
        result["tuition_info_url"] = tuition_url

    result["tuition_summary"] = extract_tuition_summary(tuition_text)
    result["major_catalog_summary"] = extract_major_summary(catalog_text)
    result["adjustment_rule"] = pick_sentences(
        brochure_text,
        ["调剂", "服从专业调剂", "专业调剂", "调剂录取"],
        limit=2,
    )
    result["foreign_language_rule"] = pick_sentences(
        brochure_text,
        ["外语", "语种", "英语语种", "口语考试", "语种要求"],
        limit=2,
    )
    result["physical_exam_rule"] = pick_sentences(
        brochure_text,
        ["体检", "色盲", "色弱", "视力", "身体条件", "身高", "健康状况"],
        limit=2,
    )
    result["single_subject_rule"] = pick_sentences(
        brochure_text,
        ["单科", "数学成绩", "英语成绩", "语文成绩", "外语成绩", "单科成绩"],
        limit=2,
    )
    result["parser_notes"] = (
        f"brochure:{brochure_source or 'none'};"
        f"catalog:{catalog_source or 'none'};"
        f"tuition:{tuition_source or 'none'}"
    )
    result["parse_status"] = 1 if any([
        result["tuition_summary"],
        result["major_catalog_summary"],
        result["adjustment_rule"],
        result["foreign_language_rule"],
        result["physical_exam_rule"],
        result["single_subject_rule"],
    ]) else 2
    result["last_parsed_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    return result


def is_structured_done(row: dict) -> bool:
    return row.get("parse_status") == 1


def fetch_university_seeds(base_url: str, seed_file: str | None = None):
    if seed_file and os.path.exists(seed_file):
        with open(seed_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        return [row for row in data if row.get("school_id")]

    url = f"{base_url.rstrip('/')}/api/university/list"
    seeds = []
    page = 1
    page_size = 100
    total = None

    while total is None or (page - 1) * page_size < total:
        response = requests.get(url, params={"page": page, "pageSize": page_size}, timeout=20)
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data", {}) or {}
        items = data.get("items", [])
        total = data.get("total", total if total is not None else 0)
        if not items:
            break
        for item in items:
            site = normalize_url(item.get("schoolSite") or "")
            seeds.append({
                "school_id": str(item.get("schoolId") or ""),
                "name": item.get("name") or "",
                "school_site": site,
                "province": item.get("province") or "",
                "city": item.get("city") or "",
            })
        page += 1

    return [s for s in seeds if s["school_id"]]


async def collect_links(page: Page, start_url: str):
    collected = []

    async def snapshot():
        return await page.evaluate(
            """() => Array.from(document.querySelectorAll('a'))
                .map(a => ({
                    text: (a.innerText || a.textContent || '').trim(),
                    href: a.href || '',
                    title: (a.getAttribute('title') || '').trim()
                }))
                .filter(a => a.href)"""
        )

    try:
        links = await snapshot()
        collected.extend(links)
    except Exception:
        pass

    return collected


def pick_best_link(base_url: str, links: list[dict], kind: str) -> str:
    candidates = []
    for link in links:
        href = sanitize_http_url(link.get("href") or "")
        if not href:
            continue
        text = " ".join(filter(None, [link.get("text"), link.get("title"), href]))
        base_score = keyword_score(text, kind) - negative_score(text, kind)
        if base_score <= 0:
            continue
        score = base_score
        if same_domain(base_url, href):
            score += 50
        if href.lower().endswith((".jpg", ".png", ".gif", ".svg", ".mp4")):
            continue
        if kind != "admission_site":
            path = urlparse(href).path or ""
            depth = len([part for part in path.split("/") if part])
            score += min(depth * 4, 20)
            if href.rstrip("/") == base_url.rstrip("/"):
                score -= 30
            if path.endswith("/") or path.endswith("index.htm") or path.endswith("index.html"):
                score -= 12
            if re.search(r"/20\d{2}|/content|/article|/info|/show|/news|/\d+\.html|/\d+\.htm", path.lower()):
                score += 18
        candidates.append((score, href))

    if not candidates:
        return ""
    candidates.sort(key=lambda item: item[0], reverse=True)
    return candidates[0][1]


async def scrape_school(page: Page, seed: dict):
    school_site = seed.get("school_site") or ""
    if not school_site:
        return {
            "school_id": seed["school_id"],
            "school_name": seed["name"],
            "source_domain": "",
            "school_site": "",
            "admission_site": "",
            "admission_brochure_url": "",
            "major_catalog_url": "",
            "tuition_info_url": "",
            "tuition_remark": "",
            "capture_method": "scraper",
            "capture_status": 0,
        }

    result = {
        "school_id": seed["school_id"],
        "school_name": seed["name"],
        "source_domain": urlparse(school_site).netloc,
        "school_site": school_site,
        "admission_site": "",
        "admission_brochure_url": "",
        "major_catalog_url": "",
        "tuition_info_url": "",
        "tuition_remark": "",
        "capture_method": "scraper",
        "capture_status": 0,
    }

    try:
        await page.goto(school_site, wait_until="domcontentloaded", timeout=15000)
        await page.wait_for_timeout(1800)
    except Exception:
        result["tuition_remark"] = "官网访问失败，需人工核验"
        result["capture_status"] = 2
        return result

    links = await collect_links(page, school_site)
    admission_site = pick_best_link(school_site, links, "admission_site")
    result["admission_site"] = admission_site

    secondary_links = links
    if admission_site:
        try:
            await page.goto(admission_site, wait_until="domcontentloaded", timeout=12000)
            await page.wait_for_timeout(1500)
            secondary_links = await collect_links(page, admission_site)
        except Exception:
            secondary_links = links

    merged_links = links + secondary_links
    result["admission_brochure_url"] = pick_best_link(admission_site or school_site, merged_links, "admission_brochure_url")
    result["major_catalog_url"] = pick_best_link(admission_site or school_site, merged_links, "major_catalog_url")
    result["tuition_info_url"] = pick_best_link(admission_site or school_site, merged_links, "tuition_info_url")

    filled = [
        result["school_site"],
        result["admission_site"],
        result["admission_brochure_url"],
        result["major_catalog_url"],
        result["tuition_info_url"],
    ]
    filled_count = len([x for x in filled if x])
    if filled_count >= 4:
        result["capture_status"] = 1
    elif filled_count >= 2:
        result["capture_status"] = 2
        result["tuition_remark"] = "已抓到部分官方入口，建议人工补齐缺失项"
    else:
        result["capture_status"] = 0
        result["tuition_remark"] = "仅抓到学校官网，需继续补充招生网/章程/收费标准"

    return await enrich_parsed_fields(page, result)


async def save_checkpoint():
    async with lock:
        payload = dedupe_rows(list(results))
        results[:] = payload
    with open(CHECKPOINT_JSON, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    with open(RESULT_JSON, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    export_sql(payload, RESULT_SQL)
    sync_outputs_to_remote()


def export_sql(rows: list[dict], output_path: str):
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(f"-- official links scraped at {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")
        for row in rows:
            row = dict(row)
            for field in URL_FIELDS:
                row[field] = sanitize_http_url(row.get(field) or "")
            url_update_sql = "".join(url_update_clause(field) for field in URL_FIELDS)
            f.write(
                "INSERT INTO `uni_official_link` "
                "(`school_id`,`school_name`,`source_domain`,`school_site`,`admission_site`,`admission_brochure_url`,`major_catalog_url`,`tuition_info_url`,`tuition_remark`,`tuition_summary`,`major_catalog_summary`,`adjustment_rule`,`foreign_language_rule`,`physical_exam_rule`,`single_subject_rule`,`parser_notes`,`capture_method`,`capture_status`,`parse_status`,`last_captured_at`,`last_verified_at`,`last_parsed_at`) "
                "VALUES "
                f"({esc_sql(row.get('school_id'))},{esc_sql(row.get('school_name'))},{esc_sql(row.get('source_domain'))},"
                f"{esc_sql(row.get('school_site'))},{esc_sql(row.get('admission_site'))},{esc_sql(row.get('admission_brochure_url'))},"
                f"{esc_sql(row.get('major_catalog_url'))},{esc_sql(row.get('tuition_info_url'))},{esc_sql(row.get('tuition_remark'))},"
                f"{esc_sql(row.get('tuition_summary'))},{esc_sql(row.get('major_catalog_summary'))},{esc_sql(row.get('adjustment_rule'))},"
                f"{esc_sql(row.get('foreign_language_rule'))},{esc_sql(row.get('physical_exam_rule'))},{esc_sql(row.get('single_subject_rule'))},"
                f"{esc_sql(row.get('parser_notes'))},{esc_sql(row.get('capture_method') or 'scraper')},{row.get('capture_status', 0)},{row.get('parse_status', 0)},NOW(),NOW(),NOW()) "
                "ON DUPLICATE KEY UPDATE "
                "`school_name`=COALESCE(NULLIF(VALUES(`school_name`),''),`school_name`),"
                "`source_domain`=COALESCE(NULLIF(VALUES(`source_domain`),''),`source_domain`),"
                + url_update_sql +
                "`tuition_remark`=COALESCE(NULLIF(VALUES(`tuition_remark`),''),`tuition_remark`),"
                "`tuition_summary`=COALESCE(NULLIF(VALUES(`tuition_summary`),''),`tuition_summary`),"
                "`major_catalog_summary`=COALESCE(NULLIF(VALUES(`major_catalog_summary`),''),`major_catalog_summary`),"
                "`adjustment_rule`=COALESCE(NULLIF(VALUES(`adjustment_rule`),''),`adjustment_rule`),"
                "`foreign_language_rule`=COALESCE(NULLIF(VALUES(`foreign_language_rule`),''),`foreign_language_rule`),"
                "`physical_exam_rule`=COALESCE(NULLIF(VALUES(`physical_exam_rule`),''),`physical_exam_rule`),"
                "`single_subject_rule`=COALESCE(NULLIF(VALUES(`single_subject_rule`),''),`single_subject_rule`),"
                "`parser_notes`=COALESCE(NULLIF(VALUES(`parser_notes`),''),`parser_notes`),"
                "`capture_method`=VALUES(`capture_method`),"
                "`capture_status`=CASE "
                "WHEN `capture_status`=1 OR VALUES(`capture_status`)=1 THEN 1 "
                "WHEN `capture_status`=2 OR VALUES(`capture_status`)=2 THEN 2 "
                "ELSE 0 END,"
                "`parse_status`=CASE "
                "WHEN `parse_status`=1 OR VALUES(`parse_status`)=1 THEN 1 "
                "WHEN `parse_status`=2 OR VALUES(`parse_status`)=2 THEN 2 "
                "ELSE 0 END,"
                "`last_captured_at`=NOW(),"
                "`last_parsed_at`=CASE "
                "WHEN COALESCE(NULLIF(VALUES(`tuition_summary`),''),NULL) IS NOT NULL "
                "OR COALESCE(NULLIF(VALUES(`major_catalog_summary`),''),NULL) IS NOT NULL "
                "OR COALESCE(NULLIF(VALUES(`adjustment_rule`),''),NULL) IS NOT NULL "
                "OR COALESCE(NULLIF(VALUES(`foreign_language_rule`),''),NULL) IS NOT NULL "
                "OR COALESCE(NULLIF(VALUES(`physical_exam_rule`),''),NULL) IS NOT NULL "
                "OR COALESCE(NULLIF(VALUES(`single_subject_rule`),''),NULL) IS NOT NULL "
                "THEN NOW() ELSE `last_parsed_at` END;\n"
            )


async def worker(worker_id: int, seeds: list[dict], delay: float, proxies: list[str]):
    global completed
    proxy = None
    if proxies:
        proxy = proxies[worker_id % len(proxies)]

    async with async_playwright() as p:
        launch_kwargs = {"headless": True}
        if proxy:
            launch_kwargs["proxy"] = {"server": proxy}
        browser: Browser = await p.chromium.launch(**launch_kwargs)
        context = await browser.new_context(
            user_agent=USER_AGENTS[worker_id % len(USER_AGENTS)],
            viewport={"width": 1440, "height": 900},
            locale="zh-CN",
            ignore_https_errors=True,
        )
        await context.route(
            "**/*",
            lambda route: route.abort() if route.request.resource_type in {"image", "media", "font"} else route.continue_(),
        )
        page = await context.new_page()

        for seed in seeds:
            row = await scrape_school(page, seed)
            async with lock:
                results[:] = [item for item in results if item.get("school_id") != row.get("school_id")]
                results.append(row)
                completed += 1
                current = completed
            print(
                f"[W{worker_id}] [{current}] {seed['name']}: "
                f"招生网={'Y' if row['admission_site'] else 'N'} "
                f"章程={'Y' if row['admission_brochure_url'] else 'N'} "
                f"专业目录={'Y' if row['major_catalog_url'] else 'N'} "
                f"收费={'Y' if row['tuition_info_url'] else 'N'}",
                flush=True,
            )
            if checkpoint_every and current % checkpoint_every == 0:
                await save_checkpoint()
            await asyncio.sleep(delay)

        await context.close()
        await browser.close()


async def main():
    global results, completed, checkpoint_every
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="https://gzly.dongsiwei.com")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--resume", type=int, default=0)
    parser.add_argument("--delay", type=float, default=1.2)
    parser.add_argument("--checkpoint-every", type=int, default=20)
    parser.add_argument("--clear-checkpoint", action="store_true")
    parser.add_argument("--seed-file", default=SEED_JSON)
    parser.add_argument("--proxy", default="", help="Comma separated proxy servers, e.g. http://127.0.0.1:7890,http://127.0.0.1:7891")
    args = parser.parse_args()

    if args.clear_checkpoint:
        for path in [CHECKPOINT_JSON, RESULT_JSON]:
            if os.path.exists(path):
                os.remove(path)

    results = dedupe_rows(load_checkpoint())
    done_ids = {str(item.get("school_id")) for item in results if item.get("school_id") and is_structured_done(item)}
    completed = len(done_ids)

    seeds = fetch_university_seeds(args.base_url, args.seed_file)
    if done_ids:
        seeds = [seed for seed in seeds if seed["school_id"] not in done_ids]
    if args.resume > 0:
        seeds = seeds[args.resume:]
    if args.limit > 0:
        seeds = seeds[:args.limit]

    if results:
        export_sql(dedupe_rows(results), RESULT_SQL)

    proxies = [item.strip() for item in args.proxy.split(",") if item.strip()]
    checkpoint_every = max(args.checkpoint_every, 1)
    print(
        f"Loaded {len(seeds)} pending schools, workers={args.workers}, proxies={len(proxies)}, resumed={completed}, checkpoint_every={checkpoint_every}",
        flush=True,
    )

    chunks = [seeds[i::args.workers] for i in range(args.workers)]
    await asyncio.gather(*[
        worker(idx + 1, chunk, args.delay, proxies)
        for idx, chunk in enumerate(chunks)
        if chunk
    ])

    await save_checkpoint()
    export_sql(results, RESULT_SQL)
    print(f"Done: {len(results)} schools -> {RESULT_JSON}", flush=True)
    print(f"SQL exported -> {RESULT_SQL}", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
