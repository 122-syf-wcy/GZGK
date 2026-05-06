package com.gzly.algorithm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MlPredictionEngine {
    @Value("${gzly.ml.enabled:false}")
    private boolean enabled;
    @Value("${gzly.ml.base-url:http://127.0.0.1:8091}")
    private String baseUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
