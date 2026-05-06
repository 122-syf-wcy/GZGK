package com.gzly.algorithm;

import com.gzly.common.ComplianceConstants;
import org.springframework.stereotype.Component;

@Component
public class ExportEngine {
    public String disclaimer() {
        return ComplianceConstants.SAFE_ASSISTANT_NOTICE;
    }
}
