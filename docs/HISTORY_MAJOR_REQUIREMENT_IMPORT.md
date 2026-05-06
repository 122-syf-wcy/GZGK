# 历史类官方选科要求入库流程

## 背景

2025 年贵州物理类招生专业目录可以直接抽取文本，已经通过 `build_major_requirement_from_catalog.py` 生成并导入 `data_major_requirement_gz`。

历史类 PDF 正文存在字体编码错乱，不能把乱码解析结果硬导入生产库。历史类必须走“人工表格 / OCR 复核 CSV → 标准化 → SQL 导入 → 抽样核验”的保守流程。

## 输入 CSV

从 OCR、人工录入或表格整理出 CSV，建议复制 `scripts/data/major_requirements_gz_history.template.csv` 作为模板。

必备字段：

- `university_name`
- `major_name`
- `resubject_requirement`

建议字段：

- `year`
- `major_id`
- `subject_type`
- `first_subject_requirement`
- `requirement_text`
- `source_url`
- `source_file`

脚本也兼容部分中文列名，如 `院校名称`、`专业名称`、`再选科目要求`、`科类`。

## 生成标准 CSV

```bash
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts
python3 server/build_major_requirement_from_manual_csv.py \
  --input-csv data/major_requirements_gz_history_manual.csv \
  --output-csv data/major_requirements_gz_history.csv \
  --reject-csv data/major_requirements_gz_history_rejects.csv \
  --schools-json data/guizhou_schools.json \
  --year 2025 \
  --subject-type 历史类 \
  --source-url '<贵州省招生考试院历史类专业目录原文链接>' \
  --source-file '<历史类专业目录文件名.pdf>'
```

输出：

- `data/major_requirements_gz_history.csv`：可导入的标准 CSV。
- `data/major_requirements_gz_history_rejects.csv`：未匹配学校、缺专业名、选科要求不明确的行，必须人工复核。

## 生成并导入 SQL

```bash
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts
INPUT_CSV=data/major_requirements_gz_history.csv \
OUTPUT_SQL=data/export/major_requirements_gz_history.sql \
DB_PASS='<DB_PASS>' \
bash server/import_major_requirement.sh
```

服务器上执行时，优先从 `/etc/gzly/data-gap-supplement.env` 注入 `DB_PASS`：

```bash
cd /root/gzly_scraper
set -a
source /etc/gzly/data-gap-supplement.env
set +a
INPUT_CSV=data/major_requirements_gz_history.csv \
OUTPUT_SQL=data/export/major_requirements_gz_history.sql \
bash server/import_major_requirement.sh
```

## 入库后核验

至少检查：

```sql
SELECT year, subject_type, resubject_requirement, COUNT(*)
FROM data_major_requirement_gz
WHERE year = 2025 AND subject_type = '历史类'
GROUP BY year, subject_type, resubject_requirement
ORDER BY resubject_requirement;
```

抽样核验：

- 医学、护理、药学、中医类。
- 公安、军警、司法类。
- 师范类。
- 政治、地理、生物等再选限制高频专业。
- OCR 低置信度或 reject 文件中出现过的学校。

## 质量门槛

- `rejects` 必须人工处理，不能忽略。
- 无法确认来源的行不能导入生产。
- 导入后生成接口仍应保留强制人工复核清单，不能把历史类结果宣传成“完全自动官方核验”。
- 任何 OCR 结果必须保留 `source_file` 和 `source_url`，方便用户回到官方材料核验。
