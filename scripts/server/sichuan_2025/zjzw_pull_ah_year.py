"""按年份拉取安徽（AH）院校专业组调档线（zjzw.cn ↔ gaokao.cn）。

与 `zjzw_pull_ah_2025.py`（硬编码 2025）相比，本脚本接受 `--year` 参数，
方便回填 AH 2024（新高考首年，与 2025 口径一致）以及未来 2026 出分后的回扫，
是 `build_training_csv_ah.py` 的上游数据采集器。

口径约束：
- ``local_province_id=34`` 即安徽（zjzw 内部省份代码）。
- 仅收取 `local_batch_name` 非空、`min` / `min_section` 为正数的样本，
  确保下游 `import_province_group_lines.py` 不会丢行。
- 来源等级 `school_verified`：gaokao.cn 的省份分数页是高校招生官网披露分数的二次聚合，
  非省考试院一手数据；reviewed 阶段必须用 `merge_zjzw_to_reviewed_ah.py`（或 v2）
  把 source URL 改成各校 `uni_official_link` 已审核 URL，才能进入 admin payload。

典型用法（在服务器 /root/gzly_scraper/sichuan_2025/ 目录）::

    # 回填 AH 2024（新高考首年），全量约 2400 院校 × 1.5s ≈ 1 小时
    set -a; source /etc/gzly/gzly.env; set +a
    python3.11 zjzw_pull_ah_year.py --year 2024 \
        --sleep 1.5 \
        --output draft/zjzw_full_ah_2024_$(date +%Y%m%d_%H%M%S).csv

    # 2026 出分后回扫
    python3.11 zjzw_pull_ah_year.py --year 2026 \
        --sleep 1.5 \
        --output draft/zjzw_full_ah_2026_$(date +%Y%m%d_%H%M%S).csv
"""
import argparse
import csv
import json
import os
import time
import urllib.request

import pymysql

PROXY = "http://127.0.0.1:7891"
UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
REFERER = "https://www.gaokao.cn/"
AH_LOCAL_PROVINCE_ID = 34


def open_mysql():
    pw = os.environ.get("MYSQL_PWD") or os.environ.get("GZLY_DB_PASSWORD")
    if not pw:
        raise SystemExit("MYSQL_PWD / GZLY_DB_PASSWORD 未设置，请先 source /etc/gzly/gzly.env")
    return pymysql.connect(
        host="127.0.0.1", user="root", password=pw, database="gzly", charset="utf8mb4"
    )


def fetch_province_scores(school_id: int):
    url = (
        "https://api.zjzw.cn/web/api/?uri=apidata/api/gk/score/province"
        f"&school_id={school_id}&local_province_id={AH_LOCAL_PROVINCE_ID}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": REFERER})
    proxy_handler = urllib.request.ProxyHandler({"http": PROXY, "https": PROXY})
    opener = urllib.request.build_opener(proxy_handler)
    try:
        with opener.open(req, timeout=12) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception as e:
        return {"code": "EXC", "message": str(e)}


def normalize_subject(t: str) -> str:
    if not t:
        return ""
    if "物理" in t or "理科" in t:
        return "物理类"
    if "历史" in t or "文科" in t:
        return "历史类"
    return t


def parse_resubject(sg_info: str) -> str:
    """从 ``首选物理，再选化学`` / ``首选历史，再选不限`` 提取再选要求。"""
    if not sg_info:
        return ""
    sg = sg_info.replace(" ", "")
    if "再选" not in sg:
        return ""
    seg = sg.split("再选", 1)[1]
    for sep in ["，", ",", "、"]:
        if sep in seg:
            seg = seg.split(sep)[0] if seg.endswith(
                ("化学", "物理", "生物", "政治", "历史", "地理", "不限")
            ) else seg
    return seg.strip().rstrip("。")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--year", type=int, required=True, help="目标年份，例如 2024 / 2025 / 2026")
    ap.add_argument("--limit", type=int, default=0, help="可选：限制院校数量（调试用）")
    ap.add_argument("--sleep", type=float, default=1.5, help="每个院校之间 sleep 秒数")
    ap.add_argument(
        "--output",
        default="",
        help="输出 CSV 路径，默认 draft/zjzw_full_ah_<year>_<timestamp>.csv",
    )
    ap.add_argument("--start-school-id", type=int, default=0, help="断点续抓的起始 school_id")
    args = ap.parse_args()

    if args.year < 2020 or args.year > 2030:
        raise SystemExit(f"year {args.year} 超出合理范围 (2020-2030)")

    output = args.output or (
        f"draft/zjzw_full_ah_{args.year}_{time.strftime('%Y%m%d_%H%M%S')}.csv"
    )
    os.makedirs(os.path.dirname(output) or ".", exist_ok=True)

    conn = open_mysql()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT school_id, name FROM sys_university "
                'WHERE school_id REGEXP "^[0-9]+$" '
                "AND CAST(school_id AS UNSIGNED) >= %s "
                "ORDER BY CAST(school_id AS UNSIGNED) ASC",
                (args.start_school_id,),
            )
            schools = cur.fetchall()
    finally:
        conn.close()
    if args.limit > 0:
        schools = schools[: args.limit]

    print(f"[zjzw-ah-{args.year}] {len(schools)} schools to query, sleep={args.sleep}s, out={output}")

    out = open(output, "w", newline="", encoding="utf-8-sig")
    writer = csv.writer(out)
    writer.writerow(
        [
            "schoolId", "universityName", "groupCode", "groupName", "subjectType",
            "firstSubjectRequirement", "resubjectRequirement", "minScore", "minRank",
            "planCount", "batch", "sourcePageUrl", "sourceUrl", "sourceHash",
            "sourceLevel", "year", "sgInfo", "localBatchName",
        ]
    )

    rows_written = 0
    api_ok = 0
    api_fail = 0

    for idx, (school_id, name) in enumerate(schools, 1):
        try:
            r = fetch_province_scores(school_id)
        except Exception as e:  # noqa: BLE001
            r = {"code": "EXC", "message": str(e)}

        if r.get("code") == "0000":
            api_ok += 1
            items = (r.get("data") or {}).get("item") or []
            for item in items:
                year = item.get("year")
                if year != args.year:
                    continue
                local_batch = (item.get("local_batch_name") or "").strip()
                if not local_batch:
                    continue
                min_score = item.get("min")
                min_rank = item.get("min_section")
                if not isinstance(min_score, (int, float)) or min_score <= 0:
                    continue
                if not isinstance(min_rank, (int, float)) or min_rank <= 0:
                    continue
                subject_type = normalize_subject(item.get("local_type_name", ""))
                sg_name = (item.get("sg_name") or "").strip().strip("()（）")
                if not sg_name:
                    sg_name = str(item.get("special_group") or "")
                sg_info = item.get("sg_info", "")
                first_sub = (
                    "物理"
                    if subject_type == "物理类"
                    else "历史"
                    if subject_type == "历史类"
                    else ""
                )
                resub = parse_resubject(sg_info)
                source_url = (
                    f"https://www.gaokao.cn/school/{school_id}/provinceline"
                    "?province=%E5%AE%89%E5%BE%BD"
                )
                writer.writerow(
                    [
                        school_id, name, sg_name, "", subject_type,
                        first_sub, resub, int(min_score), int(min_rank),
                        "", local_batch, source_url, source_url, "",
                        "school_verified", year, sg_info, local_batch,
                    ]
                )
                rows_written += 1
        elif r.get("code") == "1069":
            print(
                f"[zjzw-ah-{args.year}] {idx}/{len(schools)} {school_id} {name}: "
                "RATE LIMITED, sleep 30s",
                flush=True,
            )
            time.sleep(30)
            api_fail += 1
        else:
            api_fail += 1

        if idx % 20 == 0:
            print(
                f"[zjzw-ah-{args.year}] {idx}/{len(schools)} "
                f"api_ok={api_ok} api_fail={api_fail} rows={rows_written}",
                flush=True,
            )
        time.sleep(args.sleep)

    out.close()
    print(
        f"[zjzw-ah-{args.year}] DONE: api_ok={api_ok} api_fail={api_fail} "
        f"rows={rows_written} -> {output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
