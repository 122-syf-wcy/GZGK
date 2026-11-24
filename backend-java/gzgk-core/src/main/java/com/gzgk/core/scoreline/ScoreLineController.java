package com.gzgk.core.scoreline;

import com.gzgk.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/score-rank")
public class ScoreLineController {

    private final ScoreLineService scoreLineService;

    public ScoreLineController(ScoreLineService scoreLineService) {
        this.scoreLineService = scoreLineService;
    }

    @GetMapping("/estimate")
    public ApiResponse<ScoreLineService.RankEstimate> estimate(
            @RequestParam(defaultValue = "GZ") String provinceCode,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "物理类") String subjectType,
            @RequestParam int score
    ) {
        return ApiResponse.ok(scoreLineService.estimateRank(provinceCode, year, subjectType, score));
    }

    @GetMapping("/distribution")
    public ApiResponse<Map<String, Object>> distribution(
            @RequestParam(defaultValue = "GZ") String provinceCode,
            @RequestParam(defaultValue = "物理类") String subjectType
    ) {
        List<ScoreLineService.ScoreDistributionPoint> curve = scoreLineService.getDistributionCurve(provinceCode, subjectType);
        return ApiResponse.ok(Map.of(
                "provinceCode", provinceCode,
                "subjectType", subjectType,
                "undergraduateLine", 380,
                "specialControlLine", 482,
                "curve", curve
        ));
    }

    @GetMapping("/lines")
    public ApiResponse<List<MajorScoreRecord>> queryScoreLines(
            @RequestParam(defaultValue = "GZ") String provinceCode,
            @RequestParam(required = false) String schoolName,
            @RequestParam(required = false) String majorName,
            @RequestParam(required = false) String subjectType,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(scoreLineService.querySchoolHistory(provinceCode, schoolName, majorName, subjectType, limit));
    }
}
