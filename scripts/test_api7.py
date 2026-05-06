"""分析 dic/specialscore.json 的 pids 结构，找到分数线数据"""
import requests, json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://www.gaokao.cn/school/935/provinceline',
}

# 拿贵州大学的专业分数线目录
r = requests.get('https://static-data.gaokao.cn/www/2.0/school/935/dic/specialscore.json', headers=h, timeout=10)
d = r.json()['data']

# 分析 pids 结构
pids = d.get('pids', [])
print(f"pids type: {type(pids).__name__}, len: {len(pids)}")
if pids:
    print(f"pids[0]: {json.dumps(pids[0], ensure_ascii=False)[:300]}")
    print(f"pids[1]: {json.dumps(pids[1], ensure_ascii=False)[:300]}")

# newsdata 中贵州(52)的完整结构
nd = d['newsdata']
# 贵州2024: type=2073(物理)/2074(历史), batch=14
# 贵州2023: type=1(文)/2(理), batch=7

# groups 字段
groups = nd.get('groups', {})
for key in list(groups.keys()):
    if '52' in key:
        print(f"\ngroups[{key}] = {json.dumps(groups[key], ensure_ascii=False)[:200]}")

# year 顶层
top_year = d.get('year', {})
for key in list(top_year.keys()):
    if '52' in str(key):
        print(f"\nyear[{key}] = {json.dumps(top_year[key], ensure_ascii=False)[:200]}")

# 用 pids 里的 ID 构造可能的 URL
if pids and isinstance(pids[0], (int, str)):
    pid = pids[0]
    test_urls = [
        f"https://static-data.gaokao.cn/www/2.0/schoolspecialscore/935/{pid}.json",
        f"https://static-data.gaokao.cn/www/2.0/schoolspecialscore/{pid}.json",
        f"https://static-data.gaokao.cn/www/2.0/school/935/specialscore/{pid}.json",
    ]
    print(f"\n用 pid={pid} 测试:")
    for url in test_urls:
        try:
            r2 = requests.get(url, headers=h, timeout=6)
            short = url.split('gaokao.cn/www/2.0/')[1]
            print(f"  {'✅' if r2.status_code==200 and len(r2.text)>100 else '❌'} {r2.status_code} {short}")
            if r2.status_code == 200 and len(r2.text) > 100:
                print(f"    {r2.text[:500]}")
        except Exception as e:
            print(f"  ERR: {e}")

# 也许 pids 是 province-year-type 的组合ID
# 从 newsdata 提取贵州的 key 组合
print("\n\n=== 尝试组合 URL ===")
# 52_2024_2073_14 格式
combos = ["52_2024_2073_14", "52_2023_2_7", "52_2024_2074_14"]
for combo in combos:
    parts = combo.split("_")
    test_urls = [
        f"https://static-data.gaokao.cn/www/2.0/school/935/specialscore/{combo}.json",
        f"https://static-data.gaokao.cn/www/2.0/schoolspecialscore/935/{'/'.join(parts)}/1.json",
        f"https://static-data.gaokao.cn/www/2.0/school/935/{'/'.join(parts)}/specialscore.json",
    ]
    for url in test_urls:
        try:
            r2 = requests.get(url, headers=h, timeout=5)
            if r2.status_code == 200 and len(r2.text) > 100:
                short = url.split('935/')[1]
                print(f"  ✅ {r2.status_code} ...935/{short}")
                print(f"    {r2.text[:500]}")
        except:
            pass

print("\n完成!")
