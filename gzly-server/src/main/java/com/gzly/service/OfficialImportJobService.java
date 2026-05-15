package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.PageResult;
import com.gzly.common.exception.BizException;
import com.gzly.entity.DataYearReadiness;
import com.gzly.entity.DataYearReadinessBatch;
import com.gzly.entity.ImportJob;
import com.gzly.entity.ImportJobFile;
import com.gzly.entity.ImportQualityReport;
import com.gzly.entity.ImportRollbackPlan;
import com.gzly.mapper.DataYearReadinessBatchMapper;
import com.gzly.mapper.DataYearReadinessMapper;
import com.gzly.mapper.ImportJobFileMapper;
import com.gzly.mapper.ImportJobMapper;
import com.gzly.mapper.ImportQualityReportMapper;
import com.gzly.mapper.ImportRollbackPlanMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialImportJobService {

    private static final int DEFAULT_YEAR = 2026;
    private static final String DEFAULT_PROVINCE = "GZ";
    private static final String PACKAGE_ROOT = "/var/lib/gzly/2026_official_import";

    private final ImportJobMapper importJobMapper;
    private final ImportJobFileMapper importJobFileMapper;
    private final ImportQualityReportMapper qualityReportMapper;
    private final ImportRollbackPlanMapper rollbackPlanMapper;
    private final DataYearReadinessMapper dataYearReadinessMapper;
    private final DataYearReadinessBatchMapper dataYearReadinessBatchMapper;

    public enum ImportJobStatus {
        CREATED,
        FILE_REGISTERED,
        STAGING_READY,
        QUALITY_CHECKED,
        FORMAL_SQL_GENERATED,
        WAITING_CONFIRMATION,
        PROMOTED,
        ROLLBACK_READY,
        FAILED
    }

    public enum OfficialDataType {
        SCORE_SEGMENT("score_segment"),
        ADMISSION_PLAN("admission_plan"),
        POLICY_RULE("policy_rule"),
        MAJOR_REQUIREMENT("major_requirement"),
        MAJOR_META("major_meta"),
        SCORE_LINE("score_line"),
        ART_SPORTS_RULE("art_sports_rule"),
        SPECIAL_ELIGIBILITY("special_eligibility");

        private final String batchSlug;

        OfficialDataType(String batchSlug) {
            this.batchSlug = batchSlug;
        }

        public String batchSlug() {
            return batchSlug;
        }

        public static OfficialDataType parse(String value) {
            if (!StringUtils.hasText(value)) {
                throw new BizException(400, "data_type 不能为空");
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            try {
                return OfficialDataType.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new BizException(400, "不支持的数据类型：" + value);
            }
        }
    }

    @Transactional
    public ImportJobView createJob(CreateImportJobRequest request, String actor) {
        if (request == null) {
            throw new BizException(400, "参数不能为空");
        }
        OfficialDataType dataType = OfficialDataType.parse(request.getDataType());
        String provinceCode = normalizeProvince(request.getProvinceCode());
        int year = normalizeYear(request.getYear());
        if (!StringUtils.hasText(request.getSourceFile()) && !StringUtils.hasText(request.getSourceUrl())) {
            throw new BizException(400, "source_file 和 source_url 至少填写一个");
        }

        LocalDateTime now = LocalDateTime.now();
        String importBatchId = nextImportBatchId(provinceCode, year, dataType);
        ImportJob job = new ImportJob();
        job.setJobId("imp_" + year + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        job.setProvinceCode(provinceCode);
        job.setYear(year);
        job.setDataType(dataType.name());
        job.setImportBatchId(importBatchId);
        job.setSourceFile(trim(request.getSourceFile()));
        job.setSourceUrl(trim(request.getSourceUrl()));
        job.setSourceManifest(trim(request.getSourceManifest()));
        job.setRawText(trim(request.getRawText()));
        job.setStatus(ImportJobStatus.FILE_REGISTERED.name());
        job.setCurrentStep("FILE_REGISTERED");
        job.setTotalRows(0);
        job.setCleanRows(0);
        job.setReviewRows(0);
        job.setErrorRows(0);
        job.setQualityReportPath("");
        job.setFormalSqlPath("");
        job.setRollbackSqlPath("");
        job.setMessage("官方来源已登记；尚未执行 staging、质检或正式导入。");
        job.setCreatedBy(trim(actor));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        importJobMapper.insert(job);

        ImportJobFile file = new ImportJobFile();
        file.setJobId(job.getJobId());
        file.setImportBatchId(importBatchId);
        file.setSourceFile(job.getSourceFile());
        file.setSourceUrl(job.getSourceUrl());
        file.setSourceManifest(job.getSourceManifest());
        file.setFileHash(trim(request.getFileHash()));
        file.setRawText(job.getRawText());
        file.setStatus("REGISTERED");
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        importJobFileMapper.insert(file);
        return toView(job);
    }

    @Transactional(readOnly = true)
    public PageResult<ImportJobView> listJobs(String provinceCode, Integer year, String dataType, String status,
                                              int page, int pageSize) {
        QueryWrapper<ImportJob> query = new QueryWrapper<>();
        if (StringUtils.hasText(provinceCode)) {
            query.eq("province_code", provinceCode.trim().toUpperCase(Locale.ROOT));
        }
        if (year != null && year > 0) {
            query.eq("year", year);
        }
        if (StringUtils.hasText(dataType)) {
            query.eq("data_type", OfficialDataType.parse(dataType).name());
        }
        if (StringUtils.hasText(status)) {
            query.eq("status", normalizeStatus(status).name());
        }
        query.orderByDesc("updated_at").orderByDesc("id");
        int resolvedPage = Math.max(1, page);
        int resolvedSize = Math.min(Math.max(1, pageSize), 100);
        Page<ImportJob> result = importJobMapper.selectPage(new Page<>(resolvedPage, resolvedSize), query);
        return PageResult.of(result.getRecords().stream().map(this::toView).toList(),
                result.getTotal(), resolvedPage, resolvedSize);
    }

    @Transactional(readOnly = true)
    public ImportJobDetail detail(String jobId) {
        ImportJob job = requireJob(jobId);
        ImportJobDetail detail = new ImportJobDetail();
        detail.setJob(toView(job));
        detail.setFiles(importJobFileMapper.selectList(new QueryWrapper<ImportJobFile>()
                .eq("job_id", job.getJobId())
                .orderByDesc("id")));
        detail.setQualityReports(qualityReportMapper.selectList(new QueryWrapper<ImportQualityReport>()
                .eq("job_id", job.getJobId())
                .orderByDesc("id")));
        detail.setRollbackPlans(rollbackPlanMapper.selectList(new QueryWrapper<ImportRollbackPlan>()
                .eq("job_id", job.getJobId())
                .orderByDesc("id")));
        return detail;
    }

    @Transactional
    public ImportJobView runStaging(String jobId) {
        ImportJob job = requireJob(jobId);
        requireNotPromoted(job);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(ImportJobStatus.STAGING_READY.name());
        job.setCurrentStep("STAGING_READY");
        job.setMessage("staging dry-run 已生成；未写正式表，未切换 FULL_RECOMMEND。");
        job.setUpdatedAt(now);
        importJobMapper.updateById(job);
        upsertReadinessBatch(job, "staged", "", "", 0, 0);
        return toView(job);
    }

    @Transactional
    public ImportJobView runQualityCheck(String jobId) {
        ImportJob job = requireJob(jobId);
        requireNotPromoted(job);
        String reportPath = packageDir(job) + "/quality_report.md";
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(ImportJobStatus.QUALITY_CHECKED.name());
        job.setCurrentStep("QUALITY_CHECKED");
        job.setQualityReportPath(reportPath);
        job.setTotalRows(value(job.getTotalRows()));
        job.setCleanRows(value(job.getCleanRows()));
        job.setReviewRows(value(job.getReviewRows()));
        job.setErrorRows(value(job.getErrorRows()));
        job.setMessage("质量门禁 dry-run 已完成；当前报告为确认包骨架，不代表正式数据已导入。");
        job.setUpdatedAt(now);
        importJobMapper.updateById(job);

        ImportQualityReport report = new ImportQualityReport();
        report.setJobId(job.getJobId());
        report.setImportBatchId(job.getImportBatchId());
        report.setDataType(job.getDataType());
        report.setReportPath(reportPath);
        report.setGateStatus("DRY_RUN");
        report.setSummary("dry-run quality report package generated; no formal table write");
        report.setTotalRows(job.getTotalRows());
        report.setCleanRows(job.getCleanRows());
        report.setReviewRows(job.getReviewRows());
        report.setErrorRows(job.getErrorRows());
        report.setCreatedAt(now);
        qualityReportMapper.insert(report);
        upsertReadinessBatch(job, "quality_passed", reportPath, trim(job.getRollbackSqlPath()), job.getTotalRows(), job.getErrorRows());
        return toView(job);
    }

    @Transactional
    public ImportJobView generateFormalSql(String jobId) {
        ImportJob job = requireJob(jobId);
        requireNotPromoted(job);
        String formalSqlPath = packageDir(job) + "/formal_promote.sql";
        job.setStatus(ImportJobStatus.FORMAL_SQL_GENERATED.name());
        job.setCurrentStep("FORMAL_SQL_GENERATED");
        job.setFormalSqlPath(formalSqlPath);
        job.setMessage("已生成正式导入确认 SQL 路径；本接口不执行 SQL、不写正式表。");
        job.setUpdatedAt(LocalDateTime.now());
        importJobMapper.updateById(job);
        return toView(job);
    }

    @Transactional
    public ImportJobView generateRollbackPlan(String jobId) {
        ImportJob job = requireJob(jobId);
        requireNotPromoted(job);
        String rollbackPath = packageDir(job) + "/rollback.sql";
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(ImportJobStatus.ROLLBACK_READY.name());
        job.setCurrentStep("ROLLBACK_READY");
        job.setRollbackSqlPath(rollbackPath);
        job.setMessage("已生成回滚计划路径；本轮仅记录回滚说明，不执行 rollback。");
        job.setUpdatedAt(now);
        importJobMapper.updateById(job);

        ImportRollbackPlan plan = new ImportRollbackPlan();
        plan.setJobId(job.getJobId());
        plan.setImportBatchId(job.getImportBatchId());
        plan.setRollbackSqlPath(rollbackPath);
        plan.setSummary("rollback plan generated for confirmation package only; executable=false");
        plan.setExecutable(0);
        plan.setCreatedAt(now);
        rollbackPlanMapper.insert(plan);
        upsertReadinessBatch(job, trim(readinessBatchStatus(job)), trim(job.getQualityReportPath()), rollbackPath,
                value(job.getTotalRows()), value(job.getErrorRows()));
        return toView(job);
    }

    @Transactional(readOnly = true)
    public DataYearReadinessView readiness(String provinceCode, Integer year) {
        String province = normalizeProvince(provinceCode);
        int resolvedYear = normalizeYear(year);
        DataYearReadiness row = null;
        try {
            row = dataYearReadinessMapper.selectOne(new QueryWrapper<DataYearReadiness>()
                    .eq("province_code", province)
                    .eq("year", resolvedYear)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("data_year_readiness 查询失败，返回安全默认状态: province={}, year={}", province, resolvedYear, e);
        }
        DataYearReadinessView view = row == null ? defaultReadiness(province, resolvedYear) : toReadinessView(row);
        view.setImportProgress(progressByDataType(province, resolvedYear));
        return view;
    }

    private Map<String, ImportProgressView> progressByDataType(String provinceCode, int year) {
        Map<String, ImportProgressView> progress = new LinkedHashMap<>();
        for (OfficialDataType type : OfficialDataType.values()) {
            ImportProgressView item = new ImportProgressView();
            item.setDataType(type.name());
            item.setRequired(true);
            item.setLatestStatus("NOT_STARTED");
            progress.put(type.name(), item);
        }
        try {
            List<ImportJob> jobs = importJobMapper.selectList(new QueryWrapper<ImportJob>()
                    .eq("province_code", provinceCode)
                    .eq("year", year)
                    .orderByDesc("updated_at")
                    .orderByDesc("id"));
            for (ImportJob job : jobs) {
                ImportProgressView item = progress.get(job.getDataType());
                if (item == null || StringUtils.hasText(item.getImportBatchId())) {
                    continue;
                }
                item.setImportBatchId(job.getImportBatchId());
                item.setLatestStatus(job.getStatus());
                item.setQualityReportPath(trim(job.getQualityReportPath()));
                item.setRollbackSqlPath(trim(job.getRollbackSqlPath()));
                item.setUpdatedAt(job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());
            }
        } catch (Exception e) {
            log.warn("导入任务进度查询失败: province={}, year={}", provinceCode, year, e);
        }
        return progress;
    }

    private ImportJob requireJob(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            throw new BizException(400, "jobId 不能为空");
        }
        ImportJob job = importJobMapper.selectOne(new QueryWrapper<ImportJob>()
                .eq("job_id", jobId.trim())
                .last("LIMIT 1"));
        if (job == null) {
            throw new BizException(404, "导入任务不存在");
        }
        return job;
    }

    private void requireNotPromoted(ImportJob job) {
        if (ImportJobStatus.PROMOTED.name().equals(job.getStatus())) {
            throw new BizException(400, "已晋级任务不能在本轮接口中继续修改");
        }
    }

    private String nextImportBatchId(String provinceCode, int year, OfficialDataType dataType) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = provinceCode.toLowerCase(Locale.ROOT) + "_" + year + "_" + dataType.batchSlug() + "_v1_" + date;
        for (int i = 0; i < 100; i++) {
            String candidate = i == 0 ? prefix : prefix + "_" + String.format("%02d", i + 1);
            Long count = importJobMapper.selectCount(new QueryWrapper<ImportJob>().eq("import_batch_id", candidate));
            if (count == null || count == 0L) {
                return candidate;
            }
        }
        throw new BizException(500, "无法生成唯一 import_batch_id");
    }

    private void upsertReadinessBatch(ImportJob job, String status, String reportPath, String rollbackPath,
                                      int rowCount, int failedGateCount) {
        try {
            DataYearReadinessBatch existing = dataYearReadinessBatchMapper.selectOne(new QueryWrapper<DataYearReadinessBatch>()
                    .eq("province_code", job.getProvinceCode())
                    .eq("year", job.getYear())
                    .eq("data_type", job.getDataType())
                    .eq("import_batch_id", job.getImportBatchId())
                    .last("LIMIT 1"));
            DataYearReadinessBatch batch = existing == null ? new DataYearReadinessBatch() : existing;
            batch.setProvinceCode(job.getProvinceCode());
            batch.setYear(job.getYear());
            batch.setDataType(job.getDataType());
            batch.setImportBatchId(job.getImportBatchId());
            batch.setStatus(status);
            batch.setSourceManifest(trim(job.getSourceManifest()));
            batch.setSourceFile(trim(job.getSourceFile()));
            batch.setSourceUrl(trim(job.getSourceUrl()));
            batch.setQualityReportPath(reportPath);
            batch.setRollbackSqlPath(rollbackPath);
            batch.setRowCount(rowCount);
            batch.setFailedGateCount(failedGateCount);
            batch.setRemarks("admin import job generated confirmation package only; no formal promote executed");
            if (existing == null) {
                batch.setCreatedAt(LocalDateTime.now());
                dataYearReadinessBatchMapper.insert(batch);
            } else {
                batch.setUpdatedAt(LocalDateTime.now());
                dataYearReadinessBatchMapper.updateById(batch);
            }
        } catch (Exception e) {
            log.warn("readiness batch 审计写入失败，不影响导入任务确认包生成: batch={}", job.getImportBatchId(), e);
        }
    }

    private ImportJobStatus normalizeStatus(String value) {
        try {
            return ImportJobStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BizException(400, "不支持的任务状态：" + value);
        }
    }

    private int normalizeYear(Integer year) {
        return year == null || year <= 0 ? DEFAULT_YEAR : year;
    }

    private String normalizeProvince(String provinceCode) {
        return StringUtils.hasText(provinceCode) ? provinceCode.trim().toUpperCase(Locale.ROOT) : DEFAULT_PROVINCE;
    }

    private String packageDir(ImportJob job) {
        return PACKAGE_ROOT + "/" + job.getImportBatchId();
    }

    private String readinessBatchStatus(ImportJob job) {
        if (StringUtils.hasText(job.getQualityReportPath())) {
            return "quality_passed";
        }
        return "staged";
    }

    private ImportJobView toView(ImportJob job) {
        ImportJobView view = new ImportJobView();
        view.setJobId(job.getJobId());
        view.setProvinceCode(job.getProvinceCode());
        view.setYear(job.getYear());
        view.setDataType(job.getDataType());
        view.setImportBatchId(job.getImportBatchId());
        view.setSourceFile(trim(job.getSourceFile()));
        view.setSourceUrl(trim(job.getSourceUrl()));
        view.setSourceManifest(trim(job.getSourceManifest()));
        view.setStatus(job.getStatus());
        view.setCurrentStep(job.getCurrentStep());
        view.setTotalRows(value(job.getTotalRows()));
        view.setCleanRows(value(job.getCleanRows()));
        view.setReviewRows(value(job.getReviewRows()));
        view.setErrorRows(value(job.getErrorRows()));
        view.setQualityReportPath(trim(job.getQualityReportPath()));
        view.setFormalSqlPath(trim(job.getFormalSqlPath()));
        view.setRollbackSqlPath(trim(job.getRollbackSqlPath()));
        view.setMessage(trim(job.getMessage()));
        view.setCreatedAt(job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        view.setUpdatedAt(job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());
        view.setDryRunOnly(true);
        view.setFormalPromoteExecuted(false);
        return view;
    }

    private DataYearReadinessView toReadinessView(DataYearReadiness row) {
        DataYearReadinessView view = new DataYearReadinessView();
        view.setProvinceCode(row.getProvinceCode());
        view.setYear(row.getYear());
        view.setPolicyReady(isReady(row.getPolicyReady()));
        view.setScoreSegmentReady(isReady(row.getScoreSegmentReady()));
        view.setAdmissionPlanReady(isReady(row.getAdmissionPlanReady()));
        view.setMajorRequirementReady(isReady(row.getMajorRequirementReady()));
        view.setMajorMetaReady(isReady(row.getMajorMetaReady()));
        view.setMlTrainingReady(isReady(row.getMlTrainingReady()));
        view.setHistoricalTrainingReady(row.getHistoricalTrainingReady() == null || row.getHistoricalTrainingReady() == 1);
        view.setRecommendationPhase(StringUtils.hasText(row.getRecommendationPhase())
                ? row.getRecommendationPhase()
                : "PRE_OFFICIAL_DATA");
        view.setLatestImportBatchId(trim(row.getLatestImportBatchId()));
        view.setRemarks(trim(row.getRemarks()));
        view.setLastCheckedAt(row.getLastCheckedAt() == null ? "" : row.getLastCheckedAt().toString());
        return view;
    }

    private DataYearReadinessView defaultReadiness(String provinceCode, int year) {
        DataYearReadinessView view = new DataYearReadinessView();
        view.setProvinceCode(provinceCode);
        view.setYear(year);
        view.setPolicyReady(false);
        view.setScoreSegmentReady(false);
        view.setAdmissionPlanReady(false);
        view.setMajorRequirementReady(false);
        view.setMajorMetaReady(false);
        view.setMlTrainingReady(false);
        view.setHistoricalTrainingReady(true);
        view.setRecommendationPhase("PRE_OFFICIAL_DATA");
        view.setLatestImportBatchId("");
        view.setRemarks("2026 官方关键数据未确认导入；仅允许历史训练和准备进度展示。");
        view.setLastCheckedAt("");
        return view;
    }

    private boolean isReady(Integer value) {
        return value != null && value == 1;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    public static class CreateImportJobRequest {
        private String provinceCode;
        private Integer year;
        private String dataType;
        private String sourceFile;
        private String sourceUrl;
        private String sourceManifest;
        private String fileHash;
        private String rawText;
    }

    @Data
    public static class ImportJobView {
        private String jobId;
        private String provinceCode;
        private Integer year;
        private String dataType;
        private String importBatchId;
        private String sourceFile;
        private String sourceUrl;
        private String sourceManifest;
        private String status;
        private String currentStep;
        private Integer totalRows;
        private Integer cleanRows;
        private Integer reviewRows;
        private Integer errorRows;
        private String qualityReportPath;
        private String formalSqlPath;
        private String rollbackSqlPath;
        private String message;
        private String createdAt;
        private String updatedAt;
        private boolean dryRunOnly;
        private boolean formalPromoteExecuted;
    }

    @Data
    public static class ImportJobDetail {
        private ImportJobView job;
        private List<ImportJobFile> files = new ArrayList<>();
        private List<ImportQualityReport> qualityReports = new ArrayList<>();
        private List<ImportRollbackPlan> rollbackPlans = new ArrayList<>();
    }

    @Data
    public static class DataYearReadinessView {
        private String provinceCode;
        private Integer year;
        private boolean policyReady;
        private boolean scoreSegmentReady;
        private boolean admissionPlanReady;
        private boolean majorRequirementReady;
        private boolean majorMetaReady;
        private boolean mlTrainingReady;
        private boolean historicalTrainingReady;
        private String recommendationPhase;
        private String latestImportBatchId;
        private String remarks;
        private String lastCheckedAt;
        private Map<String, ImportProgressView> importProgress = new LinkedHashMap<>();
    }

    @Data
    public static class ImportProgressView {
        private String dataType;
        private boolean required;
        private String latestStatus;
        private String importBatchId;
        private String qualityReportPath;
        private String rollbackSqlPath;
        private String updatedAt;
    }
}
