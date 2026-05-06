"""
数据导出脚本
将爬取的 JSON 数据转换为:
  1. SQL 导入文件（MySQL）
  2. 合并的 JSON 文件
  3. Excel 文件

用法:
    python export_to_sql.py
"""

import json
import os
import glob
import re
from datetime import datetime
from config import (
    UNIVERSITY_DIR, SCORE_LINE_DIR, EXPORT_DIR,
)


def load_universities() -> list[dict]:
    """加载院校数据"""
    path = os.path.join(UNIVERSITY_DIR, "universities.json")
    if not os.path.exists(path):
        print(f"⚠️  未找到院校数据: {path}")
        return []
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_all_score_lines() -> list[dict]:
    """加载所有年份的分数线数据（兼容新旧格式）"""
    all_records = []

    # 优先加载 scrape_production.py 生成的 school_scores.json
    prod_path = os.path.join(SCORE_LINE_DIR, "school_scores.json")
    if os.path.exists(prod_path):
        with open(prod_path, "r", encoding="utf-8") as f:
            raw = json.load(f)
        # 适配字段: recruit_type → major_name
        for r in raw:
            if "major_name" not in r:
                r["major_name"] = r.get("recruit_type", "")
            if "major_id" not in r:
                r["major_id"] = ""
        all_records.extend(raw)
        print(f"  加载 school_scores.json: {len(raw)} 条 (生产爬虫)")

    # 也加载旧格式 score_lines_*.json
    pattern = os.path.join(SCORE_LINE_DIR, "score_lines_*.json")
    files = sorted(glob.glob(pattern))
    for fp in files:
        with open(fp, "r", encoding="utf-8") as f:
            records = json.load(f)
        print(f"  加载 {os.path.basename(fp)}: {len(records)} 条")
        all_records.extend(records)

    if not all_records:
        print(f"⚠️  未找到分数线数据: {SCORE_LINE_DIR}")
    return all_records


def escape_sql(val: str) -> str:
    """SQL 字符串转义"""
    if val is None:
        return "NULL"
    return "'" + str(val).replace("\\", "\\\\").replace("'", "\\'") + "'"


def export_universities_sql(universities: list[dict], output: str):
    """导出院校 SQL"""
    lines = []
    lines.append("-- =============================================")
    lines.append("-- GZLY 院校数据导入")
    lines.append(f"-- 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"-- 共 {len(universities)} 所院校")
    lines.append("-- =============================================")
    lines.append("")
    lines.append("-- 建表语句（如已存在请忽略）")
    lines.append("""CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '掌上高考院校ID',
  `name` VARCHAR(100) NOT NULL COMMENT '院校名称',
  `province` VARCHAR(20) DEFAULT '' COMMENT '省份',
  `city` VARCHAR(50) DEFAULT '' COMMENT '城市',
  `level` VARCHAR(20) DEFAULT '' COMMENT '层次(985/211/双一流/一本/二本)',
  `type_name` VARCHAR(20) DEFAULT '' COMMENT '类型(综合/理工/师范等)',
  `tags` VARCHAR(200) DEFAULT '' COMMENT '标签JSON',
  `f985` TINYINT DEFAULT 0,
  `f211` TINYINT DEFAULT 0,
  `dual_class` TINYINT DEFAULT 0,
  `nature_name` VARCHAR(20) DEFAULT '' COMMENT '公办/民办',
  `belong` VARCHAR(100) DEFAULT '' COMMENT '隶属',
  `logo_url` VARCHAR(500) DEFAULT '' COMMENT '校徽URL',
  `school_site` VARCHAR(200) DEFAULT '' COMMENT '官网',
  `phone` VARCHAR(100) DEFAULT '' COMMENT '招生电话',
  `email` VARCHAR(100) DEFAULT '' COMMENT '招生邮箱',
  `address` VARCHAR(300) DEFAULT '' COMMENT '地址',
  `content` TEXT COMMENT '简介',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_school_id` (`school_id`),
  KEY `idx_name` (`name`),
  KEY `idx_province` (`province`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校信息表';
""")
    lines.append("")
    lines.append(f"INSERT INTO `sys_university` "
                 f"(`school_id`, `name`, `province`, `city`, `level`, `type_name`, "
                 f"`tags`, `f985`, `f211`, `dual_class`, `nature_name`, `belong`, "
                 f"`logo_url`, `school_site`, `phone`, `email`, `address`, `content`) VALUES")

    # 掌上高考编码映射
    NATURE_MAP = {"36000": "公办", "36001": "民办", "36002": "中外合作办学", "36003": "内地与港澳台合作办学", "36005": "其他"}
    TYPE_MAP = {"5000": "综合", "5001": "理工", "5002": "农林", "5003": "医药", "5004": "师范",
                "5005": "语言", "5006": "财经", "5007": "政法", "5008": "体育", "5009": "艺术",
                "5010": "民族", "5011": "军事", "5012": "其他"}

    values = []
    for uni in universities:
        is985 = 1 if str(uni.get('f985', '')) == '1' else 0
        is211 = 1 if str(uni.get('f211', '')) == '1' else 0
        is_dual = 1 if str(uni.get('dual_class', '')) == '38000' else 0
        nature = NATURE_MAP.get(str(uni.get('school_nature', '')), uni.get('nature_name', ''))
        type_name = TYPE_MAP.get(str(uni.get('type', '')), uni.get('type_name', ''))
        # 自动生成 tags
        tags = []
        if is985: tags.append('985')
        if is211: tags.append('211')
        if is_dual: tags.append('双一流')
        if nature: tags.append(nature)
        tags_json = json.dumps(tags, ensure_ascii=False)
        val = (f"({escape_sql(uni.get('school_id', ''))},"
               f"{escape_sql(uni.get('name', ''))},"
               f"{escape_sql(uni.get('province_name', ''))},"
               f"{escape_sql(uni.get('city_name', ''))},"
               f"{escape_sql(uni.get('level', ''))},"
               f"{escape_sql(type_name)},"
               f"{escape_sql(tags_json)},"
               f"{is985},"
               f"{is211},"
               f"{is_dual},"
               f"{escape_sql(nature)},"
               f"{escape_sql(uni.get('belong', ''))},"
               f"{escape_sql(uni.get('logo_url', ''))},"
               f"{escape_sql(uni.get('school_site', ''))},"
               f"{escape_sql(uni.get('phone', ''))},"
               f"{escape_sql(uni.get('email', ''))},"
               f"{escape_sql(uni.get('address', ''))},"
               f"{escape_sql(uni.get('content', '')[:500] if uni.get('content') else '')})")

        values.append(val)

    # 每 50 条一个 INSERT 批次
    batch_size = 50
    final_lines = lines[:len(lines) - 1]  # 去掉最后一行 INSERT INTO...

    for i in range(0, len(values), batch_size):
        batch = values[i:i + batch_size]
        final_lines.append("")
        final_lines.append(f"INSERT INTO `sys_university` "
                          f"(`school_id`, `name`, `province`, `city`, `level`, `type_name`, "
                          f"`tags`, `f985`, `f211`, `dual_class`, `nature_name`, `belong`, "
                          f"`logo_url`, `school_site`, `phone`, `email`, `address`, `content`) VALUES")
        for j, v in enumerate(batch):
            sep = "," if j < len(batch) - 1 else ";"
            final_lines.append(f"  {v}{sep}")

    with open(output, "w", encoding="utf-8") as f:
        f.write("\n".join(final_lines) + "\n")
    print(f"✅ 院校 SQL: {output} ({len(universities)} 条)")


def export_score_lines_sql(records: list[dict], output: str):
    """导出分数线 SQL"""
    lines = []
    lines.append("-- =============================================")
    lines.append("-- GZLY 贵州投档分数线数据导入")
    lines.append(f"-- 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"-- 共 {len(records)} 条记录")
    lines.append("-- =============================================")
    lines.append("")
    lines.append("-- 建表语句（如已存在请忽略）")
    lines.append("""CREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL COMMENT '院校名称',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `major_id` VARCHAR(20) DEFAULT '' COMMENT '专业ID',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '科类(文科/理科/物理类/历史类)',
  `min_score` INT DEFAULT NULL COMMENT '最低分',
  `max_score` INT DEFAULT NULL COMMENT '最高分',
  `avg_score` INT DEFAULT NULL COMMENT '平均分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次',
  `plan_count` INT DEFAULT NULL COMMENT '招生计划数',
  `batch` VARCHAR(50) DEFAULT '本科批' COMMENT '批次',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school` (`school_id`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  KEY `idx_uni_name` (`university_name`),
  KEY `idx_min_rank` (`min_rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州历年投档分数线';
""")

    # 批量插入
    batch_size = 100
    for i in range(0, len(records), batch_size):
        batch = records[i:i + batch_size]
        lines.append("")
        lines.append("INSERT INTO `data_score_line_gz` "
                     "(`school_id`, `university_name`, `major_name`, `major_id`, "
                     "`year`, `subject_type`, `min_score`, `max_score`, `avg_score`, "
                     "`min_rank`, `plan_count`, `batch`) VALUES")
        for j, r in enumerate(batch):
            sep = "," if j < len(batch) - 1 else ";"
            val = (f"({escape_sql(r.get('school_id', ''))},"
                   f"{escape_sql(r.get('university_name', ''))},"
                   f"{escape_sql(r.get('major_name', ''))},"
                   f"{escape_sql(r.get('major_id', ''))},"
                   f"{r.get('year', 0)},"
                   f"{escape_sql(r.get('subject_type', ''))},"
                   f"{r.get('min_score') if r.get('min_score') is not None else 'NULL'},"
                   f"{r.get('max_score') if r.get('max_score') is not None else 'NULL'},"
                   f"{r.get('avg_score') if r.get('avg_score') is not None else 'NULL'},"
                   f"{r.get('min_rank') if r.get('min_rank') is not None else 'NULL'},"
                   f"{r.get('plan_count') if r.get('plan_count') is not None else 'NULL'},"
                   f"{escape_sql(r.get('batch', '本科批'))})")
            lines.append(f"  {val}{sep}")

    with open(output, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"✅ 分数线 SQL: {output} ({len(records)} 条)")


def export_merged_json(universities: list, records: list):
    """导出合并的 JSON"""
    # 院校
    uni_out = os.path.join(EXPORT_DIR, "universities_all.json")
    with open(uni_out, "w", encoding="utf-8") as f:
        json.dump(universities, f, ensure_ascii=False, indent=2)
    print(f"✅ 院校 JSON: {uni_out}")

    # 分数线
    sl_out = os.path.join(EXPORT_DIR, "score_lines_all.json")
    with open(sl_out, "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)
    print(f"✅ 分数线 JSON: {sl_out}")

    # 统计摘要
    years = sorted(set(r["year"] for r in records))
    subjects = sorted(set(r["subject_type"] for r in records))
    schools = sorted(set(r["university_name"] for r in records))
    summary = {
        "generated_at": datetime.now().isoformat(),
        "total_universities": len(universities),
        "total_score_lines": len(records),
        "years": years,
        "subject_types": subjects,
        "unique_schools_in_score_lines": len(schools),
        "breakdown": {},
    }
    for y in years:
        year_records = [r for r in records if r["year"] == y]
        summary["breakdown"][str(y)] = {
            "total": len(year_records),
            "by_subject": {
                s: len([r for r in year_records if r["subject_type"] == s])
                for s in subjects
                if any(r["subject_type"] == s for r in year_records)
            }
        }

    sum_out = os.path.join(EXPORT_DIR, "summary.json")
    with open(sum_out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"✅ 数据摘要: {sum_out}")


def export_excel(universities: list, records: list):
    """导出 Excel（需要 pandas + openpyxl）"""
    try:
        import pandas as pd
    except ImportError:
        print("⚠️  pandas 未安装，跳过 Excel 导出")
        return

    # 院校
    if universities:
        df_uni = pd.DataFrame(universities)
        cols = ["school_id", "name", "province_name", "city_name", "level",
                "type_name", "f985", "f211", "dual_class", "nature_name", "belong"]
        df_uni = df_uni[[c for c in cols if c in df_uni.columns]]
        uni_xlsx = os.path.join(EXPORT_DIR, "universities.xlsx")
        df_uni.to_excel(uni_xlsx, index=False, sheet_name="院校")
        print(f"✅ 院校 Excel: {uni_xlsx}")

    # 分数线
    if records:
        df_sl = pd.DataFrame(records)
        cols = ["university_name", "major_name", "year", "subject_type",
                "min_score", "max_score", "avg_score", "min_rank", "plan_count", "batch"]
        df_sl = df_sl[[c for c in cols if c in df_sl.columns]]
        df_sl = df_sl.sort_values(["year", "subject_type", "university_name", "major_name"])
        sl_xlsx = os.path.join(EXPORT_DIR, "score_lines.xlsx")
        df_sl.to_excel(sl_xlsx, index=False, sheet_name="分数线")
        print(f"✅ 分数线 Excel: {sl_xlsx}")


def main():
    print("=" * 60)
    print("  GZLY 数据导出")
    print("=" * 60)

    print("\n加载数据...")
    universities = load_universities()
    records = load_all_score_lines()

    if not universities and not records:
        print("\n没有可导出的数据。请先运行爬虫脚本:")
        print("  python scrape_universities.py")
        print("  python scrape_score_lines.py")
        return

    print(f"\n数据总量:")
    print(f"  院校: {len(universities)}")
    print(f"  分数线: {len(records)}")

    # 1. SQL
    print("\n--- 导出 SQL ---")
    if universities:
        export_universities_sql(universities,
                               os.path.join(EXPORT_DIR, "01_universities.sql"))
    if records:
        export_score_lines_sql(records,
                              os.path.join(EXPORT_DIR, "02_score_lines.sql"))

    # 2. JSON
    print("\n--- 导出 JSON ---")
    export_merged_json(universities, records)

    # 3. Excel
    print("\n--- 导出 Excel ---")
    export_excel(universities, records)

    print(f"\n{'=' * 60}")
    print(f"  导出完成！所有文件在: {EXPORT_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
