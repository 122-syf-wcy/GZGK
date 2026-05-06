#!/usr/bin/env python3
"""
掌上高考分数线爬取 - 生产版

流程:
  1. CDN 获取院校列表 + 详情
  2. CDN benchmarkScore 预筛选在贵州招生的院校
  3. Playwright 加载页面 + 滚动触发 rank API
  4. 拦截 rank JSON (未加密) + DOM 提取当前年份补充
  5. 导出 SQL / JSON / Excel

用法:
    python scrape_production.py                    # 全量爬取
    python scrape_production.py --limit 20         # 前20所
    python scrape_production.py --headless false    # 显示浏览器
    python scrape_production.py --school 935       # 单校测试
"""

import argparse, json, os, re, time, sys
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
CDN_H = {'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 'Referer': 'https://www.gaokao.cn/'}
PROVINCE_ID = '52'  # 贵州


# ─── CDN 数据获取 ───

def cdn_get(path, timeout=15):
    try:
        r = req_lib.get(f'{CDN}/{path}', headers=CDN_H, timeout=timeout)
        return r.json() if r.status_code == 200 else None
    except:
        return None

def cdn_school_names():
    d = cdn_get('school/name.json')
    return d.get('data', []) if d else []

def cdn_school_info(sid):
    d = cdn_get(f'school/{sid}/info.json', timeout=10)
    return d.get('data', {}) if d else None

def cdn_benchmark(sid):
    d = cdn_get(f'school/{sid}/benchmarkScore.json', timeout=8)
    return d.get('data', {}) if d else {}


# ─── 预筛选: 检查院校是否在贵州招生 ───

def has_guizhou_data(benchmark: dict) -> bool:
    """检查 benchmarkScore 中是否有贵州(52)的数据"""
    for key in benchmark:
        if f'_{PROVINCE_ID}_' in key:
            return True
    return False


# ─── Rank API 解析 ───

def parse_rank_response(body: dict, school_id: str, school_name: str) -> list[dict]:
    """解析 rank/lists.json 响应"""
    records = []
    data = body.get('data', [])
    if not isinstance(data, list):
        return records

    for entry in data:
        school_attr = entry.get('school_attr', {})
        for year_str, types_data in school_attr.items():
            if not re.match(r'^20\d{2}$', year_str):
                continue
            for type_code, items in types_data.items():
                if not isinstance(items, list):
                    continue
                for item in items:
                    score = item.get('score') or item.get('check_score')
                    if not score:
                        continue
                    records.append({
                        "school_id": school_id,
                        "university_name": school_name,
                        "year": int(year_str),
                        "subject_type": item.get('type_name', ''),
                        "batch": item.get('batch_name', ''),
                        "recruit_type": item.get('zslx', ''),
                        "min_score": int(score),
                        "min_rank": int(item['min_section']) if item.get('min_section') else None,
                    })
    return records


# ─── DOM 补充提取 ───

def extract_dom_scores(page: Page, school_id: str, school_name: str) -> list[dict]:
    """从 DOM 表格提取当前年份的分数线（补充 rank API 没覆盖的数据）"""
    raw = page.evaluate("""() => {
        // 读取当前筛选器选中的科类
        const vals = Array.from(document.querySelectorAll('.ant-select-selection-selected-value'))
            .map(v => v.innerText.trim());
        const subjectType = vals.find(v => ['物理类','历史类','理科','文科','综合'].includes(v)) || '';

        const result = [];
        document.querySelectorAll('table').forEach(table => {
            table.querySelectorAll('tbody tr').forEach(tr => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length >= 4 && cells[0].match(/^20\\d{2}$/)) {
                    cells.push(subjectType);
                    result.push(cells);
                }
            });
        });
        return result;
    }""")

    records = []
    for row in raw:
        m = re.match(r'(\d{2,3})/(\d+)', (row[3] if len(row) > 3 else '').replace(',', ''))
        if m:
            subj = row[-1] if len(row) > 4 else ''
            records.append({
                "school_id": school_id,
                "university_name": school_name,
                "year": int(row[0]),
                "subject_type": subj,
                "batch": row[1] if len(row) > 1 else '',
                "recruit_type": row[2] if len(row) > 2 else '',
                "min_score": int(m.group(1)),
                "min_rank": int(m.group(2)),
                "source": "dom",
            })
    return records


# ─── 单校爬取 ───

def scrape_one(page: Page, sid: str, sname: str) -> list[dict]:
    """加载页面 → 滚动触发 rank → 拦截 + DOM 提取"""
    captured_rank = []

    def on_resp(resp):
        if resp.status == 200 and 'json/rank/' in resp.url and 'lists.json' in resp.url:
            try:
                captured_rank.append(resp.json())
            except:
                pass

    page.on("response", on_resp)

    try:
        page.goto(f'https://www.gaokao.cn/school/{sid}/provinceline',
                  wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(2000)
        # 滚动到底部触发 rank 懒加载
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
        page.wait_for_timeout(3000)
    except Exception as e:
        page.remove_listener("response", on_resp)
        return []

    page.remove_listener("response", on_resp)

    # Rank API 数据
    records = []
    for body in captured_rank:
        records.extend(parse_rank_response(body, sid, sname))

    # DOM 补充
    dom_records = extract_dom_scores(page, sid, sname)

    # 合并: rank 优先, DOM 补充不重复的
    rank_keys = set()
    for r in records:
        rank_keys.add((r['year'], r['min_score'], r.get('batch', '')))

    for dr in dom_records:
        key = (dr['year'], dr['min_score'], dr.get('batch', ''))
        if key not in rank_keys:
            records.append(dr)

    return records


# ─── 导出 ───

def export_sql(scores, uni_info, path):
    def esc(v):
        if v is None: return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 贵州高考分数线数据\n")
        f.write(f"-- 来源: www.gaokao.cn  生成: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"-- 院校: {len(uni_info)}  分数线: {len(scores)}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        # 院校表
        f.write("""CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `province` VARCHAR(20) DEFAULT '',
  `city` VARCHAR(50) DEFAULT '',
  `level` VARCHAR(20) DEFAULT '' COMMENT '普通本科/专科',
  `type_name` VARCHAR(20) DEFAULT '' COMMENT '综合/理工/师范...',
  `nature` VARCHAR(20) DEFAULT '' COMMENT '公办/民办',
  `f985` TINYINT DEFAULT 0,
  `f211` TINYINT DEFAULT 0,
  `dual_class` TINYINT DEFAULT 0 COMMENT '双一流',
  `belong` VARCHAR(50) DEFAULT '' COMMENT '隶属',
  `logo_url` VARCHAR(500) DEFAULT '',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_school_id` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校信息';\n\n""")

        for sid, info in uni_info.items():
            f985 = 1 if str(info.get('f985')) == '1' else 0
            f211 = 1 if str(info.get('f211')) == '1' else 0
            dual = 1 if info.get('dual_class_name') else 0
            f.write(f"INSERT IGNORE INTO `sys_university` (`school_id`,`name`,`province`,`city`,"
                    f"`level`,`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(info.get('name',''))},{esc(info.get('province_name',''))},"
                    f"{esc(info.get('city_name',''))},{esc(info.get('level_name',''))},"
                    f"{esc(info.get('type_name',''))},{esc(info.get('nature_name',''))},"
                    f"{f985},{f211},{dual},{esc(info.get('belong',''))},{esc(info.get('logo',''))});\n")

        # 分数线表
        f.write("""\nCREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类/历史类/理科/文科',
  `batch` VARCHAR(50) DEFAULT '' COMMENT '录取批次',
  `recruit_type` VARCHAR(50) DEFAULT '' COMMENT '招生类型',
  `min_score` SMALLINT DEFAULT NULL COMMENT '最低分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  KEY `idx_score` (`min_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州历年投档线';\n\n""")

        for r in scores:
            f.write(f"INSERT INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,`batch`,"
                    f"`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{esc(r.get('recruit_type',''))},"
                    f"{r['min_score'] if r.get('min_score') else 'NULL'},"
                    f"{r['min_rank'] if r.get('min_rank') else 'NULL'});\n")


def export_excel(scores, uni_info, path):
    try:
        import openpyxl
    except ImportError:
        print("  ⚠️ openpyxl 未安装, 跳过 Excel")
        return

    wb = openpyxl.Workbook()
    ws1 = wb.active
    ws1.title = "院校信息"
    ws1.append(["school_id", "名称", "省份", "城市", "层次", "类型", "性质", "985", "211", "双一流"])
    for sid, info in uni_info.items():
        ws1.append([sid, info.get('name',''), info.get('province_name',''),
                    info.get('city_name',''), info.get('level_name',''),
                    info.get('type_name',''), info.get('nature_name',''),
                    str(info.get('f985','')), str(info.get('f211','')),
                    info.get('dual_class_name','')])

    ws2 = wb.create_sheet("投档线")
    ws2.append(["school_id", "院校", "年份", "科类", "批次", "招生类型", "最低分", "最低位次"])
    for r in scores:
        ws2.append([r['school_id'], r['university_name'], r['year'],
                    r['subject_type'], r.get('batch',''), r.get('recruit_type',''),
                    r.get('min_score'), r.get('min_rank')])
    wb.save(path)


# ─── 主程序 ───

def main():
    ap = argparse.ArgumentParser(description="掌上高考分数线爬取 (生产版)")
    ap.add_argument('--headless', default='true')
    ap.add_argument('--school', default='', help='指定 school_id')
    ap.add_argument('--limit', type=int, default=0, help='0=全部')
    ap.add_argument('--delay', type=float, default=1.5)
    ap.add_argument('--skip-filter', action='store_true', help='跳过预筛选')
    ap.add_argument('--from-cache', action='store_true', help='从 guizhou_schools.json 加载')
    ap.add_argument('--resume', type=int, default=0, help='从第N所开始(断点续爬)')
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 (生产版)")
    print("=" * 60)

    # === 1. 院校列表 ===
    if args.school:
        all_schools = [{"school_id": args.school}]
    elif args.from_cache:
        cache_path = os.path.join(DATA_DIR, "guizhou_schools.json")
        if os.path.exists(cache_path):
            with open(cache_path) as f:
                all_schools = json.load(f)
            print(f"\n[1] 从缓存加载: {len(all_schools)} 所贵州招生院校")
        else:
            print(f"  ⚠️ {cache_path} 不存在, 请先运行 prefilter_guizhou.py")
            sys.exit(1)
    else:
        print("\n[1] 获取院校列表...")
        all_schools = cdn_school_names()
        print(f"  总共 {len(all_schools)} 所院校")

    # === 2. 预筛选 ===
    if not args.school and not args.skip_filter:
        print("\n[2] 预筛选在贵州招生的院校 (benchmarkScore)...")
        guizhou_schools = []
        for i, s in enumerate(all_schools):
            sid = str(s.get('school_id', ''))
            bench = cdn_benchmark(sid)
            if has_guizhou_data(bench):
                guizhou_schools.append(s)
            if (i + 1) % 200 == 0:
                print(f"  扫描 {i+1}/{len(all_schools)}, 找到 {len(guizhou_schools)} 所")
            time.sleep(0.1)
        print(f"  ✅ 筛选完成: {len(guizhou_schools)}/{len(all_schools)} 所在贵州招生")
        schools = guizhou_schools
    else:
        schools = all_schools

    if args.limit > 0:
        schools = schools[:args.limit]
    if args.resume > 0:
        schools = schools[args.resume:]
        print(f"  从第 {args.resume} 所开始续爬")
    print(f"\n  将爬取 {len(schools)} 所院校")

    # === 3. 院校详情 ===
    print(f"\n[3] 获取院校详情 (CDN)...")
    uni_info = {}
    for i, s in enumerate(schools):
        sid = str(s.get('school_id', ''))
        info = cdn_school_info(sid)
        if info:
            uni_info[sid] = info
            s['name'] = info.get('name', '')
        if (i + 1) % 100 == 0:
            print(f"  {i+1}/{len(schools)}")
        time.sleep(0.1)
    with open(os.path.join(UNI_DIR, "universities.json"), 'w', encoding='utf-8') as f:
        json.dump(list(uni_info.values()), f, ensure_ascii=False, indent=2)
    print(f"  ✅ {len(uni_info)} 所院校详情")

    # === 4. Playwright 爬取 ===
    print(f"\n[4] 浏览器爬取分数线...")
    all_scores = []
    failed = []

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

            records = scrape_one(pg, sid, sname)
            all_scores.extend(records)

            years = sorted(set(r['year'] for r in records)) if records else []
            status = f"{len(records)} 条 {years}" if records else "0 条"
            print(f"  [{i+1}/{len(schools)}] {sname}: {status}")

            if not records:
                failed.append(sid)

            # Checkpoint 每 30 所
            if (i + 1) % 30 == 0 and all_scores:
                cp = os.path.join(SCORE_DIR, "checkpoint.json")
                with open(cp, 'w', encoding='utf-8') as f:
                    json.dump(all_scores, f, ensure_ascii=False, indent=2)
                print(f"  💾 checkpoint: {len(all_scores)} 条 ({len(schools)-i-1} 所剩余)")

            time.sleep(args.delay)

        br.close()

    # === 5. 去重 ===
    seen = set()
    unique = []
    for r in all_scores:
        key = (r['school_id'], r['year'], r.get('subject_type',''),
               r.get('batch',''), r.get('recruit_type',''), r.get('min_score'))
        if key not in seen:
            seen.add(key)
            unique.append(r)
    print(f"\n  去重: {len(all_scores)} → {len(unique)}")
    all_scores = unique

    # === 6. 导出 ===
    print(f"\n[5] 导出数据...")
    # JSON
    with open(os.path.join(SCORE_DIR, "school_scores.json"), 'w', encoding='utf-8') as f:
        json.dump(all_scores, f, ensure_ascii=False, indent=2)
    print(f"  ✅ JSON: {len(all_scores)} 条")

    # SQL
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")
    export_sql(all_scores, uni_info, sql_path)
    print(f"  ✅ SQL: {sql_path}")

    # Excel
    xlsx_path = os.path.join(EXPORT_DIR, "gzly_data.xlsx")
    export_excel(all_scores, uni_info, xlsx_path)
    print(f"  ✅ Excel: {xlsx_path}")

    # === 汇总 ===
    years = sorted(set(r['year'] for r in all_scores)) if all_scores else []
    subjects = sorted(set(r['subject_type'] for r in all_scores if r.get('subject_type')))
    by_year = {}
    for r in all_scores:
        by_year[r['year']] = by_year.get(r['year'], 0) + 1

    print(f"\n{'='*60}")
    print(f"  爬取完成!")
    print(f"  院校: {len(uni_info)} 所 (失败: {len(failed)})")
    print(f"  分数线: {len(all_scores)} 条")
    print(f"  年份: {years}")
    print(f"  科类: {subjects}")
    print(f"  按年统计: {dict(sorted(by_year.items()))}")
    print(f"  数据目录: {DATA_DIR}")
    if failed:
        print(f"  失败ID: {failed[:20]}{'...' if len(failed) > 20 else ''}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
