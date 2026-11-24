package com.gzgk.core.catalog;

import java.util.List;

public record UniversityRecord(
        String schoolId,
        String name,
        String province,
        String city,
        String level,
        String typeName,
        List<String> tags,
        boolean is985,
        boolean is211,
        boolean isDualClass,
        String natureName,
        String belong,
        String schoolSite,
        String officialCharterUrl,
        String summary
) {
}
