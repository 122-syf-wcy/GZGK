package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 未上线地区 AI 问答「对话码」服务。
 *
 * <p>安全约定：</p>
 * <ul>
 *   <li>数据库<b>只存哈希</b>（BCrypt）与不可逆<b>指纹</b>（SHA-256，仅用于定位会话），绝不存明文。</li>
 *   <li>对话码只在创建时完整返回一次，之后只能掩码展示。</li>
 *   <li>校验失败由上层做 5 次/IP 限流。</li>
 * </ul>
 *
 * <p>独立于 {@link SafetyCodeService}（后者绑定 PlanHistory），不复用其存储与口径。</p>
 */
@Service
public class AiQaSessionCodeService {

    private static final char[] CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int GENERATED_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${gzly.jwt.secret:}")
    private String jwtSecret;

    /** 生成新的对话码（明文，仅创建时使用）。 */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(CHARSET[RANDOM.nextInt(CHARSET.length)]);
        }
        return sb.toString();
    }

    /** 规范化对话码：去横线/空格、转大写、校验字符集与长度。 */
    public String normalize(String code) {
        if (code == null) {
            throw new BizException("请输入对话码");
        }
        String normalized = code.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BizException("请输入对话码");
        }
        if (normalized.length() < 8 || normalized.length() > 24) {
            throw new BizException("对话码长度需为8-24位");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) {
                throw new BizException("对话码仅支持字母和数字");
            }
        }
        return normalized;
    }

    /** 计算对话码的 BCrypt 哈希（落库，不可逆）。 */
    public String hash(String code) {
        return BCrypt.hashpw(payload(normalize(code)), BCrypt.gensalt(10));
    }

    /** 计算对话码的确定性指纹（落库，仅用于定位会话，不可逆）。 */
    public String fingerprint(String code) {
        return sha256Hex("ai-qa-code-fingerprint:" + normalize(code) + ":" + pepper());
    }

    /** 校验对话码是否匹配存储哈希。 */
    public boolean verify(String code, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(payload(normalize(code)), storedHash.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /** 掩码展示（找回时绝不返回完整码）。 */
    public String mask(String code) {
        String normalized;
        try {
            normalized = normalize(code);
        } catch (Exception e) {
            return "****";
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
    }

    private String payload(String normalizedCode) {
        return normalizedCode + ":" + pepper();
    }

    private String pepper() {
        return sha256Hex("ai-qa-code:" + (jwtSecret == null ? "" : jwtSecret.trim()));
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("对话码处理失败");
        }
    }
}
