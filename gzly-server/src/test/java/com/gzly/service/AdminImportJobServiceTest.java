package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminImportJobServiceTest {

    @TempDir
    Path tempDir;

    @Mock private AdminImportJobMapper jobMapper;
    @Mock private AdminImportJobFileMapper fileMapper;
    @Mock private AdminImportJobGateMapper gateMapper;
    @Mock private AdminImportJobArtifactMapper artifactMapper;

    private AdminImportJobService service;
    private Path sourceRoot;
    private Path outputRoot;
    private AdminImportJob storedJob;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() throws Exception {
        sourceRoot = tempDir.resolve("data-sources");
        outputRoot = tempDir.resolve("data-output");
        Files.createDirectories(sourceRoot.resolve("2026"));
        Files.createDirectories(outputRoot);
        service = new AdminImportJobService(jobMapper, fileMapper, gateMapper, artifactMapper);
        service.setSourceRootsText(sourceRoot.toString());
        service.setOutputRootText(outputRoot.toString());
        lenient().when(jobMapper.insert(any(AdminImportJob.class))).thenAnswer(invocation -> {
            storedJob = invocation.getArgument(0);
            storedJob.setId(1L);
            return 1;
        });
        lenient().when(jobMapper.selectById(1L)).thenAnswer(invocation -> storedJob);
        lenient().when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(gateMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(artifactMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void adminImportJob_shouldCreateJob() {
        AdminImportJobService.CreateJobRequest request = new AdminImportJobService.CreateJobRequest();
        request.setProvinceCode("GZ");
        request.setYear(2026);
        request.setBatchCode("NORMAL_UNDERGRADUATE");
        request.setSubjectType("物理类");
        request.setImportType("OFFICIAL_PLAN");
        request.setSourceType("OFFICIAL_PDF");
        request.setSourceDir(sourceRoot.resolve("2026").toString());

        AdminImportJobService.JobDetail detail = service.createJob(request, "admin");

        assertThat(detail.getJob().getId()).isEqualTo(1L);
        assertThat(detail.getJob().getStatus()).isEqualTo(AdminImportJobService.STATUS_CREATED);
        assertThat(detail.getJob().getProvinceCode()).isEqualTo("GZ");
        assertThat(detail.getJob().getOutputDir()).startsWith(outputRoot.toString());
    }

    @Test
    void adminImportJob_shouldListJobs() {
        storedJob = job("CREATED");
        Page<AdminImportJob> page = new Page<>(1, 20);
        page.setRecords(List.of(storedJob));
        page.setTotal(1);
        when(jobMapper.selectPage(any(), any())).thenReturn(page);

        AdminImportJobService.JobListResponse result = service.listJobs(1, 20, "GZ", 2026, "CREATED");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo("CREATED");
    }

    @Test
    void adminImportJob_shouldReadJobDetail() {
        storedJob = job("CREATED");
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(file(11L)));
        when(gateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(gate(12L, "PASS")));
        when(artifactMapper.selectList(any(Wrapper.class))).thenReturn(List.of(artifact(13L, "QUALITY_GATE", outputRoot.resolve("quality_gate.tsv"))));

        AdminImportJobService.JobDetail detail = service.detail(1L);

        assertThat(detail.getJob().getId()).isEqualTo(1L);
        assertThat(detail.getFiles()).hasSize(1);
        assertThat(detail.getGates()).hasSize(1);
        assertThat(detail.getArtifacts()).hasSize(1);
    }

    @Test
    void adminImportJob_shouldGenerateStagingDryRun() throws Exception {
        storedJob = job("CREATED");
        Files.writeString(sourceRoot.resolve("2026").resolve("official.pdf"), "official-source");
        when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(fileMapper.insert(any(AdminImportJobFile.class))).thenAnswer(invocation -> {
            AdminImportJobFile file = invocation.getArgument(0);
            file.setId(ids.incrementAndGet());
            return 1;
        });
        when(artifactMapper.insert(any(AdminImportJobArtifact.class))).thenAnswer(invocation -> {
            AdminImportJobArtifact artifact = invocation.getArgument(0);
            artifact.setId(ids.incrementAndGet());
            return 1;
        });

        AdminImportJobService.StagingResult result = service.generateStagingDryRun(1L);

        assertThat(result.getDryRun()).isTrue();
        assertThat(result.getFileCount()).isEqualTo(1);
        assertThat(result.getStagingPath()).endsWith("staging_manifest.json");
        assertThat(Files.exists(Path.of(result.getStagingPath()))).isTrue();
        assertThat(storedJob.getStatus()).isEqualTo(AdminImportJobService.STATUS_STAGING_GENERATED);
    }

    @Test
    void adminImportJob_shouldRunQualityCheck() throws Exception {
        storedJob = job("STAGING_GENERATED");
        when(fileMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(gateMapper.insert(any(AdminImportJobGate.class))).thenAnswer(invocation -> {
            AdminImportJobGate gate = invocation.getArgument(0);
            gate.setId(ids.incrementAndGet());
            return 1;
        });
        when(artifactMapper.insert(any(AdminImportJobArtifact.class))).thenAnswer(invocation -> {
            AdminImportJobArtifact artifact = invocation.getArgument(0);
            artifact.setId(ids.incrementAndGet());
            return 1;
        });

        AdminImportJobService.QualityCheckResult result = service.runQualityCheck(1L);

        assertThat(result.getPassed()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AdminImportJobService.STATUS_QUALITY_PASSED);
        assertThat(Files.exists(outputRoot.resolve("job-1").resolve("quality_gate.tsv"))).isTrue();
    }

    @Test
    void adminImportJob_shouldGenerateFormalSqlPath() throws Exception {
        storedJob = job(AdminImportJobService.STATUS_QUALITY_PASSED);
        when(artifactMapper.insert(any(AdminImportJobArtifact.class))).thenAnswer(invocation -> {
            AdminImportJobArtifact artifact = invocation.getArgument(0);
            artifact.setId(ids.incrementAndGet());
            return 1;
        });

        AdminImportJobService.SqlPackageResult result = service.generateFormalSql(1L);

        assertThat(result.getFormalSqlPath()).endsWith("formal_insert_body_only.sql");
        assertThat(result.getRollbackSqlPath()).endsWith("rollback.sql");
        assertThat(Files.exists(Path.of(result.getFormalSqlPath()))).isTrue();
        assertThat(Files.readString(Path.of(result.getFormalSqlPath()))).isEmpty();
        assertThat(storedJob.getStatus()).isEqualTo(AdminImportJobService.STATUS_PACKAGE_GENERATED);
    }

    @Test
    void adminImportJob_shouldGenerateRollbackPlan() throws Exception {
        storedJob = job(AdminImportJobService.STATUS_PACKAGE_GENERATED);
        when(artifactMapper.insert(any(AdminImportJobArtifact.class))).thenAnswer(invocation -> {
            AdminImportJobArtifact artifact = invocation.getArgument(0);
            artifact.setId(ids.incrementAndGet());
            return 1;
        });

        AdminImportJobService.RollbackPlanResult result = service.generateRollbackPlan(1L);

        assertThat(result.getRollbackSqlPath()).endsWith("rollback.sql");
        assertThat(result.getRollbackPlanPath()).endsWith("rollback_plan.md");
        assertThat(Files.readString(Path.of(result.getRollbackPlanPath()))).contains("formal_promote=manual_only");
        assertThat(result.getExpectedDeleteRows()).isZero();
        assertThat(storedJob.getStatus()).isEqualTo(AdminImportJobService.STATUS_READY_FOR_MANUAL_CONFIRMATION);
    }

    @Test
    void adminImportJob_shouldNotWriteFormalTables() throws Exception {
        storedJob = job(AdminImportJobService.STATUS_QUALITY_PASSED);
        when(artifactMapper.insert(any(AdminImportJobArtifact.class))).thenAnswer(invocation -> {
            AdminImportJobArtifact artifact = invocation.getArgument(0);
            artifact.setId(ids.incrementAndGet());
            return 1;
        });

        AdminImportJobService.SqlPackageResult result = service.generateFormalSql(1L);

        assertThat(Files.readString(Path.of(result.getFormalSqlPath()))).isEmpty();
        verify(jobMapper).updateById(any(AdminImportJob.class));
    }

    @Test
    void adminImportJob_shouldNotChangeReadiness() {
        storedJob = job("CREATED");
        assertThatThrownBy(() -> service.generateFormalSql(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("quality-check 未通过");
        verify(artifactMapper, never()).insert(any(AdminImportJobArtifact.class));
    }

    @Test
    void adminImportJob_shouldNotGenerateRollbackPlanBeforeSqlPackage() {
        storedJob = job(AdminImportJobService.STATUS_QUALITY_PASSED);
        assertThatThrownBy(() -> service.generateRollbackPlan(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SQL 包未生成");
        verify(artifactMapper, never()).insert(any(AdminImportJobArtifact.class));
    }

    private AdminImportJob job(String status) {
        AdminImportJob job = new AdminImportJob();
        job.setId(1L);
        job.setProvinceCode("GZ");
        job.setYear(2026);
        job.setBatchCode("NORMAL_UNDERGRADUATE");
        job.setSubjectType("物理类");
        job.setImportType("OFFICIAL_PLAN");
        job.setSourceType("OFFICIAL_PDF");
        job.setStatus(status);
        job.setSourceDir(sourceRoot.resolve("2026").toString());
        job.setOutputDir(outputRoot.resolve("job-1").toString());
        job.setCreatedBy("admin");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    private AdminImportJobFile file(Long id) {
        AdminImportJobFile file = new AdminImportJobFile();
        file.setId(id);
        file.setJobId(1L);
        file.setFileName("official.pdf");
        file.setFilePath(sourceRoot.resolve("2026").resolve("official.pdf").toString());
        file.setSha256("0".repeat(64));
        file.setFileSize(10L);
        file.setFileType("PDF");
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private AdminImportJobGate gate(Long id, String status) {
        AdminImportJobGate gate = new AdminImportJobGate();
        gate.setId(id);
        gate.setJobId(1L);
        gate.setGateName("files_registered");
        gate.setGateStatus(status);
        gate.setExpectedValue(">0");
        gate.setActualValue("1");
        gate.setCreatedAt(LocalDateTime.now());
        return gate;
    }

    private AdminImportJobArtifact artifact(Long id, String type, Path path) {
        AdminImportJobArtifact artifact = new AdminImportJobArtifact();
        artifact.setId(id);
        artifact.setJobId(1L);
        artifact.setArtifactType(type);
        artifact.setArtifactPath(path.toString());
        artifact.setSha256("0".repeat(64));
        artifact.setCreatedAt(LocalDateTime.now());
        return artifact;
    }
}
