package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AlgorithmService;
import com.gzly.service.AlgorithmService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能算法 API — 四大核心算法端点
 */
@RestController
@RequestMapping("/algorithm")
@RequiredArgsConstructor
@Validated
public class AlgorithmController {

    private final AlgorithmService algorithmService;

    // ── 算法1: 历史参考概率计算 ──

    @GetMapping("/probability")
    public Result<AdmissionProbability> probability(
            @RequestParam @Min(1) int studentRank,
            @RequestParam @NotBlank String schoolId,
            @RequestParam(required = false, defaultValue = "") String majorName,
            @RequestParam @NotBlank String subjectType) {
        return Result.ok(algorithmService.calcProbability(studentRank, schoolId, majorName, subjectType));
    }

    @PostMapping("/probability/batch")
    public Result<List<AdmissionProbability>> batchProbability(@RequestBody @Valid BatchProbRequest req) {
        return Result.ok(algorithmService.batchCalcProbability(
                req.getStudentRank(), req.getSubjectType(), req.getTargets()));
    }

    // ── 算法2: 风险评估 ──

    @GetMapping("/risk")
    public Result<RiskAssessment> risk(
            @RequestParam @NotBlank String schoolId,
            @RequestParam(required = false, defaultValue = "") String majorName,
            @RequestParam @NotBlank String subjectType) {
        return Result.ok(algorithmService.assessRisk(schoolId, majorName, subjectType));
    }

    // ── 算法3: 分数线预测 ──

    @GetMapping("/predict")
    public Result<ScorePrediction> predict(
            @RequestParam @NotBlank String schoolId,
            @RequestParam(required = false, defaultValue = "") String majorName,
            @RequestParam @NotBlank String subjectType) {
        return Result.ok(algorithmService.predictScore(schoolId, majorName, subjectType));
    }

    // ── 算法4: 协同过滤推荐 ──

    @GetMapping("/recommend")
    public Result<List<RecommendItem>> recommend(
            @RequestParam @NotBlank String schoolId,
            @RequestParam(required = false, defaultValue = "") String majorName,
            @RequestParam @NotBlank String subjectType,
            @RequestParam(defaultValue = "0") @Min(0) int studentRank,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topN) {
        return Result.ok(algorithmService.recommendSimilar(schoolId, majorName, subjectType, studentRank, clampTopN(topN)));
    }

    @PostMapping("/recommend/batch")
    public Result<List<RecommendItem>> batchRecommend(@RequestBody @Valid BatchRecommendRequest req) {
        return Result.ok(algorithmService.recommendByMultiple(
                req.getSelected(), req.getSubjectType(), req.getStudentRank(), clampTopN(req.getTopN())));
    }

    // ── 算法5: 官方一分一段表位次区间，仅作手填位次校验 ──

    @GetMapping("/estimate-rank")
    public Result<AlgorithmService.RankEstimate> estimateRank(
            @RequestParam @Min(1) @Max(750) int score,
            @RequestParam @NotBlank String subjectType,
            @RequestParam(required = false) Integer year) {
        return Result.ok(algorithmService.estimateRank(score, subjectType, year));
    }

    // ── 综合分析: 一次调用返回概率+风险+预测 ──

    @GetMapping("/analysis")
    public Result<ComprehensiveAnalysis> analysis(
            @RequestParam @Min(1) int studentRank,
            @RequestParam @NotBlank String schoolId,
            @RequestParam(required = false, defaultValue = "") String majorName,
            @RequestParam @NotBlank String subjectType) {
        ComprehensiveAnalysis result = new ComprehensiveAnalysis();
        result.setProbability(algorithmService.calcProbability(studentRank, schoolId, majorName, subjectType));
        result.setRisk(algorithmService.assessRisk(schoolId, majorName, subjectType));
        result.setPrediction(algorithmService.predictScore(schoolId, majorName, subjectType));
        return Result.ok(result);
    }

    // ── 请求/响应 DTO ──

    @Data
    public static class BatchProbRequest {
        @Min(1)
        private int studentRank;
        @NotBlank
        private String subjectType;
        @NotEmpty
        @Valid
        private List<VolunteerTarget> targets;
    }

    @Data
    public static class BatchRecommendRequest {
        @NotEmpty
        @Valid
        private List<VolunteerTarget> selected;
        @NotBlank
        private String subjectType;
        @Min(0)
        private int studentRank;
        @Min(1)
        @Max(50)
        private int topN = 10;
    }

    @Data
    public static class ComprehensiveAnalysis {
        private AdmissionProbability probability;
        private RiskAssessment risk;
        private ScorePrediction prediction;
    }

    private int clampTopN(int topN) {
        return Math.min(Math.max(topN, 1), 20);
    }
}
