"""
从掌上高考 SSR 页面直接提取分数线数据
页面是服务端渲染的，数据嵌在 HTML/DOM 中

用法:
    python scrape_ssr.py                    # 测试一所学校
    python scrape_ssr.py --limit 10         # 前10所
    python scrape_ssr.py --all              # 全部
    python scrape_ssr.py --headless false   # 显示浏览器
"""

import argparse
import json
import os
import re
import time
import requests as req_lib
from playwright.sync_api import sync_playwright

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
SCORE_DIR = os.path.join(DATA_DIR, "score_lines")
UNI_DIR = os.path.join(DATA_DIR, "universities")
os.makedirs(SCORE_DIR, exist_ok=True)
os.makedirs(UNI_DIR, exist_ok=True)


def get_all_school_ids() -> list[dict]:
    """从 CDN 获取院校名单"""
    h = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://gaokao.cn/'}
    r = req_lib.get('https://static-data.gaokao.cn/www/2.0/school/name.json', headers=h, timeout=15)
    return r.json().get('data', [])


def extract_page_data(page, school_id: str) -> dict:
    """从页面中提取所有嵌入的 JSON 数据"""
    result = {"school_id": school_id, "scores": [], "info": {}}

    # 方法1: 提取 <script> 标签中的 JSON 数据
    scripts = page.evaluate("""() => {
        const data = {};
        // 查找 window.__INITIAL_STATE__ 或类似的全局变量
        for (const key of Object.keys(window)) {
            if (key.startsWith('__') && typeof window[key] === 'object' && window[key] !== null) {
                try {
                    data[key] = JSON.stringify(window[key]).substring(0, 5000);
                } catch(e) {}
            }
        }
        // 查找 script[type="application/json"] 或内联数据
        document.querySelectorAll('script').forEach((s, i) => {
            const text = s.textContent || '';
            if (text.includes('score') || text.includes('province') || text.includes('special')) {
                if (text.length > 50 && text.length < 500000) {
                    data['script_' + i] = text.substring(0, 10000);
                }
            }
        });
        return data;
    }""")

    if scripts:
        for key, val in scripts.items():
            if 'score' in val.lower() or 'special' in val.lower():
                result['_raw_scripts'] = result.get('_raw_scripts', {})
                result['_raw_scripts'][key] = val[:2000]

    return result


def extract_table_data(page) -> list[dict]:
    """从渲染的 DOM 表格中提取分数线"""
    records = page.evaluate("""() => {
        const results = [];

        // 查找所有表格
        const tables = document.querySelectorAll('table');
        tables.forEach(table => {
            const rows = table.querySelectorAll('tbody tr, tr');
            rows.forEach(row => {
                const cells = row.querySelectorAll('td, th');
                if (cells.length >= 2) {
                    const texts = Array.from(cells).map(c => c.innerText.trim());
                    results.push({type: 'table', cells: texts});
                }
            });
        });

        // 查找非表格的数据行（一些网站用 div 布局）
        const dataRows = document.querySelectorAll(
            '.score-list .item, .data-row, .list-item, ' +
            '[class*="score"] [class*="row"], [class*="score"] [class*="item"], ' +
            '.professional-score-item, .special-score-item'
        );
        dataRows.forEach(row => {
            const texts = [];
            row.querySelectorAll('span, div, p, td').forEach(el => {
                const t = el.innerText.trim();
                if (t && t.length < 100) texts.push(t);
            });
            if (texts.length >= 2) {
                results.push({type: 'div', cells: texts});
            }
        });

        // 额外：获取页面上所有看起来像分数数据的文本块
        const scoreBlocks = document.querySelectorAll(
            '.content-table, .score-table, .province-line-content, ' +
            '.school-score, [class*="line-content"], main .content'
        );
        scoreBlocks.forEach((block, i) => {
            results.push({type: 'block_html', index: i, html: block.innerHTML.substring(0, 3000)});
        });

        return results;
    }""")
    return records


def extract_filters(page) -> dict:
    """提取页面上的筛选选项（省份、年份、科类等）"""
    filters = page.evaluate("""() => {
        const result = {provinces: [], years: [], subjects: [], batches: []};

        // 查找所有看起来像筛选器的元素
        const selectors = document.querySelectorAll(
            'select, .filter, .tab, .select-item, .dropdown-item, ' +
            '[class*="filter"], [class*="select"], [class*="tab"], ' +
            '.tag, .option, [role="option"], [role="tab"]'
        );
        selectors.forEach(el => {
            const text = el.innerText.trim();
            const cls = el.className || '';
            if (text.match(/^(19|20)\d{2}$/)) result.years.push(text);
            else if (text.match(/(物理|历史|理科|文科|文史|理工)/)) result.subjects.push(text);
            else if (text.match(/(本科|专科|提前|特殊)/)) result.batches.push(text);
            else if (text.length <= 4 && text.match(/(省|市|区|贵州|北京|上海)/)) result.provinces.push(text);
        });

        // 去重
        for (const k of Object.keys(result)) {
            result[k] = [...new Set(result[k])];
        }

        // 获取页面标题和面包屑
        result.title = document.title;
        const breadcrumb = document.querySelector('.breadcrumb, nav[aria-label]');
        result.breadcrumb = breadcrumb ? breadcrumb.innerText : '';

        // 获取当前选中的筛选值
        const activeItems = document.querySelectorAll('.active, [class*="active"], .selected, [aria-selected="true"]');
        result.activeFilters = Array.from(activeItems).map(el => el.innerText.trim()).filter(t => t.length < 20);

        return result;
    }""")
    return filters


def get_full_page_text(page) -> str:
    """获取页面的关键文本内容，用于分析"""
    return page.evaluate("""() => {
        const main = document.querySelector('main, .main, #app, .content, .container');
        return main ? main.innerText.substring(0, 5000) : document.body.innerText.substring(0, 5000);
    }""")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--headless', default='true')
    parser.add_argument('--limit', type=int, default=1)
    parser.add_argument('--all', action='store_true')
    args = parser.parse_args()

    headless = args.headless.lower() != 'false'

    print("=" * 60)
    print("  掌上高考 SSR 数据提取")
    print("=" * 60)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        ctx = browser.new_context(
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
            viewport={'width': 1280, 'height': 900},
            locale='zh-CN',
        )
        page = ctx.new_page()

        # 测试: 贵州大学
        print("\n[1] 访问贵州大学历年分数线页面...")
        page.goto('https://www.gaokao.cn/school/935/provinceline', timeout=20000)
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(2000)

        # 截图保存
        ss_path = os.path.join(DATA_DIR, "debug_screenshot.png")
        page.screenshot(path=ss_path, full_page=True)
        print(f"  截图已保存: {ss_path}")

        # 提取筛选器
        print("\n[2] 页面筛选器:")
        filters = extract_filters(page)
        for k, v in filters.items():
            if v:
                print(f"  {k}: {v}")

        # 提取页面文本
        print("\n[3] 页面关键文本:")
        text = get_full_page_text(page)
        print(f"  {text[:1500]}")

        # 提取表格数据
        print("\n[4] DOM 表格数据:")
        table_data = extract_table_data(page)
        for item in table_data[:20]:
            if item.get('type') == 'table':
                print(f"  [表格行] {item['cells']}")
            elif item.get('type') == 'div':
                print(f"  [DIV行] {item['cells']}")
            elif item.get('type') == 'block_html':
                print(f"  [HTML块 #{item['index']}] {item['html'][:300]}")

        # 提取嵌入的脚本数据
        print("\n[5] 嵌入数据:")
        embedded = extract_page_data(page, "935")
        if embedded.get('_raw_scripts'):
            for k, v in embedded['_raw_scripts'].items():
                print(f"  [{k}] {v[:500]}")
        else:
            print("  未找到嵌入的数据脚本")

        # 保存完整调试信息
        debug_info = {
            "filters": filters,
            "text_preview": text[:3000],
            "table_data": table_data[:50],
            "embedded": {k: v for k, v in embedded.items() if k != '_raw_scripts'},
            "raw_scripts": embedded.get('_raw_scripts', {}),
        }
        debug_path = os.path.join(DATA_DIR, "debug_page_data.json")
        with open(debug_path, 'w', encoding='utf-8') as f:
            json.dump(debug_info, f, ensure_ascii=False, indent=2)
        print(f"\n  完整调试数据: {debug_path}")

        browser.close()

    print("\n完成!")


if __name__ == "__main__":
    main()
