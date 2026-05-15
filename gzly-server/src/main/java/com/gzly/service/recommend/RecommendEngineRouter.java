package com.gzly.service.recommend;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.VolunteerService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendEngineRouter {

    private final BatchSupportService batchSupportService;
    private final QueryOnlyRecommendEngine queryOnlyRecommendEngine;
    private final AdmissionYearService admissionYearService;
    private final Map<String, RecommendEngine> engines;

    public RecommendEngineRouter(BatchSupportService batchSupportService,
                                 QueryOnlyRecommendEngine queryOnlyRecommendEngine,
                                 AdmissionYearService admissionYearService,
                                 List<RecommendEngine> engines) {
        this.batchSupportService = batchSupportService;
        this.queryOnlyRecommendEngine = queryOnlyRecommendEngine;
        this.admissionYearService = admissionYearService;
        this.engines = engines.stream()
                .collect(Collectors.toMap(RecommendEngine::engineName, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public RecommendEngineDecision resolve(String provinceCode, String candidateType, String batchCode) {
        return resolve(provinceCode, admissionYearService.getActiveAdmissionYear(), candidateType, batchCode, null, null);
    }

    public RecommendEngineDecision resolve(VolunteerService.GenerateRequest request, PolicyRuleConfig policy) {
        String provinceCode = request == null ? ProvincePolicyService.GZ : request.getProvinceCode();
        Integer year = request == null ? admissionYearService.getActiveAdmissionYear() : request.getYear();
        String candidateType = request == null ? PolicyRuleService.DEFAULT_CANDIDATE_TYPE : request.getCandidateType();
        String batchCode = request == null ? BatchRuleRegistry.DEFAULT_BATCH_CODE : request.getBatchCode();
        return resolve(provinceCode, year, candidateType, batchCode, request, policy);
    }

    public RecommendEngineDecision resolve(String provinceCode,
                                           Integer year,
                                           String candidateType,
                                           String batchCode,
                                           VolunteerService.GenerateRequest request,
                                           PolicyRuleConfig policy) {
        String province = provinceCode == null || provinceCode.isBlank() ? ProvincePolicyService.GZ : provinceCode.trim().toUpperCase();
        String normalizedCandidateType = BatchRuleRegistry.normalizeCandidateType(candidateType);
        String normalizedBatchCode = BatchRuleRegistry.normalizeBatchCode(batchCode);
        BatchRuleRegistry.BatchRule rule = BatchRuleRegistry.find(normalizedBatchCode).orElse(null);
        if (rule == null) {
            return unsupportedDecision(normalizedBatchCode, normalizedCandidateType);
        }
        if (!ProvincePolicyService.GZ.equals(province)) {
            return unsupportedDecision(normalizedBatchCode, normalizedCandidateType);
        }
        if (!BatchRuleRegistry.candidateTypeMatches(rule.candidateType(), normalizedCandidateType)) {
            return unsupportedDecision(normalizedBatchCode, normalizedCandidateType);
        }
        BatchSupportService.BatchSupportItem supportItem = supportItem(province, year, rule);
        String engineName = engineNameFor(rule);
        RecommendEngine engine = engines.getOrDefault(engineName, queryOnlyRecommendEngine);
        RecommendEngineDecision decision = engine.decide(request, policy, rule, supportItem);
        if (rule.mainRankEngine() && isQueryOnlySupport(decision.getSupportLevel())) {
            return queryOnlyRecommendEngine.decide(request, policy, rule, supportItem);
        }
        return decision;
    }

    private BatchSupportService.BatchSupportItem supportItem(String provinceCode,
                                                             Integer year,
                                                             BatchRuleRegistry.BatchRule rule) {
        try {
            BatchSupportService.BatchSupportResponse response = batchSupportService.supportMatrix(provinceCode, year);
            if (response == null || response.getItems() == null) {
                return null;
            }
            return response.getItems().stream()
                    .filter(item -> rule.batchCode().equals(item.getBatchCode()))
                    .filter(item -> BatchRuleRegistry.candidateTypeMatches(rule.candidateType(), item.getCandidateType()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String engineNameFor(BatchRuleRegistry.BatchRule rule) {
        return switch (rule.batchCode()) {
            case "NORMAL_UNDERGRADUATE", "NORMAL_SPECIALTY" -> OrdinaryParallelMajorEngine.NAME;
            case "EARLY_A_B", "SPECIALTY_EARLY" -> SequentialCollegeEngine.NAME;
            case "EARLY_C" -> EarlyCParallelMajorEngine.NAME;
            default -> switch (rule.category()) {
                case ART -> ArtCompositeRecommendEngine.NAME;
                case SPORTS -> SportsCompositeRecommendEngine.NAME;
                case SPECIAL_PROGRAM -> SpecialPlanEligibilityEngine.NAME;
                default -> rule.engine();
            };
        };
    }

    private boolean isQueryOnlySupport(String supportLevel) {
        return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name().equals(supportLevel)
                || BatchRuleRegistry.SupportLevel.UNSUPPORTED.name().equals(supportLevel);
    }

    private RecommendEngineDecision unsupportedDecision(String batchCode, String candidateType) {
        String resolvedBatchCode = batchCode == null || batchCode.isBlank() ? "UNKNOWN" : batchCode;
        return RecommendEngineDecision.builder()
                .engineName(QueryOnlyRecommendEngine.NAME)
                .recommendMode(BatchRuleRegistry.RecommendMode.QUERY_ONLY.name())
                .supportLevel(BatchRuleRegistry.SupportLevel.UNSUPPORTED.name())
                .maxVolunteerCount(0)
                .queryOnly(true)
                .batchCode(resolvedBatchCode)
                .batchName(resolvedBatchCode)
                .candidateType(candidateType)
                .supportReason("当前省份或批次未纳入贵州批次支持矩阵，不能静默回退到普通本科批")
                .mainRankEngine(false)
                .warnings(List.of("不支持批次不能静默回退到普通本科批"))
                .build();
    }
}
