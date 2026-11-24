package com.gzgk.core.grpc;

import com.gzgk.contracts.core.v1.*;
import com.gzgk.core.catalog.UniversityCatalogService;
import com.gzgk.core.recommend.RecommendModels.PlanItem;
import com.gzgk.core.recommend.RecommendModels.PlanSnapshot;
import com.gzgk.core.sandbox.PlanSandboxService;
import com.gzgk.core.sandbox.PlanSandboxService.MutationOp;
import com.gzgk.core.scoreline.MajorScoreRecord;
import com.gzgk.core.scoreline.ScoreLineService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Internal gRPC Server exposing deterministic Java core tools & Monte Carlo sandbox
 * to Python LangGraph Multi-Agent (`gzgk-agent`).
 */
@Service
public class CoreToolsGrpcService extends CoreToolsServiceGrpc.CoreToolsServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CoreToolsGrpcService.class);

    private final PlanSandboxService sandboxService;
    private final ScoreLineService scoreLineService;
    private final UniversityCatalogService universityCatalogService;
    private final boolean serverEnabled;
    private final int grpcPort;
    private Server server;

    public CoreToolsGrpcService(
            PlanSandboxService sandboxService,
            ScoreLineService scoreLineService,
            UniversityCatalogService universityCatalogService,
            @Value("${gzgk.core.grpc-server-enabled:true}") boolean serverEnabled,
            @Value("${gzgk.core.grpc-server-port:9090}") int grpcPort
    ) {
        this.sandboxService = sandboxService;
        this.scoreLineService = scoreLineService;
        this.universityCatalogService = universityCatalogService;
        this.serverEnabled = serverEnabled;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void start() {
        if (!serverEnabled) {
            return;
        }
        try {
            this.server = ServerBuilder.forPort(grpcPort)
                    .addService(this)
                    .build()
                    .start();
            log.info("[grpc] CoreToolsService listening on port {}", grpcPort);
        } catch (Exception e) {
            log.warn("[grpc] Failed to bind gRPC port {}: {}", grpcPort, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Override
    public void getPlanSummary(GetPlanSummaryRequest req, StreamObserver<PlanSummaryResponse> observer) {
        try {
            PlanSnapshot plan = sandboxService.getPlanOrThrow(req.getPlanId());
            observer.onNext(PlanSummaryResponse.newBuilder()
                    .setPlanId(plan.planId())
                    .setVersion(plan.version())
                    .setProvinceCode(plan.provinceCode())
                    .setProvinceName(plan.provinceName())
                    .setVolunteerUnitType(plan.volunteerUnitType())
                    .setTargetBatch(plan.targetBatch())
                    .setTotalScore(plan.totalScore())
                    .setProvinceRank(plan.provinceRank())
                    .setFirstSubject(plan.firstSubject())
                    .addAllResubjects(plan.resubjects())
                    .setStrategyMode(plan.strategyMode())
                    .setTotalItems(plan.items().size())
                    .setGradientCounts(toProtoGradient(plan.gradientCounts()))
                    .setListHitRate(plan.listHitRate())
                    .setManualReviewCount(plan.manualReviewCount())
                    .setShrinkWarningCount(plan.shrinkWarningCount())
                    .setReadinessStatus(plan.readinessStatus())
                    .build());
            observer.onCompleted();
        } catch (Exception ex) {
            observer.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPlanItems(GetPlanItemsRequest req, StreamObserver<GetPlanItemsResponse> observer) {
        try {
            PlanSnapshot plan = sandboxService.getPlanOrThrow(req.getPlanId());
            String grad = req.getGradient().trim();
            String kw = req.getKeyword().trim().toLowerCase(Locale.ROOT);
            int limit = req.getLimit() > 0 ? Math.min(96, req.getLimit()) : 20;

            List<PlanItem> matched = plan.items().stream()
                    .filter(i -> grad.isEmpty() || grad.equals(i.gradient()))
                    .filter(i -> kw.isEmpty()
                            || i.universityName().toLowerCase(Locale.ROOT).contains(kw)
                            || i.majorName().toLowerCase(Locale.ROOT).contains(kw)
                            || i.city().toLowerCase(Locale.ROOT).contains(kw))
                    .filter(i -> !req.getOnlyManualReview() || i.needsManualReview())
                    .filter(i -> !req.getOnlyShrinkWarning() || i.planChangeRate() <= -0.25)
                    .toList();

            GetPlanItemsResponse.Builder builder = GetPlanItemsResponse.newBuilder()
                    .setPlanId(plan.planId())
                    .setTotalMatched(matched.size());
            matched.stream().limit(limit).forEach(i -> builder.addItems(toProtoItem(i)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception ex) {
            observer.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void estimateRank(EstimateRankRequest req, StreamObserver<EstimateRankResponse> observer) {
        var est = scoreLineService.estimateRank(req.getProvinceCode(), req.getYear(), req.getSubjectType(), req.getScore());
        observer.onNext(EstimateRankResponse.newBuilder()
                .setProvinceCode(est.provinceCode())
                .setYear(est.year())
                .setSubjectType(est.subjectType())
                .setScore(est.score())
                .setRankLow(est.rankLow())
                .setRankHigh(est.rankHigh())
                .setSegmentCount(est.segmentCount())
                .setCumulativeCount(est.cumulativeCount())
                .setBeatPercentage(est.beatPercentage())
                .setSourceName(est.sourceName())
                .setSourceUrl(est.sourceUrl())
                .setOfficialReady(est.officialReady())
                .setLatencyMicros(est.latencyMicros())
                .build());
        observer.onCompleted();
    }

    @Override
    public void getSchoolMajorHistory(SchoolMajorHistoryRequest req, StreamObserver<SchoolMajorHistoryResponse> observer) {
        int limit = req.getLimit() > 0 ? Math.min(50, req.getLimit()) : 20;
        List<MajorScoreRecord> rows = scoreLineService.querySchoolHistory(
                req.getProvinceCode(), req.getSchoolName(), req.getMajorKeyword(), req.getSubjectType(), limit);

        SchoolMajorHistoryResponse.Builder resp = SchoolMajorHistoryResponse.newBuilder().setTotalCount(rows.size());
        for (MajorScoreRecord r : rows) {
            var uni = universityCatalogService.findBySchoolId(r.schoolId()).orElse(null);
            SchoolMajorHistoryRecord.Builder rec = SchoolMajorHistoryRecord.newBuilder()
                    .setSchoolId(r.schoolId())
                    .setUniversityName(r.universityName())
                    .setMajorName(r.majorName())
                    .setYear(r.year())
                    .setSubjectType(r.subjectType())
                    .setBatch(r.batch())
                    .setResubjectRequirement(r.resubjectRequirement())
                    .setMinScore(r.minScore() != null ? r.minScore() : 0)
                    .setMinRank(r.minRank() != null ? r.minRank() : 0)
                    .setPlanCount(r.planCount() != null ? r.planCount() : 0);
            if (uni != null) {
                rec.setProvince(uni.province())
                        .setCity(uni.city())
                        .addAllTags(uni.tags())
                        .setOfficialCharterUrl(uni.officialCharterUrl());
            }
            resp.addRecords(rec.build());
        }
        observer.onNext(resp.build());
        observer.onCompleted();
    }

    @Override
    public void simulatePlanAdjustment(SimulatePlanAdjustmentRequest req, StreamObserver<SimulatePlanAdjustmentResponse> observer) {
        try {
            List<MutationOp> ops = req.getOperationsList().stream().map(CoreToolsGrpcService::fromProtoOp).toList();
            var sim = sandboxService.simulate(req.getPlanId(), ops);
            SimulatePlanAdjustmentResponse.Builder resp = SimulatePlanAdjustmentResponse.newBuilder()
                    .setPlanId(sim.planId())
                    .setBaseVersion(sim.baseVersion())
                    .setBeforeGradient(toProtoGradient(sim.beforeGradient()))
                    .setAfterGradient(toProtoGradient(sim.afterGradient()))
                    .setBeforeListHitRate(sim.beforeListHitRate())
                    .setAfterListHitRate(sim.afterListHitRate())
                    .setHitRateDelta(sim.hitRateDelta())
                    .setGradientCliffResolved(sim.gradientCliffResolved())
                    .addAllDiagnosisNotes(sim.diagnosisNotes())
                    .addAllNormalizedOperations(req.getOperationsList())
                    .setSimulationTimeMs(sim.simulationTimeMs());
            for (var d : sim.diffEntries()) {
                resp.addDiffEntries(DiffChangeEntry.newBuilder()
                        .setAction(d.action())
                        .setVolunteerIndex(d.volunteerIndex())
                        .setUniversityName(d.universityName())
                        .setMajorName(d.majorName())
                        .setGradient(d.gradient())
                        .setChanceScore(d.chanceScore())
                        .setSummary(d.summary())
                        .build());
            }
            observer.onNext(resp.build());
            observer.onCompleted();
        } catch (Exception ex) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void applyPlanMutation(ApplyPlanMutationRequest req, StreamObserver<ApplyPlanMutationResponse> observer) {
        try {
            List<MutationOp> ops = req.getOperationsList().stream().map(CoreToolsGrpcService::fromProtoOp).toList();
            var res = sandboxService.applyMutation(
                    req.getPlanId(), req.getExpectedVersion(), req.getThreadId(), req.getSummary(), ops);
            observer.onNext(ApplyPlanMutationResponse.newBuilder()
                    .setApplied(res.applied())
                    .setPlanId(res.planId())
                    .setNewVersion(res.newVersion())
                    .setMutationLogId(res.mutationLogId())
                    .setNewListHitRate(res.newListHitRate())
                    .setNewGradient(toProtoGradient(res.newGradient()))
                    .setMessage(res.message())
                    .build());
            observer.onCompleted();
        } catch (Exception ex) {
            observer.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    private static MutationOp fromProtoOp(MutationOperation op) {
        String type = switch (op.getOpType()) {
            case MUTATION_OP_REMOVE -> "REMOVE";
            case MUTATION_OP_ADD -> "ADD";
            case MUTATION_OP_REPLACE -> "REPLACE";
            case MUTATION_OP_REORDER -> "REORDER";
            default -> "ADD";
        };
        return new MutationOp(type, op.getTargetIndex(), op.getNewIndex(),
                op.getSchoolId(), op.getUniversityName(), op.getMajorName(), op.getReason());
    }

    private static GradientCounts toProtoGradient(com.gzgk.core.recommend.RecommendModels.GradientCounts g) {
        return GradientCounts.newBuilder()
                .setSprint(g.sprint()).setSteady(g.steady()).setSafe(g.safe()).setPad(g.pad()).build();
    }

    private static PlanItemProto toProtoItem(PlanItem i) {
        return PlanItemProto.newBuilder()
                .setVolunteerIndex(i.volunteerIndex())
                .setItemId(i.itemId())
                .setGradient(i.gradient())
                .setSchoolId(i.schoolId())
                .setUniversityName(i.universityName())
                .setMajorCode(i.majorCode())
                .setMajorName(i.majorName())
                .setProvince(i.province())
                .setCity(i.city())
                .addAllSchoolTags(i.schoolTags())
                .setSchoolNature(i.schoolNature())
                .setTuition(i.tuition())
                .setLatestMinScore(i.latestMinScore())
                .setLatestMinRank(i.latestMinRank())
                .setPredictedMinRank(i.predictedMinRank())
                .setPlanCount(i.planCount())
                .setPlanChangeRate(i.planChangeRate())
                .setChanceScore(i.chanceScore())
                .setChanceLevel(i.chanceLevel())
                .setCalibratedProb(i.calibratedProb())
                .setRiskLevel(i.riskLevel())
                .setNeedsManualReview(i.needsManualReview())
                .setManualReviewReason(i.manualReviewReason())
                .setOfficialCharterUrl(i.officialCharterUrl())
                .addAllRecent3YRanks(i.recent3yRanks())
                .setLocked(i.locked())
                .build();
    }
}
