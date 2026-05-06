"""快速测试掌上高考 API 端点"""
import requests
import json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://gaokao.cn/',
}

print("=" * 50)
print("测试掌上高考 API")
print("=" * 50)

# 1) 院校名单 (已验证可用)
print("\n[1] 院校名单...")
try:
    r = requests.get('https://static-data.gaokao.cn/www/2.0/school/name.json', headers=h, timeout=10)
    print(f"  状态: {r.status_code}, 数据量: {len(r.text)} 字节")
    if r.status_code == 200:
        schools = r.json().get('data', [])
        print(f"  院校数: {len(schools)}")
        for s in schools[:3]:
            print(f"    - {s.get('name')} (ID: {s.get('school_id')})")
except Exception as e:
    print(f"  失败: {e}")

# 2) 院校详情
print("\n[2] 院校详情 (school_id=109 贵州大学)...")
for pattern in [
    'https://static-data.gaokao.cn/www/2.0/school/109/info.json',
    'https://static-data.gaokao.cn/www/2.0/school/109/pc_info.json',
]:
    try:
        r = requests.get(pattern, headers=h, timeout=8)
        short = pattern.split('109/')[1]
        print(f"  {r.status_code} {short} ({len(r.text)}字节)")
        if r.status_code == 200 and len(r.text) > 100:
            d = r.json()
            if 'data' in d:
                keys = list(d['data'].keys()) if isinstance(d['data'], dict) else 'list'
                print(f"    data keys: {keys[:10]}")
    except Exception as e:
        print(f"  失败: {type(e).__name__}")

# 3) 分数线
print("\n[3] 分数线接口...")
for pattern in [
    'https://static-data.gaokao.cn/www/2.0/schoolprovincescore/109/2024/52/5/1.json',
    'https://static-data.gaokao.cn/www/2.0/school/109/pc_score.json',
    'https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/2024/52/5/1.json',
]:
    try:
        r = requests.get(pattern, headers=h, timeout=8)
        short = pattern.split('gaokao.cn')[1][-50:]
        print(f"  {r.status_code} ...{short} ({len(r.text)}字节)")
        if r.status_code == 200 and len(r.text) > 100:
            d = r.json()
            print(f"    data preview: {json.dumps(d, ensure_ascii=False)[:300]}")
    except Exception as e:
        print(f"  失败: {type(e).__name__}")

# 4) 尝试动态API
print("\n[4] 动态API...")
try:
    r = requests.post(
        'https://api.zjzw.cn/web/api/school/lists',
        json={'province_id': '52', 'type': '1', 'page': 1, 'size': 3},
        headers=h, timeout=8
    )
    print(f"  zjzw.cn: {r.status_code} ({len(r.text)}字节)")
    if r.status_code == 200:
        print(f"    {r.text[:300]}")
except Exception as e:
    print(f"  zjzw.cn 失败: {type(e).__name__}")

print("\n测试完成！")
