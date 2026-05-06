# GZLY 数据爬取工具

从**掌上高考**（阳光高考 `api.eol.cn`）公开 API 爬取贵州高考志愿相关数据。

## 数据内容

| 数据 | 说明 |
|------|------|
| 院校信息 | 在贵州招生的所有本科院校：名称、省份、城市、层次(985/211/双一流)、类型、校徽 |
| 投档分数线 | 近 5 年 (2020-2024) 每所院校每个专业的最低分、最低位次、招生计划数 |
| 校徽图片 | 每所院校的 Logo 图片 |

> **注意**: 2024 年起贵州实行新高考 "3+1+2"，科类从 `文科/理科` 变为 `历史类/物理类`

## 快速开始

```bash
# 1. 安装依赖
cd /Users/dongsiwei/Desktop/skliis/projects/GZLY/scripts
pip install -r requirements.txt

# 2. 爬取院校信息 + 校徽
python scrape_universities.py

# 3. 爬取分数线（全部5年）
python scrape_score_lines.py

# 4. 导出为 SQL / Excel / JSON
python export_to_sql.py
```

## 按需爬取

```bash
# 仅爬取 2024 年
python scrape_score_lines.py --year 2024

# 仅爬取 2024 年物理类
python scrape_score_lines.py --year 2024 --subject 5

# 科类代码: 1=文科/历史类, 5=理科/物理类
```

## 输出目录结构

```
scripts/data/
├── universities/
│   ├── universities.json      # 院校完整数据
│   └── school_id_map.json     # ID→名称映射
├── images/
│   ├── 100001.png             # 校徽图片（以 school_id 命名）
│   └── ...
├── score_lines/
│   ├── score_lines_2020_文科.json
│   ├── score_lines_2020_理科.json
│   ├── ...
│   ├── score_lines_2024_历史类.json
│   └── score_lines_2024_物理类.json
└── export/
    ├── 01_universities.sql    # MySQL 建表 + 插入
    ├── 02_score_lines.sql     # MySQL 建表 + 插入
    ├── universities_all.json  # 合并 JSON
    ├── score_lines_all.json
    ├── universities.xlsx      # Excel
    ├── score_lines.xlsx
    └── summary.json           # 数据统计摘要
```

## 数据格式

### 院校 (universities.json)
```json
{
  "school_id": "100001",
  "name": "清华大学",
  "province_name": "北京",
  "city_name": "北京",
  "level": "985",
  "type_name": "综合",
  "tags": ["985", "211", "双一流"],
  "f985": true,
  "f211": true,
  "dual_class": true,
  "nature_name": "公办",
  "logo_url": "https://...",
  "logo_local": "100001.png"
}
```

### 分数线 (score_lines_*.json)
```json
{
  "school_id": "100001",
  "university_name": "清华大学",
  "major_name": "计算机科学与技术",
  "major_id": "080901",
  "year": 2024,
  "subject_type": "物理类",
  "min_score": 680,
  "max_score": 695,
  "avg_score": 688,
  "min_rank": 52,
  "plan_count": 3,
  "batch": "本科批"
}
```

## 配置说明

编辑 `config.py` 可调整：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `YEARS` | [2020-2024] | 爬取年份范围 |
| `REQUEST_DELAY` | 0.8s | 请求间隔（越大越礼貌） |
| `MAX_RETRIES` | 3 | 失败重试次数 |
| `TIMEOUT` | 15s | 请求超时 |

## 断点续爬

分数线爬虫支持断点续爬：
- 每个 `年份+科类` 组合单独保存一个 JSON 文件
- 如果文件已存在且非空，会自动跳过
- 要重爬某个组合，删除对应文件即可

## 导入数据库

```bash
# 导入 MySQL
mysql -u root -p gzly_db < data/export/01_universities.sql
mysql -u root -p gzly_db < data/export/02_score_lines.sql
```

## 法律声明

- 数据来源为掌上高考公开 API，仅用于教育研究目的
- 请遵守合理使用原则，不要高频请求
- 校徽图片版权归各院校所有
