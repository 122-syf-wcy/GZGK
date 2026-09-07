package com.gzly.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizUserMapper;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号注销端点。挂载于 /api/me/account，由 WebMvcConfig 的 user 鉴权拦截器统一保护。
 *
 * <p>应用商店上架要求提供账号注销能力。实现方式为匿名化而非物理删除：
 * 邮箱置空、标识改为不可逆占位、昵称改为「已注销用户」，方案记录保留但不再关联
 * 任何个人身份信息（方案内容本身只含分数与选科，不含身份数据）。</p>
 *
 * <p>已知边界：JWT 无状态，注销后旧 token 在剩余有效期内仍可通过鉴权，
 * 但此时账号已无任何个人信息可暴露；客户端注销成功后必须立即清除本地令牌。
 * 如后续需要即时吊销，应在 AuthInterceptor 中接入 Redis 黑名单。</p>
 */
@Slf4j
@RestController
@RequestMapping("/me/account")
@RequiredArgsConstructor
public class MeAccountController {

    private final BizUserMapper bizUserMapper;
    private final JwtUtil jwtUtil;

    @PostMapping("/delete")
    public Result<Map<String, Object>> delete(@RequestBody(required = false) DeleteRequest req,
                                              HttpServletRequest httpReq) {
        if (req == null || !Boolean.TRUE.equals(req.getConfirm())) {
            throw new BizException("请确认注销操作");
        }
        Long userId = requireUserId(httpReq);
        BizUser user = bizUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("当前账号不存在或已注销");
        }

        // updateById 默认跳过 null 字段，置空 email 必须用 UpdateWrapper 显式 set
        bizUserMapper.update(null, Wrappers.<BizUser>lambdaUpdate()
                .eq(BizUser::getId, userId)
                .set(BizUser::getEmail, null)
                .set(BizUser::getIdentifier, "deleted_" + userId + "_" + System.currentTimeMillis())
                .set(BizUser::getNickname, "已注销用户")
                .set(BizUser::getUpdatedAt, LocalDateTime.now()));
        log.info("账号已注销并匿名化: userId={}", userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        return Result.ok(body);
    }

    private Long requireUserId(HttpServletRequest httpReq) {
        Object attr = httpReq.getAttribute("authUserId");
        if (attr instanceof Long uid && uid > 0) return uid;
        String auth = httpReq.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long uid = jwtUtil.getUserId(auth.substring(7));
            if (uid != null && uid > 0) return uid;
        }
        throw new BizException("未登录或登录态已过期");
    }

    @Data
    public static class DeleteRequest {
        private Boolean confirm;
    }
}
