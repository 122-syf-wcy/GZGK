#!/usr/bin/env python3
"""
掌上高考分数线爬取器 (Playwright DOM 提取)

数据来源: www.gaokao.cn
提取内容:
  1. 院校基本信息 (从 CDN 静态接口)
  2. 院校级分数线 (完整，含批次/招生类型)
  3. 专业级分数线 (前5条免费可见)

用法:
    python scrape_gaokao.py                       # 测试 贵州大学
    python scrape_gaokao.py --limit 20            # 前20所
    python scrape_gaokao.py --limit 0             # 全部
    python scrape_gaokao.py --headless false       # 显示浏览器
"""

import argparse, json, os, re, sys, time
import requests as req_lib
from playwright.sync_api import sync_playwright, Page

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
UNI_DIR = os.path.join(DATA_DIR, "universities")
EXPORT_DIR = os.path.join(DATA_DIR, "export")
for d in [SCORE_DIR, UNI_DIR, EXPORT_DIR]:
    os.makedirs(d, exist_ok=True)

CDN_HEADERS = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}

# 贵州: 2024起新高考(物理类/历史类), 之前(理科/文科)
YEARS = ["2024", "2023", "2022", "2021", "2020"]
SUBJECTS_MAP = {
    "2024": ["物理类", "历史类"],
    "2023": ["理科", "文科"],
    "2022": ["理科", "文科"],
    "2021": ["理科", "文科"],
    "2020": ["理科", "文科"],
}


# ─── 院校列表 & 信息 (CDN) ───

def fetch_school_list() -> list[dict]:
    """获取全部院校 ID+名称"""
    r = req_lib.get('https://static-data.gaokao.cn/www/2.0/school/name.json',
                    headers=CDN_HEADERS, timeout=15)
    return r.json().get('data', [])

def fetch_school_list_v2() -> dict:
    """获取完整院校列表(含985/211/省份等)"""
    r = req_lib.get('https://static-data.gaokao.cn/www/2.0/school/list_v2.json',
                    headers=CDN_HEADERS, timeout=15)
    return r.json().get('data', {})

def fetch_school_info(sid: str) -> dict | None:
    """获取单个院校详情"""
    try:
        r = req_lib.get(f'https://static-data.gaokao.cn/www/2.0/school/{sid}/info.json',
                        headers=CDN_HEADERS, timeout=10)
        if r.status_code == 200:
            return r.json().get('data', {})
    except:
        pass
    return None


# ─── Playwright 页面交互 ───

def js_click_text(page: Page, text: str) -> bool:
    """用 JS 精确点击文本匹配的可见元素"""
    return page.evaluate(f"""() => {{
        const target = '{text}';
        // 优先在筛选区域查找
        const containers = document.querySelectorAll(
            '.filter, .select, .tabs, [class*="filter"], [class*="select"], [class*="tab"], [class*="year"], [class*="type"]'
        );
        // 先在筛选容器内找
        for (const container of containers) {{
            const els = container.querySelectorAll('span, div, a, li, button, label');
            for (const el of els) {{
                if (el.innerText.trim() === target && el.offsetParent !== null) {{
                    el.click();
                    return true;
                }}
            }}
        }}
        // 全局查找
        const allEls = document.querySelectorAll('span, div, a, li, button, label');
        for (const el of allEls) {{
            if (el.innerText.trim() === target && el.offsetParent !== null
                && el.offsetHeight > 0 && el.offsetWidth > 0) {{
                el.click();
                return true;
            }}
        }}
        return false;
    }}""")


def extract_tables(page: Page) -> dict:
    """提取页面上所有表格数据"""
    return page.evaluate("""() => {
        const result = {school_scores: [], major_scores: [], raw_tables: []};

        const tables = document.querySelectorAll('table.tb-normal, table');
        tables.forEach((table, ti) => {
            const headers = [];
            table.querySelectorAll('thead th, thead td').forEach(th => {
                headers.push(th.innerText.trim());
            });

            const rows = [];
            table.querySelectorAll('tbody tr').forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td'))
                    .map(td => td.innerText.trim());
                if (cells.length >= 2 && cells.some(c => c.length > 0)) {
                    rows.push(cells);
                }
            });

            result.raw_tables.push({headers, rows, index: ti});

            // 判断表格类型
            const headerText = headers.join(' ');
            if (headerText.includes('年份') && headerText.includes('录取批次')) {
                // 院校分数线表
                rows.forEach(row => {
                    if (row.length >= 4) {
                        result.school_scores.push({
                            year: row[0], batch: row[1], type: row[2], score_rank: row[3]
                        });
                    }
                });
            } else if (headerText.includes('专业名称')) {
                // 专业分数线表
                rows.forEach(row => {
                    if (row.length >= 2) {
                        result.major_scores.push({
                            name: row[0], score_rank: row[1]
                        });
                    }
                });
            }
        });

        return result;
    }""")


def parse_score_rank(text: str) -> tuple[int | None, int | None]:
    """解析 '590/9520' 格式"""
    m = re.match(r'(\d{2,3})/(\d+)', text.replace(',', ''))
    if m:
        return int(m.group(1)), int(m.group(2))
    return None, None


def scrape_one_school(page: Page, sid: str, sname: str) -> dict:
    """爬取一所学校的全部分数线"""
    result = {
        "school_id": sid,
        "name": sname,
        "school_scores": [],
        "major_scores": [],
    }

    url = f"https://www.gaokao.cn/school/{sid}/provinceline"
    try:
        page.goto(url, wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(3000)
    except Exception as e:
        print(f"    ⚠️ 加载失败: {e}")
        return result

    for year_str in YEARS:
        subjects = SUBJECTS_MAP.get(year_str, ["理科", "文科"])

        for subject in subjects:
            # 依次点击: 贵州 → 年份 → 科类
            js_click_text(page, "贵州")
            page.wait_for_timeout(800)
            js_click_text(page, year_str)
            page.wait_for_timeout(1500)
            js_click_text(page, subject)
            page.wait_for_timeout(2500)

            # 提取表格
            tables = extract_tables(page)

            # 院校分数线
            for item in tables.get('school_scores', []):
                score, rank = parse_score_rank(item.get('score_rank', ''))
                if score:
                    result["school_scores"].append({
                        "school_id": sid,
                        "university_name": sname,
                        "year": int(year_str),
                        "subject_type": subject,
                        "batch": item.get('batch', ''),
                        "recruit_type": item.get('type', ''),
                        "min_score": score,
                        "min_rank": rank,
                    })

            # 专业分数线 (前几条可见的)
            for item in tables.get('major_scores', []):
                raw_name = item.get('name', '')
                # 清理专业名: 提取第一行作为名字
                lines = [l.strip() for l in raw_name.split('\n') if l.strip()]
                major_name = lines[0] if lines else raw_name
                # 提取备注信息
                notes = ' '.join(lines[1:]) if len(lines) > 1 else ''

                score, rank = parse_score_rank(item.get('score_rank', ''))
                if score and major_name:
                    result["major_scores"].append({
                        "school_id": sid,
                        "university_name": sname,
                        "major_name": major_name,
                        "notes": notes,
                        "year": int(year_str),
                        "subject_type": subject,
                        "min_score": score,
                        "min_rank": rank,
                    })

            sc = len([s for s in result["school_scores"]
                      if s["year"] == int(year_str) and s["subject_type"] == subject])
            mc = len([s for s in result["major_scores"]
                      if s["year"] == int(year_str) and s["subject_type"] == subject])
            if sc or mc:
                print(f"    {year_str} {subject}: 院校{sc}条 专业{mc}条")

    return result


# ─── 主流程 ───

def main():
    parser = argparse.ArgumentParser(description="掌上高考分数线爬取")
    parser.add_argument('--headless', default='true', help='无头模式 true/false')
    parser.add_argument('--school', type=str, default='', help='指定school_id')
    parser.add_argument('--limit', type=int, default=1, help='院校数量(0=全部)')
    parser.add_argument('--delay', type=float, default=2.0, help='请求间隔(秒)')
    args = parser.parse_args()
    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 (Playwright)")
    print("=" * 60)

    # 1. 院校列表
    print("\n[1] 获取院校列表...")
    if args.school:
        schools = [{"school_id": args.school}]
    else:
        all_schools = fetch_school_list()
        # 获取 list_v2 来筛选在贵州招生的院校
        list_v2 = fetch_school_list_v2()
        # list_v2 包含所有院校基本信息
        schools = all_schools
        if args.limit > 0:
            schools = schools[:args.limit]

    print(f"  将爬取 {len(schools)} 所院校")

    # 2. 院校详情 (CDN)
    print("\n[2] 获取院校详情 (CDN)...")
    university_info = {}
    for i, s in enumerate(schools):
        sid = str(s.get('school_id', ''))
        info = fetch_school_info(sid)
        if info:
            university_info[sid] = info
        if (i + 1) % 100 == 0:
            print(f"  {i+1}/{len(schools)}...")
        time.sleep(0.2)

    uni_path = os.path.join(UNI_DIR, "universities.json")
    with open(uni_path, 'w', encoding='utf-8') as f:
        json.dump(list(university_info.values()), f, ensure_ascii=False, indent=2)
    print(f"  ✅ {len(university_info)} 所院校信息 → {uni_path}")

    # 3. Playwright 爬分数线
    print(f"\n[3] 启动浏览器爬取分数线...")
    all_school_scores = []
    all_major_scores = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        ctx = browser.new_context(
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
            viewport={'width': 1280, 'height': 900}, locale='zh-CN',
        )
        pg = ctx.new_page()

        for i, school in enumerate(schools):
            sid = str(school.get('school_id', ''))
            sname = school.get('name', university_info.get(sid, {}).get('name', f'school_{sid}'))

            print(f"\n  [{i+1}/{len(schools)}] {sname} (ID:{sid})")
            data = scrape_one_school(pg, sid, sname)

            all_school_scores.extend(data["school_scores"])
            all_major_scores.extend(data["major_scores"])

            # checkpoint
            if (i + 1) % 10 == 0:
                _save_checkpoint(all_school_scores, all_major_scores)
                print(f"    💾 checkpoint: 院校{len(all_school_scores)}条 专业{len(all_major_scores)}条")

            time.sleep(args.delay)

        browser.close()

    # 4. 保存
    print(f"\n[4] 保存数据...")
    _save_final(all_school_scores, all_major_scores, university_info)

    print(f"\n{'='*60}")
    print(f"  完成!")
    print(f"  院校信息: {len(university_info)} 所")
    print(f"  院校分数线: {len(all_school_scores)} 条")
    print(f"  专业分数线: {len(all_major_scores)} 条")
    print(f"  数据目录: {DATA_DIR}")
    print(f"{'='*60}")


def _save_checkpoint(school_scores, major_scores):
    with open(os.path.join(SCORE_DIR, "school_scores_checkpoint.json"), 'w', encoding='utf-8') as f:
        json.dump(school_scores, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "major_scores_checkpoint.json"), 'w', encoding='utf-8') as f:
        json.dump(major_scores, f, ensure_ascii=False, indent=2)


def _save_final(school_scores, major_scores, uni_info):
    # JSON
    with open(os.path.join(SCORE_DIR, "school_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(school_scores, f, ensure_ascii=False, indent=2)
    with open(os.path.join(SCORE_DIR, "major_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(major_scores, f, ensure_ascii=False, indent=2)

    # 按年份分文件
    by_year = {}
    for r in school_scores:
        key = f"{r['year']}_{r['subject_type']}"
        by_year.setdefault(key, []).append(r)
    for key, records in by_year.items():
        path = os.path.join(SCORE_DIR, f"school_scores_{key}.json")
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(records, f, ensure_ascii=False, indent=2)

    # SQL 导出
    _export_sql(school_scores, major_scores, uni_info)

    print(f"  ✅ school_scores.json ({len(school_scores)} 条)")
    print(f"  ✅ major_scores.json ({len(major_scores)} 条)")


def _export_sql(school_scores, major_scores, uni_info):
    """生成 MySQL SQL 文件"""
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")

    def esc(val):
        if val is None:
            return "NULL"
        s = str(val).replace("'", "\\'").replace("\\", "\\\\")
        return f"'{s}'"

    with open(sql_path, 'w', encoding='utf-8') as f:
        f.write("-- GZLY 数据导入 SQL\n")
        f.write(f"-- 生成时间: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("-- 来源: www.gaokao.cn\n\n")
        f.write("SET NAMES utf8mb4;\n\n")

        # 院校表
        f.write("-- =============================================\n")
        f.write("-- 院校信息表\n")
        f.write("-- =============================================\n")
        f.write("""CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `school_id` VARCHAR(20) NOT NULL COMMENT '掌上高考ID',
  `name` VARCHAR(100) NOT NULL COMMENT '院校名称',
  `province` VARCHAR(20) DEFAULT '' COMMENT '省份',
  `city` VARCHAR(50) DEFAULT '' COMMENT '城市',
  `level` VARCHAR(20) DEFAULT '' COMMENT '层次',
  `type_name` VARCHAR(20) DEFAULT '' COMMENT '类型(综合/理工/...)',
  `nature` VARCHAR(20) DEFAULT '' COMMENT '办学性质(公办/民办)',
  `f985` TINYINT DEFAULT 0,
  `f211` TINYINT DEFAULT 0,
  `dual_class` TINYINT DEFAULT 0 COMMENT '双一流',
  `belong` VARCHAR(50) DEFAULT '' COMMENT '隶属',
  `logo_url` VARCHAR(500) DEFAULT '',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_id` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校信息表';\n\n""")

        for sid, info in uni_info.items():
            name = info.get('name', '')
            province = info.get('province_name', '')
            city = info.get('city_name', '')
            level = info.get('level_name', '')
            type_name = info.get('type_name', '')
            nature = info.get('nature_name', '')
            f985 = 1 if str(info.get('f985')) == '1' else 0
            f211 = 1 if str(info.get('f211')) == '1' else 0
            dual = 1 if 'dual_class_name' in info and info['dual_class_name'] else 0
            belong = info.get('belong', '')
            logo = info.get('logo', '')
            f.write(f"INSERT INTO `sys_university` (`school_id`,`name`,`province`,`city`,`level`,"
                    f"`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(name)},{esc(province)},{esc(city)},{esc(level)},"
                    f"{esc(type_name)},{esc(nature)},{f985},{f211},{dual},{esc(belong)},{esc(logo)});\n")

        # 分数线表
        f.write("\n\n-- =============================================\n")
        f.write("-- 贵州历年投档线表 (院校级)\n")
        f.write("-- =============================================\n")
        f.write("""CREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类/历史类/理科/文科',
  `batch` VARCHAR(50) DEFAULT '' COMMENT '录取批次',
  `recruit_type` VARCHAR(50) DEFAULT '' COMMENT '招生类型',
  `min_score` SMALLINT DEFAULT NULL COMMENT '最低分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州历年投档线';\n\n""")

        for r in school_scores:
            f.write(f"INSERT INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,`batch`,"
                    f"`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{esc(r.get('recruit_type',''))},{r.get('min_score','NULL')},"
                    f"{r.get('min_rank','NULL')});\n")

        # 专业分数线表
        if major_scores:
            f.write("\n\n-- =============================================\n")
            f.write("-- 专业分数线表\n")
            f.write("-- =============================================\n")
            f.write("""CREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL,
  `min_score` SMALLINT DEFAULT NULL,
  `min_rank` INT DEFAULT NULL,
  `notes` TEXT COMMENT '备注(选科要求等)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_year` (`school_id`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业分数线';\n\n""")

            for r in major_scores:
                f.write(f"INSERT INTO `data_major_score_gz` "
                        f"(`school_id`,`university_name`,`major_name`,`year`,"
                        f"`subject_type`,`min_score`,`min_rank`,`notes`) VALUES "
                        f"({esc(r['school_id'])},{esc(r['university_name'])},"
                        f"{esc(r['major_name'])},{r['year']},{esc(r['subject_type'])},"
                        f"{r.get('min_score','NULL')},{r.get('min_rank','NULL')},"
                        f"{esc(r.get('notes',''))});\n")

    print(f"  ✅ SQL → {sql_path}")


if __name__ == "__main__":
    main()
