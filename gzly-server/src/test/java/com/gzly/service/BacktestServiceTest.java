package com.gzly.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.algorithm.FeatureBuildEngine;
import com.gzly.common.exception.BizException;
import com.gzly.config.FallbackPredictionProperties;
import com.gzly.entity.AlgoBacktestReport;
import com.gzly.entity.MajorScoreGz;
import com.gzly.mapper.AlgoBacktestReportMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回测框架数学正确性测试：命中率、梯度分档、覆盖率与校准桶都用手工可验证的小数据集断言。
 */
class BacktestServiceTest {

    /** 纯单元测试没有 MyBatis 环境，LambdaQueryWrapper 需要手动注册实体元数据。 */
    @BeforeAll
    static void initEntityMeta() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MajorScoreGz.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlgoBacktestReport.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), com.gzly.entity.DataAdmissionGroupLine.class);
    }

    @Test
    void run_producesVerifiableHitRateAndGradientStats() {
        MajorScoreGzMapper majorScoreGzMapper = mock(MajorScoreGzMapper.class);
        AlgoBacktestReportMapper reportMapper = mock(AlgoBacktestReportMapper.class);
        when(reportMapper.insert(any())).thenReturn(1);

        // 第一次 selectList 返回 2025 真实结果，第二次返回 2022-2024 历史参考。
        when(majorScoreGzMapper.selectList(any())).thenReturn(truthRows()).thenReturn(historyRows());

        BacktestService service = new BacktestService(
                majorScoreGzMapper,
                mock(com.gzly.mapper.DataAdmissionGroupLineMapper.class),
                reportMapper,
                new FallbackRulePredictionEngine(),
                new FeatureBuildEngine(),
                new FallbackPredictionProperties(),
                new ObjectMapper(),
                stubNormalizationService(),
                new ProvincePolicyService());

        BacktestService.BacktestRequest request = new BacktestService.BacktestRequest();
        request.setEvaluationYear(2025);
        request.setSubjectType("物理类");
        request.setStrategyMode("均衡型");
        request.setCandidateRanks(List.of(10_000));

        BacktestService.BacktestReportView view = service.run(request);

        // 6 个候选落入梯度区间，其中 5 个有真实结果：A/C/E 命中，B/D 未命中。
        assertThat(view.getEvaluatedPairs()).isEqualTo(5);
        assertThat(view.getOverallHitRate()).isEqualTo(0.6);
        assertThat(view.getCoverageRatio()).isEqualTo(0.8333);
        assertThat(view.getCutoffYear()).isEqualTo(2024);
        assertThat(view.getBrierScore()).isBetween(0.0, 1.0);

        Map<String, BacktestService.GradientStat> byGradient = view.getGradientStats().stream()
                .collect(java.util.stream.Collectors.toMap(BacktestService.GradientStat::getGradient, s -> s));
        assertThat(byGradient.get("稳").getPairs()).isEqualTo(2);
        assertThat(byGradient.get("稳").getHits()).isEqualTo(1);
        assertThat(byGradient.get("稳").getHitRate()).isEqualTo(0.5);
        assertThat(byGradient.get("保").getPairs()).isEqualTo(1);
        assertThat(byGradient.get("保").getHits()).isEqualTo(1);
        assertThat(byGradient.get("冲").getPairs()).isEqualTo(1);
        assertThat(byGradient.get("冲").getHits()).isEqualTo(0);
        assertThat(byGradient.get("垫").getPairs()).isEqualTo(1);
        assertThat(byGradient.get("垫").getHits()).isEqualTo(1);

        int calibrationPairs = view.getCalibration().stream()
                .mapToInt(BacktestService.CalibrationBucket::getPairs)
                .sum();
        assertThat(calibrationPairs).isEqualTo(5);
        assertThat(view.getParamsSnapshot()).containsKey("fallbackProps");
    }

    @Test
    void run_rejectsInvalidSubjectType() {
        BacktestService service = new BacktestService(
                mock(MajorScoreGzMapper.class),
                mock(com.gzly.mapper.DataAdmissionGroupLineMapper.class),
                mock(AlgoBacktestReportMapper.class),
                new FallbackRulePredictionEngine(),
                new FeatureBuildEngine(),
                new FallbackPredictionProperties(),
                new ObjectMapper(),
                stubNormalizationService(),
                new ProvincePolicyService());

        BacktestService.BacktestRequest request = new BacktestService.BacktestRequest();
        request.setEvaluationYear(2025);
        request.setSubjectType("综合类");

        assertThatThrownBy(() -> service.run(request)).isInstanceOf(BizException.class);
    }

    @Test
    void resolveGradientRanges_usesPureRatioForHighRankSegment() {
        Map<String, int[]> ranges = BacktestService.resolveGradientRanges(10_000, "均衡型");
        assertThat(ranges.get("冲")).containsExactly(6_500, 9_500);
        assertThat(ranges.get("稳")).containsExactly(9_500, 12_000);
        assertThat(ranges.get("保")).containsExactly(12_000, 18_000);
        assertThat(ranges.get("垫")).containsExactly(18_000, 30_000);
    }

    @Test
    void resolveGradientRanges_intersectsOffsetAndRatioForLowerSegment() {
        // rank=50000 均衡型冲档：ratio [32500,47500]、offset [40000,47000] → 交集 [40000,47000]。
        Map<String, int[]> ranges = BacktestService.resolveGradientRanges(50_000, "均衡型");
        assertThat(ranges.get("冲")).containsExactly(40_000, 47_000);
    }

    @Test
    void classifyGradient_returnsNullWhenOutOfAllRanges() {
        Map<String, int[]> ranges = BacktestService.resolveGradientRanges(10_000, "均衡型");
        assertThat(BacktestService.classifyGradient(40_000, ranges)).isNull();
        assertThat(BacktestService.classifyGradient(10_000, ranges)).isEqualTo("稳");
    }

    @Test
    void run_supportsProfessionalGroupProvinceWithGroupLevelData() {
        // 湖北组级回测：考生 10000 名，均衡型区间 冲[6500,9500] 稳[9500,12000] 保[12000,18000] 垫[18000,30000]
        com.gzly.mapper.DataAdmissionGroupLineMapper groupLineMapper =
                mock(com.gzly.mapper.DataAdmissionGroupLineMapper.class);
        AlgoBacktestReportMapper reportMapper = mock(AlgoBacktestReportMapper.class);
        when(reportMapper.insert(any())).thenReturn(1);
        when(groupLineMapper.selectList(any())).thenReturn(groupTruthRows()).thenReturn(groupHistoryRows());

        BacktestService service = new BacktestService(
                mock(MajorScoreGzMapper.class),
                groupLineMapper,
                reportMapper,
                new FallbackRulePredictionEngine(),
                new FeatureBuildEngine(),
                new FallbackPredictionProperties(),
                new ObjectMapper(),
                stubNormalizationService(),
                new ProvincePolicyService());

        BacktestService.BacktestRequest request = new BacktestService.BacktestRequest();
        request.setProvinceCode("HB");
        request.setEvaluationYear(2025);
        request.setSubjectType("物理类");
        request.setStrategyMode("均衡型");
        request.setCandidateRanks(List.of(10_000));

        BacktestService.BacktestReportView view = service.run(request);

        // A组(稳,参考11000→真实12000 命中)、B组(保,参考13000→真实9000 未命中)、C组(垫,参考25000→真实26000 命中)
        assertThat(view.getProvinceCode()).isEqualTo("HB");
        assertThat(view.getEvaluatedPairs()).isEqualTo(3);
        assertThat(view.getOverallHitRate()).isEqualTo(0.6667);
        assertThat((String) view.getParamsSnapshot().get("dataLevel")).contains("group-level");
    }

    // ══════════════════ 测试数据 ══════════════════

    /** 一分一段人数全部缺失的换算服务：normalize 退化为原值，不影响既有断言。 */
    private RankNormalizationService stubNormalizationService() {
        return new RankNormalizationService(
                mock(com.gzly.mapper.ScoreRankGzMapper.class),
                mock(com.gzly.mapper.DataScoreRankMapper.class));
    }

    private List<com.gzly.entity.DataAdmissionGroupLine> groupTruthRows() {
        return List.of(
                groupRow("A", "G01", 2025, 12_000, 40),
                groupRow("B", "G02", 2025, 9_000, 30),
                groupRow("C", "G03", 2025, 26_000, 60));
    }

    private List<com.gzly.entity.DataAdmissionGroupLine> groupHistoryRows() {
        return List.of(
                groupRow("A", "G01", 2024, 11_000, 40),
                groupRow("A", "G01", 2023, 10_500, 40),
                groupRow("B", "G02", 2024, 13_000, 30),
                groupRow("C", "G03", 2024, 25_000, 60));
    }

    private com.gzly.entity.DataAdmissionGroupLine groupRow(String schoolId, String groupCode,
                                                            int year, int minRank, int planCount) {
        com.gzly.entity.DataAdmissionGroupLine row = new com.gzly.entity.DataAdmissionGroupLine();
        row.setProvinceCode("HB");
        row.setSchoolId(schoolId);
        row.setGroupCode(groupCode);
        row.setYear(year);
        row.setSubjectType("物理类");
        row.setMinRank(minRank);
        row.setMinScore(550);
        row.setPlanCount(planCount);
        return row;
    }

    /** 2025 年真实结果。含一个重复 key 验证"取最宽松批次"聚合。 */
    private List<MajorScoreGz> truthRows() {
        return List.of(
                row("A", "计算机", 2025, 12_000, 40),
                row("B", "临床医学", 2025, 9_000, 30),
                row("C", "土木工程", 2025, 14_000, 50),
                row("C", "土木工程", 2025, 13_500, 10), // 同 key 第二批次，聚合应取更宽松的 14000
                row("D", "法学", 2025, 8_500, 20),
                row("E", "护理学", 2025, 26_000, 60));
    }

    /** 2022-2024 历史参考（candidateRank=10000，均衡型区间：冲[6500,9500] 稳[9500,12000] 保[12000,18000] 垫[18000,30000]）。 */
    private List<MajorScoreGz> historyRows() {
        return List.of(
                // A：参考位次 11000 → 稳；2025 真实 12000 → 命中
                row("A", "计算机", 2024, 11_000, 40),
                row("A", "计算机", 2023, 10_500, 40),
                // B：参考位次 11500 → 稳；2025 真实 9000 → 未命中
                row("B", "临床医学", 2024, 11_500, 30),
                // C：参考位次 13000 → 保；2025 真实 14000 → 命中
                row("C", "土木工程", 2024, 13_000, 50),
                // D：参考位次 8000 → 冲；2025 真实 8500 → 未命中
                row("D", "法学", 2024, 8_000, 20),
                // E：参考位次 25000 → 垫；2025 真实 26000 → 命中
                row("E", "护理学", 2024, 25_000, 60),
                // F：参考位次 40000 → 超出全部区间，不参与
                row("F", "旅游管理", 2024, 40_000, 30),
                // G：参考位次 9000 → 冲，但无 2025 真实结果 → 计入覆盖率分母
                row("G", "软件工程", 2024, 9_000, 25));
    }

    private MajorScoreGz row(String schoolId, String major, int year, int minRank, int planCount) {
        MajorScoreGz row = new MajorScoreGz();
        row.setSchoolId(schoolId);
        row.setUniversityName("测试大学" + schoolId);
        row.setMajorName(major);
        row.setYear(year);
        row.setSubjectType("物理类");
        row.setMinRank(minRank);
        row.setMinScore(500);
        row.setPlanCount(planCount);
        return row;
    }
}
