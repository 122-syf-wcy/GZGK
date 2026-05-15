package com.gzly.service.recommend;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.BatchQueryOnlyRecommendService;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import com.gzly.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryOnlyRecommendEngine implements RecommendEngine {

    public static final String NAME = "QueryOnlyRecommendEngine";

    private final BatchQueryOnlyRecommendService queryOnlyRecommendService;

    @Override
    public String engineName() {
        return NAME;
    }

    @Override
    public RecommendEngineDecision decide(VolunteerService.GenerateRequest request,
                                           PolicyRuleConfig policy,
                                           BatchRuleRegistry.BatchRule rule,
                                           BatchSupportService.BatchSupportItem supportItem) {
        RecommendEngineDecision decision = RecommendEngine.super.decide(request, policy, rule, supportItem);
        decision.setEngineName(NAME);
        decision.setQueryOnly(true);
        return decision;
    }

    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest request,
                                                PolicyRuleConfig policy,
                                                RecommendEngineDecision decision) {
        BatchRuleRegistry.BatchRule rule = decision.getRule();
        String reason = decision.getSupportReason() == null || decision.getSupportReason().isBlank()
                ? rule.supportNote()
                : decision.getSupportReason();
        VolunteerService.PlanResult plan = queryOnlyRecommendService.generate(request, policy, rule, reason);
        plan.setRecommendMode(decision.getRecommendMode());
        plan.setSupportLevel(decision.getSupportLevel());
        plan.setEngineName(decision.getEngineName());
        plan.setSupportReason(reason);
        plan.setWarnings(decision.getWarnings());
        return plan;
    }
}
