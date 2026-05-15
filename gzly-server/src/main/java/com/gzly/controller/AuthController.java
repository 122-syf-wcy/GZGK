package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 卡密激活与登录入口（公开访问，由 PublicRateLimitInterceptor 限流）。
 *
 * <ul>
 *   <li>{@code POST /api/auth/card-key/activate} 首次激活：卡密 + 安全码 + 昵称 → 返回 token</li>
 *   <li>{@code POST /api/auth/card-key/login}    后续登录：卡密 + 安全码 → 返回 token</li>
 *   <li>{@code GET  /api/me}                      当前登录态查询，需 JWT user/admin 任一</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/auth/card-key")
@RequiredArgsConstructor
public class AuthController {

    private static final String CARD_KEY_GONE_MESSAGE = "卡密功能已下线，请使用安全码访问志愿方案。";

    @PostMapping("/activate")
    public Result<Map<String, Object>> activate(@RequestBody ActivateRequest req, HttpServletRequest httpReq) {
        if (req == null) throw new BizException("参数不能为空");
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest httpReq) {
        if (req == null) throw new BizException("参数不能为空");
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @GetMapping("/login")
    public Result<Map<String, Object>> loginGone() {
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @Data
    public static class ActivateRequest {
        private String cardKey;
        private String secretCode;
        private String nickname;
    }

    @Data
    public static class LoginRequest {
        private String cardKey;
        private String secretCode;
    }
}
