package com.gzly.service;

import com.gzly.compliance.ComplianceTextGuard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * VolunteerExportService 诊断摘要 / 警报合规字段测试。
 *
 * 用反射调用 readDiagnosisSummary / readDiagnosisWarnings 两个 helper，
 * 验证：
 *  1) 缺失字段不抛异常；
 *  2) 字段类型不匹配时安全降级返回空；
 *  3) PlanResult.diagnosis 提供数据时如实返回。
 */
class ExportComplianceTest {

    @Test
    void readDiagnosisSummary_returnsEmptyWhenDiagnosisMissing() throws Exception {
        VolunteerExportService service = mockedService();
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setDiagnosis(null);

        String summary = invokeReadSummary(service, plan);
        assertThat(summary).isEqualTo("");
    }

    @Test
    void readDiagnosisSummary_returnsValueWhenPresent() throws Exception {
        VolunteerExportService service = mockedService();
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("summary", "整体梯度合理，专业集中度需注意");
        plan.setDiagnosis(diagnosis);

        String summary = invokeReadSummary(service, plan);
        assertThat(summary).isEqualTo("整体梯度合理，专业集中度需注意");
    }

    @Test
    void readDiagnosisSummary_returnsEmptyWhenWrongType() throws Exception {
        VolunteerExportService service = mockedService();
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("summary", 12345); // 类型错误
        plan.setDiagnosis(diagnosis);

        String summary = invokeReadSummary(service, plan);
        assertThat(summary).isEqualTo("");
    }

    @Test
    void readDiagnosisWarnings_filtersBlankAndNonStringEntries() throws Exception {
        VolunteerExportService service = mockedService();
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("warnings", List.of("梯度不合理", "", "    ", 123, "兜底参考偏少"));
        plan.setDiagnosis(diagnosis);

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) invokeReadWarnings(service, plan);
        assertThat(warnings).containsExactly("梯度不合理", "兜底参考偏少");
    }

    @Test
    void clipText_truncatesLongStringsAndAppendsEllipsis() throws Exception {
        VolunteerExportService service = mockedService();
        Method method = VolunteerExportService.class.getDeclaredMethod("clipText", String.class, int.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "abcdefghij", 5);
        assertThat(result).isEqualTo("abcde…");
        result = (String) method.invoke(service, "abc", 5);
        assertThat(result).isEqualTo("abc");
        result = (String) method.invoke(service, null, 5);
        assertThat(result).isEqualTo("");
    }

    // ───────────── helpers ─────────────

    private VolunteerExportService mockedService() {
        ComplianceTextGuard guard = mock(ComplianceTextGuard.class);
        lenient().when(guard.sanitizeText(anyString(), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(2));
        return new VolunteerExportService(null, guard);
    }

    private String invokeReadSummary(VolunteerExportService service, VolunteerService.PlanResult plan) throws Exception {
        Method method = VolunteerExportService.class.getDeclaredMethod("readDiagnosisSummary", VolunteerService.PlanResult.class);
        method.setAccessible(true);
        return (String) method.invoke(service, plan);
    }

    private Object invokeReadWarnings(VolunteerExportService service, VolunteerService.PlanResult plan) throws Exception {
        Method method = VolunteerExportService.class.getDeclaredMethod("readDiagnosisWarnings", VolunteerService.PlanResult.class);
        method.setAccessible(true);
        return method.invoke(service, plan);
    }
}
