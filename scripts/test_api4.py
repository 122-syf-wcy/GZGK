"""精准分析目录结构，找到贵州分数线URL"""
import requests, json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://gaokao.cn/',
}

# 拿完整目录
r = requests.get('https://static-data.gaokao.cn/www/2.0/school/109/dic/specialscore.json', headers=h, timeout=10)
dic = r.json()['data']
nd = dic['newsdata']

# 提取贵州(52)相关的 type 和 year
print("=== 贵州(52) 相关数据 ===")
years_for_52 = nd['year'].get('52', [])
print(f"年份: {years_for_52}")

# type 格式: {province}_{year} -> [type_ids]
for key, val in nd['type'].items():
    if key.startswith('52_'):
        print(f"type[{key}] = {val}")

# batch
for key, val in nd.get('batch', {}).items():
    if key.startswith('52_'):
        print(f"batch[{key}] = {val}")

# group
for key, val in nd.get('group', {}).items():
    if '52' in key:
        print(f"group[{key}] = {val}")

# pids 结构
pids = dic.get('pids', {})
print(f"\npids keys (前10): {list(pids.keys())[:10]}")
# 找贵州相关的 pid
for key in list(pids.keys())[:3]:
    print(f"pids[{key}] = {pids[key][:3]}...")

# top-level year
top_year = dic.get('year', {})
print(f"\ntop-level year keys (前5): {list(top_year.keys())[:5]}")
for key in list(top_year.keys())[:2]:
    print(f"year[{key}] = {top_year[key]}")

print("\n=== 尝试用 pids 构造 URL ===")
# 可能 URL 里用的是 pid 而不是 type_id
# 从 type 取贵州2024的值
type_2024 = nd['type'].get('52_2024', [])
type_2023 = nd['type'].get('52_2023', [])
batch_2024 = nd.get('batch', {}).get('52_2024', [])
batch_2023 = nd.get('batch', {}).get('52_2023', [])
print(f"type_2024={type_2024}, batch_2024={batch_2024}")
print(f"type_2023={type_2023}, batch_2023={batch_2023}")

base = "https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109"
found = False

# 用真实的 type_id 和 batch 组合
for year, types, batches in [(2024, type_2024, batch_2024), (2023, type_2023, batch_2023)]:
    for tid in types:
        for bid in (batches if batches else [0, 1, 2, 7, 14]):
            urls = [
                f"{base}/{52}/{year}/{tid}/{bid}/1.json",
                f"{base}/{52}/{tid}/{year}/{bid}/1.json",
                f"{base}/{year}/{52}/{tid}/{bid}/1.json",
                f"{base}/{52}/{year}/{tid}/1.json",
            ]
            for url in urls:
                try:
                    r2 = requests.get(url, headers=h, timeout=5)
                    if r2.status_code == 200 and len(r2.text) > 100:
                        short = url.replace(base + "/", "")
                        print(f"\n✅ 找到! {short}")
                        print(f"   {r2.text[:600]}")
                        found = True
                        break
                except:
                    pass
            if found:
                break
        if found:
            break
    if found:
        break

if not found:
    print("仍未找到。尝试 schoolprovinceindex...")
    for year in [2024, 2023]:
        for tid in type_2024 + type_2023 + [1, 5]:
            url = f"https://static-data.gaokao.cn/www/2.0/schoolprovinceindex/{year}/{52}/{tid}/1.json"
            try:
                r2 = requests.get(url, headers=h, timeout=5)
                if r2.status_code == 200 and len(r2.text) > 100:
                    short = url.replace("https://static-data.gaokao.cn/www/2.0/", "")
                    print(f"\n✅ 找到! {short}")
                    print(f"   {r2.text[:600]}")
                    found = True
                    break
            except:
                pass
        if found:
            break

print("\n完成!")
