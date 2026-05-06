"""快速预筛选: 扫描所有院校的 benchmarkScore 找出在贵州招生的"""
import requests, json, os, time

CDN = 'https://static-data.gaokao.cn/www/2.0'
H = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.gaokao.cn/'}
DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA, exist_ok=True)

print("获取院校列表...")
r = requests.get(f'{CDN}/school/name.json', headers=H, timeout=15)
schools = r.json().get('data', [])
print(f"总共 {len(schools)} 所")

guizhou = []
errors = 0
for i, s in enumerate(schools):
    sid = str(s.get('school_id', ''))
    try:
        r = requests.get(f'{CDN}/school/{sid}/benchmarkScore.json', headers=H, timeout=8)
        if r.status_code == 200:
            data = r.json().get('data', {})
            has_gz = any(f'_52_' in k for k in data)
            if has_gz:
                guizhou.append(s)
        elif r.status_code == 404:
            pass
        else:
            errors += 1
    except:
        errors += 1

    if (i + 1) % 200 == 0:
        print(f"  {i+1}/{len(schools)} 扫描完, 贵州: {len(guizhou)}, 错误: {errors}")
    time.sleep(0.08)

# 保存
out = os.path.join(DATA, "guizhou_schools.json")
with open(out, 'w', encoding='utf-8') as f:
    json.dump(guizhou, f, ensure_ascii=False, indent=2)

print(f"\n完成!")
print(f"  总扫描: {len(schools)}")
print(f"  贵州招生: {len(guizhou)}")
print(f"  错误: {errors}")
print(f"  保存: {out}")

# 打印前20所
print(f"\n前20所:")
for s in guizhou[:20]:
    print(f"  {s.get('school_id')}: {s.get('name', '?')}")
