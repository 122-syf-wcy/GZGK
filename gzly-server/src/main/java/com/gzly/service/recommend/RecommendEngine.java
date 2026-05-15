package com.gzly.service.recommend;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import com.gzly.service.VolunteerService;

import java.util.ArrayList;
import java.util.List;

public interface RecommendEngine {

    String engineName();

    default RecommendEngineDecision decide(VolunteerService.GenerateRequest request,
                                           PolicyRuleConfig policy,
                                           BatchRuleRegistry.BatchRule rule,
                                           BatchSupportService.BatchSupportItem supportItem) {
        String supportLevel = supportItem == null || supportItem.getSupportLevel() == null || supportItem.getSupportLevel().isBlank()
                ? rule.baseSupportLevel().name()
                : supportItem.getSupportLevel();
        String supportReason = supportItem == null || supportItem.getSupportReason() == null || supportItem.getSupportReason().isBlank()
                ? rule.supportNote()
                : supportItem.getSupportReason();
        boolean queryOnly = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name().equals(supportLevel)
                || BatchRuleRegistry.SupportLevel.UNSUPPORTED.name().equals(supportLevel)
                || !rule.mainRankEngine();
        List<String> warnings = new ArrayList<>();
        if (supportItem != null && supportItem.getWarnings() != null) {
            warnings.addAll(supportItem.getWarnings());
        }
        if (warnings.isEmpty() && rule.supportNote() != null && !rule.supportNote().isBlank()) {
            warnings.add(rule.supportNote());
        }
        return RecommendEngineDecision.builder()
                .engineName(engineName())
                .recommendMode(rule.recommendMode().name())
                .supportLevel(supportLevel)
                .maxVolunteerCount(rule.targetCount())
                .queryOnly(queryOnly)
                .batchCode(rule.batchCode())
                .batchName(rule.batchName())
                .candidateType(rule.candidateType())
                .supportReason(supportReason)
                .mainRankEngine(rule.mainRankEngine())
                .warnings(List.copyOf(warnings))
                .rule(rule)
                .supportItem(supportItem)
                .build();
    }
}
