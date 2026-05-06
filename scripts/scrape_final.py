"""
掌上高考分数线爬取 - 交互式 DOM 提取
通过 Playwright 模拟用户操作，选择省份/年份/科类，等待数据渲染后从 DOM 提取

用法:
    python scrape_final.py                    # 测试贵州大学
    python scrape_final.py --all --limit 50   # 前50所
    python scrape_final.py --headless false    # 显示浏览器
"""

import argparse, json, os, time, re
import requests as req_lib
from playwright.sync_api import sync_playwright, Page, TimeoutError as PwTimeout

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
UNI_DIR = os.path.join(DATA_DIR, "universities")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
for d in [SCORE_DIR, UNI_DIR, EXPORT_DIR]:
    os.makedirs(d, exist_ok=True)

YEARS = ["2024", "2023", "2022", "2021", "2020"]
# 2024: 物理类/历史类; 2020-2023: 理科/文科
SUBJECTS_NEW = ["物理类", "历史类"]
SUBJECTS_OLD = ["理科", "文科"]
NEW_GAOKAO_YEAR = 2024


def get_school_list():
    """获取院校列表"""
    h = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}
    r = req_lib.get('https://static-data.gaokao.cn/www/2.0/school/name.json', headers=h, timeout=15)
    return r.json().get('data', [])


def click_text(page: Page, text: str, timeout: int = 3000) -> bool:
    """点击包含指定文本的元素"""
    try:
        # 尝试多种选择器
        for selector in [
            f'span:has-text("{text}")',
            f'div:has-text("{text}")',
            f'a:has-text("{text}")',
            f'li:has-text("{text}")',
            f'button:has-text("{text}")',
            f'text="{text}"',
        ]:
            try:
                loc = page.locator(selector).first
                if loc.is_visible(timeout=500):
                    loc.click()
                    return True
            except:
                continue
    except:
        pass
    return False


def wait_for_table_data(page: Page, timeout_ms: int = 5000) -> bool:
    """等待表格数据行出现"""
    try:
        page.wait_for_selector(
            'table tbody tr td, .score-list .item, [class*="score"] [class*="row"]',
            timeout=timeout_ms
        )
        return True
    except:
        return False


def extract_score_table(page: Page) -> list[list[str]]:
    """提取页面上所有表格的数据行"""
    return page.evaluate("""() => {
        const rows = [];
        // 所有表格
        document.querySelectorAll('table').forEach(table => {
            const trs = table.querySelectorAll('tbody tr');
            trs.forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length >= 2 && cells.some(c => c.length > 0)) {
                    rows.push(cells);
                }
            });
        });
        return rows;
    }""")


def extract_all_visible_data(page: Page) -> dict:
    """提取页面上所有可见的分数线数据（包括非表格形式）"""
    return page.evaluate("""() => {
        const result = {tables: [], texts: [], scoreItems: []};

        // 表格数据
        document.querySelectorAll('table').forEach((table, ti) => {
            const tableData = {headers: [], rows: []};
            table.querySelectorAll('thead th, thead td').forEach(th => {
                tableData.headers.push(th.innerText.trim());
            });
            table.querySelectorAll('tbody tr').forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length >= 2) tableData.rows.push(cells);
            });
            if (tableData.rows.length > 0 || tableData.headers.length > 0) {
                result.tables.push(tableData);
            }
        });

        // 带分数样式的元素
        document.querySelectorAll('[class*="score"], [class*="line"], [class*="major"], [class*="special"]').forEach(el => {
            const text = el.innerText.trim();
            if (text.length > 5 && text.length < 500 && /\d{3}/.test(text)) {
                result.scoreItems.push(text);
            }
        });

        // 主内容区文本（用于解析）
        const main = document.querySelector('.school-score, .province-line, [class*="provinceline"], main, .main-content, #app .content');
        if (main) {
            result.mainText = main.innerText.substring(0, 10000);
        }

        return result;
    }""")


def parse_score_text(text: str, school_id: str, school_name: str, year: str, subject: str) -> list[dict]:
    """从文本中解析分数线数据"""
    records = []
    lines = text.split('\n')
    for line in lines:
        line = line.strip()
        if not line:
            continue
        # 匹配 "专业名 数字/数字" 模式
        m = re.match(r'^(.+?)\s+(\d{3})/(\d+)', line)
        if m:
            records.append({
                "school_id": school_id,
                "university_name": school_name,
                "major_name": m.group(1).strip(),
                "year": int(year),
                "subject_type": subject,
                "min_score": int(m.group(2)),
                "min_rank": int(m.group(3)),
            })
    return records


def scrape_school(page: Page, school_id: str, school_name: str) -> list[dict]:
    """爬取一所学校的所有分数线"""
    all_records = []

    url = f"https://www.gaokao.cn/school/{school_id}/provinceline"
    try:
        page.goto(url, wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(3000)  # 等 JS hydration
    except Exception as e:
        print(f"    ⚠️ 页面加载失败: {e}")
        return all_records

    # 确保选择贵州省
    click_text(page, "贵州")
    page.wait_for_timeout(1500)

    for year in YEARS:
        subjects = SUBJECTS_NEW if int(year) >= NEW_GAOKAO_YEAR else SUBJECTS_OLD

        for subject in subjects:
            # 点击年份
            click_text(page, year)
            page.wait_for_timeout(1000)

            # 点击科类
            click_text(page, subject)
            page.wait_for_timeout(2000)

            # 等待表格数据
            has_data = wait_for_table_data(page, 3000)

            # 提取数据
            data = extract_all_visible_data(page)

            year_records = []

            # 从表格提取
            for table in data.get('tables', []):
                for row in table.get('rows', []):
                    if len(row) >= 2:
                        # 解析行数据
                        # 可能格式1: [专业名, 最低分/位次, ...]
                        # 可能格式2: [年份, 批次, 类型, 分/位次, ...]
                        major_name = ""
                        min_score = None
                        min_rank = None
                        batch = "本科批"

                        # 尝试解析 "分数/位次" 格式
                        for cell in row:
                            m = re.match(r'(\d{3})/(\d+)', cell)
                            if m:
                                min_score = int(m.group(1))
                                min_rank = int(m.group(2))

                        # 第一个非数字单元格作为专业名/批次
                        for cell in row:
                            if cell and not re.match(r'^[\d/]+$', cell) and '/' not in cell:
                                if not major_name:
                                    major_name = cell
                                elif cell in ['本科批', '本科提前批', '本科提前批B段', '国家专项计划']:
                                    batch = cell

                        if min_score and major_name:
                            year_records.append({
                                "school_id": school_id,
                                "university_name": school_name,
                                "major_name": major_name,
                                "year": int(year),
                                "subject_type": subject,
                                "min_score": min_score,
                                "min_rank": min_rank,
                                "batch": batch,
                            })

            # 从文本提取（备用）
            if not year_records and data.get('mainText'):
                year_records = parse_score_text(
                    data['mainText'], school_id, school_name, year, subject
                )

            # 从 scoreItems 提取
            if not year_records:
                for item in data.get('scoreItems', []):
                    recs = parse_score_text(item, school_id, school_name, year, subject)
                    year_records.extend(recs)

            all_records.extend(year_records)
            if year_records:
                print(f"    {year} {subject}: {len(year_records)} 条")

    return all_records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--headless', default='true')
    parser.add_argument('--school', type=str, default='935')
    parser.add_argument('--all', action='store_true')
    parser.add_argument('--limit', type=int, default=0)
    args = parser.parse_args()

    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取")
    print("=" * 60)

    # 获取院校列表
    if args.all:
        schools = get_school_list()
        if args.limit > 0:
            schools = schools[:args.limit]
    else:
        schools = [{"school_id": args.school, "name": f"school_{args.school}"}]

    print(f"\n将爬取 {len(schools)} 所院校")

    all_records = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        ctx = browser.new_context(
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
            viewport={'width': 1280, 'height': 900},
            locale='zh-CN',
        )
        pg = ctx.new_page()

        # 拦截并记录所有 JSON 响应
        captured_json = []
        def on_resp(resp):
            if resp.status == 200:
                ct = resp.headers.get('content-type', '')
                if 'json' in ct or resp.url.endswith('.json'):
                    try:
                        body = resp.json()
                        captured_json.append({"url": resp.url, "data": body})
                    except:
                        pass
        pg.on("response", on_resp)

        for i, school in enumerate(schools):
            sid = str(school.get('school_id', ''))
            sname = school.get('name', sid)
            captured_json.clear()

            print(f"\n[{i+1}/{len(schools)}] {sname} (ID:{sid})")
            records = scrape_school(pg, sid, sname)

            # 也检查捕获的 JSON 数据
            if captured_json:
                print(f"    捕获 {len(captured_json)} 个 JSON 响应:")
                for cj in captured_json:
                    print(f"      {cj['url'][:100]}")
                    # 保存第一次捕获的 JSON 以便分析
                    if i == 0:
                        cap_path = os.path.join(DATA_DIR, "captured_json.json")
                        with open(cap_path, 'w', encoding='utf-8') as f:
                            json.dump(captured_json, f, ensure_ascii=False, indent=2)

            all_records.extend(records)
            print(f"    共 {len(records)} 条")

            # checkpoint
            if (i + 1) % 10 == 0 and all_records:
                cp = os.path.join(SCORE_DIR, "checkpoint.json")
                with open(cp, 'w', encoding='utf-8') as f:
                    json.dump(all_records, f, ensure_ascii=False, indent=2)

            time.sleep(1.5)

        # 截图最后一页
        pg.screenshot(path=os.path.join(DATA_DIR, "last_page.png"), full_page=True)
        browser.close()

    # 保存
    out = os.path.join(SCORE_DIR, "score_lines_all.json")
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(all_records, f, ensure_ascii=False, indent=2)

    print(f"\n{'='*60}")
    print(f"  完成! 共 {len(all_records)} 条分数线")
    print(f"  保存: {out}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
