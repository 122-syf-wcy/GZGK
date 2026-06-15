package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUserFeedback;
import com.gzly.mapper.BizUserFeedbackMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台：反馈工单处理状态管理。
 *
 * <p>路径 {@code /admin/feedback-ops/**}（与既有 /admin/feedbacks 解耦，避免改动 AdminController）。
 * 处理状态：0未处理 / 1处理中 / 2已解决 / 3已忽略。</p>
 */
@RestController
@RequestMapping("/admin/feedback-ops")
@RequiredArgsConstructor
public class AdminFeedbackOpsController {

    private final BizUserFeedbackMapper feedbackMapper;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer handleStatus) {
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 1);

        LambdaQueryWrapper<BizUserFeedback> qw = new LambdaQueryWrapper<BizUserFeedback>()
                .orderByAsc(BizUserFeedback::getHandleStatus)
                .orderByDesc(BizUserFeedback::getCreatedAt);
        if (handleStatus != null) {
            qw.eq(BizUserFeedback::getHandleStatus, handleStatus);
        }

        Page<BizUserFeedback> p = feedbackMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        result.put("counts", buildCounts());
        return Result.ok(result);
    }

    @PostMapping("/status")
    public Result<BizUserFeedback> updateStatus(@RequestBody StatusRequest req) {
        if (req == null || req.getId() == null) {
            throw new BizException("反馈ID不能为空");
        }
        if (req.getHandleStatus() == null || req.getHandleStatus() < 0 || req.getHandleStatus() > 3) {
            throw new BizException("处理状态非法（应为 0-3）");
        }
        BizUserFeedback feedback = feedbackMapper.selectById(req.getId());
        if (feedback == null) {
            throw new BizException("反馈不存在");
        }
        feedback.setHandleStatus(req.getHandleStatus());
        feedback.setHandledAt(LocalDateTime.now());
        if (req.getHandleNote() != null) {
            String note = req.getHandleNote().trim();
            feedback.setHandleNote(note.length() > 500 ? note.substring(0, 500) : note);
        }
        feedbackMapper.updateById(feedback);
        return Result.ok(feedback);
    }

    private Map<String, Object> buildCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("pending", count(0));
        counts.put("processing", count(1));
        counts.put("resolved", count(2));
        counts.put("ignored", count(3));
        return counts;
    }

    private long count(int handleStatus) {
        Long c = feedbackMapper.selectCount(new LambdaQueryWrapper<BizUserFeedback>()
                .eq(BizUserFeedback::getHandleStatus, handleStatus));
        return c == null ? 0L : c;
    }

    @Data
    public static class StatusRequest {
        private Long id;
        private Integer handleStatus;
        private String handleNote;
    }
}
