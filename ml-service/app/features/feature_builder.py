def rank_diff_features(candidate: dict, item: dict) -> dict:
    candidate_rank = int(candidate.get("rank") or 0)
    predicted_rank = int(item.get("predictedMinRank") or item.get("historyMinRank") or 0)
    return {
        "candidate_rank": candidate_rank,
        "predicted_min_rank": predicted_rank,
        "rank_diff": predicted_rank - candidate_rank if candidate_rank and predicted_rank else 0,
        "plan_change_rate": item.get("planChangeRate", 0),
    }
