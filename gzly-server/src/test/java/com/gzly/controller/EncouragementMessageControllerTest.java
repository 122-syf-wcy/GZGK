package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.EncouragementMessage;
import com.gzly.mapper.EncouragementMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EncouragementMessageControllerTest {

    @Mock
    private EncouragementMessageMapper messageMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EncouragementMessageController(messageMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsEmptyArrayWhenNoApprovedMessages() throws Exception {
        when(messageMapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/encouragement-messages").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void submit_rejectsContentTooShort() throws Exception {
        mockMvc.perform(post("/encouragement-messages")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nickname":"考生","content":"加油"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4-120")));
        verify(messageMapper, never()).insert(any(EncouragementMessage.class));
    }

    @Test
    void submit_rejectsContentTooLong() throws Exception {
        String longContent = "祝考生加油" + "鼓励".repeat(80);
        mockMvc.perform(post("/encouragement-messages")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nickname":"考生","content":"%s"}
                                """.formatted(longContent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4-120")));
        verify(messageMapper, never()).insert(any(EncouragementMessage.class));
    }

    @Test
    void submit_rejectsContentWithUrl() throws Exception {
        mockMvc.perform(post("/encouragement-messages")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nickname":"考生","content":"加油，详情见 https://spam.example/"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("广告")));
        verify(messageMapper, never()).insert(any(EncouragementMessage.class));
    }

    @Test
    void submit_rejectsContentWithBlockedWord() throws Exception {
        mockMvc.perform(post("/encouragement-messages")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nickname":"考生","content":"我可以帮你包录取，加油"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("广告")));
        verify(messageMapper, never()).insert(any(EncouragementMessage.class));
    }

    @Test
    void submit_acceptsCleanContentAndDefaultsBlankNickname() throws Exception {
        mockMvc.perform(post("/encouragement-messages")
                        .contentType(APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.42")
                        .content("""
                                {"nickname":"","content":"加油，所有考生都能找到属于自己的方向"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("贵州考生"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("加油")));
        verify(messageMapper).insert(any(EncouragementMessage.class));
    }
}
