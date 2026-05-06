"""用截图中看到的真实 API 路径测试"""
import requests
import json

h = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Referer': 'https://www.gaokao.cn/school/935/provinceline',
    'Accept': 'application/json, text/plain, */*',
}

# 截图中看到的请求 (school_id=935=贵州大学)
# 筛选: 贵州, 2025, 物理类, 本科批
urls = [
    # 院校在各省的分数线概览
    "https://www.gaokao.cn/api/school/935/provincescore.json?a=www.gaokao.cn",
    # 专业分数线
    "https://www.gaokao.cn/api/school/935/professionalscore.json?a=www.gaokao.cn",
    # 基准分
    "https://www.gaokao.cn/api/school/935/benchmarkScore.json?a=www.gaokao.cn",
    # 院校基本信息
    "https://www.gaokao.cn/api/school/935/81002.json?a=www.gaokao.cn",
    "https://www.gaokao.cn/api/school/935/81001.json?a=www.gaokao.cn",
    # 带参数的列表
    "https://www.gaokao.cn/api/school/935/list?province=%E8%B4%B5%E5%B7%9E&a=www.gaokao.cn",
]

for url in urls:
    try:
        r = requests.get(url, headers=h, timeout=10)
        short = url.split("935/")[1][:50]
        print(f"{'✅' if r.status_code == 200 else '❌'} {r.status_code} .../{short}  ({len(r.text)} bytes)")
        if r.status_code == 200 and len(r.text) > 50:
            try:
                d = r.json()
                if isinstance(d, dict):
                    print(f"   keys: {list(d.keys())[:8]}")
                    if 'data' in d:
                        data = d['data']
                        if isinstance(data, dict):
                            print(f"   data keys: {list(data.keys())[:10]}")
                            # 打印部分数据
                            for k in list(data.keys())[:2]:
                                v = data[k]
                                vs = json.dumps(v, ensure_ascii=False)[:200]
                                print(f"   data[{k}]: {vs}")
                        elif isinstance(data, list):
                            print(f"   data: list of {len(data)}")
                            if data:
                                print(f"   first: {json.dumps(data[0], ensure_ascii=False)[:300]}")
                elif isinstance(d, list):
                    print(f"   list of {len(d)}")
                    if d:
                        print(f"   first: {json.dumps(d[0], ensure_ascii=False)[:300]}")
            except:
                print(f"   (非JSON) {r.text[:150]}")
        print()
    except Exception as e:
        short = url.split("935/")[1][:50]
        print(f"⚠️ .../{short}: {type(e).__name__}: {e}")
        print()

print("完成!")
