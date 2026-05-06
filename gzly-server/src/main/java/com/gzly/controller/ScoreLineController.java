package com.gzly.controller;

import com.gzly.common.PageResult;
import com.gzly.common.Result;
import com.gzly.service.ScoreLineService;
import com.gzly.service.ScoreLineService.ScoreLineView;
import com.gzly.service.ScoreLineService.SchoolScoreSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/score-line")
@RequiredArgsConstructor
public class ScoreLineController {

    private final ScoreLineService scoreLineService;

    @GetMapping("/list")
    public Result<PageResult<ScoreLineView>> list(
            @RequestParam(required = false, defaultValue = "GZ") String provinceCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String universityName,
            @RequestParam(required = false) String majorName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        return Result.ok(scoreLineService.list(provinceCode, year, subjectType, universityName, majorName, page, pageSize));
    }

    @GetMapping("/schools")
    public Result<PageResult<SchoolScoreSummary>> schools(
            @RequestParam(required = false, defaultValue = "GZ") String provinceCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String universityName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        return Result.ok(scoreLineService.listSchoolSummaries(provinceCode, year, subjectType, universityName, page, pageSize));
    }

    @GetMapping("/school-history")
    public Result<List<ScoreLineView>> schoolHistory(
            @RequestParam(required = false, defaultValue = "GZ") String provinceCode,
            @RequestParam String schoolId,
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String subjectType,
            @RequestParam(defaultValue = "10") int maxRecords) {
        maxRecords = Math.min(Math.max(maxRecords, 1), 20);
        return Result.ok(scoreLineService.findSchoolHistory(provinceCode, schoolId, groupCode, subjectType, maxRecords));
    }

    @GetMapping("/years")
    public Result<List<Integer>> years(@RequestParam(required = false, defaultValue = "GZ") String provinceCode) {
        return Result.ok(scoreLineService.getAvailableYears(provinceCode));
    }

    /**
     * 热门专业TOP10 — 基于真实录取数据统计
     * 返回: [{name, schoolCount, avgScore, avgRank, heat}]
     */
    @GetMapping("/hot-majors")
    public Result<List<Map<String, Object>>> hotMajors(
            @RequestParam(defaultValue = "物理类") String subjectType,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(scoreLineService.getHotMajors(subjectType, Math.min(Math.max(limit, 1), 20)));
    }
}
