"""测试: 1. rank API 是否可以直接用 school_id 调用; 2. 浏览器加载时 rank 请求何时触发"""
import requests, json, re
from playwright.sync_api import sync_playwright

H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}

# 1. 直接测试不同 ID 的 rank endpoint
print("=== 1. 直接调 rank API ===")
test_ids = [935, 9549, 109, 31, 140]  # 935=贵州大学school_id, 9549=之前发现的rank_id
for rid in test_ids:
    url = f'https://static-gkcx.gaokao.cn/www/2.0/json/rank/{rid}/52/lists.json?a=www.gaokao.cn'
    try:
        r = requests.get(url, headers=H, timeout=10)
        if r.status_code == 200:
            data = r.json().get('data', [])
            # 看第一条的学校信息
            if data and isinstance(data, list) and data[0].get('school_attr'):
                years = list(data[0]['school_attr'].keys())
                print(f"  ✅ ID {rid}: 有数据, years={years}")
            else:
                print(f"  ✅ ID {rid}: 200 但数据为空或结构不同")
        else:
            print(f"  ❌ ID {rid}: {r.status_code}")
    except Exception as e:
        print(f"  ❌ ID {rid}: {e}")

# 2. 用浏览器加载页面，拦截所有 gkcx 域名请求
print("\n=== 2. 浏览器拦截所有 gkcx 请求 ===")
with sync_playwright() as p:
    br = p.chromium.launch(headless=False)
    ctx = br.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
        viewport={'width': 1280, 'height': 900}, locale='zh-CN',
    )
    pg = ctx.new_page()

    all_urls = []
    def on_resp(resp):
        url = resp.url
        if 'gaokao' in url and resp.status == 200:
            # 跳过静态资源
            skip = ['.css', '.js', '.png', '.jpg', '.gif', '.svg', '.woff', '.ico']
            if not any(s in url.lower() for s in skip):
                all_urls.append(url)

    pg.on("response", on_resp)

    # 先试 provinceline 页面
    print("  加载 /school/935/provinceline ...")
    pg.goto('https://www.gaokao.cn/school/935/provinceline', timeout=20000)
    pg.wait_for_load_state('networkidle')
    pg.wait_for_timeout(3000)

    rank_urls = [u for u in all_urls if 'rank' in u]
    print(f"  总共 {len(all_urls)} 个 gaokao 请求, rank 请求: {len(rank_urls)}")
    for u in rank_urls:
        print(f"    {u[:150]}")

    # 如果 provinceline 没有 rank 请求，试主页
    if not rank_urls:
        all_urls.clear()
        print("\n  加载 /school/935 (主页)...")
        pg.goto('https://www.gaokao.cn/school/935', timeout=20000)
        pg.wait_for_load_state('networkidle')
        pg.wait_for_timeout(3000)

        rank_urls = [u for u in all_urls if 'rank' in u]
        print(f"  总共 {len(all_urls)} 个请求, rank 请求: {len(rank_urls)}")
        for u in rank_urls:
            print(f"    {u[:150]}")

    # 打印所有 gkcx 域名的请求
    gkcx_urls = [u for u in all_urls if 'gkcx' in u]
    print(f"\n  gkcx 域名请求 ({len(gkcx_urls)}):")
    for u in gkcx_urls:
        print(f"    {u[:150]}")

    # 打印所有 JSON 请求
    json_urls = [u for u in all_urls if '.json' in u]
    print(f"\n  JSON 请求 ({len(json_urls)}):")
    for u in json_urls:
        print(f"    {u[:150]}")

    br.close()

print("\n完成!")
