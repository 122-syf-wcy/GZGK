"""Tests for `build_training_csv_ah._batch_code_ah` and `normalize` regime tagging."""
from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd
import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from build_training_csv_ah import _batch_code_ah, normalize  # noqa: E402  type: ignore


@pytest.mark.parametrize(
    ("batch", "expected"),
    [
        # 主流程批次
        ("普通本科批", "AH_BENKE"),
        ("本科批", "AH_BENKE"),
        ("普通本科批次", "AH_BENKE"),
        ("高职专科批", "AH_ZHUANKE"),
        ("专科批", "AH_ZHUANKE"),
        ("普通高职专科批", "AH_ZHUANKE"),
        # 提前批
        ("本科提前批", "AH_TIQIAN_BENKE_PARALLEL"),
        ("普通本科提前批", "AH_TIQIAN_BENKE_PARALLEL"),
        ("本科提前批军事", "AH_TIQIAN_BENKE_PARALLEL"),
        ("本科提前批公安", "AH_TIQIAN_BENKE_PARALLEL"),
        ("本科提前批司法", "AH_TIQIAN_BENKE_SEQUENTIAL"),
        ("本科提前批应急消防", "AH_TIQIAN_BENKE_SEQUENTIAL"),
        ("本科提前批其他类", "AH_TIQIAN_BENKE_SEQUENTIAL"),
        ("本科提前批综合评价", "AH_TIQIAN_BENKE_SEQUENTIAL"),
        ("高职提前批", "AH_TIQIAN_ZHUANKE_PARALLEL"),
        ("专科提前批", "AH_TIQIAN_ZHUANKE_PARALLEL"),
        ("高职专科提前批", "AH_TIQIAN_ZHUANKE_PARALLEL"),
        ("高职提前批司法", "AH_TIQIAN_ZHUANKE_SEQUENTIAL"),
        ("高职提前批其他类", "AH_TIQIAN_ZHUANKE_SEQUENTIAL"),
        # 专项
        ("国家专项计划", "AH_NATIONAL_SPECIAL"),
        ("国家专项计划本科批", "AH_NATIONAL_SPECIAL"),
        ("地方专项计划", "AH_LOCAL_SPECIAL"),
        ("高校专项计划", "AH_UNIVERSITY_SPECIAL"),
        # 艺术
        ("艺术类校考本科批", "AH_ART_XIAOKAO_BENKE"),
        ("艺术校考本科", "AH_ART_XIAOKAO_BENKE"),
        ("艺术类统考本科批", "AH_ART_TONGKAO_BENKE"),
        ("艺术类本科批A段", "AH_ART_TONGKAO_BENKE"),
        ("艺术类本科批B段", "AH_ART_TONGKAO_BENKE"),
        ("艺术类统考高职专科批", "AH_ART_TONGKAO_ZHUANKE"),
        ("艺术类专科批", "AH_ART_TONGKAO_ZHUANKE"),
        # 体育
        ("体育类本科批", "AH_SPORTS_BENKE"),
        ("体育类高职专科批", "AH_SPORTS_ZHUANKE"),
        # 兜底
        ("", "AH_BENKE"),
        (None, "AH_BENKE"),
        ("某未知批次", "AH_BENKE"),
    ],
)
def test_batch_code_ah_mapping(batch, expected):
    assert _batch_code_ah(batch) == expected


def test_normalize_marks_anhui_new_gaokao_from_2024():
    raw = pd.DataFrame(
        {
            "subject_type": ["物理类", "历史类", "理科", "文科"],
            "plan_count": [10, 5, None, 0],
            "min_score": [600, 580, 0, 500],
            "min_rank": [1000, 2000, 3000, 4000],
            "year": [2025, 2025, 2023, 2022],
            "batch": ["普通本科批", "国家专项计划", "本科一批", "本科二批"],
            "is_985": [1, 0, 0, 0],
            "is_211": [1, 0, 0, 0],
            "is_double_first_class": [1, 0, 0, 0],
            "is_public": [1, 1, 1, 1],
            "school_id": ["31", "32", "33", "34"],
            "group_code": ["W001", "W002", "", "L001"],
            "group_name": ["G1", "G2", "G3", "G4"],
            "major_name": ["M1", "M2", "M3", "M4"],
            "school_level": ["本科"] * 4,
            "school_city": ["北京"] * 4,
        }
    )
    out = normalize(raw)
    assert list(out["subject_type"]) == ["物理类", "历史类", "物理类", "历史类"]
    assert list(out["batch_code"]) == [
        "AH_BENKE",
        "AH_NATIONAL_SPECIAL",
        "AH_BENKE",
        "AH_BENKE",
    ]
    regimes = dict(zip(out["year"], out["subject_regime"]))
    assert regimes[2025] == "new"
    assert regimes[2023] == "old"
    assert regimes[2022] == "old"
