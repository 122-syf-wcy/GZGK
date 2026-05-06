package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizCardKey;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizUserMapper;
import com.gzly.service.CardKeyService;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前登录用户查询端点。
 *
 * <p>路径 {@code /api/me} 由 WebMvcConfig 的 user 鉴权拦截器统一保护，
 * 未携带有效 user/admin token 时直接 401。</p>
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final BizUserMapper bizUserMapper;
    private final CardKeyService cardKeyService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public Result<Map<String, Object>> me(HttpServletRequest httpReq) {
        Long userId = extractUserId(httpReq);
        if (userId == null) throw new BizException("未登录或登录态已过期");
        BizUser user = bizUserMapper.selectById(userId);
        if (user == null) throw new BizException("当前账号不存在");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("identifier", user.getIdentifier());
        body.put("nickname", user.getNickname());
        body.put("lastLoginAt", user.getLastLoginAt());
        BizCardKey cardKey = cardKeyService.findByUserId(userId);
        if (cardKey != null) {
            int max = cardKey.getMaxPlans() == null ? 0 : cardKey.getMaxPlans();
            int used = cardKey.getUsedPlans() == null ? 0 : cardKey.getUsedPlans();
            body.put("maxPlans", max);
            body.put("usedPlans", used);
            body.put("remainPlans", Math.max(0, max - used));
            body.put("expiresAt", cardKey.getExpiresAt());
            body.put("cardKeyStatus", cardKey.getStatus());
        }
        return Result.ok(body);
    }

    private Long extractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return jwtUtil.getUserId(auth.substring(7));
    }
}
