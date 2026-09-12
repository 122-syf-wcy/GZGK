package com.gzly.config;

import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 路径注册对齐测试。
 *
 * 既有的 WebMvcConfigAdminAuthTest 只手工构造 AuthInterceptor 再调 preHandle，
 * 无法覆盖“拦截器注册层面”的缺陷（公开端点被通配链误拦正是此类漏网根因）。
 * 本测试直接读取 addInterceptors() 产生的真实注册结果，用 Spring 公开 API
 * {@link MappedInterceptor#matches} 对给定路径做真实命中判定，从而锁定注册契约。
 */
class WebMvcConfigPathRegistrationTest {

    /** 公开端点：/alumni/media/list 不应被任何鉴权链命中（AlumniController.listMedia 无鉴权）。 */
    @Test
    void publicMediaListPathIsNotIntercepted() {
        List<String> intercepting = new ArrayList<>();
        MockHttpServletRequest request = requestFor("/alumni/media/list");
        for (MappedInterceptor mi : loadRegistrations()) {
            if (mi.matches(request)) {
                intercepting.add(String.valueOf(mi.getInterceptor()));
            }
        }
        assertThat(intercepting)
                .as("/alumni/media/list 必须公开，不应被任何鉴权拦截器命中")
                .isEmpty();
    }

    /** 审核端点：/alumni/media/pending 仍须由前置 admin-only 链保护（不含校友角色）。 */
    @Test
    void pendingMediaPathStillProtectedByAdminChain() throws Exception {
        MappedInterceptor matched = firstMatch("/alumni/media/pending");
        assertThat(matched).as("/alumni/media/pending 必须仍被拦截").isNotNull();
        assertThat(allowedRoles(matched))
                .as("/alumni/media/pending 只能放行系统管理员 admin，不得放行校友角色")
                .contains("admin")
                .doesNotContain("alumni", "alumni_admin");
    }

    /** 审核端点：/alumni/media/review 及其子路径仍须由前置 admin-only 链保护。 */
    @Test
    void reviewMediaPathStillProtectedByAdminChain() throws Exception {
        for (String path : List.of("/alumni/media/review", "/alumni/media/review/batch")) {
            MappedInterceptor matched = firstMatch(path);
            assertThat(matched).as(path + " 必须仍被拦截").isNotNull();
            assertThat(allowedRoles(matched))
                    .as(path + " 只能放行系统管理员 admin，不得放行校友角色")
                    .contains("admin")
                    .doesNotContain("alumni", "alumni_admin");
        }
    }

    /** 校友域端点：alumni_admin(校友超管) 不应因新增角色而被降权。 */
    @Test
    void alumniDomainChainStillAllowsAlumniAdmin() throws Exception {
        MappedInterceptor matched = firstMatch("/alumni/content/edit");
        assertThat(matched).as("/alumni/content/edit 必须被校友域链拦截").isNotNull();
        assertThat(allowedRoles(matched))
                .as("校友超管 alumni_admin 属于校友域最高权限，应被放行")
                .contains("alumni", "alumni_admin");
    }

    // ── 从真实注册结果中提取 ──

    private static MappedInterceptor firstMatch(String path) {
        MockHttpServletRequest request = requestFor(path);
        for (MappedInterceptor mi : loadRegistrations()) {
            if (mi.matches(request)) {
                return mi;
            }
        }
        return null;
    }

    /** 真实请求的 path 由 DispatcherServlet 预解析缓存，Mock 请求需手动补这一步。 */
    private static MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static List<MappedInterceptor> loadRegistrations() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        // rate-limit 开关未注入时为 false，因此只会注册鉴权拦截器，正好聚焦本测试目标。
        WebMvcConfig config = new WebMvcConfig(jwtUtil, mock(StringRedisTemplate.class));
        ExposedRegistry registry = new ExposedRegistry();
        config.addInterceptors(registry);
        List<MappedInterceptor> result = new ArrayList<>();
        for (Object registration : registry.registrations()) {
            result.add((MappedInterceptor) registration);
        }
        return result;
    }

    /** InterceptorRegistry#getInterceptors 为 protected，通过子类暴露只读视图。 */
    private static final class ExposedRegistry extends InterceptorRegistry {
        List<Object> registrations() {
            return getInterceptors();
        }
    }

    /** 读取 AuthInterceptor 的放行角色。字段为其私有实现细节，缺失即快速失败。 */
    private static List<String> allowedRoles(MappedInterceptor registration) throws Exception {
        HandlerInterceptor interceptor = registration.getInterceptor();
        if (!(interceptor instanceof WebMvcConfig.AuthInterceptor)) {
            return List.of();
        }
        Field field = WebMvcConfig.AuthInterceptor.class.getDeclaredField("allowedRoles");
        field.setAccessible(true);
        String[] roles = (String[]) field.get(interceptor);
        return roles == null ? List.of() : List.of(roles);
    }
}
