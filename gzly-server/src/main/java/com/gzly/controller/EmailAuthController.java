package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.EmailAuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邮箱验证码注册 / 登录（一体化）。
 * 公开端点；服务层带发送冷却、每日上限与验证码错误次数限制。
 */
@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailAuthService emailAuthService;

    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendCode(@RequestBody SendCodeRequest req) {
        emailAuthService.sendCode(req == null ? null : req.getEmail());
        return Result.ok(Map.of("cooldownSeconds", 60));
    }

    @PostMapping("/login")
    public Result<EmailAuthService.LoginResult> login(@RequestBody LoginRequest req) {
        return Result.ok(emailAuthService.loginWithCode(
                req == null ? null : req.getEmail(),
                req == null ? null : req.getCode()));
    }

    @Data
    public static class SendCodeRequest {
        private String email;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String code;
    }
}
