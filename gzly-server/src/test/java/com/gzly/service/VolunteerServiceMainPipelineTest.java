package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.CandidateFilterEngine;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.algorithm.FeatureBuildEngine;
import com.gzly.algorithm.VolunteerDiagnosisEngine;
import com.gzly.algorithm.VolunteerSortEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VolunteerService 主链路接入点测试：用反射触发 buildFilterCriteria / toCandidatePlan / hasHardRuleViolation 等
 * 关键 helper，验证 16 项硬规则正确接入；不依赖完整 generate() 流程，避免 mock 大量数据库依赖。
 */
class VolunteerServiceMainPipelineTest {

    private VolunteerService newService() {
        return new VolunteerService(
                null, null, null, null, null,
                null, new ObjectMapper(), null, new VolunteerMetricsRecorder(), new SafetyCodeService(), new ProvincePolicyService(), null,
                new CandidateFilterEngine(),
                new FeatureBuildEngine(),
                new FallbackRulePredictionEngine(),
                new VolunteerSortEngine(),
                new VolunteerDiagnosisEngine());
    }

    @Test
    void buildFilterCriteria_propagatesNewFieldsFromRequest() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setYear(2025);
        req.setBatchCode("NORMAL_UNDERGRADUATE");
        req.setCandidateType("艺术类");
        req.setSelectedSubjects(List.of("物理", "化学", "生物"));
        req.setMaxTuition(20000);
        req.setAcceptPrivateSchool(false);
        req.setAcceptChineseForeignCoop(false);
        req.setDislikedMajors(List.of("土木工程"));
        req.setMedicalLimitations(List.of("色盲"));
        req.setSingleSubjectScores(Map.of("数学", 110));
        req.setForeignLanguage("英语");
        req.setGender("女");
        req.setQualificationTags(List.of("免费医学定向"));

        Method m = VolunteerService.class.getDeclaredMethod("buildFilterCriteria",
                VolunteerService.GenerateRequest.class, String.class, String.class);
        m.setAccessible(true);
        CandidateFilterEngine.FilterCriteria c =
                (CandidateFilterEngine.FilterCriteria) m.invoke(service, req, "GZ", "物理类");

        assertThat(c.getYear()).isEqualTo(2025);
        assertThat(c.getProvince()).isEqualTo("GZ");
        assertThat(c.getBatchCode()).isEqualTo("NORMAL_UNDERGRADUATE");
        assertThat(c.getCandidateType()).isEqualTo("艺术类");
        assertThat(c.getSelectedSubjects()).containsExactlyInAnyOrder("物理", "化学", "生物");
        assertThat(c.getMaxTuition()).isEqualTo(20000);
        assertThat(c.getAcceptPrivateSchool()).isFalse();
        assertThat(c.getAcceptChineseForeignCoop()).isFalse();
        assertThat(c.getDislikedMajors()).containsExactly("土木工程");
        assertThat(c.getMedicalLimitations()).containsExactly("色盲");
        assertThat(c.getSingleSubjectScores()).containsEntry("数学", 110);
        assertThat(c.getForeignLanguage()).isEqualTo("英语");
        assertThat(c.getGender()).isEqualTo("女");
        assertThat(c.getQualifications()).containsExactly("免费医学定向");
    }

    @Test
    void buildFilterCriteria_fallsBackToLegacyAcceptFlagsWhenNewOnesMissing() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setAcceptPrivate(false);          // legacy
        req.setAcceptSinoForeign(false);      // legacy
        // acceptPrivateSchool / acceptChineseForeignCoop 未设
        Method m = VolunteerService.class.getDeclaredMethod("buildFilterCriteria",
                VolunteerService.GenerateRequest.class, String.class, String.class);
        m.setAccessible(true);
        CandidateFilterEngine.FilterCriteria c =
                (CandidateFilterEngine.FilterCriteria) m.invoke(service, req, "GZ", "物理类");
        assertThat(c.getAcceptPrivateSchool()).isFalse();
        assertThat(c.getAcceptChineseForeignCoop()).isFalse();
        // 默认 candidateType 应为 "普通类"
        assertThat(c.getCandidateType()).isEqualTo("普通类");
    }

    @Test
    void hasHardRuleViolation_rejectsItemMatchingDislikedMajors() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setDislikedMajors(List.of("土木工程"));
        Method buildCriteria = VolunteerService.class.getDeclaredMethod("buildFilterCriteria",
                VolunteerService.GenerateRequest.class, String.class, String.class);
        buildCriteria.setAccessible(true);
        CandidateFilterEngine.FilterCriteria criteria =
                (CandidateFilterEngine.FilterCriteria) buildCriteria.invoke(service, req, "GZ", "物理类");

        CandidateFilterEngine.CandidatePlan plan = new CandidateFilterEngine.CandidatePlan();
        plan.setMajorName("土木工程");
        plan.setSubjectType("物理类");

        Method hasViolation = VolunteerService.class.getDeclaredMethod("hasHardRuleViolation",
                CandidateFilterEngine.FilterCriteria.class, CandidateFilterEngine.CandidatePlan.class);
        hasViolation.setAccessible(true);
        boolean rejected = (boolean) hasViolation.invoke(service, criteria, plan);
        assertThat(rejected).isTrue();
    }

    @Test
    void hasHardRuleViolation_passesNeutralPlan() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        Method buildCriteria = VolunteerService.class.getDeclaredMethod("buildFilterCriteria",
                VolunteerService.GenerateRequest.class, String.class, String.class);
        buildCriteria.setAccessible(true);
        CandidateFilterEngine.FilterCriteria criteria =
                (CandidateFilterEngine.FilterCriteria) buildCriteria.invoke(service, req, "GZ", "物理类");

        CandidateFilterEngine.CandidatePlan plan = new CandidateFilterEngine.CandidatePlan();
        plan.setMajorName("计算机科学与技术");
        plan.setSubjectType("物理类");

        Method hasViolation = VolunteerService.class.getDeclaredMethod("hasHardRuleViolation",
                CandidateFilterEngine.FilterCriteria.class, CandidateFilterEngine.CandidatePlan.class);
        hasViolation.setAccessible(true);
        boolean rejected = (boolean) hasViolation.invoke(service, criteria, plan);
        assertThat(rejected).isFalse();
    }
}
