package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.AdminImportJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void list_returnsJobs() throws Exception {
        when(service.list("HB", 2026, null, 50)).thenReturn(List.of(Map.of("id", 7L, "provinceCode", "HB")));

        mockMvc.perform(get("/admin/import-jobs")
                        .param("provinceCode", "HB")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].provinceCode").value("HB"));
    }

    @Test
    void create_neverPromotesFormalImport() throws Exception {
        when(service.create(any())).thenReturn(Map.of(
                "id", 11L,
                "provinceCode", "SC",
                "formalPromoteAllowed", false,
                "fullRecommendSwitchAllowed", false));

        mockMvc.perform(post("/admin/import-jobs")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"SC\",\"year\":2026,\"importType\":\"bundle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formalPromoteAllowed").value(false))
                .andExpect(jsonPath("$.data.fullRecommendSwitchAllowed").value(false));
    }

    @Test
    void generateFormalSql_delegatesToPackageOnlyService() throws Exception {
        when(service.generateFormalSql(5L)).thenReturn(Map.of("id", 5L, "status", "FORMAL_SQL_GENERATED"));

        mockMvc.perform(post("/admin/import-jobs/5/generate-formal-sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FORMAL_SQL_GENERATED"));

        verify(service).generateFormalSql(5L);
    }
}
