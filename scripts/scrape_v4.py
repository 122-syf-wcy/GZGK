#!/usr/bin/env python3
"""
掌上高考分数线爬取 v4 - 直接用 CSS nth-child 定位 ant-select

用法:
    python scrape_v4.py                       # 测试贵州大学
    python scrape_v4.py --limit 50            # 前50所
    python scrape_v4.py --headless false      # 显示浏览器
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


def open_select_and_choose(page: Page, select_index: int, option_text: str) -> bool:
    """打开第 N 个 ant-select 下拉框并选择选项"""
    try:
        # 所有 ant-select（不管 enabled 与否）
        all_selects = page.locator('.ant-select')
        count = all_selects.count()
        if select_index >= count:
            return False

        target = all_selects.nth(select_index)
        # 点击 selection 区域打开下拉
        target.locator('.ant-select-selection').click()
        page.wait_for_timeout(800)

        # 点击下拉选项（Ant Design v3 用 ul > li）
        dropdown_item = page.locator('li.ant-select-dropdown-menu-item').filter(
            has_text=re.compile(f'^{re.escape(option_text)}$')
        )
        if dropdown_item.count() > 0:
            dropdown_item.first.click()
            page.wait_for_timeout(500)
            return True

        # Ant Design v4
        option_item = page.locator('.ant-select-item-option').filter(
            has_text=re.compile(f'^{re.escape(option_text)}$')
        )
        if option_item.count() > 0:
            option_item.first.click()
            page.wait_for_timeout(500)
            return True

        # 关闭
        page.keyboard.press("Escape")
        page.wait_for_timeout(200)
        return False
    except Exception as e:
        try: page.keyboard.press("Escape")
        except: pass
        return False


def get_current_filters(page: Page) -> list[str]:
    """读取所有 ant-select 当前选中的值"""
    return page.evaluate("""() => {
        return Array.from(document.querySelectorAll('.ant-select')).map(s => {
            const v = s.querySelector('.ant-select-selection-selected-value');
            return v ? v.innerText.trim() : '?';
        });
    }""")


def parse_sr(text):
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', ''))
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


def extract_tables(page: Page):
    return page.evaluate("""() => {
        const result = {school: [], major: []};
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
    result = {"school_scores": [], "major_scores": []}

    try:
        page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                  wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(4000)
    except Exception as e:
        print(f"    ⚠️ 加载失败: {e}")
        return result

    # 读取当前筛选器状态，确定索引映射
    filters = get_current_filters(page)
    print(f"    下拉框({len(filters)}): {filters}")

    # 找到上方院校分数线区域的年份和科类索引
    # 典型布局: [省份, 年份, 科类, 省份2, 年份2, 科类2, 批次, ...]
    # 上方区域索引: 0=省份, 1=年份, 2=科类
    year_idx = -1
    subj_idx = -1
    for i, val in enumerate(filters):
        if re.match(r'^20\d{2}$', val) and year_idx == -1:
            year_idx = i
        elif val in ['物理类', '历史类', '理科', '文科', '综合'] and subj_idx == -1:
            subj_idx = i

    if year_idx == -1: year_idx = 1
    if subj_idx == -1: subj_idx = 2
    print(f"    年份idx={year_idx} 科类idx={subj_idx}")

    for year_str in YEARS:
        subjects = SUBJECTS.get(year_str, ["理科", "文科"])
        for subject in subjects:
            # 切换年份
            yr_ok = open_select_and_choose(page, year_idx, year_str)
            page.wait_for_timeout(2000)

            # 切换科类
            subj_ok = open_select_and_choose(page, subj_idx, subject)
            page.wait_for_timeout(2500)

            # 验证切换结果
            cur = get_current_filters(page)
            actual_year = cur[year_idx] if year_idx < len(cur) else '?'
            actual_subj = cur[subj_idx] if subj_idx < len(cur) else '?'
            switched = (actual_year == year_str and actual_subj == subject)

            # 提取
            tables = extract_tables(page)

            sc = 0
            for row in tables.get('school', []):
                if len(row) >= 4:
                    actual_yr = row[0] if re.match(r'^20\d{2}$', row[0]) else year_str
                    score, rank = parse_sr(row[3] if len(row) > 3 else row[-1])
                    if score:
                        result["school_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "year": int(actual_yr), "subject_type": subject,
                            "batch": row[1] if len(row) > 1 else '',
                            "recruit_type": row[2] if len(row) > 2 else '',
                            "min_score": score, "min_rank": rank,
                        })
                        sc += 1

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

            tag = "✅" if switched else "⚠️"
            print(f"    {tag} {year_str} {subject} → [{actual_year} {actual_subj}]: 院校{sc} 专业{mc}")

    return result


def export_sql(ss, ms, ui):
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")
    def esc(v):
        if v is None: return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(sql_path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 数据 {time.strftime('%Y-%m-%d %H:%M:%S')}\nSET NAMES utf8mb4;\n\n")

        f.write("""CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL UNIQUE,
  `name` VARCHAR(100) NOT NULL, `province` VARCHAR(20) DEFAULT '',
  `city` VARCHAR(50) DEFAULT '', `level` VARCHAR(20) DEFAULT '',
  `type_name` VARCHAR(20) DEFAULT '', `nature` VARCHAR(20) DEFAULT '',
  `f985` TINYINT DEFAULT 0, `f211` TINYINT DEFAULT 0, `dual_class` TINYINT DEFAULT 0,
  `belong` VARCHAR(50) DEFAULT '', `logo_url` VARCHAR(500) DEFAULT '',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n""")
        for sid, info in ui.items():
            f985 = 1 if str(info.get('f985')) == '1' else 0
            f211 = 1 if str(info.get('f211')) == '1' else 0
            dual = 1 if info.get('dual_class_name') else 0
            f.write(f"INSERT IGNORE INTO `sys_university` (`school_id`,`name`,`province`,`city`,"
                    f"`level`,`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(info.get('name',''))},{esc(info.get('province_name',''))},"
                    f"{esc(info.get('city_name',''))},{esc(info.get('level_name',''))},"
                    f"{esc(info.get('type_name',''))},{esc(info.get('nature_name',''))},"
                    f"{f985},{f211},{dual},{esc(info.get('belong',''))},{esc(info.get('logo',''))});\n")

        f.write("""\nCREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100), `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL, `batch` VARCHAR(50) DEFAULT '',
  `recruit_type` VARCHAR(50) DEFAULT '', `min_score` SMALLINT, `min_rank` INT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_sy` (`school_id`,`year`), KEY `idx_ys` (`year`,`subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n""")
        for r in ss:
            f.write(f"INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,"
                    f"`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},{esc(r.get('recruit_type',''))},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')});\n")

        if ms:
            f.write("""\nCREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100), `major_name` VARCHAR(200) NOT NULL,
  `year` SMALLINT NOT NULL, `subject_type` VARCHAR(10) NOT NULL,
  `min_score` SMALLINT, `min_rank` INT, `notes` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_sy` (`school_id`,`year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n""")
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
    print("  掌上高考分数线爬取 v4")
    print("=" * 60)

    if args.school:
        schools = [{"school_id": args.school}]
    else:
        schools = cdn_school_names()
        if args.limit > 0: schools = schools[:args.limit]
    print(f"将爬取 {len(schools)} 所院校")

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

    unique_years = sorted(set(r['year'] for r in all_ss)) if all_ss else []
    print(f"\n{'='*60}")
    print(f"  完成!  院校: {len(ui)}  院校线: {len(all_ss)}  专业线: {len(all_ms)}")
    print(f"  年份: {unique_years}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
