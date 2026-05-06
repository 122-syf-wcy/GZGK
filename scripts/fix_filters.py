"""精确分析筛选器 DOM，修复点击逻辑"""
from playwright.sync_api import sync_playwright
import json, os

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA_DIR, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    ctx = browser.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        viewport={'width': 1280, 'height': 900}, locale='zh-CN',
    )
    pg = ctx.new_page()

    print("[1] 加载页面...")
    pg.goto('https://www.gaokao.cn/school/935/provinceline', timeout=25000)
    pg.wait_for_load_state('networkidle')
    pg.wait_for_timeout(3000)

    # 分析筛选器区域的精确 DOM 结构
    print("\n[2] 分析筛选器 DOM...")
    filter_info = pg.evaluate("""() => {
        const result = {filters: [], clickableItems: []};

        // 找到所有可能的筛选容器
        // 从截图看：省份、年份、科类是一行筛选器
        const allElements = document.querySelectorAll('*');
        const filterTexts = ['贵州', '2025', '2024', '2023', '物理类', '历史类', '理科', '文科', '本科批'];

        for (const el of allElements) {
            const text = el.innerText?.trim() || '';
            const directText = el.childNodes.length === 1 && el.childNodes[0].nodeType === 3
                ? el.childNodes[0].textContent.trim() : '';

            // 只找直接包含筛选文本的叶子节点
            if (filterTexts.includes(text) || filterTexts.includes(directText)) {
                const tag = el.tagName;
                const cls = el.className || '';
                const parent = el.parentElement;
                const parentCls = parent?.className || '';
                const grandCls = parent?.parentElement?.className || '';
                const rect = el.getBoundingClientRect();

                result.clickableItems.push({
                    text: text || directText,
                    tag: tag,
                    class: cls.toString().substring(0, 80),
                    parentTag: parent?.tagName || '',
                    parentClass: parentCls.toString().substring(0, 80),
                    grandClass: grandCls.toString().substring(0, 80),
                    visible: rect.width > 0 && rect.height > 0,
                    x: Math.round(rect.x),
                    y: Math.round(rect.y),
                    w: Math.round(rect.width),
                    h: Math.round(rect.height),
                    isLeaf: el.children.length === 0 || (el.children.length <= 2 && text.length < 10),
                });
            }
        }

        // 找筛选区域的容器
        const containers = document.querySelectorAll('[class*="filter"], [class*="select"], [class*="screen"], [class*="condition"], [class*="tab"]');
        containers.forEach(c => {
            const text = c.innerText?.trim().substring(0, 200) || '';
            if (text) {
                result.filters.push({
                    tag: c.tagName,
                    class: (c.className || '').toString().substring(0, 100),
                    text: text.substring(0, 200),
                    childCount: c.children.length,
                });
            }
        });

        return result;
    }""")

    print("\n=== 筛选容器 ===")
    for f in filter_info['filters']:
        print(f"  <{f['tag']} class='{f['class']}'> children={f['childCount']}")
        print(f"    text: {f['text'][:100]}")

    print("\n=== 可点击的筛选项 ===")
    for item in filter_info['clickableItems']:
        vis = "👁" if item['visible'] else "🚫"
        leaf = "🍃" if item['isLeaf'] else "🌳"
        print(f"  {vis}{leaf} '{item['text']}' <{item['tag']} class='{item['class'][:40]}'> "
              f"parent=<{item['parentTag']} class='{item['parentClass'][:30]}'> "
              f"pos=({item['x']},{item['y']}) size={item['w']}x{item['h']}")

    # 尝试用更精确的选择器点击 "贵州"
    print("\n[3] 尝试点击操作...")

    # 先截图当前状态
    pg.screenshot(path=os.path.join(DATA_DIR, "before_click.png"))

    # 方法1: 用坐标点击
    for item in filter_info['clickableItems']:
        if item['text'] == '贵州' and item['visible'] and item['isLeaf']:
            x = item['x'] + item['w'] // 2
            y = item['y'] + item['h'] // 2
            print(f"  点击 '贵州' at ({x}, {y})")
            pg.mouse.click(x, y)
            pg.wait_for_timeout(2000)
            break

    pg.screenshot(path=os.path.join(DATA_DIR, "after_guizhou.png"))

    # 再看看年份选择器出来没
    print("\n[4] 点击贵州后的筛选项...")
    post_click = pg.evaluate("""() => {
        const items = [];
        const texts = ['2025', '2024', '2023', '2022', '2021', '2020', '物理类', '历史类', '理科', '文科'];
        for (const el of document.querySelectorAll('*')) {
            const t = el.innerText?.trim();
            if (texts.includes(t) && el.children.length <= 2) {
                const rect = el.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0) {
                    items.push({
                        text: t,
                        tag: el.tagName,
                        class: (el.className || '').toString().substring(0, 60),
                        x: Math.round(rect.x + rect.width/2),
                        y: Math.round(rect.y + rect.height/2),
                    });
                }
            }
        }
        return items;
    }""")
    for item in post_click:
        print(f"  '{item['text']}' <{item['tag']}> at ({item['x']},{item['y']})")

    # 点击 2024
    for item in post_click:
        if item['text'] == '2024':
            print(f"\n  点击 '2024' at ({item['x']}, {item['y']})")
            pg.mouse.click(item['x'], item['y'])
            pg.wait_for_timeout(2000)
            break

    # 点击 物理类
    post_year = pg.evaluate("""() => {
        const items = [];
        for (const el of document.querySelectorAll('*')) {
            const t = el.innerText?.trim();
            if (t === '物理类' && el.children.length <= 2) {
                const rect = el.getBoundingClientRect();
                if (rect.width > 0) items.push({x: Math.round(rect.x+rect.width/2), y: Math.round(rect.y+rect.height/2)});
            }
        }
        return items;
    }""")
    if post_year:
        print(f"  点击 '物理类' at ({post_year[0]['x']}, {post_year[0]['y']})")
        pg.mouse.click(post_year[0]['x'], post_year[0]['y'])
        pg.wait_for_timeout(3000)

    # 最终截图
    pg.screenshot(path=os.path.join(DATA_DIR, "after_all_filters.png"), full_page=True)

    # 提取最终表格数据
    final_data = pg.evaluate("""() => {
        const rows = [];
        document.querySelectorAll('table tbody tr').forEach(tr => {
            const cells = Array.from(tr.querySelectorAll('td')).map(td => td.innerText.trim());
            if (cells.length >= 2) rows.push(cells);
        });
        return rows;
    }""")
    print(f"\n[5] 最终表格数据 ({len(final_data)} 行):")
    for row in final_data[:10]:
        print(f"  {row}")

    browser.close()

print("\n完成!")
