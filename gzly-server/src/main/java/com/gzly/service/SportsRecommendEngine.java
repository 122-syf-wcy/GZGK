package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SportsRecommendEngine {

    private final BatchQueryOnlyRecommendService queryOnlyRecommendService;

    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req,
                                                PolicyRuleConfig policy,
                                                BatchRuleRegistry.BatchRule rule) {
        return queryOnlyRecommendService.generate(req, policy, rule,
                "体育类需要体育专业成绩、综合分折算规则和体育类分批计划；当前不套用普通位次推荐模型。");
    }
}
