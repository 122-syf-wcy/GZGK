"""测试 CDN 静态 JSON 端点，找到分数线数据的直接 URL"""
import requests, json

H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}
BASE = 'https://static-data.gaokao.cn/www/2.0'
SID = '935'  # 贵州大学

def test(url, label=""):
    try:
        r = requests.get(url, headers=H, timeout=10)
        if r.status_code == 200:
            data = r.json()
            preview = json.dumps(data, ensure_ascii=False)[:400]
            print(f"✅ [{label}] {url}")
            print(f"   {preview}\n")
            return data
        else:
            print(f"❌ {r.status_code} [{label}] {url}")
    except Exception as e:
        print(f"❌ [{label}] {url} -> {e}")
    return None

# 1. 院校级分数线目录
print("=== 1. 分数线目录 ===")
dic = test(f'{BASE}/school/{SID}/dic/specialscore.json', '专业分数目录')
dic2 = test(f'{BASE}/school/{SID}/dic/provincescore.json', '省份分数目录')

# 2. 如果目录存在，解析它的结构
if dic:
    data = dic.get('data', {})
    print(f"\n=== 专业分数目录结构 ===")
    print(f"  keys: {list(data.keys())[:10]}")
    # 看 pids 结构
    pids = data.get('pids', data.get('newsdata', {}))
    if isinstance(pids, dict):
        for k, v in list(pids.items())[:5]:
            print(f"  pids[{k}] = {json.dumps(v, ensure_ascii=False)[:200]}")
    elif isinstance(pids, list):
        for item in pids[:5]:
            print(f"  pids item: {json.dumps(item, ensure_ascii=False)[:200]}")

if dic2:
    data2 = dic2.get('data', {})
    print(f"\n=== 省份分数目录结构 ===")
    print(f"  keys: {list(data2.keys())[:10]}")
    if isinstance(data2, dict):
        for k, v in list(data2.items())[:5]:
            print(f"  [{k}] = {json.dumps(v, ensure_ascii=False)[:300]}")

# 3. 尝试各种分数线 URL 模式
print("\n=== 2. 直接分数线数据 ===")
# 贵州省ID=52, 物理类=2073, 历史类=2074, 理科=1, 文科=2
patterns = [
    f'{BASE}/school/{SID}/provincescore.json',
    f'{BASE}/school/{SID}/pc_provincescore.json',
    f'{BASE}/school/{SID}/professionalscore.json',
    f'{BASE}/school/{SID}/pc_professionalscore.json',
    f'{BASE}/school/{SID}/benchmarkScore.json',
    # 带省份
    f'{BASE}/school/{SID}/52/provincescore.json',
    f'{BASE}/school/{SID}/52/2024/provincescore.json',
    f'{BASE}/school/{SID}/52/2024/2073/provincescore.json',
    # gkcx 域名
    'https://static-gkcx.gaokao.cn/www/2.0/json/school/info/935.json',
    'https://static-gkcx.gaokao.cn/www/2.0/json/school/score/935.json',
    'https://static-gkcx.gaokao.cn/www/2.0/json/school/provincescore/935.json',
    # rank 数据
    f'https://static-gkcx.gaokao.cn/www/2.0/json/rank/{SID}/52/lists.json',
]

for url in patterns:
    test(url + '?a=www.gaokao.cn', url.split('/')[-1])

# 4. 如果目录有数据，构造具体分数线 URL
print("\n=== 3. 从目录构造 URL ===")
if dic:
    data = dic.get('data', {})
    # specialscore 目录通常包含 province->year->type->batch 映射
    # 尝试遍历数据结构找到贵州(52)的组合
    for key in ['52', 'pids', 'newsdata']:
        if key in data:
            entry = data[key]
            print(f"\n  data['{key}'] type={type(entry).__name__}")
            if isinstance(entry, dict):
                for k2, v2 in list(entry.items())[:5]:
                    print(f"    [{k2}] = {json.dumps(v2, ensure_ascii=False)[:200]}")
            elif isinstance(entry, list):
                for item in entry[:5]:
                    print(f"    {json.dumps(item, ensure_ascii=False)[:200]}")

if dic2:
    data2 = dic2.get('data', {})
    for key in ['52', 'pids', 'newsdata']:
        if key in data2:
            entry = data2[key]
            print(f"\n  provincescore data['{key}'] type={type(entry).__name__}")
            if isinstance(entry, dict):
                for k2, v2 in list(entry.items())[:5]:
                    print(f"    [{k2}] = {json.dumps(v2, ensure_ascii=False)[:200]}")
            elif isinstance(entry, list):
                for item in entry[:3]:
                    print(f"    {json.dumps(item, ensure_ascii=False)[:200]}")

# 5. 尝试 rank 数据
print("\n=== 4. Rank 数据 ===")
rank = test(f'https://static-gkcx.gaokao.cn/www/2.0/json/rank/{SID}/52/lists.json?a=www.gaokao.cn', 'rank_lists')
if rank:
    rd = rank.get('data', {})
    if isinstance(rd, dict):
        for k, v in list(rd.items())[:5]:
            print(f"  [{k}] = {json.dumps(v, ensure_ascii=False)[:300]}")
    elif isinstance(rd, list):
        for item in rd[:5]:
            print(f"  {json.dumps(item, ensure_ascii=False)[:300]}")

print("\n完成!")
