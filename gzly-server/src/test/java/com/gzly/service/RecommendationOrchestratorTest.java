package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 1：编排入口的路由与门禁。行为约束：
 * 贵州走 VolunteerService、专业组省份走 ProfessionalGroupVolunteerService（与重构前一致），
 * 数据未就绪的省份在进入生成逻辑前被拦截。
 */
class RecommendationOrchestratorTest {

    private final ProvinceReadinessService readinessService = mock(ProvinceReadinessService.class);
    private final PolicyRuleService policyRuleService = mock(PolicyRuleService.class);
    private final MlPredictionService mlPredictionService = mock(MlPredictionService.class);
    private final VolunteerService volunteerService = mock(VolunteerService.class);
    private final ProfessionalGroupVolunteerService groupService = mock(ProfessionalGroupVolunteerService.class);

    private RecommendationOrchestrator orchestrator() {
        return new RecommendationOrchestrator(new ProvincePolicyService(), readinessService,
                policyRuleService, mlPredictionService, volunteerService, groupService,
                new com.fasterxml.jackson.databind.ObjectMapper(), List.of());
    }

    private VolunteerService.GenerateRequest request(String provinceCode) {
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setProvinceCode(provinceCode);
        req.setFirstSubject("物理");
        return req;
    }

    @Test
    void guizhouRoutesToMajorPipeline() {
        doNothing().when(readinessService).requireGenerationReady(anyString(), anyString());
        VolunteerService.PlanResult expected = new VolunteerService.PlanResult();
        when(volunteerService.generate(any(), any(), any())).thenReturn(expected);

        VolunteerService.PlanResult result = orchestrator().generate(request("GZ"), 1L, "127.0.0.1");

        assertThat(result).isSameAs(expected);
        verify(volunteerService).generate(any(), any(), any());
        verify(groupService, never()).generate(any(), any(), any());
    }

    @Test
    void professionalGroupProvinceRoutesToGroupPipeline() {
        doNothing().when(readinessService).requireGenerationReady(anyString(), anyString());
        VolunteerService.PlanResult expected = new VolunteerService.PlanResult();
        when(groupService.generate(any(), any(), any())).thenReturn(expected);

        VolunteerService.PlanResult result = orchestrator().generate(request("SC"), null, "127.0.0.1");

        assertThat(result).isSameAs(expected);
        verify(groupService).generate(any(), any(), any());
        verify(volunteerService, never()).generate(any(), any(), any());
    }

    @Test
    void notReadyProvinceIsBlockedBeforeGeneration() {
        doThrow(new BizException("湖北专区暂未开放智能生成：官方一分一段表尚未导入。"))
                .when(readinessService).requireGenerationReady(Mockito.eq("HB"), anyString());

        assertThatThrownBy(() -> orchestrator().generate(request("HB"), null, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("湖北");
        verify(groupService, never()).generate(any(), any(), any());
        verify(volunteerService, never()).generate(any(), any(), any());
    }

    @Test
    void newlySupportedProvincesNormalizeInsteadOfThrowing() {
        doNothing().when(readinessService).requireGenerationReady(anyString(), anyString());
        when(groupService.generate(any(), any(), any())).thenReturn(new VolunteerService.PlanResult());

        // 此前 GX/HI/YN/HA 在 normalizeProvinceCode 就抛"暂不支持该省份志愿生成"
        for (String code : new String[]{"GX", "HI", "YN", "HA"}) {
            orchestrator().generate(request(code), null, "127.0.0.1");
        }
        verify(groupService, Mockito.times(4)).generate(any(), any(), any());
    }

    @Test
    void groupGenerationReturnsCachedResultWithoutRecomputing() {
        doNothing().when(readinessService).requireGenerationReady(anyString(), anyString());
        VolunteerService.PlanResult cached = new VolunteerService.PlanResult();
        cached.setDataQualityWarning("cached-marker");
        String cachedJson;
        try {
            cachedJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cached);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        org.springframework.data.redis.core.StringRedisTemplate redis =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(cachedJson);

        RecommendationOrchestrator orchestrator = orchestrator();
        org.springframework.test.util.ReflectionTestUtils.setField(orchestrator, "stringRedisTemplate", redis);

        VolunteerService.PlanResult result = orchestrator.generate(request("SC"), 7L, "127.0.0.1");

        assertThat(result.getDataQualityWarning()).isEqualTo("cached-marker");
        verify(groupService, never()).generate(any(), any(), any());
    }

    @Test
    void groupGenerationAcquiresLockComputesAndCaches() {
        doNothing().when(readinessService).requireGenerationReady(anyString(), anyString());
        VolunteerService.PlanResult fresh = new VolunteerService.PlanResult();
        when(groupService.generate(any(), any(), any())).thenReturn(fresh);

        org.springframework.data.redis.core.StringRedisTemplate redis =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(ops.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

        RecommendationOrchestrator orchestrator = orchestrator();
        org.springframework.test.util.ReflectionTestUtils.setField(orchestrator, "stringRedisTemplate", redis);

        VolunteerService.PlanResult result = orchestrator.generate(request("SC"), 7L, "127.0.0.1");

        assertThat(result).isSameAs(fresh);
        verify(groupService).generate(any(), any(), any());
        // 结果写入缓存（键前缀 pg:result），供后续相同请求直接命中
        verify(ops).set(Mockito.startsWith("gzly:v7:pg:result:"), anyString(), any(java.time.Duration.class));
    }
}
