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

    /**
     * 主推荐链路（VolunteerService.generate）能直接吃的引擎：
     * 普通本科批/普通高职专科批/本科提前批 C 段（60 平行）。
     * 顺序志愿 / 艺术 / 体育 / 特殊计划 走批次列表服务，不进主链路。
     */
    public boolean isMainPipelineEngine() {
        if (queryOnly) return false;
        return "OrdinaryParallelMajorEngine".equals(engineName)
                || "EarlyCParallelMajorEngine".equals(engineName);
    }
}
