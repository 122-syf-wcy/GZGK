package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.SichuanDataAdminService;
import com.gzly.service.SichuanDataAdminService.ImportResult;
import com.gzly.service.SichuanDataAdminService.ScoreRankImportRequest;
import com.gzly.service.SichuanDataAdminService.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SichuanDataAdminControllerTest {

    @Mock
    private SichuanDataAdminService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SichuanDataAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void status_returnsSichuanDataStatus() throws Exception {
        StatusResponse response = new StatusResponse();
        response.setProvinceCode("SC");
        response.setProvinceName("四川");
        response.setYear(2025);
        when(service.status(2025)).thenReturn(response);

        mockMvc.perform(get("/admin/sichuan-data/status").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("SC"))
                .andExpect(jsonPath("$.data.year").value(2025));
    }

    @Test
    void importScoreRank_passesDryRunFlagAndBody() throws Exception {
        ImportResult response = new ImportResult();
        response.setProvinceCode("SC");
        response.setYear(2025);
        response.setDryRun(false);
        response.setTotalRows(1);
        when(service.importScoreRanks(any(), anyBoolean())).thenReturn(response);

        mockMvc.perform(post("/admin/sichuan-data/score-rank/import")
                        .param("dryRun", "false")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2025,
                                  "subjectType": "物理类",
                                  "sourcePageUrl": "https://www.sceea.cn/Html/202506/Newsdetail_4335.html",
                                  "sourceUrl": "https://www.sceea.cn/Upload/image/20250625/a.jpg",
                                  "rows": [{"score":700,"segmentCount":2,"cumulativeCount":2}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dryRun").value(false));

        ArgumentCaptor<ScoreRankImportRequest> captor = ArgumentCaptor.forClass(ScoreRankImportRequest.class);
        verify(service).importScoreRanks(captor.capture(), anyBoolean());
        assertThat(captor.getValue().getSubjectType()).isEqualTo("物理类");
    }
}
