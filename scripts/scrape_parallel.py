#!/usr/bin/env python3
"""
Parallel major score scraper using async Playwright.
Runs multiple browser pages concurrently for 3-4x speedup.

Usage:
    python scrape_parallel.py                          # full run, 4 workers
    python scrape_parallel.py --workers 6              # 6 concurrent workers
    python scrape_parallel.py --limit 200              # first 200 schools
    python scrape_parallel.py --resume 100             # resume from 100th
    python scrape_parallel.py --years 2025,2024        # specific years
    python scrape_parallel.py --headless false          # show browsers
    python scrape_parallel.py --skip-years 2025        # skip years with enough data
"""

import argparse, asyncio, json, os, re, sys, time
from playwright.async_api import async_playwright, Page

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
os.makedirs(SCORE_DIR, exist_ok=True)
os.makedirs(EXPORT_DIR, exist_ok=True)

ALL_YEARS = ["2025", "2024", "2023", "2022", "2021"]
SUBJECTS = {
    "2025": ["物理类", "历史类"],
    "2024": ["物理类", "历史类"],
    "2023": ["理科", "文科"],
    "2022": ["理科", "文科"],
    "2021": ["理科", "文科"],
    "2020": ["理科", "文科"],
}

lock = asyncio.Lock()
all_school_scores = []
all_major_scores = []
completed = 0
total_schools = 0


async def dismiss_modal(page: Page):
    await page.evaluate("""() => {
        document.querySelectorAll('.ant-modal-wrap').forEach(m => m.remove());
        document.querySelectorAll('.ant-modal-mask').forEach(m => m.remove());
        document.querySelectorAll('[class*="login-popup"]').forEach(m => m.remove());
        document.querySelectorAll('[class*="loginPopup"]').forEach(m => m.remove());
        document.querySelectorAll('.cus-modal-wrapper').forEach(m => m.remove());
    }""")


async def open_select(page: Page, idx: int, text: str) -> bool:
    try:
        await dismiss_modal(page)
        selects = page.locator('.ant-select')
        if await selects.count() <= idx:
            return False

        cur = await page.evaluate(f"""() => {{
            const s = document.querySelectorAll('.ant-select')[{idx}];
            const v = s ? s.querySelector('.ant-select-selection-selected-value') : null;
            return v ? v.innerText.trim() : '';
        }}""")
        if cur == text:
            return True

        target = selects.nth(idx)
        await target.locator('.ant-select-selection').click(timeout=3000)
        await page.wait_for_timeout(500)

        for selector in [
            'li.ant-select-dropdown-menu-item',
            '.ant-select-item-option',
        ]:
            item = page.locator(selector).filter(has_text=re.compile(f'^{re.escape(text)}$'))
            if await item.count() > 0:
                await item.first.click(timeout=3000)
                await page.wait_for_timeout(400)
                return True

        await page.keyboard.press("Escape")
        await page.wait_for_timeout(200)
        return False
    except Exception:
        try:
            await page.keyboard.press("Escape")
        except Exception:
            pass
        return False


async def get_filters(page: Page) -> list:
    return await page.evaluate("""() => {
        return Array.from(document.querySelectorAll('.ant-select')).map(s => {
            const v = s.querySelector('.ant-select-selection-selected-value');
            return v ? v.innerText.trim() : '?';
        });
    }""")


async def extract_data(page: Page) -> dict:
    return await page.evaluate("""() => {
        const result = {school: [], major: []};
        document.querySelectorAll('table').forEach(table => {
            table.querySelectorAll('tbody tr').forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length < 2) return;
                if (cells[0].match(/^20\\d{2}$/)) {
                    result.school.push(cells);
                } else if (cells[0].length > 1 && !cells[0].match(/^\\d+$/)
                           && cells[0] !== '专业名称' && cells[0] !== '年份') {
                    result.major.push(cells);
                }
            });
        });
        return result;
    }""")


def parse_score_rank(text):
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', '').replace('，', ''))
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


async def scrape_school(page: Page, sid: str, sname: str, years: list) -> dict:
    result = {"school_scores": [], "major_scores": []}

    try:
        await page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                        wait_until='domcontentloaded', timeout=15000)
        await page.wait_for_timeout(4000)
        await dismiss_modal(page)
    except Exception:
        return result

    filters = await get_filters(page)
    if len(filters) < 3:
        return result

    prov_idx, year_idx, subj_idx = -1, 1, 2
    year_idx2, subj_idx2 = -1, -1
    provinces = {'北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江',
                 '上海','江苏','浙江','安徽','福建','江西','山东','河南',
                 '湖北','湖南','广东','广西','海南','重庆','四川','贵州',
                 '云南','西藏','陕西','甘肃','青海','宁夏','新疆'}
    for i, val in enumerate(filters):
        if val in provinces:
            if prov_idx == -1:
                prov_idx = i
        elif re.match(r'^20\d{2}$', val):
            if i <= 3:
                year_idx = i
            elif year_idx2 == -1:
                year_idx2 = i
        elif val in ('物理类', '历史类', '理科', '文科', '综合'):
            if i <= 3:
                subj_idx = i
            elif subj_idx2 == -1:
                subj_idx2 = i

    if prov_idx >= 0 and filters[prov_idx] != '贵州':
        try:
            await dismiss_modal(page)
            await page.wait_for_timeout(300)
            await page.locator('.ant-select').nth(prov_idx).click(timeout=5000)
            await page.wait_for_timeout(800)
            await dismiss_modal(page)
            gz_div = page.locator('div[class*="score-plan_item"]').filter(has_text=re.compile('^贵州$'))
            if await gz_div.count() > 0:
                await gz_div.first.click(timeout=5000)
                await page.wait_for_timeout(2500)
                await dismiss_modal(page)
            else:
                ok = await open_select(page, prov_idx, '贵州')
                if not ok:
                    return result
                await page.wait_for_timeout(2000)
        except Exception:
            return result

    prov_idx2 = -1
    filters2 = await get_filters(page)
    for i, val in enumerate(filters2):
        if val in provinces and i > prov_idx and i != year_idx and i != subj_idx:
            prov_idx2 = i
            break

    for year_str in years:
        subjects = SUBJECTS.get(year_str, ["理科", "文科"])
        for subject in subjects:
            try:
                await dismiss_modal(page)
                await open_select(page, year_idx, year_str)
                await page.wait_for_timeout(1200)
                await dismiss_modal(page)
                await open_select(page, subj_idx, subject)
                await page.wait_for_timeout(1800)
                if prov_idx2 > 0:
                    try:
                        await page.locator('.ant-select').nth(prov_idx2).click(timeout=3000)
                        await page.wait_for_timeout(500)
                        gz2 = page.locator('div[class*="score-plan_item"]').filter(has_text=re.compile('^贵州$'))
                        if await gz2.count() > 0:
                            await gz2.first.click(timeout=3000)
                        else:
                            await open_select(page, prov_idx2, '贵州')
                        await page.wait_for_timeout(600)
                    except Exception:
                        pass
                if year_idx2 > 0:
                    await open_select(page, year_idx2, year_str)
                    await page.wait_for_timeout(600)
                if subj_idx2 > 0:
                    await open_select(page, subj_idx2, subject)
                    await page.wait_for_timeout(1200)
            except Exception:
                continue

            tables = await extract_data(page)

            for row in tables.get('school', []):
                if len(row) >= 4:
                    actual_yr = row[0] if re.match(r'^20\d{2}$', row[0]) else year_str
                    score, rank = parse_score_rank(row[3] if len(row) > 3 else row[-1])
                    if score:
                        result["school_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "year": int(actual_yr), "subject_type": subject,
                            "batch": row[1] if len(row) > 1 else '',
                            "recruit_type": row[2] if len(row) > 2 else '',
                            "min_score": score, "min_rank": rank,
                        })

            for row in tables.get('major', []):
                if len(row) >= 2:
                    raw = row[0]
                    lines = [l.strip() for l in raw.split('\n') if l.strip()]
                    name = lines[0] if lines else raw
                    notes = ' '.join(lines[1:]) if len(lines) > 1 else ''
                    for remove_str in ['（外语语种要求：不限）', '（外语语种要求：英语）']:
                        name = name.replace(remove_str, '')
                    name = name.strip()
                    score, rank = parse_score_rank(row[1] if len(row) > 1 else '')
                    if score and name:
                        result["major_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "major_name": name, "notes": notes,
                            "year": int(year_str), "subject_type": subject,
                            "min_score": score, "min_rank": rank,
                        })

    return result


async def worker(worker_id: int, schools: list, years: list, browser, delay: float):
    global completed, all_school_scores, all_major_scores

    ctx = await browser.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) '
                   'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        viewport={'width': 1280, 'height': 900},
        locale='zh-CN',
    )
    page = await ctx.new_page()

    for school in schools:
        sid = str(school.get('school_id', ''))
        sname = school.get('name', f'school_{sid}')

        data = await scrape_school(page, sid, sname, years)
        ss = data["school_scores"]
        ms = data["major_scores"]

        async with lock:
            all_school_scores.extend(ss)
            all_major_scores.extend(ms)
            completed += 1
            n = completed

        yrs_m = sorted(set(r['year'] for r in ms)) if ms else []
        print(f"  W{worker_id} [{n}/{total_schools}] {sname}: "
              f"院校{len(ss)}条 专业{len(ms)}条{yrs_m}", flush=True)

        if n % 20 == 0:
            await save_checkpoint()

        await asyncio.sleep(delay)

    await ctx.close()


async def save_checkpoint():
    async with lock:
        ss_copy = list(all_school_scores)
        ms_copy = list(all_major_scores)

    with open(os.path.join(SCORE_DIR, "parallel_cp_school.json"), 'w', encoding='utf-8') as f:
        json.dump(ss_copy, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "parallel_cp_major.json"), 'w', encoding='utf-8') as f:
        json.dump(ms_copy, f, ensure_ascii=False, indent=2)
    print(f"  [checkpoint] 院校{len(ss_copy)} 专业{len(ms_copy)}", flush=True)


def export_sql(school_scores, major_scores, path):
    def esc(v):
        if v is None:
            return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY parallel scrape {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        if major_scores:
            f.write("""CREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `major_name` VARCHAR(200) NOT NULL,
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL,
  `min_score` SMALLINT DEFAULT NULL,
  `min_rank` INT DEFAULT NULL,
  `notes` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  UNIQUE KEY `uk_record` (`school_id`, `major_name`(100), `year`, `subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州专业投档线';\n\n""")
            for r in major_scores:
                f.write(
                    f"INSERT IGNORE INTO `data_major_score_gz` "
                    f"(`school_id`,`university_name`,`major_name`,"
                    f"`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},"
                    f"{esc(r['major_name'])},{r['year']},{esc(r['subject_type'])},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')},"
                    f"{esc(r.get('notes',''))});\n"
                )

        if school_scores:
            f.write("\n-- 院校分数线\n")
            for r in school_scores:
                f.write(
                    f"INSERT IGNORE INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,"
                    f"`batch`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')});\n"
                )


async def main():
    global total_schools, all_school_scores, all_major_scores

    ap = argparse.ArgumentParser(description="并行爬取贵州专业分数线")
    ap.add_argument('--workers', type=int, default=4)
    ap.add_argument('--headless', default='true')
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--resume', type=int, default=0)
    ap.add_argument('--delay', type=float, default=1.0)
    ap.add_argument('--years', default='', help='逗号分隔，指定要爬的年份')
    ap.add_argument('--skip-years', default='', help='逗号分隔，跳过已有数据的年份')
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'
    years = args.years.split(',') if args.years else ALL_YEARS
    if args.skip_years:
        skip = set(args.skip_years.split(','))
        years = [y for y in years if y not in skip]

    cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
    if not os.path.exists(cache_path):
        print(f"错误: {cache_path} 不存在")
        sys.exit(1)
    with open(cache_path) as f:
        schools = json.load(f)

    if args.resume > 0:
        cp_s = os.path.join(SCORE_DIR, "parallel_cp_school.json")
        cp_m = os.path.join(SCORE_DIR, "parallel_cp_major.json")
        if os.path.exists(cp_s):
            all_school_scores = json.load(open(cp_s))
        if os.path.exists(cp_m):
            all_major_scores = json.load(open(cp_m))
        schools = schools[args.resume:]
        print(f"从第 {args.resume} 所继续，已加载 checkpoint: "
              f"院校{len(all_school_scores)} 专业{len(all_major_scores)}")
    if args.limit > 0:
        schools = schools[:args.limit]

    total_schools = len(schools)
    w = min(args.workers, total_schools)

    print("=" * 60)
    print(f"  并行爬取贵州专业级分数线")
    print(f"  院校: {total_schools} 所 | Workers: {w} | 年份: {years}")
    print("=" * 60)

    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=headless)
        print(f"浏览器已启动，{w} 个 worker 开始工作...", flush=True)

        chunks = [[] for _ in range(w)]
        for i, s in enumerate(schools):
            chunks[i % w].append(s)

        tasks = [
            worker(i, chunks[i], years, browser, args.delay)
            for i in range(w)
        ]
        await asyncio.gather(*tasks)
        await browser.close()

    with open(os.path.join(SCORE_DIR, "parallel_school_all.json"), 'w', encoding='utf-8') as f:
        json.dump(all_school_scores, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "parallel_major_all.json"), 'w', encoding='utf-8') as f:
        json.dump(all_major_scores, f, ensure_ascii=False, indent=2)

    sql_path = os.path.join(EXPORT_DIR, "parallel_scores.sql")
    export_sql(all_school_scores, all_major_scores, sql_path)

    by_year_m = {}
    for r in all_major_scores:
        by_year_m[r['year']] = by_year_m.get(r['year'], 0) + 1

    print(f"\n{'='*60}")
    print(f"  爬取完成!")
    print(f"  院校分数线: {len(all_school_scores)} 条")
    print(f"  专业分数线: {len(all_major_scores)} 条 {dict(sorted(by_year_m.items()))}")
    print(f"  SQL: {sql_path}")
    print(f"{'='*60}")


if __name__ == "__main__":
    asyncio.run(main())
