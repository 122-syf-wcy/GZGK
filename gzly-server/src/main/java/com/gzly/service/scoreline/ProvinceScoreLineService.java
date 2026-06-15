package com.gzly.service.scoreline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.gzly.service.scoreline.ScoreLineModels.TYPE_ADMISSION_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_ART_SPORT;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_CONTROL_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_MAJOR_GROUP_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_MAJOR_SCORE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_SCORE_RANK;

@Service
@RequiredArgsConstructor
public class ProvinceScoreLineService {

    private final ProvinceScoreLineAdapterRegistry registry;

    public ScoreLineModels.Capability capability(String provinceCode) {
        return registry.getAdapter(provinceCode).capability();
    }

    public ScoreLineModels.QueryResult query(String provinceCode, String type, ScoreLineModels.Query query) {
        ProvinceScoreLineAdapter adapter = registry.getAdapter(provinceCode);
        query.setScoreLineType(type);
        return switch (type) {
            case TYPE_CONTROL_LINE -> adapter.queryControlLine(query);
            case TYPE_SCORE_RANK -> adapter.queryScoreRank(query);
            case TYPE_ADMISSION_LINE -> adapter.queryAdmissionLine(query);
            case TYPE_MAJOR_GROUP_LINE -> adapter.queryMajorGroupLine(query);
            case TYPE_MAJOR_SCORE -> adapter.queryMajorScoreLine(query);
            case TYPE_ART_SPORT -> adapter.queryArtSportLine(query);
            default -> adapter.queryAdmissionLine(query);
        };
    }
}
