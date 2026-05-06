"""用 school_id=935 在 CDN 上尝试"""
import requests, json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://www.gaokao.cn/school/935/provinceline',
}

sid = 935
urls = [
    # CDN 静态
    f"https://static-data.gaokao.cn/www/2.0/school/{sid}/dic/specialscore.json",
    f"https://static-data.gaokao.cn/www/2.0/school/{sid}/info.json",
    f"https://static-data.gaokao.cn/www/2.0/school/{sid}/provincescore.json",
    f"https://static-data.gaokao.cn/www/2.0/school/{sid}/professionalscore.json",
    # www 域名下的 JSON (可能需要 cookie)
    f"https://www.gaokao.cn/school/{sid}/provincescore.json?a=www.gaokao.cn",
    f"https://www.gaokao.cn/school/{sid}/professionalscore.json?a=www.gaokao.cn",
    # api 子目录
    f"https://www.gaokao.cn/api/school/{sid}/provincescore.json?a=www.gaokao.cn",
    f"https://www.gaokao.cn/api/school/provincescore.json?school_id={sid}&a=www.gaokao.cn",
    # SSR 页面内嵌数据
    f"https://www.gaokao.cn/school/{sid}/json/provincescore.json",
]

for url in urls:
    try:
        r = requests.get(url, headers=h, timeout=8)
        # 缩短显示
        short = url.replace("https://static-data.gaokao.cn/www/2.0/", "CDN: ")
        short = short.replace("https://www.gaokao.cn/", "WWW: ")
        ok = r.status_code == 200 and len(r.text) > 100
        is_json = False
        if ok:
            try:
                r.json()
                is_json = True
            except:
                pass
        tag = "✅JSON" if (ok and is_json) else ("⚠️HTML" if (ok and not is_json) else f"❌{r.status_code}")
        print(f"  {tag} {short[:80]}  ({len(r.text)}B)")
        if ok and is_json:
            print(f"    {r.text[:400]}")
    except Exception as e:
        short = url.replace("https://static-data.gaokao.cn/www/2.0/", "CDN: ")
        short = short.replace("https://www.gaokao.cn/", "WWW: ")
        print(f"  ⚠️ERR {short[:80]}: {type(e).__name__}")

print("\n完成!")
