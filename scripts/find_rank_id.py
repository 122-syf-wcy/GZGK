"""找到 school_id → rank_id 的映射关系"""
import requests, json, re
from playwright.sync_api import sync_playwright

H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}

# 1. 从 list_v2.json 中找映射
print("=== 1. list_v2.json 中 school 935 的数据 ===")
r = requests.get('https://static-data.gaokao.cn/www/2.0/school/list_v2.json', headers=H, timeout=15)
lv2 = r.json().get('data', {})
entry = lv2.get('935', {})
print(f"  935 (贵州大学): {json.dumps(entry, ensure_ascii=False)}")

# 2. 看 school_code.json 中有没有线索
print("\n=== 2. school_code.json 中 935 相关 ===")
r2 = requests.get('https://static-data.gaokao.cn/www/2.0/school/school_code.json', headers=H, timeout=15)
sc = r2.json().get('data', {})
# 找 school_id=935 的所有条目
for code, info in sc.items():
    if info.get('school_id') == '935':
        print(f"  {code}: {info}")

# 3. 从页面 HTML 和 JS 中搜索 9549
print("\n=== 3. 浏览器中搜索 rank_id ===")
with sync_playwright() as p:
    br = p.chromium.launch(headless=True)
    ctx = br.new_context(user_agent='Mozilla/5.0', viewport={'width': 1280, 'height': 900})
    pg = ctx.new_page()

    # 记录所有请求 URL
    all_urls = []
    def on_req(req):
        all_urls.append(req.url)
    pg.on("request", on_req)

    pg.goto('https://www.gaokao.cn/school/935/provinceline', timeout=20000)
    pg.wait_for_load_state('networkidle')
    pg.wait_for_timeout(3000)

    # 滚动页面触发懒加载
    pg.evaluate("window.scrollTo(0, document.body.scrollHeight)")
    pg.wait_for_timeout(3000)

    # 搜索页面 HTML 中的 9549
    html = pg.content()
    matches = re.findall(r'9549', html)
    print(f"  HTML 中 '9549' 出现: {len(matches)} 次")

    # 搜索所有 script 内容中的 rank
    rank_refs = pg.evaluate("""() => {
        const results = [];
        // 搜索 window 上的数据
        for (const key of Object.keys(window)) {
            try {
                const val = JSON.stringify(window[key]);
                if (val && val.includes('9549')) {
                    results.push({source: 'window.' + key, match: val.substring(val.indexOf('9549') - 50, val.indexOf('9549') + 50)});
                }
            } catch(e) {}
        }
        // 搜索 script 标签
        document.querySelectorAll('script').forEach((s, i) => {
            const text = s.textContent || '';
            if (text.includes('9549') || text.includes('rank_id') || text.includes('rankId')) {
                const idx = text.indexOf('9549');
                if (idx >= 0) {
                    results.push({source: 'script_' + i, match: text.substring(Math.max(0, idx - 80), idx + 80)});
                }
            }
        });
        return results;
    }""")
    print(f"  JS 中找到 {len(rank_refs)} 处引用:")
    for ref in rank_refs[:5]:
        print(f"    [{ref['source']}] ...{ref['match']}...")

    # 看所有 gkcx 和 rank 请求
    rank_reqs = [u for u in all_urls if 'rank' in u.lower()]
    gkcx_reqs = [u for u in all_urls if 'gkcx' in u]
    print(f"\n  rank 请求: {len(rank_reqs)}")
    for u in rank_reqs: print(f"    {u[:150]}")
    print(f"  gkcx 请求: {len(gkcx_reqs)}")
    for u in gkcx_reqs: print(f"    {u[:150]}")

    # 也搜 __NEXT_DATA__ 或 __INITIAL_STATE__
    next_data = pg.evaluate("""() => {
        const el = document.getElementById('__NEXT_DATA__');
        return el ? el.textContent.substring(0, 2000) : null;
    }""")
    if next_data:
        print(f"\n  __NEXT_DATA__: {next_data[:500]}")
        if '9549' in next_data:
            idx = next_data.index('9549')
            print(f"    找到 9549 at: ...{next_data[max(0,idx-80):idx+80]}...")

    br.close()

# 4. 尝试找 linkage.json 或其他映射
print("\n=== 4. 其他映射端点 ===")
test_urls = [
    'https://static-data.gaokao.cn/www/2.0/info/linkage.json',
    'https://static-gkcx.gaokao.cn/www/2.0/json/school/info/935.json',
]
for url in test_urls:
    try:
        r = requests.get(url + '?a=www.gaokao.cn', headers=H, timeout=10)
        if r.status_code == 200:
            d = r.json()
            s = json.dumps(d, ensure_ascii=False)
            if '9549' in s:
                idx = s.index('9549')
                print(f"  ✅ {url} 包含 9549: ...{s[max(0,idx-100):idx+100]}...")
            else:
                print(f"  ❌ {url} 不含 9549")
    except Exception as e:
        print(f"  ❌ {url}: {e}")

print("\n完成!")
