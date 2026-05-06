package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.service.SiteStatsService;
import com.gzly.service.SiteStatsService.OnlineStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SiteStatsControllerTest {

    @Mock
    private SiteStatsService siteStatsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteStatsController(siteStatsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void online_returnsActiveUsersFromService() throws Exception {
        OnlineStats stats = new OnlineStats();
        stats.setActiveUsers(7L);
        stats.setWindowSeconds(300L);
        stats.setTotalViews(1234L);
        stats.setTodayViews(56L);
        when(siteStatsService.touchAndCount(any(HttpServletRequest.class))).thenReturn(stats);

        mockMvc.perform(get("/site-stats/online")
                        .header("User-Agent", "GZLYTest/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeUsers").value(7))
                .andExpect(jsonPath("$.data.windowSeconds").value(300))
                .andExpect(jsonPath("$.data.totalViews").value(1234))
                .andExpect(jsonPath("$.data.todayViews").value(56));
    }
}
