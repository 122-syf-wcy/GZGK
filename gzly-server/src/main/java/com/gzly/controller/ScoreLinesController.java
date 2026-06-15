package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.scoreline.ProvinceScoreLineService;
import com.gzly.service.scoreline.ScoreLineModels;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/score-lines/{provinceCode}")
@RequiredArgsConstructor
public class ScoreLinesController {

    private final ProvinceScoreLineService provinceScoreLineService;

    @GetMapping("/capability")
    public Result<ScoreLineModels.Capability> capability(@PathVariable String provinceCode) {
        return Result.ok(provinceScoreLineService.capability(provinceCode));
    }

    @GetMapping("/control-lines")
    public Result<ScoreLineModels.QueryResult> controlLines(@PathVariable String provinceCode,
                                                            ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_CONTROL_LINE, params.toQuery()));
    }

    @GetMapping("/score-rank")
    public Result<ScoreLineModels.QueryResult> scoreRank(@PathVariable String provinceCode,
                                                         ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_SCORE_RANK, params.toQuery()));
    }

    @GetMapping("/admission-lines")
    public Result<ScoreLineModels.QueryResult> admissionLines(@PathVariable String provinceCode,
                                                              ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_ADMISSION_LINE, params.toQuery()));
    }

    @GetMapping("/major-group-lines")
    public Result<ScoreLineModels.QueryResult> majorGroupLines(@PathVariable String provinceCode,
                                                               ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_MAJOR_GROUP_LINE, params.toQuery()));
    }

    @GetMapping("/major-score-lines")
    public Result<ScoreLineModels.QueryResult> majorScoreLines(@PathVariable String provinceCode,
                                                               ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_MAJOR_SCORE, params.toQuery()));
    }

    @GetMapping("/art-sport-lines")
    public Result<ScoreLineModels.QueryResult> artSportLines(@PathVariable String provinceCode,
                                                             ScoreLineQueryParams params) {
        return Result.ok(provinceScoreLineService.query(provinceCode, ScoreLineModels.TYPE_ART_SPORT, params.toQuery()));
    }

    @Data
    public static class ScoreLineQueryParams {
        private Integer year;
        private String batchCode;
        private String subjectCategory;
        private String subjectType;
        private String selectedSubjects;
        private String schoolCode;
        private String schoolName;
        private String majorGroupCode;
        private String majorName;
        private Integer score;
        private int page = 1;
        private int pageSize = 20;

        ScoreLineModels.Query toQuery() {
            ScoreLineModels.Query query = new ScoreLineModels.Query();
            query.setYear(year);
            query.setBatchCode(batchCode);
            query.setSubjectCategory(subjectCategory);
            query.setSubjectType(subjectType);
            query.setSelectedSubjects(selectedSubjects);
            query.setSchoolCode(schoolCode);
            query.setSchoolName(schoolName);
            query.setMajorGroupCode(majorGroupCode);
            query.setMajorName(majorName);
            query.setScore(score);
            query.setPage(Math.max(page, 1));
            query.setPageSize(Math.min(Math.max(pageSize, 1), 100));
            return query;
        }
    }
}
