package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizUserMapper;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮箱验证码注册/登录测试：格式校验、冷却与上限、验证码校验、新用户自动建号。
 */
class EmailAuthServiceTest {

    private BizUserMapper bizUserMapper;
    private JavaMailSender mailSender;
    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private JwtUtil jwtUtil;
    private EmailAuthService service;

    @BeforeEach
    void setUp() throws Exception {
        bizUserMapper = mock(BizUserMapper.class);
        mailSender = mock(JavaMailSender.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generate(anyLong(), anyString())).thenReturn("mock-token");

        service = new EmailAuthService(bizUserMapper, mailSender, redisTemplate, jwtUtil);
        Field from = EmailAuthService.class.getDeclaredField("mailFrom");
        from.setAccessible(true);
        from.set(service, "noreply@example.com");
    }

    @Test
    void sendCode_rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.sendCode("not-an-email"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendCode_respectsCooldown() {
        when(redisTemplate.hasKey(startsWith("auth:email:cooldown:"))).thenReturn(true);
        assertThatThrownBy(() -> service.sendCode("student@example.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void sendCode_sendsMailAndStoresCode() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOps.increment(startsWith("auth:email:daily:"))).thenReturn(1L);

        service.sendCode("Student@Example.com");

        verify(valueOps).set(eq("auth:email:code:student@example.com"), anyString(), any(java.time.Duration.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void loginWithCode_rejectsWrongCode() {
        when(valueOps.increment(startsWith("auth:email:attempts:"))).thenReturn(1L);
        when(valueOps.get("auth:email:code:student@example.com")).thenReturn("123456");

        assertThatThrownBy(() -> service.loginWithCode("student@example.com", "654321"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("验证码错误");
    }

    @Test
    void loginWithCode_blocksAfterTooManyAttempts() {
        when(valueOps.increment(startsWith("auth:email:attempts:"))).thenReturn(6L);
        assertThatThrownBy(() -> service.loginWithCode("student@example.com", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("次数过多");
    }

    @Test
    void loginWithCode_createsUserOnFirstLogin() {
        when(valueOps.increment(startsWith("auth:email:attempts:"))).thenReturn(1L);
        when(valueOps.get("auth:email:code:student@example.com")).thenReturn("123456");
        when(bizUserMapper.selectOne(any())).thenReturn(null);
        when(bizUserMapper.insert(any(BizUser.class))).thenAnswer(invocation -> {
            BizUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        });

        EmailAuthService.LoginResult result = service.loginWithCode("student@example.com", "123456");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getUserId()).isEqualTo(88L);
        assertThat(result.getToken()).isEqualTo("mock-token");
        assertThat(result.getEmail()).isEqualTo("student@example.com");
    }

    @Test
    void loginWithCode_logsInExistingUser() {
        when(valueOps.increment(startsWith("auth:email:attempts:"))).thenReturn(1L);
        when(valueOps.get("auth:email:code:old@example.com")).thenReturn("123456");
        BizUser existing = new BizUser();
        existing.setId(7L);
        existing.setEmail("old@example.com");
        existing.setNickname("老同学");
        when(bizUserMapper.selectOne(any())).thenReturn(existing);
        when(bizUserMapper.updateById(any(BizUser.class))).thenReturn(1);

        EmailAuthService.LoginResult result = service.loginWithCode("old@example.com", "123456");

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getNickname()).isEqualTo("老同学");
    }
}
