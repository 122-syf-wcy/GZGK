package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzly.common.exception.BizException;
import com.gzly.entity.University;
import com.gzly.entity.UniversityQa;
import com.gzly.mapper.UniversityMapper;
import com.gzly.mapper.UniversityQaMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 问答服务
 * status: 0=待AI审核, 1=已通过上线, 2=已拒绝, 3=AI已审待人工审核
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UniversityQaService {

    private static final String DEFAULT_TEXT_PASS_REASON = "未触发明显违规规则，建议人工复核后发布";
    private static final String DEFAULT_IMAGE_PASS_REASON = "未发现明显违规视觉内容，建议人工复核后发布";
    private static final String DEFAULT_REJECT_REASON = "命中平台内容安全策略，建议根据提示修改后重新提交";
    private static final String DEFAULT_ERROR_REASON = "AI审核流程异常，已转人工复核";
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.90D;
    private static final String REVIEW_ACTOR_AI = "ai";
    private static final String REVIEW_ACTOR_ALUMNI = "alumni";
    private static final String REVIEW_ACTOR_ADMIN = "admin";

    private final UniversityQaMapper qaMapper;
    private final UniversityMapper universityMapper;
    private final Executor taskExecutor;
    private final AiConfigService aiConfigService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 公开接口 ──

    /**
     * 查询某大学的已审核问答列表(问题+回答嵌套)
     */
    public List<QaThread> listApproved(String schoolId, int page, int pageSize) {
        // 查已审核的问题
        Page<UniversityQa> qPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<UniversityQa> qw = new LambdaQueryWrapper<>();
        qw.eq(UniversityQa::getSchoolId, schoolId)
          .isNull(UniversityQa::getParentId)
          .eq(UniversityQa::getStatus, 1)
          .orderByDesc(UniversityQa::getLikeCount)
          .orderByDesc(UniversityQa::getCreatedAt);
        Page<UniversityQa> questions = qaMapper.selectPage(qPage, qw);

        if (questions.getRecords().isEmpty()) {
            return List.of();
        }

        // 查这些问题的已审核回答
        List<Long> qIds = questions.getRecords().stream()
                .map(UniversityQa::getId).collect(Collectors.toList());
        LambdaQueryWrapper<UniversityQa> aw = new LambdaQueryWrapper<>();
        aw.in(UniversityQa::getParentId, qIds)
          .eq(UniversityQa::getStatus, 1)
          .orderByDesc(UniversityQa::getLikeCount)
          .orderByAsc(UniversityQa::getCreatedAt);
        List<UniversityQa> answers = qaMapper.selectList(aw);

        Map<Long, List<UniversityQa>> answerMap = answers.stream()
                .collect(Collectors.groupingBy(UniversityQa::getParentId));

        List<QaThread> threads = new ArrayList<>();
        for (UniversityQa q : questions.getRecords()) {
            QaThread t = new QaThread();
            t.setQuestion(q);
            t.setAnswers(answerMap.getOrDefault(q.getId(), List.of()));
            t.setAnswerCount(t.getAnswers().size());
            t.setTotalPages((int) questions.getPages());
            threads.add(t);
        }
        return threads;
    }

    /**
     * 提交问题
     */
    public UniversityQa submitQuestion(String schoolId, String content, String authorName,
                                        String authorType, String clientIp) {
        ensureQaEnabled(schoolId);
        UniversityQa qa = new UniversityQa();
        qa.setSchoolId(schoolId);
        qa.setParentId(null);
        qa.setContent(content.trim());
        qa.setAuthorName(authorName != null && !authorName.isBlank() ? authorName.trim() : "匿名考生");
        qa.setAuthorType(authorType != null ? authorType : "anonymous");
        qa.setStatus(0); // 先插入
        qa.setLikeCount(0);
        qa.setIpHash(hashIp(clientIp));
        qaMapper.insert(qa);

        // 异步AI审核
        aiReviewAsync(qa);
        return qa;
    }

    /**
     * 提交回答
     */
    public UniversityQa submitAnswer(Long questionId, String content, String authorName,
                                      String authorType, String clientIp) {
        UniversityQa question = qaMapper.selectById(questionId);
        if (question == null || question.getParentId() != null) {
            throw new IllegalArgumentException("问题不存在");
        }
        ensureQaEnabled(question.getSchoolId());

        UniversityQa answer = new UniversityQa();
        answer.setSchoolId(question.getSchoolId());
        answer.setParentId(questionId);
        answer.setContent(content.trim());
        answer.setAuthorName(authorName != null && !authorName.isBlank() ? authorName.trim() : "热心学长");
        answer.setAuthorType(authorType != null ? authorType : "anonymous");
        answer.setStatus(0);
        answer.setLikeCount(0);
        answer.setIpHash(hashIp(clientIp));
        qaMapper.insert(answer);

        // 异步AI审核
        aiReviewAsync(answer);
        return answer;
    }

    /**
     * 校友管理员回复本校问题（AI一审后交给系统管理员二审）
     */
    public UniversityQa alumniReply(Long questionId, String content, String authorName, String schoolId) {
        UniversityQa question = qaMapper.selectById(questionId);
        if (question == null || question.getParentId() != null) {
            throw new IllegalArgumentException("问题不存在");
        }
        if (!question.getSchoolId().equals(schoolId)) {
            throw new IllegalArgumentException("只能回复本校问题");
        }
        ensureQaEnabled(schoolId);

        UniversityQa answer = new UniversityQa();
        answer.setSchoolId(schoolId);
        answer.setParentId(questionId);
        answer.setContent(content.trim());
        answer.setAuthorName(authorName != null && !authorName.isBlank() ? authorName.trim() : "校友管理员");
        answer.setAuthorType("alumni");
        answer.setStatus(0); // 先待审
        answer.setLikeCount(0);
        qaMapper.insert(answer);

        // AI一审后按校友自治策略流转
        aiReviewAsync(answer);
        return answer;
    }

    public void alumniReview(Long id, int status, String reviewNote, Long alumniAdminId, String schoolId) {
        UniversityQa qa = qaMapper.selectById(id);
        if (qa == null) throw new BizException("问答不存在");
        validateReviewStatus(status);
        if (!schoolId.equals(qa.getSchoolId())) {
            throw new BizException("只能处理本校问答");
        }
        if (qa.getStatus() == null || qa.getStatus() != 3) {
            throw new BizException("仅待校友复核内容可处理");
        }
        qa.setStatus(status);
        qa.setReviewNote(status == 2 ? requireReviewNote(reviewNote) : "");
        qa.setReviewActorRole(REVIEW_ACTOR_ALUMNI);
        qa.setReviewActorId(alumniAdminId);
        qa.setReviewedAt(LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    public void updateAlumniReviewNote(Long id, String reviewNote, String schoolId, Long alumniAdminId) {
        UniversityQa qa = qaMapper.selectById(id);
        if (qa == null) throw new BizException("问答不存在");
        if (!schoolId.equals(qa.getSchoolId())) {
            throw new BizException("只能处理本校问答");
        }
        if (qa.getStatus() == null || qa.getStatus() != 2) {
            throw new BizException("仅已退回内容可修改退回说明");
        }
        qa.setReviewNote(requireReviewNote(reviewNote));
        qa.setReviewActorRole(REVIEW_ACTOR_ALUMNI);
        qa.setReviewActorId(alumniAdminId);
        qa.setReviewedAt(LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    public UniversityQa editReplyAndResubmit(Long id, String content, Long alumniAdminId, String schoolId) {
        UniversityQa qa = qaMapper.selectById(id);
        if (qa == null) throw new BizException("回复不存在");
        if (!schoolId.equals(qa.getSchoolId())) throw new BizException("只能编辑本校回复");
        if (!"alumni".equals(qa.getAuthorType())) throw new BizException("仅校友回复可编辑重提");
        if (qa.getStatus() == null || qa.getStatus() != 2) throw new BizException("仅已退回回复可编辑重提");
        qa.setContent(content.trim());
        qa.setStatus(0);
        qa.setAiReviewResult(null);
        qa.setReviewNote("");
        qa.setReviewActorRole(null);
        qa.setReviewActorId(null);
        qa.setReviewedAt(null);
        qaMapper.updateById(qa);
        aiReviewAsync(qa);
        return qa;
    }

    /**
     * 校友管理员查看本校待审核列表(status=3, AI通过待人工)
     */
    public Page<UniversityQa> listPendingBySchool(String schoolId, int page, int pageSize) {
        return qaMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UniversityQa>()
                        .eq(UniversityQa::getSchoolId, schoolId)
                        .eq(UniversityQa::getStatus, 3)
                        .orderByDesc(UniversityQa::getCreatedAt));
    }

    public List<UniversityQa> listAlumniRepliesBySchool(String schoolId) {
        return qaMapper.selectList(new LambdaQueryWrapper<UniversityQa>()
                .eq(UniversityQa::getSchoolId, schoolId)
                .eq(UniversityQa::getAuthorType, "alumni")
                .orderByDesc(UniversityQa::getCreatedAt));
    }

    public Page<UniversityQa> listMonitorLogs(String schoolId, Integer status, int page, int pageSize) {
        LambdaQueryWrapper<UniversityQa> wrapper = new LambdaQueryWrapper<UniversityQa>()
                .orderByDesc(UniversityQa::getCreatedAt);
        if (schoolId != null && !schoolId.isBlank()) {
            wrapper.eq(UniversityQa::getSchoolId, schoolId);
        }
        if (status != null) {
            wrapper.eq(UniversityQa::getStatus, status);
        }
        return qaMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    public List<QaMonitorSchoolVO> listMonitorSchools(String filter) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime oneDayAgo = now.minusHours(24);

        List<UniversityQa> qaList = qaMapper.selectList(new LambdaQueryWrapper<UniversityQa>()
                .ge(UniversityQa::getCreatedAt, sevenDaysAgo)
                .orderByDesc(UniversityQa::getCreatedAt));
        if (qaList.isEmpty()) {
            return List.of();
        }

        Map<String, List<UniversityQa>> grouped = qaList.stream()
                .collect(Collectors.groupingBy(UniversityQa::getSchoolId));
        Map<String, University> universityMap = universityMapper.selectList(new LambdaQueryWrapper<University>()
                        .in(University::getSchoolId, grouped.keySet()))
                .stream()
                .collect(Collectors.toMap(University::getSchoolId, item -> item));

        List<QaMonitorSchoolVO> result = new ArrayList<>();
        for (Map.Entry<String, List<UniversityQa>> entry : grouped.entrySet()) {
            String schoolId = entry.getKey();
            List<UniversityQa> rows = entry.getValue();
            University university = universityMap.get(schoolId);
            long askCount24h = rows.stream().filter(item -> item.getParentId() == null && isAfter(item.getCreatedAt(), oneDayAgo)).count();
            long replyCount24h = rows.stream().filter(item -> item.getParentId() != null && isAfter(item.getCreatedAt(), oneDayAgo)).count();
            long approvedCount24h = rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 1 && isAfter(item.getReviewedAt() != null ? item.getReviewedAt() : item.getCreatedAt(), oneDayAgo)).count();
            long autoRejectedCount24h = rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 2 && REVIEW_ACTOR_AI.equals(item.getReviewActorRole()) && isAfter(item.getReviewedAt(), oneDayAgo)).count();
            long alumniReviewCount24h = rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 3 && isAfter(item.getCreatedAt(), oneDayAgo)).count();
            if (askCount24h == 0 && replyCount24h == 0 && autoRejectedCount24h == 0 && alumniReviewCount24h == 0) {
                continue;
            }

            QaMonitorSchoolVO vo = new QaMonitorSchoolVO();
            vo.setSchoolId(schoolId);
            vo.setSchoolName(university != null ? university.getName() : schoolId);
            vo.setAskCount24h((int) askCount24h);
            vo.setReplyCount24h((int) replyCount24h);
            vo.setApprovedCount24h((int) approvedCount24h);
            vo.setAutoRejectedCount24h((int) autoRejectedCount24h);
            vo.setManualReviewCount24h((int) alumniReviewCount24h);
            vo.setLatestRiskAt(rows.stream().map(item -> item.getReviewedAt() != null ? item.getReviewedAt() : item.getCreatedAt()).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
            vo.setQaDisabled(university != null && Integer.valueOf(1).equals(university.getQaDisabled()));
            vo.setQaDisabledReason(university != null ? university.getQaDisabledReason() : null);
            vo.setQaDisabledUntil(university != null ? university.getQaDisabledUntil() : null);
            vo.setHighRisk(autoRejectedCount24h >= 3 || alumniReviewCount24h >= 5);
            vo.setRecentRiskLogs(rows.stream()
                    .filter(item -> (item.getStatus() != null && item.getStatus() == 2) || (item.getStatus() != null && item.getStatus() == 3))
                    .sorted(Comparator.comparing(UniversityQa::getCreatedAt).reversed())
                    .limit(3)
                    .map(item -> {
                        QaRiskSnippet snippet = new QaRiskSnippet();
                        snippet.setId(item.getId());
                        snippet.setContent(item.getContent());
                        snippet.setAuthorName(item.getAuthorName());
                        snippet.setStatus(item.getStatus());
                        snippet.setReviewNote(item.getReviewNote());
                        snippet.setCreatedAt(item.getCreatedAt());
                        return snippet;
                    })
                    .collect(Collectors.toList()));
            if (matchesMonitorFilter(vo, filter)) {
                result.add(vo);
            }
        }

        result.sort(Comparator.comparing(QaMonitorSchoolVO::isHighRisk).reversed()
                .thenComparing(QaMonitorSchoolVO::getLatestRiskAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public List<QaDailyTrendVO> listSevenDayTrend(String schoolId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<UniversityQa> wrapper = new LambdaQueryWrapper<UniversityQa>()
                .ge(UniversityQa::getCreatedAt, sevenDaysAgo)
                .orderByAsc(UniversityQa::getCreatedAt);
        if (schoolId != null && !schoolId.isBlank()) {
            wrapper.eq(UniversityQa::getSchoolId, schoolId);
        }
        List<UniversityQa> rows = qaMapper.selectList(wrapper);

        List<QaDailyTrendVO> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = now.minusDays(i).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            QaDailyTrendVO point = new QaDailyTrendVO();
            point.setLabel(dayStart.getMonthValue() + "/" + dayStart.getDayOfMonth());
            point.setAskCount((int) rows.stream().filter(item -> item.getParentId() == null && inRange(item.getCreatedAt(), dayStart, dayEnd)).count());
            point.setReplyCount((int) rows.stream().filter(item -> item.getParentId() != null && inRange(item.getCreatedAt(), dayStart, dayEnd)).count());
            point.setApprovedCount((int) rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 1 && inRange(item.getReviewedAt() != null ? item.getReviewedAt() : item.getCreatedAt(), dayStart, dayEnd)).count());
            point.setAutoRejectedCount((int) rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 2 && REVIEW_ACTOR_AI.equals(item.getReviewActorRole()) && inRange(item.getReviewedAt() != null ? item.getReviewedAt() : item.getCreatedAt(), dayStart, dayEnd)).count());
            point.setManualReviewCount((int) rows.stream().filter(item -> item.getStatus() != null && item.getStatus() == 3 && inRange(item.getCreatedAt(), dayStart, dayEnd)).count());
            result.add(point);
        }
        return result;
    }

    /**
     * 点赞
     */
    public void like(Long id) {
        qaMapper.update(null, new LambdaUpdateWrapper<UniversityQa>()
                .eq(UniversityQa::getId, id)
                .setSql("like_count = like_count + 1"));
    }

    // ── 管理接口 ──

    /**
     * 管理后台: 按学校+状态筛选全部QA
     */
    public Page<UniversityQa> listAll(String schoolId, Integer status, int page, int pageSize) {
        LambdaQueryWrapper<UniversityQa> qw = new LambdaQueryWrapper<>();
        if (schoolId != null && !schoolId.isBlank()) {
            qw.eq(UniversityQa::getSchoolId, schoolId);
        }
        if (status != null) {
            qw.eq(UniversityQa::getStatus, status);
        }
        qw.orderByDesc(UniversityQa::getCreatedAt);
        return qaMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /**
     * 待审核列表
     */
    public Page<UniversityQa> listPending(int page, int pageSize) {
        return qaMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UniversityQa>()
                        .eq(UniversityQa::getStatus, 3)
                        .orderByDesc(UniversityQa::getCreatedAt));
    }

    /**
     * 审核操作
     */
    public void review(Long id, int status, String reviewNote) {
        UniversityQa qa = qaMapper.selectById(id);
        if (qa == null) throw new BizException("记录不存在");
        validateReviewStatus(status);
        if (qa.getStatus() == null || qa.getStatus() != 3) {
            throw new BizException("仅待人工审核内容可进行二审");
        }
        qa.setStatus(status);
        qa.setReviewNote(status == 2 ? requireReviewNote(reviewNote) : "");
        qa.setReviewActorRole(REVIEW_ACTOR_ADMIN);
        qa.setReviewActorId(0L);
        qa.setReviewedAt(LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    public BatchReviewResult batchReview(List<Long> ids, int status, String reviewNote) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择至少一条问答记录");
        }
        validateReviewStatus(status);
        String normalizedReviewNote = status == 2 ? requireReviewNote(reviewNote) : "";

        List<UniversityQa> records = qaMapper.selectBatchIds(ids);
        Map<Long, UniversityQa> recordMap = records.stream()
                .collect(Collectors.toMap(UniversityQa::getId, item -> item));

        List<String> invalidItems = new ArrayList<>();
        for (Long id : ids) {
            UniversityQa record = recordMap.get(id);
            if (record == null) {
                invalidItems.add(id + " (不存在)");
                continue;
            }
            if (record.getStatus() == null || record.getStatus() != 3) {
                invalidItems.add(id + " (非待人工审核)");
            }
        }
        if (!invalidItems.isEmpty()) {
            throw new BizException("以下问答不可审核：" + String.join("；", invalidItems));
        }

        for (UniversityQa record : records) {
            record.setStatus(status);
            record.setReviewNote(normalizedReviewNote);
            record.setReviewActorRole(REVIEW_ACTOR_ADMIN);
            record.setReviewActorId(0L);
            record.setReviewedAt(LocalDateTime.now());
            qaMapper.updateById(record);
        }

        BatchReviewResult result = new BatchReviewResult();
        result.setProcessedCount(records.size());
        result.setProcessedIds(records.stream().map(UniversityQa::getId).collect(Collectors.toList()));
        return result;
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        // 如果是问题，同时删除所有回答
        UniversityQa qa = qaMapper.selectById(id);
        if (qa != null && qa.getParentId() == null) {
            qaMapper.delete(new LambdaQueryWrapper<UniversityQa>()
                    .eq(UniversityQa::getParentId, id));
        }
        qaMapper.deleteById(id);
    }

    /**
     * 统计待审核数量(含AI审核中0和AI通过待人工3)
     */
    public long countPending() {
        return qaMapper.selectCount(new LambdaQueryWrapper<UniversityQa>()
                .eq(UniversityQa::getStatus, 3));
    }

    // ── AI 内容审核 ──

    private void aiReviewAsync(UniversityQa qa) {
        try {
            taskExecutor.execute(() -> {
                try {
                    String aiResult = callAiContentReview(qa.getContent());
                    applyAiDecisionToQa(qa, aiResult);
                    log.info("AI review qa#{} finished, result={}", qa.getId(), aiResult);
                } catch (Exception e) {
                    log.warn("AI review failed for qa#{}, fallback to human review", qa.getId(), e);
                    applyAiDecisionToQa(qa, buildAiErrorResult(e.getMessage()));
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("AI review queue full for qa#{}, fallback to manual review", qa.getId());
            applyAiDecisionToQa(qa, buildAiErrorResult("系统繁忙，已转人工审核"));
        }
    }

    /**
     * 调用AI文本审核(gpt-4o-mini)，返回完整JSON结果字符串
     */
    public String callAiContentReview(String content) {
        return callAiContentReview(content, "text", DEFAULT_TEXT_PASS_REASON, DEFAULT_REJECT_REASON);
    }

    public String callAiContentReview(String content, String mode, String passReason, String rejectReason) {
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                return buildNormalizedAiResult(null, "AI服务未配置或已停用", "error");
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getReviewModel());
            body.put("max_tokens", 300);
            body.put("temperature", 0.0);

            ArrayNode messages = body.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content",
                    "你是高考志愿填报平台的内容安全审核员。请审核用户提交的问答内容。\n" +
                    "审核标准：\n" +
                    "1. 违规类型：色情低俗、暴力恐怖、政治敏感、广告推销、人身攻击侮辱、泄露个人隐私(手机号/身份证等)、违法犯罪信息、虚假招生信息\n" +
                    "2. 允许内容：正常的高考/大学/专业/录取咨询、学校评价(含负面但客观的)、学习生活经验分享\n" +
                    "请严格以JSON回复，必须包含reason字段说明审核依据：\n" +
                    "通过: {\"pass\":true,\"reason\":\"审核通过的依据说明\"}\n" +
                    "拒绝: {\"pass\":false,\"reason\":\"违规原因和具体依据\"}");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", "请审核以下内容：\n" + content);

            return normalizeAiReviewResult(callOpenAiApi(body, config), mode, passReason, rejectReason);
        } catch (Exception e) {
            return buildNormalizedAiResult(null, "AI接口异常：" + sanitizeAiText(e.getMessage()), "error");
        }
    }

    /**
     * 调用AI图片审核(gpt-4o视觉)，返回完整JSON结果字符串
     */
    public String callAiImageReview(String imageUrl) {
        try {
            AiConfigService.RuntimeAiConfig config = aiConfigService.currentRuntimeConfig();
            if (!config.isUsable()) {
                return buildNormalizedAiResult(null, "AI服务未配置或已停用", "error");
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getVisionModel());
            body.put("max_tokens", 300);
            body.put("temperature", 0.0);

            ArrayNode messages = body.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content",
                    "你是高考志愿填报平台的图片安全审核员。请审核用户上传的图片。\n" +
                    "审核标准：\n" +
                    "1. 违规类型：色情低俗、暴力血腥、政治敏感、广告二维码、个人隐私信息(身份证/准考证号码清晰可见)、违法内容\n" +
                    "2. 允许内容：校园风景、教学楼、食堂、宿舍、校园活动、录取通知书(模糊处理个人信息的)、学校宣传材料\n" +
                    "请严格以JSON回复，必须包含reason字段说明审核依据：\n" +
                    "通过: {\"pass\":true,\"reason\":\"图片内容描述和通过依据\"}\n" +
                    "拒绝: {\"pass\":false,\"reason\":\"违规原因和具体依据\"}");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode contentArr = userMsg.putArray("content");
            ObjectNode textPart = contentArr.addObject();
            textPart.put("type", "text");
            textPart.put("text", "请审核这张图片是否适合在高考志愿填报平台展示：");
            ObjectNode imgPart = contentArr.addObject();
            imgPart.put("type", "image_url");
            ObjectNode imgUrl = imgPart.putObject("image_url");
            imgUrl.put("url", imageUrl);
            imgUrl.put("detail", "low");

            return normalizeAiReviewResult(callOpenAiApi(body, config), "vision", DEFAULT_IMAGE_PASS_REASON, DEFAULT_REJECT_REASON);
        } catch (Exception e) {
            return buildNormalizedAiResult(null, "AI接口异常：" + sanitizeAiText(e.getMessage()), "error");
        }
    }

    public String buildAiErrorResult(String reason) {
        return buildNormalizedAiResult(null, normalizeErrorReason(reason), "error");
    }

    /**
     * 通用OpenAI API调用，解析AI回复JSON
     */
    private String callOpenAiApi(ObjectNode body, AiConfigService.RuntimeAiConfig config) {
        try {
            RequestBody reqBody = RequestBody.create(
                    body.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(resolveChatCompletionsUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(reqBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "{\"error\":\"HTTP " + response.code() + "\"}";
                }
                String respStr = response.body().string();
                JsonNode root = objectMapper.readTree(respStr);
                String aiContent = root.path("choices").path(0).path("message").path("content").asText("");
                if (aiContent.contains("\"pass\"")) {
                    String json = aiContent.substring(aiContent.indexOf("{"), aiContent.lastIndexOf("}") + 1);
                    return json;
                }
                return "{\"pass\":true,\"reason\":\"" + sanitizeAiText(aiContent) + "\"}";
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    // ── DTO ──

    @Data
    public static class QaThread {
        private UniversityQa question;
        private List<UniversityQa> answers;
        private int answerCount;
        private int totalPages;
    }

    @Data
    public static class BatchReviewResult {
        private int processedCount;
        private List<Long> processedIds;
    }

    @Data
    public static class QaMonitorSchoolVO {
        private String schoolId;
        private String schoolName;
        private int askCount24h;
        private int replyCount24h;
        private int approvedCount24h;
        private int autoRejectedCount24h;
        private int manualReviewCount24h;
        private boolean highRisk;
        private boolean qaDisabled;
        private String qaDisabledReason;
        private LocalDateTime qaDisabledUntil;
        private LocalDateTime latestRiskAt;
        private List<QaRiskSnippet> recentRiskLogs;
    }

    @Data
    public static class QaRiskSnippet {
        private Long id;
        private String content;
        private String authorName;
        private Integer status;
        private String reviewNote;
        private LocalDateTime createdAt;
    }

    @Data
    public static class QaDailyTrendVO {
        private String label;
        private int askCount;
        private int replyCount;
        private int approvedCount;
        private int autoRejectedCount;
        private int manualReviewCount;
    }

    @Data
    public static class AiReviewDecision {
        private Boolean pass;
        private String reason;
        private String mode;
        private Double confidence;
        private String decision;
    }

    // ── 工具 ──

    private String hashIp(String ip) {
        if (ip == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return ip.hashCode() + "";
        }
    }

    private void ensureQaEnabled(String schoolId) {
        University university = universityMapper.selectOne(new LambdaQueryWrapper<University>()
                .eq(University::getSchoolId, schoolId)
                .last("LIMIT 1"));
        if (university == null) {
            return;
        }
        boolean disabled = Integer.valueOf(1).equals(university.getQaDisabled());
        boolean stillDisabled = university.getQaDisabledUntil() == null || university.getQaDisabledUntil().isAfter(LocalDateTime.now());
        if (disabled && stillDisabled) {
            String reason = university.getQaDisabledReason() == null || university.getQaDisabledReason().isBlank()
                    ? "学校问答功能暂时关闭"
                    : university.getQaDisabledReason();
            throw new BizException(reason);
        }
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private void validateReviewStatus(int status) {
        if (status != 1 && status != 2) {
            throw new BizException("审核状态仅支持通过(1)或退回(2)");
        }
    }

    private String requireReviewNote(String reviewNote) {
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BizException("退回时必须填写原因");
        }
        return reviewNote.trim();
    }

    private boolean isAfter(LocalDateTime value, LocalDateTime compare) {
        return value != null && (value.isAfter(compare) || value.isEqual(compare));
    }

    private boolean inRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null && (value.isEqual(start) || value.isAfter(start)) && value.isBefore(end);
    }

    private boolean matchesMonitorFilter(QaMonitorSchoolVO vo, String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return true;
        }
        return switch (filter) {
            case "highRisk" -> vo.isHighRisk();
            case "manualReview" -> vo.getManualReviewCount24h() > 0;
            case "rejected" -> vo.getAutoRejectedCount24h() > 0;
            case "approved" -> vo.getApprovedCount24h() > 0;
            default -> true;
        };
    }

    private String normalizeAiReviewResult(String rawResult, String mode, String defaultPassReason, String defaultRejectReason) {
        try {
            JsonNode root = objectMapper.readTree(rawResult);
            if (root.has("error")) {
                return buildNormalizedAiResult(null, normalizeErrorReason(root.path("error").asText("")), "error");
            }
            JsonNode passNode = root.get("pass");
            if (passNode == null || passNode.isMissingNode()) {
                String fallbackReason = sanitizeAiText(root.path("reason").asText(rawResult));
                if (fallbackReason.isBlank()) {
                    fallbackReason = DEFAULT_ERROR_REASON;
                }
                return buildNormalizedAiResult(null, fallbackReason, "error");
            }

            Boolean pass = passNode.isBoolean() ? passNode.asBoolean() : null;
            String reason = sanitizeAiText(root.path("reason").asText(""));
            if (pass == null) {
                if (reason.isBlank()) {
                    reason = DEFAULT_ERROR_REASON;
                }
                return buildNormalizedAiResult(null, reason, "error");
            }
            if (reason.isBlank()) {
                reason = pass ? defaultPassReason : defaultRejectReason;
            }
            return buildNormalizedAiResult(pass, reason, mode);
        } catch (Exception e) {
            String reason = sanitizeAiText(rawResult);
            if (reason.isBlank()) {
                reason = DEFAULT_ERROR_REASON;
            }
            return buildNormalizedAiResult(null, reason, "error");
        }
    }

    private String buildNormalizedAiResult(Boolean pass, String reason, String mode) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            if (pass == null) {
                node.putNull("pass");
            } else {
                node.put("pass", pass);
            }
            String finalReason = sanitizeAiText(reason).isBlank() ? DEFAULT_ERROR_REASON : sanitizeAiText(reason);
            Double confidence = estimateConfidence(pass, mode);
            String decision = determineDecision(pass, confidence);
            node.put("reason", finalReason);
            node.put("mode", mode);
            if (confidence == null) {
                node.putNull("confidence");
            } else {
                node.put("confidence", confidence);
            }
            node.put("decision", decision);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"pass\":null,\"reason\":\"" + DEFAULT_ERROR_REASON + "\",\"mode\":\"error\",\"confidence\":null,\"decision\":\"error\"}";
        }
    }

    private String normalizeErrorReason(String reason) {
        String clean = sanitizeAiText(reason);
        if (clean.isBlank()) {
            return DEFAULT_ERROR_REASON;
        }
        if (clean.startsWith("AI接口异常：")) {
            return clean;
        }
        return "AI接口异常：" + clean;
    }

    private String sanitizeAiText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\n", " ").trim();
    }

    private Double estimateConfidence(Boolean pass, String mode) {
        if (pass == null || "error".equals(mode)) {
            return null;
        }
        if ("fallback-text".equals(mode)) {
            return 0.60D;
        }
        if ("vision".equals(mode) || "text".equals(mode)) {
            return 0.90D;
        }
        return 0.85D;
    }

    private String determineDecision(Boolean pass, Double confidence) {
        if (pass == null || confidence == null) {
            return "error";
        }
        if (Boolean.TRUE.equals(pass)) {
            return confidence >= HIGH_CONFIDENCE_THRESHOLD ? "auto_approved" : "manual_review";
        }
        return confidence >= HIGH_CONFIDENCE_THRESHOLD ? "auto_rejected" : "manual_review";
    }

    public AiReviewDecision parseAiReviewDecision(String rawResult) {
        try {
            JsonNode root = objectMapper.readTree(rawResult);
            AiReviewDecision result = new AiReviewDecision();
            if (root.has("pass") && !root.get("pass").isNull()) {
                result.setPass(root.get("pass").asBoolean());
            }
            result.setReason(root.path("reason").asText(DEFAULT_ERROR_REASON));
            result.setMode(root.path("mode").asText("error"));
            if (root.has("confidence") && !root.get("confidence").isNull()) {
                result.setConfidence(root.get("confidence").asDouble());
            }
            result.setDecision(root.path("decision").asText("error"));
            return result;
        } catch (Exception e) {
            AiReviewDecision result = new AiReviewDecision();
            result.setPass(null);
            result.setReason(DEFAULT_ERROR_REASON);
            result.setMode("error");
            result.setConfidence(null);
            result.setDecision("error");
            return result;
        }
    }

    private void applyAiDecisionToQa(UniversityQa qa, String aiResult) {
        AiReviewDecision decision = parseAiReviewDecision(aiResult);
        qa.setAiReviewResult(aiResult);
        qa.setReviewedAt(LocalDateTime.now());
        if ("auto_approved".equals(decision.getDecision())) {
            qa.setStatus(1);
            qa.setReviewNote("");
            qa.setReviewActorRole(REVIEW_ACTOR_AI);
            qa.setReviewActorId(0L);
        } else if ("auto_rejected".equals(decision.getDecision())) {
            qa.setStatus(2);
            qa.setReviewNote(decision.getReason());
            qa.setReviewActorRole(REVIEW_ACTOR_AI);
            qa.setReviewActorId(0L);
        } else {
            qa.setStatus(3);
            qa.setReviewNote("");
            qa.setReviewActorRole(null);
            qa.setReviewActorId(null);
            qa.setReviewedAt(null);
        }
        qaMapper.updateById(qa);
    }

    public void hideQaByAdmin(Long id, String reason, Long adminUserId) {
        UniversityQa qa = qaMapper.selectById(id);
        if (qa == null) throw new BizException("问答不存在");
        qa.setStatus(2);
        qa.setReviewNote(requireReviewNote(reason));
        qa.setReviewActorRole(REVIEW_ACTOR_ADMIN);
        qa.setReviewActorId(adminUserId);
        qa.setReviewedAt(LocalDateTime.now());
        qaMapper.updateById(qa);
    }

    public void toggleSchoolQa(String schoolId, boolean disabled, String reason, LocalDateTime until, Long adminUserId) {
        LambdaUpdateWrapper<University> wrapper = new LambdaUpdateWrapper<University>()
                .eq(University::getSchoolId, schoolId)
                .set(University::getQaDisabled, disabled ? 1 : 0)
                .set(University::getQaDisabledReason, disabled ? (reason == null ? "" : reason.trim()) : "")
                .set(University::getQaDisabledUntil, disabled ? until : null);
        universityMapper.update(null, wrapper);
    }
}
