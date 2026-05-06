"""记录访问分数线页面时的所有网络请求，找到真实 API URL"""
from playwright.sync_api import sync_playwright
import json, os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(SCRIPT_DIR, "data", "network_log.json")

requests_log = []

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    context = browser.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
        viewport={'width': 1280, 'height': 800},
        locale='zh-CN',
    )
    page = context.new_page()

    def on_response(response):
        url = response.url
        # 过滤掉静态资源
        skip = ['.css', '.js', '.png', '.jpg', '.gif', '.svg', '.woff', '.ico', 'google', 'baidu', 'cnzz', 'push.js']
        if any(s in url.lower() for s in skip):
            return
        entry = {
            "status": response.status,
            "url": url,
            "content_type": response.headers.get("content-type", ""),
            "size": len(response.body()) if response.status == 200 else 0,
        }
        # 如果是 JSON 且包含分数相关数据，保存 body
        if response.status == 200 and 'json' in entry["content_type"]:
            try:
                body = response.json()
                entry["body_preview"] = json.dumps(body, ensure_ascii=False)[:500]
            except:
                pass
        requests_log.append(entry)
        print(f"  {response.status} {url[:120]}")

    page.on("response", on_response)

    print("访问贵州大学分数线页面...")
    page.goto("https://www.gaokao.cn/school/935/provinceline", wait_until="networkidle", timeout=30000)
    page.wait_for_timeout(3000)

    print(f"\n记录了 {len(requests_log)} 个请求")
    print("\n保存到:", LOG_FILE)

    os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)
    with open(LOG_FILE, "w", encoding="utf-8") as f:
        json.dump(requests_log, f, ensure_ascii=False, indent=2)

    # 打印所有 JSON 请求
    print("\n=== JSON 请求 ===")
    for r in requests_log:
        if 'json' in r.get("content_type", "") or r["url"].endswith(".json"):
            print(f"  {r['status']} [{r['size']}B] {r['url'][:150]}")
            if 'body_preview' in r:
                print(f"    {r['body_preview'][:200]}")

    browser.close()

print("\n完成!")
