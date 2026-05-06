#!/usr/bin/env python3
"""
掌上高考分数线爬取 v3 - 用 Playwright locator 正确操作 Ant Design Select

用法:
    python scrape_v3.py                       # 测试贵州大学
    python scrape_v3.py --limit 50            # 前50所
    python scrape_v3.py --headless false      # 显示浏览器
"""

import argparse, json, os, re, time
import requests as req_lib
from playwright.sync_api import sync_playwright, Page

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
UNI_DIR = os.path.join(DATA_DIR, "universities")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
for d in [SCORE_DIR, UNI_DIR, EXPORT_DIR]:
    os.makedirs(d, exist_ok=True)

CDN = 'https://static-data.gaokao.cn/www/2.0'
CDN_H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}

YEARS = ["2024", "2023", "2022", "2021", "2020"]
SUBJECTS = {
    "2024": ["物理类", "历史类"],
    "2023": ["理科", "文科"], "2022": ["理科", "文科"],
    "2021": ["理科", "文科"], "2020": ["理科", "文科"],
}


def cdn_school_names():
    r = req_lib.get(f'{CDN}/school/name.json', headers=CDN_H, timeout=15)
    return r.json().get('data', [])

def cdn_school_info(sid):
    try:
        r = req_lib.get(f'{CDN}/school/{sid}/info.json', headers=CDN_H, timeout=10)
        return r.json().get('data', {}) if r.status_code == 200 else None
    except:
        return None


def click_ant_select(page: Page, nth: int, option_text: str) -> bool:
    """
    用 Playwright locator 操作第 nth 个 Ant Design Select
    nth: 0-based index of the ant-select on the page
    """
    try:
        # 找到第 nth 个 ant-select，点击打开下拉
        select = page.locator('.ant-select.ant-select-enabled').nth(nth)
        select.click()
        page.wait_for_timeout(600)

        # 在弹出的 dropdown 中点击选项
        # Ant Design dropdown menu items
        option = page.locator('.ant-select-dropdown-menu-item').filter(has_text=re.compile(f'^{re.escape(option_text)}$'))
        if option.count() > 0:
            option.first.click()
            page.wait_for_timeout(300)
            return True

        # 备选: ant-select-item (Ant Design v4+)
        option2 = page.locator('.ant-select-item-option').filter(has_text=re.compile(f'^{re.escape(option_text)}$'))
        if option2.count() > 0:
            option2.first.click()
            page.wait_for_timeout(300)
            return True

        # 关闭下拉
        page.keyboard.press("Escape")
        return False
    except Exception as e:
        try:
            page.keyboard.press("Escape")
        except:
            pass
        return False


def parse_sr(text):
    """解析 '590/9520' """
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', ''))
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


def extract_tables(page: Page):
    """提取表格数据"""
    return page.evaluate("""() => {
        const result = {school: [], major: [], currentFilters: ''};

        // 读取当前选中的筛选值
        const vals = document.querySelectorAll('.ant-select-selection-selected-value');
        result.currentFilters = Array.from(vals).map(v => v.innerText.trim()).join(' | ');

        document.querySelectorAll('table').forEach(table => {
            table.querySelectorAll('tbody tr').forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length < 2) return;

                if (cells[0].match(/^20\\d{2}$/)) {
                    result.school.push(cells);
                } else if (cells[0].length > 1 && cells[0] !== '专业名称' && !cells[0].match(/^\\d+$/)) {
                    result.major.push(cells);
                }
            });
        });
        return result;
    }""")


def scrape_school(page: Page, sid: str, sname: str) -> dict:
    """爬取一所学校的所有年份分数线"""
    result = {"school_scores": [], "major_scores": []}

    try:
        page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                  wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(4000)
    except Exception as e:
        print(f"    ⚠️ 加载失败: {e}")
        return result

    # 检测页面上有多少个 ant-select
    select_count = page.locator('.ant-select.ant-select-enabled').count()
    print(f"    找到 {select_count} 个下拉框")

    if select_count == 0:
        print(f"    ⚠️ 页面无筛选器，跳过")
        return result

    # 确保省份=贵州 (第1个下拉 index=0)
    current = page.evaluate("""() => {
        const v = document.querySelector('.ant-select-selection-selected-value');
        return v ? v.innerText.trim() : '';
    }""")
    if current != '贵州':
        click_ant_select(page, 0, '贵州')
        page.wait_for_timeout(2500)

    # 年份是第2个下拉(index=1), 科类是第3个(index=2)
    # 但如果有两组筛选器(上方院校+下方专业)，索引会翻倍
    # 先确认哪些索引对应哪些筛选项
    filter_vals = page.evaluate("""() => {
        const selects = document.querySelectorAll('.ant-select.ant-select-enabled');
        return Array.from(selects).map((s, i) => {
            const v = s.querySelector('.ant-select-selection-selected-value');
            return {index: i, value: v ? v.innerText.trim() : '?'};
        });
    }""")
    print(f"    筛选器: {filter_vals}")

    # 找到年份和科类的 index
    year_idx = -1
    subj_idx = -1
    for fv in filter_vals:
        val = fv['value']
        idx = fv['index']
        if re.match(r'^20\d{2}$', val) and year_idx == -1:
            year_idx = idx
        elif val in ['物理类', '历史类', '理科', '文科', '综合'] and subj_idx == -1:
            subj_idx = idx

    if year_idx == -1:
        print(f"    ⚠️ 未找到年份下拉框")
        # 可能所有筛选器都在一行：省份(0) 年份(1) 科类(2) 批次(3)
        year_idx = 1
        subj_idx = 2

    print(f"    年份索引={year_idx}, 科类索引={subj_idx}")

    prev_data_hash = ""

    for year_str in YEARS:
        subjects = SUBJECTS.get(year_str, ["理科", "文科"])
        for subject in subjects:
            # 切换年份
            yr_ok = click_ant_select(page, year_idx, year_str)
            page.wait_for_timeout(2000)

            # 切换科类
            subj_ok = click_ant_select(page, subj_idx, subject)
            page.wait_for_timeout(2500)

            # 提取
            tables = extract_tables(page)
            cur_filters = tables.get('currentFilters', '')

            # 去重检查
            data_hash = json.dumps(tables['school'][:3] + tables['major'][:3])
            if data_hash == prev_data_hash and prev_data_hash:
                # 数据没变，可能切换失败
                pass
            prev_data_hash = data_hash

            # 院校分数线: 只取年份匹配的行
            sc = 0
            for row in tables.get('school', []):
                if len(row) >= 4:
                    # 有些情况下不显示年份列，直接取所有行
                    actual_year = row[0] if re.match(r'^20\d{2}$', row[0]) else year_str
                    score, rank = parse_sr(row[3] if len(row) > 3 else row[-1])
                    if score:
                        result["school_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "year": int(actual_year), "subject_type": subject,
                            "batch": row[1] if len(row) > 1 else '',
                            "recruit_type": row[2] if len(row) > 2 else '',
                            "min_score": score, "min_rank": rank,
                        })
                        sc += 1

            # 专业分数线
            mc = 0
            for row in tables.get('major', []):
                if len(row) >= 2:
                    raw = row[0]
                    lines = [l.strip() for l in raw.split('\n') if l.strip()]
                    name = lines[0] if lines else raw
                    notes = ' '.join(lines[1:]) if len(lines) > 1 else ''
                    score, rank = parse_sr(row[1])
                    if score and name:
                        result["major_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "major_name": name, "notes": notes,
                            "year": int(year_str), "subject_type": subject,
                            "min_score": score, "min_rank": rank,
                        })
                        mc += 1

            status = f"院校{sc} 专业{mc}"
            filter_info = f" [{cur_filters}]" if cur_filters else ""
            if yr_ok or subj_ok:
                print(f"    {year_str} {subject}: {status}{filter_info}")
            else:
                print(f"    {year_str} {subject}: {status} ⚠️切换可能失败{filter_info}")

    return result


def export_sql(ss, ms, ui):
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")
    def esc(v):
        if v is None: return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(sql_path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 数据 {time.strftime('%Y-%m-%d %H:%M:%S')}\nSET NAMES utf8mb4;\n\n")

        f.write("CREATE TABLE IF NOT EXISTS `sys_university` (\n"
                "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL UNIQUE,\n"
                "  `name` VARCHAR(100) NOT NULL, `province` VARCHAR(20) DEFAULT '',\n"
                "  `city` VARCHAR(50) DEFAULT '', `level` VARCHAR(20) DEFAULT '',\n"
                "  `type_name` VARCHAR(20) DEFAULT '', `nature` VARCHAR(20) DEFAULT '',\n"
                "  `f985` TINYINT DEFAULT 0, `f211` TINYINT DEFAULT 0, `dual_class` TINYINT DEFAULT 0,\n"
                "  `belong` VARCHAR(50) DEFAULT '', `logo_url` VARCHAR(500) DEFAULT '',\n"
                "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")
        for sid, info in ui.items():
            f985 = 1 if str(info.get('f985')) == '1' else 0
            f211 = 1 if str(info.get('f211')) == '1' else 0
            dual = 1 if info.get('dual_class_name') else 0
            f.write(f"INSERT IGNORE INTO `sys_university` (`school_id`,`name`,`province`,`city`,`level`,"
                    f"`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(info.get('name',''))},{esc(info.get('province_name',''))},"
                    f"{esc(info.get('city_name',''))},{esc(info.get('level_name',''))},"
                    f"{esc(info.get('type_name',''))},{esc(info.get('nature_name',''))},"
                    f"{f985},{f211},{dual},{esc(info.get('belong',''))},{esc(info.get('logo',''))});\n")

        f.write("\nCREATE TABLE IF NOT EXISTS `data_score_line_gz` (\n"
                "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,\n"
                "  `university_name` VARCHAR(100), `year` SMALLINT NOT NULL,\n"
                "  `subject_type` VARCHAR(10) NOT NULL, `batch` VARCHAR(50) DEFAULT '',\n"
                "  `recruit_type` VARCHAR(50) DEFAULT '', `min_score` SMALLINT, `min_rank` INT,\n"
                "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                "  KEY `idx_sy` (`school_id`,`year`), KEY `idx_ys` (`year`,`subject_type`)\n"
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")
        for r in ss:
            f.write(f"INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,"
                    f"`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},{esc(r.get('recruit_type',''))},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')});\n")

        if ms:
            f.write("\nCREATE TABLE IF NOT EXISTS `data_major_score_gz` (\n"
                    "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,\n"
                    "  `university_name` VARCHAR(100), `major_name` VARCHAR(200) NOT NULL,\n"
                    "  `year` SMALLINT NOT NULL, `subject_type` VARCHAR(10) NOT NULL,\n"
                    "  `min_score` SMALLINT, `min_rank` INT, `notes` TEXT,\n"
                    "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                    "  KEY `idx_sy` (`school_id`,`year`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")
            for r in ms:
                f.write(f"INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,"
                        f"`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES "
                        f"({esc(r['school_id'])},{esc(r['university_name'])},{esc(r['major_name'])},"
                        f"{r['year']},{esc(r['subject_type'])},{r.get('min_score','NULL')},"
                        f"{r.get('min_rank','NULL')},{esc(r.get('notes',''))});\n")

    print(f"  ✅ SQL → {sql_path}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--headless', default='true')
    ap.add_argument('--school', default='')
    ap.add_argument('--limit', type=int, default=1)
    ap.add_argument('--delay', type=float, default=2.0)
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 v3")
    print("=" * 60)

    if args.school:
        schools = [{"school_id": args.school}]
    else:
        schools = cdn_school_names()
        if args.limit > 0:
            schools = schools[:args.limit]
    print(f"\n将爬取 {len(schools)} 所院校")

    print("\n[1] 院校详情...")
    ui = {}
    for s in schools:
        sid = str(s.get('school_id', ''))
        info = cdn_school_info(sid)
        if info:
            ui[sid] = info
            s['name'] = info.get('name', '')
        time.sleep(0.15)
    with open(os.path.join(UNI_DIR, "universities.json"), 'w', encoding='utf-8') as f:
        json.dump(list(ui.values()), f, ensure_ascii=False, indent=2)
    print(f"  ✅ {len(ui)} 所")

    print(f"\n[2] 浏览器爬取...")
    all_ss, all_ms = [], []

    with sync_playwright() as p:
        br = p.chromium.launch(headless=headless)
        ctx = br.new_context(
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
            viewport={'width': 1280, 'height': 900}, locale='zh-CN',
        )
        pg = ctx.new_page()

        for i, school in enumerate(schools):
            sid = str(school.get('school_id', ''))
            sname = school.get('name') or f'school_{sid}'
            print(f"\n  [{i+1}/{len(schools)}] {sname} (ID:{sid})")
            data = scrape_school(pg, sid, sname)
            all_ss.extend(data["school_scores"])
            all_ms.extend(data["major_scores"])

            if (i + 1) % 10 == 0 and all_ss:
                with open(os.path.join(SCORE_DIR, "cp.json"), 'w', encoding='utf-8') as f:
                    json.dump(all_ss, f, ensure_ascii=False, indent=2)
            time.sleep(args.delay)
        br.close()

    print(f"\n[3] 保存...")
    with open(os.path.join(SCORE_DIR, "school_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ss, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "major_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ms, f, ensure_ascii=False, indent=2)
    export_sql(all_ss, all_ms, ui)

    # 去重统计
    unique_years = sorted(set(r['year'] for r in all_ss)) if all_ss else []
    print(f"\n{'='*60}")
    print(f"  完成!  院校: {len(ui)}  院校线: {len(all_ss)}  专业线: {len(all_ms)}")
    print(f"  年份: {unique_years}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
