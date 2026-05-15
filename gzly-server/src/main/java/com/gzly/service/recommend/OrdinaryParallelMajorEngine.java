package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class OrdinaryParallelMajorEngine implements RecommendEngine {

    public static final String NAME = "OrdinaryParallelMajorEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
