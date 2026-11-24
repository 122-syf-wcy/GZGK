package com.gzgk.core.sandbox;

import com.gzgk.common.algorithm.LogRankChanceModel;
import com.gzgk.common.algorithm.PortfolioMonteCarloSimulator;
import com.gzgk.common.api.BizException;
import com.gzgk.common.id.UlidGenerator;
import com.gzgk.core.catalog.UniversityCatalogService;
import com.gzgk.core.catalog.UniversityRecord;
import com.gzgk.core.policy.ProvincePolicyModels.ProvincePolicyView;
import com.gzgk.core.policy.ProvincePolicyService;
import com.gzgk.core.recommend.RecommendModels.*;
import com.gzgk.core.recommend.RecommendPipelineService;
import com.gzgk.core.scoreline.MajorScoreRecord;
import com.gzgk.core.scoreline.ScoreLineService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Deterministic Sandbox Simulation & Optimistic-Locked HITL Mutation Engine.
 *
 * Powers the Agent's signature capability:
 * 1. `simulate`: Pure in-memory what-if analysis running 2000-iter Monte Carlo re-evaluation
 *    without mutating persisted state.
 * 2. `apply`: Optimistic-locked (`expectedVersion`) plan update with immutable audit trail
 *    (`MutationLogEntry`) supporting one-click rollback from the Web UI.
 */
@Service
public class PlanSandboxService {

    private final RecommendPipelineService recommendPipelineService;
    private final UniversityCatalogService universityCatalogService;
    private final ScoreLineService scoreLineService;
    private final ProvincePolicyService provincePolicyService;

    private final Map<String, Deque<MutationLogEntry>> mutationHistory = new ConcurrentHashMap<>();

    public PlanSandboxService(
            RecommendPipelineService recommendPipelineService,
            UniversityCatalogService universityCatalogService,
            ScoreLineService scoreLineService,
            ProvincePolicyService provincePolicyService
    ) {
        this.recommendPipelineService = recommendPipelineService;
        this.universityCatalogService = universityCatalogService;
        this.scoreLineService = scoreLineService;
        this.provincePolicyService = provincePolicyService;
    }

    public PlanSnapshot getPlanOrThrow(String planId) {
        return recommendPipelineService.findHotPlan(planId)
                .orElseThrow(() -> new BizException(404, "未找到志愿方案：" + planId));
    }

    public List<PlanSnapshot> listRecentPlans() {
        return recommendPipelineService.listRecentHotPlans();
    }

    public SimulationResult simulate(String planId, List<MutationOp> operations) {
        long t0 = System.nanoTime();
        PlanSnapshot base = getPlanOrThrow(planId);
        ProvincePolicyView policy = provincePolicyService.getPolicy(base.provinceCode());

        List<PlanItem> working = new ArrayList<>(base.items().stream().map(i -> i.withHighlighted(false)).toList());
        List<DiffEntry> diffs = new ArrayList<>();

        for (MutationOp op : operations) {
            if ("REMOVE".equalsIgnoreCase(op.opType())) {
                int idx = findIndex(working, op.targetIndex());
                if (idx >= 0) {
                    PlanItem removed = working.remove(idx);
                    diffs.add(new DiffEntry("REMOVE", removed.volunteerIndex(), removed.universityName(),
                            removed.majorName(), removed.gradient(), removed.chanceScore(),
                            op.reason() != null && !op.reason().isBlank() ? op.reason() : "移除高风险/缩招志愿"));
                }
            } else if ("ADD".equalsIgnoreCase(op.opType())) {
                PlanItem added = materializeCandidateItem(base, policy, op);
                int insertAt = op.newIndex() > 0 ? Math.min(op.newIndex() - 1, working.size()) : working.size();
                working.add(insertAt, added);
                diffs.add(new DiffEntry("ADD", insertAt + 1, added.universityName(), added.majorName(),
                        added.gradient(), added.chanceScore(),
                        op.reason() != null && !op.reason().isBlank() ? op.reason() : "新增匹配稳保志愿"));
            } else if ("REPLACE".equalsIgnoreCase(op.opType())) {
                int idx = findIndex(working, op.targetIndex());
                if (idx >= 0) {
                    PlanItem oldItem = working.remove(idx);
                    diffs.add(new DiffEntry("REMOVE", oldItem.volunteerIndex(), oldItem.universityName(),
                            oldItem.majorName(), oldItem.gradient(), oldItem.chanceScore(), "替换原高风险条目"));
                    PlanItem replacement = materializeCandidateItem(base, policy, op);
                    working.add(idx, replacement);
                    diffs.add(new DiffEntry("ADD", idx + 1, replacement.universityName(), replacement.majorName(),
                            replacement.gradient(), replacement.chanceScore(),
                            op.reason() != null && !op.reason().isBlank() ? op.reason() : "沙箱优选替换"));
                }
            } else if ("REORDER".equalsIgnoreCase(op.opType())) {
                int idx = findIndex(working, op.targetIndex());
                if (idx >= 0 && op.newIndex() > 0) {
                    PlanItem moved = working.remove(idx);
                    int dest = Math.min(op.newIndex() - 1, working.size());
                    working.add(dest, moved.withHighlighted(true));
                    diffs.add(new DiffEntry("MOVE", dest + 1, moved.universityName(), moved.majorName(),
                            moved.gradient(), moved.chanceScore(), "调整志愿排序位置"));
                }
            }
        }

        // Re-index cleanly 1..N
        List<PlanItem> reindexed = new ArrayList<>(working.size());
        for (int i = 0; i < working.size(); i++) {
            reindexed.add(working.get(i).withIndex(i + 1));
        }

        GradientCounts afterGrad = RecommendPipelineService.countGradients(reindexed);
        double afterHitRate = PortfolioMonteCarloSimulator.listHitRate(
                reindexed.stream().map(PlanItem::calibratedProb).toList());
        int manualReview = (int) reindexed.stream().filter(PlanItem::needsManualReview).count();
        int shrinkCount = (int) reindexed.stream().filter(i -> i.planChangeRate() <= -0.25).count();
        List<String> diagnosis = RecommendPipelineService.buildDiagnosisNotes(afterGrad, afterHitRate, manualReview, shrinkCount, policy);
        long simMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);

        boolean cliffResolved = afterHitRate >= base.listHitRate();
        return new SimulationResult(
                base.planId(), base.version(), base.gradientCounts(), afterGrad,
                base.listHitRate(), afterHitRate, Math.round((afterHitRate - base.listHitRate()) * 10000.0) / 10000.0,
                cliffResolved, diagnosis, diffs, operations, reindexed, simMs
        );
    }

    public ApplyResult applyMutation(String planId, int expectedVersion, String threadId, String summary, List<MutationOp> operations) {
        PlanSnapshot current = getPlanOrThrow(planId);
        if (expectedVersion > 0 && current.version() != expectedVersion) {
            throw new BizException(409, "方案版本已更新（当前 v" + current.version() + "，期望 v" + expectedVersion + "），请刷新后重试");
        }
        SimulationResult sim = simulate(planId, operations);
        int nextVersion = current.version() + 1;
        int manualCount = (int) sim.simulatedItems().stream().filter(PlanItem::needsManualReview).count();
        int shrinkCount = (int) sim.simulatedItems().stream().filter(i -> i.planChangeRate() <= -0.25).count();

        PlanSnapshot updated = new PlanSnapshot(
                current.planId(), nextVersion, current.provinceCode(), current.provinceName(),
                current.volunteerUnitType(), current.targetBatch(), current.totalScore(), current.provinceRank(),
                current.firstSubject(), current.resubjects(), current.strategyMode(), current.fingerprint(),
                current.readinessStatus(), sim.afterGradient(), sim.afterListHitRate(),
                manualCount, shrinkCount, sim.diagnosisNotes(), current.disclaimer(), current.metrics(),
                sim.simulatedItems(), current.extraMeta()
        );
        recommendPipelineService.saveHotPlan(updated);

        String mutationId = UlidGenerator.nextUlid();
        MutationLogEntry entry = new MutationLogEntry(
                mutationId, planId, current.version(), nextVersion,
                threadId == null ? "" : threadId,
                summary == null || summary.isBlank() ? "AI 沙箱调优变更" : summary,
                current.listHitRate(), updated.listHitRate(), operations,
                current.items(), updated.items(), Instant.now()
        );
        mutationHistory.computeIfAbsent(planId, k -> new ConcurrentLinkedDeque<>()).addFirst(entry);
        return new ApplyResult(true, planId, nextVersion, mutationId, updated.listHitRate(), updated.gradientCounts(), updated, "已应用方案变更至 v" + nextVersion);
    }

    public ApplyResult rollbackLast(String planId) {
        Deque<MutationLogEntry> stack = mutationHistory.get(planId);
        if (stack == null || stack.isEmpty()) {
            throw new BizException(400, "当前方案没有可撤销的变更记录");
        }
        PlanSnapshot current = getPlanOrThrow(planId);
        MutationLogEntry last = stack.pop();
        int nextVersion = current.version() + 1;
        GradientCounts restoredGrad = RecommendPipelineService.countGradients(last.beforeSnapshot());
        double restoredHit = last.hitRateBefore();
        ProvincePolicyView policy = provincePolicyService.getPolicy(current.provinceCode());
        int manualCount = (int) last.beforeSnapshot().stream().filter(PlanItem::needsManualReview).count();
        int shrinkCount = (int) last.beforeSnapshot().stream().filter(i -> i.planChangeRate() <= -0.25).count();
        List<String> notes = RecommendPipelineService.buildDiagnosisNotes(restoredGrad, restoredHit, manualCount, shrinkCount, policy);

        PlanSnapshot restored = new PlanSnapshot(
                current.planId(), nextVersion, current.provinceCode(), current.provinceName(),
                current.volunteerUnitType(), current.targetBatch(), current.totalScore(), current.provinceRank(),
                current.firstSubject(), current.resubjects(), current.strategyMode(), current.fingerprint(),
                current.readinessStatus(), restoredGrad, restoredHit, manualCount, shrinkCount,
                notes, current.disclaimer(), current.metrics(), last.beforeSnapshot(), current.extraMeta()
        );
        recommendPipelineService.saveHotPlan(restored);
        return new ApplyResult(true, planId, nextVersion, UlidGenerator.nextUlid(), restoredHit, restoredGrad, restored, "已撤销上一次变更（当前 v" + nextVersion + "）");
    }

    public List<MutationLogEntry> listMutations(String planId) {
        Deque<MutationLogEntry> stack = mutationHistory.get(planId);
        return stack == null ? List.of() : new ArrayList<>(stack);
    }

    private PlanItem materializeCandidateItem(PlanSnapshot base, ProvincePolicyView policy, MutationOp op) {
        String schoolHint = op.universityName() == null ? "" : op.universityName();
        String majorHint = op.majorName() == null ? "计算机科学与技术" : op.majorName();
        List<MajorScoreRecord> matches = scoreLineService.querySchoolHistory(base.provinceCode(), schoolHint, majorHint, base.firstSubject() + "类", 5);

        UniversityRecord uni = null;
        if (op.schoolId() != null && !op.schoolId().isBlank()) {
            uni = universityCatalogService.findBySchoolId(op.schoolId()).orElse(null);
        }
        if (uni == null && !schoolHint.isBlank()) {
            var found = universityCatalogService.search(schoolHint, "", "", 1, 1);
            if (!found.items().isEmpty()) {
                uni = found.items().get(0);
            }
        }

        int refRank = !matches.isEmpty() && matches.get(0).minRank() != null
                ? matches.get(0).minRank()
                : (int) Math.round(base.provinceRank() * 1.12);
        int refScore = !matches.isEmpty() && matches.get(0).minScore() != null
                ? matches.get(0).minScore()
                : Math.max(380, base.totalScore() - 4);
        int planCount = !matches.isEmpty() && matches.get(0).planCount() != null ? matches.get(0).planCount() : 28;
        var pred = LogRankChanceModel.predict(base.provinceRank(), refRank, 0.06, 0.0, 90, 0.1, policy.sigmaMin());

        String finalSchoolId = uni != null ? uni.schoolId() : (op.schoolId() != null ? op.schoolId() : "141");
        String finalSchoolName = uni != null ? uni.name() : (!schoolHint.isBlank() ? schoolHint : "重庆邮电大学");
        List<Integer> ranks3y = matches.stream().map(MajorScoreRecord::minRank).filter(r -> r != null && r > 0).limit(3).toList();
        if (ranks3y.isEmpty()) {
            ranks3y = List.of(refRank, (int) (refRank * 1.03), (int) (refRank * 0.98));
        }

        return new PlanItem(
                0,
                finalSchoolId + ":" + Math.abs(majorHint.hashCode()),
                pred.gradient(),
                finalSchoolId,
                finalSchoolName,
                "",
                majorHint,
                uni != null ? uni.province() : "重庆",
                uni != null ? uni.city() : "重庆市",
                uni != null ? uni.tags() : List.of("公办"),
                uni != null && !uni.natureName().isBlank() ? uni.natureName() : "公办",
                "5,600元/年",
                refScore,
                refRank,
                pred.predictedMinRank(),
                planCount,
                0.0,
                pred.chanceScore(),
                pred.chanceLevel(),
                pred.calibratedProb(),
                pred.riskLevel(),
                false,
                "",
                uni != null ? uni.officialCharterUrl() : "https://zs.cqupt.edu.cn/",
                ranks3y,
                false,
                true
        );
    }

    private static int findIndex(List<PlanItem> list, int targetVolunteerIndex) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).volunteerIndex() == targetVolunteerIndex) {
                return i;
            }
        }
        return -1;
    }

    public record MutationOp(
            String opType,          // REMOVE | ADD | REPLACE | REORDER
            int targetIndex,
            int newIndex,
            String schoolId,
            String universityName,
            String majorName,
            String reason
    ) {
    }

    public record DiffEntry(
            String action,
            int volunteerIndex,
            String universityName,
            String majorName,
            String gradient,
            int chanceScore,
            String summary
    ) {
    }

    public record SimulationResult(
            String planId,
            int baseVersion,
            GradientCounts beforeGradient,
            GradientCounts afterGradient,
            double beforeListHitRate,
            double afterListHitRate,
            double hitRateDelta,
            boolean gradientCliffResolved,
            List<String> diagnosisNotes,
            List<DiffEntry> diffEntries,
            List<MutationOp> operations,
            List<PlanItem> simulatedItems,
            long simulationTimeMs
    ) {
    }

    public record MutationLogEntry(
            String mutationId,
            String planId,
            int fromVersion,
            int toVersion,
            String threadId,
            String summary,
            double hitRateBefore,
            double hitRateAfter,
            List<MutationOp> operations,
            List<PlanItem> beforeSnapshot,
            List<PlanItem> afterSnapshot,
            Instant createdAt
    ) {
    }

    public record ApplyResult(
            boolean applied,
            String planId,
            int newVersion,
            String mutationLogId,
            double newListHitRate,
            GradientCounts newGradient,
            PlanSnapshot updatedPlan,
            String message
    ) {
    }
}
