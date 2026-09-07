package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizUserMapper;
import com.gzly.util.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 邮箱验证码注册 / 登录一体服务（替代已下线的卡密体系）。
 *
 * 流程：发送验证码（60s 冷却、每日上限、5 分钟有效）→ 校验验证码
 * （错误次数限制防爆破）→ 邮箱未注册则自动建号 → 签发 user JWT。
 * 验证码与计数均存 Redis，无本地状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int DAILY_SEND_LIMIT = 10;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final String KEY_CODE = "auth:email:code:";
    private static final String KEY_COOLDOWN = "auth:email:cooldown:";
    private static final String KEY_DAILY = "auth:email:daily:";
    private static final String KEY_ATTEMPTS = "auth:email:attempts:";

    private final BizUserMapper bizUserMapper;
    private final JavaMailSender mailSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String mailFrom;

    // ══════════════════ 发送验证码 ══════════════════

    public void sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_COOLDOWN + email))) {
            throw new BizException("验证码发送过于频繁，请 1 分钟后再试");
        }
        Long dailyCount = stringRedisTemplate.opsForValue().increment(KEY_DAILY + email);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(KEY_DAILY + email, Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > DAILY_SEND_LIMIT) {
            throw new BizException("今日验证码发送次数已达上限，请明天再试");
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new BizException("邮件服务未配置，请联系管理员");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(KEY_CODE + email, code, CODE_TTL);
        stringRedisTemplate.delete(KEY_ATTEMPTS + email);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("【高考志愿辅助】邮箱验证码");
            message.setText("你的验证码是：" + code + "\n\n"
                    + "5 分钟内有效，用于登录高考志愿辅助系统。如非本人操作请忽略本邮件。\n"
                    + "本系统为公益辅助工具，结果仅供参考，请以省考试院和高校官方信息为准。");
            mailSender.send(message);
        } catch (Exception e) {
            stringRedisTemplate.delete(KEY_CODE + email);
            log.error("验证码邮件发送失败: email={}, err={}", maskEmail(email), e.getMessage());
            throw new BizException("验证码发送失败，请确认邮箱地址后重试");
        }
        stringRedisTemplate.opsForValue().set(KEY_COOLDOWN + email, "1", SEND_COOLDOWN);
        log.info("验证码已发送: email={}", maskEmail(email));
    }

    // ══════════════════ 验证码登录（未注册自动建号） ══════════════════

    public LoginResult loginWithCode(String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BizException("验证码格式不正确");
        }

        Long attempts = stringRedisTemplate.opsForValue().increment(KEY_ATTEMPTS + email);
        if (attempts != null && attempts == 1L) {
            stringRedisTemplate.expire(KEY_ATTEMPTS + email, CODE_TTL);
        }
        if (attempts != null && attempts > MAX_VERIFY_ATTEMPTS) {
            throw new BizException("验证码错误次数过多，请重新获取");
        }

        String expected = stringRedisTemplate.opsForValue().get(KEY_CODE + email);
        if (expected == null) {
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!expected.equals(code)) {
            throw new BizException("验证码错误，请重新输入");
        }
        stringRedisTemplate.delete(KEY_CODE + email);
        stringRedisTemplate.delete(KEY_ATTEMPTS + email);

        BizUser user = bizUserMapper.selectOne(new LambdaQueryWrapper<BizUser>()
                .eq(BizUser::getEmail, email)
                .last("LIMIT 1"));
        boolean created = false;
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            user = new BizUser();
            user.setEmail(email);
            user.setIdentifier(email);
            user.setNickname(defaultNickname(email));
            user.setRemainCount(0);
            user.setTotalUsed(0);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setLastLoginAt(now);
            bizUserMapper.insert(user);
            created = true;
            log.info("邮箱注册新用户: userId={}, email={}", user.getId(), maskEmail(email));
        } else {
            user.setLastLoginAt(now);
            user.setUpdatedAt(now);
            bizUserMapper.updateById(user);
        }

        LoginResult result = new LoginResult();
        result.setToken(jwtUtil.generate(user.getId(), email));
        result.setUserId(user.getId());
        result.setEmail(email);
        result.setNickname(user.getNickname());
        result.setNewUser(created);
        return result;
    }

    // ══════════════════ 工具 ══════════════════

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 128) {
            throw new BizException("邮箱格式不正确");
        }
        return email;
    }

    private String defaultNickname(String email) {
        String prefix = email.substring(0, email.indexOf('@'));
        if (prefix.length() <= 2) {
            return prefix + "同学";
        }
        return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return "***" + email.substring(at);
        }
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    @Data
    public static class LoginResult {
        private String token;
        private Long userId;
        private String email;
        private String nickname;
        private boolean newUser;
    }
}
