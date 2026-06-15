#!/usr/bin/env python3
"""Extract official 2025 professional-group admission-line images into drafts.

The default output is a draft/review queue only. Writing reviewed CSVs requires
an explicit confirmation flag because model/OCR output is not a substitute for
human verification.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
RAW_DIR = ROOT / "raw"
DRAFT_DIR = ROOT / "draft"
REPORTS_DIR = ROOT / "reports"
REVIEWED_DIR = ROOT / "reviewed"

USER_AGENT = "Mozilla/5.0 (compatible; GZLY-GroupLineVision/1.0; +https://gzly.dongsiwei.com)"
SUBJECT_TYPES = ("历史类", "物理类")

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
    "sourcePageUrl",
    "sourceUrl",
    "sourceHash",
    "sourceLevel",
]

DRAFT_HEADERS = [
    *GROUP_LINE_HEADERS,
    "provinceCode",
    "imageIndex",
    "rawPath",
    "reviewStatus",
    "confidence",
    "parserNotes",
]


@dataclass(frozen=True)
class GroupLineSource:
    province_code: str
    province_name: str
    subject_type: str
    batch: str
    title: str
    source_page_url: str
    source_level: str
    image_url_pattern: str


SOURCES: tuple[GroupLineSource, ...] = (
    GroupLineSource(
        "HB",
        "湖北",
        "历史类",
        "本科普通批",
        "湖北省2025年本科普通批录取院校（首选历史）平行志愿投档分数线",
        "https://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202507/t20250721_5727303.shtml",
        "manual_verified",
        r"W020250721.*_ORIGIN\.jpg$",
    ),
    GroupLineSource(
        "HB",
        "湖北",
        "物理类",
        "本科普通批",
        "湖北省2025年本科普通批录取院校（首选物理）平行志愿投档分数线",
        "https://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202507/t20250721_5727304.shtml",
        "manual_verified",
        r"W020250721.*_ORIGIN\.jpg$",
    ),
    GroupLineSource(
        "AH",
        "安徽",
        "历史类",
        "普通本科批次",
        "安徽省2025年普通高校招生普通本科批院校投档最低分及名次（历史科目组合）",
        "https://gaokao.eol.cn/an_hui/dongtai/202507/t20250725_2682977.shtml",
        "manual_verified",
        r"W020250725337430.*\.jpg$",
    ),
    GroupLineSource(
        "AH",
        "安徽",
        "物理类",
        "普通本科批次",
        "安徽省2025年普通高校招生普通本科批院校投档最低分及名次（物理科目组合）",
        "https://gaokao.eol.cn/an_hui/dongtai/202507/t20250725_2682985.shtml",
        "manual_verified",
        r"W020250725351388.*\.jpg$",
    ),
)


SYSTEM_PROMPT = """你是高考公开数据抽取助手。只抽取图片中明确出现的院校专业组投档线，不猜测。
输出必须是 JSON 对象，不要输出 Markdown。
JSON 结构：
{
  "rows": [
    {
      "schoolId": "院校代号或空字符串",
      "universityName": "院校名称",
      "groupCode": "专业组代号，如 001、A00405 或图片中的完整专业组代号",
      "groupName": "专业组名称或空字符串",
      "firstSubjectRequirement": "物理|历史|",
      "resubjectRequirement": "再选科目要求或空字符串",
      "minScore": 0,
      "minRank": 0,
      "planCount": "",
      "notes": "不确定处说明",
      "confidence": 0.0
    }
  ],
  "notes": []
}
规则：
- 湖北图片只抽本科普通批平行志愿投档分数线；安徽图片只抽普通本科批投档最低分及名次。
- 如果图片含“投档人数”，可放入 planCount 字段，但 notes 必须写明它是投档人数，不是招生计划；后续不会进入 group_plans。
- 看不清的院校名、专业组代码、最低分不要编造；该行宁可省略。
- minScore 必须是 1-750 的整数；minRank 看不到就填 0。
"""


def selected_provinces(value: str) -> set[str]:
    if value.upper() == "ALL":
        return {"SC", "HB", "AH"}
    return {item.strip().upper() for item in value.split(",") if item.strip()}


def text(value: Any) -> str:
    return str(value or "").strip()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def safe_name(value: str) -> str:
    decoded = urllib.parse.unquote(value or "")
    cleaned = re.sub(r"https?://", "", decoded, flags=re.I)
    cleaned = re.sub(r"[^0-9A-Za-z._-]+", "_", cleaned).strip("._-")
    return f"{(cleaned or 'source')[:90]}_{hashlib.sha1(value.encode('utf-8')).hexdigest()[:10]}"


def fetch_url(url: str, timeout: int) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def decode_body(body: bytes) -> str:
    for encoding in ("utf-8", "gb18030", "gbk"):
        try:
            return body.decode(encoding)
        except UnicodeDecodeError:
            continue
    return body.decode("utf-8", errors="replace")


def extract_links(page_url: str, body: str) -> list[str]:
    links: list[str] = []
    for raw in re.findall(r"""(?i)(?:href|src)\s*=\s*['"]([^'"]+)['"]""", body):
        url = html.unescape(urllib.parse.urljoin(page_url, raw))
        if url not in links:
            links.append(url)
    return links


def source_items(source: GroupLineSource, timeout: int, refresh: bool) -> list[dict[str, Any]]:
    raw_page_dir = RAW_DIR / source.province_code / "official_pages" / "group_line"
    raw_image_dir = RAW_DIR / source.province_code / "official_downloads" / "group_line"
    raw_page_dir.mkdir(parents=True, exist_ok=True)
    raw_image_dir.mkdir(parents=True, exist_ok=True)

    page_path = raw_page_dir / f"group_line_{source.subject_type}_{safe_name(source.source_page_url)}.html"
    if refresh or not page_path.exists():
        page_body = fetch_url(source.source_page_url, timeout)
        page_path.write_bytes(page_body)
    else:
        page_body = page_path.read_bytes()
    page_text = decode_body(page_body)

    pattern = re.compile(source.image_url_pattern, re.I)
    image_urls = [
        url for url in extract_links(source.source_page_url, page_text)
        if pattern.search(urllib.parse.urlparse(url).path)
    ]
    unique_urls: list[str] = []
    for image_url in image_urls:
        if image_url not in unique_urls:
            unique_urls.append(image_url)

    items: list[dict[str, Any]] = []
    for image_index, image_url in enumerate(unique_urls, 1):
        image_path = raw_image_dir / f"group_line_{source.subject_type}_{image_index:03d}_{safe_name(image_url)}.jpg"
        if refresh or not image_path.exists():
            image_body = fetch_url(image_url, timeout)
            image_path.write_bytes(image_body)
        else:
            image_body = image_path.read_bytes()
        items.append({
            "provinceCode": source.province_code,
            "provinceName": source.province_name,
            "subjectType": source.subject_type,
            "batch": source.batch,
            "title": source.title,
            "sourcePageUrl": source.source_page_url,
            "sourceUrl": image_url,
            "sourceHash": sha256_bytes(image_body),
            "sourceLevel": source.source_level,
            "rawPath": str(image_path),
            "imageIndex": image_index,
        })
    return items


def api_config() -> tuple[str, str, str]:
    base_url = (
        os.environ.get("VISION_BASE_URL")
        or os.environ.get("GZLY_VISION_BASE_URL")
        or os.environ.get("GZLY_AI_BASE_URL")
        or ""
    )
    api_key = (
        os.environ.get("VISION_API_KEY")
        or os.environ.get("GZLY_VISION_API_KEY")
        or os.environ.get("GZLY_AI_API_KEY")
        or ""
    )
    model = (
        os.environ.get("VISION_MODEL")
        or os.environ.get("GZLY_VISION_MODEL")
        or os.environ.get("GZLY_AI_MODEL")
        or "gpt-4o-mini"
    )
    if not base_url or not api_key:
        raise SystemExit(
            "Missing VISION_BASE_URL/GZLY_VISION_BASE_URL/GZLY_AI_BASE_URL "
            "or VISION_API_KEY/GZLY_VISION_API_KEY/GZLY_AI_API_KEY"
        )
    endpoint = base_url.rstrip("/")
    if not endpoint.endswith("/chat/completions"):
        endpoint += "/chat/completions"
    return endpoint, api_key, model


def guess_mime_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".png":
        return "image/png"
    if suffix == ".webp":
        return "image/webp"
    return "application/octet-stream"


def chat_completion(messages: list[dict[str, Any]], max_tokens: int, timeout: int) -> str:
    endpoint, api_key, model = api_config()
    payload = json.dumps({
        "model": model,
        "messages": messages,
        "temperature": 0,
        "max_tokens": max_tokens,
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        result = json.loads(response.read().decode("utf-8"))
    return result["choices"][0]["message"]["content"]


def parse_json_response(raw: str) -> dict[str, Any]:
    body = raw.strip()
    if body.startswith("```"):
        body = re.sub(r"^```(?:json)?", "", body, flags=re.I).strip()
        body = re.sub(r"```$", "", body).strip()
    body = body.strip()
    if body.startswith('"') and body.endswith('"'):
        try:
            nested = json.loads(body)
            if isinstance(nested, str):
                body = nested.strip()
        except json.JSONDecodeError:
            pass
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", body, flags=re.S)
        if not match:
            return {"rows": [], "notes": [raw[:1000]], "rawResponse": raw}
        try:
            parsed = json.loads(match.group(0))
        except json.JSONDecodeError:
            return {"rows": [], "notes": [raw[:1000]], "rawResponse": raw}
    if not isinstance(parsed, dict):
        return {"rows": [], "notes": ["model response is not a JSON object"], "rawResponse": raw}
    return parsed


def call_model(item: dict[str, Any], max_tokens: int, timeout: int) -> dict[str, Any]:
    path = Path(item["rawPath"])
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    instruction = (
        f"请抽取{item['provinceName']}2025年{item['batch']}{item['subjectType']}院校专业组投档线。"
        f"\n来源页：{item['sourcePageUrl']}"
        f"\n图片：{item['sourceUrl']}"
        "\n只输出 JSON。"
    )
    raw = chat_completion(
        [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": instruction},
                    {"type": "image_url", "image_url": {"url": f"data:{guess_mime_type(path)};base64,{data}"}},
                ],
            },
        ],
        max_tokens=max_tokens,
        timeout=timeout,
    )
    parsed = parse_json_response(raw)
    parsed["sourceMeta"] = item
    parsed["rawResponse"] = raw
    return parsed


def expand_image_slices(items: list[dict[str, Any]], image_slices: int) -> list[dict[str, Any]]:
    if image_slices <= 1:
        return items
    try:
        from PIL import Image
    except ImportError as exc:
        raise RuntimeError("Pillow is required for image slicing") from exc
    sliced_items: list[dict[str, Any]] = []
    slice_dir = DRAFT_DIR / "group_line_slices"
    slice_dir.mkdir(parents=True, exist_ok=True)
    for item in items:
        path = Path(item["rawPath"])
        with Image.open(path) as image:
            overlap = max(60, image.height // 80)
            for slice_index in range(image_slices):
                top = max(0, image.height * slice_index // image_slices - (overlap if slice_index else 0))
                bottom = min(image.height, image.height * (slice_index + 1) // image_slices + (overlap if slice_index < image_slices - 1 else 0))
                out = slice_dir / f"{path.stem}_slice{slice_index + 1:02d}{path.suffix}"
                image.crop((0, top, image.width, bottom)).save(out)
                next_item = dict(item)
                next_item["rawPath"] = str(out)
                next_item["imageIndex"] = f"{item.get('imageIndex')}.{slice_index + 1}"
                next_item["parserSliceOf"] = str(path)
                sliced_items.append(next_item)
    return sliced_items


def clean_school_name(value: str) -> str:
    cleaned = re.sub(r"\s+", "", value)
    cleaned = re.sub(r"[^\u4e00-\u9fffA-Za-z0-9（）()·\-]", "", cleaned)
    for token in ("物理科目组合", "历史科目组合", "院校名称", "科类", "投档", "最低分", "名次"):
        cleaned = cleaned.replace(token, "")
    return cleaned


def clean_group_code(value: Any) -> str:
    raw = text(value).translate(str.maketrans({"O": "0", "o": "0", "l": "1", "I": "1"}))
    return re.sub(r"[^0-9A-Za-z]", "", raw).upper()


def infer_resubject(value: str) -> str:
    compact = re.sub(r"\s+", "", value)
    if "不限" in compact:
        return "不限"
    if "化" in compact:
        return "化学"
    if "政" in compact or "思想" in compact:
        return "思想政治"
    if "地" in compact:
        return "地理"
    if "生" in compact:
        return "生物"
    return ""


def numbers_in_line(line: str) -> list[int]:
    values: list[int] = []
    for raw in re.findall(r"\d+", line):
        try:
            values.append(int(raw))
        except ValueError:
            continue
    return values


def parse_ah_tesseract_line(item: dict[str, Any], line: str) -> dict[str, Any] | None:
    code_match = re.search(r"\b(\d{4})\b", line)
    if not code_match:
        return None
    compact = re.sub(r"\s+", "", line)
    subject_label = "物理科目组合" if item["subjectType"] == "物理类" else "历史科目组合"
    subject_idx = compact.find(subject_label)
    if subject_idx < 0:
        subject_idx = compact.find("科目组合")
    school_part = compact[code_match.end():subject_idx if subject_idx > 0 else len(compact)]
    university_name = clean_school_name(school_part)
    if len(university_name) < 2:
        return None
    after_subject = compact[subject_idx if subject_idx > 0 else code_match.end():]
    group_match = re.search(r"([0-9OoIl]{3})[^0-9]{0,10}专业组", after_subject)
    if not group_match:
        group_match = re.search(r"\b([0-9OoIl]{3})\b", after_subject)
    if not group_match:
        return None
    group_code = clean_group_code(group_match.group(1))
    nums = numbers_in_line(line)
    rank = ""
    score = ""
    for value in reversed(nums):
        if value > 1000 and not rank:
            rank = str(value)
            continue
        if 100 <= value <= 750 and not score:
            score = str(value)
            break
    if not score:
        return None
    plan_count = ""
    excluded = {int(score), int(rank or 0), int(code_match.group(1))}
    if group_code.isdigit():
        excluded.add(int(group_code))
    for value in reversed(nums):
        if value in excluded:
            continue
        if 0 <= value < 1000:
            plan_count = str(value)
            break
    return {
        "schoolId": code_match.group(1),
        "universityName": university_name,
        "groupCode": group_code,
        "groupName": f"{group_code}专业组",
        "firstSubjectRequirement": "历史" if item["subjectType"] == "历史类" else "物理",
        "resubjectRequirement": infer_resubject(after_subject),
        "minScore": score,
        "minRank": rank,
        "planCount": plan_count,
        "notes": "tesseract_ah_line；planCount 若存在来自投档人数，不等同招生计划",
        "confidence": 0.55,
    }


def parse_hb_tesseract_line(item: dict[str, Any], line: str) -> dict[str, Any] | None:
    code_match = re.search(r"\b([A-Za-z]\d{5})\b", line)
    if not code_match:
        return None
    group_code = clean_group_code(code_match.group(1))
    compact = re.sub(r"\s+", "", line)
    code_idx = compact.find(code_match.group(1))
    tail = compact[code_idx + len(code_match.group(1)):] if code_idx >= 0 else compact
    group_name_match = re.search(r"第([0-9OoIl]{1,2})组", tail)
    if not group_name_match:
        return None
    university_name = clean_school_name(tail[:group_name_match.start()])
    if len(university_name) < 2:
        return None
    score = ""
    for value in numbers_in_line(line):
        if 100 <= value <= 750:
            score = str(value)
            break
    if not score:
        return None
    group_no = clean_group_code(group_name_match.group(1))
    return {
        "schoolId": "",
        "universityName": university_name,
        "groupCode": group_code,
        "groupName": f"第{group_no}组",
        "firstSubjectRequirement": "历史" if item["subjectType"] == "历史类" else "物理",
        "resubjectRequirement": infer_resubject(tail),
        "minScore": score,
        "minRank": "",
        "planCount": "",
        "notes": "tesseract_hb_line；最低位次需用官方一分一段换算或人工补录",
        "confidence": 0.5,
    }


def tesseract_text(path: Path, province_code: str, timeout: int) -> str:
    try:
        from PIL import Image, ImageOps
    except ImportError as exc:
        raise RuntimeError("Pillow is required for tesseract fallback") from exc
    texts: list[str] = []
    with Image.open(path) as image:
        slice_height = 850 if province_code == "AH" else 1050
        boxes = []
        top = 0
        while top < image.height:
            bottom = min(image.height, top + slice_height)
            boxes.append((0, top, image.width, bottom))
            if bottom == image.height:
                break
            top = max(0, bottom - 40)
        psm = "11" if province_code == "HB" else "6"
        for box in boxes:
            cropped = image.crop(box)
            scaled = ImageOps.grayscale(cropped).resize((cropped.width * 2, cropped.height * 2))
            with tempfile.NamedTemporaryFile(suffix=".png") as handle:
                scaled.save(handle.name)
                completed = subprocess.run(
                    ["tesseract", handle.name, "stdout", "-l", "chi_sim+eng", "--psm", psm, "--oem", "1"],
                    check=True,
                    text=True,
                    capture_output=True,
                    timeout=timeout,
                )
                texts.append(completed.stdout)
    return "\n".join(texts)


def extract_with_tesseract(item: dict[str, Any], timeout: int) -> dict[str, Any]:
    raw_text = tesseract_text(Path(item["rawPath"]), item["provinceCode"], timeout)
    parser = parse_ah_tesseract_line if item["provinceCode"] == "AH" else parse_hb_tesseract_line
    rows = []
    for line in raw_text.splitlines():
        parsed = parser(item, line)
        if parsed:
            rows.append(parsed)
    return {"rows": rows, "notes": ["tesseract_fallback"], "sourceMeta": item, "rawResponse": raw_text}


def int_or_empty(value: Any) -> str:
    raw = text(value).replace(",", "")
    if not raw:
        return ""
    match = re.search(r"\d+", raw)
    return match.group(0) if match else ""


def normalize_group_code(value: Any) -> str:
    raw = text(value).replace("第", "").replace("组", "")
    raw = re.sub(r"\s+", "", raw)
    return clean_group_code(raw)


def normalize_rows(extractions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for extraction in extractions:
        source = extraction.get("sourceMeta") or {}
        for row in extraction.get("rows") or []:
            if not isinstance(row, dict):
                continue
            min_score = int_or_empty(row.get("minScore"))
            if not min_score:
                continue
            score_value = int(min_score)
            if score_value <= 0 or score_value > 750:
                continue
            group_code = normalize_group_code(row.get("groupCode"))
            university_name = text(row.get("universityName"))
            if not group_code or not university_name:
                continue
            subject_type = text(source.get("subjectType"))
            first_subject = "历史" if subject_type == "历史类" else "物理" if subject_type == "物理类" else text(row.get("firstSubjectRequirement"))
            notes = text(row.get("notes"))
            if text(row.get("planCount")):
                notes = (notes + "；" if notes else "") + "planCount 来自投档人数字段，不等同招生计划"
            confidence = text(row.get("confidence"))
            review_status = "needs_manual_review"
            try:
                if float(confidence or 0) >= 0.9 and not notes:
                    review_status = "model_high_confidence_needs_spot_check"
            except ValueError:
                pass
            rows.append({
                "provinceCode": source.get("provinceCode", ""),
                "schoolId": text(row.get("schoolId")),
                "universityName": university_name,
                "groupCode": group_code,
                "groupName": text(row.get("groupName")),
                "subjectType": subject_type,
                "firstSubjectRequirement": first_subject,
                "resubjectRequirement": text(row.get("resubjectRequirement")),
                "minScore": min_score,
                "minRank": int_or_empty(row.get("minRank")),
                "planCount": text(row.get("planCount")),
                "batch": source.get("batch", ""),
                "sourcePageUrl": source.get("sourcePageUrl", ""),
                "sourceUrl": source.get("sourceUrl", ""),
                "sourceHash": source.get("sourceHash", ""),
                "sourceLevel": source.get("sourceLevel", "manual_verified"),
                "imageIndex": source.get("imageIndex", ""),
                "rawPath": source.get("rawPath", ""),
                "reviewStatus": review_status,
                "confidence": confidence,
                "parserNotes": notes,
            })
    rows.sort(key=lambda item: (
        item.get("provinceCode", ""),
        item.get("subjectType", ""),
        tuple(int(part) for part in re.findall(r"\d+", str(item.get("imageIndex") or "0"))[:2]),
        item.get("schoolId", ""),
        item.get("groupCode", ""),
    ))
    return rows


def write_csv(path: Path, headers: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in headers})


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def reviewed_file(province_code: str) -> Path:
    return REVIEWED_DIR / province_code / f"group_lines_{province_code.lower()}_2025_reviewed.csv"


def existing_reviewed_rows(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [row for row in csv.DictReader(handle) if any(text(value) for value in row.values())]


def write_reviewed(rows: list[dict[str, Any]], provinces: set[str], overwrite: bool) -> dict[str, Any]:
    report: dict[str, Any] = {}
    for province_code in sorted(provinces):
        province_rows = [row for row in rows if row.get("provinceCode") == province_code]
        if not province_rows:
            continue
        path = reviewed_file(province_code)
        existing = [] if overwrite else existing_reviewed_rows(path)
        merged = [*existing]
        seen = {
            (row.get("schoolId", ""), row.get("universityName", ""), row.get("groupCode", ""), row.get("subjectType", ""))
            for row in existing
        }
        added = 0
        for row in province_rows:
            key = (row.get("schoolId", ""), row.get("universityName", ""), row.get("groupCode", ""), row.get("subjectType", ""))
            if key in seen:
                continue
            seen.add(key)
            merged.append(row)
            added += 1
        write_csv(path, GROUP_LINE_HEADERS, merged)
        report[province_code] = {"path": str(path), "existing": len(existing), "added": added, "total": len(merged)}
    return report


def write_province_outputs(rows: list[dict[str, Any]], provinces: set[str], summary: dict[str, Any]) -> None:
    for province_code in sorted(provinces):
        province_rows = [row for row in rows if row.get("provinceCode") == province_code]
        province_summary = dict(summary)
        province_summary["provinceCode"] = province_code
        province_summary["draftRows"] = len(province_rows)
        province_summary["groups"] = {
            key: value for key, value in summary.get("groups", {}).items()
            if key.startswith(f"{province_code}_")
        }
        report_dir = REPORTS_DIR / province_code
        draft_path = DRAFT_DIR / f"group_line_vision_draft_{province_code.lower()}.csv"
        queue_path = report_dir / "group_line_review_queue.csv"
        write_csv(draft_path, DRAFT_HEADERS, province_rows)
        write_csv(queue_path, DRAFT_HEADERS, province_rows)
        write_json(report_dir / "group_line_vision_summary.json", province_summary)


def summarize(rows: list[dict[str, Any]], items: list[dict[str, Any]], errors: list[dict[str, Any]]) -> dict[str, Any]:
    groups: dict[str, dict[str, Any]] = {}
    for row in rows:
        key = f"{row.get('provinceCode')}_{row.get('subjectType')}"
        item = groups.setdefault(key, {"rows": 0, "groups": set(), "needsManualReview": 0})
        item["rows"] += 1
        item["groups"].add((row.get("schoolId"), row.get("universityName"), row.get("groupCode")))
        if row.get("reviewStatus") == "needs_manual_review":
            item["needsManualReview"] += 1
    serializable_groups = {
        key: {**value, "groups": len(value["groups"])}
        for key, value in groups.items()
    }
    return {
        "sourceImages": len(items),
        "draftRows": len(rows),
        "groups": serializable_groups,
        "errors": errors,
        "note": "Draft rows require manual review before production import. 投档人数不等同招生计划，未写入 group_plans。",
    }


def rebuild_from_jsonl(path: Path, provinces: set[str]) -> int:
    extractions: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                extractions.append(json.loads(line))
    rows = normalize_rows(extractions)
    write_csv(DRAFT_DIR / "group_line_vision_draft.csv", DRAFT_HEADERS, rows)
    write_csv(REPORTS_DIR / "group_line_review_queue.csv", DRAFT_HEADERS, rows)
    summary = summarize(rows, [extraction.get("sourceMeta") or {} for extraction in extractions], [])
    write_province_outputs(rows, provinces, summary)
    write_json(REPORTS_DIR / "group_line_vision_summary.json", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--province", default="ALL", help="HB|AH|ALL|HB,AH")
    parser.add_argument("--refresh", action="store_true", help="Refetch source pages/images")
    parser.add_argument("--limit-images", type=int, default=0, help="Limit images per run, 0 means no limit")
    parser.add_argument("--image-slices", type=int, default=1, help="Split each source image vertically before vision extraction")
    parser.add_argument("--timeout", type=int, default=45)
    parser.add_argument("--max-tokens", type=int, default=4096)
    parser.add_argument("--backend", choices=["auto", "vision", "tesseract"], default="auto")
    parser.add_argument("--write-reviewed", action="store_true", help="Write extracted rows into reviewed CSVs")
    parser.add_argument("--overwrite-reviewed", action="store_true")
    parser.add_argument("--confirm-reviewed-write", default="", help="Must be OFFICIAL_GROUP_LINE_REVIEWED when --write-reviewed is used")
    parser.add_argument("--rebuild-from-jsonl", default="", help="Rebuild draft/report outputs from an existing extraction JSONL")
    args = parser.parse_args()

    provinces = selected_provinces(args.province)
    if args.rebuild_from_jsonl:
        return rebuild_from_jsonl(Path(args.rebuild_from_jsonl), provinces)

    sources = [source for source in SOURCES if source.province_code in provinces]
    if not sources:
        raise SystemExit("No group-line image sources selected.")

    items: list[dict[str, Any]] = []
    for source in sources:
        items.extend(source_items(source, args.timeout, args.refresh))
    if args.limit_images > 0:
        items = items[: args.limit_images]
    items = expand_image_slices(items, args.image_slices)

    errors: list[dict[str, Any]] = []
    extractions: list[dict[str, Any]] = []
    out_jsonl = DRAFT_DIR / "group_line_vision_extractions.jsonl"
    out_jsonl.parent.mkdir(parents=True, exist_ok=True)
    with out_jsonl.open("w", encoding="utf-8") as handle:
        for idx, item in enumerate(items, 1):
            print(f"[{idx}/{len(items)}] extracting {item['provinceCode']} {item['subjectType']} image {item['imageIndex']}: {item['sourceUrl']}")
            try:
                if args.backend == "tesseract":
                    extraction = extract_with_tesseract(item, args.timeout)
                else:
                    try:
                        extraction = call_model(item, args.max_tokens, args.timeout)
                    except (TimeoutError, urllib.error.URLError, OSError, KeyError, json.JSONDecodeError):
                        if args.backend == "vision":
                            raise
                        extraction = extract_with_tesseract(item, args.timeout)
            except (TimeoutError, urllib.error.URLError, OSError, KeyError, json.JSONDecodeError, RuntimeError, subprocess.SubprocessError) as exc:
                errors.append({
                    "provinceCode": item.get("provinceCode"),
                    "subjectType": item.get("subjectType"),
                    "sourceUrl": item.get("sourceUrl"),
                    "error": str(exc),
                })
                continue
            handle.write(json.dumps(extraction, ensure_ascii=False) + "\n")
            extractions.append(extraction)

    rows = normalize_rows(extractions)
    write_csv(DRAFT_DIR / "group_line_vision_draft.csv", DRAFT_HEADERS, rows)
    write_csv(REPORTS_DIR / "group_line_review_queue.csv", DRAFT_HEADERS, rows)
    summary = summarize(rows, items, errors)
    write_province_outputs(rows, provinces, summary)

    if args.write_reviewed:
        if args.confirm_reviewed_write != "OFFICIAL_GROUP_LINE_REVIEWED":
            raise SystemExit("Refuse reviewed write: set --confirm-reviewed-write OFFICIAL_GROUP_LINE_REVIEWED")
        summary["reviewedWrite"] = write_reviewed(rows, provinces, args.overwrite_reviewed)

    write_json(REPORTS_DIR / "group_line_vision_summary.json", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if not errors else 2


if __name__ == "__main__":
    sys.exit(main())
