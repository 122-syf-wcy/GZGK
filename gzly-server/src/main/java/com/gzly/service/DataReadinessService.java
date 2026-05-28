package com.gzly.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataReadinessService {

    public static final String PRE_OFFICIAL_DATA = "PRE_OFFICIAL_DATA";
    public static final String OFFICIAL_DATA_PARTIAL = "OFFICIAL_DATA_PARTIAL";
    public static final String OFFICIAL_DATA_IMPORTED = "OFFICIAL_DATA_IMPORTED";
    public static final String MODEL_RETRAINED = "MODEL_RETRAINED";
    public static final String FULL_RECOMMEND_READY = "FULL_RECOMMEND_READY";

    private final JdbcTemplate jdbcTemplate;

    public DataReadinessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Readiness get(String provinceCode, int year) {
        String province = normalizeProvince(provinceCode);
        String sql = "SELECT province_code, year, policy_ready, score_segment_ready, admission_plan_ready, major_requirement_ready, "
                + "major_meta_ready, ml_training_ready, historical_training_ready, recommendation_phase, "
                + "COALESCE(latest_import_batch_id,'') AS latest_import_batch_id, "
                + "DATE_FORMAT(COALESCE(last_checked_at,NOW()), '%Y-%m-%dT%H:%i:%s') AS last_checked_at, "
                + "COALESCE(remarks,'') AS remarks "
                + "FROM data_year_readiness WHERE province_code=? AND year=? LIMIT 1";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, province, year);
            if (!rows.isEmpty()) {
                return fromRow(province, year, rows.get(0));
            }
        } catch (DataAccessException ignored) {
            // Conservative default below.
        }
        Readiness r = new Readiness();
        r.provinceCode = province;
        r.year = year;
        r.recommendationPhase = PRE_OFFICIAL_DATA;
        r.latestImportBatchId = "";
        r.lastCheckedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        r.remarks = province + " " + year + " 官方数据未完整核验，仅允许展示政策和数据缺口。";
        return r;
    }

    public boolean isFullRecommendReady(String provinceCode, int year) {
        Readiness r = get(provinceCode, year);
        return r.policyReady && r.scoreSegmentReady && r.admissionPlanReady
                && r.majorRequirementReady && r.majorMetaReady && r.mlTrainingReady
                && FULL_RECOMMEND_READY.equalsIgnoreCase(r.recommendationPhase);
    }

    public Map<String, Object> toMap(Readiness r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provinceCode", r.provinceCode);
        data.put("year", r.year);
        data.put("policyReady", r.policyReady);
        data.put("scoreSegmentReady", r.scoreSegmentReady);
        data.put("admissionPlanReady", r.admissionPlanReady);
        data.put("majorRequirementReady", r.majorRequirementReady);
        data.put("majorMetaReady", r.majorMetaReady);
        data.put("mlTrainingReady", r.mlTrainingReady);
        data.put("historicalTrainingReady", r.historicalTrainingReady);
        data.put("recommendationPhase", r.recommendationPhase);
        data.put("latestImportBatchId", r.latestImportBatchId);
        data.put("lastCheckedAt", r.lastCheckedAt);
        data.put("remarks", r.remarks);
        data.put("phaseGate", phaseGate(r.recommendationPhase));
        return data;
    }

    public List<Map<String, Object>> phaseGates() {
        return List.of(
                phaseGate(PRE_OFFICIAL_DATA),
                phaseGate(OFFICIAL_DATA_PARTIAL),
                phaseGate(OFFICIAL_DATA_IMPORTED),
                phaseGate(MODEL_RETRAINED),
                phaseGate(FULL_RECOMMEND_READY)
        );
    }

    public Map<String, Object> phaseGate(String phase) {
        String normalized = string(phase, PRE_OFFICIAL_DATA).toUpperCase();
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("phase", normalized);
        switch (normalized) {
            case OFFICIAL_DATA_PARTIAL -> {
                gate.put("frontText", "官方数据部分到位");
                gate.put("apiBehavior", "仅对已导入并通过门禁的批次展示更完整的数据状态，未齐批次继续历史估算或只查策略。");
                gate.put("entryGate", List.of("2026 官方源文件已登记", "staging dry-run 通过", "quality gate 无硬阻塞"));
                gate.put("rollback", "回退到 PRE_OFFICIAL_DATA，并下线相关未确认批次的完整数据入口。");
            }
            case OFFICIAL_DATA_IMPORTED -> {
                gate.put("frontText", "官方数据已导入待训练");
                gate.put("apiBehavior", "batch-support 可展示官方行数和 post-check，推荐仍不进入 FULL。");
                gate.put("entryGate", List.of("formal import 经人工确认执行", "post-check 通过", "无 fake 2026", "readiness 数据门禁全绿但模型未激活"));
                gate.put("rollback", "执行确认过的招生数据 rollback，并恢复上一阶段 readiness。");
            }
            case MODEL_RETRAINED -> {
                gate.put("frontText", "模型已重训待灰度");
                gate.put("apiBehavior", "仅灰度/管理员验证模型结果，不向普通用户开放完整数据生成。");
                gate.put("entryGate", List.of("训练集固定", "离线评估通过", "线上影子请求无异常", "人工复核样本通过"));
                gate.put("rollback", "停用新模型版本，回退 fallback-rule 或上一稳定模型。");
            }
            case FULL_RECOMMEND_READY -> {
                gate.put("frontText", "完整数据生成可开放");
                gate.put("apiBehavior", "普通主批可返回完整数据生成；非普通批仍按各自资格/综合分门禁隔离。");
                gate.put("entryGate", List.of("官方数据导入完成", "模型重训完成", "灰度和合规文案通过", "人工确认开启 FULL_RECOMMEND"));
                gate.put("rollback", "关闭 FULL_RECOMMEND，回退 MODEL_RETRAINED 或 PRE_OFFICIAL_DATA 展示。");
            }
            default -> {
                gate.put("phase", PRE_OFFICIAL_DATA);
                gate.put("frontText", "官方数据未发布");
                gate.put("apiBehavior", "targetYear 仅用于展示；使用历史数据窗口做估算，非普通批只查策略和缺口。");
                gate.put("entryGate", List.of("2026 官方数据尚未完整发布", "不得伪造 2026", "不得开启 FULL_RECOMMEND"));
                gate.put("rollback", "当前阶段无需数据回滚；保持历史估算和查询模式。");
            }
        }
        return gate;
    }

    private Readiness fromRow(String province, int year, Map<String, Object> row) {
        Readiness r = new Readiness();
        r.provinceCode = province;
        r.year = toInt(row.get("year"), year);
        r.policyReady = bool(row.get("policy_ready"));
        r.scoreSegmentReady = bool(row.get("score_segment_ready"));
        r.admissionPlanReady = bool(row.get("admission_plan_ready"));
        r.majorRequirementReady = bool(row.get("major_requirement_ready"));
        r.majorMetaReady = bool(row.get("major_meta_ready"));
        r.mlTrainingReady = bool(row.get("ml_training_ready"));
        r.historicalTrainingReady = bool(row.get("historical_training_ready"));
        r.recommendationPhase = string(row.get("recommendation_phase"), PRE_OFFICIAL_DATA);
        r.latestImportBatchId = string(row.get("latest_import_batch_id"), "");
        r.lastCheckedAt = string(row.get("last_checked_at"), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        r.remarks = string(row.get("remarks"), province + " " + year + " 官方数据未完整核验。");
        return r;
    }

    private static String normalizeProvince(String provinceCode) {
        return provinceCode == null || provinceCode.isBlank() ? ProvincePolicyService.GZ : provinceCode.trim().toUpperCase();
    }

    private static boolean bool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }

    private static int toInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    public static class Readiness {
        public String provinceCode;
        public int year;
        public boolean policyReady;
        public boolean scoreSegmentReady;
        public boolean admissionPlanReady;
        public boolean majorRequirementReady;
        public boolean majorMetaReady;
        public boolean mlTrainingReady;
        public boolean historicalTrainingReady;
        public String recommendationPhase;
        public String latestImportBatchId;
        public String lastCheckedAt;
        public String remarks;
    }
}
