package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchSupportService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 针对 {@link AdminController#gzBacktestBatchSupport(Integer)} 的契约测试：
 *
 * <ul>
 *   <li>允许 admin 直接传历史年份 backtest（{@code publicYearLocked=false}）；</li>
 *   <li>{@code batchSupportService} 注入缺失时抛 {@link BizException}（线上 graceful 降级）；</li>
 *   <li>端点只读、不写库、不触发推荐生成、不调用 ML。</li>
 * </ul>
 *
 * AdminController 主要承担管理面 mapper 编排，与 batch-support 端点无关；
 * 构造器传入 16 个 null mapper/util 不影响该端点路径，因为它仅依赖
 * {@code @Autowired(required = false) BatchSupportService}。
 */
class AdminControllerBatchSupportTest {

    @Test
    void adminBatchSupport_withHistoricalYear_shouldAllowBacktest() {
        AdminController controller = newController(buildBatchSupportService());

        Result<BatchSupportService.BatchSupportResponse> result = controller.gzBacktestBatchSupport(2024);

        assertThat(result.getCode()).isEqualTo(0);
        BatchSupportService.BatchSupportResponse response = result.getData();
        assertThat(response.getYear()).isEqualTo(2024);
        assertThat(response.isPublicYearLocked()).isFalse();
    }

    @Test
    void adminBatchSupport_withActiveYear_shouldExposeEstimateRecommend() {
        AdminController controller = newController(buildBatchSupportService());

        Result<BatchSupportService.BatchSupportResponse> result = controller.gzBacktestBatchSupport(2026);

        assertThat(result.getCode()).isEqualTo(0);
        BatchSupportService.BatchSupportResponse response = result.getData();
        assertThat(response.getYear()).isEqualTo(2026);
        assertThat(response.isPublicYearLocked()).isFalse();
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(itemSupportLevel(response, "NORMAL_UNDERGRADUATE")).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(itemSupportLevel(response, "NORMAL_SPECIALTY")).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(itemSupportLevel(response, "EARLY_A_B")).isEqualTo("QUERY_ONLY");
    }

    @Test
    void adminBatchSupport_whenServiceMissing_shouldThrowGracefulError() {
        AdminController controller = newController(null);

        assertThatThrownBy(() -> controller.gzBacktestBatchSupport(2026))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("批次支持服务不可用");
    }

    private static AdminController newController(BatchSupportService batchSupportService) {
        AdminController controller = new AdminController(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        if (batchSupportService != null) {
            ReflectionTestUtils.setField(controller, "batchSupportService", batchSupportService);
        }
        return controller;
    }

    private static BatchSupportService buildBatchSupportService() {
        AdmissionYearService admissionYearService = new AdmissionYearService();
        admissionYearService.setActiveAdmissionYear(2026);
        admissionYearService.setHistoryYears("2025,2024");
        admissionYearService.setTrainingYears("2024,2025");
        JdbcTemplate jdbc = new ReadinessAwareJdbcTemplate(readinessRow(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA));
        return new BatchSupportService(jdbc, admissionYearService);
    }

    private static String itemSupportLevel(BatchSupportService.BatchSupportResponse response, String code) {
        return response.getItems().stream()
                .filter(item -> code.equals(item.getBatchCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("batch not found: " + code))
                .getSupportLevel();
    }

    private static Map<String, Object> readinessRow(String phase) {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_ready", 0);
        row.put("score_segment_ready", 0);
        row.put("admission_plan_ready", 0);
        row.put("major_requirement_ready", 0);
        row.put("major_meta_ready", 0);
        row.put("ml_training_ready", 0);
        row.put("historical_training_ready", 1);
        row.put("recommendation_phase", phase);
        row.put("latest_import_batch_id", "");
        row.put("last_checked_at", "");
        row.put("remarks", "admin-batch-support-test");
        return row;
    }

    /** 同 VolunteerRecommendControllerBatchSupportTest 风格，仅响应 readiness/支持矩阵相关 SQL。 */
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
