package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class EarlyCParallelMajorEngine implements RecommendEngine {

    public static final String NAME = "EarlyCParallelMajorEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
