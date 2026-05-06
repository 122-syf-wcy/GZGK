"""
用 Playwright 无头浏览器爬取掌上高考真实分数线数据
自动拦截网络请求，提取 JSON 数据

用法:
    python scrape_with_browser.py
    python scrape_with_browser.py --headless false   # 显示浏览器窗口调试
    python scrape_with_browser.py --school 935        # 只爬一所学校
"""

import argparse
import json
import os
import re
import time
from urllib.parse import urlencode, quote
from playwright.sync_api import sync_playwright, Page, Response

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
UNI_DIR = os.path.join(DATA_DIR, "universities")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
IMG_DIR = os.path.join(DATA_DIR, "images")
for d in [DATA_DIR, EXPORT_DIR, UNI_DIR, SCORE_DIR, IMG_DIR]:
    os.makedirs(d, exist_ok=True)

PROVINCE = "贵州"
YEARS = [2024, 2023, 2022, 2021, 2020]
# 2024+ 物理类/历史类; 2020-2023 理科/文科
SUBJECT_MAP = {
    2024: ["物理类", "历史类"],
    2023: ["理科", "文科"],
    2022: ["理科", "文科"],
    2021: ["理科", "文科"],
    2020: ["理科", "文科"],
}


def get_school_ids_from_cdn() -> list[dict]:
    """从 CDN 获取全部院校 ID 列表"""
    import requests
    h = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}
    r = requests.get(
        'https://static-data.gaokao.cn/www/2.0/school/name.json',
        headers=h, timeout=15
    )
    schools = r.json().get('data', [])
    print(f"从 CDN 获取到 {len(schools)} 所院校")
    return schools


def get_school_info_from_cdn(school_id: str) -> dict | None:
    """从 CDN 获取院校详情"""
    import requests
    h = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}
    try:
        r = requests.get(
            f'https://static-data.gaokao.cn/www/2.0/school/{school_id}/info.json',
            headers=h, timeout=10
        )
        if r.status_code == 200:
            return r.json().get('data', {})
    except:
        pass
    return None


def scrape_school_scores(page: Page, school_id: str, school_name: str) -> list[dict]:
    """用 Playwright 访问院校分数线页面，拦截并提取数据"""
    all_records = []
    captured_data = {}

    def on_response(response: Response):
        url = response.url
        # 捕获分数线相关的 JSON 响应
        if response.status == 200:
            for keyword in ['professionalscore', 'provincescore', 'specialscore',
                           'benchmarkScore', 'web?autosign']:
                if keyword in url:
                    try:
                        body = response.json()
                        captured_data[keyword] = body
                    except:
                        try:
                            captured_data[keyword] = response.text()
                        except:
                            pass

    page.on("response", on_response)

    # 访问该校的分数线页面
    url = f"https://www.gaokao.cn/school/{school_id}/provinceline"
    try:
        page.goto(url, wait_until="networkidle", timeout=20000)
    except:
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=15000)
            page.wait_for_timeout(3000)
        except Exception as e:
            print(f"    ⚠️ 页面加载失败: {e}")
            return all_records

    # 等页面加载
    page.wait_for_timeout(2000)

    # 选择贵州省
    try:
        # 查找省份选择器并选择贵州
        province_selectors = [
            'text=贵州',
            '.province-select >> text=贵州',
            'span:has-text("贵州")',
            'div.select-item:has-text("贵州")',
        ]
        for sel in province_selectors:
            try:
                el = page.locator(sel).first
                if el.is_visible(timeout=1000):
                    el.click()
                    page.wait_for_timeout(1500)
                    break
            except:
                continue
    except:
        pass

    # 遍历年份和科类
    for year in YEARS:
        subjects = SUBJECT_MAP.get(year, ["理科", "文科"])
        for subject in subjects:
            captured_data.clear()

            # 尝试选择年份
            try:
                year_el = page.locator(f'text="{year}"').first
                if year_el.is_visible(timeout=1000):
                    year_el.click()
                    page.wait_for_timeout(1000)
            except:
                pass

            # 尝试选择科类
            try:
                subj_el = page.locator(f'text="{subject}"').first
                if subj_el.is_visible(timeout=1000):
                    subj_el.click()
                    page.wait_for_timeout(2000)
            except:
                pass

            # 等待数据加载
            page.wait_for_timeout(1500)

            # 从捕获的数据中提取分数线
            for key, data in captured_data.items():
                if isinstance(data, dict) and 'data' in data:
                    records = extract_records(data['data'], school_id, school_name, year, subject)
                    all_records.extend(records)

            # 如果没有通过拦截获取到数据，尝试从页面 DOM 提取
            if not any(captured_data.values()):
                dom_records = extract_from_dom(page, school_id, school_name, year, subject)
                all_records.extend(dom_records)

    page.remove_listener("response", on_response)
    return all_records


def extract_records(data, school_id: str, school_name: str,
                    year: int, subject: str) -> list[dict]:
    """从 API 响应数据中提取分数线记录"""
    records = []

    # 数据可能有多种结构
    items = []
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        # 尝试常见的数据键
        for key in ['item', 'items', 'list', 'special', 'score']:
            if key in data and isinstance(data[key], list):
                items = data[key]
                break
        # 可能是嵌套字典
        if not items:
            for k, v in data.items():
                if isinstance(v, list) and v and isinstance(v[0], dict):
                    items = v
                    break
                if isinstance(v, dict):
                    for k2, v2 in v.items():
                        if isinstance(v2, list) and v2 and isinstance(v2[0], dict):
                            items.extend(v2)

    for item in items:
        if not isinstance(item, dict):
            continue
        # 提取专业名
        major = (item.get('spname') or item.get('sp_name') or
                item.get('special_name') or item.get('major_name') or
                item.get('name') or '')
        if not major:
            continue

        record = {
            "school_id": school_id,
            "university_name": school_name,
            "major_name": major,
            "major_id": str(item.get('sp_id', item.get('spcode', item.get('special_id', '')))),
            "year": year,
            "subject_type": subject,
            "min_score": safe_int(item.get('min', item.get('min_score'))),
            "max_score": safe_int(item.get('max', item.get('max_score'))),
            "avg_score": safe_int(item.get('average', item.get('avg_score', item.get('avg')))),
            "min_rank": safe_int(item.get('min_section', item.get('min_rank',
                        item.get('proscore', item.get('lowest_rank'))))),
            "plan_count": safe_int(item.get('num', item.get('plan_num',
                          item.get('sp_num', item.get('plan_count'))))),
            "batch": item.get('local_batch_name', item.get('batch_name', '本科批')),
        }
        records.append(record)

    return records


def extract_from_dom(page: Page, school_id: str, school_name: str,
                     year: int, subject: str) -> list[dict]:
    """从页面 DOM 表格中提取分数线"""
    records = []
    try:
        rows = page.query_selector_all('table tbody tr, .score-table .row, .data-row')
        for row in rows:
            cells = row.query_selector_all('td, .cell')
            if len(cells) >= 3:
                texts = [c.inner_text().strip() for c in cells]
                # 尝试解析: 专业名, 招生类型, 最低分/位次
                major = texts[0] if texts[0] else ''
                if not major or major in ['专业名称', '序号']:
                    continue
                record = {
                    "school_id": school_id,
                    "university_name": school_name,
                    "major_name": major,
                    "major_id": "",
                    "year": year,
                    "subject_type": subject,
                    "min_score": safe_int(texts[2] if len(texts) > 2 else None),
                    "max_score": None,
                    "avg_score": None,
                    "min_rank": safe_int(texts[3] if len(texts) > 3 else None),
                    "plan_count": safe_int(texts[4] if len(texts) > 4 else None),
                    "batch": "本科批",
                }
                if record["min_score"]:
                    records.append(record)
    except:
        pass
    return records


def safe_int(val) -> int | None:
    if val is None or val == '' or val == '-' or val == '--':
        return None
    try:
        return int(float(str(val).replace(',', '')))
    except:
        return None


def save_checkpoint(all_records: list, filename: str = "score_lines_checkpoint.json"):
    path = os.path.join(SCORE_DIR, filename)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(all_records, f, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(description="用 Playwright 爬取掌上高考分数线")
    parser.add_argument('--headless', default='true', help='是否无头模式 (true/false)')
    parser.add_argument('--school', type=str, help='只爬指定 school_id')
    parser.add_argument('--limit', type=int, default=0, help='限制爬取院校数量(0=全部)')
    args = parser.parse_args()

    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 (Playwright 浏览器自动化)")
    print("=" * 60)

    # 1) 获取院校列表
    print("\n[1] 获取院校列表...")
    if args.school:
        school_list = [{"school_id": args.school, "name": f"school_{args.school}"}]
    else:
        school_list = get_school_ids_from_cdn()

    if args.limit > 0:
        school_list = school_list[:args.limit]

    print(f"  将爬取 {len(school_list)} 所院校")

    # 2) 获取院校详细信息 (从 CDN)
    print("\n[2] 获取院校详情...")
    universities = []
    import requests
    h_cdn = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}
    for i, s in enumerate(school_list):
        sid = str(s.get('school_id', ''))
        info = get_school_info_from_cdn(sid)
        if info:
            universities.append(info)
        if (i + 1) % 50 == 0:
            print(f"  {i + 1}/{len(school_list)}")
        time.sleep(0.3)

    # 保存院校信息
    if universities:
        uni_path = os.path.join(UNI_DIR, "universities.json")
        with open(uni_path, 'w', encoding='utf-8') as f:
            json.dump(universities, f, ensure_ascii=False, indent=2)
        print(f"  ✅ 保存 {len(universities)} 所院校信息 -> {uni_path}")

    # 3) 用 Playwright 爬取分数线
    print(f"\n[3] 启动浏览器爬取分数线...")
    all_score_records = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        context = browser.new_context(
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
            viewport={'width': 1280, 'height': 800},
            locale='zh-CN',
        )
        pg = context.new_page()

        for i, school in enumerate(school_list):
            sid = str(school.get('school_id', ''))
            sname = school.get('name', f'school_{sid}')
            print(f"\n  [{i + 1}/{len(school_list)}] {sname} (ID:{sid})")

            records = scrape_school_scores(pg, sid, sname)
            all_score_records.extend(records)
            print(f"    获取 {len(records)} 条专业分数线")

            # 每 10 所学校保存一次 checkpoint
            if (i + 1) % 10 == 0:
                save_checkpoint(all_score_records)
                print(f"    💾 checkpoint: {len(all_score_records)} 条")

            time.sleep(1)

        browser.close()

    # 4) 保存最终数据
    print(f"\n[4] 保存数据...")
    final_path = os.path.join(SCORE_DIR, "score_lines_all.json")
    with open(final_path, 'w', encoding='utf-8') as f:
        json.dump(all_score_records, f, ensure_ascii=False, indent=2)

    # 按年份分文件
    by_year = {}
    for r in all_score_records:
        key = f"{r['year']}_{r['subject_type']}"
        by_year.setdefault(key, []).append(r)
    for key, records in by_year.items():
        path = os.path.join(SCORE_DIR, f"score_lines_{key}.json")
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(records, f, ensure_ascii=False, indent=2)

    print(f"\n{'=' * 60}")
    print(f"  爬取完成！")
    print(f"  院校: {len(universities)}")
    print(f"  分数线: {len(all_score_records)} 条")
    print(f"  数据目录: {DATA_DIR}")
    print(f"{'=' * 60}")
    print(f"\n下一步: python export_to_sql.py 导出 SQL")


if __name__ == "__main__":
    main()
