package com.gzly.service.scoreline;

public interface ProvinceScoreLineAdapter {

    String provinceCode();

    ScoreLineModels.Capability capability();

    ScoreLineModels.QueryResult queryControlLine(ScoreLineModels.Query query);

    ScoreLineModels.QueryResult queryScoreRank(ScoreLineModels.Query query);

    ScoreLineModels.QueryResult queryAdmissionLine(ScoreLineModels.Query query);

    ScoreLineModels.QueryResult queryMajorGroupLine(ScoreLineModels.Query query);

    ScoreLineModels.QueryResult queryMajorScoreLine(ScoreLineModels.Query query);

    ScoreLineModels.QueryResult queryArtSportLine(ScoreLineModels.Query query);

    String getMissingDataReason(String scoreLineType);
}
