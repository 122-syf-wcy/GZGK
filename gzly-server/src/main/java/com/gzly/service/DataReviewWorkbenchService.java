package com.gzly.service;

import com.gzly.common.exception.BizException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Review-only workbench for CQ/GS/XJ Level 2 manual review.
 *
 * <p>This service intentionally reads and writes only fixed files under a
 * whitelisted review-only directory. It does not write business tables,
 * does not create processed files, and does not trigger imports.</p>
 */
@Service
public class DataReviewWorkbenchService {

    private static final Set<String> ALLOWED_PROVINCES = Set.of("CQ", "GS", "XJ");
    private static final Set<String> ALLOWED_DECISIONS = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final String TEMPLATE_FILE = "group_plan_manual_intake_template.csv";
    private static final String CHECKLIST_FILE = "manual_review_checklist_20260623.tsv";
    private static final String SIDECAR_FILE = "review_decisions_sidecar.csv";
    private static final String REVIEWED_FILE = "reviewed_group_plan_manual_intake.csv";
    private static final String APPROVED_FILE = "approved_only.csv";
    private static final String REJECTED_FILE = "rejected_rows.tsv";
    private static final String SUMMARY_FILE = "review_summary.md";

    private final Path reviewDir;

    public DataReviewWorkbenchService(
            @Value("${gzly.data-review.cq-gs-xj.dir:/root/gzly_cq_gs_xj_level2_data_20260614/evidence/school_official_plan_availability_20260623/manual_intake_review_only_20260623}")
            String reviewDir) {
        this.reviewDir = Path.of(reviewDir).toAbsolutePath().normalize();
    }

    public ReviewListResponse list(ReviewQuery query) {
        List<ReviewRow> all = loadMergedRows();
        List<ReviewRow> filtered = all.stream()
                .filter(row -> isBlank(query.getProvinceCode()) || row.getProvinceCode().equalsIgnoreCase(query.getProvinceCode().trim()))
                .filter(row -> isBlank(query.getSchool()) || row.getSchoolName().contains(query.getSchool().trim())
                        || row.getSchoolCode().contains(query.getSchool().trim()))
                .filter(row -> isBlank(query.getStatus()) || row.getCurrentStatus().equalsIgnoreCase(query.getStatus().trim()))
                .filter(row -> query.getSourceUrlEmpty() == null
                        || query.getSourceUrlEmpty().equals(isBlank(row.getSourceUrl())))
                .filter(row -> query.getPlanCountAnomaly() == null
                        || query.getPlanCountAnomaly().equals(row.isPlanCountAnomaly()))
                .sorted(Comparator.comparing(ReviewRow::getLineNo))
                .toList();

        int page = Math.max(1, query.getPage());
        int size = Math.min(Math.max(1, query.getSize()), 200);
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());

        ReviewListResponse response = new ReviewListResponse();
        response.setItems(filtered.subList(from, to));
        response.setTotal(filtered.size());
        response.setPage(page);
        response.setPageSize(size);
        response.setStats(buildStats(all));
        response.setReviewDirectory(reviewDir.toString());
        response.setTemplatePath(resolveAllowed(TEMPLATE_FILE).toString());
        response.setSidecarPath(resolveAllowed(SIDECAR_FILE).toString());
        response.setValidatorCommand(validatorCommand());
        response.setSafetyNotice("人工审核通过不等于可导入正式库。导入前仍需 dry-run、preflight 和用户确认。");
        return response;
    }

    public ReviewRow saveDecision(ReviewDecisionRequest request) {
        int lineNo = request.getLineNo();
        if (lineNo <= 1) {
            throw new BizException("无效的审核行号");
        }
        List<ReviewRow> currentRows = loadMergedRows();
        boolean rowExists = currentRows.stream().anyMatch(row -> row.getLineNo() == lineNo);
        if (!rowExists) {
            throw new BizException("审核行不存在");
        }
        String decision = normalizeDecision(request.getDecision());
        String reviewer = sanitizeCell(request.getReviewer());
        String note = sanitizeCell(request.getReviewNote());
        String reason = sanitizeCell(request.getRejectReason());
        if ("APPROVED".equals(decision) && reviewer.isBlank()) {
            throw new BizException("通过审核必须填写 reviewer");
        }
        if ("REJECTED".equals(decision) && reason.isBlank()) {
            throw new BizException("拒绝审核必须填写 reject_reason");
        }
        Map<Integer, ReviewDecision> decisions = loadDecisions();
        ReviewDecision item = decisions.getOrDefault(lineNo, new ReviewDecision());
        item.setLineNo(lineNo);
        item.setDecision(decision);
        item.setReviewer(reviewer);
        item.setReviewNote(note);
        item.setRejectReason(reason);
        item.setUpdatedAt(LocalDateTime.now().toString());
        decisions.put(lineNo, item);
        writeDecisions(decisions);
        return loadMergedRows().stream()
                .filter(row -> row.getLineNo() == lineNo)
                .findFirst()
                .orElseThrow(() -> new BizException("审核行不存在"));
    }

    public ExportResponse exportReviewedFiles() {
        List<ReviewRow> rows = loadMergedRows();
        List<ReviewRow> approved = rows.stream().filter(row -> "APPROVED".equals(row.getCurrentStatus())).toList();
        List<ReviewRow> rejected = rows.stream().filter(row -> "REJECTED".equals(row.getCurrentStatus())).toList();

        writeReviewedCsv(rows, resolveAllowed(REVIEWED_FILE));
        writeReviewedCsv(approved, resolveAllowed(APPROVED_FILE));
        writeRejectedTsv(rejected, resolveAllowed(REJECTED_FILE));
        writeSummary(rows, approved, rejected, resolveAllowed(SUMMARY_FILE));

        ExportResponse response = new ExportResponse();
        response.setReviewedPath(resolveAllowed(REVIEWED_FILE).toString());
        response.setApprovedPath(resolveAllowed(APPROVED_FILE).toString());
        response.setRejectedPath(resolveAllowed(REJECTED_FILE).toString());
        response.setSummaryPath(resolveAllowed(SUMMARY_FILE).toString());
        response.setApprovedCount(approved.size());
        response.setRejectedCount(rejected.size());
        response.setPendingCount((int) rows.stream().filter(row -> "PENDING".equals(row.getCurrentStatus())).count());
        response.setImportAllowed(false);
        response.setNotice("已导出 review-only 审核结果；仍未生成 processed，仍禁止 import。");
        return response;
    }

    private ReviewStats buildStats(List<ReviewRow> rows) {
        ReviewStats stats = new ReviewStats();
        stats.setTotal(rows.size());
        stats.setApproved((int) rows.stream().filter(row -> "APPROVED".equals(row.getCurrentStatus())).count());
        stats.setRejected((int) rows.stream().filter(row -> "REJECTED".equals(row.getCurrentStatus())).count());
        stats.setPending((int) rows.stream().filter(row -> "PENDING".equals(row.getCurrentStatus())).count());
        stats.setPlanCountAnomaly((int) rows.stream().filter(ReviewRow::isPlanCountAnomaly).count());
        stats.setSourceUrlEmpty((int) rows.stream().filter(row -> isBlank(row.getSourceUrl())).count());
        Map<String, Long> byProvince = rows.stream()
                .collect(Collectors.groupingBy(ReviewRow::getProvinceCode, LinkedHashMap::new, Collectors.counting()));
        stats.setByProvince(byProvince);
        return stats;
    }

    private List<ReviewRow> loadMergedRows() {
        List<Map<String, String>> templateRows = readDelimited(resolveAllowed(TEMPLATE_FILE), ',');
        Map<Integer, Map<String, String>> checklistByLine = readDelimited(resolveAllowed(CHECKLIST_FILE), '\t').stream()
                .filter(row -> parseInt(row.get("source_line"), -1) > 1)
                .collect(Collectors.toMap(row -> parseInt(row.get("source_line"), -1), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Integer, ReviewDecision> decisions = loadDecisions();

        List<ReviewRow> rows = new ArrayList<>();
        int lineNo = 2;
        for (Map<String, String> raw : templateRows) {
            ReviewDecision decision = decisions.get(lineNo);
            Map<String, String> checklist = checklistByLine.getOrDefault(lineNo, Map.of());
            ReviewRow row = new ReviewRow();
            row.setLineNo(lineNo);
            row.setProvinceCode(upper(raw.get("province_code")));
            row.setYear(raw.getOrDefault("year", ""));
            row.setSchoolCode(raw.getOrDefault("school_id", ""));
            row.setSchoolName(raw.getOrDefault("university_name", ""));
            row.setBatch(raw.getOrDefault("batch", ""));
            row.setSubjectCategory(raw.getOrDefault("subject_type", ""));
            row.setMajorGroupCode(raw.getOrDefault("major_group_code", ""));
            row.setMajorGroupName(raw.getOrDefault("major_group_name", ""));
            row.setMajorName(raw.getOrDefault("major_name", ""));
            row.setPlanCount(raw.getOrDefault("plan_count", ""));
            row.setSourceName(raw.getOrDefault("source_name", ""));
            row.setSourceUrl(raw.getOrDefault("source_url", ""));
            row.setEvidenceFile(firstNonBlank(raw.get("source_file_path"), checklist.get("evidence_file")));
            row.setConfidence(raw.getOrDefault("confidence", ""));
            row.setNotes(raw.getOrDefault("notes", ""));
            row.setReviewer(raw.getOrDefault("reviewer", ""));
            row.setOriginalStatus(firstNonBlank(checklist.get("review_status"), "PENDING_HUMAN_REVIEW"));
            String status = decision == null ? "PENDING" : normalizeDecision(decision.getDecision());
            row.setCurrentStatus(status);
            row.setReviewNote(decision == null ? "" : decision.getReviewNote());
            row.setRejectReason(decision == null ? "" : decision.getRejectReason());
            row.setUpdatedAt(decision == null ? "" : decision.getUpdatedAt());
            row.setReviewerToFill(decision == null ? "" : decision.getReviewer());
            row.setPlanCountAnomaly(parseInt(row.getPlanCount(), -1) <= 0);
            row.setSourceUrlEmpty(isBlank(row.getSourceUrl()));
            rows.add(row);
            lineNo++;
        }
        return rows;
    }

    private List<Map<String, String>> readDelimited(Path path, char delimiter) {
        if (!Files.exists(path)) {
            throw new BizException("审核文件不存在: " + path.getFileName());
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = parseDelimitedLine(headerLine, delimiter);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseDelimitedLine(line, delimiter);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString());
        return out;
    }

    private String toDelimitedLine(List<String> values, char delimiter) {
        return values.stream()
                .map(value -> escapeDelimited(value == null ? "" : value, delimiter))
                .collect(Collectors.joining(String.valueOf(delimiter)));
    }

    private String escapeDelimited(String value, char delimiter) {
        String cleaned = value.replace("\r", " ").replace("\n", " ").trim();
        if (cleaned.indexOf(delimiter) >= 0 || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private Map<Integer, ReviewDecision> loadDecisions() {
        Path path = resolveAllowed(SIDECAR_FILE);
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        return readDelimited(path, ',').stream()
                .map(row -> {
                    ReviewDecision d = new ReviewDecision();
                    d.setLineNo(parseInt(row.get("line_no"), -1));
                    d.setDecision(normalizeDecision(row.get("decision")));
                    d.setReviewer(row.getOrDefault("reviewer", ""));
                    d.setReviewNote(row.getOrDefault("review_note", ""));
                    d.setRejectReason(row.getOrDefault("reject_reason", ""));
                    d.setUpdatedAt(row.getOrDefault("updated_at", ""));
                    return d;
                })
                .filter(d -> d.getLineNo() > 1)
                .collect(Collectors.toMap(ReviewDecision::getLineNo, Function.identity(), (a, b) -> b, LinkedHashMap::new));
    }

    private void writeDecisions(Map<Integer, ReviewDecision> decisions) {
        Path path = resolveAllowed(SIDECAR_FILE);
        try {
            Files.createDirectories(reviewDir);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("line_no,decision,reviewer,review_note,reject_reason,updated_at");
                writer.newLine();
                decisions.values().stream()
                        .sorted(Comparator.comparingInt(ReviewDecision::getLineNo))
                        .forEach(decision -> writeLine(writer, List.of(
                                String.valueOf(decision.getLineNo()),
                                normalizeDecision(decision.getDecision()),
                                sanitizeCell(decision.getReviewer()),
                                sanitizeCell(decision.getReviewNote()),
                                sanitizeCell(decision.getRejectReason()),
                                sanitizeCell(decision.getUpdatedAt())
                        ), ','));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeReviewedCsv(List<ReviewRow> rows, Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("line_no,province_code,year,batch,subject_category,school_code,school_name,major_group_code,major_group_name,major_name,plan_count,source_name,source_url,evidence_file,current_status,reviewer,review_note,reject_reason,confidence,notes");
            writer.newLine();
            for (ReviewRow row : rows) {
                writeLine(writer, List.of(
                        String.valueOf(row.getLineNo()),
                        row.getProvinceCode(),
                        row.getYear(),
                        row.getBatch(),
                        row.getSubjectCategory(),
                        row.getSchoolCode(),
                        row.getSchoolName(),
                        row.getMajorGroupCode(),
                        row.getMajorGroupName(),
                        row.getMajorName(),
                        row.getPlanCount(),
                        row.getSourceName(),
                        row.getSourceUrl(),
                        row.getEvidenceFile(),
                        row.getCurrentStatus(),
                        firstNonBlank(row.getReviewerToFill(), row.getReviewer()),
                        row.getReviewNote(),
                        row.getRejectReason(),
                        row.getConfidence(),
                        row.getNotes()
                ), ',');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeRejectedTsv(List<ReviewRow> rows, Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("line_no\tprovince_code\tschool_name\tmajor_name\treject_reason\treview_note\tsource_url\tevidence_file");
            writer.newLine();
            for (ReviewRow row : rows) {
                writeLine(writer, List.of(
                        String.valueOf(row.getLineNo()),
                        row.getProvinceCode(),
                        row.getSchoolName(),
                        row.getMajorName(),
                        row.getRejectReason(),
                        row.getReviewNote(),
                        row.getSourceUrl(),
                        row.getEvidenceFile()
                ), '\t');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeSummary(List<ReviewRow> rows, List<ReviewRow> approved, List<ReviewRow> rejected, Path path) {
        ReviewStats stats = buildStats(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("# CQ/GS/XJ 人工审核工作台导出摘要\n\n");
        sb.append("- 导出时间：").append(LocalDateTime.now()).append("\n");
        sb.append("- 总行数：").append(stats.getTotal()).append("\n");
        sb.append("- APPROVED：").append(approved.size()).append("\n");
        sb.append("- REJECTED：").append(rejected.size()).append("\n");
        sb.append("- PENDING：").append(stats.getPending()).append("\n");
        sb.append("- 是否生成 processed：NO\n");
        sb.append("- 是否 import：NO\n");
        sb.append("- 是否允许写 DB：NO\n");
        sb.append("- 说明：人工审核通过不等于可导入正式库；仍需 dry-run、preflight 和用户确认。\n");
        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLine(BufferedWriter writer, List<String> values, char delimiter) {
        try {
            writer.write(toDelimitedLine(values, delimiter));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolveAllowed(String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new BizException("非法文件名");
        }
        Path path = reviewDir.resolve(filename).normalize();
        if (!path.startsWith(reviewDir)) {
            throw new BizException("非法审核文件路径");
        }
        return path;
    }

    private String validatorCommand() {
        return "cd /root/gzly_cq_gs_xj_level2_data_20260614 && "
                + "GZLY_MANUAL_INTAKE_DIR=" + reviewDir
                + " python3 scripts/validate_manual_intake_dry_run.py "
                + "--output-json preflight/manual_review_validator_result_20260623.json "
                + "--output-md reports/manual_review_validator_result_20260623.md";
    }

    private String normalizeDecision(String decision) {
        String value = upper(decision);
        if (value.isBlank()) {
            return "PENDING";
        }
        if (!ALLOWED_DECISIONS.contains(value)) {
            throw new BizException("审核状态仅支持 PENDING / APPROVED / REJECTED");
        }
        return value;
    }

    private String sanitizeCell(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 1000) {
            trimmed = trimmed.substring(0, 1000);
        }
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'" + trimmed;
        }
        return trimmed;
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (!isBlank(second) ? second : "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Data
    public static class ReviewQuery {
        private int page = 1;
        private int size = 50;
        private String provinceCode;
        private String school;
        private String status;
        private Boolean sourceUrlEmpty;
        private Boolean planCountAnomaly;
    }

    @Data
    public static class ReviewDecisionRequest {
        private int lineNo;
        private String decision;
        private String reviewer;
        private String reviewNote;
        private String rejectReason;
    }

    @Data
    public static class ReviewDecision {
        private int lineNo;
        private String decision = "PENDING";
        private String reviewer = "";
        private String reviewNote = "";
        private String rejectReason = "";
        private String updatedAt = "";
    }

    @Data
    public static class ReviewRow {
        private int lineNo;
        private String provinceCode;
        private String year;
        private String schoolCode;
        private String schoolName;
        private String batch;
        private String subjectCategory;
        private String majorGroupCode;
        private String majorGroupName;
        private String majorName;
        private String planCount;
        private String sourceName;
        private String sourceUrl;
        private String evidenceFile;
        private String originalStatus;
        private String currentStatus;
        private String reviewer;
        private String reviewerToFill;
        private String reviewNote;
        private String rejectReason;
        private String confidence;
        private String notes;
        private String updatedAt;
        private boolean sourceUrlEmpty;
        private boolean planCountAnomaly;
    }

    @Data
    public static class ReviewStats {
        private int total;
        private int pending;
        private int approved;
        private int rejected;
        private int sourceUrlEmpty;
        private int planCountAnomaly;
        private Map<String, Long> byProvince = new LinkedHashMap<>();
    }

    @Data
    public static class ReviewListResponse {
        private List<ReviewRow> items = List.of();
        private int total;
        private int page;
        private int pageSize;
        private ReviewStats stats;
        private String reviewDirectory;
        private String templatePath;
        private String sidecarPath;
        private String validatorCommand;
        private String safetyNotice;
    }

    @Data
    public static class ExportResponse {
        private String reviewedPath;
        private String approvedPath;
        private String rejectedPath;
        private String summaryPath;
        private int approvedCount;
        private int rejectedCount;
        private int pendingCount;
        private boolean importAllowed;
        private String notice;
    }
}
