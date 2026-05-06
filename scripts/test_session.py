"""用 Session 先访问页面获取 Cookie，再请求 JSON"""
import requests, json

s = requests.Session()
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
})

# 1) 先访问页面获取 cookies
print("[1] 访问贵州大学分数线页面...")
r = s.get('https://www.gaokao.cn/school/935/provinceline', timeout=10)
print(f"  页面: {r.status_code}, cookies: {dict(s.cookies)}")

# 2) 用同一 session 请求分数线 JSON
print("\n[2] 请求分数线数据...")
json_urls = [
    'https://www.gaokao.cn/api/school/935/provincescore.json',
    'https://www.gaokao.cn/school/935/provincescore.json',
    # 从页面HTML中可能发现的API路径
]

# 也从页面 HTML 中提取可能的 API base URL
html = r.text
import re
# 找 JSON 数据或 API 路径
api_patterns = re.findall(r'(https?://[^"\']+(?:score|special)[^"\']*\.json[^"\']*)', html)
if api_patterns:
    print(f"  页面中发现 API URL: {api_patterns[:5]}")
    json_urls.extend(api_patterns[:5])

# 找 __NUXT__ 或类似的 SSR 数据
nuxt_match = re.search(r'window\.__NUXT__\s*=\s*', html)
next_match = re.search(r'__NEXT_DATA__', html)
config_match = re.findall(r'(api[A-Za-z]*[Uu]rl|baseURL|apiBase)["\']?\s*[:=]\s*["\']([^"\']+)', html)
print(f"  NUXT: {bool(nuxt_match)}, NEXT: {bool(next_match)}")
if config_match:
    print(f"  Config URLs: {config_match[:5]}")

# 找 script src 可能包含 API 配置
scripts = re.findall(r'src="(/assets/js/[^"]+)"', html)
print(f"  Scripts: {scripts[:5]}")

# 请求 JSON
for url in json_urls:
    if '?' not in url:
        url += '?a=www.gaokao.cn'
    try:
        r2 = s.get(url, timeout=8)
        is_json = False
        try:
            r2.json()
            is_json = True
        except:
            pass
        tag = "✅" if (r2.status_code == 200 and is_json) else f"❌{r2.status_code}"
        short = url.replace('https://www.gaokao.cn/', '')[:60]
        print(f"  {tag} {short} ({len(r2.text)}B)")
        if is_json and r2.status_code == 200:
            print(f"    {r2.text[:300]}")
    except Exception as e:
        print(f"  ERR: {e}")

# 3) 尝试从 tdkconfig.js 找 API 配置
print("\n[3] 检查配置文件...")
for js_url in ['https://www.gaokao.cn/assets/js/tdkconfig.js']:
    try:
        r3 = s.get(js_url, timeout=8)
        print(f"  {r3.status_code} tdkconfig.js ({len(r3.text)}B)")
        if r3.status_code == 200:
            # 找 API 相关配置
            api_refs = re.findall(r'["\']([^"\']*api[^"\']*)["\']', r3.text)
            base_refs = re.findall(r'(base[Uu]rl|apiUrl|domain)\s*[=:]\s*["\']([^"\']+)', r3.text)
            if api_refs:
                print(f"    API refs: {api_refs[:5]}")
            if base_refs:
                print(f"    Base refs: {base_refs[:5]}")
            print(f"    Content: {r3.text[:500]}")
    except:
        pass

print("\n完成!")
