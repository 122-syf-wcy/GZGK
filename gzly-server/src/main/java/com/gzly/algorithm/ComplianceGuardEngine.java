package com.gzly.algorithm;

import com.gzly.compliance.ComplianceTextGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ComplianceGuardEngine {
    private final ComplianceTextGuard guard;

    public String sanitize(String businessType, String businessId, String text) {
        return guard.sanitizeText(businessType, businessId, text);
    }
}
