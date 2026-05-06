package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.BizUserFeedback;
import com.gzly.mapper.BizUserFeedbackMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock
    private BizUserFeedbackMapper feedbackMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedbackController(feedbackMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void submit_rejectsContentTooShort() throws Exception {
        mockMvc.perform(post("/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"content":"内容太短","sourcePage":"/home"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("10-500")));
        verify(feedbackMapper, never()).insert(any(BizUserFeedback.class));
    }

    @Test
    void submit_rejectsContentTooLong() throws Exception {
        String tooLong = "x".repeat(501);
        mockMvc.perform(post("/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"content":"%s","sourcePage":"/home"}
                                """.formatted(tooLong)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("10-500")));
        verify(feedbackMapper, never()).insert(any(BizUserFeedback.class));
    }

    @Test
    void submit_truncatesSourcePageAndAcceptsValidContent() throws Exception {
        String longSource = "/path?".repeat(40);
        mockMvc.perform(post("/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"content":"系统使用过程中遇到一个明显的问题：志愿生成卡住，请尽快查看","sourcePage":"%s"}
                                """.formatted(longSource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        ArgumentCaptor<BizUserFeedback> captor = ArgumentCaptor.forClass(BizUserFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourcePage().length()).isLessThanOrEqualTo(120);
        assertThat(captor.getValue().getContent()).contains("志愿生成卡住");
    }
}
