package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.entity.PlanHistory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzly.service.SafetyCodeIdentityService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.VolunteerService;
import com.gzly.service.CredentialAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/volunteer/plans")
@RequiredArgsConstructor
public class VolunteerPlanController {

    private final VolunteerService volunteerService;
    private final SafetyCodeRequestResolver safetyCodeRequestResolver;
    private static final String SAFETY_CODE_FORBIDDEN_MESSAGE = "安全码错误或无权访问该方案";
    private static final String SAFETY_CODE_REQUIRED_MESSAGE = "请先输入安全码";
    private static final int LIST_PAGE_SIZE_MAX = 50;

    private final SafetyCodeService safetyCodeService;
    private final SafetyCodeIdentityService safetyCodeIdentityService;
    private final CredentialAttemptLimiter credentialAttemptLimiter;
    private final PlanHistoryMapper planHistoryMapper;

    @GetMapping("/{planId}")
    public Result<VolunteerService.PlanResult> detail(@PathVariable Long planId,
                                                      HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request);
        if (!verifyPlanAccess(planId, key, request, "volunteer-plan-detail")) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, key);
        if (plan == null) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        return Result.ok(plan);
    }

    @GetMapping("")
    public Result<Map<String, Object>> listMyPlans(@RequestParam(value = "page", defaultValue = "1") int page,
                                                   @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                   HttpServletRequest request) {
        String code = safetyCodeRequestResolver.resolve(request);
        if (code == null || code.isBlank()) {
            throw new BizException(403, SAFETY_CODE_REQUIRED_MESSAGE);
        }
        String normalized = safetyCodeService.normalizeSafetyCode(code);
        String fingerprint = safetyCodeIdentityService.fingerprintOf(normalized);
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new BizException(403, SAFETY_CODE_REQUIRED_MESSAGE);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, LIST_PAGE_SIZE_MAX));
        QueryWrapper<PlanHistory> total = new QueryWrapper<PlanHistory>()
                .eq("safety_code_fingerprint", fingerprint)
                .eq("deleted", 0);
        long count = planHistoryMapper.selectCount(total);
        QueryWrapper<PlanHistory> wrapper = new QueryWrapper<PlanHistory>()
                .eq("safety_code_fingerprint", fingerprint)
                .eq("deleted", 0)
                .orderByDesc("created_at")
                .last("LIMIT " + ((safePage - 1) * safeSize) + ", " + safeSize);
        List<PlanHistory> rows = planHistoryMapper.selectList(wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (PlanHistory row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("planId", row.getId());
            item.put("createdAt", row.getCreatedAt());
            item.put("score", row.getTotalScore());
            item.put("rank", row.getProvinceRank());
            item.put("provinceCode", row.getProvinceCode());
            item.put("batchName", row.getTargetBatch());
            item.put("strategyMode", row.getStrategyMode());
            item.put("firstSubject", row.getFirstSubject());
            item.put("itemCount", row.getItemCount());
            records.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", count);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        return Result.ok(data);
    }

    @PostMapping("/{planId}/verify-safety-code")
    public Result<Map<String, Object>> verifySafetyCode(@PathVariable Long planId,
                                                        @RequestBody(required = false) VerifySafetyCodeRequest body,
                                                        HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request, body);
        if (!verifyPlanAccess(planId, key, request, "volunteer-plan-verify")) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        return Result.ok(Map.of("valid", true, "planId", planId));
    }

    private boolean verifyPlanAccess(Long planId, String key, HttpServletRequest request, String namespace) {
        String clientIp = credentialAttemptLimiter.clientIp(request);
        String target = planId == null ? "*" : String.valueOf(planId);
        credentialAttemptLimiter.ensureNotLocked(namespace, clientIp, target);
        boolean ok = safetyCodeService.verifyPlanAccess(planId, key);
        if (!ok) {
            credentialAttemptLimiter.recordFailure(namespace, clientIp, target);
            return false;
        }
        credentialAttemptLimiter.reset(namespace, clientIp, target);
        return true;
    }

    @Data
    public static class VerifySafetyCodeRequest {
        private String safetyCode;
        private String accessKey;
    }
}
