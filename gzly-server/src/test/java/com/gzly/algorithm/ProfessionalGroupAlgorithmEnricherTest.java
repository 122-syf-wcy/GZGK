package com.gzly.algorithm;

import com.gzly.config.FallbackPredictionProperties;
import com.gzly.service.RankNormalizationService;
import com.gzly.service.VolunteerService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 阶段 2/3 并线验收：专业组条目必须获得与贵州链路同构的机会指数、校准概率（含退档折减）、
 * 概率定档、策略化排序、诊断与整表安全度——重构前这些字段恒为 0 / 写死文案。
 */
class ProfessionalGroupAlgorithmEnricherTest {

    private final ProfessionalGroupAlgorithmEnricher enricher = new ProfessionalGroupAlgorithmEnricher(
            new FeatureBuildEngine(),
            new FallbackRulePredictionEngine(),
            new VolunteerSortEngine(),
            new VolunteerDiagnosisEngine(),
            new RankNormalizationService(
                    mock(com.gzly.mapper.ScoreRankGzMapper.class),
                    mock(com.gzly.mapper.DataScoreRankMapper.class)),
            new FallbackPredictionProperties(),
            new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void enrichComputesChanceScoreFromGroupHistory() {
        // 考生 30000 名；保底组调档位次 52000（明显更靠后）→ 机会指数应显著大于 50
        VolunteerService.VolunteerItem safe = groupItem("A大学", "G01", "保", 52000,
                List.of(52000, 50000, 51000), List.of(60, 55, 58));
        // 冲档组调档位次 21000（明显更靠前）→ 机会指数应低于 50
        VolunteerService.VolunteerItem rush = groupItem("B大学", "G02", "冲", 21000,
                List.of(21000, 22000, 20500), List.of(30, 32, 31));

        ProfessionalGroupAlgorithmEnricher.Outcome outcome =
                enricher.enrich(new ArrayList<>(List.of(safe, rush)), request(), 30000, 45);

        VolunteerService.VolunteerItem enrichedSafe = byGroup(outcome.getItems(), "G01");
        VolunteerService.VolunteerItem enrichedRush = byGroup(outcome.getItems(), "G02");
        assertThat(enrichedSafe.getChanceScore()).isGreaterThan(60);
        assertThat(enrichedRush.getChanceScore()).isLessThan(50);
        assertThat(enrichedSafe.getChanceLevel()).isNotBlank();
        assertThat(enrichedSafe.getRiskLevel()).isNotEqualTo("需复核");
        assertThat(enrichedSafe.getRiskColor()).isEqualTo("green");
        assertThat(enrichedRush.getRiskColor()).isIn("red", "yellow");
        assertThat(enrichedSafe.getDataConfidence()).isGreaterThan(0);
        assertThat(enrichedSafe.getRankDiff()).isEqualTo(52000 - 30000);
        // 校准概率：等渗校准 × (1 − 退档先验 3%)，且必须低于未折减的校准上限
        assertThat(enrichedSafe.getCalibratedProbability()).isGreaterThan(80).isLessThan(97);
        assertThat(enrichedSafe.getRiskReason()).contains("退档");
    }

    @Test
    void enrichReclassifiesGradientByCalibratedProbability() {
        // 调档位次 80000 vs 考生 30000 → 机会指数约 96，校准概率约 0.946 ∈ [0.85,0.95) → 应归"保"而非预筛的"垫"
        VolunteerService.VolunteerItem floor = groupItem("C大学", "G03", "垫", 80000,
                List.of(80000, 78000), List.of(40, 40));
        VolunteerService.VolunteerItem rush = groupItem("B大学", "G02", "冲", 21000,
                List.of(21000, 22000), List.of(30, 30));
        VolunteerService.VolunteerItem stable = groupItem("A大学", "G01", "稳", 31000,
                List.of(31000, 30500), List.of(50, 50));

        ProfessionalGroupAlgorithmEnricher.Outcome outcome =
                enricher.enrich(new ArrayList<>(List.of(floor, rush, stable)), request(), 30000, 45);

        assertThat(byGroup(outcome.getItems(), "G03").getGradient()).isEqualTo("保");
        assertThat(byGroup(outcome.getItems(), "G02").getGradient()).isEqualTo("冲");
        assertThat(byGroup(outcome.getItems(), "G01").getGradient()).isEqualTo("稳");
        // 排序仍按 冲→稳→保→垫 梯度序 + finalScore，index 连续
        assertThat(outcome.getItems()).extracting(VolunteerService.VolunteerItem::getGradient)
                .containsExactly("冲", "稳", "保");
        assertThat(outcome.getItems()).extracting(VolunteerService.VolunteerItem::getIndex)
                .containsExactly(1, 2, 3);
        // recommendationScore 由排序引擎写回，不再是数据质量分
        assertThat(outcome.getItems()).allMatch(item -> item.getRecommendationScore() != 0);
    }

    @Test
    void enrichAppliesPreferenceTags() {
        VolunteerService.VolunteerItem item = groupItem("贵州大学", "G01", "稳", 31000,
                List.of(31000), List.of(50));
        item.setGroupMajors(List.of("计算机科学与技术", "软件工程"));
        item.setCity("贵阳");

        VolunteerService.GenerateRequest req = request();
        req.setPreferredMajors(List.of("计算机"));
        req.setPreferredRegions(List.of("贵阳"));

        ProfessionalGroupAlgorithmEnricher.Outcome outcome =
                enricher.enrich(new ArrayList<>(List.of(item)), req, 30000, 45);

        assertThat(outcome.getItems().get(0).getMatchTag()).isEqualTo("双匹配");
        assertThat(outcome.getItems().get(0).getMatchScore()).isEqualTo(100);
    }

    @Test
    void enrichProducesDiagnosisAndPortfolioSafety() {
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            VolunteerService.VolunteerItem item = groupItem("学校" + i, "G" + i, i < 3 ? "保" : "垫",
                    60000 + i * 2000, List.of(60000 + i * 2000, 59000 + i * 2000), List.of(50, 50));
            item.setDataConfidenceScore(80);
            items.add(item);
        }

        ProfessionalGroupAlgorithmEnricher.Outcome outcome = enricher.enrich(items, request(), 30000, 45);

        assertThat(outcome.getDiagnosis()).containsKeys("totalCount", "gradientCount", "warnings");
        assertThat(outcome.getPortfolioSafety()).isNotNull();
        assertThat(outcome.getPortfolioSafety().probability()).isGreaterThan(90);
        assertThat(outcome.getPortfolioSafety().safeTailCount()).isEqualTo(6);
    }

    // ══════════════════ 构造器 ══════════════════

    private VolunteerService.GenerateRequest request() {
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setProvinceCode("SC");
        req.setFirstSubject("物理");
        req.setStrategyMode("均衡型");
        return req;
    }

    private VolunteerService.VolunteerItem groupItem(String school, String groupCode, String gradient,
                                                     int latestMinRank, List<Integer> historyRanks,
                                                     List<Integer> planCounts) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setUniversityName(school);
        item.setGroupCode(groupCode);
        item.setGroupName(school + groupCode + "组");
        item.setMajorName(school + groupCode + "组");
        item.setGradient(gradient);
        item.setHistoryMinRank(latestMinRank);
        item.setDataConfidenceScore(75);
        item.setConfidenceLabel("中可信");
        List<VolunteerService.HistoryRecord> records = new ArrayList<>();
        for (int i = 0; i < historyRanks.size(); i++) {
            VolunteerService.HistoryRecord record = new VolunteerService.HistoryRecord();
            record.setYear(2025 - i);
            record.setMinRank(historyRanks.get(i));
            record.setPlanCount(i < planCounts.size() ? planCounts.get(i) : null);
            records.add(record);
        }
        item.setHistoryRecords(records);
        return item;
    }

    private VolunteerService.VolunteerItem byGroup(List<VolunteerService.VolunteerItem> items, String groupCode) {
        return items.stream().filter(i -> groupCode.equals(i.getGroupCode())).findFirst().orElseThrow();
    }
}
