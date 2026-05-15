package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class SpecialPlanEligibilityEngine implements RecommendEngine {

    public static final String NAME = "SpecialPlanEligibilityEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
