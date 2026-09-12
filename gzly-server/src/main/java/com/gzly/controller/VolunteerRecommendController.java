package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.RecommendationOrchestrator;
import com.gzly.service.VolunteerService;
import com.gzly.util.ClientIpResolver;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公开推荐接口。生成全流程（归一/门禁/政策/路由/ML/包装）统一在
 * {@link RecommendationOrchestrator#generateWithPolicy}，本控制器只负责 HTTP 关注点。
 */
@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class VolunteerRecommendController {

    private final RecommendationOrchestrator recommendationOrchestrator;
    private final JwtUtil jwtUtil;

    @PostMapping("/recommend")
    public Result<VolunteerService.PlanResult> recommend(@RequestBody VolunteerService.GenerateRequest req,
                                                         HttpServletRequest httpReq) {
        if (req == null) {
            return Result.fail(400, "参数不能为空");
        }
        return Result.ok(recommendationOrchestrator.generateWithPolicy(
                req, tryExtractUserId(httpReq), ClientIpResolver.resolve(httpReq)));
    }

    private Long tryExtractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return jwtUtil.getUserId(auth.substring(7));
        }
        return null;
    }
}
