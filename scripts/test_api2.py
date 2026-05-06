"""探测分数线API的正确路径格式"""
import requests
import json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://gaokao.cn/',
    'Origin': 'https://www.gaokao.cn',
}

print("探测分数线 API 路径")
print("=" * 50)

# 贵州大学 school_id=109, 贵州省=52
# 尝试所有可能的 URL 格式
patterns = [
    # 专业分数线
    "https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/52/5/2024/1.json",
    "https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/52/2024/5/1.json",
    "https://static-data.gaokao.cn/www/2.0/school/109/score/52/5/2024.json",
    "https://static-data.gaokao.cn/www/2.0/school/109/specialscore/52/5/2024.json",
    # 院校分数线概览
    "https://static-data.gaokao.cn/www/2.0/schoolprovincescore/109/52/5/2024.json",
    "https://static-data.gaokao.cn/www/2.0/schoolprovincescore/109/52/2024/5.json",
    "https://static-data.gaokao.cn/www/2.0/school/109/provincescore.json",
    # 3.0 版本?
    "https://static-data.gaokao.cn/www/3.0/schoolprovincescore/109/2024/52/5/1.json",
    "https://static-data.gaokao.cn/www/3.0/schoolspecialscore/109/2024/52/5/1.json",
    "https://static-data.gaokao.cn/www/3.0/school/109/info.json",
    # score 接口
    "https://static-data.gaokao.cn/www/2.0/school/109/dic/specialscore.json",
    "https://static-data.gaokao.cn/www/2.0/school/109/dic/score.json",
    # 2023年（旧高考）
    "https://static-data.gaokao.cn/www/2.0/schoolprovincescore/109/2023/52/5/1.json",
    "https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/2023/52/5/1.json",
    "https://static-data.gaokao.cn/www/2.0/schoolspecialscore/109/52/5/2023/1.json",
]

for url in patterns:
    try:
        r = requests.get(url, headers=h, timeout=6)
        # 只显示简短路径
        short = url.replace("https://static-data.gaokao.cn/www/", "")
        status = "✅" if r.status_code == 200 and len(r.text) > 100 else "❌"
        print(f"  {status} {r.status_code} {short}")
        if r.status_code == 200 and len(r.text) > 100:
            print(f"     >>> 找到! 数据: {r.text[:400]}")
            print()
    except Exception as e:
        short = url.replace("https://static-data.gaokao.cn/www/", "")
        print(f"  ⚠️  {short}: {type(e).__name__}")

print("\n完成!")
