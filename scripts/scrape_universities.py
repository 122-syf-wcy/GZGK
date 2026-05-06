"""
院校信息爬取脚本
数据源: 掌上高考 API (api.eol.cn)
爬取内容: 在贵州招生的所有本科院校基本信息 + 校徽图片

用法:
    python scrape_universities.py
"""

import json
import os
import time
import requests
from tqdm import tqdm
from config import (
    UNIVERSITY_LIST_URL, UNIVERSITY_DETAIL_URL, STATIC_BASE,
    PROVINCE_ID, HEADERS, REQUEST_DELAY, REQUEST_DELAY_IMAGE,
    MAX_RETRIES, RETRY_DELAY, TIMEOUT,
    UNIVERSITY_DIR, IMAGE_DIR,
)


def fetch_with_retry(url: str, params: dict = None, data: dict = None,
                     method: str = "GET") -> dict | None:
    """带重试的请求"""
    for attempt in range(MAX_RETRIES):
        try:
            if method == "POST":
                resp = requests.post(url, json=data, headers=HEADERS, timeout=TIMEOUT)
            else:
                resp = requests.get(url, params=params, headers=HEADERS, timeout=TIMEOUT)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            print(f"  [重试 {attempt + 1}/{MAX_RETRIES}] {e}")
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY)
    return None


def fetch_university_list(page: int = 1, size: int = 20) -> dict | None:
    """获取院校列表（在贵州招生的本科院校）"""
    payload = {
        "province_id": PROVINCE_ID,
        "type": "1",           # 本科
        "page": page,
        "size": size,
        "sort": "view_total",
    }
    return fetch_with_retry(UNIVERSITY_LIST_URL, data=payload, method="POST")


def fetch_university_detail(school_id: str) -> dict | None:
    """获取院校详情"""
    params = {"school_id": school_id}
    return fetch_with_retry(UNIVERSITY_DETAIL_URL, params=params)


def download_image(url: str, save_path: str) -> bool:
    """下载图片"""
    if os.path.exists(save_path):
        return True
    try:
        resp = requests.get(url, headers=HEADERS, timeout=TIMEOUT, stream=True)
        resp.raise_for_status()
        with open(save_path, "wb") as f:
            for chunk in resp.iter_content(8192):
                f.write(chunk)
        return True
    except Exception as e:
        print(f"  图片下载失败: {e}")
        return False


def parse_university(raw: dict) -> dict:
    """解析院校数据为标准格式"""
    # 判断层次标签
    tags = []
    f985 = raw.get("f985", 0)
    f211 = raw.get("f211", 0)
    dual_class = raw.get("dual_class", 0)

    if f985:
        tags.append("985")
    if f211:
        tags.append("211")
    if dual_class:
        tags.append("双一流")

    level_tag = raw.get("level_name", "")
    school_type = raw.get("type_name", "")

    # 确定院校层次
    if f985:
        level = "985"
    elif f211:
        level = "211"
    elif dual_class:
        level = "双一流"
    else:
        level = level_tag if level_tag else "普通本科"

    return {
        "school_id": str(raw.get("school_id", "")),
        "name": raw.get("name", ""),
        "province_name": raw.get("province_name", ""),
        "city_name": raw.get("city_name", ""),
        "level": level,
        "level_name": level_tag,
        "type_name": school_type,
        "tags": tags,
        "f985": bool(f985),
        "f211": bool(f211),
        "dual_class": bool(dual_class),
        "logo_url": raw.get("logo", ""),
        "school_site": raw.get("school_site", ""),
        "content": raw.get("content", ""),      # 简介（如果有）
        "belong": raw.get("belong", ""),         # 隶属
        "nature_name": raw.get("nature_name", ""),  # 公办/民办
        "phone": raw.get("phone", ""),
        "email": raw.get("email", ""),
        "address": raw.get("address", ""),
    }


def main():
    print("=" * 60)
    print("  贵州招生院校信息爬取")
    print("  数据源: 掌上高考 (api.eol.cn)")
    print("=" * 60)

    all_universities = []
    page = 1
    size = 20
    total = None

    # 1. 爬取院校列表
    print("\n[1/3] 获取院校列表...")
    while True:
        result = fetch_university_list(page=page, size=size)
        if not result:
            print(f"  第 {page} 页请求失败，停止")
            break

        data = result.get("data", {})
        if total is None:
            total = data.get("numFound", 0)
            print(f"  共发现 {total} 所院校")

        items = data.get("item", [])
        if not items:
            break

        for item in items:
            uni = parse_university(item)
            all_universities.append(uni)

        print(f"  已获取 {len(all_universities)} / {total}")
        if len(all_universities) >= total:
            break

        page += 1
        time.sleep(REQUEST_DELAY)

    if not all_universities:
        print("未获取到任何院校数据，请检查网络或 API 接口")
        return

    # 2. 补充详情（可选，获取简介等）
    print(f"\n[2/3] 获取院校详情（共 {len(all_universities)} 所）...")
    for i, uni in enumerate(tqdm(all_universities, desc="院校详情")):
        detail = fetch_university_detail(uni["school_id"])
        if detail and "data" in detail:
            d = detail["data"]
            uni["content"] = d.get("content", uni.get("content", ""))
            uni["school_site"] = d.get("school_site", uni.get("school_site", ""))
            uni["belong"] = d.get("belong", uni.get("belong", ""))
            uni["phone"] = d.get("phone", uni.get("phone", ""))
            uni["email"] = d.get("email", uni.get("email", ""))
            uni["address"] = d.get("address", uni.get("address", ""))
            # 更新 logo
            if d.get("logo"):
                uni["logo_url"] = d["logo"]
        time.sleep(REQUEST_DELAY)

    # 3. 下载校徽图片
    print(f"\n[3/3] 下载校徽图片...")
    success_count = 0
    for uni in tqdm(all_universities, desc="下载校徽"):
        logo_url = uni.get("logo_url", "")
        if not logo_url:
            continue
        # 补全 URL
        if logo_url.startswith("//"):
            logo_url = "https:" + logo_url
        elif logo_url.startswith("/"):
            logo_url = STATIC_BASE + logo_url

        ext = os.path.splitext(logo_url.split("?")[0])[-1] or ".png"
        save_name = f"{uni['school_id']}{ext}"
        save_path = os.path.join(IMAGE_DIR, save_name)

        if download_image(logo_url, save_path):
            uni["logo_local"] = save_name
            success_count += 1

        time.sleep(REQUEST_DELAY_IMAGE)

    print(f"  校徽下载完成: {success_count} / {len(all_universities)}")

    # 保存数据
    output_file = os.path.join(UNIVERSITY_DIR, "universities.json")
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(all_universities, f, ensure_ascii=False, indent=2)
    print(f"\n✅ 院校数据已保存: {output_file}")
    print(f"   共 {len(all_universities)} 所院校")

    # 保存精简列表（用于 ID 映射）
    id_map = {uni["school_id"]: uni["name"] for uni in all_universities}
    map_file = os.path.join(UNIVERSITY_DIR, "school_id_map.json")
    with open(map_file, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)
    print(f"   ID 映射已保存: {map_file}")


if __name__ == "__main__":
    main()
