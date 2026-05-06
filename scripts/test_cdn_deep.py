"""深入分析 CDN 数据结构，找到贵州省分数线的完整 URL"""
import requests, json

H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}
BASE = 'https://static-data.gaokao.cn/www/2.0'
SID = '935'

def get(url):
    r = requests.get(url, headers=H, timeout=10)
    return r.json() if r.status_code == 200 else None

# 1. 获取完整的 provincescore 目录
print("=== provincescore.json 完整结构 ===")
dic = get(f'{BASE}/school/{SID}/dic/provincescore.json?a=www.gaokao.cn')
if dic:
    data = dic['data']
    # data 字段包含按年份分组的详细信息
    data_list = data.get('data', [])
    print(f"data_list 长度: {len(data_list)}")

    # 找贵州(pid=52)的数据
    for year_entry in data_list:
        year = year_entry.get('year')
        provinces = year_entry.get('province', [])
        for prov in provinces:
            if prov.get('pid') == 52:
                print(f"\n  {year} 贵州(52):")
                print(f"    type: {prov.get('type')}")
                print(f"    batch: {prov.get('batch')}")
                print(f"    first: {prov.get('first')}")
                print(f"    batch_group: {json.dumps(prov.get('batch_group', {}), ensure_ascii=False)[:300]}")

    # newsdata.type 和 batch 中贵州的组合
    nd = data.get('newsdata', {})
    print(f"\n=== 贵州的 type 组合 ===")
    for key, val in nd.get('type', {}).items():
        if key.startswith('52_'):
            print(f"  {key}: {val}")

    print(f"\n=== 贵州的 batch 组合 ===")
    for key, val in nd.get('batch', {}).items():
        if key.startswith('52_'):
            print(f"  {key}: {val}")

# 2. 获取 specialscore 目录（专业分数线）
print("\n\n=== specialscore.json 贵州部分 ===")
dic2 = get(f'{BASE}/school/{SID}/dic/specialscore.json?a=www.gaokao.cn')
if dic2:
    data2 = dic2['data']
    nd2 = data2.get('newsdata', {})
    print("贵州 type:")
    for key, val in nd2.get('type', {}).items():
        if key.startswith('52_'):
            print(f"  {key}: {val}")
    print("贵州 batch:")
    for key, val in nd2.get('batch', {}).items():
        if key.startswith('52_'):
            print(f"  {key}: {val}")

# 3. benchmarkScore 完整数据（贵州部分）
print("\n\n=== benchmarkScore 贵州部分 ===")
bench = get(f'{BASE}/school/{SID}/benchmarkScore.json?a=www.gaokao.cn')
if bench:
    bd = bench.get('data', {})
    for key, val in sorted(bd.items()):
        if '_52_' in key:
            print(f"  {key}: {val}")

# 4. 尝试构造实际分数线数据 URL
# 从目录看: 52_2024_1 -> batch [7]，52_2025_2073 -> batch [14,6]
print("\n\n=== 尝试分数线数据 URL ===")
# 贵州2024年理科: province=52, year=2024, type=1, batch=7
combos = [
    # year, province, type, batch
    (2025, 52, 2073, 14),
    (2025, 52, 2073, 6),
    (2024, 52, 1, 7),
    (2024, 52, 2, 7),
    (2023, 52, 1, 7),
]
for year, prov, typ, batch in combos:
    urls = [
        f'{BASE}/school/{SID}/{prov}/{year}/{typ}/{batch}/provincescore.json',
        f'{BASE}/school/{SID}/{prov}/{year}/{typ}/provincescore.json',
        f'{BASE}/school/{SID}/{year}/{prov}/{typ}/{batch}.json',
        f'{BASE}/school/score/{SID}/{prov}/{year}/{typ}/{batch}.json',
        f'https://static-gkcx.gaokao.cn/www/2.0/json/school/provincescore/{SID}/{prov}/{year}/{typ}/{batch}.json',
        f'https://static-gkcx.gaokao.cn/www/2.0/json/school/score/{SID}/{prov}/{year}/{typ}.json',
    ]
    for url in urls:
        d = get(url + '?a=www.gaokao.cn')
        if d:
            print(f"✅ {url}")
            print(f"   {json.dumps(d, ensure_ascii=False)[:400]}")

# 5. 从 gkcx 域名找 rank 数据（这个之前在网络日志中看到过）
print("\n\n=== gkcx rank 数据 ===")
# 之前捕获到: static-gkcx.gaokao.cn/www/2.0/json/rank/9549/52/lists.json
# 9549 可能是另一个ID格式
rank_urls = [
    f'https://static-gkcx.gaokao.cn/www/2.0/json/rank/935/52/lists.json',
    f'https://static-gkcx.gaokao.cn/www/2.0/json/rank/9549/52/lists.json',
]
for url in rank_urls:
    d = get(url + '?a=www.gaokao.cn')
    if d:
        print(f"✅ {url}")
        preview = json.dumps(d, ensure_ascii=False)[:500]
        print(f"   {preview}")

print("\n完成!")
