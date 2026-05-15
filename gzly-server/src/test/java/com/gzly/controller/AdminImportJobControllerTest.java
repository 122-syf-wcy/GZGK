package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.AdminImportJobService;
import com.gzly.service.AdminImportJobService.JobDetail;
import com.gzly.service.AdminImportJobService.JobListResponse;
import com.gzly.service.AdminImportJobService.JobSummary;
import com.gzly.service.AdminImportJobService.QualityCheckResult;
import com.gzly.service.AdminImportJobService.RollbackPlanResult;
import com.gzly.service.AdminImportJobService.SqlPackageResult;
import com.gzly.service.AdminImportJobService.StagingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminImportJobControllerTest {

    @Mock
    private AdminImportJobService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminImportJobController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createJob_returnsJobDetail() throws Exception {
        when(service.createJob(any(), eq("admin"))).thenReturn(detail(1L, "CREATED"));

        mockMvc.perform(post("/admin/import-jobs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "provinceCode": "GZ",
                                  "year": 2026,
                                  "batchCode": "NORMAL_UNDERGRADUATE",
                                  "subjectType": "物理类"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.job.id").value(1))
                .andExpect(jsonPath("$.data.job.status").value("CREATED"));
    }

    @Test
    void listJobs_returnsPage() throws Exception {
        JobListResponse response = new JobListResponse();
        response.setItems(List.of(summary(1L, "CREATED")));
        response.setTotal(1L);
        response.setPage(1);
        response.setSize(20);
        when(service.listJobs(1, 20, "GZ", 2026, null)).thenReturn(response);

        mockMvc.perform(get("/admin/import-jobs")
                        .param("provinceCode", "GZ")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].provinceCode").value("GZ"));
    }

    @Test
    void readJobDetail_returnsDetail() throws Exception {
        when(service.detail(1L)).thenReturn(detail(1L, "CREATED"));

        mockMvc.perform(get("/admin/import-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.id").value(1));
    }

    @Test
    void generateStaging_returnsDryRunResult() throws Exception {
        StagingResult response = new StagingResult();
        response.setJobId(1L);
        response.setStatus("STAGING_GENERATED");
        response.setDryRun(true);
        response.setStagingPath("/opt/gzly/data-output/guizhou/import-jobs/job/staging_manifest.json");
        response.setFileCount(1);
        when(service.generateStagingDryRun(1L)).thenReturn(response);

        mockMvc.perform(post("/admin/import-jobs/1/staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.status").value("STAGING_GENERATED"));
    }

    @Test
    void qualityCheck_returnsGateResult() throws Exception {
        QualityCheckResult response = new QualityCheckResult();
        response.setJobId(1L);
        response.setStatus("QUALITY_PASSED");
        response.setPassed(true);
        response.setGates(List.of());
        when(service.runQualityCheck(1L)).thenReturn(response);

        mockMvc.perform(post("/admin/import-jobs/1/quality-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void generateFormalSql_returnsPaths() throws Exception {
        SqlPackageResult response = new SqlPackageResult();
        response.setJobId(1L);
        response.setStatus("PACKAGE_GENERATED");
        response.setFormalSqlPath("/opt/gzly/data-output/guizhou/import-jobs/job/formal_insert_body_only.sql");
        response.setRollbackSqlPath("/opt/gzly/data-output/guizhou/import-jobs/job/rollback.sql");
        response.setCleanRowCount(0L);
        when(service.generateFormalSql(1L)).thenReturn(response);

        mockMvc.perform(post("/admin/import-jobs/1/generate-formal-sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formalSqlPath").value(org.hamcrest.Matchers.endsWith("formal_insert_body_only.sql")))
                .andExpect(jsonPath("$.data.cleanRowCount").value(0));
    }

    @Test
    void rollbackPlan_returnsPaths() throws Exception {
        RollbackPlanResult response = new RollbackPlanResult();
        response.setJobId(1L);
        response.setStatus("READY_FOR_MANUAL_CONFIRMATION");
        response.setRollbackSqlPath("/opt/gzly/data-output/guizhou/import-jobs/job/rollback.sql");
        response.setRollbackPlanPath("/opt/gzly/data-output/guizhou/import-jobs/job/rollback_plan.md");
        response.setExpectedDeleteRows(0L);
        when(service.generateRollbackPlan(1L)).thenReturn(response);

        mockMvc.perform(post("/admin/import-jobs/1/rollback-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_MANUAL_CONFIRMATION"))
                .andExpect(jsonPath("$.data.expectedDeleteRows").value(0));
    }

    private JobDetail detail(Long id, String status) {
        JobDetail detail = new JobDetail();
        detail.setJob(summary(id, status));
        detail.setFiles(List.of());
        detail.setGates(List.of());
        detail.setArtifacts(List.of());
        return detail;
    }

    private JobSummary summary(Long id, String status) {
        JobSummary summary = new JobSummary();
        summary.setId(id);
        summary.setProvinceCode("GZ");
        summary.setYear(2026);
        summary.setBatchCode("NORMAL_UNDERGRADUATE");
        summary.setSubjectType("物理类");
        summary.setImportType("OFFICIAL_PLAN");
        summary.setSourceType("OFFICIAL_PDF");
        summary.setStatus(status);
        summary.setSourceDir("/opt/gzly/data-sources/guizhou/2026");
        summary.setOutputDir("/opt/gzly/data-output/guizhou/import-jobs/job");
        summary.setCreatedBy("admin");
        return summary;
    }
}
