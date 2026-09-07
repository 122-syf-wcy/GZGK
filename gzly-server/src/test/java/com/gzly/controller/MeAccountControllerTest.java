package com.gzly.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizUserMapper;
import com.gzly.util.JwtUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeAccountControllerTest {

    @Mock
    private BizUserMapper bizUserMapper;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    /** 纯单元测试没有 MyBatis 环境，LambdaUpdateWrapper 需要手动注册实体元数据。 */
    @BeforeAll
    static void initEntityMeta() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizUser.class);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MeAccountController(bizUserMapper, jwtUtil))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void delete_requiresExplicitConfirm() throws Exception {
        mockMvc.perform(post("/me/account/delete")
                        .requestAttr("authUserId", 7L)
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("确认")));
        verify(bizUserMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void delete_rejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/me/account/delete")
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未登录")));
        verify(bizUserMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void delete_rejectsMissingUser() throws Exception {
        when(bizUserMapper.selectById(7L)).thenReturn(null);
        mockMvc.perform(post("/me/account/delete")
                        .requestAttr("authUserId", 7L)
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不存在")));
        verify(bizUserMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_anonymizesAccount() throws Exception {
        BizUser existing = new BizUser();
        existing.setId(7L);
        existing.setEmail("student@example.com");
        existing.setNickname("s***t");
        when(bizUserMapper.selectById(7L)).thenReturn(existing);

        mockMvc.perform(post("/me/account/delete")
                        .requestAttr("authUserId", 7L)
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(true));

        ArgumentCaptor<Wrapper<BizUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bizUserMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        LambdaUpdateWrapper<BizUser> wrapper = (LambdaUpdateWrapper<BizUser>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertThat(sqlSet).contains("email");
        assertThat(sqlSet).contains("identifier");
        assertThat(sqlSet).contains("nickname");
    }
}
