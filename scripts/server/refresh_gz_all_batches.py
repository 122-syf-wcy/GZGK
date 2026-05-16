#!/usr/bin/env python3
"""贵州全批次专业 / 院校分数线刷新。

设计目标：
1. 在生产服务器后台运行，覆盖 ``sys_university`` 中所有院校。
2. 直接调用 ``api.zjzw.cn`` 的 ``apidata/api/gk/score/special`` 接口
   （和现有 ``refresh_gz_recent.py``、``scrape_major_api.py`` 同构）。
3. 默认抓取 ``2025`` 与 ``2024``、首选科目（物理/历史）+ 艺术类（历史/物理）
   + 体育类（历史/物理）六组 ``local_type_id``：
       - 2073 物理类
       - 2074 历史类
       - 2292 艺术类（历史）
       - 2293 艺术类（物理）
       - 2294 体育类（历史）
       - 2295 体育类（物理）
4. 每个 ``(school_id, year, local_type_id)`` 抓全部分页；写到
   ``data/refresh_gz_all_batches/{batch_id}/...`` 下：
       - ``checkpoint.json``  已完成 ``school_id`` 集合
       - ``raw/items_<sid>.jsonl`` 抓到的原始条目
       - ``summary.json`` 全量统计
       - ``upsert_major_score.sql`` ``INSERT ON DUPLICATE KEY UPDATE``
       - ``upsert_score_line.sql``  按 (school_id, university_name, major_name,
         year, subject_type, batch) 维度幂等 upsert
5. 全量幂等：同一 ``(school_id, major_name, year, subject_type, batch)``
   多次执行只会更新值，不会插入重复行。``data_score_line_gz`` 历史已存在
   重复行（无 unique key），脚本会先按业务键
   ``DELETE`` 当前 (school_id, year, subject_type, batch) 旧值再批量 ``INSERT``
   ，避免新增更多重复。

使用示例
~~~~~~~~

.. code-block:: bash

   # 默认 2024+2025 / 6 个 type / 全量院校
   DB_PASS=*** python3 server/refresh_gz_all_batches.py \\
       --workers 3 --delay 1.5 --background

   # 只抓某一年某批 type
   python3 server/refresh_gz_all_batches.py \\
       --years 2025 --types 2292,2293 --limit 100

脚本约定
~~~~~~~~

* ``--background`` 仅生成 ``run.sh`` 与 ``nohup`` 命令提示；脚本本身不再
  自启动后台，便于上层 ``run_data_gap_supplement.sh`` 类调度。
* 默认 ``--dry-run-import`` 仅生成 SQL，不直接落库，方便人工核验后
  ``mysql gzly < upsert_major_score.sql``。
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

import requests


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_WORK_DIR = SCRIPT_DIR.parent / "data" / "refresh_gz_all_batches"

API_BASE = "https://api.zjzw.cn/web/api/"
API_URI = "apidata/api/gk/score/special"
PROVINCE_ID = "52"
PAGE_SIZE = "20"

DEFAULT_TYPES = [
    ("2073", "物理类"),
    ("2074", "历史类"),
    ("2292", "艺术类（历史）"),
    ("2293", "艺术类（物理）"),
    ("2294", "体育类（历史）"),
    ("2295", "体育类（物理）"),
]
DEFAULT_YEARS = [2024, 2025]

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
    ),
    "Referer": "https://www.gaokao.cn/",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Connection": "close",
}

# 直连模式下使用的长退避；走 mihomo load-balance 代理池时使用 PROXY 短退避，
# 因为命中 1069 时只需切换下一个 exit IP（每次请求都走新 TCP），不需要全局冷却。
RATE_LIMIT_BACKOFFS = [10, 30, 60, 120, 180]
PROXY_RATE_LIMIT_BACKOFFS = [0.5, 1.0, 2.0, 4.0, 8.0]


def _build_proxies_mapping(proxy: str | None) -> dict | None:
    """Translate ``--proxy`` CLI arg into a requests ``proxies={...}`` dict.

    ``proxy`` may be a single HTTP/SOCKS URL (e.g. ``http://127.0.0.1:7891`` or
    ``socks5://127.0.0.1:1080``). Empty / ``"none"`` disables proxying.
    """
    if not proxy or proxy.strip().lower() in {"none", "direct"}:
        return None
    proxy = proxy.strip()
    return {"http": proxy, "https": proxy}

# 与历史 ``scrape_major_api.py`` 保持一致的 major_name 清洗规则：
# 只去掉"（外语语种要求：xxx）"备注，保留其它学费/校区/包含子专业的括号说明，
# 以避免和生产库已有 major_name 不匹配，进而产生重复行。
_FOREIGN_LANG_PATTERN = re.compile(r"（外语语种要求：[^）]*）")


def clean_major_name(name: str | None) -> str:
    if not name:
        return ""
    cleaned = _FOREIGN_LANG_PATTERN.sub("", name)
    return re.sub(r"\s+", " ", cleaned).strip()


# ────────────────────────────────────────────────────────────────────────────────
# 工具
# ────────────────────────────────────────────────────────────────────────────────


def _safe_int(val) -> int | None:
    if val is None or val == "" or val == "-" or val == "--":
        return None
    try:
        return int(float(str(val)))
    except (ValueError, TypeError):
        return None


def sql_quote(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def mysql_value(value) -> str:
    if value is None or value == "":
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return sql_quote(str(value))


# ────────────────────────────────────────────────────────────────────────────────
# 节流
# ────────────────────────────────────────────────────────────────────────────────


class Throttle:
    """每个 worker 自己按 ``delay`` 节拍，全局只共享 cooldown / 计数。

    总 QPS ≈ ``workers / delay``：例如 workers=5 delay=1.0 → 约 5 req/s；
    workers=5 delay=0.5 → 约 10 req/s。命中 1069 限速时进入全局冷却。
    """

    def __init__(self, delay: float):
        self.delay = delay
        self.cool_lock = threading.Lock()
        self.cooldown_until = 0.0
        self.api_calls = 0
        self.rate_hits = 0
        self._tls = threading.local()

    def wait(self) -> None:
        last = getattr(self._tls, "last_request_time", 0.0)
        now = time.time()
        if now < self.cooldown_until:
            time.sleep(self.cooldown_until - now)
            now = time.time()
        elapsed = now - last
        jitter = random.uniform(0, self.delay * 0.4)
        needed = self.delay + jitter - elapsed
        if needed > 0:
            time.sleep(needed)
        self._tls.last_request_time = time.time()
        # counters are append-only ints; CPython atomic enough for stats
        self.api_calls += 1

    def cool(self, seconds: float) -> None:
        with self.cool_lock:
            self.cooldown_until = max(self.cooldown_until, time.time() + seconds)
            self.rate_hits += 1

    def note_rate_limit(self) -> None:
        """Record a 1069 hit without forcing a global cooldown.

        Used in proxy-pool mode: a single 1069 from one exit IP shouldn't pause
        all workers — the next request will rotate to a different exit IP.
        """
        with self.cool_lock:
            self.rate_hits += 1


# ────────────────────────────────────────────────────────────────────────────────
# 抓取
# ────────────────────────────────────────────────────────────────────────────────


@dataclass
class FetchStats:
    schools_seen: int = 0
    schools_with_data: int = 0
    items_fetched: int = 0
    pages_fetched: int = 0
    http_errors: int = 0
    rate_limit_hits: int = 0
    api_calls: int = 0
    failed_pages: int = 0


def fetch_page(
    throttle: Throttle,
    sid: str,
    year: int,
    type_id: str,
    page: int,
    *,
    proxies: dict | None = None,
) -> dict:
    """抓取单页；遇 1069 限速 / HTTP 异常自动指数退避。

    当 ``proxies`` 提供时，所有请求经该代理 (建议指向本地 mihomo 的
    load-balance 端口，例如 ``http://127.0.0.1:7891``)；rate-limit 时仍按指数
    退避，但因为每次连接都会被代理重新路由到不同的 exit IP，1069 几乎不会触发。
    """
    params = {
        "local_province_id": PROVINCE_ID,
        "local_type_id": type_id,
        "page": str(page),
        "school_id": sid,
        "size": PAGE_SIZE,
        "uri": API_URI,
        "year": str(year),
    }
    last_payload: dict = {}
    for attempt in range(5):
        throttle.wait()
        try:
            response = requests.get(
                API_BASE,
                params=params,
                headers=HEADERS,
                timeout=20,
                proxies=proxies,
            )
            payload = response.json()
        except Exception as exc:
            if attempt in (0, 4):
                print(
                    f"    HTTP fail sid={sid} y={year} t={type_id} p={page}: {exc}",
                    flush=True,
                )
            time.sleep(5 + random.uniform(0, 3))
            last_payload = {"code": "FAIL", "message": str(exc)}
            continue
        code = str(payload.get("code", ""))
        if code == "0000":
            return payload
        if code == "1069":
            if proxies:
                wait = PROXY_RATE_LIMIT_BACKOFFS[min(attempt, len(PROXY_RATE_LIMIT_BACKOFFS) - 1)]
                if attempt >= 2:
                    print(
                        f"    rate-limit sid={sid} y={year} t={type_id} p={page} attempt={attempt} (proxy) "
                        f"-> short wait {wait}s",
                        flush=True,
                    )
                throttle.note_rate_limit()
                time.sleep(wait)
            else:
                wait = RATE_LIMIT_BACKOFFS[min(attempt, len(RATE_LIMIT_BACKOFFS) - 1)]
                print(
                    f"    rate-limit sid={sid} y={year} t={type_id} p={page} -> wait {wait}s",
                    flush=True,
                )
                throttle.cool(wait)
                time.sleep(wait + random.uniform(0, 3))
            last_payload = payload
            continue
        last_payload = payload
        if attempt >= 1:
            return payload
        time.sleep(2)
    return last_payload


def fetch_school(
    school: dict,
    *,
    years: Iterable[int],
    types: Iterable[tuple[str, str]],
    throttle: Throttle,
    stats: FetchStats,
    raw_path: Path,
    failures_path: Path | None = None,
    failures_lock: threading.Lock | None = None,
    proxies: dict | None = None,
) -> list[dict]:
    sid = str(school.get("school_id"))
    sname = school.get("name") or f"school_{sid}"
    aggregated: list[dict] = []
    for year in years:
        for type_id, type_label in types:
            page = 1
            while True:
                stats.pages_fetched += 1
                payload = fetch_page(throttle, sid, year, type_id, page, proxies=proxies)
                code = str(payload.get("code"))
                if code != "0000":
                    stats.failed_pages += 1
                    if code in ("FAIL", ""):
                        stats.http_errors += 1
                    if failures_path is not None:
                        failure = {
                            "school_id": sid,
                            "university_name": sname,
                            "year": year,
                            "local_type_id": type_id,
                            "type_label": type_label,
                            "page": page,
                            "code": code,
                            "message": payload.get("message") or payload.get("msg") or "",
                            "recorded_at": datetime.now().isoformat(),
                        }
                        line = json.dumps(failure, ensure_ascii=False) + "\n"
                        if failures_lock is not None:
                            with failures_lock:
                                with failures_path.open("a", encoding="utf-8") as fh:
                                    fh.write(line)
                        else:
                            with failures_path.open("a", encoding="utf-8") as fh:
                                fh.write(line)
                    break
                data_block = payload.get("data") or {}
                items = data_block.get("item") or []
                num_found = data_block.get("numFound") or 0
                if not items:
                    break
                for it in items:
                    cleaned_major = clean_major_name(it.get("spname"))
                    if not cleaned_major:
                        continue
                    record = {
                        "school_id": sid,
                        "university_name": sname,
                        "major_name": cleaned_major,
                        "major_id": str(it.get("special_id") or "").strip(),
                        "year": int(year),
                        "subject_type": (it.get("local_type_name") or type_label).strip(),
                        "batch": (it.get("local_batch_name") or "").strip(),
                        "resubject_requirement": (it.get("sg_info") or it.get("special_type") or "").strip(),
                        "min_score": _safe_int(it.get("min")),
                        "max_score": _safe_int(it.get("max")),
                        "avg_score": _safe_int(it.get("average")),
                        "min_rank": _safe_int(it.get("min_section")),
                        "plan_count": _safe_int(it.get("num")),
                        "raw_local_type_id": type_id,
                    }
                    aggregated.append(record)
                stats.items_fetched += len(items)
                if page * int(PAGE_SIZE) >= int(num_found):
                    break
                page += 1
    stats.api_calls = throttle.api_calls
    stats.rate_limit_hits = throttle.rate_hits
    if aggregated:
        stats.schools_with_data += 1
        raw_path.mkdir(parents=True, exist_ok=True)
        out_file = raw_path / f"items_{sid}.jsonl"
        with out_file.open("w", encoding="utf-8") as fh:
            for r in aggregated:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    stats.schools_seen += 1
    return aggregated


# ────────────────────────────────────────────────────────────────────────────────
# 数据库导出
# ────────────────────────────────────────────────────────────────────────────────


def write_major_score_sql(rows: list[dict], output_path: Path) -> None:
    """生成 ``data_major_score_gz`` 幂等 upsert。

    依赖现有 ``UNIQUE KEY uk_record(school_id, major_name(100), year, subject_type, batch(30))``。
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        output_path.write_text(
            "-- no rows generated, nothing to upsert into data_major_score_gz\n",
            encoding="utf-8",
        )
        return
    with output_path.open("w", encoding="utf-8") as fh:
        fh.write("-- generated by refresh_gz_all_batches.py\n")
        fh.write("-- idempotent: ON DUPLICATE KEY UPDATE on uk_record\n\n")
        fh.write(
            "INSERT INTO `data_major_score_gz` (\n"
            "  `school_id`, `university_name`, `major_name`, `major_id`, `year`,\n"
            "  `subject_type`, `batch`, `resubject_requirement`, `min_score`,\n"
            "  `max_score`, `avg_score`, `min_rank`, `plan_count`\n"
            ") VALUES\n"
        )
        rendered: list[str] = []
        for r in rows:
            rendered.append(
                "("
                + ", ".join(
                    [
                        mysql_value(r["school_id"]),
                        mysql_value(r["university_name"]),
                        mysql_value(r["major_name"]),
                        mysql_value(r["major_id"]),
                        mysql_value(r["year"]),
                        mysql_value(r["subject_type"]),
                        mysql_value(r["batch"]),
                        mysql_value(r["resubject_requirement"]),
                        mysql_value(r["min_score"]),
                        mysql_value(r["max_score"]),
                        mysql_value(r["avg_score"]),
                        mysql_value(r["min_rank"]),
                        mysql_value(r["plan_count"]),
                    ]
                )
                + ")"
            )
        fh.write(",\n".join(rendered))
        fh.write(
            "\nON DUPLICATE KEY UPDATE\n"
            "  `university_name` = VALUES(`university_name`),\n"
            "  `major_id`        = VALUES(`major_id`),\n"
            "  `resubject_requirement` = VALUES(`resubject_requirement`),\n"
            "  `min_score`       = VALUES(`min_score`),\n"
            "  `max_score`       = VALUES(`max_score`),\n"
            "  `avg_score`       = VALUES(`avg_score`),\n"
            "  `min_rank`        = VALUES(`min_rank`),\n"
            "  `plan_count`      = VALUES(`plan_count`);\n"
        )


def write_score_line_sql(rows: list[dict], output_path: Path) -> None:
    """生成 ``data_score_line_gz`` 幂等 upsert。

    该表 **无 unique key**，历史可能存在重复行。脚本对每个
    ``(school_id, year, subject_type, batch)`` 先 ``DELETE`` 旧行（仅覆盖
    本轮抓到的 school×year×subject×batch 组合），再批量 ``INSERT`` 新行；
    其他 batch / 历史数据保持不变。
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        output_path.write_text(
            "-- no rows generated, nothing to upsert into data_score_line_gz\n",
            encoding="utf-8",
        )
        return
    groups: dict[tuple, list[dict]] = {}
    for r in rows:
        key = (
            str(r["school_id"]),
            int(r["year"]),
            r["subject_type"],
            r["batch"],
        )
        groups.setdefault(key, []).append(r)
    with output_path.open("w", encoding="utf-8") as fh:
        fh.write("-- generated by refresh_gz_all_batches.py\n")
        fh.write("-- per-(school_id,year,subject_type,batch) DELETE + INSERT,\n")
        fh.write("-- because data_score_line_gz has no unique key.\n\n")
        for (sid, year, subj, batch), batch_rows in groups.items():
            fh.write(
                f"DELETE FROM `data_score_line_gz` WHERE `school_id` = {mysql_value(sid)} "
                f"AND `year` = {year} AND `subject_type` = {mysql_value(subj)} "
                f"AND `batch` = {mysql_value(batch)};\n"
            )
            fh.write(
                "INSERT INTO `data_score_line_gz` (\n"
                "  `school_id`, `university_name`, `major_name`, `major_id`, `year`,\n"
                "  `subject_type`, `min_score`, `max_score`, `avg_score`, `min_rank`,\n"
                "  `plan_count`, `batch`, `resubject_requirement`\n"
                ") VALUES\n"
            )
            rendered: list[str] = []
            for r in batch_rows:
                rendered.append(
                    "("
                    + ", ".join(
                        [
                            mysql_value(r["school_id"]),
                            mysql_value(r["university_name"]),
                            mysql_value(r["major_name"]),
                            mysql_value(r["major_id"]),
                            mysql_value(r["year"]),
                            mysql_value(r["subject_type"]),
                            mysql_value(r["min_score"]),
                            mysql_value(r["max_score"]),
                            mysql_value(r["avg_score"]),
                            mysql_value(r["min_rank"]),
                            mysql_value(r["plan_count"]),
                            mysql_value(r["batch"]),
                            mysql_value(r["resubject_requirement"]),
                        ]
                    )
                    + ")"
                )
            fh.write(",\n".join(rendered))
            fh.write(";\n\n")


def load_rows_from_raw(raw_path: Path) -> list[dict]:
    """Load all previously fetched JSONL rows for a batch.

    ``--resume`` may skip schools that already finished in an earlier process.
    Rebuilding SQL from the raw JSONL files keeps the final SQL complete even
    after interruptions or multi-day foreground runs.
    """
    rows: list[dict] = []
    if not raw_path.exists():
        return rows
    for path in sorted(raw_path.glob("items_*.jsonl")):
        with path.open("r", encoding="utf-8") as fh:
            for line_no, line in enumerate(fh, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError as exc:
                    raise ValueError(f"Invalid JSON in {path}:{line_no}: {exc}") from exc
    return rows


def summarize_rows(rows: list[dict]) -> dict:
    subjects: dict[str, int] = {}
    batches: dict[str, int] = {}
    schools_with_rows: set[str] = set()
    for row in rows:
        schools_with_rows.add(str(row.get("school_id") or ""))
        subject_key = f"{row.get('year')}|{row.get('subject_type')}"
        batch_key = f"{row.get('year')}|{row.get('subject_type')}|{row.get('batch')}"
        subjects[subject_key] = subjects.get(subject_key, 0) + 1
        batches[batch_key] = batches.get(batch_key, 0) + 1
    return {
        "raw_rows": len(rows),
        "raw_schools_with_rows": len([s for s in schools_with_rows if s]),
        "raw_subject_breakdown": dict(sorted(subjects.items())),
        "raw_batch_breakdown": dict(sorted(batches.items())),
    }


def load_failed_school_ids(failures_path: Path) -> set[str]:
    """Return school IDs that had at least one failed page in this batch."""
    failed: set[str] = set()
    if not failures_path.exists():
        return failed
    with failures_path.open("r", encoding="utf-8") as fh:
        for line_no, line in enumerate(fh, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON in {failures_path}:{line_no}: {exc}") from exc
            sid = str(row.get("school_id") or "").strip()
            if sid:
                failed.add(sid)
    return failed


# ────────────────────────────────────────────────────────────────────────────────
# 数据库读取
# ────────────────────────────────────────────────────────────────────────────────


def load_schools_from_db(args: argparse.Namespace) -> list[dict]:
    if not args.db_pass:
        raise SystemExit("--db-pass / DB_PASS is required to enumerate schools")
    env = os.environ.copy()
    env["MYSQL_PWD"] = args.db_pass
    cmd = [
        "mysql",
        "-N",
        "-B",
        "-u",
        args.db_user,
        "-h",
        args.db_host,
        "-P",
        str(args.db_port),
        args.db_name,
        "-e",
        "SELECT school_id, name FROM sys_university WHERE school_id IS NOT NULL "
        "AND TRIM(school_id) <> '' ORDER BY CAST(school_id AS UNSIGNED)",
    ]
    out = subprocess.run(cmd, env=env, check=True, stdout=subprocess.PIPE, text=True).stdout
    schools: list[dict] = []
    for line in out.splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        sid, name = parts[0].strip(), parts[1].strip()
        if sid:
            schools.append({"school_id": sid, "name": name})
    return schools


# ────────────────────────────────────────────────────────────────────────────────
# CLI
# ────────────────────────────────────────────────────────────────────────────────


def parse_types(text: str | None) -> list[tuple[str, str]]:
    if not text:
        return list(DEFAULT_TYPES)
    by_id = {tid: label for tid, label in DEFAULT_TYPES}
    selected: list[tuple[str, str]] = []
    for token in text.split(","):
        token = token.strip()
        if not token:
            continue
        label = by_id.get(token, f"local_type_{token}")
        selected.append((token, label))
    return selected


def parse_years(text: str | None) -> list[int]:
    if not text:
        return list(DEFAULT_YEARS)
    return [int(x) for x in text.split(",") if x.strip()]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--years", default=None,
                        help="逗号分隔年份，默认 2024,2025")
    parser.add_argument("--types", default=None,
                        help="逗号分隔 local_type_id；默认 2073,2074,2292,2293,2294,2295")
    parser.add_argument("--limit", type=int, default=0,
                        help="只处理前 N 所院校（调试用）")
    parser.add_argument("--school-ids", default=None,
                        help="只处理指定 school_id，逗号分隔；用于按 failures/log 二次补抓")
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--delay", type=float, default=1.5)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    parser.add_argument("--batch-id", default=None,
                        help="本轮抓取 ID，默认 timestamp；目录:work_dir/<batch_id>")
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--db-pass", default=os.getenv("DB_PASS", ""))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "gzly"))
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--resume", action="store_true",
                        help="恢复同一 batch_id 之前未完成的 schools")
    parser.add_argument("--print-only", action="store_true",
                        help="只读出 schools / 配置摘要，不抓取")
    parser.add_argument("--proxy", default=os.getenv("GZLY_API_PROXY"),
                        help="HTTP/SOCKS 代理 URL，例如 http://127.0.0.1:7891；"
                             "建议指向 gzly-mihomo 的 mixed-port 以做 IP 轮询。"
                             "可用 GZLY_API_PROXY 环境变量传入；'none' 表示直连。")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    years = parse_years(args.years)
    types = parse_types(args.types)
    batch_id = args.batch_id or datetime.now().strftime("%Y%m%d%H%M%S")
    work_dir: Path = args.work_dir / batch_id
    work_dir.mkdir(parents=True, exist_ok=True)
    checkpoint_path = work_dir / "checkpoint.json"
    raw_path = work_dir / "raw"
    raw_path.mkdir(exist_ok=True)

    schools = load_schools_from_db(args)
    if args.school_ids:
        selected_ids = {x.strip() for x in args.school_ids.split(",") if x.strip()}
        schools = [s for s in schools if str(s.get("school_id")) in selected_ids]
    if args.limit > 0:
        schools = schools[: args.limit]

    done: set[str] = set()
    if args.resume and checkpoint_path.exists():
        try:
            done = set(json.loads(checkpoint_path.read_text(encoding="utf-8")).get("done", []))
            print(f"[resume] already done schools = {len(done)}", flush=True)
        except Exception:
            done = set()

    print(
        f"batch_id={batch_id} schools={len(schools)} years={years} types={[t[0] for t in types]}"
        f" workers={args.workers} delay={args.delay} work_dir={work_dir}",
        flush=True,
    )
    if args.print_only:
        return 0

    throttle = Throttle(delay=args.delay)
    stats = FetchStats()
    proxies = _build_proxies_mapping(args.proxy)
    all_rows: list[dict] = []
    aggregate_lock = threading.Lock()
    failures_lock = threading.Lock()
    failures_path = work_dir / "failures.jsonl"

    pending = [s for s in schools if str(s["school_id"]) not in done]
    started_at = time.time()
    completed_counter = {"n": 0}
    if proxies:
        print(f"using proxy={args.proxy}", flush=True)
    else:
        print("using direct connection (no proxy)", flush=True)

    def persist_checkpoint():
        checkpoint_path.write_text(
            json.dumps({"done": sorted(done), "updated_at": datetime.now().isoformat()},
                       ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    def handle(school: dict):
        try:
            rows = fetch_school(
                school,
                years=years,
                types=types,
                throttle=throttle,
                stats=stats,
                raw_path=raw_path,
                failures_path=failures_path,
                failures_lock=failures_lock,
                proxies=proxies,
            )
        except Exception as exc:
            print(f"  ! sid={school.get('school_id')} {school.get('name')} fail: {exc}",
                  flush=True)
            return
        with aggregate_lock:
            all_rows.extend(rows)
            done.add(str(school["school_id"]))
            completed_counter["n"] += 1
            n = completed_counter["n"]
            if n % 20 == 0 or n == len(pending):
                persist_checkpoint()
                elapsed = time.time() - started_at
                rate = n / elapsed if elapsed > 0 else 0
                eta = (len(pending) - n) / rate if rate > 0 else 0
                print(
                    f"  [{n}/{len(pending)}] schools done={len(done)} items={stats.items_fetched}"
                    f" api={stats.api_calls} rate_limit={stats.rate_limit_hits}"
                    f" elapsed={elapsed:.0f}s rate={rate:.2f}/s ETA={eta:.0f}s",
                    flush=True,
                )

    if not pending:
        print("No pending schools; checkpoint already complete.", flush=True)
    else:
        with ThreadPoolExecutor(max_workers=args.workers) as executor:
            futures = [executor.submit(handle, s) for s in pending]
            for _ in as_completed(futures):
                pass

    persist_checkpoint()

    # 汇总
    elapsed = time.time() - started_at
    sql_rows = load_rows_from_raw(raw_path)
    failed_school_ids = load_failed_school_ids(failures_path)
    complete_sql_rows = [
        row for row in sql_rows
        if str(row.get("school_id") or "").strip() not in failed_school_ids
    ]
    raw_summary = summarize_rows(sql_rows)
    summary = {
        "batch_id": batch_id,
        "completed_at": datetime.now().isoformat(),
        "schools_total": len(schools),
        "schools_processed": len(done),
        "schools_with_data": stats.schools_with_data,
        "items_fetched": stats.items_fetched,
        "api_calls": stats.api_calls,
        "rate_limit_hits": stats.rate_limit_hits,
        "http_errors": stats.http_errors,
        "failed_pages": stats.failed_pages,
        "failed_schools": len(failed_school_ids),
        "complete_sql_rows": len(complete_sql_rows),
        "elapsed_seconds": round(elapsed, 1),
        "years": years,
        "types": types,
        **raw_summary,
    }
    (work_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"summary={json.dumps(summary, ensure_ascii=False)}", flush=True)

    # 输出 SQL：从 raw 目录重建，确保 --resume 后已完成学校也进入最终 SQL。
    if len(sql_rows) != len(all_rows):
        print(
            f"sql rows rebuilt from raw={len(sql_rows)} current_process_rows={len(all_rows)}",
            flush=True,
        )
    if failed_school_ids:
        (work_dir / "failed_school_ids.txt").write_text(
            "\n".join(sorted(failed_school_ids, key=lambda v: int(v) if v.isdigit() else v)) + "\n",
            encoding="utf-8",
        )
        write_major_score_sql(complete_sql_rows, work_dir / "upsert_major_score_complete_only.sql")
        write_score_line_sql(complete_sql_rows, work_dir / "upsert_score_line_complete_only.sql")
        print(
            f"complete-only sql excludes failed schools={len(failed_school_ids)}",
            flush=True,
        )
    write_major_score_sql(sql_rows, work_dir / "upsert_major_score.sql")
    write_score_line_sql(sql_rows, work_dir / "upsert_score_line.sql")
    print(
        f"sql files: {work_dir / 'upsert_major_score.sql'}; {work_dir / 'upsert_score_line.sql'}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
