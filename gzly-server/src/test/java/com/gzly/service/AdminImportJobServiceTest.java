package com.gzly.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminImportJobServiceTest {

    @Test
    void generatedSqlTemplatesKeepQuotedStringLiteralsAndNoFormalPromotion() throws Exception {
        AdminImportJobService service = new AdminImportJobService(
                mock(JdbcTemplate.class),
                new DataReadinessService(mock(JdbcTemplate.class)));
        Map<String, Object> job = Map.of(
                "id", 88L,
                "province_code", "HB",
                "year", 2026);

        String ddl = invokeString(service, "stagingDdl", job);
        String rollback = invokeString(service, "rollbackSql", job);
        String postCheck = invokeString(service, "postCheck", job);

        assertThat(ddl).contains("DEFAULT ''", "DEFAULT 'draft'");
        assertThat(ddl).doesNotContain("DEFAULT ,", "DEFAULT draft");
        assertThat(rollback).contains("SELECT 'rollback.blocked_until_confirmed'");
        assertThat(postCheck).contains("province_code='HB'", "SELECT 'fake_2026_score_rank'");
        assertThat(postCheck).doesNotContain("province_code= + province +");
    }

    private String invokeString(AdminImportJobService service, String methodName, Map<String, Object> job) throws Exception {
        Method method = AdminImportJobService.class.getDeclaredMethod(methodName, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(service, job);
    }
}
