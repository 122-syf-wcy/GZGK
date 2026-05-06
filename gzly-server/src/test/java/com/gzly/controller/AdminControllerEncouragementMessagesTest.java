package com.gzly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.EncouragementMessage;
import com.gzly.mapper.AlumniAdminMapper;
import com.gzly.mapper.AnnouncementMapper;
import com.gzly.mapper.BizUserFeedbackMapper;
import com.gzly.mapper.BizUserMapper;
import com.gzly.mapper.EncouragementMessageMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import com.gzly.mapper.SpecialAdmissionPolicyMapper;
import com.gzly.mapper.UniOfficialLinkMapper;
import com.gzly.mapper.UniversityMapper;
import com.gzly.service.AiConfigService;
import com.gzly.service.OfficialLinkPriorityService;
import com.gzly.service.UniversityQaService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerEncouragementMessagesTest {

    @Mock private BizUserMapper userMapper;
    @Mock private PlanHistoryMapper planMapper;
    @Mock private ScoreLineGzMapper scoreLineMapper;
    @Mock private UniversityMapper universityMapper;
    @Mock private AlumniAdminMapper alumniMapper;
    @Mock private MajorScoreGzMapper majorScoreMapper;
    @Mock private UniOfficialLinkMapper officialLinkMapper;
    @Mock private SpecialAdmissionPolicyMapper specialAdmissionPolicyMapper;
    @Mock private AnnouncementMapper announcementMapper;
    @Mock private BizUserFeedbackMapper feedbackMapper;
    @Mock private EncouragementMessageMapper encouragementMessageMapper;
    @Mock private UniversityQaService qaService;
    @Mock private AiConfigService aiConfigService;
    @Mock private OfficialLinkPriorityService officialLinkPriorityService;
    @Mock private VolunteerMetricsRecorder volunteerMetricsRecorder;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(
                userMapper,
                planMapper,
                scoreLineMapper,
                universityMapper,
                alumniMapper,
                majorScoreMapper,
                officialLinkMapper,
                specialAdmissionPolicyMapper,
                announcementMapper,
                feedbackMapper,
                encouragementMessageMapper,
                qaService,
                aiConfigService,
                officialLinkPriorityService,
                volunteerMetricsRecorder,
                jwtUtil
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldListEncouragementMessagesForAdmin() throws Exception {
        EncouragementMessage message = new EncouragementMessage();
        message.setId(12L);
        message.setNickname("贵州考生");
        message.setContent("稳住心态，按自己的节奏走。");
        message.setStatus(1);
        message.setCreatedAt(LocalDateTime.of(2026, 4, 26, 19, 50));

        Page<EncouragementMessage> page = new Page<>(1, 20);
        page.setRecords(List.of(message));
        page.setTotal(1);

        when(encouragementMessageMapper.selectPage(any(), any())).thenReturn(page);
        when(encouragementMessageMapper.selectCount(any())).thenReturn(1L, 0L);

        mockMvc.perform(get("/admin/encouragement-messages")
                        .param("page", "1")
                        .param("size", "20")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.visibleCount").value(1))
                .andExpect(jsonPath("$.data.hiddenCount").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(12))
                .andExpect(jsonPath("$.data.items[0].nickname").value("贵州考生"))
                .andExpect(jsonPath("$.data.items[0].status").value(1));
    }

    @Test
    void shouldSoftDeleteEncouragementMessage() throws Exception {
        EncouragementMessage message = new EncouragementMessage();
        message.setId(21L);
        message.setStatus(1);

        when(encouragementMessageMapper.selectById(21L)).thenReturn(message);

        mockMvc.perform(delete("/admin/encouragement-messages/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("已下架"));

        ArgumentCaptor<EncouragementMessage> captor = ArgumentCaptor.forClass(EncouragementMessage.class);
        verify(encouragementMessageMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectDeletingMissingEncouragementMessage() throws Exception {
        when(encouragementMessageMapper.selectById(404L)).thenReturn(null);

        mockMvc.perform(delete("/admin/encouragement-messages/404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("留言不存在"));
    }
}
