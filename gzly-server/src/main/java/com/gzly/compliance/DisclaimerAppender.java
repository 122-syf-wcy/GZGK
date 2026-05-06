package com.gzly.compliance;

import com.gzly.common.ComplianceConstants;
import org.springframework.stereotype.Component;

@Component
public class DisclaimerAppender {

    public String append(String text) {
        String value = text == null ? "" : text.trim();
        if (value.contains(ComplianceConstants.SAFE_ASSISTANT_NOTICE)) {
            return value;
        }
        if (value.isBlank()) {
            return ComplianceConstants.SAFE_ASSISTANT_NOTICE;
        }
        return value + "\n\n" + ComplianceConstants.SAFE_ASSISTANT_NOTICE;
    }
}
