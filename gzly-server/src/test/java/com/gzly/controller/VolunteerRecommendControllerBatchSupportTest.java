package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchSupportService;
import com.gzly.service.DataYearReadinessService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.VolunteerService;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 针对 {@link VolunteerRecommendController#gzBatchSupport(Integer)} 的契约测试：
 *
 * <ul>
 *   <li>HTTP 200 / Result.ok 路径；</li>
 *   <li>PRE_OFFICIAL_DATA 下普通本科/专科 ESTIMATE_RECOMMEND，其他批 QUERY_ONLY；</li>
 *   <li>公共调用历史年份必须被 {@code admissionYearService.normalizePublicYear} 拒绝；</li>
 *   <li>端点只读：不触发 VolunteerService 生成、不调用 ML、不写 plan history。</li>
 * </ul>
 *
 * 使用 Mockito 提供 9 个 controller 依赖中除 BatchSupportService / AdmissionYearService /
 * DataYearReadinessService 外的全部 mock，保证 batch-support 端点路径不依赖任何写库/算法/ML 通道。
 */
class VolunteerRecommendControllerBatchSupportTest {

    private VolunteerService volunteerService;
    private ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    private ProvincePolicyService provincePolicyService;
    private PolicyRuleService policyRuleService;
    private MlPredictionService mlPredictionService;
    private JwtUtil jwtUtil;
    private AdmissionYearService admissionYearService;
    private BatchSupportService batchSupportService;
    private DataYearReadinessService dataYearReadinessService;
    private VolunteerRecommendController controller;

    @BeforeEach
    void setUp() {
        volunteerService = mock(VolunteerService.class);
        professionalGroupVolunteerService = mock(ProfessionalGroupVolunteerService.class);
        provincePolicyService = mock(ProvincePolicyService.class);
        policyRuleService = mock(PolicyRuleService.class);
        mlPredictionService = mock(MlPredictionService.class);
        jwtUtil = mock(JwtUtil.class);
        admissionYearService = new AdmissionYearService();
        admissionYearService.setActiveAdmissionYear(2026);
        admissionYearService.setHistoryYears("2025,2024");
        admissionYearService.setTrainingYears("2024,2025");
        JdbcTemplate jdbc = new ReadinessAwareJdbcTemplate(readinessRow(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA, false, true));
        batchSupportService = new BatchSupportService(jdbc, admissionYearService);
        dataYearReadinessService = new DataYearReadinessService(jdbc, admissionYearService);
        controller = new VolunteerRecommendController(
                volunteerService,
                professionalGroupVolunteerService,
                provincePolicyService,
                policyRuleService,
                mlPredictionService,
                batchSupportService,
                admissionYearService,
                dataYearReadinessService,
                jwtUtil);
    }

    @Test
    void publicBatchSupport_shouldReturn200() {
        Result<BatchSupportService.BatchSupportResponse> result = controller.gzBatchSupport(null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getProvinceCode()).isEqualTo("GZ");
        assertThat(result.getData().getYear()).isEqualTo(2026);
        assertThat(result.getData().isPublicYearLocked()).isTrue();
    }

    @Test
    void publicBatchSupport_shouldExposeEstimateRecommendForNormalBatches() {
        Result<BatchSupportService.BatchSupportResponse> result = controller.gzBatchSupport(2026);
        BatchSupportService.BatchSupportResponse response = result.getData();

        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(response.isEstimateMode()).isTrue();
        assertThat(response.getDataSourceYears()).containsExactly(2024, 2025);
        assertThat(itemSupportLevel(response, "NORMAL_UNDERGRADUATE")).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(itemSupportLevel(response, "NORMAL_SPECIALTY")).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(response.getSummary()).containsEntry("TRIAL_RECOMMEND", 0L);
        assertThat(response.getSummary()).containsEntry("ESTIMATE_RECOMMEND", 2L);
    }

    @Test
    void publicBatchSupport_shouldKeepNonNormalBatchesQueryOnly() {
        Result<BatchSupportService.BatchSupportResponse> result = controller.gzBatchSupport(2026);
        BatchSupportService.BatchSupportResponse response = result.getData();

        for (String code : List.of(
                "EARLY_A_B", "EARLY_C", "SPECIALTY_EARLY",
                "ART_UNDERGRADUATE_A", "ART_UNDERGRADUATE_B", "ART_SPECIALTY",
                "SPORTS_UNDERGRADUATE", "SPORTS_SPECIALTY",
                "NATIONAL_SPECIAL", "LOCAL_SPECIAL", "UNIVERSITY_SPECIAL",
                "ETHNIC_CLASS", "PREPARATORY", "ORIENTED",
                "FREE_MEDICAL", "TEACHER_EXCELLENCE")) {
            assertThat(itemSupportLevel(response, code))
                    .as("batch %s should stay QUERY_ONLY in PRE_OFFICIAL_DATA", code)
                    .isEqualTo("QUERY_ONLY");
        }
    }

    @Test
    void publicBatchSupport_withHistoricalYear_shouldReject() {
        assertThatThrownBy(() -> controller.gzBatchSupport(2024))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前填报入口仅支持最新高考年份");
        assertThatThrownBy(() -> controller.gzBatchSupport(2025))
                .isInstanceOf(BizException.class);
    }

    @Test
    void batchSupportEndpoint_shouldNotWritePlanHistory_orCallMl() {
        controller.gzBatchSupport(null);
        controller.gzBatchSupport(2026);

        // 端点只读：不允许触发 VolunteerService 生成、ProfessionalGroupVolunteerService、
        // PolicyRuleService 写场景、MlPredictionService 调用。
        verifyNoInteractions(volunteerService);
        verifyNoInteractions(professionalGroupVolunteerService);
        verifyNoInteractions(policyRuleService);
        verifyNoInteractions(mlPredictionService);
    }

    private static String itemSupportLevel(BatchSupportService.BatchSupportResponse response, String code) {
        return response.getItems().stream()
                .filter(item -> code.equals(item.getBatchCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("batch not found: " + code))
                .getSupportLevel();
    }

    private static Map<String, Object> readinessRow(String phase, boolean mlReady, boolean historicalReady) {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_ready", 0);
        row.put("score_segment_ready", 0);
        row.put("admission_plan_ready", 0);
        row.put("major_requirement_ready", 0);
        row.put("major_meta_ready", 0);
        row.put("ml_training_ready", mlReady ? 1 : 0);
        row.put("historical_training_ready", historicalReady ? 1 : 0);
        row.put("recommendation_phase", phase);
        row.put("latest_import_batch_id", "");
        row.put("last_checked_at", "");
        row.put("remarks", "controller-batch-support-test");
        return row;
    }

    /** 极简 FakeJdbcTemplate：让 BatchSupportService / DataYearReadinessService 拿到模拟 readiness 行，
     *  其余 DB 调用都返回"无数据/无表"，模拟 PRE_OFFICIAL_DATA 但有历史数据已就绪的状态。 */
    private static class ReadinessAwareJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> readinessRow;

        ReadinessAwareJdbcTemplate(Map<String, Object> readinessRow) {
            this.readinessRow = readinessRow;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (Long.class.equals(requiredType)) {
                if (sql.contains("information_schema.tables")) {
                    String table = String.valueOf(args[0]);
                    if ("data_year_readiness".equals(table)
                            || "data_score_line_gz".equals(table)
                            || "data_major_score_gz".equals(table)
                            || "data_admission_plan_gz".equals(table)
                            || "admission_plan".equals(table)
                            || "data_major_requirement_gz".equals(table)) {
                        return requiredType.cast(1L);
                    }
                    return requiredType.cast(0L);
                }
                if (sql.contains("information_schema.columns")) {
                    String table = String.valueOf(args[0]);
                    String column = String.valueOf(args[1]);
                    if (("data_admission_plan_gz".equals(table) || "admission_plan".equals(table)) && "batch_code".equals(column)) {
                        return requiredType.cast(1L);
                    }
                    if (("data_score_line_gz".equals(table) || "data_major_score_gz".equals(table)) && "batch".equals(column)) {
                        return requiredType.cast(1L);
                    }
                    return requiredType.cast(0L);
                }
                return requiredType.cast(0L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM data_year_readiness")) {
                return List.of(readinessRow);
            }
            if (sql.startsWith("SELECT policy_status")) {
                // 普通本科 / 专科存在政策，其它批次也存在但都触发 baseSupportLevel=QUERY_ONLY 分支。
                return List.of(Map.of(
                        "policy_status", "confirmed",
                        "max_volunteer_count", 96,
                        "major_per_school_count", 0,
                        "has_adjustment", 0));
            }
            if (sql.contains("data_score_line_gz")) {
                return List.of(
                        Map.of("batch", "普通本科批", "cnt", 12L),
                        Map.of("batch", "普通类高职专科批", "cnt", 8L));
            }
            if (sql.contains("data_major_score_gz")) {
                return List.of(
                        Map.of("batch", "普通本科批", "cnt", 20L),
                        Map.of("batch", "普通类高职专科批", "cnt", 6L));
            }
            if (sql.contains("data_admission_plan_gz")) {
                return List.of(
                        Map.of("data_batch", "普通本科批", "cnt", 30L),
                        Map.of("data_batch", "NORMAL_SPECIALIST", "cnt", 10L));
            }
            return List.of();
        }
    }
}
