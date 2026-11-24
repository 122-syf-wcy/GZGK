package com.gzgk.core.policy;

import java.util.Map;

public final class ProvincePolicyModels {
    private ProvincePolicyModels() {
    }

    public record ProvincePolicyView(
            String provinceCode,
            String provinceName,
            String subjectMode,         // 3+1+2 | 3+3
            String scoreSystem,         // RAW_750 | STANDARD_900
            String volunteerUnitType,   // MAJOR_96 | PROFESSIONAL_GROUP_45
            String volunteerModeLabel,
            int maxVolunteerCount,
            int majorPerGroupCount,
            boolean hasAdjustment,
            double sigmaMin,
            String readinessStatus,     // FULL | ESTIMATE | BLOCKED
            String officialSourceName,
            String officialSourceUrl,
            Map<String, Integer> defaultGradientCounts
    ) {
    }
}
