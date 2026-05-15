package com.gzly.service.recommend;

import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendEngineDecision {
    private String engineName;
    private String recommendMode;
    private String supportLevel;
    private int maxVolunteerCount;
    private boolean queryOnly;
    private String batchCode;
    private String batchName;
    private String candidateType;
    private String supportReason;
    private boolean mainRankEngine;
    @Builder.Default
    private List<String> warnings = List.of();
    private BatchRuleRegistry.BatchRule rule;
    private BatchSupportService.BatchSupportItem supportItem;

    public boolean isOrdinaryParallelMajorEngine() {
        return "OrdinaryParallelMajorEngine".equals(engineName) && !queryOnly;
    }
}
