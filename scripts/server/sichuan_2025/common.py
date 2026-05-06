#!/usr/bin/env python3
"""Shared helpers for the Sichuan 2025 data discovery pipeline.

The pipeline intentionally writes only local raw/draft/review/report artifacts.
It never writes production tables directly.
"""

import csv
import hashlib
import html
import json
import mimetypes
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parent
RAW_DIR = ROOT / "raw"
DRAFT_DIR = ROOT / "draft"
REVIEWED_DIR = ROOT / "reviewed"
REPORTS_DIR = ROOT / "reports"
PAYLOADS_DIR = ROOT / "payloads"
DEFAULT_ENV_FILE = "/etc/gzly/gzly.env"
DEFAULT_DB_NAME = "gzly"
USER_AGENT = (
    "Mozilla/5.0 (compatible; GZLY-SichuanDataCrawler/1.0; "
    "+https://gzly.dongsiwei.com)"
)

SCORE_RANK_HEADERS = ["score", "scoreLabel", "segmentCount", "cumulativeCount"]
GROUP_LINE_HEADERS = [
    "schoolId",
    "universityName",
    "groupCode",
    "groupName",
    "subjectType",
    "firstSubjectRequirement",
    "resubjectRequirement",
    "minScore",
    "minRank",
    "planCount",
    "batch",
]
GROUP_PLAN_HEADERS = [
    "schoolId",
    "universityName",
    "groupCode",
    "groupName",
    "majorCode",
    "majorName",
    "subjectType",
    "firstSubjectRequirement",
    "resubjectRequirement",
    "planCount",
    "tuition",
    "studyYears",
    "batch",
]
SOURCE_META_HEADERS = ["sourcePageUrl", "sourceUrl", "sourceHash", "sourceLevel"]


@dataclass(frozen=True)
class FetchResult:
    url: str
    final_url: str
    status: int
    content_type: str
    body: bytes
    error: str = ""


def ensure_dirs() -> None:
    for directory in (RAW_DIR, DRAFT_DIR, REVIEWED_DIR, REPORTS_DIR, PAYLOADS_DIR):
        directory.mkdir(parents=True, exist_ok=True)


def now_ms() -> int:
    return int(time.time() * 1000)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_name(value: str, fallback: str = "item") -> str:
    text = urllib.parse.unquote(value or "").strip()
    text = re.sub(r"https?://", "", text, flags=re.I)
    text = re.sub(r"[^0-9A-Za-z._-]+", "_", text).strip("._-")
    if not text:
        text = fallback
    return f"{text[:80]}_{sha256_text(value)[:10]}"


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


def is_sceea_url(url: str) -> bool:
    host = host_of(url)
    return host == "sceea.cn" or host.endswith(".sceea.cn")


def looks_like_download(url: str, content_type: str = "") -> bool:
    suffix = Path(urllib.parse.urlparse(url).path).suffix.lower()
    if suffix in {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".jpg", ".jpeg", ".png", ".webp"}:
        return True
    content_type = content_type.lower()
    return any(token in content_type for token in ("pdf", "image/", "msword", "spreadsheet"))


def fetch_url(url: str, timeout: int = 20, retries: int = 2, sleep_seconds: float = 0.5,
              max_bytes: int | None = None) -> FetchResult:
    last_error = ""
    request_url = iri_to_uri(url)
    for attempt in range(retries + 1):
        try:
            request = urllib.request.Request(request_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=timeout) as response:
                if max_bytes is None or max_bytes <= 0:
                    body = response.read()
                else:
                    body = response.read(max_bytes + 1)
                    if len(body) > max_bytes:
                        return FetchResult(
                            url=url,
                            final_url=response.geturl(),
                            status=getattr(response, "status", 200),
                            content_type=response.headers.get("Content-Type", ""),
                            body=b"",
                            error=f"content too large: >{max_bytes} bytes",
                        )
                return FetchResult(
                    url=url,
                    final_url=response.geturl(),
                    status=getattr(response, "status", 200),
                    content_type=response.headers.get("Content-Type", ""),
                    body=body,
                )
        except (urllib.error.URLError, TimeoutError, OSError, UnicodeError, ValueError) as exc:
            last_error = str(exc)
            if attempt < retries:
                time.sleep(sleep_seconds * (attempt + 1))
    return FetchResult(url=url, final_url=url, status=0, content_type="", body=b"", error=last_error)


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


def extract_links(text: str, base_url: str) -> list[str]:
    links: list[str] = []
    patterns = [
        r"""(?i)(?:href|src)\s*=\s*['"]([^'"]+)['"]""",
        r"""(?i)url\(['"]?([^)'"]+)['"]?\)""",
    ]
    for pattern in patterns:
        for raw in re.findall(pattern, text or ""):
            url = normalize_url(base_url, raw)
            if url and url not in links:
                links.append(url)
    return links


def strip_html_text(text: str) -> str:
    text = re.sub(r"(?is)<script.*?</script>|<style.*?</style>", " ", text or "")
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def content_snippet(text: str, keywords: Iterable[str], window: int = 160) -> str:
    plain = strip_html_text(text)
    lower = plain.lower()
    for keyword in keywords:
        idx = lower.find(keyword.lower())
        if idx >= 0:
            start = max(0, idx - window)
            end = min(len(plain), idx + len(keyword) + window)
            return plain[start:end]
    return plain[: window * 2]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def append_jsonl(path: Path, values: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        for value in values:
            handle.write(json.dumps(value, ensure_ascii=False, sort_keys=True) + "\n")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def write_csv(path: Path, headers: list[str], rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in headers})


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def save_fetch_result(result: FetchResult, directory: Path, prefix: str) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    path = urllib.parse.urlparse(result.final_url or result.url).path
    suffix = Path(path).suffix
    if not suffix:
        suffix = ".html" if "html" in result.content_type.lower() else ".bin"
    target = directory / f"{safe_name(prefix + '_' + (result.final_url or result.url))}{suffix}"
    target.write_bytes(result.body)
    return target


def parse_env_file(path: str = DEFAULT_ENV_FILE) -> dict[str, str]:
    values: dict[str, str] = {}
    env_path = Path(path)
    if not env_path.exists():
        return values
    for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'\"")
    return values


def mysql_query(sql: str, env_file: str = DEFAULT_ENV_FILE, db_name: str = DEFAULT_DB_NAME) -> list[list[str]]:
    env_values = parse_env_file(env_file)
    env = os.environ.copy()
    user = env_values.get("GZLY_DB_USERNAME") or env.get("GZLY_DB_USERNAME") or "root"
    password = env_values.get("GZLY_DB_PASSWORD") or env.get("GZLY_DB_PASSWORD") or ""
    env["MYSQL_PWD"] = password
    command = ["mysql", "-N", "-B", "-u", user, db_name, "-e", sql]
    completed = subprocess.run(command, env=env, check=True, text=True, capture_output=True)
    rows: list[list[str]] = []
    for line in completed.stdout.splitlines():
        rows.append(line.split("\t"))
    return rows


def guess_mime_type(path: Path) -> str:
    return mimetypes.guess_type(path.name)[0] or "application/octet-stream"


def int_or_blank(value: Any) -> int | str:
    if value is None or value == "":
        return ""
    try:
        return int(str(value).replace(",", "").strip())
    except ValueError:
        return ""
