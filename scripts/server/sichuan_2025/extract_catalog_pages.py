#!/usr/bin/env python3.11
"""Batch-extract Sichuan招生计划合订本 catalog pages with a vision model.

Source: https://plan.sceea.cn/img/{lk|wk}/{lk|wk} (N).gif
- lk = 理科 = 物理类 (232 pages, 本科 5..61)
- wk = 文科 = 历史类 (140 pages, 本科 5..61)

Each page produces a JSON file with extracted rows. Resumable: re-runs skip
pages that already have a non-empty JSON output.

ENV:
  GZLY_VISION_BASE_URL, GZLY_VISION_API_KEY  (loaded from /etc/gzly/vision.env)
  VISION_MODEL_OVERRIDE   default mimo-v2.5
  VISION_MAX_TOKENS       default 32000
  VISION_TIMEOUT_SECONDS  default 240
"""
from __future__ import annotations

import argparse
import base64
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from io import BytesIO
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("[fatal] need pillow: pip install pillow", file=sys.stderr)
    sys.exit(2)


PROMPT_TMPL = (
    "你是高考公开数据抽取助手。这是四川省2025年普通高校招生专业目录的一页扫描图，"
    "类别：{subject_label}。\n"
    "请逐行抽取页面上所有可识别的院校与专业行。\n"
    "严格输出 JSON 对象，不要 markdown、不要解释、不要多余字段：\n"
    "{{\n"
    "  \"page\": {page_no},\n"
    "  \"subjectType\": \"{subject_type}\",\n"
    "  \"batchHints\": [\"该页观察到的批次名\"],\n"
    "  \"rows\": [\n"
    "    {{\n"
    "      \"schoolCode\": \"4位院校代号\",\n"
    "      \"universityName\": \"院校名称（去掉括号地址）\",\n"
    "      \"groupCode\": \"3位专业组代号\",\n"
    "      \"majorCode\": \"2位专业代号\",\n"
    "      \"majorName\": \"专业名称\",\n"
    "      \"firstSubject\": \"物理|历史\",\n"
    "      \"resubject\": \"化学|生物|不限|...\",\n"
    "      \"planCount\": \"计划数 整数字符串\",\n"
    "      \"tuition\": \"学费 元/年 整数字符串\",\n"
    "      \"studyYears\": \"学制 整数字符串\",\n"
    "      \"batch\": \"批次名 如本科批B段\"\n"
    "    }}\n"
    "  ]\n"
    "}}\n"
    "规则：1) 若某字段不可见就留空字符串；2) 不要凭空生成；3) 院校代号与专业代号必须是图上明确出现的；"
    "4) 院校名称去掉括号地址，例如 \"西南医科大学(四川省泸州市)\" -> \"西南医科大学\"。"
)

SUBJECT_LABEL = {
    "物理类": "理科（首选物理）",
    "历史类": "文科（首选历史）",
}


def api_config() -> tuple[str, str, str]:
    base_url = os.environ.get("GZLY_VISION_BASE_URL", "")
    api_key = os.environ.get("GZLY_VISION_API_KEY", "")
    model = (
        os.environ.get("VISION_MODEL_OVERRIDE")
        or os.environ.get("GZLY_VISION_MODEL")
        or "mimo-v2.5"
    )
    if not base_url or not api_key:
        raise SystemExit("Missing GZLY_VISION_BASE_URL / GZLY_VISION_API_KEY")
    return base_url.rstrip("/"), api_key, model


def download_page(url: str, target: Path) -> bytes:
    if target.exists() and target.stat().st_size > 1000:
        return target.read_bytes()
    req = urllib.request.Request(url, headers={"User-Agent": "GZLY-catalog-fetch/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = r.read()
    target.write_bytes(data)
    return data


def to_png_b64(raw: bytes) -> str:
    im = Image.open(BytesIO(raw)).convert("RGB")
    buf = BytesIO()
    im.save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode()


def call_vision(model: str, prompt: str, img_b64: str, *, max_tokens: int, timeout: int) -> dict:
    base_url, api_key, _ = api_config()
    endpoint = base_url + "/chat/completions"
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你按要求输出严格的 JSON。"},
            {"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": "data:image/png;base64," + img_b64}},
            ]},
        ],
        "temperature": 0,
        "max_tokens": max_tokens,
        "chat_template_kwargs": {"enable_thinking": False},
        "reasoning_effort": "low",
    }
    req = urllib.request.Request(
        endpoint,
        data=json.dumps(body).encode(),
        headers={"Authorization": "Bearer " + api_key, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def _strip_markdown_fence(text: str) -> str:
    t = (text or "").strip()
    if not t.startswith("```"):
        return t
    # strip opening fence (```json or just ```)
    nl = t.find("\n")
    if nl < 0:
        return t
    t = t[nl + 1:]
    # strip trailing ``` if present
    if t.rstrip().endswith("```"):
        t = t.rstrip()[:-3]
    return t.strip()


def _recover_truncated_rows(t: str) -> list[dict] | None:
    """Try to extract complete row objects from a truncated `"rows": [ ... ]` body."""
    rows_idx = t.find('"rows"')
    if rows_idx < 0:
        return None
    bracket_idx = t.find("[", rows_idx)
    if bracket_idx < 0:
        return None
    # walk the array char by char tracking brace depth, collect complete objects
    depth_obj = 0
    obj_start = -1
    rows: list[dict] = []
    in_string = False
    escape = False
    for i in range(bracket_idx + 1, len(t)):
        ch = t[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
            continue
        if ch == "{":
            if depth_obj == 0:
                obj_start = i
            depth_obj += 1
        elif ch == "}":
            depth_obj -= 1
            if depth_obj == 0 and obj_start >= 0:
                obj_text = t[obj_start:i + 1]
                try:
                    rows.append(json.loads(obj_text))
                except Exception:
                    pass
                obj_start = -1
        elif ch == "]" and depth_obj == 0:
            break
    return rows or None


def parse_json_safe(text: str) -> dict:
    t = _strip_markdown_fence(text)
    if not t:
        return {"rows": [], "_parse_error": "empty", "_raw": ""}
    # try direct parse
    try:
        return json.loads(t)
    except Exception:
        pass
    # truncated recovery: collect any complete row objects we can find
    rows = _recover_truncated_rows(t)
    if rows:
        return {"rows": rows, "_parse_error": "recovered_truncated"}
    return {"rows": [], "_parse_error": "json_decode", "_raw": t[:1000]}


def process_page(
    *,
    series: str,
    page: int,
    subject_type: str,
    cache_dir: Path,
    out_dir: Path,
    model: str,
    max_tokens: int,
    timeout: int,
    force: bool,
) -> dict:
    filename = f"{series} ({page}).gif"
    img_url = "https://plan.sceea.cn/img/" + series + "/" + urllib.parse.quote(filename)
    cache_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"{series}_{page:03d}.json"
    if out_file.exists() and not force:
        try:
            existing = json.loads(out_file.read_text(encoding="utf-8"))
            if existing.get("rows"):
                return {"skipped": True, "page": page, "rows": len(existing["rows"]), "elapsed": 0.0}
        except Exception:
            pass

    raw = download_page(img_url, cache_dir / f"{series}_{page:03d}.gif")
    img_b64 = to_png_b64(raw)
    prompt = PROMPT_TMPL.format(
        page_no=page,
        subject_type=subject_type,
        subject_label=SUBJECT_LABEL.get(subject_type, subject_type),
    )

    t0 = time.time()
    try:
        resp = call_vision(model, prompt, img_b64, max_tokens=max_tokens, timeout=timeout)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode(errors="replace")[:500]
        out_file.write_text(json.dumps({"error": f"http {e.code}", "body": err_body, "page": page}, ensure_ascii=False), encoding="utf-8")
        return {"error": f"http {e.code}", "page": page, "elapsed": time.time() - t0}
    except Exception as e:
        out_file.write_text(json.dumps({"error": str(e), "page": page}, ensure_ascii=False), encoding="utf-8")
        return {"error": str(e), "page": page, "elapsed": time.time() - t0}
    elapsed = time.time() - t0

    choice = resp["choices"][0]
    content = choice["message"].get("content") or ""
    # some MiMo responses put reasoning/output in alternate fields
    reasoning_content = choice["message"].get("reasoning_content") or ""
    finish_reason = choice.get("finish_reason", "")
    usage = resp.get("usage", {})

    parsed = parse_json_safe(content)
    if not parsed.get("rows") and reasoning_content:
        # fallback: model put final answer in reasoning_content
        parsed_alt = parse_json_safe(reasoning_content)
        if parsed_alt.get("rows"):
            parsed = parsed_alt

    parsed.setdefault("page", page)
    parsed.setdefault("subjectType", subject_type)
    parsed["_finish_reason"] = finish_reason
    parsed["_usage"] = usage
    parsed["_elapsed_seconds"] = round(elapsed, 1)
    parsed["_source_url"] = img_url
    parsed["_model"] = model
    if not parsed.get("rows"):
        parsed["_raw_content"] = content[:4000]
        parsed["_raw_reasoning"] = reasoning_content[:4000]
    out_file.write_text(json.dumps(parsed, ensure_ascii=False, indent=2), encoding="utf-8")
    return {
        "page": page,
        "rows": len(parsed.get("rows", []) or []),
        "elapsed": round(elapsed, 1),
        "finish_reason": finish_reason,
        "usage": usage,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--series", choices=["lk", "wk"], required=True, help="lk=物理类, wk=历史类")
    ap.add_argument("--pages", required=True, help="页码范围，如 5-14 或 5,8,30")
    ap.add_argument("--cache-dir", default="/root/gzly_scraper/sichuan_2025/catalog_cache")
    ap.add_argument("--output-dir", default="/root/gzly_scraper/sichuan_2025/catalog_extract")
    ap.add_argument("--max-tokens", type=int, default=32000)
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("--model", default="")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    if args.model:
        os.environ["VISION_MODEL_OVERRIDE"] = args.model

    pages: list[int] = []
    for chunk in args.pages.split(","):
        if "-" in chunk:
            a, b = chunk.split("-", 1)
            pages.extend(range(int(a), int(b) + 1))
        elif chunk.strip():
            pages.append(int(chunk))

    subject = "物理类" if args.series == "lk" else "历史类"
    model = os.environ.get("VISION_MODEL_OVERRIDE") or os.environ.get("GZLY_VISION_MODEL") or "mimo-v2.5"
    print(f"[catalog] series={args.series} subject={subject} model={model} pages={pages}")

    summary = []
    total_rows = 0
    total_elapsed = 0.0
    for p in pages:
        info = process_page(
            series=args.series,
            page=p,
            subject_type=subject,
            cache_dir=Path(args.cache_dir),
            out_dir=Path(args.output_dir),
            model=model,
            max_tokens=args.max_tokens,
            timeout=args.timeout,
            force=args.force,
        )
        summary.append(info)
        rows = info.get("rows", 0) or 0
        total_rows += rows
        total_elapsed += info.get("elapsed", 0.0) or 0.0
        mark = "SKIP" if info.get("skipped") else ("ERR " if info.get("error") else "OK  ")
        extra = ""
        if info.get("usage"):
            extra = f" tokens={info['usage'].get('total_tokens')} finish={info.get('finish_reason')}"
        print(f"  {mark} page {p:3d}: rows={rows} elapsed={info.get('elapsed', 0)}s{extra} {info.get('error','')}")

    print(f"\n[catalog] total {len(pages)} pages -> {total_rows} rows in {total_elapsed:.1f}s")
    print(f"[catalog] outputs at {args.output_dir}/")


if __name__ == "__main__":
    main()
