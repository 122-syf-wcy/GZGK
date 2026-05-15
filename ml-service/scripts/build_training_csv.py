"""GZLY ML 训练数据 ETL：从 data_score_line_gz × sys_university 构建 lag 特征。

用法：
    GZLY_DB_PASSWORD=xxx python scripts/build_training_csv.py \
        --output data/training_rank.csv

环境变量：
    GZLY_DB_HOST     默认 127.0.0.1
    GZLY_DB_PORT     默认 3306
    GZLY_DB_NAME     默认 gzly
    GZLY_DB_USER     默认 root
    GZLY_DB_PASSWORD 必填

注意：
- 当前训练表为 data_score_line_gz（21789 行 2020-2025），按 (school_id, subject_type) 排序按年份算 lag。
- 输出 CSV 字段对齐 rank_prediction_model.NUMERIC_FEATURES + CATEGORICAL_FEATURES。
- 标签 min_rank 用本年实际录取最低位次；早年（2021/2022/2023）按文理科 → 物理类/历史类 兼容映射。
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
import sys
from pathlib import Path
from typing import Iterable, Sequence

import pandas as pd
import pymysql


SUBJECT_TYPE_NORMALIZE = {
    "物理类": "物理类",
    "历史类": "历史类",
    "理科": "物理类",
    "文科": "历史类",
    "综合": "综合",
}


def parse_train_years(value: str | None) -> list[int]:
    if not value:
        return []
    years: list[int] = []
    for part in value.split(","):
        item = part.strip()
        if not item:
            continue
        if not item.isdigit() or len(item) != 4:
            raise ValueError("--train-years 仅接受逗号分隔年份，例如 2024,2025,2026")
        years.append(int(item))
    return sorted(set(years))


def load_raw(conn, train_years: Sequence[int] | None = None) -> pd.DataFrame:
    params: list[int] = []
    where = ["s.min_rank IS NOT NULL", "s.min_rank > 0"]
    if train_years:
        placeholders = ",".join(["%s"] * len(train_years))
        where.append(f"s.year IN ({placeholders})")
        params.extend(train_years)
    sql = (
        "SELECT s.school_id, s.university_name, s.major_name, s.year, s.subject_type, "
        "       s.min_score, s.min_rank, s.plan_count, s.batch, s.resubject_requirement, "
        "       u.province AS school_province, u.city AS school_city, u.level AS school_level, "
        "       u.type_name AS school_type, u.f985 AS is_985, u.f211 AS is_211, "
        "       u.dual_class AS is_double_first_class, "
        "       CASE WHEN u.nature_name LIKE '%公办%' THEN 1 ELSE 0 END AS is_public "
        "FROM data_score_line_gz s LEFT JOIN sys_university u ON u.school_id = s.school_id "
        f"WHERE {' AND '.join(where)}"
    )
    return pd.read_sql(sql, conn, params=params or None)


def table_exists(conn, table_name: str) -> bool:
    with conn.cursor() as cursor:
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name = %s",
            (table_name,),
        )
        row = cursor.fetchone()
    return bool(row and row[0])


def load_official_context_inventory(conn, train_years: Sequence[int]) -> dict[str, object]:
    """只读盘点 2026 官方数据上下文，不把未带标签的 2026 计划伪造成训练标签。"""
    years = list(train_years)
    if not years:
        years = [2024, 2025, 2026]
    placeholders = ",".join(["%s"] * len(years))
    inventory: dict[str, object] = {"years": years}
    table_queries = {
        "admission_plan_rows": (
            "admission_plan",
            "SELECT COUNT(*) FROM admission_plan WHERE year IN ({}) AND province IN ('GZ', '贵州', '')",
        ),
        "admission_plan_restriction_rows": (
            "admission_plan",
            "SELECT COUNT(*) FROM admission_plan WHERE year IN ({}) AND province IN ('GZ', '贵州', '') "
            "AND (COALESCE(remarks, '') <> '' OR COALESCE(special_limit, '') <> '')",
        ),
        "score_rank_rows": (
            "data_score_rank_gz",
            "SELECT COUNT(*) FROM data_score_rank_gz WHERE year IN ({})",
        ),
        "major_requirement_rows": (
            "data_major_requirement_gz",
            "SELECT COUNT(*) FROM data_major_requirement_gz WHERE year IN ({})",
        ),
    }
    with conn.cursor() as cursor:
        for key, (table_name, query_template) in table_queries.items():
            if not table_exists(conn, table_name):
                inventory[key] = "table_missing"
                continue
            cursor.execute(query_template.format(placeholders), years)
            row = cursor.fetchone()
            inventory[key] = int(row[0]) if row else 0
    return inventory


def normalize(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["subject_type"] = df["subject_type"].fillna("").map(lambda x: SUBJECT_TYPE_NORMALIZE.get(x, x))
    df = df[df["subject_type"].isin(["物理类", "历史类", "综合"])]
    df["plan_count"] = df["plan_count"].fillna(0).astype(int)
    df["min_score"] = df["min_score"].fillna(0).astype(int)
    df["min_rank"] = df["min_rank"].astype(int)
    for col in ("is_985", "is_211", "is_double_first_class", "is_public"):
        df[col] = df[col].fillna(0).astype(int)
    df["batch_code"] = df["batch"].fillna("NORMAL_UNDERGRADUATE").map(_batch_code)
    df["candidate_type"] = "普通类"
    df["major_category"] = df["major_name"].fillna("").map(lambda v: v.split("（")[0][:8])
    df["school_code"] = df["school_id"].astype(str)
    df["major_code"] = df["major_name"].fillna("").map(lambda v: v[:32])
    df["tuition"] = 0
    df["major_hot_score"] = 0.0
    df["school_ranking_score"] = df["is_985"] * 50 + df["is_211"] * 30 + df["is_double_first_class"] * 15
    df["employment_score"] = df["is_public"] * 60 + df["is_985"] * 25 + df["is_211"] * 10
    return df


def _batch_code(batch: str) -> str:
    if not batch:
        return "NORMAL_UNDERGRADUATE"
    if "提前" in batch:
        if "C" in batch:
            return "EARLY_C"
        return "EARLY_A_B"
    if "专科" in batch or "高职" in batch:
        return "NORMAL_SPECIALTY"
    return "NORMAL_UNDERGRADUATE"


def add_lag_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.sort_values(["school_code", "subject_type", "major_code", "year"]).reset_index(drop=True)
    g = df.groupby(["school_code", "subject_type", "major_code"], sort=False)
    df["min_rank_lag_1"] = g["min_rank"].shift(1)
    df["min_rank_lag_2"] = g["min_rank"].shift(2)
    df["min_rank_lag_3"] = g["min_rank"].shift(3)
    df["last_year_plan_count"] = g["plan_count"].shift(1)
    df["current_plan_count"] = df["plan_count"]
    df["plan_change_rate"] = (df["current_plan_count"] - df["last_year_plan_count"]) / df["last_year_plan_count"].replace({0: pd.NA})
    df["plan_change_rate"] = df["plan_change_rate"].fillna(0).astype(float)

    lag_cols = ["min_rank_lag_1", "min_rank_lag_2", "min_rank_lag_3"]
    df["avg_rank_lag_3"] = df[lag_cols].mean(axis=1)
    df["median_rank_lag_3"] = df[lag_cols].median(axis=1)
    rolling_std = df[lag_cols].std(axis=1, ddof=0)
    df["rank_volatility_3y"] = (rolling_std / df["avg_rank_lag_3"].replace({0: pd.NA})).fillna(0).astype(float).clip(0, 1)
    df["rank_trend_3y"] = (df["min_rank_lag_1"] - df["min_rank_lag_3"]).fillna(0).astype(float)

    df["has_supplement_lag_1"] = ((df["plan_change_rate"] < -0.10) | df["min_rank_lag_1"].isna()).astype(int)
    df["first_round_full_lag_1"] = (df["plan_change_rate"] >= 0).astype(int)

    return df


def filter_trainable(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df = df[df["min_rank_lag_1"].notna()]
    return df


def select_export(df: pd.DataFrame) -> pd.DataFrame:
    cols = [
        "year", "tuition", "current_plan_count", "last_year_plan_count", "plan_change_rate",
        "min_rank_lag_1", "min_rank_lag_2", "min_rank_lag_3", "avg_rank_lag_3", "median_rank_lag_3",
        "rank_volatility_3y", "rank_trend_3y", "major_hot_score", "school_ranking_score", "employment_score",
        "batch_code", "candidate_type", "subject_type", "school_code", "major_code", "school_level",
        "is_985", "is_211", "is_double_first_class", "is_public", "school_city", "major_category",
        "has_supplement_lag_1", "first_round_full_lag_1",
        "min_rank",
    ]
    cols = [c for c in cols if c in df.columns]
    out = df[cols].copy()
    out["candidate_rank"] = (out["min_rank"] - (out["min_rank"] * 0.05).round()).astype(int)
    return out


def open_connection() -> "pymysql.connections.Connection":
    password = os.environ.get("GZLY_DB_PASSWORD")
    if not password:
        sys.exit("GZLY_DB_PASSWORD 必须设置")
    return pymysql.connect(
        host=os.environ.get("GZLY_DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("GZLY_DB_PORT", "3306")),
        user=os.environ.get("GZLY_DB_USER", "root"),
        password=password,
        database=os.environ.get("GZLY_DB_NAME", "gzly"),
        charset="utf8mb4",
        connect_timeout=8,
    )


REQUIRED_OUTPUT_COLUMNS = (
    "year", "current_plan_count", "last_year_plan_count", "plan_change_rate",
    "min_rank_lag_1", "rank_volatility_3y", "rank_trend_3y",
    "batch_code", "candidate_type", "subject_type", "school_code",
    "min_rank", "candidate_rank",
)


def validate_export(df: pd.DataFrame, min_rows: int, required_years: Sequence[int] | None = None) -> None:
    """硬校验：缺关键列或行数过少时直接抛 RuntimeError，避免训练脚本拿到劣化数据。"""
    missing = [col for col in REQUIRED_OUTPUT_COLUMNS if col not in df.columns]
    if missing:
        raise RuntimeError(f"导出 CSV 缺关键列: {missing}; 请检查 ETL 流程")
    if len(df) < min_rows:
        raise RuntimeError(
            f"训练样本不足 ({len(df)} < {min_rows})，模型质量无法保证；"
            "请扩充 data_score_line_gz / data_major_score_gz 或检查 lag 特征生成"
        )
    if df["min_rank"].le(0).any():
        raise RuntimeError("min_rank 包含非正值，标签错误，请检查数据清洗")
    if df["candidate_rank"].le(0).any():
        raise RuntimeError("candidate_rank 包含非正值，请检查 select_export 中的派生逻辑")
    if required_years:
        present_years = {int(y) for y in df["year"].dropna().astype(int).unique().tolist()}
        missing_years = [year for year in required_years if year not in present_years]
        if missing_years:
            raise RuntimeError(
                f"训练样本缺少年份 {missing_years}；不得用其他年份数据冒充缺失年份"
            )


def write_quality_report(
    report_path: Path,
    *,
    train_years: Sequence[int],
    raw: pd.DataFrame,
    cleaned: pd.DataFrame,
    enriched: pd.DataFrame,
    trainable: pd.DataFrame,
    exported: pd.DataFrame,
    official_context: dict[str, object],
    min_rows: int,
    validation_error: str | None,
) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    key_columns = [
        "year",
        "school_code",
        "major_code",
        "subject_type",
        "batch_code",
        "min_rank",
        "candidate_rank",
        "current_plan_count",
        "min_rank_lag_1",
    ]
    completeness = {
        col: round(float(exported[col].notna().mean()), 4)
        for col in key_columns
        if col in exported.columns and len(exported) > 0
    }
    year_counts = (
        exported["year"].value_counts().sort_index().astype(int).to_dict()
        if "year" in exported.columns and len(exported) > 0
        else {}
    )
    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "trainYears": list(train_years),
        "minRows": min_rows,
        "rowCounts": {
            "raw": int(len(raw)),
            "cleaned": int(len(cleaned)),
            "enriched": int(len(enriched)),
            "trainable": int(len(trainable)),
            "exported": int(len(exported)),
        },
        "yearCounts": year_counts,
        "featureCompleteness": completeness,
        "officialContext": official_context,
        "validationError": validation_error,
        "boundary": "quality report only; no fake 2026 data; no model activation; no readiness update",
    }
    lines = [
        "# GZLY Training CSV Quality Report",
        "",
        f"- generatedAt: `{payload['generatedAt']}`",
        f"- trainYears: `{','.join(str(y) for y in train_years) if train_years else 'all available labeled years'}`",
        f"- minRows: `{min_rows}`",
        "- boundary: quality report only; no fake 2026 data; no model activation; no readiness update.",
        "",
        "## Row Counts",
        "",
        "| stage | rows |",
        "|---|---:|",
    ]
    for stage, count in payload["rowCounts"].items():
        lines.append(f"| {stage} | {count} |")
    lines.extend(["", "## Year Coverage", "", "| year | rows |", "|---|---:|"])
    for year, count in year_counts.items():
        lines.append(f"| {year} | {count} |")
    if not year_counts:
        lines.append("| none | 0 |")
    lines.extend(["", "## Feature Completeness", "", "| column | completeness |", "|---|---:|"])
    for col, ratio in completeness.items():
        lines.append(f"| {col} | {ratio:.2%} |")
    lines.extend(["", "## Official 2026 Context Inventory", "", "| item | value |", "|---|---|"])
    for key, value in official_context.items():
        lines.append(f"| {key} | `{value}` |")
    lines.extend(["", "## Validation", ""])
    lines.append(f"- status: `{'failed' if validation_error else 'passed'}`")
    if validation_error:
        lines.append(f"- error: `{validation_error}`")
    lines.extend(["", "## Machine Readable Snapshot", "", json.dumps(payload, ensure_ascii=False, indent=2)])
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="data/training_rank.csv", help="输出 CSV 路径")
    parser.add_argument("--limit", type=int, default=0, help="可选：限制行数（调试用）")
    parser.add_argument("--train-years", default="",
                        help="逗号分隔训练年份，例如 2024,2025,2026；默认使用全部带标签年份")
    parser.add_argument("--require-train-years", action="store_true",
                        help="开启后要求 --train-years 中每个年份都出现在导出样本中")
    parser.add_argument("--quality-report", default="",
                        help="可选：输出训练 CSV 质量报告 Markdown 路径")
    parser.add_argument("--min-rows", type=int, default=50,
                        help="导出后最少行数；少于该数直接报错（默认 50）")
    parser.add_argument("--strict", action="store_true",
                        help="开启严格模式：行数不足、关键列缺失时直接非零退出而非告警")
    args = parser.parse_args(argv)
    try:
        train_years = parse_train_years(args.train_years)
    except ValueError as exc:
        print(f"[etl][ERROR] {exc}", file=sys.stderr)
        return 2

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    print("[etl] connecting MySQL ...")
    with open_connection() as conn:
        raw = load_raw(conn, train_years)
        official_context = load_official_context_inventory(conn, train_years)
    print(f"[etl] raw rows: {len(raw)}")
    if train_years:
        print(f"[etl] requested train years: {train_years}")

    cleaned = normalize(raw)
    print(f"[etl] after normalize: {len(cleaned)}")

    enriched = add_lag_features(cleaned)
    print(f"[etl] after lag: {len(enriched)}")

    trainable = filter_trainable(enriched)
    print(f"[etl] trainable rows (有上一年位次): {len(trainable)}")

    exported = select_export(trainable)
    if args.limit > 0:
        exported = exported.head(args.limit)

    try:
        required_years = train_years if args.require_train_years else []
        validate_export(exported, args.min_rows, required_years)
        validation_error = None
    except RuntimeError as exc:
        validation_error = str(exc)
        if args.strict:
            print(f"[etl][ERROR] {exc}", file=sys.stderr)
            if args.quality_report:
                write_quality_report(
                    Path(args.quality_report),
                    train_years=train_years,
                    raw=raw,
                    cleaned=cleaned,
                    enriched=enriched,
                    trainable=trainable,
                    exported=exported,
                    official_context=official_context,
                    min_rows=args.min_rows,
                    validation_error=validation_error,
                )
            return 2
        print(f"[etl][WARN] {exc}", file=sys.stderr)

    exported.to_csv(out_path, index=False, encoding="utf-8-sig")
    print(f"[etl] saved -> {out_path} (rows={len(exported)}, cols={len(exported.columns)})")
    if args.quality_report:
        write_quality_report(
            Path(args.quality_report),
            train_years=train_years,
            raw=raw,
            cleaned=cleaned,
            enriched=enriched,
            trainable=trainable,
            exported=exported,
            official_context=official_context,
            min_rows=args.min_rows,
            validation_error=validation_error,
        )
        print(f"[etl] quality report -> {args.quality_report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
