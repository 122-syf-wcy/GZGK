"""
贵州历年投档分数线爬取脚本（按专业）
数据源: 掌上高考 API (api.eol.cn)
爬取内容: 近5年在贵州招生的所有院校每个专业的投档分数线

用法:
    python scrape_score_lines.py
    python scrape_score_lines.py --year 2024
    python scrape_score_lines.py --year 2024 --subject 5
"""

import argparse
import json
import os
import time
import requests
from tqdm import tqdm
from config import (
    SCORE_LINE_URL, SCHOOL_SCORE_URL, PROVINCE_ID,
    HEADERS, REQUEST_DELAY, MAX_RETRIES, RETRY_DELAY, TIMEOUT,
    YEARS, SUBJECT_TYPES_OLD, SUBJECT_TYPES_NEW, NEW_GAOKAO_START_YEAR,
    SCORE_LINE_DIR, UNIVERSITY_DIR,
)


def fetch_with_retry(url: str, params: dict = None, data: dict = None,
                     method: str = "GET") -> dict | None:
    for attempt in range(MAX_RETRIES):
        try:
            if method == "POST":
                resp = requests.post(url, json=data, headers=HEADERS, timeout=TIMEOUT)
            else:
                resp = requests.get(url, params=params, headers=HEADERS, timeout=TIMEOUT)
            resp.raise_for_status()
            result = resp.json()
            return result
        except Exception as e:
            print(f"  [重试 {attempt + 1}/{MAX_RETRIES}] {e}")
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY)
    return None


def get_subject_types(year: int) -> dict:
    """根据年份返回对应的科类"""
    if year >= NEW_GAOKAO_START_YEAR:
        return SUBJECT_TYPES_NEW
    return SUBJECT_TYPES_OLD


def load_school_ids() -> list[str]:
    """加载已爬取的院校 ID 列表"""
    map_file = os.path.join(UNIVERSITY_DIR, "school_id_map.json")
    if os.path.exists(map_file):
        with open(map_file, "r", encoding="utf-8") as f:
            return list(json.load(f).keys())
    return []


def fetch_school_list_for_year(year: int, subject_id: int,
                               page: int = 1, size: int = 20) -> dict | None:
    """获取某年某科类在贵州招生的院校列表（含概览分数线）"""
    payload = {
        "local_province_id": PROVINCE_ID,
        "year": str(year),
        "type": str(subject_id),
        "local_type_id": "1",   # 本科批
        "page": page,
        "size": size,
        "sort": "min",
        "order": "desc",
    }
    return fetch_with_retry(SCHOOL_SCORE_URL, data=payload, method="POST")


def fetch_major_scores(school_id: str, year: int, subject_id: int,
                       page: int = 1, size: int = 50) -> dict | None:
    """获取某院校某年某科类的专业分数线"""
    payload = {
        "school_id": school_id,
        "local_province_id": PROVINCE_ID,
        "year": str(year),
        "type": str(subject_id),
        "local_type_id": "1",
        "page": page,
        "size": size,
    }
    return fetch_with_retry(SCORE_LINE_URL, data=payload, method="POST")


def scrape_year_subject(year: int, subject_id: int, subject_name: str,
                        school_ids: list[str] | None = None):
    """爬取某一年某科类的全部分数线数据"""
    output_file = os.path.join(SCORE_LINE_DIR, f"score_lines_{year}_{subject_name}.json")

    # 如果已存在且非空，跳过
    if os.path.exists(output_file):
        with open(output_file, "r", encoding="utf-8") as f:
            existing = json.load(f)
        if existing:
            print(f"  ⏭ {year} {subject_name} 已存在 {len(existing)} 条，跳过（删除文件可重新爬取）")
            return existing

    all_records = []

    # 方式1: 如果有院校ID列表，逐校爬取专业分数线
    if school_ids:
        print(f"  按院校逐个爬取专业分数线（{len(school_ids)} 所院校）...")
        for sid in tqdm(school_ids, desc=f"{year} {subject_name}"):
            page = 1
            while True:
                result = fetch_major_scores(sid, year, subject_id, page=page, size=50)
                if not result:
                    break

                data = result.get("data", {})
                items = data.get("item", [])
                if not items:
                    break

                for item in items:
                    record = {
                        "school_id": str(item.get("school_id", sid)),
                        "university_name": item.get("name", item.get("school_name", "")),
                        "major_name": item.get("spname", item.get("sp_name", "")),
                        "major_id": str(item.get("sp_id", item.get("spcode", ""))),
                        "year": year,
                        "subject_type": subject_name,
                        "subject_id": subject_id,
                        "min_score": _safe_int(item.get("min", item.get("min_score"))),
                        "max_score": _safe_int(item.get("max", item.get("max_score"))),
                        "avg_score": _safe_int(item.get("average", item.get("avg_score"))),
                        "min_rank": _safe_int(item.get("min_section",
                                              item.get("min_rank",
                                              item.get("proscore")))),
                        "plan_count": _safe_int(item.get("num",
                                               item.get("plan_num",
                                               item.get("zslx_num")))),
                        "batch": item.get("local_batch_name",
                                         item.get("local_type_name", "本科批")),
                    }
                    if record["major_name"]:
                        all_records.append(record)

                total = data.get("numFound", 0)
                if page * 50 >= total:
                    break
                page += 1
                time.sleep(REQUEST_DELAY * 0.5)

            time.sleep(REQUEST_DELAY)
    else:
        # 方式2: 先获取有分数线的院校列表，再逐校爬专业
        print(f"  先获取院校分数线概览...")
        school_overview = []
        page = 1
        total = None
        while True:
            result = fetch_school_list_for_year(year, subject_id, page=page, size=30)
            if not result:
                break

            data = result.get("data", {})
            if total is None:
                total = data.get("numFound", 0)
                print(f"    共 {total} 所院校有分数线")

            items = data.get("item", [])
            if not items:
                break

            for item in items:
                school_overview.append({
                    "school_id": str(item.get("school_id", "")),
                    "name": item.get("name", ""),
                    "min": _safe_int(item.get("min")),
                    "max": _safe_int(item.get("max")),
                    "min_section": _safe_int(item.get("min_section")),
                })

            if len(school_overview) >= total:
                break
            page += 1
            time.sleep(REQUEST_DELAY)

        # 逐校爬取专业分数线
        print(f"  逐校爬取专业分数线（{len(school_overview)} 所）...")
        for school in tqdm(school_overview, desc=f"{year} {subject_name}"):
            sid = school["school_id"]
            pg = 1
            while True:
                result = fetch_major_scores(sid, year, subject_id, page=pg, size=50)
                if not result:
                    break

                data = result.get("data", {})
                items = data.get("item", [])
                if not items:
                    break

                for item in items:
                    record = {
                        "school_id": str(item.get("school_id", sid)),
                        "university_name": item.get("name",
                                           item.get("school_name",
                                           school.get("name", ""))),
                        "major_name": item.get("spname", item.get("sp_name", "")),
                        "major_id": str(item.get("sp_id", item.get("spcode", ""))),
                        "year": year,
                        "subject_type": subject_name,
                        "subject_id": subject_id,
                        "min_score": _safe_int(item.get("min", item.get("min_score"))),
                        "max_score": _safe_int(item.get("max", item.get("max_score"))),
                        "avg_score": _safe_int(item.get("average", item.get("avg_score"))),
                        "min_rank": _safe_int(item.get("min_section",
                                              item.get("min_rank",
                                              item.get("proscore")))),
                        "plan_count": _safe_int(item.get("num",
                                               item.get("plan_num",
                                               item.get("zslx_num")))),
                        "batch": item.get("local_batch_name",
                                         item.get("local_type_name", "本科批")),
                    }
                    if record["major_name"]:
                        all_records.append(record)

                numFound = data.get("numFound", 0)
                if pg * 50 >= numFound:
                    break
                pg += 1
                time.sleep(REQUEST_DELAY * 0.5)

            time.sleep(REQUEST_DELAY)

    # 保存
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(all_records, f, ensure_ascii=False, indent=2)
    print(f"  ✅ {year} {subject_name}: {len(all_records)} 条专业分数线 -> {output_file}")

    return all_records


def _safe_int(val) -> int | None:
    """安全转换为整数"""
    if val is None or val == "" or val == "-" or val == "--":
        return None
    try:
        return int(float(str(val)))
    except (ValueError, TypeError):
        return None


def main():
    parser = argparse.ArgumentParser(description="贵州历年投档分数线爬取")
    parser.add_argument("--year", type=int, help="仅爬取指定年份")
    parser.add_argument("--subject", type=int, help="仅爬取指定科类 (1=文/历史, 5=理/物理)")
    args = parser.parse_args()

    print("=" * 60)
    print("  贵州历年投档分数线爬取（按专业）")
    print("  数据源: 掌上高考 (api.eol.cn)")
    print("=" * 60)

    # 加载已爬取的院校 ID
    school_ids = load_school_ids()
    if school_ids:
        print(f"\n已加载 {len(school_ids)} 个院校 ID（来自 universities 爬取结果）")
    else:
        print("\n未找到院校 ID 列表，将通过分数线接口逐步获取")
        print("建议先运行: python scrape_universities.py")
        school_ids = None

    years = [args.year] if args.year else YEARS
    total_records = 0

    for year in years:
        subject_types = get_subject_types(year)

        for sid, sname in subject_types.items():
            if args.subject and sid != args.subject:
                continue

            print(f"\n--- {year} 年 {sname} ---")
            records = scrape_year_subject(year, sid, sname, school_ids)
            total_records += len(records)

    # 汇总
    print(f"\n{'=' * 60}")
    print(f"  爬取完成！")
    print(f"  共 {total_records} 条专业分数线记录")
    print(f"  数据保存在: {SCORE_LINE_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
