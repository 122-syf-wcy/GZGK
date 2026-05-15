package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.exception.BizException;
import com.gzly.entity.AdminImportJob;
import com.gzly.entity.AdminImportJobArtifact;
import com.gzly.entity.AdminImportJobFile;
import com.gzly.entity.AdminImportJobGate;
import com.gzly.mapper.AdminImportJobArtifactMapper;
import com.gzly.mapper.AdminImportJobFileMapper;
import com.gzly.mapper.AdminImportJobGateMapper;
import com.gzly.mapper.AdminImportJobMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminImportJobService {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_STAGING_GENERATED = "STAGING_GENERATED";
    public static final String STATUS_QUALITY_PASSED = "QUALITY_PASSED";
    public static final String STATUS_PACKAGE_GENERATED = "PACKAGE_GENERATED";
    public static final String STATUS_READY_FOR_MANUAL_CONFIRMATION = "READY_FOR_MANUAL_CONFIRMATION";
    public static final String STATUS_FAILED = "FAILED";

    private static final DateTimeFormatter DIR_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminImportJobMapper jobMapper;
    private final AdminImportJobFileMapper fileMapper;
    private final AdminImportJobGateMapper gateMapper;
    private final AdminImportJobArtifactMapper artifactMapper;

    @Value("${gzly.admin-import.source-roots:/opt/gzly/data-sources/guizhou,/opt/gzly/data-output/guizhou}")
    private String sourceRootsText;

    @Value("${gzly.admin-import.output-root:/opt/gzly/data-output/guizhou/import-jobs}")
    private String outputRootText;

    public void setSourceRootsText(String sourceRootsText) {
        this.sourceRootsText = sourceRootsText;
    }

    public void setOutputRootText(String outputRootText) {
        this.outputRootText = outputRootText;
    }

    @Transactional
    public JobDetail createJob(CreateJobRequest request, String createdBy) {
        CreateJobRequest req = request == null ? new CreateJobRequest() : request;
        String provinceCode = normalizeProvince(req.getProvinceCode());
        int year = req.getYear() == null || req.getYear() <= 0 ? 2026 : req.getYear();
        String importType = cleanToken(req.getImportType(), "OFFICIAL_DATA_IMPORT");
        String sourceType = cleanToken(req.getSourceType(), "OFFICIAL_SOURCE");
        String batchCode = cleanText(req.getBatchCode());
        String subjectType = cleanText(req.getSubjectType());
        String sourceDir = normalizeAllowedPath(defaultIfBlank(req.getSourceDir(), defaultSourceDir(provinceCode, year)), sourceRoots(), "source_dir 不在允许目录内").toString();
        String outputDir = normalizeAllowedPath(defaultIfBlank(req.getOutputDir(), defaultOutputDir(provinceCode, year, importType)), List.of(outputRoot()), "output_dir 不在允许目录内").toString();

        AdminImportJob job = new AdminImportJob();
        job.setProvinceCode(provinceCode);
        job.setYear(year);
        job.setBatchCode(batchCode);
        job.setSubjectType(subjectType);
        job.setImportType(importType);
        job.setSourceType(sourceType);
        job.setStatus(STATUS_CREATED);
        job.setSourceDir(sourceDir);
        job.setOutputDir(outputDir);
        job.setCreatedBy(defaultIfBlank(createdBy, "admin"));
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.insert(job);
        return detail(job.getId());
    }

    @Transactional(readOnly = true)
    public JobListResponse listJobs(int page, int size, String provinceCode, Integer year, String status) {
        int resolvedPage = Math.max(1, page);
        int resolvedSize = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<AdminImportJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(provinceCode)) {
            wrapper.eq(AdminImportJob::getProvinceCode, normalizeProvince(provinceCode));
        }
        if (year != null && year > 0) {
            wrapper.eq(AdminImportJob::getYear, year);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AdminImportJob::getStatus, status.trim());
        }
        wrapper.orderByDesc(AdminImportJob::getUpdatedAt).orderByDesc(AdminImportJob::getId);
        Page<AdminImportJob> result = jobMapper.selectPage(new Page<>(resolvedPage, resolvedSize), wrapper);
        JobListResponse response = new JobListResponse();
        response.setItems(result.getRecords().stream().map(this::summary).toList());
        response.setTotal(result.getTotal());
        response.setPage(resolvedPage);
        response.setSize(resolvedSize);
        return response;
    }

    @Transactional(readOnly = true)
    public JobDetail detail(Long jobId) {
        AdminImportJob job = requireJob(jobId);
        JobDetail detail = new JobDetail();
        detail.setJob(summary(job));
        detail.setFiles(fileMapper.selectList(new LambdaQueryWrapper<AdminImportJobFile>()
                .eq(AdminImportJobFile::getJobId, job.getId())
                .orderByAsc(AdminImportJobFile::getId)).stream().map(this::fileView).toList());
        detail.setGates(gateMapper.selectList(new LambdaQueryWrapper<AdminImportJobGate>()
                .eq(AdminImportJobGate::getJobId, job.getId())
                .orderByAsc(AdminImportJobGate::getId)).stream().map(this::gateView).toList());
        detail.setArtifacts(artifactMapper.selectList(new LambdaQueryWrapper<AdminImportJobArtifact>()
                .eq(AdminImportJobArtifact::getJobId, job.getId())
                .orderByAsc(AdminImportJobArtifact::getId)).stream().map(this::artifactView).toList());
        return detail;
    }

    @Transactional
    public StagingResult generateStagingDryRun(Long jobId) {
        AdminImportJob job = requireJob(jobId);
        Path sourceDir = normalizeAllowedPath(job.getSourceDir(), sourceRoots(), "source_dir 不在允许目录内");
        Path outputDir = normalizeAllowedPath(job.getOutputDir(), List.of(outputRoot()), "output_dir 不在允许目录内");
        if (!Files.isDirectory(sourceDir)) {
            throw new BizException("source_dir 不存在或不是目录");
        }
        try {
            Files.createDirectories(outputDir);
            List<Path> files;
            try (Stream<Path> stream = Files.list(sourceDir)) {
                files = stream.filter(Files::isRegularFile)
                        .sorted()
                        .toList();
            }
            List<FileView> fileViews = new ArrayList<>();
            for (Path file : files) {
                AdminImportJobFile jobFile = upsertFile(job.getId(), file);
                fileViews.add(fileView(jobFile));
            }
            Path manifest = outputDir.resolve("staging_manifest.json");
            Files.writeString(manifest, stagingManifest(job, fileViews));
            AdminImportJobArtifact artifact = insertArtifact(job.getId(), "STAGING_MANIFEST", manifest);
            updateStatus(job, STATUS_STAGING_GENERATED);
            StagingResult result = new StagingResult();
            result.setJobId(job.getId());
            result.setStatus(STATUS_STAGING_GENERATED);
            result.setDryRun(true);
            result.setStagingPath(manifest.toString());
            result.setFileCount(fileViews.size());
            result.setFiles(fileViews);
            result.setArtifact(artifactView(artifact));
            return result;
        } catch (IOException e) {
            updateStatus(job, STATUS_FAILED);
            throw new BizException("生成 staging dry-run 失败: " + e.getMessage());
        }
    }

    @Transactional
    public QualityCheckResult runQualityCheck(Long jobId) {
        AdminImportJob job = requireJob(jobId);
        Path outputDir = normalizeAllowedPath(job.getOutputDir(), List.of(outputRoot()), "output_dir 不在允许目录内");
        List<GateView> gates = new ArrayList<>();
        long fileCount = fileMapper.selectCount(new LambdaQueryWrapper<AdminImportJobFile>().eq(AdminImportJobFile::getJobId, job.getId()));
        gates.add(insertGate(job.getId(), "source_dir_allowed", "PASS", "under allowed source roots", job.getSourceDir(), ""));
        gates.add(insertGate(job.getId(), "output_dir_allowed", "PASS", "under output root", job.getOutputDir(), ""));
        gates.add(insertGate(job.getId(), "files_registered", fileCount > 0 ? "PASS" : "FAIL", ">0", String.valueOf(fileCount), ""));
        gates.add(insertGate(job.getId(), "formal_promote_disabled", "PASS", "no promote endpoint", "disabled", ""));
        gates.add(insertGate(job.getId(), "formal_tables_not_written", "PASS", "no formal table mapper/write", "metadata only", ""));
        boolean passed = gates.stream().allMatch(g -> "PASS".equals(g.getGateStatus()));
        try {
            Files.createDirectories(outputDir);
            Path gatePath = outputDir.resolve("quality_gate.tsv");
            Files.writeString(gatePath, qualityGateTsv(gates));
            AdminImportJobArtifact artifact = insertArtifact(job.getId(), "QUALITY_GATE", gatePath);
            updateStatus(job, passed ? STATUS_QUALITY_PASSED : STATUS_FAILED);
            QualityCheckResult result = new QualityCheckResult();
            result.setJobId(job.getId());
            result.setStatus(passed ? STATUS_QUALITY_PASSED : STATUS_FAILED);
            result.setPassed(passed);
            result.setGates(gates);
            result.setArtifact(artifactView(artifact));
            return result;
        } catch (IOException e) {
            updateStatus(job, STATUS_FAILED);
            throw new BizException("生成 quality gate 失败: " + e.getMessage());
        }
    }

    @Transactional
    public SqlPackageResult generateFormalSql(Long jobId) {
        AdminImportJob job = requireJob(jobId);
        if (!STATUS_QUALITY_PASSED.equals(job.getStatus()) && !STATUS_PACKAGE_GENERATED.equals(job.getStatus()) && !STATUS_READY_FOR_MANUAL_CONFIRMATION.equals(job.getStatus())) {
            throw new BizException("quality-check 未通过，不能生成正式 SQL 包");
        }
        Path outputDir = normalizeAllowedPath(job.getOutputDir(), List.of(outputRoot()), "output_dir 不在允许目录内");
        try {
            Files.createDirectories(outputDir);
            Path formalSql = outputDir.resolve("formal_insert_body_only.sql");
            Path rollbackSql = outputDir.resolve("rollback.sql");
            if (!Files.exists(formalSql)) {
                Files.writeString(formalSql, "");
            }
            if (!Files.exists(rollbackSql)) {
                Files.writeString(rollbackSql, "");
            }
            AdminImportJobArtifact formalArtifact = insertArtifact(job.getId(), "FORMAL_INSERT_SQL", formalSql);
            AdminImportJobArtifact rollbackArtifact = insertArtifact(job.getId(), "ROLLBACK_SQL", rollbackSql);
            updateStatus(job, STATUS_PACKAGE_GENERATED);
            SqlPackageResult result = new SqlPackageResult();
            result.setJobId(job.getId());
            result.setStatus(STATUS_PACKAGE_GENERATED);
            result.setFormalSqlPath(formalSql.toString());
            result.setFormalSqlSha256(formalArtifact.getSha256());
            result.setRollbackSqlPath(rollbackSql.toString());
            result.setRollbackSqlSha256(rollbackArtifact.getSha256());
            result.setCleanRowCount(0L);
            return result;
        } catch (IOException e) {
            updateStatus(job, STATUS_FAILED);
            throw new BizException("生成正式 SQL 包失败: " + e.getMessage());
        }
    }

    @Transactional
    public RollbackPlanResult generateRollbackPlan(Long jobId) {
        AdminImportJob job = requireJob(jobId);
        if (!STATUS_PACKAGE_GENERATED.equals(job.getStatus()) && !STATUS_READY_FOR_MANUAL_CONFIRMATION.equals(job.getStatus())) {
            throw new BizException("SQL 包未生成，不能生成 rollback 计划");
        }
        Path outputDir = normalizeAllowedPath(job.getOutputDir(), List.of(outputRoot()), "output_dir 不在允许目录内");
        try {
            Files.createDirectories(outputDir);
            Path rollbackSql = outputDir.resolve("rollback.sql");
            if (!Files.exists(rollbackSql)) {
                Files.writeString(rollbackSql, "");
            }
            Path plan = outputDir.resolve("rollback_plan.md");
            Files.writeString(plan, rollbackPlanText(job, rollbackSql));
            AdminImportJobArtifact rollbackArtifact = insertArtifact(job.getId(), "ROLLBACK_SQL", rollbackSql);
            AdminImportJobArtifact planArtifact = insertArtifact(job.getId(), "ROLLBACK_PLAN", plan);
            updateStatus(job, STATUS_READY_FOR_MANUAL_CONFIRMATION);
            RollbackPlanResult result = new RollbackPlanResult();
            result.setJobId(job.getId());
            result.setStatus(STATUS_READY_FOR_MANUAL_CONFIRMATION);
            result.setRollbackSqlPath(rollbackSql.toString());
            result.setRollbackSqlSha256(rollbackArtifact.getSha256());
            result.setRollbackPlanPath(plan.toString());
            result.setRollbackPlanSha256(planArtifact.getSha256());
            result.setExpectedDeleteRows(0L);
            return result;
        } catch (IOException e) {
            updateStatus(job, STATUS_FAILED);
            throw new BizException("生成 rollback 计划失败: " + e.getMessage());
        }
    }

    private AdminImportJob requireJob(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BizException("任务不存在");
        }
        AdminImportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        return job;
    }

    private AdminImportJobFile upsertFile(Long jobId, Path file) throws IOException {
        String path = file.toString();
        AdminImportJobFile existing = fileMapper.selectOne(new LambdaQueryWrapper<AdminImportJobFile>()
                .eq(AdminImportJobFile::getJobId, jobId)
                .eq(AdminImportJobFile::getFilePath, path)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        AdminImportJobFile jobFile = new AdminImportJobFile();
        jobFile.setJobId(jobId);
        jobFile.setFileName(file.getFileName().toString());
        jobFile.setFilePath(path);
        jobFile.setSha256(sha256(file));
        jobFile.setFileSize(Files.size(file));
        jobFile.setFileType(fileType(file));
        jobFile.setSourceUrl("");
        jobFile.setCreatedAt(LocalDateTime.now());
        fileMapper.insert(jobFile);
        return jobFile;
    }

    private GateView insertGate(Long jobId, String name, String status, String expected, String actual, String samplePath) {
        AdminImportJobGate gate = new AdminImportJobGate();
        gate.setJobId(jobId);
        gate.setGateName(name);
        gate.setGateStatus(status);
        gate.setExpectedValue(expected);
        gate.setActualValue(actual);
        gate.setSamplePath(samplePath);
        gate.setCreatedAt(LocalDateTime.now());
        gateMapper.insert(gate);
        return gateView(gate);
    }

    private AdminImportJobArtifact insertArtifact(Long jobId, String artifactType, Path path) throws IOException {
        AdminImportJobArtifact artifact = new AdminImportJobArtifact();
        artifact.setJobId(jobId);
        artifact.setArtifactType(artifactType);
        artifact.setArtifactPath(path.toString());
        artifact.setSha256(sha256(path));
        artifact.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(artifact);
        return artifact;
    }

    private void updateStatus(AdminImportJob job, String status) {
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private String normalizeProvince(String provinceCode) {
        String value = defaultIfBlank(provinceCode, "GZ").trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{2,16}")) {
            throw new BizException("province_code 格式非法");
        }
        return value;
    }

    private String cleanToken(String value, String defaultValue) {
        String resolved = defaultIfBlank(value, defaultValue).trim().toUpperCase(Locale.ROOT);
        if (!resolved.matches("[A-Z0-9_-]{1,80}")) {
            throw new BizException("任务类型字段格式非法");
        }
        return resolved;
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 80) {
            throw new BizException("任务字段过长");
        }
        return trimmed;
    }

    private String defaultSourceDir(String provinceCode, int year) {
        return "/opt/gzly/data-sources/guizhou/" + year;
    }

    private String defaultOutputDir(String provinceCode, int year, String importType) {
        String suffix = provinceCode.toLowerCase(Locale.ROOT) + "_" + year + "_" + importType.toLowerCase(Locale.ROOT) + "_" + LocalDateTime.now().format(DIR_TIME);
        return outputRoot().resolve(suffix).toString();
    }

    private Path outputRoot() {
        return Path.of(outputRootText).toAbsolutePath().normalize();
    }

    private List<Path> sourceRoots() {
        List<Path> roots = new ArrayList<>();
        for (String item : sourceRootsText.split(",")) {
            if (StringUtils.hasText(item)) {
                roots.add(Path.of(item.trim()).toAbsolutePath().normalize());
            }
        }
        if (roots.isEmpty()) {
            roots.add(Path.of("/opt/gzly/data-sources/guizhou").toAbsolutePath().normalize());
        }
        return roots;
    }

    private Path normalizeAllowedPath(String rawPath, List<Path> allowedRoots, String message) {
        if (!StringUtils.hasText(rawPath)) {
            throw new BizException(message);
        }
        Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        boolean allowed = allowedRoots.stream().anyMatch(path::startsWith);
        if (!allowed) {
            throw new BizException(message);
        }
        return path;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String fileType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "UNKNOWN";
        }
        return name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("计算 SHA256 失败", e);
        }
    }

    private String stagingManifest(AdminImportJob job, List<FileView> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"jobId\": ").append(job.getId()).append(",\n");
        builder.append("  \"provinceCode\": \"").append(json(job.getProvinceCode())).append("\",\n");
        builder.append("  \"year\": ").append(job.getYear()).append(",\n");
        builder.append("  \"dryRun\": true,\n");
        builder.append("  \"fileCount\": ").append(files.size()).append("\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String qualityGateTsv(List<GateView> gates) {
        StringBuilder builder = new StringBuilder("gate_name\tgate_status\texpected_value\tactual_value\tsample_path\n");
        for (GateView gate : gates) {
            builder.append(gate.getGateName()).append('\t')
                    .append(gate.getGateStatus()).append('\t')
                    .append(gate.getExpectedValue()).append('\t')
                    .append(gate.getActualValue()).append('\t')
                    .append(gate.getSamplePath()).append('\n');
        }
        return builder.toString();
    }

    private String rollbackPlanText(AdminImportJob job, Path rollbackSql) {
        return "# Rollback plan\n\n"
                + "job_id=" + job.getId() + "\n"
                + "province_code=" + job.getProvinceCode() + "\n"
                + "year=" + job.getYear() + "\n"
                + "rollback_sql=" + rollbackSql + "\n"
                + "expected_delete_rows=0\n"
                + "formal_promote=manual_only\n";
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JobSummary summary(AdminImportJob job) {
        JobSummary summary = new JobSummary();
        summary.setId(job.getId());
        summary.setProvinceCode(job.getProvinceCode());
        summary.setYear(job.getYear());
        summary.setBatchCode(job.getBatchCode());
        summary.setSubjectType(job.getSubjectType());
        summary.setImportType(job.getImportType());
        summary.setSourceType(job.getSourceType());
        summary.setStatus(job.getStatus());
        summary.setSourceDir(job.getSourceDir());
        summary.setOutputDir(job.getOutputDir());
        summary.setCreatedBy(job.getCreatedBy());
        summary.setCreatedAt(job.getCreatedAt());
        summary.setUpdatedAt(job.getUpdatedAt());
        return summary;
    }

    private FileView fileView(AdminImportJobFile file) {
        FileView view = new FileView();
        view.setId(file.getId());
        view.setJobId(file.getJobId());
        view.setFileName(file.getFileName());
        view.setFilePath(file.getFilePath());
        view.setSha256(file.getSha256());
        view.setFileSize(file.getFileSize());
        view.setFileType(file.getFileType());
        view.setSourceUrl(file.getSourceUrl());
        view.setCreatedAt(file.getCreatedAt());
        return view;
    }

    private GateView gateView(AdminImportJobGate gate) {
        GateView view = new GateView();
        view.setId(gate.getId());
        view.setJobId(gate.getJobId());
        view.setGateName(gate.getGateName());
        view.setGateStatus(gate.getGateStatus());
        view.setExpectedValue(gate.getExpectedValue());
        view.setActualValue(gate.getActualValue());
        view.setSamplePath(gate.getSamplePath());
        view.setCreatedAt(gate.getCreatedAt());
        return view;
    }

    private ArtifactView artifactView(AdminImportJobArtifact artifact) {
        ArtifactView view = new ArtifactView();
        view.setId(artifact.getId());
        view.setJobId(artifact.getJobId());
        view.setArtifactType(artifact.getArtifactType());
        view.setArtifactPath(artifact.getArtifactPath());
        view.setSha256(artifact.getSha256());
        view.setCreatedAt(artifact.getCreatedAt());
        return view;
    }

    @Data
    public static class CreateJobRequest {
        private String provinceCode;
        private Integer year;
        private String batchCode;
        private String subjectType;
        private String importType;
        private String sourceType;
        private String sourceDir;
        private String outputDir;
    }

    @Data
    public static class JobListResponse {
        private List<JobSummary> items;
        private Long total;
        private Integer page;
        private Integer size;
    }

    @Data
    public static class JobDetail {
        private JobSummary job;
        private List<FileView> files;
        private List<GateView> gates;
        private List<ArtifactView> artifacts;
    }

    @Data
    public static class JobSummary {
        private Long id;
        private String provinceCode;
        private Integer year;
        private String batchCode;
        private String subjectType;
        private String importType;
        private String sourceType;
        private String status;
        private String sourceDir;
        private String outputDir;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class FileView {
        private Long id;
        private Long jobId;
        private String fileName;
        private String filePath;
        private String sha256;
        private Long fileSize;
        private String fileType;
        private String sourceUrl;
        private LocalDateTime createdAt;
    }

    @Data
    public static class GateView {
        private Long id;
        private Long jobId;
        private String gateName;
        private String gateStatus;
        private String expectedValue;
        private String actualValue;
        private String samplePath;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ArtifactView {
        private Long id;
        private Long jobId;
        private String artifactType;
        private String artifactPath;
        private String sha256;
        private LocalDateTime createdAt;
    }

    @Data
    public static class StagingResult {
        private Long jobId;
        private String status;
        private Boolean dryRun;
        private String stagingPath;
        private Integer fileCount;
        private List<FileView> files;
        private ArtifactView artifact;
    }

    @Data
    public static class QualityCheckResult {
        private Long jobId;
        private String status;
        private Boolean passed;
        private List<GateView> gates;
        private ArtifactView artifact;
    }

    @Data
    public static class SqlPackageResult {
        private Long jobId;
        private String status;
        private String formalSqlPath;
        private String formalSqlSha256;
        private String rollbackSqlPath;
        private String rollbackSqlSha256;
        private Long cleanRowCount;
    }

    @Data
    public static class RollbackPlanResult {
        private Long jobId;
        private String status;
        private String rollbackSqlPath;
        private String rollbackSqlSha256;
        private String rollbackPlanPath;
        private String rollbackPlanSha256;
        private Long expectedDeleteRows;
    }
}
