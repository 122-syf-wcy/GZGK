package com.gzly.config;

import com.gzly.common.exception.ConfigurationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityPropertiesValidator {

    @Value("${gzly.jwt.secret:}")
    private String jwtSecret;

    @Value("${gzly.admin.password:}")
    private String adminPassword;

    @Value("${gzly.admin.password-hash:}")
    private String adminPasswordHash;

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new ConfigurationException("GZLY_JWT_SECRET must be set and at least 32 characters");
        }
        boolean hasPasswordHash = adminPasswordHash != null && !adminPasswordHash.isBlank();
        boolean hasLegacyPassword = adminPassword != null && adminPassword.length() >= 8;
        if (!hasPasswordHash && !hasLegacyPassword) {
            throw new ConfigurationException("GZLY_ADMIN_PASSWORD_HASH must be set, or set GZLY_ADMIN_PASSWORD with at least 8 characters for legacy fallback");
        }
    }
}
