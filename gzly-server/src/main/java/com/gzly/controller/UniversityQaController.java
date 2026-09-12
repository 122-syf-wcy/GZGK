package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.UniversityQaService;
import com.gzly.service.UniversityQaService.QaThread;
import com.gzly.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class UniversityQaController {

    private final UniversityQaService qaService;

    /** 查询某大学已审核问答 */
    @GetMapping("/list")
    public Result<List<QaThread>> list(
            @RequestParam String schoolId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(qaService.listApproved(schoolId, page, pageSize));
    }

    /** 提交问题 */
    @PostMapping("/ask")
    public Result<String> ask(@RequestBody AskRequest req, HttpServletRequest request) {
        if (req.getContent() == null || req.getContent().trim().length() < 5) {
            return Result.fail("问题内容至少5个字");
        }
        if (req.getContent().trim().length() > 500) {
            return Result.fail("问题内容不能超过500字");
        }
        qaService.submitQuestion(req.getSchoolId(), req.getContent(),
                req.getAuthorName(), req.getAuthorType(), ClientIpResolver.resolve(request));
        return Result.ok("问题已提交，AI审核后将自动发布或进入校友管理员复核");
    }

    /** 提交回答 */
    @PostMapping("/answer")
    public Result<String> answer(@RequestBody AnswerRequest req, HttpServletRequest request) {
        if (req.getContent() == null || req.getContent().trim().length() < 2) {
            return Result.fail("回答内容至少2个字");
        }
        if (req.getContent().trim().length() > 1000) {
            return Result.fail("回答内容不能超过1000字");
        }
        qaService.submitAnswer(req.getQuestionId(), req.getContent(),
                req.getAuthorName(), req.getAuthorType(), ClientIpResolver.resolve(request));
        return Result.ok("回答已提交，AI审核后将自动发布或进入校友管理员复核");
    }

    /** 点赞 */
    @PostMapping("/like/{id}")
    public Result<String> like(@PathVariable Long id) {
        qaService.like(id);
        return Result.ok("点赞成功");
    }

    // ── DTO ──

    @Data
    public static class AskRequest {
        private String schoolId;
        private String content;
        private String authorName;
        private String authorType;
    }

    @Data
    public static class AnswerRequest {
        private Long questionId;
        private String content;
        private String authorName;
        private String authorType;
    }
}
