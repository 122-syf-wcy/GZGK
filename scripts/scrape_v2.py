#!/usr/bin/env python3
"""
掌上高考分数线爬取 v2 - 正确处理 Ant Design Select 下拉框

用法:
    python scrape_v2.py                       # 测试贵州大学
    python scrape_v2.py --limit 50            # 前50所
    python scrape_v2.py --headless false      # 显示浏览器
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


# ─── CDN 数据获取 ───

def cdn_school_names() -> list[dict]:
    r = req_lib.get(f'{CDN}/school/name.json', headers=CDN_H, timeout=15)
    return r.json().get('data', [])

def cdn_school_info(sid: str) -> dict | None:
    try:
        r = req_lib.get(f'{CDN}/school/{sid}/info.json', headers=CDN_H, timeout=10)
        if r.status_code == 200:
            return r.json().get('data', {})
    except:
        pass
    return None


# ─── Ant Design Select 操作 ───

def ant_select(page: Page, container_index: int, option_text: str) -> bool:
    """
    操作 Ant Design Select 下拉框
    container_index: 筛选器区域中第几个 ant-select (0=省份,1=年份,2=科类,3=批次)
    option_text: 要选择的选项文本
    """
    success = page.evaluate(f"""(args) => {{
        const [idx, text] = args;
        // 找到专业分数线区域的筛选器（页面下方的那组）
        const filterAreas = document.querySelectorAll('.slt-drop');
        // 用最后一组筛选器（专业分数线的），如果只有一组就用那个
        const area = filterAreas.length > 1 ? filterAreas[filterAreas.length - 1] : filterAreas[0];
        if (!area) return false;

        const selects = area.querySelectorAll('.ant-select');
        if (idx >= selects.length) return false;

        // 点击打开下拉框
        selects[idx].click();
        return true;
    }}""", [container_index, option_text])

    if not success:
        return False

    page.wait_for_timeout(500)

    # 在弹出的下拉菜单中点击选项
    clicked = page.evaluate(f"""(text) => {{
        // Ant Design dropdown 是挂在 body 下的
        const items = document.querySelectorAll('.ant-select-dropdown-menu-item, .ant-select-item');
        for (const item of items) {{
            if (item.innerText.trim() === text) {{
                item.click();
                return true;
            }}
        }}
        return false;
    }}""", option_text)

    page.wait_for_timeout(800)
    return clicked


def ant_select_in_area(page: Page, area_selector: str, select_index: int, option_text: str) -> bool:
    """在指定区域内操作第 N 个 ant-select"""
    # 先点击打开下拉框
    opened = page.evaluate(f"""(args) => {{
        const [areaSel, idx] = args;
        // 尝试多种方式找到筛选区域
        let area = document.querySelector(areaSel);
        if (!area) {{
            // 备用：找所有 slt-drop
            const drops = document.querySelectorAll('.slt-drop, .filter-compents_filterSeletcBox__1gdci');
            // 取所有 ant-select
            const allSelects = document.querySelectorAll('.ant-select.ant-select-enabled');
            if (idx < allSelects.length) {{
                allSelects[idx].querySelector('.ant-select-selection').click();
                return true;
            }}
        }}
        if (!area) return false;
        const selects = area.querySelectorAll('.ant-select');
        if (idx >= selects.length) return false;
        selects[idx].querySelector('.ant-select-selection')?.click();
        return true;
    }}""", [area_selector, select_index])

    if not opened:
        return False

    page.wait_for_timeout(600)

    # 点击下拉选项
    clicked = page.evaluate(f"""(text) => {{
        const items = document.querySelectorAll(
            '.ant-select-dropdown-menu-item, .ant-select-item, .ant-select-item-option'
        );
        for (const item of items) {{
            if (item.innerText.trim() === text && !item.classList.contains('ant-select-dropdown-menu-item-disabled')) {{
                item.click();
                return true;
            }}
        }}
        return false;
    }}""", option_text)

    page.wait_for_timeout(500)

    # 如果没找到，关闭下拉框
    if not clicked:
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)

    return clicked


def select_filters(page: Page, year: str, subject: str) -> bool:
    """设置院校分数线区域的筛选条件（省份已默认选好）"""
    # 页面有两组筛选器：上面的（院校分数线）和下面的（专业分数线）
    # ant-select 顺序: 0=省份, 1=年份, 2=科类, 3=批次
    # 但两组加起来: 0-3 是上面的, 4-7 是下面的
    # 我们操作上面那组(院校分数线)的年份(index=1)和科类(index=2)

    # 年份: 第2个 ant-select (index=1)
    yr_ok = ant_select_in_area(page, '.slt-drop', 1, year)
    page.wait_for_timeout(1500)

    # 科类: 第3个 ant-select (index=2)
    subj_ok = ant_select_in_area(page, '.slt-drop', 2, subject)
    page.wait_for_timeout(2000)

    return yr_ok or subj_ok


def extract_all_tables(page: Page) -> dict:
    """提取页面上的分数线表格数据"""
    return page.evaluate("""() => {
        const result = {school: [], major: []};

        const tables = document.querySelectorAll('table');
        tables.forEach(table => {
            // 判断表类型
            const firstTh = table.querySelector('thead th, thead td, tr:first-child th, tr:first-child td');
            const headerText = firstTh ? firstTh.innerText : '';

            table.querySelectorAll('tbody tr').forEach(tr => {
                const tds = tr.querySelectorAll('td');
                if (tds.length < 2) return;
                const cells = Array.from(tds).map(td => td.innerText.trim());

                if (cells[0].match(/^20\\d{2}$/)) {
                    // 院校分数线行: [年份, 批次, 类型, 分/位次, ...]
                    result.school.push(cells);
                } else if (cells[0].length > 1 && !cells[0].match(/^\\d+$/) && cells[0] !== '专业名称') {
                    // 专业分数线行: [专业名, 分/位次, ...]
                    result.major.push(cells);
                }
            });
        });

        return result;
    }""")


def parse_score_rank(text: str):
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', ''))
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


def scrape_school(page: Page, sid: str, sname: str) -> dict:
    """爬取一所学校"""
    result = {"school_id": sid, "name": sname, "school_scores": [], "major_scores": []}

    try:
        page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                  wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(4000)  # 等 JS hydration + API 解密
    except Exception as e:
        print(f"    ⚠️ 加载失败: {e}")
        return result

    # 确保省份选的是贵州 (通常默认就是用户所在省)
    # 先检查当前选的省份
    current_province = page.evaluate("""() => {
        const sel = document.querySelector('.ant-select-selection-selected-value');
        return sel ? sel.innerText.trim() : '';
    }""")
    if current_province != '贵州':
        ant_select_in_area(page, '.slt-drop', 0, '贵州')
        page.wait_for_timeout(2000)

    for year_str in YEARS:
        subjects = SUBJECTS.get(year_str, ["理科", "文科"])
        for subject in subjects:
            # 设置筛选条件
            select_filters(page, year_str, subject)
            page.wait_for_timeout(2000)

            # 提取表格
            tables = extract_all_tables(page)

            # 验证年份是否匹配（避免重复数据）
            school_count = 0
            for row in tables.get('school', []):
                if len(row) >= 4 and row[0] == year_str:
                    score, rank = parse_score_rank(row[3])
                    if score:
                        result["school_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "year": int(year_str), "subject_type": subject,
                            "batch": row[1] if len(row) > 1 else '',
                            "recruit_type": row[2] if len(row) > 2 else '',
                            "min_score": score, "min_rank": rank,
                        })
                        school_count += 1

            major_count = 0
            for row in tables.get('major', []):
                if len(row) >= 2:
                    raw_name = row[0]
                    lines = [l.strip() for l in raw_name.split('\n') if l.strip()]
                    major_name = lines[0] if lines else raw_name
                    notes = ' '.join(lines[1:]) if len(lines) > 1 else ''
                    score, rank = parse_score_rank(row[1])
                    if score and major_name:
                        result["major_scores"].append({
                            "school_id": sid, "university_name": sname,
                            "major_name": major_name, "notes": notes,
                            "year": int(year_str), "subject_type": subject,
                            "min_score": score, "min_rank": rank,
                        })
                        major_count += 1

            if school_count or major_count:
                print(f"    {year_str} {subject}: 院校{school_count} 专业{major_count}")

    return result


# ─── SQL 导出 ───

def export_sql(school_scores, major_scores, uni_info):
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")

    def esc(v):
        if v is None: return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(sql_path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 数据导入 SQL  生成: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        # sys_university
        f.write("CREATE TABLE IF NOT EXISTS `sys_university` (\n"
                "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,\n"
                "  `school_id` VARCHAR(20) NOT NULL UNIQUE,\n"
                "  `name` VARCHAR(100) NOT NULL,\n"
                "  `province` VARCHAR(20) DEFAULT '',\n"
                "  `city` VARCHAR(50) DEFAULT '',\n"
                "  `level` VARCHAR(20) DEFAULT '',\n"
                "  `type_name` VARCHAR(20) DEFAULT '',\n"
                "  `nature` VARCHAR(20) DEFAULT '',\n"
                "  `f985` TINYINT DEFAULT 0,\n"
                "  `f211` TINYINT DEFAULT 0,\n"
                "  `dual_class` TINYINT DEFAULT 0,\n"
                "  `belong` VARCHAR(50) DEFAULT '',\n"
                "  `logo_url` VARCHAR(500) DEFAULT '',\n"
                "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")

        for sid, info in uni_info.items():
            f985 = 1 if str(info.get('f985')) == '1' else 0
            f211 = 1 if str(info.get('f211')) == '1' else 0
            dual = 1 if info.get('dual_class_name') else 0
            f.write(f"INSERT IGNORE INTO `sys_university` "
                    f"(`school_id`,`name`,`province`,`city`,`level`,`type_name`,`nature`,"
                    f"`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(info.get('name',''))},"
                    f"{esc(info.get('province_name',''))},{esc(info.get('city_name',''))},"
                    f"{esc(info.get('level_name',''))},{esc(info.get('type_name',''))},"
                    f"{esc(info.get('nature_name',''))},"
                    f"{f985},{f211},{dual},{esc(info.get('belong',''))},"
                    f"{esc(info.get('logo',''))});\n")

        # data_score_line_gz
        f.write("\nCREATE TABLE IF NOT EXISTS `data_score_line_gz` (\n"
                "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,\n"
                "  `school_id` VARCHAR(20) NOT NULL,\n"
                "  `university_name` VARCHAR(100) DEFAULT '',\n"
                "  `year` SMALLINT NOT NULL,\n"
                "  `subject_type` VARCHAR(10) NOT NULL,\n"
                "  `batch` VARCHAR(50) DEFAULT '',\n"
                "  `recruit_type` VARCHAR(50) DEFAULT '',\n"
                "  `min_score` SMALLINT DEFAULT NULL,\n"
                "  `min_rank` INT DEFAULT NULL,\n"
                "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                "  KEY `idx_school_year` (`school_id`,`year`),\n"
                "  KEY `idx_year_subject` (`year`,`subject_type`)\n"
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")

        for r in school_scores:
            f.write(f"INSERT INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,`batch`,"
                    f"`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},"
                    f"{r['year']},{esc(r['subject_type'])},"
                    f"{esc(r.get('batch',''))},{esc(r.get('recruit_type',''))},"
                    f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')});\n")

        if major_scores:
            f.write("\nCREATE TABLE IF NOT EXISTS `data_major_score_gz` (\n"
                    "  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,\n"
                    "  `school_id` VARCHAR(20) NOT NULL,\n"
                    "  `university_name` VARCHAR(100) DEFAULT '',\n"
                    "  `major_name` VARCHAR(200) NOT NULL,\n"
                    "  `year` SMALLINT NOT NULL,\n"
                    "  `subject_type` VARCHAR(10) NOT NULL,\n"
                    "  `min_score` SMALLINT DEFAULT NULL,\n"
                    "  `min_rank` INT DEFAULT NULL,\n"
                    "  `notes` TEXT,\n"
                    "  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                    "  KEY `idx_school_year` (`school_id`,`year`)\n"
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n\n")
            for r in major_scores:
                f.write(f"INSERT INTO `data_major_score_gz` "
                        f"(`school_id`,`university_name`,`major_name`,`year`,"
                        f"`subject_type`,`min_score`,`min_rank`,`notes`) VALUES "
                        f"({esc(r['school_id'])},{esc(r['university_name'])},"
                        f"{esc(r['major_name'])},{r['year']},"
                        f"{esc(r['subject_type'])},"
                        f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')},"
                        f"{esc(r.get('notes',''))});\n")

    print(f"  ✅ SQL → {sql_path}")


# ─── 主程序 ───

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--headless', default='true')
    ap.add_argument('--school', default='')
    ap.add_argument('--limit', type=int, default=1)
    ap.add_argument('--delay', type=float, default=2.0)
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 v2")
    print("=" * 60)

    # 院校列表
    if args.school:
        schools = [{"school_id": args.school}]
    else:
        schools = cdn_school_names()
        if args.limit > 0:
            schools = schools[:args.limit]
    print(f"\n将爬取 {len(schools)} 所院校")

    # 院校详情
    print("\n[1] 获取院校详情 (CDN)...")
    uni_info = {}
    for i, s in enumerate(schools):
        sid = str(s.get('school_id', ''))
        info = cdn_school_info(sid)
        if info:
            uni_info[sid] = info
            if not s.get('name'):
                s['name'] = info.get('name', '')
        if (i + 1) % 100 == 0:
            print(f"  {i+1}/{len(schools)}")
        time.sleep(0.15)
    with open(os.path.join(UNI_DIR, "universities.json"), 'w', encoding='utf-8') as f:
        json.dump(list(uni_info.values()), f, ensure_ascii=False, indent=2)
    print(f"  ✅ {len(uni_info)} 所")

    # Playwright 爬分数线
    print(f"\n[2] 启动浏览器...")
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
            sname = school.get('name') or uni_info.get(sid, {}).get('name', f'school_{sid}')
            print(f"\n  [{i+1}/{len(schools)}] {sname} (ID:{sid})")

            data = scrape_school(pg, sid, sname)
            all_ss.extend(data["school_scores"])
            all_ms.extend(data["major_scores"])

            if (i + 1) % 10 == 0:
                with open(os.path.join(SCORE_DIR, "checkpoint_ss.json"), 'w', encoding='utf-8') as f:
                    json.dump(all_ss, f, ensure_ascii=False, indent=2)
                print(f"    💾 checkpoint: {len(all_ss)} + {len(all_ms)}")

            time.sleep(args.delay)

        br.close()

    # 保存
    print(f"\n[3] 保存数据...")
    with open(os.path.join(SCORE_DIR, "school_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ss, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "major_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(all_ms, f, ensure_ascii=False, indent=2)
    export_sql(all_ss, all_ms, uni_info)

    # 统计
    years = sorted(set(r['year'] for r in all_ss)) if all_ss else []
    print(f"\n{'='*60}")
    print(f"  完成!")
    print(f"  院校: {len(uni_info)}")
    print(f"  院校分数线: {len(all_ss)} 条")
    print(f"  专业分数线: {len(all_ms)} 条")
    print(f"  年份覆盖: {years}")
    print(f"  数据: {DATA_DIR}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
