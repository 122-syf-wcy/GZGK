package com.gzly.algorithm;

import com.gzly.service.PolicyRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyRuleEngine {
    private final PolicyRuleService policyRuleService;

    public PolicyRuleService.PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
        return policyRuleService.requirePolicy(provinceCode, year, candidateType, batchCode);
    }
}
