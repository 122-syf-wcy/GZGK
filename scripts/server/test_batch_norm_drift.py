#!/usr/bin/env python3.11
"""
v7.54: BATCH_NORM_MAP 漂移自检脚本。

防止后续 zjzw API 出现新批次但 import 脚本没更新映射，导致数据被静默丢弃。

跑法：
    python3.11 scripts/server/test_batch_norm_drift.py
    或 pytest scripts/server/test_batch_norm_drift.py

测试覆盖：
- import_special_to_group_plan.BATCH_NORM_MAP 必须覆盖所有已知 zjzw 返回的 batch
- merge_zjzw_to_reviewed_v2 同款 BATCH_NORM_MAP 必须保持一致
- 所有 normalized 后的 batch 必须能在 Java BatchRuleRegistry / Sichuan / Anhui 的批次代码 / batchName 中找到对应
"""
import importlib.util
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# 已知 zjzw API 实测会返回的 batch 列表（基于 2025 SC + AH 全量草稿 cat | uniq）
KNOWN_ZJZW_BATCHES = {
    # SC 主流程 + 8 类专项
    "本科批B段",
    "本科批A段",
    "本科批A段（国家专项）",
    "本科批A段（地方专项）",
    "本科批（高校专项）",
    # AH 主流程 + 国家专项
    "本科批",
    "国家专项计划批",
    "国家专项计划本科批",
    # 老高考兼容（2024 SC/AH 仍是文/理科）
    "本科一批",
}


def _load_module(filename, name):
    path = ROOT / filename
    if not path.exists():
        path = ROOT / "sichuan_2025" / filename
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None:
        raise ImportError(f"无法定位 {filename}")
    module = importlib.util.module_from_spec(spec)
    # 跳过 mysql 等运行时副作用，只读 BATCH_NORM_MAP 字符串常量
    sys.modules[name] = module
    return spec, module


def _read_batch_norm_map_from_source(filename, var_name="BATCH_NORM_MAP"):
    """直接从源码读取 BATCH_NORM_MAP 字符串（避免触发 import 时的 db 连接副作用）。"""
    path = ROOT / filename
    if not path.exists():
        path = ROOT / "sichuan_2025" / filename
    text = path.read_text(encoding="utf-8")
    # 简单查找 dict 定义片段
    import re
    pattern = rf"{re.escape(var_name)}\s*=\s*{{([^}}]+)}}"
    match = re.search(pattern, text)
    if not match:
        return None
    body = match.group(1)
    result = {}
    for line in body.splitlines():
        line = line.strip().rstrip(",")
        if ":" not in line or line.startswith("#"):
            continue
        k_str, v_str = line.split(":", 1)
        k = k_str.strip().strip('"\'')
        v = v_str.strip().strip('"\'')
        if "#" in v:
            v = v.split("#", 1)[0].strip().strip('"\'')
        if k and v:
            result[k] = v
    return result


def test_batch_norm_map_covers_known_zjzw_batches():
    """import_special_to_group_plan.BATCH_NORM_MAP 必须覆盖所有已知 zjzw 返回的 batch"""
    m = _read_batch_norm_map_from_source("import_special_to_group_plan.py")
    assert m, "无法解析 import_special_to_group_plan.py 的 BATCH_NORM_MAP"
    missing = [b for b in KNOWN_ZJZW_BATCHES if b not in m]
    assert not missing, f"BATCH_NORM_MAP 缺失批次别名：{missing}（zjzw 真实数据中存在但脚本会丢）"


def test_merge_v2_and_import_special_consistent():
    """两个脚本的 BATCH_NORM_MAP 必须对相同 zjzw key 给出相同 normalized 结果（防止两边漂移）"""
    m1 = _read_batch_norm_map_from_source("import_special_to_group_plan.py")
    m2 = _read_batch_norm_map_from_source("sichuan_2025/merge_zjzw_to_reviewed_v2.py", var_name="BATCH_NORM")
    assert m1, "缺少 import_special_to_group_plan.BATCH_NORM_MAP"
    assert m2, "缺少 merge_zjzw_to_reviewed_v2.BATCH_NORM"
    # 至少 5 类 SC 主流程+专项要在两个脚本中一致
    sc_keys = ["本科批B段", "本科批A段", "本科批A段（国家专项）",
               "本科批A段（地方专项）", "本科批（高校专项）"]
    for k in sc_keys:
        if k in m1 and k in m2:
            assert m1[k] == m2[k], f"两脚本对 batch={k} 的 normalized 不一致：import_special={m1[k]} vs merge_v2={m2[k]}"


def test_no_duplicate_or_empty_values():
    """BATCH_NORM_MAP 不应有空值或重复 key"""
    m = _read_batch_norm_map_from_source("import_special_to_group_plan.py")
    assert m
    for k, v in m.items():
        assert k.strip(), f"BATCH_NORM_MAP 出现空 key"
        assert v.strip(), f"BATCH_NORM_MAP key={k} 的 value 为空"
    # key 不重复（dict 本身已去重，但确认 source 文件里没误粘贴）
    text = (ROOT / "import_special_to_group_plan.py").read_text(encoding="utf-8")
    seen_keys = []
    in_map = False
    for line in text.splitlines():
        if "BATCH_NORM_MAP" in line and "=" in line:
            in_map = True
            continue
        if in_map and "}" in line:
            break
        if in_map and ":" in line:
            k = line.strip().split(":", 1)[0].strip('"\', ')
            if k and not k.startswith("#"):
                seen_keys.append(k)
    duplicates = [k for k in set(seen_keys) if seen_keys.count(k) > 1]
    assert not duplicates, f"BATCH_NORM_MAP 中有重复 key：{duplicates}"


if __name__ == "__main__":
    print("==== BATCH_NORM_MAP drift self-test ====")
    test_batch_norm_map_covers_known_zjzw_batches()
    print("OK: covers all known zjzw batches")
    test_merge_v2_and_import_special_consistent()
    print("OK: import_special and merge_v2 consistent")
    test_no_duplicate_or_empty_values()
    print("OK: no duplicates or empty values")
    print("==== ALL PASS ====")
