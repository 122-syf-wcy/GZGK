"""诊断：选2024年贵州物理类后的完整DOM表格结构"""
from playwright.sync_api import sync_playwright
import json, os, time

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA_DIR, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    ctx = browser.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        viewport={'width': 1280, 'height': 900}, locale='zh-CN',
    )
    pg = ctx.new_page()

    # 记录所有响应
    responses = []
    def on_resp(resp):
        if resp.status == 200:
            ct = resp.headers.get('content-type', '')
            if 'json' in ct or resp.url.endswith('.json'):
                try:
                    responses.append({"url": resp.url, "data": resp.json()})
                except:
                    pass
    pg.on("response", on_resp)

    print("[1] 加载页面...")
    pg.goto('https://www.gaokao.cn/school/935/provinceline', timeout=25000)
    pg.wait_for_load_state('networkidle')
    pg.wait_for_timeout(3000)

    print("[2] 点击 2024...")
    pg.evaluate("""() => {
        const els = document.querySelectorAll('span, div, a, li, button');
        for (const el of els) {
            if (el.innerText.trim() === '2024' && el.offsetParent !== null) {
                el.click();
                return true;
            }
        }
        return false;
    }""")
    pg.wait_for_timeout(3000)

    print("[3] 点击 物理类...")
    pg.evaluate("""() => {
        const els = document.querySelectorAll('span, div, a, li, button');
        for (const el of els) {
            if (el.innerText.trim() === '物理类' && el.offsetParent !== null) {
                el.click();
                return true;
            }
        }
        return false;
    }""")
    pg.wait_for_timeout(4000)

    print("[4] 分析 DOM 结构...")
    dom_info = pg.evaluate("""() => {
        const result = {allTables: [], sections: [], rawHTML: ''};

        // 所有表格的完整信息
        document.querySelectorAll('table').forEach((table, ti) => {
            const info = {
                index: ti,
                className: table.className,
                parentClass: table.parentElement?.className || '',
                grandparentClass: table.parentElement?.parentElement?.className || '',
                headers: [],
                dataRows: [],
                rowCount: 0,
            };
            // 表头
            table.querySelectorAll('thead th, thead td').forEach(th => {
                info.headers.push(th.innerText.trim());
            });
            // 如果没有 thead，取第一行
            if (info.headers.length === 0) {
                const firstRow = table.querySelector('tr');
                if (firstRow) {
                    firstRow.querySelectorAll('th, td').forEach(cell => {
                        info.headers.push(cell.innerText.trim());
                    });
                }
            }
            // 数据行
            table.querySelectorAll('tbody tr, tr').forEach((tr, ri) => {
                const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
                if (cells.length > 0 && cells.some(c => c.length > 0)) {
                    info.dataRows.push(cells);
                    info.rowCount++;
                }
            });
            result.allTables.push(info);
        });

        // 查找包含"专业分数线"、"院校分数线"等标题的区块
        document.querySelectorAll('h1,h2,h3,h4,h5,.title,.section-title,[class*="title"]').forEach(el => {
            const text = el.innerText.trim();
            if (text.length < 50) {
                result.sections.push({
                    tag: el.tagName,
                    class: el.className,
                    text: text,
                });
            }
        });

        // 获取主内容区的 HTML 结构（不含脚本）
        const main = document.querySelector('main, .main, .content, .school-content, [class*="provinceline"]');
        if (main) {
            // 获取简化版 HTML
            const clone = main.cloneNode(true);
            clone.querySelectorAll('script, style, svg, img').forEach(el => el.remove());
            result.rawHTML = clone.innerHTML.substring(0, 8000);
        }

        return result;
    }""")

    print(f"\n=== 找到 {len(dom_info['allTables'])} 个表格 ===")
    for t in dom_info['allTables']:
        print(f"\n表格 #{t['index']} class='{t['className'][:60]}' parent='{t['parentClass'][:60]}'")
        print(f"  表头: {t['headers']}")
        print(f"  数据行数: {t['rowCount']}")
        for row in t['dataRows'][:5]:
            print(f"  行: {row}")
        if t['rowCount'] > 5:
            print(f"  ... ({t['rowCount']-5} more)")

    print(f"\n=== 区块标题 ===")
    for s in dom_info['sections']:
        print(f"  <{s['tag']} class='{s['class'][:40]}'> {s['text']}")

    # 保存完整诊断数据
    diag = {"dom": dom_info, "captured_urls": [r["url"] for r in responses]}
    with open(os.path.join(DATA_DIR, "dom_diagnosis.json"), 'w', encoding='utf-8') as f:
        json.dump(diag, f, ensure_ascii=False, indent=2)

    # 截图
    pg.screenshot(path=os.path.join(DATA_DIR, "page_2024.png"), full_page=True)
    print(f"\n截图: {os.path.join(DATA_DIR, 'page_2024.png')}")

    # 看看捕获的 zjzw.cn 响应数量
    zjzw = [r for r in responses if 'zjzw.cn' in r['url']]
    print(f"\n捕获 zjzw.cn 响应: {len(zjzw)}")
    for r in zjzw[:3]:
        print(f"  {r['url'][:120]}")

    browser.close()

print("\n完成!")
