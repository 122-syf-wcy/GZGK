package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.AlumniAdmin;
import com.gzly.entity.UniMedia;
import com.gzly.entity.UniContentEdit;
import com.gzly.entity.UniversityQa;
import com.gzly.entity.University;
import com.gzly.util.JwtUtil;
import com.gzly.util.PasswordUtil;
import com.gzly.util.SafeUrlUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.gzly.mapper.AlumniAdminMapper;
import com.gzly.mapper.UniMediaMapper;
import com.gzly.mapper.UniContentEditMapper;
import com.gzly.mapper.UniversityMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

@Slf4j
@RestController
@RequestMapping("/alumni")
@RequiredArgsConstructor
public class AlumniController {

    private final AlumniAdminMapper alumniMapper;
    private final UniMediaMapper mediaMapper;
    private final UniContentEditMapper editMapper;
    private final UniversityMapper universityMapper;
    private final com.gzly.service.UniversityQaService qaService;
    private final JwtUtil jwtUtil;
    private final Executor taskExecutor;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads";

    // ── 校友申请 ──

    @PostMapping("/apply")
    public Result<Long> apply(@RequestBody ApplyRequest req) {
        AlumniAdmin admin = new AlumniAdmin();
        admin.setSchoolId(req.getSchoolId());
        admin.setNickname(req.getNickname());
        admin.setPhone(req.getPhone());
        admin.setEmail(req.getEmail());
        admin.setCredentialUrl(req.getCredentialUrl());
        admin.setGraduationYear(req.getGraduationYear());
        admin.setMajor(req.getMajor());
        admin.setBio(req.getBio());
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            throw new BizException("密码至少8位");
        }
        admin.setPasswordHash(PasswordUtil.hash(req.getPassword()));
        admin.setRole(0);
        admin.setStatus(0);
        admin.setCreatedAt(LocalDateTime.now());
        alumniMapper.insert(admin);
        log.info("Alumni application: {} for school {}", req.getNickname(), req.getSchoolId());
        return Result.ok(admin.getId());
    }

    @GetMapping("/has-admin")
    public Result<Boolean> hasAdmin(@RequestParam String schoolId) {
        Long count = alumniMapper.selectCount(
            new LambdaQueryWrapper<AlumniAdmin>()
                .eq(AlumniAdmin::getSchoolId, schoolId)
                .eq(AlumniAdmin::getStatus, 1));
        return Result.ok(count > 0);
    }

    @GetMapping("/application/status")
    public Result<Map<String, Object>> applicationStatus(@RequestParam String phone) {
        AlumniAdmin a = alumniMapper.selectOne(
            new LambdaQueryWrapper<AlumniAdmin>().eq(AlumniAdmin::getPhone, phone)
                .orderByDesc(AlumniAdmin::getCreatedAt).last("LIMIT 1"));
        if (a == null) return Result.ok(null);
        Map<String, Object> safe = new java.util.HashMap<>();
        safe.put("status", a.getStatus());
        safe.put("role", a.getRole());
        safe.put("schoolId", a.getSchoolId());
        return Result.ok(safe);
    }

    // ── 管理员登录 ──

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        AlumniAdmin a = alumniMapper.selectOne(
            new LambdaQueryWrapper<AlumniAdmin>().eq(AlumniAdmin::getPhone, req.getPhone())
                .eq(AlumniAdmin::getStatus, 1));
        if (a == null) throw new BizException("账号不存在或未通过审核");
        if (!PasswordUtil.matches(req.getPassword(), a.getPasswordHash()))
            throw new BizException("密码错误");
        if (PasswordUtil.needsRehash(a.getPasswordHash())) {
            a.setPasswordHash(PasswordUtil.hash(req.getPassword()));
        }
        a.setLastLoginAt(LocalDateTime.now());
        alumniMapper.updateById(a);
        // 校友超管（role>=9）在校友域内是最高权限，但不等同于系统管理员：
        // 签发 alumni_admin 而非 admin，避免其令牌通过 /admin/** 的 admin-only 链越权。
        String role = a.getRole() != null && a.getRole() >= 9 ? "alumni_admin" : "alumni";
        String token = jwtUtil.generate(a.getId(), a.getSchoolId(), role);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("token", token);
        result.put("admin", a);
        return Result.ok(result);
    }

    // ── 超管审核 ──

    @GetMapping("/admin/applications")
    public Result<List<AlumniAdmin>> pendingApplications(
            @RequestParam(defaultValue = "0") int status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlumniAdmin> p = alumniMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<AlumniAdmin>().eq(AlumniAdmin::getStatus, status)
                .orderByDesc(AlumniAdmin::getCreatedAt));
        return Result.ok(p.getRecords());
    }

    @PostMapping("/admin/review")
    public Result<Void> reviewApplication(@RequestBody ReviewRequest req) {
        if (req.getId() == null) throw new BizException("审核ID不能为空");
        alumniMapper.update(null, new LambdaUpdateWrapper<AlumniAdmin>()
            .eq(AlumniAdmin::getId, req.getId())
            .set(AlumniAdmin::getStatus, req.getApproved() ? 1 : 2)
            .set(AlumniAdmin::getRole, req.getApproved() ? 1 : 0)
            .set(!req.getApproved(), AlumniAdmin::getRejectReason, req.getReason()));
        return Result.ok(null);
    }

    // ── 图片上传 ──

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @PostMapping("/media/upload")
    public Result<String> uploadMedia(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BizException("文件为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BizException("文件不能超过10MB");
        String ext = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) throw new BizException("不支持的文件类型: " + ext);
        String filename = UUID.randomUUID() + ext;
        Path dir = Paths.get(UPLOAD_DIR, "media");
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        if (!target.normalize().startsWith(dir.normalize())) throw new BizException("非法文件路径");
        file.transferTo(target.toFile());
        String url = "/uploads/media/" + filename;
        return Result.ok(url);
    }

    @PostMapping("/media/save")
    public Result<Long> saveMedia(@RequestBody SaveMediaRequest req, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        String schoolId = req.getSchoolId() != null && !req.getSchoolId().isBlank() ? req.getSchoolId() : currentAdmin.getSchoolId();
        verifyAlumniOwnership(currentAdmin.getId(), schoolId);
        UniMedia m = new UniMedia();
        m.setSchoolId(schoolId);
        m.setUploaderId(currentAdmin.getId());
        m.setMediaType(req.getMediaType());
        m.setUrl(sanitizeMediaUrl(req.getUrl(), req.getMediaType()));
        m.setThumbUrl(sanitizeOptionalUrl(req.getThumbUrl()));
        m.setCaption(req.getCaption());
        m.setSortOrder(req.getSortOrder());
        m.setStatus(0);
        m.setCreatedAt(LocalDateTime.now());
        mediaMapper.insert(m);

        // 所有媒体内容都先走 AI 一审，再交给系统管理员二审
        final Long mediaId = m.getId();
        try {
            taskExecutor.execute(() -> aiReviewMediaAsync(mediaId, req));
        } catch (RejectedExecutionException e) {
            markMediaPendingManual(mediaId, "系统繁忙，已转人工审核");
        }

        return Result.ok(m.getId());
    }

    @GetMapping("/media/list")
    public Result<List<UniMedia>> listMedia(
            @RequestParam String schoolId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<UniMedia> wrapper = new LambdaQueryWrapper<UniMedia>()
                .eq(UniMedia::getSchoolId, schoolId)
                .orderByAsc(UniMedia::getSortOrder)
                .orderByDesc(UniMedia::getCreatedAt);
        if (status != null) {
            wrapper.eq(UniMedia::getStatus, status);
        }
        List<UniMedia> list = mediaMapper.selectList(wrapper);
        return Result.ok(list);
    }

    @GetMapping("/media/pending")
    public Result<List<UniMedia>> pendingMedia(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<UniMedia> wrapper = new LambdaQueryWrapper<UniMedia>()
                .orderByDesc(UniMedia::getCreatedAt);
        if (status != null && status > 0) {
            wrapper.eq(UniMedia::getStatus, status);
        }
        Page<UniMedia> p = mediaMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(p.getRecords());
    }

    @DeleteMapping("/media/{id}")
    public Result<Void> deleteMedia(@PathVariable Long id, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        UniMedia media = mediaMapper.selectById(id);
        if (media != null && !media.getUploaderId().equals(currentAdmin.getId())) {
            if (currentAdmin.getRole() == null || currentAdmin.getRole() < 9) {
                throw new BizException("无权删除其他人的内容");
            }
        }
        mediaMapper.deleteById(id);
        return Result.ok(null);
    }

    @PostMapping("/media/review")
    public Result<Void> reviewMedia(@RequestBody ReviewRequest req, HttpServletRequest request) {
        if (req.getId() == null) throw new BizException("审核ID不能为空");
        if (req.getApproved() == null) throw new BizException("审核动作不能为空");
        UniMedia media = mediaMapper.selectById(req.getId());
        if (media == null) throw new BizException("内容不存在");
        if (media.getStatus() == null || media.getStatus() != 3) {
            throw new BizException("仅待人工审核内容可进行二审");
        }
        String reviewNote = Boolean.TRUE.equals(req.getApproved()) ? "" : requireReviewNote(req.getReason());
        Long reviewerId = getAuthUserId(request);
        mediaMapper.update(null, new LambdaUpdateWrapper<UniMedia>()
            .eq(UniMedia::getId, req.getId())
            .eq(UniMedia::getStatus, 3)
            .set(UniMedia::getStatus, req.getApproved() ? 1 : 2)
            .set(UniMedia::getReviewNote, reviewNote)
            .set(UniMedia::getReviewActorRole, "admin")
            .set(UniMedia::getReviewActorId, reviewerId)
            .set(UniMedia::getReviewedAt, LocalDateTime.now()));
        return Result.ok(null);
    }

    @PostMapping("/media/review/batch")
    public Result<Map<String, Object>> reviewMediaBatch(@RequestBody BatchReviewRequest req, HttpServletRequest request) {
        if (req.getIds() == null || req.getIds().isEmpty()) throw new BizException("请选择至少一条媒体内容");
        if (req.getApproved() == null) throw new BizException("审核动作不能为空");
        String reviewNote = Boolean.TRUE.equals(req.getApproved()) ? "" : requireReviewNote(req.getReason());
        Long reviewerId = getAuthUserId(request);
        List<UniMedia> records = mediaMapper.selectBatchIds(req.getIds());
        Map<Long, UniMedia> recordMap = records.stream().collect(java.util.stream.Collectors.toMap(UniMedia::getId, item -> item));
        List<String> invalidItems = new java.util.ArrayList<>();
        for (Long id : req.getIds()) {
            UniMedia record = recordMap.get(id);
            if (record == null) {
                invalidItems.add(id + " (不存在)");
                continue;
            }
            if (record.getStatus() == null || record.getStatus() != 3) {
                invalidItems.add(id + " (非待人工审核)");
            }
        }
        if (!invalidItems.isEmpty()) {
            throw new BizException("以下媒体不可审核：" + String.join("；", invalidItems));
        }
        for (UniMedia record : records) {
            record.setStatus(Boolean.TRUE.equals(req.getApproved()) ? 1 : 2);
            record.setReviewNote(reviewNote);
            record.setReviewActorRole("admin");
            record.setReviewActorId(reviewerId);
            record.setReviewedAt(LocalDateTime.now());
            mediaMapper.updateById(record);
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("processedCount", records.size());
        result.put("processedIds", records.stream().map(UniMedia::getId).toList());
        return Result.ok(result);
    }

    // ── 内容编辑 ──

    @PostMapping("/content/edit")
    public Result<Long> submitEdit(@RequestBody UniContentEdit edit, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        verifyAlumniOwnership(currentAdmin.getId(), edit.getSchoolId());
        edit.setEditorId(currentAdmin.getId());
        edit.setStatus(0);
        edit.setCreatedAt(LocalDateTime.now());
        editMapper.insert(edit);
        final Long editId = edit.getId();
        try {
            taskExecutor.execute(() -> aiReviewContentEditAsync(editId));
        } catch (RejectedExecutionException e) {
            markEditPendingManual(editId, "系统繁忙，已转人工审核");
        }
        return Result.ok(edit.getId());
    }

    @GetMapping("/content/edits")
    public Result<List<UniContentEdit>> pendingEdits(
            @RequestParam(defaultValue = "0") int status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<UniContentEdit> wrapper = new LambdaQueryWrapper<UniContentEdit>()
                .orderByDesc(UniContentEdit::getCreatedAt);
        if (status == 0) {
            wrapper.eq(UniContentEdit::getStatus, 3);
        } else {
            wrapper.eq(UniContentEdit::getStatus, status);
        }
        Page<UniContentEdit> p = editMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(p.getRecords());
    }

    @GetMapping("/content/my-edits")
    public Result<List<UniContentEdit>> myEdits(
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        LambdaQueryWrapper<UniContentEdit> wrapper = new LambdaQueryWrapper<UniContentEdit>()
                .eq(UniContentEdit::getSchoolId, currentAdmin.getSchoolId())
                .orderByDesc(UniContentEdit::getCreatedAt);
        if (status != null) {
            wrapper.eq(UniContentEdit::getStatus, status);
        }
        return Result.ok(editMapper.selectList(wrapper));
    }

    @PostMapping("/content/review")
    public Result<Void> reviewEdit(@RequestBody ReviewRequest req, HttpServletRequest request) {
        if (req.getId() == null) throw new BizException("审核ID不能为空");
        if (req.getApproved() == null) throw new BizException("审核动作不能为空");
        UniContentEdit edit = editMapper.selectById(req.getId());
        if (edit == null) throw new BizException("记录不存在");
        if (edit.getStatus() == null || edit.getStatus() != 3) {
            throw new BizException("仅待人工审核内容可进行二审");
        }
        String reviewNote = Boolean.TRUE.equals(req.getApproved()) ? "" : requireReviewNote(req.getReason());
        Long reviewerId = getAuthUserId(request);
        editMapper.update(null, new LambdaUpdateWrapper<UniContentEdit>()
            .eq(UniContentEdit::getId, req.getId())
            .eq(UniContentEdit::getStatus, 3)
            .set(UniContentEdit::getStatus, req.getApproved() ? 1 : 2)
            .set(UniContentEdit::getReviewNote, reviewNote)
            .set(UniContentEdit::getReviewActorRole, "admin")
            .set(UniContentEdit::getReviewActorId, reviewerId)
            .set(UniContentEdit::getReviewedAt, LocalDateTime.now()));

        if (Boolean.TRUE.equals(req.getApproved())) {
            applyEditToUniversity(req.getId());
        }
        return Result.ok(null);
    }

    @PostMapping("/content/review/batch")
    public Result<Map<String, Object>> reviewEditBatch(@RequestBody BatchReviewRequest req, HttpServletRequest request) {
        if (req.getIds() == null || req.getIds().isEmpty()) throw new BizException("请选择至少一条编辑记录");
        if (req.getApproved() == null) throw new BizException("审核动作不能为空");
        String reviewNote = Boolean.TRUE.equals(req.getApproved()) ? "" : requireReviewNote(req.getReason());
        Long reviewerId = getAuthUserId(request);
        List<UniContentEdit> records = editMapper.selectBatchIds(req.getIds());
        Map<Long, UniContentEdit> recordMap = records.stream().collect(java.util.stream.Collectors.toMap(UniContentEdit::getId, item -> item));
        List<String> invalidItems = new java.util.ArrayList<>();
        for (Long id : req.getIds()) {
            UniContentEdit record = recordMap.get(id);
            if (record == null) {
                invalidItems.add(id + " (不存在)");
                continue;
            }
            if (record.getStatus() == null || record.getStatus() != 3) {
                invalidItems.add(id + " (非待人工审核)");
            }
        }
        if (!invalidItems.isEmpty()) {
            throw new BizException("以下编辑记录不可审核：" + String.join("；", invalidItems));
        }
        for (UniContentEdit record : records) {
            record.setStatus(Boolean.TRUE.equals(req.getApproved()) ? 1 : 2);
            record.setReviewNote(reviewNote);
            record.setReviewActorRole("admin");
            record.setReviewActorId(reviewerId);
            record.setReviewedAt(LocalDateTime.now());
            editMapper.updateById(record);
            if (Boolean.TRUE.equals(req.getApproved())) {
                applyEditToUniversity(record.getId());
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("processedCount", records.size());
        result.put("processedIds", records.stream().map(UniContentEdit::getId).toList());
        return Result.ok(result);
    }

    private void applyEditToUniversity(Long editId) {
        UniContentEdit edit = editMapper.selectById(editId);
        if (edit == null) return;

        Map<String, BiConsumer<LambdaUpdateWrapper<University>, String>> fieldMap = Map.of(
            "content", (w, v) -> w.set(University::getContent, v),
            "address", (w, v) -> w.set(University::getAddress, v),
            "phone", (w, v) -> w.set(University::getPhone, v),
            "email", (w, v) -> w.set(University::getEmail, v),
            "schoolSite", (w, v) -> w.set(University::getSchoolSite, v)
        );

        BiConsumer<LambdaUpdateWrapper<University>, String> setter = fieldMap.get(edit.getFieldName());
        if (setter != null) {
            LambdaUpdateWrapper<University> wrapper = new LambdaUpdateWrapper<University>()
                .eq(University::getSchoolId, edit.getSchoolId());
            setter.accept(wrapper, edit.getNewValue());
            universityMapper.update(null, wrapper);
            log.info("Applied edit {} to university {}: {} = {}", editId, edit.getSchoolId(), edit.getFieldName(), edit.getNewValue());
        }
    }

    private void verifyAlumniOwnership(Long adminId, String schoolId) {
        if (adminId == null || schoolId == null) return;
        AlumniAdmin admin = alumniMapper.selectById(adminId);
        if (admin == null) throw new BizException("管理员不存在");
        if (admin.getRole() >= 9) return;
        if (!schoolId.equals(admin.getSchoolId())) {
            throw new BizException("只能操作自己学校的内容");
        }
    }

    // ── DTOs ──

    @Data
    public static class ApplyRequest {
        private String schoolId;
        private String nickname;
        private String phone;
        private String email;
        private String credentialUrl;
        private Short graduationYear;
        private String major;
        private String bio;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String phone;
        private String password;
    }

    @Data
    public static class ReviewRequest {
        private Long id;
        private Boolean approved;
        private String reason;
    }

    @Data
    public static class BatchReviewRequest {
        private List<Long> ids;
        private Boolean approved;
        private String reason;
    }

    @Data
    public static class QaReviewRequest {
        private Long id;
        private Integer status;
        private String reason;
    }

    @Data
    public static class QaUpdateNoteRequest {
        private Long id;
        private String reviewNote;
    }

    @Data
    public static class QaEditReplyRequest {
        private Long id;
        private String content;
    }

    @Data
    public static class SaveMediaRequest {
        private String schoolId;
        private Long uploaderId;
        private Integer mediaType;
        private String url;
        private String thumbUrl;
        private String caption;
        private Integer sortOrder;
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }

    // ── 问答管理（校友管理员） ──

    @PostMapping("/qa/reply")
    public Result<String> qaReply(@RequestBody Map<String, String> req, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        Long questionId = Long.parseLong(req.get("questionId"));
        String content = req.get("content");
        String authorName = req.get("authorName");
        if (content == null || content.trim().length() < 2) return Result.fail("回答至少2个字");
        qaService.alumniReply(questionId, content, authorName, currentAdmin.getSchoolId());
        return Result.ok("回答已提交，AI一审后将交给系统管理员二审");
    }

    @GetMapping("/qa/pending")
    public Result<Map<String, Object>> qaPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        var p = qaService.listPendingBySchool(currentAdmin.getSchoolId(), page, size);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        return Result.ok(result);
    }

    @GetMapping("/qa/history")
    public Result<List<com.gzly.entity.UniversityQa>> qaHistory(HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        return Result.ok(qaService.listAlumniRepliesBySchool(currentAdmin.getSchoolId()));
    }

    @PostMapping("/qa/review")
    public Result<String> qaReview(@RequestBody QaReviewRequest req, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        if (req.getId() == null) throw new BizException("审核ID不能为空");
        if (req.getStatus() == null) throw new BizException("审核状态不能为空");
        qaService.alumniReview(req.getId(), req.getStatus(), req.getReason(), currentAdmin.getId(), currentAdmin.getSchoolId());
        return Result.ok(req.getStatus() == 1 ? "已发布" : "已退回");
    }

    @PostMapping("/qa/review/update-note")
    public Result<Void> qaReviewUpdateNote(@RequestBody QaUpdateNoteRequest req, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        if (req.getId() == null) throw new BizException("问答ID不能为空");
        qaService.updateAlumniReviewNote(req.getId(), req.getReviewNote(), currentAdmin.getSchoolId(), currentAdmin.getId());
        return Result.ok();
    }

    @PostMapping("/qa/reply/edit-and-resubmit")
    public Result<Long> qaReplyEditAndResubmit(@RequestBody QaEditReplyRequest req, HttpServletRequest request) {
        AlumniAdmin currentAdmin = requireCurrentAlumni(request);
        if (req.getId() == null) throw new BizException("回复ID不能为空");
        if (req.getContent() == null || req.getContent().trim().length() < 2) throw new BizException("回答内容至少2个字");
        UniversityQa updated = qaService.editReplyAndResubmit(req.getId(), req.getContent(), currentAdmin.getId(), currentAdmin.getSchoolId());
        return Result.ok(updated.getId());
    }

    private void aiReviewMediaAsync(Long mediaId, SaveMediaRequest req) {
        try {
            String result;
            Integer mediaType = req.getMediaType();
            if (mediaType != null && (mediaType == 1 || mediaType == 4) && req.getUrl() != null) {
                String imageUrl = req.getUrl().startsWith("http")
                        ? req.getUrl()
                        : "https://gzly.dongsiwei.com" + req.getUrl();
                result = qaService.callAiImageReview(imageUrl);
                if (result != null && result.contains("\"error\"")) {
                    log.warn("AI image review fallback to text review for media#{}, result={}", mediaId, result);
                    result = qaService.callAiContentReview(
                            "以下是图片上传的元信息，请基于图片说明和链接做保守审核：\n" + buildMediaReviewText(req),
                            "fallback-text",
                            "未发现明显违规视觉内容，建议人工复核后发布",
                            "命中平台内容安全策略，建议人工复核后决定是否退回");
                }
            } else {
                result = qaService.callAiContentReview(buildMediaReviewText(req));
            }

            var decision = qaService.parseAiReviewDecision(result);
            UniMedia update = new UniMedia();
            update.setId(mediaId);
            update.setAiReviewResult(result);
            if ("auto_approved".equals(decision.getDecision())) {
                update.setStatus(1);
                update.setReviewNote("");
                update.setReviewActorRole("ai");
                update.setReviewActorId(0L);
                update.setReviewedAt(LocalDateTime.now());
            } else if ("auto_rejected".equals(decision.getDecision())) {
                update.setStatus(2);
                update.setReviewNote(decision.getReason());
                update.setReviewActorRole("ai");
                update.setReviewActorId(0L);
                update.setReviewedAt(LocalDateTime.now());
            } else {
                update.setStatus(3);
                update.setReviewNote("");
                update.setReviewActorRole(null);
                update.setReviewActorId(null);
                update.setReviewedAt(null);
            }
            mediaMapper.updateById(update);
            log.info("AI media review media#{} result={}", mediaId, result);
        } catch (Exception e) {
            log.warn("AI media review failed for media#{}", mediaId, e);
            UniMedia update = new UniMedia();
            update.setId(mediaId);
            update.setAiReviewResult(qaService.buildAiErrorResult(e.getMessage()));
            update.setStatus(3);
            update.setReviewNote("");
            update.setReviewActorRole(null);
            update.setReviewActorId(null);
            update.setReviewedAt(null);
            mediaMapper.updateById(update);
        }
    }

    private void aiReviewContentEditAsync(Long editId) {
        try {
            UniContentEdit edit = editMapper.selectById(editId);
            if (edit == null) return;
            String content = "字段：" + edit.getFieldName() + "\n内容：" + edit.getNewValue();
            String result = qaService.callAiContentReview(content);
            var decision = qaService.parseAiReviewDecision(result);
            UniContentEdit update = new UniContentEdit();
            update.setId(editId);
            update.setAiReviewResult(result);
            if ("auto_approved".equals(decision.getDecision())) {
                update.setStatus(1);
                update.setReviewNote("");
                update.setReviewActorRole("ai");
                update.setReviewActorId(0L);
                update.setReviewedAt(LocalDateTime.now());
            } else if ("auto_rejected".equals(decision.getDecision())) {
                update.setStatus(2);
                update.setReviewNote(decision.getReason());
                update.setReviewActorRole("ai");
                update.setReviewActorId(0L);
                update.setReviewedAt(LocalDateTime.now());
            } else {
                update.setStatus(3);
                update.setReviewNote("");
                update.setReviewActorRole(null);
                update.setReviewActorId(null);
                update.setReviewedAt(null);
            }
            editMapper.updateById(update);
            if ("auto_approved".equals(decision.getDecision())) {
                applyEditToUniversity(editId);
            }
            log.info("AI content edit review edit#{} result={}", editId, result);
        } catch (Exception e) {
            log.warn("AI content edit review failed for edit#{}", editId, e);
            UniContentEdit update = new UniContentEdit();
            update.setId(editId);
            update.setAiReviewResult(qaService.buildAiErrorResult(e.getMessage()));
            update.setStatus(3);
            update.setReviewNote("");
            update.setReviewActorRole(null);
            update.setReviewActorId(null);
            update.setReviewedAt(null);
            editMapper.updateById(update);
        }
    }

    private void markMediaPendingManual(Long mediaId, String reason) {
        UniMedia update = new UniMedia();
        update.setId(mediaId);
        update.setAiReviewResult(qaService.buildAiErrorResult(reason));
        update.setStatus(3);
        update.setReviewNote("");
        update.setReviewActorRole(null);
        update.setReviewActorId(null);
        update.setReviewedAt(null);
        mediaMapper.updateById(update);
    }

    private void markEditPendingManual(Long editId, String reason) {
        UniContentEdit update = new UniContentEdit();
        update.setId(editId);
        update.setAiReviewResult(qaService.buildAiErrorResult(reason));
        update.setStatus(3);
        update.setReviewNote("");
        update.setReviewActorRole(null);
        update.setReviewActorId(null);
        update.setReviewedAt(null);
        editMapper.updateById(update);
    }

    private String buildMediaReviewText(SaveMediaRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("schoolId=").append(req.getSchoolId()).append('\n');
        sb.append("mediaType=").append(req.getMediaType()).append('\n');
        sb.append("url=").append(req.getUrl() == null ? "" : req.getUrl()).append('\n');
        if (req.getThumbUrl() != null && !req.getThumbUrl().isBlank()) {
            sb.append("thumbUrl=").append(req.getThumbUrl()).append('\n');
        }
        if (req.getCaption() != null && !req.getCaption().isBlank()) {
            sb.append("caption=").append(req.getCaption()).append('\n');
        }
        return sb.toString();
    }

    private String requireReviewNote(String reviewNote) {
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BizException("退回时必须填写原因");
        }
        return reviewNote.trim();
    }

    private AlumniAdmin requireCurrentAlumni(HttpServletRequest request) {
        Long userId = getAuthUserId(request);
        if (userId == null || userId <= 0) {
            throw new BizException("未登录或身份异常");
        }
        AlumniAdmin admin = alumniMapper.selectById(userId);
        if (admin == null) {
            throw new BizException("管理员不存在");
        }
        return admin;
    }

    private Long getAuthUserId(HttpServletRequest request) {
        Object value = request.getAttribute("authUserId");
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String sanitizeMediaUrl(String value, Integer mediaType) {
        if (mediaType != null && mediaType == 2 && (value == null || value.isBlank())) {
            return "";
        }
        if (mediaType != null && (mediaType == 1 || mediaType == 3 || mediaType == 4)) {
            return SafeUrlUtil.requirePublicUrl(value, "资源地址");
        }
        return sanitizeOptionalUrl(value);
    }

    private String sanitizeOptionalUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SafeUrlUtil.sanitizePublicUrl(value);
        if (sanitized.isEmpty()) {
            throw new BizException("链接格式不安全");
        }
        return sanitized;
    }
}
