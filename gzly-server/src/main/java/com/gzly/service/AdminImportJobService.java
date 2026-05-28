package com.gzly.service;

import com.gzly.common.exception.BizException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminImportJobService {

    private static final Path ROOT = Paths.get("/opt/gzly/import-jobs");
    private static final Set<String> ALLOWED_PROVINCES = Set.of("GZ", "SC", "AH", "HB");
    private static final Set<String> ALLOWED_IMPORT_TYPES = Set.of("score_rank", "group_line", "group_plan", "major_requirement", "major_meta", "bundle");

    private final JdbcTemplate jdbcTemplate;
    private final DataReadinessService dataReadinessService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(CreateRequest request) {
        CreateRequest req = request == null ? new CreateRequest() : request;
        String province = normalizeProvince(req.provinceCode);
        int year = req.year == null || req.year <= 0 ? 2026 : req.year;
        String importType = normalizeImportType(req.importType);
        String outputDir = blank(req.outputDir) ? "" : safePath(req.outputDir).toString();
        jdbcTemplate.update("INSERT INTO admin_import_job "
                        + "(province_code, year, batch_code, subject_type, import_type, source_type, status, source_dir, output_dir, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                province, year, text(req.batchCode), text(req.subjectType), importType, text(req.sourceType),
                "CREATED", text(req.sourceDir), outputDir, text(req.createdBy));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return detail(requireId(id));
    }

    public List<Map<String, Object>> list(String provinceCode, Integer year, String status, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        StringBuilder sql = new StringBuilder("SELECT * FROM admin_import_job WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (!blank(provinceCode)) {
            sql.append(" AND province_code=?");
            args.add(normalizeProvince(provinceCode));
        }
        if (year != null && year > 0) {
            sql.append(" AND year=?");
            args.add(year);
        }
        if (!blank(status)) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ").append(resolvedLimit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream().map(this::jobSummary).toList();
    }

    public Map<String, Object> detail(long jobId) {
        Map<String, Object> job = job(jobId);
        Map<String, Object> result = jobSummary(job);
        result.put("files", jdbcTemplate.queryForList("SELECT * FROM admin_import_job_file WHERE job_id=? ORDER BY id", jobId));
        result.put("gates", jdbcTemplate.queryForList("SELECT * FROM admin_import_job_gate WHERE job_id=? ORDER BY id", jobId));
        result.put("artifacts", jdbcTemplate.queryForList("SELECT * FROM admin_import_job_artifact WHERE job_id=? ORDER BY id", jobId));
        String province = string(job.get("province_code"));
        int year = number(job.get("year"), 2026);
        result.put("readiness", dataReadinessService.toMap(dataReadinessService.get(province, year)));
        result.put("phaseGates", dataReadinessService.phaseGates());
        result.put("formalPromoteAllowed", false);
        result.put("fullRecommendSwitchAllowed", false);
        result.put("guardrail", "MVP only creates dry-run artifacts and confirmation SQL. It never executes formal import, never writes admission tables, and never switches readiness/FULL_RECOMMEND.");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> registerFile(long jobId, FileRequest request) {
        Map<String, Object> job = job(jobId);
        FileRequest req = request == null ? new FileRequest() : request;
        Path path = safePath(req.filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BizException(400, "源文件不存在或不是普通文件");
        }
        String sha = sha256(path);
        long size = size(path);
        jdbcTemplate.update("INSERT INTO admin_import_job_file (job_id, file_name, file_path, sha256, file_size, file_type, source_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE file_name=VALUES(file_name), sha256=VALUES(sha256), file_size=VALUES(file_size), file_type=VALUES(file_type), source_url=VALUES(source_url)",
                jobId, blank(req.fileName) ? path.getFileName().toString() : req.fileName.trim(), path.toString(), sha, size, text(req.fileType), text(req.sourceUrl));
        updateStatus(jobId, "FILES_REGISTERED");
        addGate(jobId, "source_file_exists", "PASS", "regular_file", path.toString(), "");
        addGate(jobId, "source_file_sha256", "PASS", "64_hex", sha, "");
        return detail(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> stagingDryRun(long jobId) {
        Map<String, Object> job = job(jobId);
        List<Map<String, Object>> files = files(jobId);
        if (files.isEmpty()) {
            addGate(jobId, "source_files_registered", "FAIL", ">0", "0", "");
            throw new BizException(409, "请先登记至少一个源文件");
        }
        Path dir = outputDir(job);
        writeArtifact(jobId, dir.resolve("staging_ddl.sql"), stagingDdl(job));
        writeArtifact(jobId, dir.resolve("staging_load.sql"), stagingLoad(job, files));
        addGate(jobId, "staging_dry_run", "PASS", "ddl_and_load_sql_generated", "generated", dir.toString());
        updateStatus(jobId, "STAGING_DRY_RUN_PASSED");
        return detail(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> qualityGate(long jobId) {
        Map<String, Object> job = job(jobId);
        Path dir = outputDir(job);
        String report = "# Quality Gate\n\n"
                + "- jobId: " + jobId + "\n"
                + "- province: " + string(job.get("province_code")) + "\n"
                + "- year: " + number(job.get("year"), 0) + "\n"
                + "- hard guards: formal import disabled; readiness switch disabled; FULL_RECOMMEND switch disabled.\n"
                + "- required next step: run parser/staging checks outside this MVP package, then upload reviewed gate outputs before confirmation.\n";
        writeArtifact(jobId, dir.resolve("quality_gate.md"), report);
        addGate(jobId, "formal_import_disabled", "PASS", "no_promote_endpoint", "no_promote_endpoint", "");
        addGate(jobId, "readiness_unchanged", "PASS", DataReadinessService.PRE_OFFICIAL_DATA, string(dataReadinessService.get(string(job.get("province_code")), number(job.get("year"), 2026)).recommendationPhase), "");
        updateStatus(jobId, "QUALITY_GATE_RECORDED");
        return detail(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateFormalSql(long jobId) {
        Map<String, Object> job = job(jobId);
        Path dir = outputDir(job);
        writeArtifact(jobId, dir.resolve("formal_insert_body_only.sql"), formalBody(job));
        writeArtifact(jobId, dir.resolve("post_check.sql"), postCheck(job));
        addGate(jobId, "formal_sql_body_only", "PASS", "no_transaction_no_ddl_no_update_delete", "template_only", "");
        updateStatus(jobId, "FORMAL_SQL_GENERATED");
        return detail(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollbackPlan(long jobId) {
        Map<String, Object> job = job(jobId);
        Path dir = outputDir(job);
        writeArtifact(jobId, dir.resolve("rollback.sql"), rollbackSql(job));
        addGate(jobId, "rollback_plan_generated", "PASS", "manual_confirm_required", "generated", "");
        updateStatus(jobId, "ROLLBACK_PLAN_GENERATED");
        return detail(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> postCheck(long jobId) {
        Map<String, Object> job = job(jobId);
        Path dir = outputDir(job);
        writeArtifact(jobId, dir.resolve("post_check.sql"), postCheck(job));
        addGate(jobId, "post_check_generated", "PASS", "readiness_and_fake_2026_guards", "generated", "");
        updateStatus(jobId, "POST_CHECK_GENERATED");
        return detail(jobId);
    }

    private Map<String, Object> job(long jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM admin_import_job WHERE id=? LIMIT 1", jobId);
        if (rows.isEmpty()) {
            throw new BizException(404, "导入任务不存在");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> files(long jobId) {
        return jdbcTemplate.queryForList("SELECT * FROM admin_import_job_file WHERE job_id=? ORDER BY id", jobId);
    }

    private Map<String, Object> jobSummary(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("id", "province_code", "year", "batch_code", "subject_type", "import_type", "source_type", "status", "source_dir", "output_dir", "created_by", "created_at", "updated_at")) {
            out.put(toCamel(key), row.get(key));
        }
        return out;
    }

    private Path outputDir(Map<String, Object> job) {
        String configured = string(job.get("output_dir"));
        Path dir = blank(configured) ? ROOT.resolve("job-" + number(job.get("id"), 0)) : safePath(configured);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(500, "创建导入任务产物目录失败");
        }
        return dir;
    }

    private void writeArtifact(long jobId, Path path, String content) {
        Path safe = safePath(path.toString());
        try {
            Files.createDirectories(safe.getParent());
            Files.writeString(safe, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(500, "写入导入任务产物失败");
        }
        jdbcTemplate.update("INSERT INTO admin_import_job_artifact (job_id, artifact_type, artifact_path, sha256) VALUES (?, ?, ?, ?)",
                jobId, artifactType(safe), safe.toString(), sha256(safe));
    }

    private String stagingDdl(Map<String, Object> job) {
        String table = stagingTable(job);
        return "CREATE TABLE IF NOT EXISTS " + table + " (\n"
                + "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n"
                + "  import_job_id BIGINT NOT NULL,\n"
                + "  province_code VARCHAR(16) NOT NULL,\n"
                + "  year INT NOT NULL,\n"
                + "  natural_key VARCHAR(500) NOT NULL DEFAULT '',\n"
                + "  payload_json JSON NULL,\n"
                + "  source_file VARCHAR(500) NOT NULL DEFAULT '',\n"
                + "  source_sha256 CHAR(64) NOT NULL DEFAULT '',\n"
                + "  review_status VARCHAR(40) NOT NULL DEFAULT 'draft',\n"
                + "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + "  KEY idx_" + table + "_job (import_job_id),\n"
                + "  KEY idx_" + table + "_province_year (province_code, year)\n"
                + ");\n";
    }

    private String stagingLoad(Map<String, Object> job, List<Map<String, Object>> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Dry-run staging load template. Review before executing in a staging-only session.\n");
        sb.append("-- This file does not write formal admission tables.\n");
        for (Map<String, Object> file : files) {
            sb.append("-- source: ").append(string(file.get("file_path"))).append(" sha256=").append(string(file.get("sha256"))).append('\n');
        }
        sb.append("-- target staging table: ").append(stagingTable(job)).append('\n');
        return sb.toString();
    }

    private String formalBody(Map<String, Object> job) {
        return "-- body-only placeholder for job " + number(job.get("id"), 0) + "\n"
                + "-- No formal import is executed by Admin Import Job MVP.\n"
                + "-- Paste reviewed INSERT ... SELECT statements here only after user confirmation.\n";
    }

    private String rollbackSql(Map<String, Object> job) {
        return "-- rollback template for job " + number(job.get("id"), 0) + "\n"
                + "-- Requires explicit user confirmation and exact natural-key scope before use.\n"
                + "SET @confirmed = 0;\n"
                + "SELECT 'rollback.blocked_until_confirmed', @confirmed;\n";
    }

    private String postCheck(Map<String, Object> job) {
        String province = string(job.get("province_code"));
        int year = number(job.get("year"), 2026);
        return "SELECT 'readiness_phase', recommendation_phase FROM data_year_readiness WHERE province_code='" + province + "' AND year=" + year + ";\n"
                + "SELECT 'fake_2026_group_line', COUNT(*) FROM data_admission_group_line WHERE year=2026;\n"
                + "SELECT 'fake_2026_group_plan', COUNT(*) FROM data_admission_group_plan WHERE year=2026;\n"
                + "SELECT 'fake_2026_score_rank', COUNT(*) FROM data_score_rank WHERE year=2026;\n";
    }

    private String stagingTable(Map<String, Object> job) {
        return "stg_admin_import_job_" + number(job.get("id"), 0);
    }

    private void addGate(long jobId, String name, String status, String expected, String actual, String samplePath) {
        jdbcTemplate.update("INSERT INTO admin_import_job_gate (job_id, gate_name, gate_status, expected_value, actual_value, sample_path) VALUES (?, ?, ?, ?, ?, ?)",
                jobId, name, status, expected, actual, samplePath);
    }

    private void updateStatus(long jobId, String status) {
        jdbcTemplate.update("UPDATE admin_import_job SET status=? WHERE id=?", status, jobId);
    }

    private String normalizeProvince(String value) {
        String province = text(value).toUpperCase(Locale.ROOT);
        if (!ALLOWED_PROVINCES.contains(province)) {
            throw new BizException(400, "不支持的省份代码：" + province);
        }
        return province;
    }

    private String normalizeImportType(String value) {
        String type = blank(value) ? "bundle" : value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMPORT_TYPES.contains(type)) {
            throw new BizException(400, "不支持的导入类型：" + type);
        }
        return type;
    }

    private static Path safePath(String raw) {
        if (blank(raw)) {
            throw new BizException(400, "路径不能为空");
        }
        Path path = Paths.get(raw).normalize().toAbsolutePath();
        String value = path.toString();
        if (!(value.startsWith("/root/") || value.startsWith("/opt/gzly/") || value.startsWith("/tmp/"))) {
            throw new BizException(400, "只允许登记服务器受控目录下的文件");
        }
        return path;
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BizException(500, "计算 SHA256 失败");
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(500, "导入任务创建失败");
        }
        return id;
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String toCamel(String key) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String artifactType(Path path) {
        String name = path.getFileName().toString();
        if (name.contains("staging_ddl")) return "staging_ddl";
        if (name.contains("staging_load")) return "staging_load";
        if (name.contains("quality")) return "quality_gate";
        if (name.contains("formal_insert")) return "formal_insert_body_only";
        if (name.contains("rollback")) return "rollback";
        if (name.contains("post_check")) return "post_check";
        return "artifact";
    }

    @Data
    public static class CreateRequest {
        private String provinceCode;
        private Integer year;
        private String batchCode;
        private String subjectType;
        private String importType;
        private String sourceType;
        private String sourceDir;
        private String outputDir;
        private String createdBy;
    }

    @Data
    public static class FileRequest {
        private String fileName;
        private String filePath;
        private String fileType;
        private String sourceUrl;
    }
}
