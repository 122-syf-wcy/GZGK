"""根据 dic 目录找到分数线数据的正确 URL"""
import requests
import json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://gaokao.cn/',
}

print("根据目录数据探测专业分数线 URL")
print("=" * 50)

# 先拿目录看结构
r = requests.get('https://static-data.gaokao.cn/www/2.0/school/109/dic/specialscore.json', headers=h, timeout=10)
dic = r.json()['data']
print(f"目录 keys: {list(dic.keys())}")
newsdata = dic.get('newsdata', {})
print(f"newsdata keys: {list(newsdata.keys())}")

# 贵州=52 的年份和科类
year_data = newsdata.get('year', {})
# 找到 52(贵州) 对应的 type
type_data = newsdata.get('type', {})
print(f"\nyear entries (前5): {dict(list(year_data.items())[:5])}")
print(f"type entries (前5): {dict(list(type_data.items())[:5])}")

# 在 province 列表中确认 52 存在
provinces = newsdata.get('province', [])
print(f"\nprovinces 包含52: {52 in provinces}")

# 尝试用 province_id + year + type 拼 URL
# type: 可能是 1=文科/历史, 5=理科/物理  或  其他编码
# 贵州 province=52, year=2024
base = "https://static-data.gaokao.cn/www/2.0/schoolspecialscore"

# 穷举 type 值（1-10）和不同的 URL 顺序
combos = []
for type_id in [1, 2, 5, 73, 2073, 2074]:
    for year in [2024, 2023]:
        combos.extend([
            f"{base}/109/{year}/{52}/{type_id}/1.json",
            f"{base}/109/{52}/{year}/{type_id}/1.json",
            f"{base}/109/{52}/{type_id}/{year}/1.json",
            f"{base}/109/{type_id}/{52}/{year}/1.json",
            f"{base}/109/{year}/{type_id}/{52}/1.json",
        ])

# 去重
seen = set()
unique = []
for c in combos:
    if c not in seen:
        seen.add(c)
        unique.append(c)

print(f"\n测试 {len(unique)} 个 URL 组合...")
for url in unique:
    try:
        r = requests.get(url, headers=h, timeout=5)
        short = url.replace("https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/", "")
        if r.status_code == 200 and len(r.text) > 100:
            print(f"  ✅ {short} ({len(r.text)} 字节)")
            print(f"     数据: {r.text[:500]}")
            print()
            break
        # 不打印 404
    except:
        pass
else:
    print("  全部 404，尝试其他 base URL...")
    # 尝试不同的 base
    alt_bases = [
        "https://static-data.gaokao.cn/www/2.0/schoolspecialindex",
        "https://static-data.gaokao.cn/www/2.0/schoolscore",
    ]
    for ab in alt_bases:
        for year in [2024, 2023]:
            for tid in [1, 5]:
                url = f"{ab}/109/{year}/{52}/{tid}/1.json"
                try:
                    r = requests.get(url, headers=h, timeout=5)
                    short = url.replace("https://static-data.gaokao.cn/www/2.0/", "")
                    if r.status_code == 200 and len(r.text) > 100:
                        print(f"  ✅ {short} ({len(r.text)} 字节)")
                        print(f"     数据: {r.text[:500]}")
                        break
                except:
                    pass

print("\n完成!")
