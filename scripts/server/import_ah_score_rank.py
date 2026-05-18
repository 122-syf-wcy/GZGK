#!/usr/bin/env python3.11
"""
导入安徽 2025 一分一段表到 data_score_rank。

数据源：安徽省 2025 高考一分一段表（公开数据，转载于合肥本地宝 m.hf.bendibao.com）
口径：包含政策加分 + 艺术体育考生，不含已录取/少年班

公式：
  rank_high = cumulative_count （该分数同分人数累计最后位次）
  rank_low  = cumulative_count - segment_count + 1
  score_label = "{score}分"（高端区间 691-750 用 "691分及以上"）
"""
import os, pymysql, sys

SOURCE_NAME = "安徽省教育招生考试院"
SOURCE_URL = "https://m.hf.bendibao.com/edu/yifenyiduanchaxun/?sheng=安徽&year=2025&type={subject}"
SOURCE_PAGE_URL = "https://edu.anhuinews.com/kszx/gk/gzdt/202506/t20250625_8581781.html"
PARSE_METHOD = "web_table_extracted_2026-05-18"

def parse(path):
    """读 tab/whitespace 分隔的 3 列：score_or_range, segment_count, cumulative_count"""
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line: continue
            parts = line.split()
            if len(parts) != 3: continue
            score_field, seg_str, cum_str = parts
            try:
                seg = int(seg_str); cum = int(cum_str)
            except ValueError: continue
            if "-" in score_field:
                # 高端区间 691-750 → 入库为 score=low (691)，label "691分及以上"
                lo, hi = score_field.split("-", 1)
                lo = int(lo); hi = int(hi)
                rows.append((lo, f"{lo}分及以上 ({lo}-{hi})", seg, cum))
            else:
                s = int(score_field)
                rows.append((s, f"{s}分", seg, cum))
    return rows

def db():
    pw = os.environ["MYSQL_PWD"]
    return pymysql.connect(host="127.0.0.1", user="root", password=pw, database="gzly", charset="utf8mb4")

def import_subject(conn, subject_type, source_subject_param, rows):
    cur = conn.cursor()
    # 清掉本省本年本科目老数据，避免重复 upsert
    cur.execute("DELETE FROM data_score_rank WHERE province_code=%s AND year=%s AND subject_type=%s",
                ("AH", 2025, subject_type))
    deleted = cur.rowcount
    sql = """
INSERT INTO data_score_rank
(province_code, province_name, year, subject_type, score, score_label,
 segment_count, cumulative_count, rank_low, rank_high,
 source_name, source_url, source_page_url, source_file, parse_method)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
"""
    src_url = SOURCE_URL.format(subject=source_subject_param)
    inserted = 0
    for (score, label, seg, cum) in rows:
        rank_high = cum
        rank_low = max(1, cum - seg + 1)
        cur.execute(sql, (
            "AH", "安徽", 2025, subject_type, score, label,
            seg, cum, rank_low, rank_high,
            SOURCE_NAME, src_url, SOURCE_PAGE_URL,
            f"ah_2025_score_rank_{subject_type.replace('类','')}.txt",
            PARSE_METHOD,
        ))
        inserted += 1
    conn.commit()
    print(f"  {subject_type}: deleted={deleted} inserted={inserted}")
    return inserted

def main():
    base = os.path.dirname(os.path.abspath(__file__))
    physics = parse(os.path.join(base, "ah_2025_score_rank_physics.txt"))
    history = parse(os.path.join(base, "ah_2025_score_rank_history.txt"))
    print(f"parsed physics={len(physics)} rows, history={len(history)} rows")
    if not physics or not history:
        print("ERROR: empty parse", file=sys.stderr)
        sys.exit(2)
    conn = db()
    try:
        n1 = import_subject(conn, "物理类", "物理", physics)
        n2 = import_subject(conn, "历史类", "历史", history)
    finally:
        conn.close()
    print(f"DONE: 物理类={n1}, 历史类={n2}, total={n1+n2}")

if __name__ == "__main__":
    main()
