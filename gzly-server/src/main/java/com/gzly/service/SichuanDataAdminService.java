package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.DataAdmissionGroupLine;
import com.gzly.entity.DataAdmissionGroupPlan;
import com.gzly.entity.DataScoreRank;
import com.gzly.entity.DataSourceRegistry;
import com.gzly.entity.University;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataAdmissionGroupPlanMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.DataSourceRegistryMapper;
import com.gzly.mapper.UniversityMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SichuanDataAdminService {

    private static final String PROVINCE_CODE = ProvincePolicyService.SC;
    private static final String TARGET_BATCH = "普通本科批B段";
    private static final String SOURCE_LEVEL_SCHOOL_VERIFIED = "school_verified";
    private static final String RANK_SOURCE_ORIGINAL = "original";
    private static final String RANK_SOURCE_CONVERTED = "score_rank_converted";
    private static final int DEFAULT_YEAR = 2025;
    private static final int TARGET_GROUP_COUNT = 45;
    private static final Set<String> SUBJECT_TYPES = Set.of("物理类", "历史类");
    private static final Set<String> BLOCKED_SUPPLEMENT_HOST_HINTS = Set.of(
            "eol.cn", "gaokao.cn", "youzy.cn", "dxsbb.com", "zhiyuan", "sczjw.com.cn", "scedu.net"
    );
    private static final Map<String, ProvinceDataContext> PROVINCE_CONTEXTS = Map.of(
            ProvincePolicyService.SC, new ProvinceDataContext(
                    ProvincePolicyService.SC, "四川", "四川省教育考试院", "普通本科批B段", "本科批",
                    Set.of("sceea.cn"),
                    "四川2025官方一分一段为图片发布，需完成OCR草稿与人工复核后导入物理类、历史类数据",
                    "四川省教育考试院公开页为普通本科批B段投档汇总新闻，未发现完整院校专业组调档线明细；不写生产表",
                    "四川省教育考试院公开页目前为招生计划更正通知，未发现普通本科批B段全量结构化招生计划；不写生产表"
            ),
            ProvincePolicyService.HB, new ProvinceDataContext(
                    ProvincePolicyService.HB, "湖北", "湖北省教育考试院", "本科普通批", "本科批",
                    Set.of("hbccks.cn", "hubei.gov.cn", "jyt.hubei.gov.cn"),
                    "湖北2025官方一分一段需完成OCR草稿与人工复核后导入物理类、历史类数据",
                    "湖北本科普通批院校专业组调档线需省级官方或高校官网来源人工复核后导入",
                    "湖北本科普通批院校专业组招生计划需省级官方或高校官网来源人工复核后导入"
            ),
            ProvincePolicyService.AH, new ProvinceDataContext(
                    ProvincePolicyService.AH, "安徽", "安徽省教育招生考试院", "普通本科批次", "本科批",
                    Set.of("ahzsks.cn", "anhuinews.com", "chsi.com.cn", "eol.cn"),
                    "安徽2025官方一分一段需完成OCR草稿与人工复核后导入物理类、历史类数据",
                    "安徽普通本科批次院校专业组投档线需省级官方或高校官网来源人工复核后导入",
                    "安徽普通本科批次院校专业组招生计划需省级官方或高校官网来源人工复核后导入"
            )
    );
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)(?:src|href)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern OCR_RANK_PATTERN = Pattern.compile("(\\d{2,3})\\D+(\\d+)\\D+(\\d+)");

    private final DataSourceRegistryMapper sourceRegistryMapper;
    private final DataScoreRankMapper dataScoreRankMapper;
    private final DataAdmissionGroupLineMapper groupLineMapper;
    private final DataAdmissionGroupPlanMapper groupPlanMapper;
    private final UniversityMapper universityMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Transactional(readOnly = true)
    public StatusResponse status(Integer requestedYear) {
        return status(PROVINCE_CODE, requestedYear);
    }

    @Transactional(readOnly = true)
    public StatusResponse status(String provinceCode, Integer requestedYear) {
        ProvinceDataContext context = context(provinceCode);
        int year = normalizeYear(requestedYear);
        StatusResponse response = new StatusResponse();
        response.setProvinceCode(context.provinceCode());
        response.setProvinceName(context.provinceName());
        response.setYear(year);

        Map<String, SubjectStatus> subjects = new LinkedHashMap<>();
        boolean allReady = true;
        for (String subjectType : List.of("物理类", "历史类")) {
            SubjectStatus subject = buildSubjectStatus(context, year, subjectType);
            subjects.put(subjectType, subject);
            allReady = allReady && subject.isGenerationReady();
        }
        response.setSubjects(subjects);
        response.setGenerationReady(allReady);
        List<DataSourceRegistry> sources = loadSourceRegistries(context, year);
        response.setSourceStatusCounts(countSourceStatuses(sources));
        response.setSourceCompleteness(buildSourceCompleteness(context, year, subjects, sources));
        response.setBlockingReasons(buildBlockingReasons(response.getSourceCompleteness()));
        response.setOfficialSourceName(context.sourceName());
        response.setTargetBatch(context.targetBatch());
        response.setTargetGroupCount(TARGET_GROUP_COUNT);
        return response;
    }

    public SourceRefreshResult refreshSources(SourceRefreshRequest request) {
        return refreshSources(PROVINCE_CODE, request);
    }

    public SourceRefreshResult refreshSources(String provinceCode, SourceRefreshRequest request) {
        ProvinceDataContext context = context(provinceCode);
        int year = normalizeYear(request == null ? null : request.getYear());
        boolean fetchChildren = request == null || request.getFetchChildren() == null || request.getFetchChildren();
        SourceRefreshResult result = new SourceRefreshResult();
        result.setProvinceCode(context.provinceCode());
        result.setProvinceName(context.provinceName());
        result.setYear(year);

        List<OfficialSource> officialSources = officialSources(context, year);
        List<DataSourceRegistry> registries = new ArrayList<>();
        for (OfficialSource source : officialSources) {
            try {
                registries.add(fetchOfficialSource(context, source, fetchChildren));
            } catch (Exception e) {
                log.warn("{}官方来源刷新失败: pageUrl={}", context.provinceName(), source.sourcePageUrl(), e);
                DataSourceRegistry failed = buildRegistry(context, source, "", "", "failed",
                        "官方来源抓取失败: " + e.getMessage(), LocalDateTime.now());
                registries.add(failed);
                result.getErrors().add(source.sourcePageUrl() + ": " + e.getMessage());
            }
        }

        for (DataSourceRegistry registry : registries) {
            upsertSourceRegistry(registry);
            result.setRegistered(result.getRegistered() + 1);
            if ("failed".equals(registry.getStatus())) {
                result.setFailed(result.getFailed() + 1);
            }
        }
        result.setSources(registries.stream().map(SourceView::from).toList());
        return result;
    }

    @Transactional
    public ImportResult importScoreRanks(ScoreRankImportRequest request, boolean dryRun) {
        return importScoreRanks(PROVINCE_CODE, request, dryRun);
    }

    @Transactional
    public ImportResult importScoreRanks(String provinceCode, ScoreRankImportRequest request, boolean dryRun) {
        ProvinceDataContext context = context(provinceCode);
        validateScoreRankRequest(context, request);
        List<ScoreRankRow> rows = collectScoreRankRows(request);
        ImportResult result = baseImportResult(context, dryRun, rows.size());
        List<DataScoreRank> entities = new ArrayList<>();
        Set<Integer> seenScores = new HashSet<>();
        int previousCumulative = 0;
        Integer previousScore = null;

        rows.sort(Comparator.comparing(ScoreRankRow::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        for (int i = 0; i < rows.size(); i++) {
            ScoreRankRow row = rows.get(i);
            int lineNo = i + 1;
            List<String> errors = validateScoreRankRow(row, previousScore, previousCumulative, seenScores);
            if (!errors.isEmpty()) {
                reject(result, lineNo, errors);
                continue;
            }

            int segment = row.getSegmentCount() == null
                    ? row.getCumulativeCount() - previousCumulative
                    : row.getSegmentCount();
            int rankLow = Math.max(1, row.getCumulativeCount() - segment + 1);
            DataScoreRank entity = new DataScoreRank();
            entity.setProvinceCode(context.provinceCode());
            entity.setProvinceName(context.provinceName());
            entity.setYear(request.getYear());
            entity.setSubjectType(request.getSubjectType());
            entity.setScore(row.getScore());
            entity.setScoreLabel(StringUtils.hasText(row.getScoreLabel()) ? row.getScoreLabel().trim() : String.valueOf(row.getScore()));
            entity.setSegmentCount(segment);
            entity.setCumulativeCount(row.getCumulativeCount());
            entity.setCumulativeRate(row.getCumulativeRate());
            entity.setRankLow(rankLow);
            entity.setRankHigh(row.getCumulativeCount());
            entity.setSourceName(context.sourceName());
            entity.setSourceUrl(trimToEmpty(request.getSourceUrl()));
            entity.setSourcePageUrl(trimToEmpty(request.getSourcePageUrl()));
            entity.setSourceFile(trimToEmpty(request.getSourceFile()));
            entity.setParseMethod(defaultText(request.getParseMethod(), "manual_verified"));
            entities.add(entity);
            seenScores.add(row.getScore());
            previousScore = row.getScore();
            previousCumulative = row.getCumulativeCount();
        }

        return writeScoreRanks(context, request, dryRun, result, entities);
    }

    @Transactional
    public ImportResult importGroupLines(GroupLineImportRequest request, boolean dryRun) {
        return importGroupLines(PROVINCE_CODE, request, dryRun);
    }

    @Transactional
    public ImportResult importGroupLines(String provinceCode, GroupLineImportRequest request, boolean dryRun) {
        ProvinceDataContext context = context(provinceCode);
        validateGroupLineRequest(context, request);
        List<GroupLineRow> rows = collectGroupLineRows(request);
        ImportResult result = baseImportResult(context, dryRun, rows.size());
        List<DataAdmissionGroupLine> entities = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            GroupLineRow row = rows.get(i);
            int lineNo = i + 1;
            List<String> errors = validateGroupLineRow(row, seenKeys);
            if (row != null) {
                applyGroupLineRankSource(context, request.getYear(), row, errors);
            }
            University university = resolveUniversity(row.getSchoolId(), row.getUniversityName());
            if (university == null) {
                errors.add("院校未匹配");
                addUnresolvedSchool(result, row.getSchoolId(), row.getUniversityName());
            }
            if (!errors.isEmpty()) {
                reject(result, lineNo, errors);
                continue;
            }
            entities.add(toGroupLineEntity(context, request, row, university));
        }

        if (result.getRejected() > 0 && !dryRun) {
            result.setMessage("存在未通过复核的院校专业组线，未写入生产表");
            return result;
        }
        for (DataAdmissionGroupLine entity : entities) {
            DataAdmissionGroupLine existing = findGroupLine(context, entity.getYear(), entity.getSchoolId(),
                    entity.getGroupCode(), entity.getSubjectType(), entity.getBatch());
            if (dryRun) {
                countDryRunWrite(result, existing);
                continue;
            }
            if (existing == null) {
                groupLineMapper.insert(entity);
                result.setInserted(result.getInserted() + 1);
            } else {
                mergeGroupLine(existing, entity, request.isPreserveNonEmpty());
                groupLineMapper.updateById(existing);
                result.setUpdated(result.getUpdated() + 1);
            }
        }
        result.setValidRows(entities.size());
        if (!dryRun && !entities.isEmpty()) {
            upsertImportRegistry(context, request.getYear(), "group_line", "", request.getSourcePageUrl(), request.getSourceUrl(),
                    request.getSourceLevel(), request.getSourceHash(), request.getParseMethod(), entities.size());
        }
        return result;
    }

    @Transactional
    public ImportResult importGroupPlans(GroupPlanImportRequest request, boolean dryRun) {
        return importGroupPlans(PROVINCE_CODE, request, dryRun);
    }

    @Transactional
    public ImportResult importGroupPlans(String provinceCode, GroupPlanImportRequest request, boolean dryRun) {
        ProvinceDataContext context = context(provinceCode);
        validateGroupPlanRequest(context, request);
        List<GroupPlanRow> rows = collectGroupPlanRows(request);
        ImportResult result = baseImportResult(context, dryRun, rows.size());
        List<DataAdmissionGroupPlan> entities = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            GroupPlanRow row = rows.get(i);
            int lineNo = i + 1;
            List<String> errors = validateGroupPlanRow(row, seenKeys);
            University university = resolveUniversity(row.getSchoolId(), row.getUniversityName());
            if (university == null) {
                errors.add("院校未匹配");
                addUnresolvedSchool(result, row.getSchoolId(), row.getUniversityName());
            }
            DataAdmissionGroupLine groupLine = null;
            if (university != null && StringUtils.hasText(row.getGroupCode()) && StringUtils.hasText(row.getSubjectType())) {
                groupLine = findGroupLine(context, request.getYear(), university.getSchoolId(), row.getGroupCode().trim(),
                        row.getSubjectType().trim(), context.targetBatch());
            }
            if (groupLine == null) {
                errors.add("未找到对应院校专业组线");
            }
            if (!errors.isEmpty()) {
                reject(result, lineNo, errors);
                continue;
            }
            entities.add(toGroupPlanEntity(context, request, row, university, groupLine));
        }

        if (result.getRejected() > 0 && !dryRun) {
            result.setMessage("存在未通过复核的招生计划，未写入生产表");
            return result;
        }
        for (DataAdmissionGroupPlan entity : entities) {
            DataAdmissionGroupPlan existing = findGroupPlan(context, entity.getYear(), entity.getSchoolId(),
                    entity.getGroupCode(), entity.getSubjectType(), entity.getMajorCode());
            if (dryRun) {
                countDryRunWrite(result, existing);
                continue;
            }
            if (existing == null) {
                groupPlanMapper.insert(entity);
                result.setInserted(result.getInserted() + 1);
            } else {
                mergeGroupPlan(existing, entity, request.isPreserveNonEmpty());
                groupPlanMapper.updateById(existing);
                result.setUpdated(result.getUpdated() + 1);
            }
        }
        result.setValidRows(entities.size());
        if (!dryRun && !entities.isEmpty()) {
            upsertImportRegistry(context, request.getYear(), "group_plan", "", request.getSourcePageUrl(), request.getSourceUrl(),
                    request.getSourceLevel(), request.getSourceHash(), request.getParseMethod(), entities.size());
        }
        return result;
    }

    private ImportResult writeScoreRanks(ProvinceDataContext context, ScoreRankImportRequest request, boolean dryRun,
                                         ImportResult result, List<DataScoreRank> entities) {
        if (result.getRejected() > 0 && !dryRun) {
            result.setMessage("存在未通过复核的一分一段行，未写入生产表");
            return result;
        }
        for (DataScoreRank entity : entities) {
            DataScoreRank existing = findScoreRank(context, entity.getYear(), entity.getSubjectType(), entity.getScore());
            if (dryRun) {
                countDryRunWrite(result, existing);
                continue;
            }
            if (existing == null) {
                dataScoreRankMapper.insert(entity);
                result.setInserted(result.getInserted() + 1);
            } else {
                mergeScoreRank(existing, entity, request.isPreserveNonEmpty());
                dataScoreRankMapper.updateById(existing);
                result.setUpdated(result.getUpdated() + 1);
            }
        }
        result.setValidRows(entities.size());
        if (!dryRun && !entities.isEmpty()) {
            upsertImportRegistry(context, request.getYear(), "score_rank", request.getSubjectType(), request.getSourcePageUrl(), request.getSourceUrl(),
                    "manual_verified", request.getSourceHash(), request.getParseMethod(), entities.size());
        }
        return result;
    }

    private SubjectStatus buildSubjectStatus(ProvinceDataContext context, int year, String subjectType) {
        SubjectStatus status = new SubjectStatus();
        status.setSubjectType(subjectType);
        status.setScoreRankRows(safeLong(dataScoreRankMapper.selectCount(new LambdaQueryWrapper<DataScoreRank>()
                .eq(DataScoreRank::getProvinceCode, context.provinceCode())
                .eq(DataScoreRank::getYear, year)
                .eq(DataScoreRank::getSubjectType, subjectType))));
        status.setGroupLineGroups(safeLong(groupLineMapper.countDistinctGroups(context.provinceCode(), year, subjectType, context.batchKeyword())));
        status.setGroupPlanGroups(safeLong(groupPlanMapper.countDistinctGroups(context.provinceCode(), year, subjectType, context.batchKeyword())));
        status.setGroupLineOriginalRankGroups(safeLong(groupLineMapper.countDistinctGroupsByRankSource(
                context.provinceCode(), year, subjectType, context.batchKeyword(), RANK_SOURCE_ORIGINAL)));
        status.setGroupLineConvertedRankGroups(safeLong(groupLineMapper.countDistinctGroupsByRankSource(
                context.provinceCode(), year, subjectType, context.batchKeyword(), RANK_SOURCE_CONVERTED)));
        status.setGroupLineSchoolVerifiedGroups(safeLong(groupLineMapper.countDistinctGroupsBySourceLevel(
                context.provinceCode(), year, subjectType, context.batchKeyword(), SOURCE_LEVEL_SCHOOL_VERIFIED)));
        status.setGroupPlanSchoolVerifiedGroups(safeLong(groupPlanMapper.countDistinctGroupsBySourceLevel(
                context.provinceCode(), year, subjectType, context.batchKeyword(), SOURCE_LEVEL_SCHOOL_VERIFIED)));
        status.setGroupLineRemainingGroups(Math.max(0, TARGET_GROUP_COUNT - status.getGroupLineGroups()));
        status.setGroupPlanRemainingGroups(Math.max(0, TARGET_GROUP_COUNT - status.getGroupPlanGroups()));
        boolean ready = status.getScoreRankRows() > 0
                && status.getGroupLineGroups() >= TARGET_GROUP_COUNT
                && status.getGroupPlanGroups() >= TARGET_GROUP_COUNT;
        status.setGenerationReady(ready);
        status.setLockReason(ready ? "" : context.provinceName() + "2025官方一分一段、" + context.targetBatch() + "院校专业组线或招生计划尚未完整核验");
        return status;
    }

    private List<DataSourceRegistry> loadSourceRegistries(ProvinceDataContext context, int year) {
        List<DataSourceRegistry> sources = sourceRegistryMapper.selectList(new LambdaQueryWrapper<DataSourceRegistry>()
                .eq(DataSourceRegistry::getProvinceCode, context.provinceCode())
                .eq(DataSourceRegistry::getYear, year));
        return sources == null ? List.of() : sources;
    }

    private Map<String, Long> countSourceStatuses(List<DataSourceRegistry> sources) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DataSourceRegistry source : sources) {
            String key = defaultText(source.getStatus(), "unknown");
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }
        return counts;
    }

    private Map<String, SourceCompleteness> buildSourceCompleteness(ProvinceDataContext context,
                                                                    int year,
                                                                    Map<String, SubjectStatus> subjects,
                                                                    List<DataSourceRegistry> sources) {
        Map<String, SourceCompleteness> completeness = new LinkedHashMap<>();
        long expectedSubjectMinimum = (long) TARGET_GROUP_COUNT * SUBJECT_TYPES.size();
        boolean scoreRankReady = subjects.values().stream().allMatch(subject -> subject.getScoreRankRows() > 0);
        boolean groupLineReady = subjects.values().stream().allMatch(subject -> subject.getGroupLineGroups() >= TARGET_GROUP_COUNT);
        boolean groupPlanReady = subjects.values().stream().allMatch(subject -> subject.getGroupPlanGroups() >= TARGET_GROUP_COUNT);

        completeness.put("score_rank", sourceCompleteness(
                "score_rank",
                "官方一分一段",
                subjects.values().stream().mapToLong(SubjectStatus::getScoreRankRows).sum(),
                subjects.values().stream().filter(subject -> subject.getScoreRankRows() > 0).count(),
                SUBJECT_TYPES.size(),
                hasRegisteredSource(sources, "score_rank"),
                scoreRankReady,
                context.scoreRankBlockingReason()));
        completeness.put("group_line", sourceCompleteness(
                "group_line",
                context.targetBatch() + "院校专业组调档线",
                safeLong(groupLineMapper.countRowsByBatch(context.provinceCode(), year, context.batchKeyword())),
                subjects.values().stream().mapToLong(SubjectStatus::getGroupLineGroups).sum(),
                expectedSubjectMinimum,
                hasRegisteredSource(sources, "group_line"),
                groupLineReady,
                context.groupLineBlockingReason()));
        completeness.put("group_plan", sourceCompleteness(
                "group_plan",
                context.targetBatch() + "院校专业组招生计划",
                safeLong(groupPlanMapper.countRowsByBatch(context.provinceCode(), year, context.batchKeyword())),
                subjects.values().stream().mapToLong(SubjectStatus::getGroupPlanGroups).sum(),
                expectedSubjectMinimum,
                hasRegisteredSource(sources, "group_plan"),
                groupPlanReady,
                context.groupPlanBlockingReason()));
        return completeness;
    }

    private SourceCompleteness sourceCompleteness(String dataType, String label, long importedRows, long importedGroups,
                                                  long expectedMinimum, boolean officialSourceRegistered,
                                                  boolean ready, String blockingReason) {
        SourceCompleteness completeness = new SourceCompleteness();
        completeness.setDataType(dataType);
        completeness.setLabel(label);
        completeness.setImportedRows(importedRows);
        completeness.setImportedGroups(importedGroups);
        completeness.setExpectedMinimum(expectedMinimum);
        completeness.setRemainingGroups(Math.max(0, expectedMinimum - importedGroups));
        completeness.setRemainingRows(completeness.getRemainingGroups());
        completeness.setOfficialSourceRegistered(officialSourceRegistered);
        completeness.setReady(ready);
        completeness.setSourceCompleteness(ready
                ? "ready"
                : officialSourceRegistered ? "official_source_registered" : "missing_official_source");
        completeness.setBlockingReason(ready ? "" : blockingReason);
        return completeness;
    }

    private List<String> buildBlockingReasons(Map<String, SourceCompleteness> completenessMap) {
        return completenessMap.values().stream()
                .filter(source -> !source.isReady())
                .map(SourceCompleteness::getBlockingReason)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean hasRegisteredSource(List<DataSourceRegistry> sources, String dataType) {
        return sources.stream().anyMatch(source -> dataType.equals(source.getDataType())
                && !"failed".equals(defaultText(source.getStatus(), "")));
    }

    private DataSourceRegistry fetchOfficialSource(ProvinceDataContext context, OfficialSource source, boolean fetchChildren)
            throws IOException, InterruptedException {
        byte[] pageBytes = fetchBytes(context, source.sourcePageUrl());
        String pageText = new String(pageBytes, StandardCharsets.UTF_8);
        List<String> childUrls = extractOfficialResourceUrls(context, source.sourcePageUrl(), pageText);
        List<String> sourceUrls = childUrls.isEmpty() ? List.of(source.sourcePageUrl()) : childUrls;
        MessageDigest digest = sha256Digest();
        digest.update(pageBytes);
        if (fetchChildren) {
            for (String childUrl : childUrls) {
                try {
                    digest.update(fetchBytes(context, childUrl));
                } catch (IOException e) {
                    log.warn("{}官方来源子资源抓取失败: url={}", context.provinceName(), childUrl, e);
                }
            }
        }
        String sourceHash = hex(digest.digest());
        return buildRegistry(context, source, String.join("\n", sourceUrls), sourceHash,
                "manual_review", "官方来源已登记，结构化导入前需人工核验", LocalDateTime.now());
    }

    private byte[] fetchBytes(ProvinceDataContext context, String url) throws IOException, InterruptedException {
        if (!isOfficialUrl(context, url)) {
            throw new BizException("仅允许抓取" + context.sourceName() + "官方链接");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "GZLY-Data-Audit/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private List<String> extractOfficialResourceUrls(ProvinceDataContext context, String pageUrl, String html) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(html);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String resolved = resolveUrl(pageUrl, raw);
            if (!StringUtils.hasText(resolved) || !isOfficialUrl(context, resolved)) {
                continue;
            }
            String lower = resolved.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    || lower.endsWith(".pdf") || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                    || lower.endsWith(".doc") || lower.endsWith(".docx")) {
                urls.add(resolved);
            }
        }
        return urls.stream().distinct().toList();
    }

    private String resolveUrl(String pageUrl, String raw) {
        if (!StringUtils.hasText(raw) || raw.startsWith("javascript:") || raw.startsWith("#")) {
            return "";
        }
        try {
            return URI.create(pageUrl).resolve(raw.trim()).toString();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private DataSourceRegistry buildRegistry(ProvinceDataContext context, OfficialSource source, String sourceUrl, String sourceHash,
                                             String status, String notes, LocalDateTime checkedAt) {
        DataSourceRegistry registry = new DataSourceRegistry();
        registry.setProvinceCode(context.provinceCode());
        registry.setProvinceName(context.provinceName());
        registry.setYear(source.year());
        registry.setSubjectType(source.subjectType());
        registry.setDataType(source.dataType());
        registry.setBatch(source.batch());
        registry.setSourceName(context.sourceName());
        registry.setSourcePageUrl(source.sourcePageUrl());
        registry.setSourceUrl(sourceUrl);
        registry.setSourceLevel("official");
        registry.setSourceHash(sourceHash);
        registry.setParseMethod(source.parseMethod());
        registry.setStatus(status);
        registry.setRowCount(0);
        registry.setNotes(notes);
        registry.setLastCheckedAt(checkedAt);
        return registry;
    }

    private void upsertSourceRegistry(DataSourceRegistry next) {
        ProvinceDataContext context = context(next.getProvinceCode());
        DataSourceRegistry existing = findSourceRegistry(context, next.getYear(), next.getSubjectType(), next.getDataType(),
                next.getBatch(), next.getSourcePageUrl());
        if (existing == null) {
            sourceRegistryMapper.insert(next);
            return;
        }
        existing.setProvinceName(next.getProvinceName());
        existing.setSourceName(next.getSourceName());
        existing.setSourceUrl(next.getSourceUrl());
        existing.setSourceLevel(next.getSourceLevel());
        existing.setSourceHash(next.getSourceHash());
        existing.setParseMethod(next.getParseMethod());
        existing.setStatus(next.getStatus());
        existing.setRowCount(next.getRowCount());
        existing.setNotes(next.getNotes());
        existing.setLastCheckedAt(next.getLastCheckedAt());
        sourceRegistryMapper.updateById(existing);
    }

    private void upsertImportRegistry(ProvinceDataContext context, int year, String dataType, String subjectType, String sourcePageUrl, String sourceUrl,
                                      String sourceLevel, String sourceHash, String parseMethod, int rowCount) {
        String batch = "score_rank".equals(dataType) ? "" : context.targetBatch();
        OfficialSource source = new OfficialSource(year, subjectType, dataType, batch,
                sourcePageUrl, defaultText(parseMethod, "manual_verified"));
        DataSourceRegistry registry = buildRegistry(context, source, trimToEmpty(sourceUrl),
                defaultText(sourceHash, sha256(sourceUrl + "|" + dataType + "|" + rowCount)),
                "imported", "复核导入完成", LocalDateTime.now());
        registry.setSourceLevel(defaultText(sourceLevel, "manual_verified"));
        registry.setSourceName(sourceNameForLevel(context, registry.getSourceLevel()));
        registry.setRowCount(rowCount);
        upsertSourceRegistry(registry);
    }

    private DataSourceRegistry findSourceRegistry(ProvinceDataContext context, int year, String subjectType, String dataType,
                                                  String batch, String sourcePageUrl) {
        return sourceRegistryMapper.selectOne(new LambdaQueryWrapper<DataSourceRegistry>()
                .eq(DataSourceRegistry::getProvinceCode, context.provinceCode())
                .eq(DataSourceRegistry::getYear, year)
                .eq(DataSourceRegistry::getSubjectType, trimToEmpty(subjectType))
                .eq(DataSourceRegistry::getDataType, dataType)
                .eq(DataSourceRegistry::getBatch, trimToEmpty(batch))
                .eq(DataSourceRegistry::getSourcePageUrl, trimToEmpty(sourcePageUrl))
                .last("LIMIT 1"));
    }

    private void validateScoreRankRequest(ProvinceDataContext context, ScoreRankImportRequest request) {
        if (request == null) {
            throw new BizException("导入参数不能为空");
        }
        request.setYear(normalizeYear(request.getYear()));
        requireSubject(context, request.getSubjectType());
        requireOfficialSource(context, request.getSourcePageUrl(), request.getSourceUrl());
        request.setPreserveNonEmpty(defaultBoolean(request.getPreserveNonEmpty(), true));
    }

    private void validateGroupLineRequest(ProvinceDataContext context, GroupLineImportRequest request) {
        if (request == null) {
            throw new BizException("导入参数不能为空");
        }
        request.setYear(normalizeYear(request.getYear()));
        request.setPreserveNonEmpty(defaultBoolean(request.getPreserveNonEmpty(), true));
        request.setSourceLevel(defaultText(request.getSourceLevel(), "manual_verified"));
        requireAllowedReviewedSource(context, request.getSourcePageUrl(), request.getSourceUrl(), request.getSourceLevel());
    }

    private void validateGroupPlanRequest(ProvinceDataContext context, GroupPlanImportRequest request) {
        if (request == null) {
            throw new BizException("导入参数不能为空");
        }
        request.setYear(normalizeYear(request.getYear()));
        request.setPreserveNonEmpty(defaultBoolean(request.getPreserveNonEmpty(), true));
        request.setSourceLevel(defaultText(request.getSourceLevel(), "manual_verified"));
        requireAllowedReviewedSource(context, request.getSourcePageUrl(), request.getSourceUrl(), request.getSourceLevel());
    }

    private int normalizeYear(Integer year) {
        int value = year == null ? DEFAULT_YEAR : year;
        if (value != DEFAULT_YEAR) {
            throw new BizException("首版仅允许导入2025年普通类本科批次院校专业组数据");
        }
        return value;
    }

    private void requireSubject(ProvinceDataContext context, String subjectType) {
        if (!SUBJECT_TYPES.contains(trimToEmpty(subjectType))) {
            throw new BizException(context.provinceName() + "数据科类必须为物理类或历史类");
        }
    }

    private void requireOfficialSource(ProvinceDataContext context, String sourcePageUrl, String sourceUrl) {
        if (!isOfficialUrl(context, sourcePageUrl)) {
            throw new BizException("sourcePageUrl必须为" + context.sourceName() + "官方或授权来源链接");
        }
        for (String url : trimToEmpty(sourceUrl).split("\\R")) {
            if (StringUtils.hasText(url) && !isOfficialUrl(context, url.trim())) {
                throw new BizException("sourceUrl仅允许" + context.sourceName() + "官方或授权来源链接");
            }
        }
    }

    private void requireAllowedReviewedSource(ProvinceDataContext context, String sourcePageUrl, String sourceUrl, String sourceLevel) {
        if (SOURCE_LEVEL_SCHOOL_VERIFIED.equals(sourceLevel)) {
            requireAllowedSchoolSource(context, sourcePageUrl, "sourcePageUrl");
            for (String url : trimToEmpty(sourceUrl).split("\\R")) {
                if (StringUtils.hasText(url)) {
                    requireAllowedSchoolSource(context, url.trim(), "sourceUrl");
                }
            }
            return;
        }
        requireOfficialSource(context, sourcePageUrl, sourceUrl);
    }

    private void requireAllowedSchoolSource(ProvinceDataContext context, String url, String fieldName) {
        if (isOfficialUrl(context, url)) {
            return;
        }
        if (!StringUtils.hasText(url)) {
            throw new BizException(fieldName + "不能为空");
        }
        String host;
        try {
            host = hostOf(url);
        } catch (URISyntaxException e) {
            throw new BizException(fieldName + "必须为有效HTTP/HTTPS链接");
        }
        if (!StringUtils.hasText(host)) {
            throw new BizException(fieldName + "必须为有效HTTP/HTTPS链接");
        }
        for (String blockedHostHint : BLOCKED_SUPPLEMENT_HOST_HINTS) {
            if (host.contains(blockedHostHint)) {
                throw new BizException(fieldName + "不允许使用第三方聚合或门户来源");
            }
        }
    }

    private boolean isOfficialUrl(ProvinceDataContext context, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            String host = hostOf(value);
            return context.officialHosts().stream()
                    .anyMatch(officialHost -> host.equals(officialHost) || host.endsWith("." + officialHost));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String hostOf(String value) throws URISyntaxException {
        URI uri = new URI(value.trim());
        String scheme = defaultText(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return "";
        }
        return defaultText(uri.getHost(), "").toLowerCase(Locale.ROOT);
    }

    private List<String> validateScoreRankRow(ScoreRankRow row, Integer previousScore, int previousCumulative,
                                              Set<Integer> seenScores) {
        List<String> errors = new ArrayList<>();
        if (row == null) {
            errors.add("空行");
            return errors;
        }
        if (row.getScore() == null || row.getScore() < 0 || row.getScore() > 750) {
            errors.add("分数必须在0-750之间");
        } else if (!seenScores.add(row.getScore())) {
            errors.add("分数重复");
        }
        if (row.getCumulativeCount() == null || row.getCumulativeCount() <= previousCumulative) {
            errors.add("累计人数必须递增");
        }
        if (row.getSegmentCount() != null) {
            if (row.getSegmentCount() < 0) {
                errors.add("本段人数不能为负数");
            } else if (row.getCumulativeCount() != null && row.getSegmentCount() > row.getCumulativeCount()) {
                errors.add("本段人数不能大于累计人数");
            } else if (previousScore != null && row.getScore() != null
                    && previousScore == row.getScore() + 1
                    && row.getCumulativeCount() != null
                    && row.getSegmentCount() != row.getCumulativeCount() - previousCumulative) {
                errors.add("连续分数的本段人数与累计人数差值不一致");
            } else if (previousScore != null && row.getScore() != null
                    && previousScore > row.getScore() + 1
                    && row.getCumulativeCount() != null
                    && row.getCumulativeCount() - row.getSegmentCount() < previousCumulative) {
                errors.add("省略分数段后的累计人数与本段人数不一致");
            }
        }
        return errors;
    }

    private List<String> validateGroupLineRow(GroupLineRow row, Set<String> seenKeys) {
        List<String> errors = new ArrayList<>();
        if (row == null) {
            errors.add("空行");
            return errors;
        }
        requireText(errors, row.getGroupCode(), "院校专业组代码不能为空");
        requireSubjectValue(errors, row.getSubjectType());
        if (row.getMinScore() == null || row.getMinScore() <= 0 || row.getMinScore() > 750) {
            errors.add("最低分必须在1-750之间");
        }
        if (row.getMinRank() != null && row.getMinRank() <= 0) {
            errors.add("最低位次填写时必须大于0");
        }
        String batch = defaultText(row.getBatch(), TARGET_BATCH);
        if (!batch.contains("本科") || !batch.contains("批")) {
            errors.add("批次必须为普通本科批次口径");
        }
        String key = trimToEmpty(row.getSchoolId()) + "#" + trimToEmpty(row.getUniversityName())
                + "#" + trimToEmpty(row.getGroupCode()) + "#" + trimToEmpty(row.getSubjectType());
        if (!seenKeys.add(key)) {
            errors.add("导入批次内院校专业组重复");
        }
        return errors;
    }

    private void applyGroupLineRankSource(ProvinceDataContext context, int year, GroupLineRow row, List<String> errors) {
        if (row.getMinRank() != null && row.getMinRank() > 0) {
            row.setRankSourceType(defaultText(row.getRankSourceType(), RANK_SOURCE_ORIGINAL));
            row.setRankSourceNote(defaultText(row.getRankSourceNote(), "院校专业组原始调档位次，来源于官方或学校官网公开数据。"));
            return;
        }
        if (row.getMinScore() == null || row.getMinScore() <= 0
                || !SUBJECT_TYPES.contains(trimToEmpty(row.getSubjectType()))) {
            return;
        }
        DataScoreRank rank = dataScoreRankMapper.selectNearestAtOrBelow(
                context.provinceCode(), year, row.getSubjectType().trim(), row.getMinScore());
        if (rank == null || rank.getRankHigh() == null || rank.getRankHigh() <= 0) {
            errors.add("缺少最低位次且未命中" + context.provinceName() + "官方一分一段可换算位次");
            return;
        }
        row.setMinRank(rank.getRankHigh());
        row.setRankSourceType(RANK_SOURCE_CONVERTED);
        row.setRankSourceUrl(rank.getSourceUrl());
        row.setRankSourcePageUrl(rank.getSourcePageUrl());
        int rankLow = rank.getRankLow() == null || rank.getRankLow() <= 0 ? rank.getRankHigh() : rank.getRankLow();
        row.setRankSourceNote(String.format(
                "%d年%s%s官方一分一段按最低分%s换算，约%d-%d名；该值不是原始调档位次，需结合来源复核。",
                rank.getYear(), rank.getSubjectType(), context.provinceName(), rank.getScoreLabel(), rankLow, rank.getRankHigh()));
    }

    private List<String> validateGroupPlanRow(GroupPlanRow row, Set<String> seenKeys) {
        List<String> errors = new ArrayList<>();
        if (row == null) {
            errors.add("空行");
            return errors;
        }
        requireText(errors, row.getGroupCode(), "院校专业组代码不能为空");
        requireText(errors, row.getMajorCode(), "专业代码不能为空");
        requireText(errors, row.getMajorName(), "专业名称不能为空");
        requireSubjectValue(errors, row.getSubjectType());
        if (row.getPlanCount() != null && row.getPlanCount() < 0) {
            errors.add("计划数不能为负数");
        }
        String key = trimToEmpty(row.getSchoolId()) + "#" + trimToEmpty(row.getUniversityName())
                + "#" + trimToEmpty(row.getGroupCode()) + "#" + trimToEmpty(row.getSubjectType())
                + "#" + trimToEmpty(row.getMajorCode());
        if (!seenKeys.add(key)) {
            errors.add("导入批次内招生计划专业重复");
        }
        return errors;
    }

    private void requireSubjectValue(List<String> errors, String subjectType) {
        if (!SUBJECT_TYPES.contains(trimToEmpty(subjectType))) {
            errors.add("科类必须为物理类或历史类");
        }
    }

    private void requireText(List<String> errors, String value, String message) {
        if (!StringUtils.hasText(value)) {
            errors.add(message);
        }
    }

    private University resolveUniversity(String schoolId, String universityName) {
        if (StringUtils.hasText(schoolId)) {
            University byId = universityMapper.selectOne(new LambdaQueryWrapper<University>()
                    .eq(University::getSchoolId, schoolId.trim())
                    .last("LIMIT 1"));
            if (byId != null) {
                return byId;
            }
        }
        if (StringUtils.hasText(universityName)) {
            return universityMapper.selectOne(new LambdaQueryWrapper<University>()
                    .eq(University::getName, universityName.trim())
                    .last("LIMIT 1"));
        }
        return null;
    }

    private DataScoreRank findScoreRank(ProvinceDataContext context, int year, String subjectType, int score) {
        return dataScoreRankMapper.selectOne(new LambdaQueryWrapper<DataScoreRank>()
                .eq(DataScoreRank::getProvinceCode, context.provinceCode())
                .eq(DataScoreRank::getYear, year)
                .eq(DataScoreRank::getSubjectType, subjectType)
                .eq(DataScoreRank::getScore, score)
                .last("LIMIT 1"));
    }

    private DataAdmissionGroupLine findGroupLine(ProvinceDataContext context, int year, String schoolId, String groupCode,
                                                 String subjectType, String batch) {
        return groupLineMapper.selectOne(new LambdaQueryWrapper<DataAdmissionGroupLine>()
                .eq(DataAdmissionGroupLine::getProvinceCode, context.provinceCode())
                .eq(DataAdmissionGroupLine::getYear, year)
                .eq(DataAdmissionGroupLine::getSchoolId, schoolId)
                .eq(DataAdmissionGroupLine::getGroupCode, groupCode)
                .eq(DataAdmissionGroupLine::getSubjectType, subjectType)
                .eq(DataAdmissionGroupLine::getBatch, defaultText(batch, context.targetBatch()))
                .last("LIMIT 1"));
    }

    private DataAdmissionGroupPlan findGroupPlan(ProvinceDataContext context, int year, String schoolId, String groupCode,
                                                 String subjectType, String majorCode) {
        return groupPlanMapper.selectOne(new LambdaQueryWrapper<DataAdmissionGroupPlan>()
                .eq(DataAdmissionGroupPlan::getProvinceCode, context.provinceCode())
                .eq(DataAdmissionGroupPlan::getYear, year)
                .eq(DataAdmissionGroupPlan::getSchoolId, schoolId)
                .eq(DataAdmissionGroupPlan::getGroupCode, groupCode)
                .eq(DataAdmissionGroupPlan::getSubjectType, subjectType)
                .eq(DataAdmissionGroupPlan::getMajorCode, majorCode)
                .last("LIMIT 1"));
    }

    private DataAdmissionGroupLine toGroupLineEntity(ProvinceDataContext context, GroupLineImportRequest request, GroupLineRow row, University university) {
        DataAdmissionGroupLine entity = new DataAdmissionGroupLine();
        entity.setProvinceCode(context.provinceCode());
        entity.setProvinceName(context.provinceName());
        entity.setYear(request.getYear());
        entity.setSchoolId(university.getSchoolId());
        entity.setUniversityName(defaultText(row.getUniversityName(), university.getName()));
        entity.setGroupCode(row.getGroupCode().trim());
        entity.setGroupName(defaultText(row.getGroupName(), row.getGroupCode().trim()));
        entity.setSubjectType(row.getSubjectType().trim());
        entity.setFirstSubjectRequirement(defaultText(row.getFirstSubjectRequirement(), firstSubject(row.getSubjectType())));
        entity.setResubjectRequirement(trimToEmpty(row.getResubjectRequirement()));
        entity.setMinScore(row.getMinScore());
        entity.setMinRank(row.getMinRank());
        entity.setRankSourceType(defaultText(row.getRankSourceType(), row.getMinRank() == null ? "" : RANK_SOURCE_ORIGINAL));
        entity.setRankSourceNote(trimToEmpty(row.getRankSourceNote()));
        entity.setRankSourceUrl(trimToEmpty(row.getRankSourceUrl()));
        entity.setRankSourcePageUrl(trimToEmpty(row.getRankSourcePageUrl()));
        entity.setPlanCount(row.getPlanCount());
        entity.setBatch(defaultText(row.getBatch(), context.targetBatch()));
        entity.setSourceName(sourceNameForLevel(context, request.getSourceLevel()));
        entity.setSourceUrl(trimToEmpty(request.getSourceUrl()));
        entity.setSourcePageUrl(trimToEmpty(request.getSourcePageUrl()));
        entity.setSourceLevel(defaultText(request.getSourceLevel(), "manual_verified"));
        entity.setParseMethod(defaultText(request.getParseMethod(), "manual_verified"));
        return entity;
    }

    private DataAdmissionGroupPlan toGroupPlanEntity(ProvinceDataContext context, GroupPlanImportRequest request, GroupPlanRow row,
                                                     University university, DataAdmissionGroupLine groupLine) {
        DataAdmissionGroupPlan entity = new DataAdmissionGroupPlan();
        entity.setProvinceCode(context.provinceCode());
        entity.setProvinceName(context.provinceName());
        entity.setYear(request.getYear());
        entity.setSchoolId(university.getSchoolId());
        entity.setUniversityName(defaultText(row.getUniversityName(), university.getName()));
        entity.setGroupCode(row.getGroupCode().trim());
        entity.setGroupName(defaultText(row.getGroupName(), groupLine.getGroupName()));
        entity.setMajorCode(row.getMajorCode().trim());
        entity.setMajorName(row.getMajorName().trim());
        entity.setSubjectType(row.getSubjectType().trim());
        entity.setFirstSubjectRequirement(defaultText(row.getFirstSubjectRequirement(), groupLine.getFirstSubjectRequirement()));
        entity.setResubjectRequirement(defaultText(row.getResubjectRequirement(), groupLine.getResubjectRequirement()));
        entity.setPlanCount(row.getPlanCount());
        entity.setTuition(trimToEmpty(row.getTuition()));
        entity.setStudyYears(trimToEmpty(row.getStudyYears()));
        entity.setBatch(defaultText(row.getBatch(), context.targetBatch()));
        entity.setSourceName(sourceNameForLevel(context, request.getSourceLevel()));
        entity.setSourceUrl(trimToEmpty(request.getSourceUrl()));
        entity.setSourcePageUrl(trimToEmpty(request.getSourcePageUrl()));
        entity.setSourceLevel(defaultText(request.getSourceLevel(), "manual_verified"));
        entity.setParseMethod(defaultText(request.getParseMethod(), "manual_verified"));
        return entity;
    }

    private String firstSubject(String subjectType) {
        return "历史类".equals(subjectType) ? "历史" : "物理";
    }

    private String sourceNameForLevel(ProvinceDataContext context, String sourceLevel) {
        return SOURCE_LEVEL_SCHOOL_VERIFIED.equals(sourceLevel) ? "学校官网补充" : context.sourceName();
    }

    private void mergeScoreRank(DataScoreRank target, DataScoreRank next, boolean preserveNonEmpty) {
        if (!preserveNonEmpty || emptyNumber(target.getSegmentCount())) target.setSegmentCount(next.getSegmentCount());
        if (!preserveNonEmpty || emptyNumber(target.getCumulativeCount())) target.setCumulativeCount(next.getCumulativeCount());
        if (!preserveNonEmpty || emptyNumber(target.getRankLow())) target.setRankLow(next.getRankLow());
        if (!preserveNonEmpty || emptyNumber(target.getRankHigh())) target.setRankHigh(next.getRankHigh());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getScoreLabel())) target.setScoreLabel(next.getScoreLabel());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceName())) target.setSourceName(next.getSourceName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceUrl())) target.setSourceUrl(next.getSourceUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourcePageUrl())) target.setSourcePageUrl(next.getSourcePageUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceFile())) target.setSourceFile(next.getSourceFile());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getParseMethod())) target.setParseMethod(next.getParseMethod());
        if (!preserveNonEmpty || target.getCumulativeRate() == null) target.setCumulativeRate(next.getCumulativeRate());
    }

    private void mergeGroupLine(DataAdmissionGroupLine target, DataAdmissionGroupLine next, boolean preserveNonEmpty) {
        if (!preserveNonEmpty || !StringUtils.hasText(target.getUniversityName())) target.setUniversityName(next.getUniversityName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getGroupName())) target.setGroupName(next.getGroupName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getFirstSubjectRequirement())) target.setFirstSubjectRequirement(next.getFirstSubjectRequirement());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getResubjectRequirement())) target.setResubjectRequirement(next.getResubjectRequirement());
        if (!preserveNonEmpty || emptyNumber(target.getMinScore())) target.setMinScore(next.getMinScore());
        if (!preserveNonEmpty || emptyNumber(target.getMinRank())) target.setMinRank(next.getMinRank());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getRankSourceType())) target.setRankSourceType(next.getRankSourceType());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getRankSourceNote())) target.setRankSourceNote(next.getRankSourceNote());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getRankSourceUrl())) target.setRankSourceUrl(next.getRankSourceUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getRankSourcePageUrl())) target.setRankSourcePageUrl(next.getRankSourcePageUrl());
        if (!preserveNonEmpty || emptyNumber(target.getPlanCount())) target.setPlanCount(next.getPlanCount());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceName())) target.setSourceName(next.getSourceName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceUrl())) target.setSourceUrl(next.getSourceUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourcePageUrl())) target.setSourcePageUrl(next.getSourcePageUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceLevel())) target.setSourceLevel(next.getSourceLevel());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getParseMethod())) target.setParseMethod(next.getParseMethod());
    }

    private void mergeGroupPlan(DataAdmissionGroupPlan target, DataAdmissionGroupPlan next, boolean preserveNonEmpty) {
        if (!preserveNonEmpty || !StringUtils.hasText(target.getUniversityName())) target.setUniversityName(next.getUniversityName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getGroupName())) target.setGroupName(next.getGroupName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getMajorName())) target.setMajorName(next.getMajorName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getFirstSubjectRequirement())) target.setFirstSubjectRequirement(next.getFirstSubjectRequirement());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getResubjectRequirement())) target.setResubjectRequirement(next.getResubjectRequirement());
        if (!preserveNonEmpty || emptyNumber(target.getPlanCount())) target.setPlanCount(next.getPlanCount());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getTuition())) target.setTuition(next.getTuition());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getStudyYears())) target.setStudyYears(next.getStudyYears());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceName())) target.setSourceName(next.getSourceName());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceUrl())) target.setSourceUrl(next.getSourceUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourcePageUrl())) target.setSourcePageUrl(next.getSourcePageUrl());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getSourceLevel())) target.setSourceLevel(next.getSourceLevel());
        if (!preserveNonEmpty || !StringUtils.hasText(target.getParseMethod())) target.setParseMethod(next.getParseMethod());
    }

    private boolean emptyNumber(Integer value) {
        return value == null || value <= 0;
    }

    private void countDryRunWrite(ImportResult result, Object existing) {
        if (existing == null) {
            result.setInserted(result.getInserted() + 1);
        } else {
            result.setUpdated(result.getUpdated() + 1);
        }
    }

    private void reject(ImportResult result, int lineNo, List<String> errors) {
        result.setRejected(result.getRejected() + 1);
        RowError error = new RowError();
        error.setLineNo(lineNo);
        error.setErrors(errors);
        result.getErrors().add(error);
    }

    private void addUnresolvedSchool(ImportResult result, String schoolId, String universityName) {
        String value = defaultText(schoolId, "") + "|" + defaultText(universityName, "");
        if (!result.getUnresolvedSchools().contains(value)) {
            result.getUnresolvedSchools().add(value);
        }
    }

    private ImportResult baseImportResult(ProvinceDataContext context, boolean dryRun, int totalRows) {
        ImportResult result = new ImportResult();
        result.setDryRun(dryRun);
        result.setTotalRows(totalRows);
        result.setProvinceCode(context.provinceCode());
        result.setProvinceName(context.provinceName());
        result.setYear(DEFAULT_YEAR);
        result.setMessage("success");
        return result;
    }

    private List<ScoreRankRow> collectScoreRankRows(ScoreRankImportRequest request) {
        List<ScoreRankRow> rows = new ArrayList<>();
        if (request.getRows() != null) {
            rows.addAll(request.getRows());
        }
        if (StringUtils.hasText(request.getCsvText())) {
            rows.addAll(parseScoreRankCsv(request.getCsvText()));
        }
        if (rows.isEmpty() && StringUtils.hasText(request.getOcrText())) {
            rows.addAll(parseScoreRankOcr(request.getOcrText()));
        }
        if (rows.isEmpty()) {
            throw new BizException("一分一段导入行不能为空");
        }
        return rows;
    }

    private List<GroupLineRow> collectGroupLineRows(GroupLineImportRequest request) {
        List<GroupLineRow> rows = new ArrayList<>();
        if (request.getRows() != null) {
            rows.addAll(request.getRows());
        }
        if (StringUtils.hasText(request.getCsvText())) {
            rows.addAll(parseGroupLineCsv(request.getCsvText()));
        }
        if (rows.isEmpty()) {
            throw new BizException("院校专业组线导入行不能为空");
        }
        return rows;
    }

    private List<GroupPlanRow> collectGroupPlanRows(GroupPlanImportRequest request) {
        List<GroupPlanRow> rows = new ArrayList<>();
        if (request.getRows() != null) {
            rows.addAll(request.getRows());
        }
        if (StringUtils.hasText(request.getCsvText())) {
            rows.addAll(parseGroupPlanCsv(request.getCsvText()));
        }
        if (rows.isEmpty()) {
            throw new BizException("招生计划导入行不能为空");
        }
        return rows;
    }

    private List<ScoreRankRow> parseScoreRankOcr(String text) {
        List<ScoreRankRow> rows = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = OCR_RANK_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            ScoreRankRow row = new ScoreRankRow();
            row.setScore(parseInteger(matcher.group(1)));
            row.setSegmentCount(parseInteger(matcher.group(2)));
            row.setCumulativeCount(parseInteger(matcher.group(3)));
            rows.add(row);
        }
        return rows;
    }

    private List<ScoreRankRow> parseScoreRankCsv(String csvText) {
        List<Map<String, String>> records = parseCsv(csvText);
        List<ScoreRankRow> rows = new ArrayList<>();
        for (Map<String, String> record : records) {
            ScoreRankRow row = new ScoreRankRow();
            row.setScore(parseInteger(value(record, "score", "分数")));
            row.setScoreLabel(value(record, "scoreLabel", "score_label", "分数段"));
            row.setSegmentCount(parseInteger(value(record, "segmentCount", "segment_count", "本段人数", "人数")));
            row.setCumulativeCount(parseInteger(value(record, "cumulativeCount", "cumulative_count", "累计人数", "累计")));
            rows.add(row);
        }
        return rows;
    }

    private List<GroupLineRow> parseGroupLineCsv(String csvText) {
        List<Map<String, String>> records = parseCsv(csvText);
        List<GroupLineRow> rows = new ArrayList<>();
        for (Map<String, String> record : records) {
            GroupLineRow row = new GroupLineRow();
            row.setSchoolId(value(record, "schoolId", "school_id", "院校代码", "院校ID"));
            row.setUniversityName(value(record, "universityName", "university_name", "院校名称"));
            row.setGroupCode(value(record, "groupCode", "group_code", "专业组代码", "院校专业组代码"));
            row.setGroupName(value(record, "groupName", "group_name", "专业组名称", "院校专业组名称"));
            row.setSubjectType(value(record, "subjectType", "subject_type", "科类"));
            row.setFirstSubjectRequirement(value(record, "firstSubjectRequirement", "first_subject_requirement", "首选科目"));
            row.setResubjectRequirement(value(record, "resubjectRequirement", "resubject_requirement", "再选科目"));
            row.setMinScore(parseInteger(value(record, "minScore", "min_score", "最低分", "调档分", "投档分")));
            row.setMinRank(parseInteger(value(record, "minRank", "min_rank", "最低位次", "调档位次", "投档位次")));
            row.setPlanCount(parseInteger(value(record, "planCount", "plan_count", "计划数")));
            row.setBatch(value(record, "batch", "批次"));
            rows.add(row);
        }
        return rows;
    }

    private List<GroupPlanRow> parseGroupPlanCsv(String csvText) {
        List<Map<String, String>> records = parseCsv(csvText);
        List<GroupPlanRow> rows = new ArrayList<>();
        for (Map<String, String> record : records) {
            GroupPlanRow row = new GroupPlanRow();
            row.setSchoolId(value(record, "schoolId", "school_id", "院校代码", "院校ID"));
            row.setUniversityName(value(record, "universityName", "university_name", "院校名称"));
            row.setGroupCode(value(record, "groupCode", "group_code", "专业组代码", "院校专业组代码"));
            row.setGroupName(value(record, "groupName", "group_name", "专业组名称", "院校专业组名称"));
            row.setMajorCode(value(record, "majorCode", "major_code", "专业代码"));
            row.setMajorName(value(record, "majorName", "major_name", "专业名称"));
            row.setSubjectType(value(record, "subjectType", "subject_type", "科类"));
            row.setFirstSubjectRequirement(value(record, "firstSubjectRequirement", "first_subject_requirement", "首选科目"));
            row.setResubjectRequirement(value(record, "resubjectRequirement", "resubject_requirement", "再选科目"));
            row.setPlanCount(parseInteger(value(record, "planCount", "plan_count", "计划数")));
            row.setTuition(value(record, "tuition", "学费"));
            row.setStudyYears(value(record, "studyYears", "study_years", "学制"));
            row.setBatch(value(record, "batch", "批次"));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> parseCsv(String csvText) {
        List<String> lines = csvText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (lines.size() < 2) {
            return List.of();
        }
        List<String> headers = splitCsvLine(lines.get(0));
        List<Map<String, String>> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsvLine(lines.get(i));
            Map<String, String> record = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                record.put(headers.get(j).trim(), j < values.size() ? values.get(j).trim() : "");
            }
            records.add(record);
        }
        return records;
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private String value(Map<String, String> record, String... keys) {
        for (String key : keys) {
            String value = record.get(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProvinceDataContext context(String provinceCode) {
        String normalized = provinceCode == null ? PROVINCE_CODE : provinceCode.trim().toUpperCase(Locale.ROOT);
        ProvinceDataContext context = PROVINCE_CONTEXTS.get(normalized);
        if (context == null) {
            throw new BizException("暂不支持该省份数据导入");
        }
        return context;
    }

    private List<OfficialSource> officialSources(ProvinceDataContext context, int year) {
        if (ProvincePolicyService.SC.equals(context.provinceCode())) {
            return List.of(
                    new OfficialSource(year, "", "requirement", "", "https://www.sceea.cn/Html/202501/Newsdetail_4130.html", "official_html"),
                    new OfficialSource(year, "", "requirement", "", "https://www.sceea.cn/Html/202505/Newsdetail_4261.html", "official_html"),
                    new OfficialSource(year, "历史类", "score_rank", "", "https://www.sceea.cn/Html/202506/Newsdetail_4334.html", "html_image_ocr"),
                    new OfficialSource(year, "物理类", "score_rank", "", "https://www.sceea.cn/Html/202506/Newsdetail_4335.html", "html_image_ocr"),
                    new OfficialSource(year, "", "group_line", context.targetBatch(), "https://www.sceea.cn/Html/202507/Newsdetail_4405.html", "manual_verified"),
                    new OfficialSource(year, "", "group_plan", context.targetBatch(), "https://www.sceea.cn/Html/202506/Newsdetail_4330.html", "manual_verified"),
                    new OfficialSource(year, "", "group_plan", context.targetBatch(), "https://www.sceea.cn/Html/202506/Newsdetail_4338.html", "manual_verified")
            );
        }
        if (ProvincePolicyService.HB.equals(context.provinceCode())) {
            return List.of(
                    new OfficialSource(year, "", "requirement", context.targetBatch(), "http://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202506/t20250618_5697087.shtml", "official_html"),
                    new OfficialSource(year, "", "requirement", context.targetBatch(), "https://jyt.hubei.gov.cn/bmdt/ztzl/gxzs/zszy/zsfw/202506/P020250620581412805028.pdf", "official_pdf"),
                    new OfficialSource(year, "", "score_rank", "", "http://www.hbccks.cn/html/gkgzzt/yfyd/", "official_html"),
                    new OfficialSource(year, "物理类", "score_rank", "", "http://www.hbccks.cn/html/yfyd/2025-06/142616.html", "html_image_ocr"),
                    new OfficialSource(year, "历史类", "score_rank", "", "http://www.hbccks.cn/html/yfyd/2025-06/142617.html", "html_image_ocr")
            );
        }
        return List.of(
                new OfficialSource(year, "", "requirement", context.targetBatch(), "https://gaokao.chsi.com.cn/gkxx/zc/ss/202505/20250512/2293378850-8.html", "authorized_reprint_html"),
                new OfficialSource(year, "", "requirement", context.targetBatch(), "https://gaokao.eol.cn/an_hui/dongtai/202505/t20250513_2668022.shtml", "authorized_reprint_html"),
                new OfficialSource(year, "", "score_rank", "", "http://edu.anhuinews.com/kszx/gk/gzdt/202506/t20250625_8581781.html", "authorized_reprint_image_ocr")
        );
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String sha256(String text) {
        MessageDigest digest = sha256Digest();
        digest.update(trimToEmpty(text).getBytes(StandardCharsets.UTF_8));
        return hex(digest.digest());
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private boolean defaultBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private record ProvinceDataContext(String provinceCode, String provinceName, String sourceName,
                                       String targetBatch, String batchKeyword, Set<String> officialHosts,
                                       String scoreRankBlockingReason, String groupLineBlockingReason,
                                       String groupPlanBlockingReason) {
    }

    private record OfficialSource(int year, String subjectType, String dataType, String batch,
                                  String sourcePageUrl, String parseMethod) {
    }

    @Data
    public static class SourceRefreshRequest {
        private Integer year;
        private Boolean fetchChildren;
    }

    @Data
    public static class SourceRefreshResult {
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private int registered;
        private int failed;
        private List<String> errors = new ArrayList<>();
        private List<SourceView> sources = new ArrayList<>();
    }

    @Data
    public static class SourceView {
        private String dataType;
        private String subjectType;
        private String batch;
        private String sourcePageUrl;
        private String sourceUrl;
        private String sourceHash;
        private String status;
        private String parseMethod;
        private LocalDateTime lastCheckedAt;

        static SourceView from(DataSourceRegistry registry) {
            SourceView view = new SourceView();
            view.setDataType(registry.getDataType());
            view.setSubjectType(registry.getSubjectType());
            view.setBatch(registry.getBatch());
            view.setSourcePageUrl(registry.getSourcePageUrl());
            view.setSourceUrl(registry.getSourceUrl());
            view.setSourceHash(registry.getSourceHash());
            view.setStatus(registry.getStatus());
            view.setParseMethod(registry.getParseMethod());
            view.setLastCheckedAt(registry.getLastCheckedAt());
            return view;
        }
    }

    @Data
    public static class StatusResponse {
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private String officialSourceName;
        private String targetBatch;
        private int targetGroupCount;
        private boolean generationReady;
        private Map<String, SubjectStatus> subjects = new LinkedHashMap<>();
        private Map<String, Long> sourceStatusCounts = new LinkedHashMap<>();
        private Map<String, SourceCompleteness> sourceCompleteness = new LinkedHashMap<>();
        private List<String> blockingReasons = new ArrayList<>();
    }

    @Data
    public static class SubjectStatus {
        private String subjectType;
        private long scoreRankRows;
        private long groupLineGroups;
        private long groupPlanGroups;
        private long groupLineOriginalRankGroups;
        private long groupLineConvertedRankGroups;
        private long groupLineSchoolVerifiedGroups;
        private long groupPlanSchoolVerifiedGroups;
        private long groupLineRemainingGroups;
        private long groupPlanRemainingGroups;
        private boolean generationReady;
        private String lockReason;
    }

    @Data
    public static class SourceCompleteness {
        private String dataType;
        private String label;
        private String sourceCompleteness;
        private String blockingReason;
        private long importedRows;
        private long importedGroups;
        private long expectedMinimum;
        private long remainingRows;
        private long remainingGroups;
        private boolean officialSourceRegistered;
        private boolean ready;
    }

    @Data
    public static class ImportResult {
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private boolean dryRun;
        private int totalRows;
        private int validRows;
        private int inserted;
        private int updated;
        private int skipped;
        private int rejected;
        private String message;
        private List<RowError> errors = new ArrayList<>();
        private List<String> unresolvedSchools = new ArrayList<>();
    }

    @Data
    public static class RowError {
        private int lineNo;
        private List<String> errors = new ArrayList<>();
    }

    @Data
    public static class ScoreRankImportRequest {
        private Integer year;
        private String subjectType;
        private String sourcePageUrl;
        private String sourceUrl;
        private String sourceFile;
        private String sourceHash;
        private String parseMethod;
        private Boolean preserveNonEmpty;
        private String csvText;
        private String ocrText;
        private List<ScoreRankRow> rows = new ArrayList<>();

        boolean isPreserveNonEmpty() {
            return defaultBooleanBoxed(preserveNonEmpty, true);
        }
    }

    @Data
    public static class ScoreRankRow {
        private Integer score;
        private String scoreLabel;
        private Integer segmentCount;
        private Integer cumulativeCount;
        private BigDecimal cumulativeRate;
    }

    @Data
    public static class GroupLineImportRequest {
        private Integer year;
        private String sourcePageUrl;
        private String sourceUrl;
        private String sourceHash;
        private String sourceLevel;
        private String parseMethod;
        private Boolean preserveNonEmpty;
        private String csvText;
        private List<GroupLineRow> rows = new ArrayList<>();

        boolean isPreserveNonEmpty() {
            return defaultBooleanBoxed(preserveNonEmpty, true);
        }
    }

    @Data
    public static class GroupLineRow {
        private String schoolId;
        private String universityName;
        private String groupCode;
        private String groupName;
        private String subjectType;
        private String firstSubjectRequirement;
        private String resubjectRequirement;
        private Integer minScore;
        private Integer minRank;
        private String rankSourceType;
        private String rankSourceNote;
        private String rankSourceUrl;
        private String rankSourcePageUrl;
        private Integer planCount;
        private String batch;
    }

    @Data
    public static class GroupPlanImportRequest {
        private Integer year;
        private String sourcePageUrl;
        private String sourceUrl;
        private String sourceHash;
        private String sourceLevel;
        private String parseMethod;
        private Boolean preserveNonEmpty;
        private String csvText;
        private List<GroupPlanRow> rows = new ArrayList<>();

        boolean isPreserveNonEmpty() {
            return defaultBooleanBoxed(preserveNonEmpty, true);
        }
    }

    @Data
    public static class GroupPlanRow {
        private String schoolId;
        private String universityName;
        private String groupCode;
        private String groupName;
        private String majorCode;
        private String majorName;
        private String subjectType;
        private String firstSubjectRequirement;
        private String resubjectRequirement;
        private Integer planCount;
        private String tuition;
        private String studyYears;
        private String batch;
    }

    private static boolean defaultBooleanBoxed(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
