def safe_float(value, default: float = 0.0) -> float:
    try:
        return float(value)
    except Exception:
        return default
