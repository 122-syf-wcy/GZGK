package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class ArtCompositeRecommendEngine implements RecommendEngine {

    public static final String NAME = "ArtCompositeRecommendEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
