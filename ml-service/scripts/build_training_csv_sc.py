"""四川 ML 训练数据 ETL：从 data_admission_group_line × data_admission_group_plan × sys_university 构建 baseline 训练 CSV。

用法：
    GZLY_DB_PASSWORD=xxx python scripts/build_training_csv_sc.py \\
        --output data/training_rank_sc.csv \\
        --train-years 2025 \\
        --min-rows 10 \\
        --quality-report reports/sc_training_quality.md

与贵州版（build_training_csv.py）的差异：
1. 数据源：data_admission_group_line（院校专业组维度 SC/HB/AH 共用） + data_admission_group_plan（专业级补充）
2. 训练单元：(school_id, group_code, subject_type) 而非 (school_id, major_name, subject_type)
3. major_name：从 data_admission_group_plan 按 (school_id, group_code, year) JOIN 取第一条专业名做 proxy
4. min-rows 默认 10（SC 2025 数据仅 27 行，建立 baseline 前不强行要求 50 行）
5. lag 特征大量为空（SC 仅 2025 一年）；输出 CSV 仍按统一 schema 兼容 train_rank_model

边界：
- 这个 ETL 是"建训练 baseline"用，**不允许激活 SC 专属 ML 模型**直到 SC 至少 ≥2 年 group_line 数据齐备
- 当 train-years 包含 2026 时，要求该年份在 group_line 中真实存在标签行，禁止用其他年份冒充
- 输出 quality report 详细列出缺口，提示运维下一步动作
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
            raise ValueError("--train-years 仅接受逗号分隔年份，例如 2025,2026")
        years.append(int(item))
    return sorted(set(years))


def load_raw(conn, province_code: str, train_years: Sequence[int] | None = None) -> pd.DataFrame:
    """从 data_admission_group_line × sys_university 取 SC 院校专业组调档线。

    LEFT JOIN data_admission_group_plan 按 (school_id, group_code, year, subject_type) 取该组的第一条
    专业名作为 major_name proxy（让训练 schema 兼容贵州 build_training_csv 的输出）。
    """
    params: list[object] = [province_code]
    where = ["s.province_code = %s", "s.min_rank IS NOT NULL", "s.min_rank > 0"]
    if train_years:
        placeholders = ",".join(["%s"] * len(train_years))
        where.append(f"s.year IN ({placeholders})")
        params.extend(train_years)
    sql = (
        "SELECT s.school_id, s.university_name, s.group_code, s.group_name, "
        "       s.year, s.subject_type, s.min_score, s.min_rank, s.plan_count, s.batch, "
        "       s.resubject_requirement, s.first_subject_requirement, s.source_level, "
        "       (SELECT p.major_name FROM data_admission_group_plan p "
        "        WHERE p.province_code = s.province_code AND p.school_id = s.school_id "
        "          AND p.group_code = s.group_code AND p.year = s.year "
        "          AND p.subject_type = s.subject_type "
        "        ORDER BY p.major_code ASC LIMIT 1) AS major_name, "
        "       u.province AS school_province, u.city AS school_city, u.level AS school_level, "
        "       u.type_name AS school_type, u.f985 AS is_985, u.f211 AS is_211, "
        "       u.dual_class AS is_double_first_class, "
        "       CASE WHEN u.nature_name LIKE '%%公办%%' THEN 1 ELSE 0 END AS is_public "
        "FROM data_admission_group_line s LEFT JOIN sys_university u ON u.school_id = s.school_id "
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


def load_official_context_inventory(conn, province_code: str, train_years: Sequence[int]) -> dict[str, object]:
    """盘点 SC group_line / group_plan / data_score_rank 在指定年份的现状，方便质量报告生成。"""
    years = list(train_years) if train_years else [2024, 2025, 2026]
    placeholders = ",".join(["%s"] * len(years))
    inventory: dict[str, object] = {"provinceCode": province_code, "years": years}
    queries = {
        "group_line_rows": (
            "data_admission_group_line",
            f"SELECT COUNT(*) FROM data_admission_group_line WHERE province_code = %s AND year IN ({placeholders})",
        ),
        "group_line_distinct_groups": (
            "data_admission_group_line",
            f"SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type)) FROM data_admission_group_line "
            f"WHERE province_code = %s AND year IN ({placeholders})",
        ),
        "group_plan_rows": (
            "data_admission_group_plan",
            f"SELECT COUNT(*) FROM data_admission_group_plan WHERE province_code = %s AND year IN ({placeholders})",
        ),
        "score_rank_rows": (
            "data_score_rank",
            f"SELECT COUNT(*) FROM data_score_rank WHERE province_code = %s AND year IN ({placeholders})",
        ),
    }
    with conn.cursor() as cursor:
        for key, (table_name, sql) in queries.items():
            if not table_exists(conn, table_name):
                inventory[key] = "table_missing"
                continue
            cursor.execute(sql, [province_code, *years])
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
    df["batch_code"] = df["batch"].fillna("SC_BENKE_B").map(_batch_code_sc)
    df["candidate_type"] = "普通类"
    df["major_category"] = df["major_name"].fillna(df["group_name"].fillna("")).map(lambda v: str(v).split("（")[0][:8])
    df["school_code"] = df["school_id"].astype(str)
    # 训练单元：四川按 group_code 训练，不能按 major_name（一个组多个专业）
    df["major_code"] = df["group_code"].fillna("").map(lambda v: str(v)[:32])
    df["tuition"] = 0
    df["major_hot_score"] = 0.0
    df["school_ranking_score"] = df["is_985"] * 50 + df["is_211"] * 30 + df["is_double_first_class"] * 15
    df["employment_score"] = df["is_public"] * 60 + df["is_985"] * 25 + df["is_211"] * 10
    # SC 2025 起新高考"院校专业组"，2024 是老高考；标记 subject_regime
    df["subject_regime"] = df["year"].astype(int).map(lambda y: "new" if y >= 2025 else "old")
    return df


def _batch_code_sc(batch: str) -> str:
    if not batch:
        return "SC_BENKE_B"
    if "B段" in batch or "本科批" in batch:
        return "SC_BENKE_B"
    if "提前" in batch and "B" in batch:
        return "SC_TIQIAN_B"
    if "提前" in batch and "A" in batch:
        return "SC_TIQIAN_A"
    if "国家专项" in batch:
        return "SC_BENKE_A_NATIONAL"
    if "地方专项" in batch:
        return "SC_BENKE_A_LOCAL"
    if "区域" in batch:
        return "SC_BENKE_REGION_BALANCE"
    if "民族预科" in batch or "少数民族" in batch:
        return "SC_BENKE_MINORITY_PRE"
    if "专科" in batch or "高职" in batch:
        return "SC_ZHUANKE_B"
    return "SC_BENKE_B"


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


def filter_trainable(df: pd.DataFrame, allow_no_lag: bool) -> pd.DataFrame:
    """SC 当前仅 2025 一年，所有行 min_rank_lag_1 都是 NaN。

    - allow_no_lag=True：不过滤 lag NaN 行（baseline 阶段保留所有行，作为"统计样本"）
    - allow_no_lag=False：过滤掉 lag NaN 行（与 GZ 一致，等 SC ≥2 年数据后用）
    """
    df = df.copy()
    if allow_no_lag:
        return df
    return df[df["min_rank_lag_1"].notna()]


def select_export(df: pd.DataFrame) -> pd.DataFrame:
    cols = [
        "year", "tuition", "current_plan_count", "last_year_plan_count", "plan_change_rate",
        "min_rank_lag_1", "min_rank_lag_2", "min_rank_lag_3", "avg_rank_lag_3", "median_rank_lag_3",
        "rank_volatility_3y", "rank_trend_3y", "major_hot_score", "school_ranking_score", "employment_score",
        "batch_code", "candidate_type", "subject_type", "subject_regime", "school_code", "major_code", "school_level",
        "is_985", "is_211", "is_double_first_class", "is_public", "school_city", "major_category",
        "has_supplement_lag_1", "first_round_full_lag_1",
        "min_rank",
    ]
    cols = [c for c in cols if c in df.columns]
    return df[cols].copy()


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
    "min_rank",
)


def validate_export(df: pd.DataFrame, min_rows: int, required_years: Sequence[int] | None = None,
                    allow_no_lag: bool = False) -> None:
    missing = [col for col in REQUIRED_OUTPUT_COLUMNS if col not in df.columns]
    if missing:
        raise RuntimeError(f"导出 CSV 缺关键列: {missing}; 请检查 ETL 流程")
    if len(df) < min_rows:
        hint = "等待 SC 2025 数据补齐到 ≥45 行" if allow_no_lag else "等待 SC ≥2 年数据齐备后做 lag 训练"
        raise RuntimeError(
            f"训练样本不足 ({len(df)} < {min_rows})；{hint}。"
            "本 ETL 不会写入伪派生 lag 数据。"
        )
    if df["min_rank"].le(0).any():
        raise RuntimeError("min_rank 包含非正值，标签错误，请检查数据清洗")
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
    province_code: str,
    train_years: Sequence[int],
    raw: pd.DataFrame,
    cleaned: pd.DataFrame,
    enriched: pd.DataFrame,
    trainable: pd.DataFrame,
    exported: pd.DataFrame,
    official_context: dict[str, object],
    min_rows: int,
    allow_no_lag: bool,
    validation_error: str | None,
) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    key_columns = [
        "year", "school_code", "major_code", "subject_type", "batch_code",
        "min_rank", "current_plan_count", "min_rank_lag_1",
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
        "provinceCode": province_code,
        "trainYears": list(train_years),
        "minRows": min_rows,
        "allowNoLag": allow_no_lag,
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
        "boundary": "Sichuan baseline ETL only; no fake lag data; no model activation; no readiness update; safe to schedule for incremental years.",
    }
    lines = [
        f"# {province_code} Training CSV Quality Report (Sichuan Baseline)",
        "",
        f"- generatedAt: `{payload['generatedAt']}`",
        f"- provinceCode: `{province_code}`",
        f"- trainYears: `{','.join(str(y) for y in train_years) if train_years else 'all available labeled years'}`",
        f"- minRows: `{min_rows}` (allowNoLag={allow_no_lag})",
        "- boundary: quality report only; no fake data; no model activation.",
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
    lines.extend(["", "## Official Data Context Inventory", "", "| item | value |", "|---|---|"])
    for key, value in official_context.items():
        lines.append(f"| {key} | `{value}` |")
    lines.extend(["", "## Validation", ""])
    lines.append(f"- status: `{'failed' if validation_error else 'passed'}`")
    if validation_error:
        lines.append(f"- error: `{validation_error}`")
    lines.extend([
        "",
        "## Next Actions",
        "",
        "1. SC 2025 院校专业组线 / 计划数据补齐到 ≥45 组（运行 /root/gzly_scraper/sichuan_2025 pipeline）",
        "2. SC 2024 老高考数据**不要**导入 group_line / group_plan（口径不同）",
        "3. 2026 官方数据出（约 6 月下旬）后，按 train-years=2025,2026 重新运行本 ETL + train_models_on_server.py",
        "4. SC 至少需要 2 年带标签数据（2025+2026）才能产生 lag 特征，开启正式 rank 训练",
        "",
        "## Machine Readable Snapshot",
        "",
        json.dumps(payload, ensure_ascii=False, indent=2),
    ])
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="data/training_rank_sc.csv", help="输出 CSV 路径")
    parser.add_argument("--province-code", default="SC", help="省份代码，默认 SC；HB / AH 可复用")
    parser.add_argument("--limit", type=int, default=0, help="可选：限制行数（调试用）")
    parser.add_argument("--train-years", default="",
                        help="逗号分隔训练年份，例如 2025,2026；默认使用全部带标签年份")
    parser.add_argument("--require-train-years", action="store_true",
                        help="开启后要求 --train-years 中每个年份都出现在导出样本中")
    parser.add_argument("--quality-report", default="",
                        help="可选：输出训练 CSV 质量报告 Markdown 路径")
    parser.add_argument("--min-rows", type=int, default=10,
                        help="导出后最少行数；少于该数直接报错（SC baseline 默认 10）")
    parser.add_argument("--allow-no-lag", action="store_true",
                        help="允许 lag 特征为空的行（SC 单年数据 baseline 必须开启）")
    parser.add_argument("--strict", action="store_true",
                        help="开启严格模式：行数不足、关键列缺失时直接非零退出而非告警")
    args = parser.parse_args(argv)
    try:
        train_years = parse_train_years(args.train_years)
    except ValueError as exc:
        print(f"[sc-etl][ERROR] {exc}", file=sys.stderr)
        return 2

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"[sc-etl] connecting MySQL (province={args.province_code}) ...")
    with open_connection() as conn:
        raw = load_raw(conn, args.province_code, train_years)
        official_context = load_official_context_inventory(conn, args.province_code, train_years)
    print(f"[sc-etl] raw rows: {len(raw)}")
    if train_years:
        print(f"[sc-etl] requested train years: {train_years}")

    cleaned = normalize(raw)
    print(f"[sc-etl] after normalize: {len(cleaned)}")

    enriched = add_lag_features(cleaned)
    print(f"[sc-etl] after lag: {len(enriched)}")

    trainable = filter_trainable(enriched, args.allow_no_lag)
    print(f"[sc-etl] trainable rows (allowNoLag={args.allow_no_lag}): {len(trainable)}")

    exported = select_export(trainable)
    if args.limit > 0:
        exported = exported.head(args.limit)

    try:
        required_years = train_years if args.require_train_years else []
        validate_export(exported, args.min_rows, required_years, args.allow_no_lag)
        validation_error = None
    except RuntimeError as exc:
        validation_error = str(exc)
        if args.strict:
            print(f"[sc-etl][ERROR] {exc}", file=sys.stderr)
            if args.quality_report:
                write_quality_report(
                    Path(args.quality_report),
                    province_code=args.province_code,
                    train_years=train_years,
                    raw=raw,
                    cleaned=cleaned,
                    enriched=enriched,
                    trainable=trainable,
                    exported=exported,
                    official_context=official_context,
                    min_rows=args.min_rows,
                    allow_no_lag=args.allow_no_lag,
                    validation_error=validation_error,
                )
            return 2
        print(f"[sc-etl][WARN] {exc}", file=sys.stderr)

    exported.to_csv(out_path, index=False, encoding="utf-8-sig")
    print(f"[sc-etl] saved -> {out_path} (rows={len(exported)}, cols={len(exported.columns)})")
    if args.quality_report:
        write_quality_report(
            Path(args.quality_report),
            province_code=args.province_code,
            train_years=train_years,
            raw=raw,
            cleaned=cleaned,
            enriched=enriched,
            trainable=trainable,
            exported=exported,
            official_context=official_context,
            min_rows=args.min_rows,
            allow_no_lag=args.allow_no_lag,
            validation_error=validation_error,
        )
        print(f"[sc-etl] quality report -> {args.quality_report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
