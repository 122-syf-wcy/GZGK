package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class SportsCompositeRecommendEngine implements RecommendEngine {

    public static final String NAME = "SportsCompositeRecommendEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
