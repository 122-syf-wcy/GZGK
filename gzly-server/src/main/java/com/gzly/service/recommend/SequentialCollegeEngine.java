package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

@Service
public class SequentialCollegeEngine implements RecommendEngine {

    public static final String NAME = "SequentialCollegeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
