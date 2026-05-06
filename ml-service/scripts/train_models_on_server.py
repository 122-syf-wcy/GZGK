"""服务器端训练脚本：在 ml-service 进程外，直接调用训练函数完成 rank/chance 双模型训练，
然后通过 GZLY 后台 API 写入 ml_model_registry 注册表。

设计目标：
1. 不再调用 ml-service 的 /ml/train HTTP 接口（避免训练递归触发、超时不稳定等问题）；
2. 训练逻辑直接 import 自 ml-service 的训练函数，单进程跑完；
3. 训练完成后通过 POST /api/admin/ml/models/register 写入注册表，由运维使用 /activate 端点单独激活。

典型用法（部署服务器上 cron / systemd timer 调起）::

    GZLY_DB_PASSWORD=*** \
    GZLY_API_TOKEN=admin-secret \
    python -m scripts.train_models_on_server \
        --models rank chance \
        --output-dir /var/lib/gzly-ml/models \
        --csv data/training_rank.csv \
        --register

环境变量：
    GZLY_DB_PASSWORD     必填，构造 CSV 时连库需要
    GZLY_API_BASE        默认 http://127.0.0.1:8090
    GZLY_API_TOKEN       后台管理 token（如果有），通过 X-Admin-Token 头透传
    GZLY_REGISTRY_PATH   注册接口路径，默认 /api/admin/ml/models/register
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

import urllib.error
import urllib.request

# 训练函数：直接 import，不走 ml-service 的 HTTP
SCRIPT_DIR = Path(__file__).resolve().parent
SERVICE_DIR = SCRIPT_DIR.parent
sys.path.insert(0, str(SERVICE_DIR))

from app.models.chance_score_model import train_chance_model  # noqa: E402
from app.models.rank_prediction_model import train_rank_model  # noqa: E402


def ensure_csv(csv_path: Path, min_rows: int, regenerate: bool) -> None:
    """如果 CSV 不存在或 regenerate=True，调用 build_training_csv 重建。

    采用延迟 import：只有真正需要重建 CSV 时才 import build_training_csv（依赖 pymysql），
    以便在已经存在 CSV 的纯训练环境也能 import 本模块。
    """
    if csv_path.exists() and not regenerate:
        return
    print(f"[train] (re)building training CSV -> {csv_path}", flush=True)
    from scripts.build_training_csv import main as build_csv_main  # noqa: WPS433  延迟 import
    rc = build_csv_main([
        "--output", str(csv_path),
        "--min-rows", str(min_rows),
        "--strict",
    ])
    if rc != 0:
        sys.exit(f"[train] build_training_csv failed with code {rc}")


def stamped_version(model_name: str) -> str:
    return f"{model_name}-v{datetime.now().strftime('%Y%m%d-%H%M%S')}"


def post_register(payload: dict[str, Any]) -> dict[str, Any]:
    """向 GZLY 后台 POST 注册表更新。失败抛 RuntimeError。"""
    api_base = os.environ.get("GZLY_API_BASE", "http://127.0.0.1:8090").rstrip("/")
    path = os.environ.get("GZLY_REGISTRY_PATH", "/api/admin/ml/models/register")
    url = f"{api_base}{path}"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    token = os.environ.get("GZLY_API_TOKEN")
    if token:
        req.add_header("X-Admin-Token", token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text) if text else {"status": "no_content"}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="ignore") if exc.fp else exc.reason
        raise RuntimeError(f"register HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"register URL error: {exc.reason}") from exc


def train_rank(csv: Path, output_dir: Path, train_year_range: str | None,
               status: str, register: bool) -> dict[str, Any]:
    print("[train] training rank-prediction model ...", flush=True)
    result = train_rank_model(str(csv), str(output_dir))
    if result.get("status") != "ok":
        print(f"[train] rank-prediction failed: {result}", file=sys.stderr)
        return result
    version = stamped_version("rank-prediction")
    payload = {
        "modelName": "rank-prediction",
        "modelType": result.get("modelType", "LightGBMRegressor"),
        "modelVersion": version,
        "trainYearRange": train_year_range or "",
        "trainDataCount": int(result.get("trainDataCount", 0)),
        "metrics": result.get("metrics", {}),
        "featureSchema": result.get("featureSchema", {}),
        "modelFilePath": result.get("modelFilePath", ""),
        "status": status,
    }
    if register:
        try:
            registered = post_register(payload)
            print(f"[train] rank-prediction registered: {registered}", flush=True)
            result["registry"] = registered
        except Exception as exc:
            print(f"[train] rank-prediction register failed: {exc}", file=sys.stderr, flush=True)
            result["registryError"] = str(exc)
    return result


def train_chance(csv: Path, output_dir: Path, train_year_range: str | None,
                 status: str, register: bool) -> dict[str, Any]:
    print("[train] training chance-score model ...", flush=True)
    result = train_chance_model(str(csv), str(output_dir))
    if result.get("status") != "ok":
        print(f"[train] chance-score failed: {result}", file=sys.stderr)
        return result
    version = stamped_version("chance-score")
    payload = {
        "modelName": "chance-score",
        "modelType": result.get("modelType", "LightGBMClassifier"),
        "modelVersion": version,
        "trainYearRange": train_year_range or "",
        "trainDataCount": int(result.get("trainDataCount", 0)),
        "metrics": result.get("metrics", {}),
        "featureSchema": result.get("featureSchema", {}),
        "modelFilePath": result.get("modelFilePath", ""),
        "status": status,
    }
    if register:
        try:
            registered = post_register(payload)
            print(f"[train] chance-score registered: {registered}", flush=True)
            result["registry"] = registered
        except Exception as exc:
            print(f"[train] chance-score register failed: {exc}", file=sys.stderr, flush=True)
            result["registryError"] = str(exc)
    return result


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="GZLY 服务器端 ML 模型训练 + 注册")
    parser.add_argument("--models", nargs="+", default=["rank", "chance"],
                        choices=["rank", "chance"], help="要训练的模型集合")
    parser.add_argument("--csv", default="data/training_rank.csv", help="训练用 CSV 路径")
    parser.add_argument("--output-dir", default="/var/lib/gzly-ml/models",
                        help="模型 .joblib 输出目录")
    parser.add_argument("--regenerate-csv", action="store_true",
                        help="重新构建训练 CSV（跑 build_training_csv.py --strict）")
    parser.add_argument("--min-rows", type=int, default=50,
                        help="重新构建 CSV 时强制的最少行数，少于即报错")
    parser.add_argument("--register", action="store_true",
                        help="训练完成后调用 /api/admin/ml/models/register 写注册表")
    parser.add_argument("--status", default="draft", choices=["draft", "active", "archived"],
                        help="注册时初始状态；推荐保持 draft，单独通过 /activate 激活")
    parser.add_argument("--train-year-range", default=None,
                        help="训练年份范围标签，例如 2019-2024（写入 ml_model_registry）")
    args = parser.parse_args(argv)

    csv_path = Path(args.csv)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    ensure_csv(csv_path, args.min_rows, args.regenerate_csv)

    summary: dict[str, Any] = {}
    if "rank" in args.models:
        summary["rank"] = train_rank(csv_path, output_dir, args.train_year_range,
                                     args.status, args.register)
    if "chance" in args.models:
        summary["chance"] = train_chance(csv_path, output_dir, args.train_year_range,
                                         args.status, args.register)

    print("[train] summary:")
    print(json.dumps(summary, ensure_ascii=False, indent=2, default=str))
    failed = [k for k, v in summary.items() if isinstance(v, dict) and v.get("status") != "ok"]
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
