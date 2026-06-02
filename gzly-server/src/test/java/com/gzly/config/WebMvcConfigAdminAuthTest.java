package com.gzly.config;

import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebMvcConfigAdminAuthTest {

    @Test
    void adminSichuanDataPathRequiresAdminToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        WebMvcConfig.AuthInterceptor interceptor = new WebMvcConfig.AuthInterceptor(jwtUtil, "admin");

        MockHttpServletRequest noToken = new MockHttpServletRequest("GET", "/admin/sichuan-data/status");
        MockHttpServletResponse noTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(noToken, noTokenResponse, new Object())).isFalse();
        assertThat(noTokenResponse.getStatus()).isEqualTo(401);

        when(jwtUtil.isValid("user-token")).thenReturn(true);
        when(jwtUtil.getRole("user-token")).thenReturn("user");
        MockHttpServletRequest userToken = new MockHttpServletRequest("GET", "/admin/sichuan-data/status");
        userToken.addHeader("Authorization", "Bearer user-token");
        MockHttpServletResponse userTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(userToken, userTokenResponse, new Object())).isFalse();
        assertThat(userTokenResponse.getStatus()).isEqualTo(403);

        when(jwtUtil.isValid("admin-token")).thenReturn(true);
        when(jwtUtil.getRole("admin-token")).thenReturn("admin");
        when(jwtUtil.getUserId("admin-token")).thenReturn(0L);
        MockHttpServletRequest adminToken = new MockHttpServletRequest("GET", "/admin/sichuan-data/status");
        adminToken.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse adminTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(adminToken, adminTokenResponse, new Object())).isTrue();
        assertThat(adminToken.getAttribute("authRole")).isEqualTo("admin");
    }

    @Test
    void adminProvinceDataPathRequiresAdminToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        WebMvcConfig.AuthInterceptor interceptor = new WebMvcConfig.AuthInterceptor(jwtUtil, "admin");

        MockHttpServletRequest noToken = new MockHttpServletRequest("GET", "/admin/province-data/HB/status");
        MockHttpServletResponse noTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(noToken, noTokenResponse, new Object())).isFalse();
        assertThat(noTokenResponse.getStatus()).isEqualTo(401);

        when(jwtUtil.isValid("user-token")).thenReturn(true);
        when(jwtUtil.getRole("user-token")).thenReturn("user");
        MockHttpServletRequest userToken = new MockHttpServletRequest("GET", "/admin/province-data/AH/group-lines/import");
        userToken.addHeader("Authorization", "Bearer user-token");
        MockHttpServletResponse userTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(userToken, userTokenResponse, new Object())).isFalse();
        assertThat(userTokenResponse.getStatus()).isEqualTo(403);

        when(jwtUtil.isValid("admin-token")).thenReturn(true);
        when(jwtUtil.getRole("admin-token")).thenReturn("admin");
        when(jwtUtil.getUserId("admin-token")).thenReturn(0L);
        MockHttpServletRequest adminToken = new MockHttpServletRequest("POST", "/admin/province-data/SC/group-plans/import");
        adminToken.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse adminTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(adminToken, adminTokenResponse, new Object())).isTrue();
        assertThat(adminToken.getAttribute("authRole")).isEqualTo("admin");
    }

    @Test
    void adminImportJobPathRejectsNormalUserToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        WebMvcConfig.AuthInterceptor interceptor = new WebMvcConfig.AuthInterceptor(jwtUtil, "admin");

        when(jwtUtil.isValid("user-token")).thenReturn(true);
        when(jwtUtil.getRole("user-token")).thenReturn("user");
        MockHttpServletRequest userToken = new MockHttpServletRequest("POST", "/admin/import-jobs");
        userToken.addHeader("Authorization", "Bearer user-token");
        MockHttpServletResponse userTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(userToken, userTokenResponse, new Object())).isFalse();
        assertThat(userTokenResponse.getStatus()).isEqualTo(403);

        when(jwtUtil.isValid("admin-token")).thenReturn(true);
        when(jwtUtil.getRole("admin-token")).thenReturn("admin");
        when(jwtUtil.getUserId("admin-token")).thenReturn(0L);
        MockHttpServletRequest adminToken = new MockHttpServletRequest("GET", "/admin/data-year-readiness");
        adminToken.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse adminTokenResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(adminToken, adminTokenResponse, new Object())).isTrue();
        assertThat(adminToken.getAttribute("authRole")).isEqualTo("admin");
    }
}
