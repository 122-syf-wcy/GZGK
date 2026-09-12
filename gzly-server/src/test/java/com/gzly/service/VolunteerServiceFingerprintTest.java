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
 * 请求指纹 buildGenerateFingerprint 回归测试。
 *
 * <p>背景：指纹是 120s 结果缓存与请求锁的 key。此前只收约 16 个字段，缺 year / batchCode /
 * candidateType / selectedSubjects / maxTuition / acceptPrivateSchool / acceptChineseForeignCoop /
 * dislikedMajors / medicalLimitations / singleSubjectScores / foreignLanguage / gender /
 * qualificationTags 等会被硬规则过滤消费的输入，导致"同分但不同条件"在缓存窗口内共享错误结果。
 * 本测试锁死：条件不同则指纹不同、同条件可复现、集合顺序不影响指纹。</p>
 */
class VolunteerServiceFingerprintTest {

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

    private String fingerprint(VolunteerService service, VolunteerService.GenerateRequest req) throws Exception {
        Method m = VolunteerService.class.getDeclaredMethod("buildGenerateFingerprint",
                VolunteerService.GenerateRequest.class, Long.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, req, 1001L, "127.0.0.1");
    }

    private VolunteerService.GenerateRequest baseRequest() {
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setProvinceCode("GZ");
        req.setYear(2025);
        req.setBatchCode("NORMAL_UNDERGRADUATE");
        req.setCandidateType("普通类");
        req.setTotalScore(580);
        req.setProvinceRank(12000);
        req.setFirstSubject("物理");
        req.setResubjects(List.of("化学", "生物"));
        req.setSelectedSubjects(List.of("物理", "化学", "生物"));
        req.setPreferredMajors(List.of("计算机", "电子信息"));
        req.setPreferredRegions(List.of("北京", "上海"));
        req.setStrategyMode("均衡型");
        req.setDecisionPriority("专业优先");
        req.setCareerGoal("就业优先");
        req.setTuitionBudget("均衡预算");
        req.setAcceptPrivate(true);
        req.setAcceptSinoForeign(false);
        req.setAcceptPrivateSchool(true);
        req.setAcceptChineseForeignCoop(false);
        req.setMaxTuition(30000);
        req.setDislikedMajors(List.of("土木工程"));
        req.setMedicalLimitations(List.of("色盲"));
        req.setSingleSubjectScores(Map.of("数学", 110));
        req.setForeignLanguage("英语");
        req.setGender("女");
        req.setQualificationTags(List.of("免费医学定向"));
        req.setDisclaimerVersion("v1");
        return req;
    }

    @Test
    void differentDislikedMajors_sameScore_producesDifferentFingerprint() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest a = baseRequest();
        VolunteerService.GenerateRequest b = baseRequest();
        b.setDislikedMajors(List.of("土木工程", "护理学"));

        assertThat(fingerprint(service, a)).isNotEqualTo(fingerprint(service, b));
    }

    @Test
    void sameConditions_repeatedCalls_producesSameFingerprint() throws Exception {
        VolunteerService service = newService();

        assertThat(fingerprint(service, baseRequest())).isEqualTo(fingerprint(service, baseRequest()));
    }

    @Test
    void sameSetDifferentOrder_producesSameFingerprint() throws Exception {
        VolunteerService service = newService();
        VolunteerService.GenerateRequest a = baseRequest();
        VolunteerService.GenerateRequest b = baseRequest();
        // 集合内容相同、顺序不同：指纹必须一致
        b.setDislikedMajors(List.of("护理学", "土木工程"));
        a.setDislikedMajors(List.of("土木工程", "护理学"));
        b.setResubjects(List.of("生物", "化学"));
        b.setPreferredMajors(List.of("电子信息", "计算机"));
        b.setSingleSubjectScores(Map.of("数学", 110));

        assertThat(fingerprint(service, a)).isEqualTo(fingerprint(service, b));
    }

    @Test
    void differentFilterConsumedScalars_produceDifferentFingerprints() throws Exception {
        VolunteerService service = newService();
        String base = fingerprint(service, baseRequest());

        VolunteerService.GenerateRequest yearChanged = baseRequest();
        yearChanged.setYear(2024);
        VolunteerService.GenerateRequest batchChanged = baseRequest();
        batchChanged.setBatchCode("EARLY_A_B");
        VolunteerService.GenerateRequest typeChanged = baseRequest();
        typeChanged.setCandidateType("艺术类");
        VolunteerService.GenerateRequest tuitionChanged = baseRequest();
        tuitionChanged.setMaxTuition(10000);
        VolunteerService.GenerateRequest langChanged = baseRequest();
        langChanged.setForeignLanguage("日语");
        VolunteerService.GenerateRequest genderChanged = baseRequest();
        genderChanged.setGender("男");
        VolunteerService.GenerateRequest acceptChanged = baseRequest();
        acceptChanged.setAcceptPrivateSchool(false);

        assertThat(fingerprint(service, yearChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, batchChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, typeChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, tuitionChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, langChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, genderChanged)).isNotEqualTo(base);
        assertThat(fingerprint(service, acceptChanged)).isNotEqualTo(base);
    }
}
