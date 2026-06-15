package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.PlanHistory;
import com.gzly.mapper.PlanHistoryMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台：志愿方案记录「恢复软删除」。
 *
 * <p>路径 {@code /admin/plans/**}（与 AdminController 同前缀，方法路径不冲突），
 * 由全局 AuthInterceptor(admin) 保护。<b>只做软删恢复，不物理操作数据。</b></p>
 */
@RestController
@RequestMapping("/admin/plans")
@RequiredArgsConstructor
public class AdminPlanOpsController {

    private final PlanHistoryMapper planMapper;

    @PostMapping("/{id}/restore")
    public Result<Map<String, Object>> restore(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BizException("方案 ID 不能为空");
        }
        int affected = restoreIds(List.of(id));
        if (affected <= 0) {
            throw new BizException("方案不存在或未处于已删除状态");
        }
        return Result.ok(Map.of("restoredCount", affected, "ids", List.of(id)));
    }

    @PostMapping("/batch-restore")
    public Result<Map<String, Object>> batchRestore(@RequestBody(required = false) BatchRestoreRequest body) {
        List<Long> ids = sanitize(body == null ? null : body.getIds());
        if (ids.isEmpty()) {
            throw new BizException("请选择要恢复的方案记录");
        }
        int affected = restoreIds(ids);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedCount", ids.size());
        result.put("restoredCount", affected);
        result.put("ids", ids);
        return Result.ok(result);
    }

    private int restoreIds(List<Long> ids) {
        List<Long> safeIds = sanitize(ids);
        if (safeIds.isEmpty()) {
            return 0;
        }
        return planMapper.update(null, new LambdaUpdateWrapper<PlanHistory>()
                .in(PlanHistory::getId, safeIds)
                .eq(PlanHistory::getDeleted, 1)
                .set(PlanHistory::getDeleted, 0)
                .set(PlanHistory::getDeletedAt, null)
                .set(PlanHistory::getDeletedBy, null)
                .set(PlanHistory::getDeleteReason, null));
    }

    private List<Long> sanitize(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).filter(x -> x > 0).distinct().limit(500).toList();
    }

    @Data
    public static class BatchRestoreRequest {
        private List<Long> ids;
    }
}
