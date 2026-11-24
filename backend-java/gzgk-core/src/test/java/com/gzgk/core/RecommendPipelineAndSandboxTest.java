package com.gzgk.core;

import com.gzgk.common.api.BizException;
import com.gzgk.core.catalog.UniversityCatalogService;
import com.gzgk.core.policy.ProvincePolicyService;
import com.gzgk.core.recommend.RecommendModels.GeneratePlanRequest;
import com.gzgk.core.recommend.RecommendPipelineService;
import com.gzgk.core.sandbox.PlanSandboxService;
import com.gzgk.core.sandbox.PlanSandboxService.MutationOp;
import com.gzgk.core.scoreline.ScoreLineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "gzgk.core.storage-mode=local-files",
        "gzgk.core.grpc-server-enabled=false",
        "gzgk.core.ml-grpc-enabled=false",
        "gzgk.core.redis-enabled=false",
        "gzgk.core.kafka-enabled=false"
})
class RecommendPipelineAndSandboxTest {

    @Autowired
    private UniversityCatalogService universityCatalogService;

    @Autowired
    private ScoreLineService scoreLineService;

    @Autowired
    private ProvincePolicyService provincePolicyService;

    @Autowired
    private RecommendPipelineService recommendPipelineService;

    @Autowired
    private PlanSandboxService planSandboxService;

    @Test
    void fullStackRealDatasetGenerationCacheReuseAndHitlSandboxLifecycle() {
        // 1. Verify real dataset warm-up
        assertTrue(universityCatalogService.totalCount() >= 2000, "Should load 2,198 real universities");
        var rankEst = scoreLineService.estimateRank("GZ", 2025, "物理类", 586);
        assertTrue(rankEst.officialReady());
        assertTrue(rankEst.rankLow() > 0 && rankEst.rankHigh() >= rankEst.rankLow());
        assertEquals("FULL", provincePolicyService.getPolicy("GZ").readinessStatus());

        // 2. First student generates 96-volunteer plan
        GeneratePlanRequest reqStudentA = new GeneratePlanRequest(
                "GZ", 2026, 586, rankEst.rankHigh(), "物理", List.of("化学", "生物"),
                "balanced", List.of("计算机", "电子"), List.of(), List.of("重庆", "贵州"),
                false, false, 10000, true, "2026.1", "student-A"
        );
        var planA = recommendPipelineService.generate(reqStudentA);
        assertNotNull(planA.planId());
        assertEquals(96, planA.items().size());
        assertTrue(planA.listHitRate() > 0.90);
        assertEquals("rule-log-rank-fallback", planA.metrics().predictionEngine());

        // 3. Different student with identical score/rank/preferences MUST hit normalized fingerprint cache!
        GeneratePlanRequest reqStudentB = new GeneratePlanRequest(
                "GZ", 2026, 586, rankEst.rankHigh(), "物理", List.of("生物", "化学"),
                "balanced", List.of("电子", "计算机"), List.of(), List.of("贵州", "重庆"),
                false, false, 10000, true, "2026.1", "student-B"
        );
        var planB = recommendPipelineService.generate(reqStudentB);
        assertNotEquals(planA.planId(), planB.planId());
        assertEquals(planA.fingerprint(), planB.fingerprint());
        assertTrue(planB.metrics().cacheHit(), "Normalized fingerprint must enable cross-student cache sharing");

        // 4. Pure Sandbox Simulation (HITL what-if) must NOT mutate persisted version
        List<MutationOp> ops = List.of(
                new MutationOp("REMOVE", 5, 0, "", "", "", "移除高波动冲档"),
                new MutationOp("ADD", 0, 19, "141", "重庆邮电大学", "计算机科学与技术", "补充川渝计算机稳档")
        );
        var sim = planSandboxService.simulate(planA.planId(), ops);
        assertEquals(1, sim.baseVersion());
        assertEquals(2, sim.diffEntries().size());
        assertEquals(1, planSandboxService.getPlanOrThrow(planA.planId()).version());

        // 5. Optimistic-locked HITL Apply bumps version 1 -> 2 and rejects stale version 1 with 409
        var applied = planSandboxService.applyMutation(planA.planId(), 1, "thread-001", "沙箱替换川渝计算机", ops);
        assertTrue(applied.applied());
        assertEquals(2, applied.newVersion());
        assertTrue(applied.updatedPlan().items().stream().anyMatch(i -> i.universityName().contains("重庆邮电")));

        BizException conflict = assertThrows(BizException.class, () ->
                planSandboxService.applyMutation(planA.planId(), 1, "thread-001", "stale", ops));
        assertEquals(409, conflict.getCode());

        // 6. One-click rollback restores original items and bumps version 2 -> 3
        var rolledBack = planSandboxService.rollbackLast(planA.planId());
        assertEquals(3, rolledBack.newVersion());
        assertEquals(planA.items().size(), rolledBack.updatedPlan().items().size());
    }
}
