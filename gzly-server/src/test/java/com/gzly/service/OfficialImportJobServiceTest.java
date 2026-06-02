package com.gzly.service;

import com.gzly.entity.DataYearReadiness;
import com.gzly.entity.ImportJob;
import com.gzly.entity.ImportJobFile;
import com.gzly.entity.ImportRollbackPlan;
import com.gzly.mapper.DataYearReadinessBatchMapper;
import com.gzly.mapper.DataYearReadinessMapper;
import com.gzly.mapper.ImportJobFileMapper;
import com.gzly.mapper.ImportJobMapper;
import com.gzly.mapper.ImportQualityReportMapper;
import com.gzly.mapper.ImportRollbackPlanMapper;
import com.gzly.service.OfficialImportJobService.CreateImportJobRequest;
import com.gzly.service.OfficialImportJobService.DataYearReadinessView;
import com.gzly.service.OfficialImportJobService.ImportJobStatus;
import com.gzly.service.OfficialImportJobService.ImportJobView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficialImportJobServiceTest {

    @Mock private ImportJobMapper importJobMapper;
    @Mock private ImportJobFileMapper importJobFileMapper;
    @Mock private ImportQualityReportMapper qualityReportMapper;
    @Mock private ImportRollbackPlanMapper rollbackPlanMapper;
    @Mock private DataYearReadinessMapper dataYearReadinessMapper;
    @Mock private DataYearReadinessBatchMapper dataYearReadinessBatchMapper;

    private OfficialImportJobService service;

    @BeforeEach
    void setUp() {
        service = new OfficialImportJobService(
                importJobMapper,
                importJobFileMapper,
                qualityReportMapper,
                rollbackPlanMapper,
                dataYearReadinessMapper,
                dataYearReadinessBatchMapper
        );
    }

    @Test
    void createJobGeneratesUniqueImportBatchIdAndRegistersSourceFile() {
        when(importJobMapper.selectCount(any())).thenReturn(1L, 0L);
        CreateImportJobRequest request = new CreateImportJobRequest();
        request.setProvinceCode("GZ");
        request.setYear(2026);
        request.setDataType("ADMISSION_PLAN");
        request.setSourceFile("2026-plan.xlsx");
        request.setSourceUrl("https://zsksy.guizhou.gov.cn/plan.xlsx");
        request.setSourceManifest("manifest/2026-plan.json");

        ImportJobView view = service.createJob(request, "admin");

        assertThat(view.getStatus()).isEqualTo(ImportJobStatus.FILE_REGISTERED.name());
        assertThat(view.getImportBatchId()).startsWith("gz_2026_admission_plan_v1_");
        assertThat(view.getImportBatchId()).endsWith("_02");
        assertThat(view.isDryRunOnly()).isTrue();
        assertThat(view.isFormalPromoteExecuted()).isFalse();

        ArgumentCaptor<ImportJob> jobCaptor = ArgumentCaptor.forClass(ImportJob.class);
        verify(importJobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getDataType()).isEqualTo("ADMISSION_PLAN");
        assertThat(jobCaptor.getValue().getFormalSqlPath()).isEmpty();

        ArgumentCaptor<ImportJobFile> fileCaptor = ArgumentCaptor.forClass(ImportJobFile.class);
        verify(importJobFileMapper).insert(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getSourceFile()).isEqualTo("2026-plan.xlsx");
        assertThat(fileCaptor.getValue().getImportBatchId()).isEqualTo(view.getImportBatchId());
    }

    @Test
    void jobStateFlowGeneratesConfirmationPackageWithoutFormalPromote() {
        ImportJob job = baseJob();
        when(importJobMapper.selectOne(any())).thenReturn(job);
        when(dataYearReadinessBatchMapper.selectOne(any())).thenReturn(null);

        ImportJobView staging = service.runStaging(job.getJobId());
        assertThat(staging.getStatus()).isEqualTo(ImportJobStatus.STAGING_READY.name());
        assertThat(staging.isFormalPromoteExecuted()).isFalse();

        ImportJobView quality = service.runQualityCheck(job.getJobId());
        assertThat(quality.getStatus()).isEqualTo(ImportJobStatus.QUALITY_CHECKED.name());
        assertThat(quality.getQualityReportPath()).endsWith("/quality_report.md");

        ImportJobView formalSql = service.generateFormalSql(job.getJobId());
        assertThat(formalSql.getStatus()).isEqualTo(ImportJobStatus.FORMAL_SQL_GENERATED.name());
        assertThat(formalSql.getFormalSqlPath()).endsWith("/formal_promote.sql");
        assertThat(formalSql.isFormalPromoteExecuted()).isFalse();
    }

    @Test
    void rollbackPlanOnlyRecordsPathAndIsNotExecutable() {
        ImportJob job = baseJob();
        when(importJobMapper.selectOne(any())).thenReturn(job);
        when(dataYearReadinessBatchMapper.selectOne(any())).thenReturn(null);

        ImportJobView view = service.generateRollbackPlan(job.getJobId());

        assertThat(view.getStatus()).isEqualTo(ImportJobStatus.ROLLBACK_READY.name());
        assertThat(view.getRollbackSqlPath()).endsWith("/rollback.sql");
        ArgumentCaptor<ImportRollbackPlan> planCaptor = ArgumentCaptor.forClass(ImportRollbackPlan.class);
        verify(rollbackPlanMapper).insert(planCaptor.capture());
        assertThat(planCaptor.getValue().getExecutable()).isZero();
    }

    @Test
    void readinessFallsBackToSafePreOfficialDataWhenRowMissing() {
        when(dataYearReadinessMapper.selectOne(any())).thenReturn(null);
        when(importJobMapper.selectList(any())).thenReturn(List.of());

        DataYearReadinessView view = service.readiness("GZ", 2026);

        assertThat(view.getRecommendationPhase()).isEqualTo("PRE_OFFICIAL_DATA");
        assertThat(view.isHistoricalTrainingReady()).isTrue();
        assertThat(view.isAdmissionPlanReady()).isFalse();
        assertThat(view.getImportProgress()).containsKey("ADMISSION_PLAN");
    }

    @Test
    void readinessMapsDatabaseFlags() {
        DataYearReadiness row = new DataYearReadiness();
        row.setProvinceCode("GZ");
        row.setYear(2026);
        row.setPolicyReady(1);
        row.setScoreSegmentReady(1);
        row.setAdmissionPlanReady(0);
        row.setMajorRequirementReady(1);
        row.setMajorMetaReady(0);
        row.setMlTrainingReady(0);
        row.setHistoricalTrainingReady(1);
        row.setRecommendationPhase("OFFICIAL_DATA_PARTIAL");
        row.setLatestImportBatchId("gz_2026_score_segment_v1_20260513");
        row.setLastCheckedAt(LocalDateTime.of(2026, 5, 13, 12, 0));
        when(dataYearReadinessMapper.selectOne(any())).thenReturn(row);
        when(importJobMapper.selectList(any())).thenReturn(List.of());

        DataYearReadinessView view = service.readiness("gz", 2026);

        assertThat(view.getRecommendationPhase()).isEqualTo("OFFICIAL_DATA_PARTIAL");
        assertThat(view.isPolicyReady()).isTrue();
        assertThat(view.isAdmissionPlanReady()).isFalse();
        assertThat(view.getLatestImportBatchId()).isEqualTo("gz_2026_score_segment_v1_20260513");
    }

    private ImportJob baseJob() {
        ImportJob job = new ImportJob();
        job.setJobId("imp_2026_test");
        job.setProvinceCode("GZ");
        job.setYear(2026);
        job.setDataType("ADMISSION_PLAN");
        job.setImportBatchId("gz_2026_admission_plan_v1_20260513");
        job.setSourceFile("plan.xlsx");
        job.setSourceUrl("https://zsksy.guizhou.gov.cn/plan.xlsx");
        job.setSourceManifest("manifest.json");
        job.setStatus(ImportJobStatus.FILE_REGISTERED.name());
        job.setCurrentStep("FILE_REGISTERED");
        job.setTotalRows(0);
        job.setCleanRows(0);
        job.setReviewRows(0);
        job.setErrorRows(0);
        job.setQualityReportPath("");
        job.setFormalSqlPath("");
        job.setRollbackSqlPath("");
        job.setMessage("");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }
}
