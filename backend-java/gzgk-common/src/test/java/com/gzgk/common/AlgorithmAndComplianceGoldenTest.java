package com.gzgk.common;

import com.gzgk.common.algorithm.LogRankChanceModel;
import com.gzgk.common.algorithm.PortfolioMonteCarloSimulator;
import com.gzgk.common.algorithm.ProbabilityCalibration;
import com.gzgk.common.algorithm.SubjectMatcher;
import com.gzgk.common.compliance.ComplianceTextSanitizer;
import com.gzgk.common.id.UlidGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmAndComplianceGoldenTest {

    @Test
    void probabilityCalibrationIsMonotonicAndBounded() {
        double prev = 0;
        for (int s = 0; s <= 100; s++) {
            double p = ProbabilityCalibration.fromChanceScore(s);
            assertTrue(p >= 0.02 && p <= 0.97, "Out of bounds at score " + s);
            assertTrue(p >= prev, "Non-monotonic at score " + s);
            prev = p;
        }
        assertEquals(0.02, ProbabilityCalibration.fromChanceScore(0), 1e-6);
        assertEquals(0.55, ProbabilityCalibration.fromChanceScore(55), 1e-6);
        assertEquals(0.97, ProbabilityCalibration.fromChanceScore(100), 1e-6);
    }

    @Test
    void monteCarloSimulatorIsDeterministicAndSensitiveToPortfolioStrength() {
        List<Double> risky = List.of(0.15, 0.20, 0.25);
        List<Double> safer = List.of(0.15, 0.20, 0.25, 0.72, 0.88, 0.94);
        double hitRisky = PortfolioMonteCarloSimulator.listHitRate(risky);
        double hitSafer = PortfolioMonteCarloSimulator.listHitRate(safer);
        assertEquals(hitRisky, PortfolioMonteCarloSimulator.listHitRate(risky), 1e-9);
        assertTrue(hitSafer > 0.98, "Expected strong portfolio hit rate > 0.98, got " + hitSafer);
        assertTrue(hitSafer > hitRisky);
    }

    @Test
    void logRankModelPenalizesShrinkingPlans() {
        var stable = LogRankChanceModel.predict(8450, 8800, 0.08, 0.0, 90, 0.1, 0.06);
        var shrinking = LogRankChanceModel.predict(8450, 8800, 0.08, -0.45, 90, 0.1, 0.06);
        assertTrue(stable.chanceScore() > shrinking.chanceScore());
        assertTrue(stable.internalChanceScore() - shrinking.internalChanceScore() >= 0.14);
    }

    @Test
    void subjectMatcherHandles312Logic() {
        assertTrue(SubjectMatcher.matches312("物理", List.of("化学", "生物"), "物理类", "化学"));
        assertTrue(SubjectMatcher.matches312("物理", List.of("化学", "生物"), "物理类", "化学和生物"));
        assertFalse(SubjectMatcher.matches312("物理", List.of("地理", "生物"), "物理类", "化学"));
        assertTrue(SubjectMatcher.matches312("历史", List.of("政治", "地理"), "历史类", "思想政治"));
        assertFalse(SubjectMatcher.matches312("历史", List.of("政治", "地理"), "物理类", "不限"));
    }

    @Test
    void complianceSanitizerLoadsSharedContractAndBlocksHardTerms() {
        Path contractPath = Path.of("../../contracts/compliance-terms.json").normalize();
        ComplianceTextSanitizer sanitizer = ComplianceTextSanitizer.fromJsonFile(contractPath);
        assertTrue(sanitizer.getHardTerms().size() >= 38);

        var res = sanitizer.sanitize("该方案100%录取，保录取且保证不滑档，录取概率高达99%，稳了！");
        assertTrue(res.modified());
        assertFalse(res.text().contains("保录取"));
        assertFalse(res.text().contains("100%录取"));
        assertFalse(res.text().contains("保证不滑档"));
        assertFalse(res.text().contains("录取概率"));
        assertFalse(res.text().contains("稳了"));
        assertTrue(res.hitTerms().size() >= 4);
    }

    @Test
    void ulidGeneratorProducesUnique26CharIds() {
        String a = UlidGenerator.nextUlid();
        String b = UlidGenerator.nextUlid();
        assertEquals(26, a.length());
        assertEquals(26, b.length());
        assertNotEquals(a, b);
    }
}
