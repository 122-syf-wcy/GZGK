#!/usr/bin/env python3
"""
贵州专业级分数线爬取 - 基于 Playwright 浏览器

通过浏览器访问 www.gaokao.cn/school/{sid}/provinceline 页面,
操作下拉框切换年份/科类，从 DOM 表格提取院校级和专业级分数线。

用法:
    python scrape_major_scores.py                          # 全量(从缓存)
    python scrape_major_scores.py --school 935             # 单校测试
    python scrape_major_scores.py --limit 50               # 前50所
    python scrape_major_scores.py --resume 100             # 从第100所继续
    python scrape_major_scores.py --headless false          # 显示浏览器
    python scrape_major_scores.py --years 2024,2025         # 仅爬指定年份
"""

import argparse, json, os, re, sys, time
from playwright.sync_api import sync_playwright, Page

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
os.makedirs(SCORE_DIR, exist_ok=True)
os.makedirs(EXPORT_DIR, exist_ok=True)

ALL_YEARS = ["2025", "2024", "2023", "2022", "2021", "2020"]
SUBJECTS = {
    "2025": ["物理类", "历史类"],
    "2024": ["物理类", "历史类"],
    "2023": ["理科", "文科"],
    "2022": ["理科", "文科"],
    "2021": ["理科", "文科"],
    "2020": ["理科", "文科"],
}


def dismiss_modal(page: Page):
    """关闭登录弹窗等遮挡物"""
    page.evaluate("""() => {
        document.querySelectorAll('.ant-modal-wrap').forEach(m => m.style.display = 'none');
        document.querySelectorAll('.ant-modal-mask').forEach(m => m.style.display = 'none');
    }""")


def open_select(page: Page, idx: int, text: str) -> bool:
    try:
        dismiss_modal(page)
        selects = page.locator('.ant-select')
        if idx >= selects.count():
            return False

        cur = page.evaluate(f"""() => {{
            const s = document.querySelectorAll('.ant-select')[{idx}];
            const v = s ? s.querySelector('.ant-select-selection-selected-value') : null;
            return v ? v.innerText.trim() : '';
        }}""")
        if cur == text:
            return True

        target = selects.nth(idx)
        target.locator('.ant-select-selection').click(timeout=3000)
        page.wait_for_timeout(500)

        for selector in [
            'li.ant-select-dropdown-menu-item',
            '.ant-select-item-option',
        ]:
            item = page.locator(selector).filter(has_text=re.compile(f'^{re.escape(text)}$'))
            if item.count() > 0:
                item.first.click(timeout=3000)
                page.wait_for_timeout(400)
                return True

        page.keyboard.press("Escape")
        page.wait_for_timeout(200)
        return False
    except Exception:
        try:
            page.keyboard.press("Escape")
        except Exception:
            pass
        return False


def get_filters(page: Page) -> list[str]:
    return page.evaluate("""() => {
        return Array.from(document.querySelectorAll('.ant-select')).map(s => {
            const v = s.querySelector('.ant-select-selection-selected-value');
            return v ? v.innerText.trim() : '?';
        });
    }""")


def extract_data(page: Page) -> dict:
    return page.evaluate("""() => {
        const result = {school: [], major: []};
        const tables = document.querySelectorAll('table');
        tables.forEach((table, ti) => {
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


def parse_score_rank(text: str) -> tuple:
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', '').replace('，', ''))
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


def scrape_school(page: Page, sid: str, sname: str, years: list[str]) -> dict:
    result = {"school_scores": [], "major_scores": []}

    try:
        page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                  wait_until='domcontentloaded', timeout=15000)
        page.wait_for_timeout(4000)
        dismiss_modal(page)
    except Exception as e:
        print(f"    加载失败: {e}", flush=True)
        return result

    filters = get_filters(page)
    if len(filters) < 3:
        print(f"    下拉框不足: {filters}", flush=True)
        return result

    year_idx, subj_idx = 1, 2
    year_idx2, subj_idx2 = -1, -1
    for i, val in enumerate(filters):
        if re.match(r'^20\d{2}$', val):
            if i <= 2:
                year_idx = i
            elif year_idx2 == -1:
                year_idx2 = i
        elif val in ('物理类', '历史类', '理科', '文科', '综合'):
            if i <= 2:
                subj_idx = i
            elif subj_idx2 == -1:
                subj_idx2 = i

    for year_str in years:
        subjects = SUBJECTS.get(year_str, ["理科", "文科"])
        for subject in subjects:
            try:
                open_select(page, year_idx, year_str)
                page.wait_for_timeout(1200)
                open_select(page, subj_idx, subject)
                page.wait_for_timeout(1800)

                if year_idx2 > 0:
                    open_select(page, year_idx2, year_str)
                    page.wait_for_timeout(600)
                if subj_idx2 > 0:
                    open_select(page, subj_idx2, subject)
                    page.wait_for_timeout(1200)
            except Exception:
                continue

            tables = extract_data(page)

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
                    score, rank = parse_score_rank(row[1] if len(row) > 1 else '')
                    if score and name:
                        result["major_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "major_name": name, "notes": notes,
                            "year": int(year_str), "subject_type": subject,
                            "min_score": score, "min_rank": rank,
                        })

    return result


def export_sql(school_scores, major_scores, path):
    def esc(v):
        if v is None:
            return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 专业分数线数据 {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        if school_scores:
            f.write("-- 院校分数线\n")
            for r in school_scores:
                f.write(
                    f"INSERT IGNORE INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,"
                    f"`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{esc(r.get('recruit_type',''))},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')});\n"
                )

        if major_scores:
            f.write("\n-- 专业分数线\n")
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


def main():
    ap = argparse.ArgumentParser(description="贵州专业级分数线爬取")
    ap.add_argument('--headless', default='true')
    ap.add_argument('--school', default='')
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--resume', type=int, default=0)
    ap.add_argument('--delay', type=float, default=1.5)
    ap.add_argument('--years', default='', help='逗号分隔年份,如 2024,2025')
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'
    years = args.years.split(',') if args.years else ALL_YEARS

    print("=" * 60)
    print("  贵州专业级分数线爬取")
    print(f"  年份: {years}")
    print("=" * 60)

    print("启动中...", flush=True)

    if args.school:
        schools = [{"school_id": args.school, "name": f"school_{args.school}"}]
    else:
        cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
        if not os.path.exists(cache_path):
            print(f"错误: {cache_path} 不存在")
            sys.exit(1)
        with open(cache_path) as f:
            schools = json.load(f)

    if args.resume > 0:
        schools = schools[args.resume:]
        print(f"从第 {args.resume} 所继续")
    if args.limit > 0:
        schools = schools[:args.limit]
    print(f"将爬取 {len(schools)} 所院校\n")

    cp_ss = os.path.join(SCORE_DIR, "major_cp_school.json")
    cp_ms = os.path.join(SCORE_DIR, "major_cp_major.json")
    all_ss = json.load(open(cp_ss)) if os.path.exists(cp_ss) and args.resume > 0 else []
    all_ms = json.load(open(cp_ms)) if os.path.exists(cp_ms) and args.resume > 0 else []

    print("启动浏览器...", flush=True)
    pw = sync_playwright().start()
    br = pw.chromium.launch(headless=headless)
    ctx = br.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) '
                   'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        viewport={'width': 1280, 'height': 900},
        locale='zh-CN',
    )
    pg = ctx.new_page()
    print("浏览器就绪", flush=True)

    try:
        for i, school in enumerate(schools):
            sid = str(school.get('school_id', ''))
            sname = school.get('name', f'school_{sid}')

            data = scrape_school(pg, sid, sname, years)
            ss = data["school_scores"]
            ms = data["major_scores"]
            all_ss.extend(ss)
            all_ms.extend(ms)

            yrs_s = sorted(set(r['year'] for r in ss)) if ss else []
            yrs_m = sorted(set(r['year'] for r in ms)) if ms else []
            print(f"  [{i+1}/{len(schools)}] {sname}: "
                  f"院校{len(ss)}条{yrs_s} 专业{len(ms)}条{yrs_m}", flush=True)

            if (i + 1) % 20 == 0:
                with open(cp_ss, 'w', encoding='utf-8') as f:
                    json.dump(all_ss, f, ensure_ascii=False, indent=2)
                with open(cp_ms, 'w', encoding='utf-8') as f:
                    json.dump(all_ms, f, ensure_ascii=False, indent=2)
                print(f"  checkpoint: 院校{len(all_ss)} 专业{len(all_ms)}", flush=True)

            time.sleep(args.delay)
    finally:
        br.close()
        pw.stop()

    with open(os.path.join(SCORE_DIR, "school_scores_all.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ss, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "major_scores_all.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ms, f, ensure_ascii=False, indent=2)

    sql_path = os.path.join(EXPORT_DIR, "major_scores.sql")
    export_sql(all_ss, all_ms, sql_path)

    by_year_s = {}
    for r in all_ss:
        by_year_s[r['year']] = by_year_s.get(r['year'], 0) + 1
    by_year_m = {}
    for r in all_ms:
        by_year_m[r['year']] = by_year_m.get(r['year'], 0) + 1

    print(f"\n{'='*60}")
    print(f"  爬取完成!")
    print(f"  院校分数线: {len(all_ss)} 条 {dict(sorted(by_year_s.items()))}")
    print(f"  专业分数线: {len(all_ms)} 条 {dict(sorted(by_year_m.items()))}")
    print(f"  SQL: {sql_path}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
