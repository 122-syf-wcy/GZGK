"""
GZLY 数据爬取配置
数据源: 掌上高考 / 阳光高考 (api.eol.cn)
"""

# ---- 爬取范围 ----
PROVINCE_ID = "52"          # 贵州省代码
PROVINCE_NAME = "贵州"
YEARS = [2020, 2021, 2022, 2023, 2024, 2025]

# 2024年起贵州实行新高考 "3+1+2"
# 2020-2023: 文科(1) / 理科(5)
# 2024:      历史类(1) / 物理类(5)
SUBJECT_TYPES_OLD = {1: "文科", 5: "理科"}          # 2020-2023
SUBJECT_TYPES_NEW = {1: "历史类", 5: "物理类"}      # 2024+
NEW_GAOKAO_START_YEAR = 2024

# ---- API 配置 ----
BASE_URL = "https://api.eol.cn"

# 院校列表
UNIVERSITY_LIST_URL = f"{BASE_URL}/web/api/school/lists"
# 院校详情
UNIVERSITY_DETAIL_URL = f"{BASE_URL}/web/api/school/detail"
# 分数线（按专业）
SCORE_LINE_URL = f"{BASE_URL}/web/api/score/major"
# 院校分数线概览
SCHOOL_SCORE_URL = f"{BASE_URL}/web/api/score/school"

# 静态资源（校徽等）
STATIC_BASE = "https://static-data.gaokao.cn"

# CDN 直接访问
CDN_BASE = "https://static-data.gaokao.cn/www/2.0"
CDN_GKCX = "https://static-gkcx.gaokao.cn/www/2.0"

# ---- 请求配置 ----
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/125.0.0.0 Safari/537.36",
    "Referer": "https://gaokao.cn/",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

# 请求间隔（秒），礼貌爬取
REQUEST_DELAY = 0.8         # 普通请求间隔
REQUEST_DELAY_IMAGE = 0.3   # 图片下载间隔
MAX_RETRIES = 3             # 最大重试次数
RETRY_DELAY = 3             # 重试间隔（秒）
TIMEOUT = 15                # 请求超时（秒）
CONCURRENT_DOWNLOADS = 3    # 图片并发下载数

# ---- 输出路径 ----
import os
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
UNIVERSITY_DIR = os.path.join(DATA_DIR, "universities")
IMAGE_DIR = os.path.join(DATA_DIR, "images")
SCORE_LINE_DIR = os.path.join(DATA_DIR, "score_lines")
EXPORT_DIR = os.path.join(DATA_DIR, "export")

# 确保目录存在
for d in [DATA_DIR, UNIVERSITY_DIR, IMAGE_DIR, SCORE_LINE_DIR, EXPORT_DIR]:
    os.makedirs(d, exist_ok=True)
