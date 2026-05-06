package com.gzly.controller;

import com.gzly.common.exception.BizException;
import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.SichuanDataAdminService;
import com.gzly.service.SichuanDataAdminService.GroupLineImportRequest;
import com.gzly.service.SichuanDataAdminService.ImportResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProvinceDataAdminControllerTest {

    @Mock
    private SichuanDataAdminService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProvinceDataAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void status_passesProvinceCodeToService() throws Exception {
        StatusResponse response = new StatusResponse();
        response.setProvinceCode("HB");
        response.setProvinceName("湖北");
        response.setYear(2025);
        when(service.status("HB", 2025)).thenReturn(response);

        mockMvc.perform(get("/admin/province-data/HB/status").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("HB"));
    }

    @Test
    void importGroupLines_passesProvinceCodeAndDryRunFlag() throws Exception {
        ImportResult response = new ImportResult();
        response.setProvinceCode("AH");
        response.setYear(2025);
        response.setDryRun(false);
        when(service.importGroupLines(eq("AH"), any(), anyBoolean())).thenReturn(response);

        mockMvc.perform(post("/admin/province-data/AH/group-lines/import")
                        .param("dryRun", "false")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2025,
                                  "sourcePageUrl": "https://zs.example.edu.cn/ah-2025",
                                  "sourceUrl": "https://zs.example.edu.cn/ah-2025",
                                  "sourceLevel": "school_verified",
                                  "rows": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dryRun").value(false));

        ArgumentCaptor<GroupLineImportRequest> captor = ArgumentCaptor.forClass(GroupLineImportRequest.class);
        verify(service).importGroupLines(eq("AH"), captor.capture(), eq(false));
        assertThat(captor.getValue().getSourceLevel()).isEqualTo("school_verified");
    }

    @Test
    void status_returnsBizError_whenProvinceCodeNotSupported() throws Exception {
        when(service.status(eq("XX"), any())).thenThrow(new BizException("不支持的省份代码：XX"));

        mockMvc.perform(get("/admin/province-data/XX/status").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("不支持的省份代码：XX"));
    }

    @Test
    void status_returnsBadRequest_whenYearIsNotInteger() throws Exception {
        mockMvc.perform(get("/admin/province-data/HB/status").param("year", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("year")));
    }

    @Test
    void importGroupLines_returnsBadRequest_whenDryRunIsNotBoolean() throws Exception {
        mockMvc.perform(post("/admin/province-data/AH/group-lines/import")
                        .param("dryRun", "not-bool")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2025,
                                  "sourcePageUrl": "https://zs.example.edu.cn/ah-2025",
                                  "sourceUrl": "https://zs.example.edu.cn/ah-2025",
                                  "sourceLevel": "school_verified",
                                  "rows": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("dryRun")));
    }
}
