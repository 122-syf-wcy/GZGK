package com.gzly.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.SkillsQueryLog;
import com.gzly.entity.VolunteerAiAnalysis;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.mapper.SkillsQueryLogMapper;
import com.gzly.mapper.VolunteerAiAnalysisMapper;
import com.gzly.service.VolunteerService;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录态用户的「我的方案」端点。挂载于 /api/me/plans，依赖 WebMvcConfig 上的 user 鉴权拦截器。
 *
 * <ul>
 *   <li>{@code GET  /api/me/plans?page&size}                       分页列出当前用户所有方案</li>
 *   <li>{@code GET  /api/me/plans/:id}                             单条方案详情（按 user_id 校验所有权）</li>
 *   <li>{@code GET  /api/me/plans/:id/skills-history}              该方案下的 skills 对话历史</li>
 *   <li>{@code POST /api/me/plans/claim}                           把通过 accessKey 验证的旧匿名方案绑定到当前账号</li>
 * </ul>
 */
@RestController
@RequestMapping("/me/plans")
@RequiredArgsConstructor
public class MePlanController {

    private final PlanHistoryMapper planHistoryMapper;
    private final VolunteerAiAnalysisMapper aiAnalysisMapper;
    private final SkillsQueryLogMapper skillsQueryLogMapper;
    private final VolunteerService volunteerService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             HttpServletRequest httpReq) {
        Long userId = requireUserId(httpReq);
        size = Math.min(Math.max(size, 1), 50);
        page = Math.max(page, 1);
        Page<PlanHistory> result = planHistoryMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<PlanHistory>()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
                        .orderByDesc("created_at"));

        List<Map<String, Object>> items = result.getRecords().stream().map(this::toListItem).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", result.getTotal());
        body.put("page", page);
        body.put("size", size);
        return Result.ok(body);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest httpReq) {
        Long userId = requireUserId(httpReq);
        PlanHistory row = requirePlan(id, userId);

        Map<String, Object> body = new LinkedHashMap<>();
        VolunteerService.PlanResult plan = volunteerService.getPlanResultForInternal(row.getId());
        if (plan != null) {
            body.putAll(objectMapper.convertValue(plan, new TypeReference<Map<String, Object>>() {}));
        }
        body.put("id", row.getId());
        body.put("provinceCode", row.getProvinceCode());
        body.put("targetBatch", row.getTargetBatch());
        body.put("totalScore", row.getTotalScore());
        body.put("provinceRank", row.getProvinceRank());
        body.put("firstSubject", row.getFirstSubject());
        body.put("strategyMode", row.getStrategyMode());
        body.put("decisionPriority", row.getDecisionPriority());
        body.put("careerGoal", row.getCareerGoal());
        body.put("itemCount", row.getItemCount());
        body.put("createdAt", row.getCreatedAt());
        body.put("planJson", row.getPlanJson());
        body.put("manualReviewJson", row.getManualReviewJson());
        body.put("metricsJson", row.getMetricsJson());
        body.put("dataQualityWarning", row.getDataQualityWarning());

        VolunteerAiAnalysis analysis = aiAnalysisMapper.selectOne(new QueryWrapper<VolunteerAiAnalysis>()
                .eq("plan_id", id)
                .last("LIMIT 1"));
        if (analysis != null) {
            Map<String, Object> aiBlock = new LinkedHashMap<>();
            aiBlock.put("status", analysis.getAnalysisStatus());
            aiBlock.put("conclusion", analysis.getConclusionText());
            aiBlock.put("complianceStatus", analysis.getComplianceStatus());
            aiBlock.put("updatedAt", analysis.getUpdatedAt());
            aiBlock.put("sanitizedAnalysisText", analysis.getSanitizedAnalysisText());
            body.put("aiAnalysis", aiBlock);
        }
        Long skillCount = skillsQueryLogMapper.selectCount(new QueryWrapper<SkillsQueryLog>()
                .eq("plan_id", id));
        body.put("skillsCount", skillCount == null ? 0 : skillCount);
        return Result.ok(body);
    }

    /**
     * 把一份「旧匿名 / 跨设备生成」的方案绑定到当前登录账号。
     *
     * <p>只允许当 plan.user_id 为空或 0（即没有真实账号绑定）时挂载；如果方案本身已属于当前用户，
     * 直接返回幂等成功；如果属于其他真实账号，拒绝绑定，避免越权。</p>
     */
    @PostMapping("/claim")
    public Result<Map<String, Object>> claim(@RequestBody(required = false) ClaimRequest req,
                                             HttpServletRequest httpReq) {
        Long userId = requireUserId(httpReq);
        Long planId = req == null ? null : req.getPlanId();
        String accessKey = req == null ? null : firstNonBlank(req.getSafetyCode(), req.getAccessKey());
        if (planId == null || planId <= 0) throw new BizException("方案 id 不能为空");
        if (accessKey == null || accessKey.isBlank()) throw new BizException("访问密钥不能为空");
        if (!volunteerService.isValidPlanAccessKey(planId, accessKey.trim())) {
            throw new BizException("访问密钥无效，无法绑定该方案");
        }
        PlanHistory row = planHistoryMapper.selectById(planId);
        if (row == null) throw new BizException("方案不存在");
        if (Integer.valueOf(1).equals(row.getDeleted())) throw new BizException("方案已删除，无法绑定");
        Long currentOwner = row.getUserId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planId", planId);
        if (currentOwner != null && currentOwner > 0) {
            if (currentOwner.equals(userId)) {
                body.put("claimed", false);
                body.put("alreadyOwned", true);
                return Result.ok(body);
            }
            throw new BizException("该方案已被其他账号绑定，无法重复绑定");
        }
        PlanHistory update = new PlanHistory();
        update.setId(planId);
        update.setUserId(userId);
        planHistoryMapper.updateById(update);
        body.put("claimed", true);
        body.put("alreadyOwned", false);
        return Result.ok(body);
    }

    @GetMapping("/{id}/skills-history")
    public Result<List<Map<String, Object>>> skillsHistory(@PathVariable Long id, HttpServletRequest httpReq) {
        Long userId = requireUserId(httpReq);
        requirePlan(id, userId);

        List<SkillsQueryLog> logs = skillsQueryLogMapper.selectList(new QueryWrapper<SkillsQueryLog>()
                .eq("plan_id", id)
                .orderByAsc("created_at"));
        List<Map<String, Object>> items = logs.stream().map(log -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("question", log.getQuestion());
            m.put("answer", log.getSanitizedAnswer());
            m.put("complianceStatus", log.getComplianceStatus());
            m.put("createdAt", log.getCreatedAt());
            return m;
        }).toList();
        return Result.ok(items);
    }

    // ---------------------------------------------------------------------
    // 私有工具
    // ---------------------------------------------------------------------

    private Map<String, Object> toListItem(PlanHistory row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("provinceCode", row.getProvinceCode());
        m.put("targetBatch", row.getTargetBatch());
        m.put("totalScore", row.getTotalScore());
        m.put("provinceRank", row.getProvinceRank());
        m.put("firstSubject", row.getFirstSubject());
        m.put("strategyMode", row.getStrategyMode());
        m.put("itemCount", row.getItemCount());
        m.put("hasAiAnalysis", row.getAiAnalysis() != null && !row.getAiAnalysis().isBlank());
        m.put("createdAt", row.getCreatedAt());
        return m;
    }

    private PlanHistory requirePlan(Long id, Long userId) {
        if (id == null || id <= 0) throw new BizException("方案 id 不能为空");
        PlanHistory row = planHistoryMapper.selectById(id);
        if (row == null) throw new BizException("方案不存在");
        if (Integer.valueOf(1).equals(row.getDeleted())) throw new BizException("方案已删除");
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BizException("无权限访问该方案");
        }
        return row;
    }

    @lombok.Data
    public static class ClaimRequest {
        private Long planId;
        private String safetyCode;
        private String accessKey;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Long requireUserId(HttpServletRequest httpReq) {
        // AuthInterceptor 已经将 userId 写入 attribute，避免重复解析
        Object attr = httpReq.getAttribute("authUserId");
        if (attr instanceof Long uid && uid > 0) return uid;
        String auth = httpReq.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long uid = jwtUtil.getUserId(auth.substring(7));
            if (uid != null && uid > 0) return uid;
        }
        throw new BizException("未登录或登录态已过期");
    }
}
