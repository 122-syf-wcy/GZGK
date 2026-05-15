package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtRecommendEngine {

    private final BatchQueryOnlyRecommendService queryOnlyRecommendService;

    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req,
                                                PolicyRuleConfig policy,
                                                BatchRuleRegistry.BatchRule rule) {
        return queryOnlyRecommendService.generate(req, policy, rule,
                "艺术类需要专业统考或校考成绩、综合分折算规则和艺术类分批计划；当前不套用普通位次推荐模型。");
    }
}
