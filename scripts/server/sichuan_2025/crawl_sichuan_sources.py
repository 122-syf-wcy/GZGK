#!/usr/bin/env python3
"""Discover and archive Sichuan Education Examination Authority sources."""

import argparse
import csv
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
    is_sceea_url,
    looks_like_download,
    save_fetch_result,
    sha256_bytes,
    sha256_file,
    strip_html_text,
    write_csv,
    write_json,
)


OFFICIAL_SOURCES: list[dict[str, str]] = [
    {
        "dataType": "requirement",
        "subjectType": "",
        "batch": "",
        "title": "2025录取方案",
        "url": "https://www.sceea.cn/Html/202501/Newsdetail_4130.html",
    },
    {
        "dataType": "requirement",
        "subjectType": "",
        "batch": "",
        "title": "2025招生实施规定",
        "url": "https://www.sceea.cn/Html/202505/Newsdetail_4261.html",
    },
    {
        "dataType": "score_rank",
        "subjectType": "历史类",
        "batch": "",
        "title": "2025四川普通高考历史类一分一段",
        "url": "https://www.sceea.cn/Html/202506/Newsdetail_4334.html",
    },
    {
        "dataType": "score_rank",
        "subjectType": "物理类",
        "batch": "",
        "title": "2025四川普通高考物理类一分一段",
        "url": "https://www.sceea.cn/Html/202506/Newsdetail_4335.html",
    },
    {
        "dataType": "group_line",
        "subjectType": "",
        "batch": "普通本科批B段",
        "title": "2025普通本科批B段投档汇总新闻",
        "url": "https://www.sceea.cn/Html/202507/Newsdetail_4405.html",
    },
    {
        "dataType": "group_plan",
        "subjectType": "",
        "batch": "普通本科批B段",
        "title": "2025招生计划更正一",
        "url": "https://www.sceea.cn/Html/202506/Newsdetail_4330.html",
    },
    {
        "dataType": "group_plan",
        "subjectType": "",
        "batch": "普通本科批B段",
        "title": "2025招生计划更正二",
        "url": "https://www.sceea.cn/Html/202506/Newsdetail_4338.html",
    },
]

DISCOVERY_KEYWORDS = [
    "2025",
    "四川",
    "普通高考",
    "本科批",
    "本科批B段",
    "B段",
    "院校专业组",
    "投档",
    "调档",
    "招生计划",
    "一分一段",
]


def audit_one(source: dict[str, str], download_children: bool) -> dict[str, Any]:
    result = fetch_url(source["url"])
    record: dict[str, Any] = {
        **source,
        "status": result.status,
        "finalUrl": result.final_url,
        "contentType": result.content_type,
        "error": result.error,
        "sourceHash": sha256_bytes(result.body) if result.body else "",
        "rawPath": "",
        "children": [],
    }
    if not result.body:
        return record

    raw_path = save_fetch_result(result, RAW_DIR / "official_pages", source["dataType"])
    record["rawPath"] = str(raw_path)
    text = decode_body(result.body, result.content_type)
    record["snippet"] = content_snippet(text, DISCOVERY_KEYWORDS)
    for link in extract_links(text, result.final_url or source["url"]):
        if not is_sceea_url(link):
            continue
        child: dict[str, Any] = {
            "url": link,
            "dataType": source["dataType"],
            "subjectType": source["subjectType"],
            "batch": source["batch"],
            "downloaded": False,
            "contentType": "",
            "sourceHash": "",
            "rawPath": "",
        }
        if download_children and looks_like_download(link):
            child_result = fetch_url(link)
            child["contentType"] = child_result.content_type
            child["status"] = child_result.status
            child["error"] = child_result.error
            if child_result.body:
                child_path = save_fetch_result(
                    child_result,
                    RAW_DIR / "official_downloads" / source["dataType"],
                    f"{source['subjectType']}_{Path(link).name}",
                )
                child["downloaded"] = True
                child["rawPath"] = str(child_path)
                child["sourceHash"] = sha256_file(child_path)
        record["children"].append(child)
    return record


def discover_related_pages(records: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    candidates: list[str] = []
    for record in records:
        for child in record.get("children", []):
            url = child.get("url", "")
            if "/Html/2025" in url and url.endswith(".html") and url not in candidates:
                candidates.append(url)
    discovered: list[dict[str, Any]] = []
    for url in candidates[:limit]:
        result = fetch_url(url, retries=1)
        if not result.body:
            discovered.append({"url": url, "status": result.status, "error": result.error, "matched": False})
            continue
        text = decode_body(result.body, result.content_type)
        plain = strip_html_text(text)
        matched_keywords = [kw for kw in DISCOVERY_KEYWORDS if kw in plain]
        discovered.append({
            "url": url,
            "status": result.status,
            "contentType": result.content_type,
            "sourceHash": sha256_bytes(result.body),
            "matched": bool(matched_keywords),
            "matchedKeywords": matched_keywords,
            "snippet": content_snippet(text, DISCOVERY_KEYWORDS),
        })
    return discovered


def write_markdown_report(records: list[dict[str, Any]], discovered: list[dict[str, Any]]) -> None:
    lines = [
        "# 四川 2025 官方来源抓取报告",
        "",
        "## 已知来源",
        "",
    ]
    for record in records:
        child_count = len(record.get("children", []))
        downloaded = sum(1 for child in record.get("children", []) if child.get("downloaded"))
        lines.append(
            f"- {record['title']}：HTTP {record.get('status')}，"
            f"子链接 {child_count}，已下载附件/图片 {downloaded}，hash `{record.get('sourceHash', '')[:16]}`"
        )
    lines.extend(["", "## 站内关联页面候选", ""])
    for item in discovered:
        if item.get("matched"):
            lines.append(
                f"- {item['url']}：命中 {','.join(item.get('matchedKeywords', []))}，"
                f"hash `{item.get('sourceHash', '')[:16]}`"
            )
    if not any(item.get("matched") for item in discovered):
        lines.append("- 未发现新的高置信关联页面。")
    (REPORTS_DIR / "source_audit.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Archive official SCEEA Sichuan 2025 source pages.")
    parser.add_argument("--download-children", action="store_true", help="download official images/PDF/doc/xls children")
    parser.add_argument("--discover-limit", type=int, default=40, help="max linked 2025 SCEEA pages to inspect")
    args = parser.parse_args()
    ensure_dirs()

    records = [audit_one(source, args.download_children) for source in OFFICIAL_SOURCES]
    discovered = discover_related_pages(records, args.discover_limit)
    write_json(REPORTS_DIR / "source_audit.json", {"officialSources": records, "discoveredPages": discovered})

    flat_rows: list[dict[str, Any]] = []
    for record in records:
        flat_rows.append({
            "kind": "official_page",
            "dataType": record["dataType"],
            "subjectType": record["subjectType"],
            "batch": record["batch"],
            "url": record["url"],
            "status": record.get("status", ""),
            "contentType": record.get("contentType", ""),
            "sourceHash": record.get("sourceHash", ""),
            "rawPath": record.get("rawPath", ""),
            "downloaded": "",
            "snippet": record.get("snippet", ""),
        })
        for child in record.get("children", []):
            flat_rows.append({
                "kind": "official_child",
                "dataType": child.get("dataType", ""),
                "subjectType": child.get("subjectType", ""),
                "batch": child.get("batch", ""),
                "url": child.get("url", ""),
                "status": child.get("status", ""),
                "contentType": child.get("contentType", ""),
                "sourceHash": child.get("sourceHash", ""),
                "rawPath": child.get("rawPath", ""),
                "downloaded": child.get("downloaded", False),
                "snippet": "",
            })
    for item in discovered:
        flat_rows.append({
            "kind": "discovered_page",
            "dataType": "",
            "subjectType": "",
            "batch": "",
            "url": item.get("url", ""),
            "status": item.get("status", ""),
            "contentType": item.get("contentType", ""),
            "sourceHash": item.get("sourceHash", ""),
            "rawPath": "",
            "downloaded": "",
            "snippet": item.get("snippet", ""),
        })
    write_csv(
        REPORTS_DIR / "source_audit.csv",
        ["kind", "dataType", "subjectType", "batch", "url", "status", "contentType", "sourceHash", "rawPath", "downloaded", "snippet"],
        flat_rows,
    )
    write_markdown_report(records, discovered)
    print(f"wrote {REPORTS_DIR / 'source_audit.json'}")
    print(f"wrote {REPORTS_DIR / 'source_audit.csv'}")
    print(f"wrote {REPORTS_DIR / 'source_audit.md'}")


if __name__ == "__main__":
    main()
