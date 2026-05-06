#!/usr/bin/env python3
"""
掌上高考分数线爬取 v5 - 拦截 rank API (未加密的完整数据)

策略:
  1. 用 Playwright 加载页面
  2. 拦截 static-gkcx.gaokao.cn/www/2.0/json/rank/{id}/{province}/lists.json
  3. 直接从响应 JSON 提取分数线 (无需操作 UI)
  4. 同时从 CDN 获取院校详情

用法:
    python scrape_rank.py                       # 测试贵州大学
    python scrape_rank.py --limit 50            # 前50所
    python scrape_rank.py --limit 0             # 全部
    python scrape_rank.py --headless false      # 显示浏览器
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
CDN_H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}
PROVINCE_ID = 52  # 贵州


def cdn_school_names():
    r = req_lib.get(f'{CDN}/school/name.json', headers=CDN_H, timeout=15)
    return r.json().get('data', [])

def cdn_school_info(sid):
    try:
        r = req_lib.get(f'{CDN}/school/{sid}/info.json', headers=CDN_H, timeout=10)
        return r.json().get('data', {}) if r.status_code == 200 else None
    except:
        return None


def parse_rank_data(rank_json: dict, school_id: str, school_name: str) -> list[dict]:
    """解析 rank/lists.json 的数据"""
    records = []
    data = rank_json.get('data', [])
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
                    min_section = item.get('min_section')
                    zslx = item.get('zslx', '')
                    batch_name = item.get('batch_name', '')
                    type_name = item.get('type_name', '')

                    if score:
                        records.append({
                            "school_id": school_id,
                            "university_name": school_name,
                            "year": int(year_str),
                            "subject_type": type_name,
                            "batch": batch_name,
                            "recruit_type": zslx,
                            "min_score": int(score) if score else None,
                            "min_rank": int(min_section) if min_section else None,
                            "type_code": type_code,
                        })
    return records


def scrape_school_rank(page: Page, school_id: str, school_name: str) -> list[dict]:
    """加载页面，拦截 rank JSON 请求"""
    captured = []

    def on_response(resp):
        url = resp.url
        # 拦截 rank/lists.json 响应
        if 'json/rank/' in url and 'lists.json' in url and resp.status == 200:
            try:
                body = resp.json()
                captured.append(body)
            except:
                pass

    page.on("response", on_response)

    try:
        page.goto(f'https://www.gaokao.cn/school/{school_id}/provinceline',
                  wait_until="domcontentloaded", timeout=20000)
        page.wait_for_timeout(3000)
        # 滚动页面触发 rank 懒加载请求
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
        page.wait_for_timeout(4000)
        # 再滚回顶部触发其他可能的请求
        page.evaluate("window.scrollTo(0, 0)")
        page.wait_for_timeout(1000)
    except Exception as e:
        print(f"    ⚠️ 加载失败: {e}")

    page.remove_listener("response", on_response)

    records = []
    if captured:
        for body in captured:
            records.extend(parse_rank_data(body, school_id, school_name))
    return records


def export_all(school_scores, uni_info):
    """导出 JSON + SQL + Excel"""

    # JSON
    json_path = os.path.join(SCORE_DIR, "school_scores.json")
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(school_scores, f, ensure_ascii=False, indent=2)
    print(f"  ✅ JSON: {json_path} ({len(school_scores)} 条)")

    # 按年份分文件
    by_year = {}
    for r in school_scores:
        k = f"{r['year']}_{r['subject_type']}"
        by_year.setdefault(k, []).append(r)
    for k, recs in by_year.items():
        p = os.path.join(SCORE_DIR, f"scores_{k}.json")
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(recs, f, ensure_ascii=False, indent=2)

    # SQL
    sql_path = os.path.join(EXPORT_DIR, "gzly_data.sql")
    def esc(v):
        if v is None: return "NULL"
        return "'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'"

    with open(sql_path, 'w', encoding='utf-8') as f:
        f.write(f"-- GZLY 贵州高考分数线数据\n")
        f.write(f"-- 来源: www.gaokao.cn (static-gkcx CDN)\n")
        f.write(f"-- 生成: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("SET NAMES utf8mb4;\n\n")

        # 院校表
        f.write("""DROP TABLE IF EXISTS `sys_university`;
CREATE TABLE `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `province` VARCHAR(20) DEFAULT '',
  `city` VARCHAR(50) DEFAULT '',
  `level` VARCHAR(20) DEFAULT '' COMMENT '层次(本科/专科)',
  `type_name` VARCHAR(20) DEFAULT '' COMMENT '类型(综合/理工)',
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
            f.write(f"INSERT INTO `sys_university` (`school_id`,`name`,`province`,`city`,"
                    f"`level`,`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES "
                    f"({esc(sid)},{esc(info.get('name',''))},{esc(info.get('province_name',''))},"
                    f"{esc(info.get('city_name',''))},{esc(info.get('level_name',''))},"
                    f"{esc(info.get('type_name',''))},{esc(info.get('nature_name',''))},"
                    f"{f985},{f211},{dual},{esc(info.get('belong',''))},{esc(info.get('logo',''))});\n")

        # 分数线表
        f.write("""\nDROP TABLE IF EXISTS `data_score_line_gz`;
CREATE TABLE `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100) DEFAULT '',
  `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类/历史类/理科/文科',
  `batch` VARCHAR(50) DEFAULT '' COMMENT '录取批次',
  `recruit_type` VARCHAR(50) DEFAULT '' COMMENT '招生类型(普通类/中外合作等)',
  `min_score` SMALLINT DEFAULT NULL COMMENT '最低分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_year` (`school_id`, `year`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  KEY `idx_score` (`min_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州历年投档线';\n\n""")

        for r in school_scores:
            f.write(f"INSERT INTO `data_score_line_gz` "
                    f"(`school_id`,`university_name`,`year`,`subject_type`,`batch`,"
                    f"`recruit_type`,`min_score`,`min_rank`) VALUES "
                    f"({esc(r['school_id'])},{esc(r['university_name'])},{r['year']},"
                    f"{esc(r['subject_type'])},{esc(r.get('batch',''))},"
                    f"{esc(r.get('recruit_type',''))},"
                    f"{r['min_score'] if r.get('min_score') else 'NULL'},"
                    f"{r['min_rank'] if r.get('min_rank') else 'NULL'});\n")

    print(f"  ✅ SQL: {sql_path}")

    # Excel (openpyxl)
    try:
        import openpyxl
        wb = openpyxl.Workbook()

        # 院校 sheet
        ws_uni = wb.active
        ws_uni.title = "院校信息"
        ws_uni.append(["school_id", "名称", "省份", "城市", "层次", "类型", "性质", "985", "211", "双一流", "隶属"])
        for sid, info in uni_info.items():
            ws_uni.append([sid, info.get('name',''), info.get('province_name',''),
                          info.get('city_name',''), info.get('level_name',''),
                          info.get('type_name',''), info.get('nature_name',''),
                          info.get('f985',''), info.get('f211',''),
                          info.get('dual_class_name',''), info.get('belong','')])

        # 分数线 sheet
        ws_score = wb.create_sheet("投档线")
        ws_score.append(["school_id", "院校名称", "年份", "科类", "批次", "招生类型", "最低分", "最低位次"])
        for r in school_scores:
            ws_score.append([r['school_id'], r['university_name'], r['year'],
                            r['subject_type'], r.get('batch',''), r.get('recruit_type',''),
                            r.get('min_score'), r.get('min_rank')])

        xlsx_path = os.path.join(EXPORT_DIR, "gzly_data.xlsx")
        wb.save(xlsx_path)
        print(f"  ✅ Excel: {xlsx_path}")
    except ImportError:
        print("  ⚠️ openpyxl 未安装，跳过 Excel 导出")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--headless', default='true')
    ap.add_argument('--school', default='')
    ap.add_argument('--limit', type=int, default=1)
    ap.add_argument('--delay', type=float, default=1.5)
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考分数线爬取 v5 (Rank API)")
    print("=" * 60)

    # 院校列表
    if args.school:
        schools = [{"school_id": args.school}]
    else:
        schools = cdn_school_names()
        if args.limit > 0:
            schools = schools[:args.limit]
    print(f"\n将爬取 {len(schools)} 所院校\n")

    # CDN 院校详情
    print("[1] 获取院校详情...")
    uni_info = {}
    for i, s in enumerate(schools):
        sid = str(s.get('school_id', ''))
        info = cdn_school_info(sid)
        if info:
            uni_info[sid] = info
            s['name'] = info.get('name', '')
        if (i + 1) % 50 == 0:
            print(f"  {i+1}/{len(schools)}")
        time.sleep(0.15)
    with open(os.path.join(UNI_DIR, "universities.json"), 'w', encoding='utf-8') as f:
        json.dump(list(uni_info.values()), f, ensure_ascii=False, indent=2)
    print(f"  ✅ {len(uni_info)} 所\n")

    # Playwright 拦截 rank 数据
    print("[2] 浏览器拦截分数线...")
    all_scores = []

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

            records = scrape_school_rank(pg, sid, sname)
            all_scores.extend(records)

            # 统计
            years = sorted(set(r['year'] for r in records)) if records else []
            print(f"  [{i+1}/{len(schools)}] {sname}: {len(records)} 条 {years}")

            # checkpoint
            if (i + 1) % 20 == 0 and all_scores:
                cp = os.path.join(SCORE_DIR, "checkpoint.json")
                with open(cp, 'w', encoding='utf-8') as f:
                    json.dump(all_scores, f, ensure_ascii=False, indent=2)
                print(f"  💾 checkpoint: {len(all_scores)} 条")

            time.sleep(args.delay)

        br.close()

    # 去重
    seen = set()
    unique_scores = []
    for r in all_scores:
        key = (r['school_id'], r['year'], r['subject_type'], r.get('batch',''),
               r.get('recruit_type',''), r.get('min_score'))
        if key not in seen:
            seen.add(key)
            unique_scores.append(r)
    if len(unique_scores) < len(all_scores):
        print(f"\n  去重: {len(all_scores)} → {len(unique_scores)}")
    all_scores = unique_scores

    # 导出
    print(f"\n[3] 导出数据...")
    export_all(all_scores, uni_info)

    # 汇总
    years = sorted(set(r['year'] for r in all_scores)) if all_scores else []
    subjects = sorted(set(r['subject_type'] for r in all_scores)) if all_scores else []
    print(f"\n{'='*60}")
    print(f"  完成!")
    print(f"  院校: {len(uni_info)}")
    print(f"  分数线: {len(all_scores)} 条")
    print(f"  年份: {years}")
    print(f"  科类: {subjects}")
    print(f"  数据: {DATA_DIR}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
