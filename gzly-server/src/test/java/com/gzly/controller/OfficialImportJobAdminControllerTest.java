package com.gzly.controller;

import com.gzly.common.PageResult;
import com.gzly.service.OfficialImportJobService;
import com.gzly.service.OfficialImportJobService.CreateImportJobRequest;
import com.gzly.service.OfficialImportJobService.ImportJobView;
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
class OfficialImportJobAdminControllerTest {

    @Mock private OfficialImportJobService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OfficialImportJobAdminController(service)).build();
    }

    @Test
    void adminCanCreateImportJob() throws Exception {
        ImportJobView view = new ImportJobView();
        view.setJobId("imp_2026_test");
        view.setProvinceCode("GZ");
        view.setYear(2026);
        view.setDataType("SCORE_SEGMENT");
        view.setImportBatchId("gz_2026_score_segment_v1_20260513");
        view.setStatus("FILE_REGISTERED");
        view.setDryRunOnly(true);
        view.setFormalPromoteExecuted(false);
        when(service.createJob(any(CreateImportJobRequest.class), eq("admin"))).thenReturn(view);

        mockMvc.perform(post("/admin/import-jobs")
                        .requestAttr("authIdentifier", "admin")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "provinceCode": "GZ",
                                  "year": 2026,
                                  "dataType": "SCORE_SEGMENT",
                                  "sourceFile": "score-segment.xlsx",
                                  "sourceUrl": "https://zsksy.guizhou.gov.cn/score.xlsx"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobId").value("imp_2026_test"))
                .andExpect(jsonPath("$.data.formalPromoteExecuted").value(false));
    }

    @Test
    void adminCanListImportJobs() throws Exception {
        ImportJobView view = new ImportJobView();
        view.setJobId("imp_2026_test");
        view.setDataType("ADMISSION_PLAN");
        view.setStatus("QUALITY_CHECKED");
        when(service.listJobs("GZ", 2026, null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(view), 1, 1, 20));

        mockMvc.perform(get("/admin/import-jobs")
                        .param("provinceCode", "GZ")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dataType").value("ADMISSION_PLAN"))
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
