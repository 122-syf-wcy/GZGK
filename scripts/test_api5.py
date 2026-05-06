"""用正确的 type/batch 参数找到分数线 URL"""
import requests

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://gaokao.cn/',
}

# 贵州大学=109, 贵州=52
# 2024: type=2073(物理)/2074(历史), batch=14
# 2023: type=1(文)/2(理), batch=7

sid = 109
prov = 52
combos = [
    (2024, 2073, 14),  # 物理类
    (2023, 2, 7),      # 理科
]

bases = [
    "https://static-data.gaokao.cn/www/2.0/schoolspecialscore",
    "https://static-data.gaokao.cn/www/2.0/schoolscore",
]

print("穷举 URL 参数顺序")
print("=" * 50)

found_patterns = []

for year, tid, bid in combos:
    print(f"\n--- {year} type={tid} batch={bid} ---")
    # 生成所有可能的排列
    params = [sid, prov, year, tid, bid]
    import itertools
    # 只测试有意义的排列：sid 在最前，page=1 在最后
    orders = [
        f"{sid}/{prov}/{year}/{tid}/{bid}/1.json",
        f"{sid}/{prov}/{tid}/{year}/{bid}/1.json",
        f"{sid}/{year}/{prov}/{tid}/{bid}/1.json",
        f"{sid}/{tid}/{prov}/{year}/{bid}/1.json",
        f"{sid}/{bid}/{prov}/{year}/{tid}/1.json",
        f"{sid}/{year}/{tid}/{prov}/{bid}/1.json",
        f"{sid}/{prov}/{year}/{bid}/{tid}/1.json",
        # 不带 batch
        f"{sid}/{prov}/{year}/{tid}/1.json",
        f"{sid}/{year}/{prov}/{tid}/1.json",
        f"{sid}/{tid}/{year}/{prov}/1.json",
        # 不带 page
        f"{sid}/{prov}/{year}/{tid}/{bid}.json",
        f"{sid}/{prov}/{year}/{tid}.json",
    ]
    
    for base in bases:
        for order in orders:
            url = f"{base}/{order}"
            try:
                r = requests.get(url, headers=h, timeout=4)
                if r.status_code == 200 and len(r.text) > 100:
                    short = order
                    print(f"  ✅ {base.split('2.0/')[1]}/{short}")
                    print(f"     {r.text[:400]}")
                    found_patterns.append(url)
                    break
            except:
                pass
        if found_patterns:
            break
    
    if not found_patterns:
        print("  CDN 静态文件都 404")

if not found_patterns:
    print("\n\n--- 尝试动态 API (POST) ---")
    api_urls = [
        "https://api.eol.cn/gkcx/api/school/province/score",
        "https://api.eol.cn/gkcx/api/school/special/score", 
        "https://www.gaokao.cn/api/school/specialscore",
        "https://api.gaokao.cn/api/school/specialscore",
    ]
    payload = {
        "school_id": "109",
        "province_id": "52",
        "year": "2024",
        "type": "2073",
        "batch": "14",
        "page": 1,
        "size": 5,
    }
    for url in api_urls:
        try:
            r = requests.post(url, json=payload, headers=h, timeout=6)
            print(f"  POST {url.split('.cn')[1]}: {r.status_code} ({len(r.text)})")
            if r.status_code == 200 and len(r.text) > 50:
                print(f"    {r.text[:400]}")
        except Exception as e:
            print(f"  POST {url.split('.cn')[1]}: {type(e).__name__}")
        try:
            r = requests.get(url, params=payload, headers=h, timeout=6)
            print(f"  GET  {url.split('.cn')[1]}: {r.status_code} ({len(r.text)})")
            if r.status_code == 200 and len(r.text) > 50:
                print(f"    {r.text[:400]}")
        except Exception as e:
            print(f"  GET  {url.split('.cn')[1]}: {type(e).__name__}")

print("\n完成!")
