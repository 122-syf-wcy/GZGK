package com.gzgk.core.recommend;

import com.gzgk.common.algorithm.LogRankChanceModel;
import com.gzgk.contracts.ml.v1.BatchPredictRequest;
import com.gzgk.contracts.ml.v1.BatchPredictResponse;
import com.gzgk.contracts.ml.v1.CandidateContext;
import com.gzgk.contracts.ml.v1.MlPredictServiceGrpc;
import com.gzgk.contracts.ml.v1.PredictFeatureItem;
import com.gzgk.contracts.ml.v1.PredictItemResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC client facade to `gzgk-ml` guarded by Resilience4j CircuitBreaker + 300ms deadline.
 * Whenever ML service is disabled, slow, or tripped, seamlessly degrades to deterministic
 * `LogRankChanceModel` (Brier 0.0155) with zero user-facing errors.
 */
@Component
public class MlGrpcClientFacade {

    private static final Logger log = LoggerFactory.getLogger(MlGrpcClientFacade.class);

    private final boolean mlEnabled;
    private final ManagedChannel channel;
    private final MlPredictServiceGrpc.MlPredictServiceBlockingStub stub;
    private final CircuitBreaker circuitBreaker;
    private final AtomicLong mlHitCount = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();

    public MlGrpcClientFacade(
            @Value("${gzgk.core.ml-grpc-enabled:false}") boolean mlEnabled,
            @Value("${gzgk.core.ml-grpc-host:127.0.0.1}") String host,
            @Value("${gzgk.core.ml-grpc-port:50051}") int port
    ) {
        this.mlEnabled = mlEnabled;
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .failureRateThreshold(30f)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.circuitBreaker = CircuitBreaker.of("gzgk-ml-grpc", cfg);
        if (mlEnabled) {
            this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            this.stub = MlPredictServiceGrpc.newBlockingStub(channel);
        } else {
            this.channel = null;
            this.stub = null;
        }
    }

    public BatchPredictionOutcome predictBatch(
            String provinceCode,
            int year,
            String subjectType,
            int candidateScore,
            int candidateRank,
            double sigmaMin,
            List<FeatureInput> inputs
    ) {
        if (mlEnabled && stub != null) {
            try {
                BatchPredictResponse resp = circuitBreaker.executeSupplier(() -> callRemoteMl(
                        provinceCode, year, subjectType, candidateScore, candidateRank, sigmaMin, inputs));
                Map<String, LogRankChanceModel.Prediction> mapped = new LinkedHashMap<>();
                for (PredictItemResult r : resp.getPredictionsList()) {
                    double calProb = com.gzgk.common.algorithm.ProbabilityCalibration.fromChanceScore(r.getChanceScore());
                    String grad = r.getChanceScore() >= 88 ? "垫"
                            : r.getChanceScore() >= 74 ? "保"
                            : r.getChanceScore() >= 52 ? "稳" : "冲";
                    mapped.put(r.getItemId(), new LogRankChanceModel.Prediction(
                            r.getPredictedMinRank(), r.getRankDiff(), r.getInternalChanceScore(),
                            r.getChanceScore(), calProb, r.getChanceLevel(), r.getRiskLevel(),
                            grad, (int) Math.round(r.getDataConfidence())
                    ));
                }
                mlHitCount.incrementAndGet();
                return new BatchPredictionOutcome("ml-grpc:" + resp.getModelVersion(), mapped);
            } catch (Exception ex) {
                log.warn("[ml-facade] gRPC call failed or circuit open ({}); degrading to log-rank model.", ex.getMessage());
            }
        }

        // Deterministic instant fallback
        Map<String, LogRankChanceModel.Prediction> fallback = new LinkedHashMap<>();
        for (FeatureInput in : inputs) {
            fallback.put(in.itemId(), LogRankChanceModel.predict(
                    candidateRank, in.latestMinRank(), in.rankVolatility3y(),
                    in.planChangeRate(), in.dataConfidence(), in.hotTrendScore(), sigmaMin));
        }
        fallbackCount.incrementAndGet();
        return new BatchPredictionOutcome("rule-log-rank-fallback", fallback);
    }

    private BatchPredictResponse callRemoteMl(
            String provinceCode, int year, String subjectType, int candidateScore,
            int candidateRank, double sigmaMin, List<FeatureInput> inputs
    ) {
        BatchPredictRequest.Builder req = BatchPredictRequest.newBuilder()
                .setModelVersion("latest")
                .setCandidate(CandidateContext.newBuilder()
                        .setProvinceCode(provinceCode)
                        .setYear(year)
                        .setSubjectType(subjectType)
                        .setCandidateScore(candidateScore)
                        .setCandidateRank(candidateRank)
                        .build());
        for (FeatureInput in : inputs) {
            req.addItems(PredictFeatureItem.newBuilder()
                    .setItemId(in.itemId())
                    .setSchoolId(in.schoolId())
                    .setMajorName(in.majorName())
                    .setMinRankLastYear(in.latestMinRank())
                    .setRankVolatility3Y(in.rankVolatility3y())
                    .setPlanChangeRate(in.planChangeRate())
                    .setMajorHotScore(in.hotTrendScore())
                    .setDataConfidence(in.dataConfidence())
                    .setIs985(in.is985())
                    .setIs211(in.is211())
                    .setIsDoubleFirstClass(in.isDualClass())
                    .setIsPublic(in.isPublic())
                    .setPlanCount(in.planCount())
                    .setProvinceSigmaMin(sigmaMin)
                    .build());
        }
        return stub.withDeadlineAfter(300, TimeUnit.MILLISECONDS).batchPredict(req.build());
    }

    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }

    public long getMlHitCount() {
        return mlHitCount.get();
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    public record FeatureInput(
            String itemId,
            String schoolId,
            String majorName,
            int latestMinRank,
            double rankVolatility3y,
            double planChangeRate,
            double dataConfidence,
            double hotTrendScore,
            boolean is985,
            boolean is211,
            boolean isDualClass,
            boolean isPublic,
            int planCount
    ) {
    }

    public record BatchPredictionOutcome(
            String engineLabel,
            Map<String, LogRankChanceModel.Prediction> predictions
    ) {
    }
}
