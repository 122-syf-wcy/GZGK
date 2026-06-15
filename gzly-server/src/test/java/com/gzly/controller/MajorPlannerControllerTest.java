package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.MajorPlannerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MajorPlannerControllerTest {

    @Test
    void evaluate_shouldReturnPlanCodeOnlyOnCreate() throws Exception {
        MajorPlannerService service = Mockito.mock(MajorPlannerService.class);
        MajorPlannerService.MajorPlannerView view = new MajorPlannerService.MajorPlannerView();
        view.setId(7L);
        view.setPlanNo("MP202606130001");
        view.setPlanCode("ABCD2345EFGH");
        view.setPlanCodeMasked("AB****GH");
        when(service.evaluate(any())).thenReturn(view);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/major-planner/evaluate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"subjectCategory\":\"物理 + 化学 + 政治\",\"likedSubjects\":[\"数学\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("ABCD2345EFGH"))
                .andExpect(jsonPath("$.data.planCodeMasked").value("AB****GH"));
    }

    @Test
    void detail_shouldReadPlanCodeFromHeader() throws Exception {
        MajorPlannerService service = Mockito.mock(MajorPlannerService.class);
        MajorPlannerService.MajorPlannerView view = new MajorPlannerService.MajorPlannerView();
        view.setId(7L);
        when(service.detail(7L, "ABCD2345EFGH")).thenReturn(view);
        MockMvc mvc = mvc(service);

        mvc.perform(get("/major-planner/results/7").header("X-Major-Plan-Code", "ABCD2345EFGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));

        verify(service).detail(7L, "ABCD2345EFGH");
    }

    @Test
    void aiAnalysis_shouldNotRequireCodeInUrl() throws Exception {
        MajorPlannerService service = Mockito.mock(MajorPlannerService.class);
        MajorPlannerService.AiAnalysisResult result = MajorPlannerService.AiAnalysisResult.ok("OK", true, false, "");
        when(service.aiAnalysis(7L, "ABCD2345EFGH", true)).thenReturn(result);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/major-planner/results/7/ai-analysis")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planCode\":\"ABCD2345EFGH\",\"forceRefresh\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("OK"));

        verify(service).aiAnalysis(7L, "ABCD2345EFGH", true);
    }

    private MockMvc mvc(MajorPlannerService service) {
        return MockMvcBuilders.standaloneSetup(new MajorPlannerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
