package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.UniOfficialLink;
import com.gzly.mapper.PlanHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialLinkPriorityService {

    private static final int MIN_RECENT_PLAN_THRESHOLD = 100;
    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(
            "weibo.com",
            "weibo.cn",
            "t.cn",
            "cctv.com"
    );
    private static final Set<String> ENTRY_ONLY_PATHS = Set.of(
            "",
            "/",
            "/index.html",
            "/index.htm",
            "/default.html",
            "/default.htm"
    );

    private final PlanHistoryMapper planHistoryMapper;
    private final ObjectMapper objectMapper;

    public PriorityContext buildPriorityContext(Collection<String> schoolIds, int requestedWindowDays) {
        Set<String> trackedSchoolIds = schoolIds == null
                ? Set.of()
                : schoolIds.stream()
                .filter(this::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int windowDays = normalizeWindowDays(requestedWindowDays);
        PlanSample sample = loadPlanSample(trackedSchoolIds, windowDays);
        boolean fallbackTriggered = false;

        if (windowDays == 30 && sample.planCount() < MIN_RECENT_PLAN_THRESHOLD) {
            sample = loadPlanSample(trackedSchoolIds, 90);
            windowDays = 90;
            fallbackTriggered = true;
        }

        int maxPlanHitCount = sample.planHitCountMap().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        return new PriorityContext(
                windowDays,
                fallbackTriggered,
                sample.planCount(),
                sample.planHitCountMap(),
                maxPlanHitCount
        );
    }

    public PriorityMeta buildPriorityMeta(String schoolId, UniOfficialLink link, PriorityContext context) {
        List<String> missingFields = collectPriorityMissingFields(link);
        int gapCount = missingFields.size();
        int planHitCount = context.planHitCountMap().getOrDefault(schoolId, 0);
        int hotScore = context.maxPlanHitCount() <= 0
                ? 0
                : (int) Math.round(planHitCount * 100.0 / context.maxPlanHitCount());

        String priorityLevel = resolvePriorityLevel(planHitCount, gapCount);
        List<String> priorityReasons = buildPriorityReasons(missingFields, planHitCount, context);

        return new PriorityMeta(planHitCount, hotScore, gapCount, missingFields, priorityLevel, priorityReasons);
    }

    private PlanSample loadPlanSample(Set<String> trackedSchoolIds, int windowDays) {
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
        List<PlanHistory> plans = planHistoryMapper.selectList(new QueryWrapper<PlanHistory>()
                .ge("created_at", since)
                .select("id", "plan_json", "created_at")
                .orderByDesc("created_at"));

        Map<String, Integer> planHitCountMap = new java.util.HashMap<>();
        for (PlanHistory plan : plans) {
            Set<String> schoolIdSet = extractSchoolIds(plan.getId(), plan.getPlanJson(), trackedSchoolIds);
            for (String schoolId : schoolIdSet) {
                planHitCountMap.merge(schoolId, 1, Integer::sum);
            }
        }

        return new PlanSample(plans.size(), planHitCountMap);
    }

    private Set<String> extractSchoolIds(Long planId, String planJson, Set<String> trackedSchoolIds) {
        if (isBlank(planJson)) {
            return Set.of();
        }

        try {
            JsonNode root = objectMapper.readTree(planJson);
            if (!root.isArray()) {
                return Set.of();
            }

            Set<String> schoolIdSet = new LinkedHashSet<>();
            for (JsonNode node : root) {
                String schoolId = node.path("schoolId").asText("");
                if (isBlank(schoolId)) {
                    continue;
                }
                String trimmedSchoolId = schoolId.trim();
                if (!trackedSchoolIds.isEmpty() && !trackedSchoolIds.contains(trimmedSchoolId)) {
                    continue;
                }
                schoolIdSet.add(trimmedSchoolId);
            }
            return schoolIdSet;
        } catch (Exception e) {
            log.warn("解析志愿方案学校热度失败: planId={}", planId, e);
            return Set.of();
        }
    }

    private List<String> collectPriorityMissingFields(UniOfficialLink link) {
        List<String> missingFields = new ArrayList<>();
        if (link == null || isMissingDetailUrl(link.getTuitionInfoUrl(), link)) {
            missingFields.add("tuitionInfoUrl");
        }
        if (link == null || isBlank(link.getTuitionSummary())) {
            missingFields.add("tuitionSummary");
        }
        if (link == null || isMissingDetailUrl(link.getAdmissionBrochureUrl(), link)) {
            missingFields.add("admissionBrochureUrl");
        }
        if (link == null || isMissingDetailUrl(link.getMajorCatalogUrl(), link)) {
            missingFields.add("majorCatalogUrl");
        }
        if (link == null || link.getParseStatus() == null || link.getParseStatus() != 1) {
            missingFields.add("parsedContent");
        }
        return missingFields;
    }

    public static String sanitizeOfficialUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("#") || "about:blank".equals(lower)) {
            return "";
        }
        URI uri = tryParseUri(trimmed);
        if (uri == null) {
            return "";
        }
        String scheme = uri.getScheme();
        if (isBlank(scheme) || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return "";
        }
        String host = normalizeHost(uri.getHost());
        if (isBlank(host) || isBlockedHost(host)) {
            return "";
        }
        return trimmed;
    }

    public static boolean hasUsableDetailUrl(String value, String... entryUrls) {
        String sanitized = sanitizeOfficialUrl(value);
        if (sanitized.isEmpty()) {
            return false;
        }
        URI uri = tryParseUri(sanitized);
        if (uri == null || isEntryOnlyPath(uri)) {
            return false;
        }

        String normalizedCandidate = normalizeUrlForComparison(uri);
        for (String entryUrl : entryUrls) {
            String sanitizedEntryUrl = sanitizeOfficialUrl(entryUrl);
            if (sanitizedEntryUrl.isEmpty()) {
                continue;
            }
            URI entryUri = tryParseUri(sanitizedEntryUrl);
            if (entryUri == null) {
                continue;
            }
            if (normalizedCandidate.equals(normalizeUrlForComparison(entryUri))) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildPriorityReasons(List<String> missingFields, int planHitCount, PriorityContext context) {
        List<String> reasons = new ArrayList<>();
        if (planHitCount > 0) {
            reasons.add("近" + context.activeWindowDays() + "天方案命中 " + planHitCount + " 次");
        }
        if (context.fallbackTriggered()) {
            reasons.add("近30天样本不足，已回退90天热度窗口");
        }
        for (String field : missingFields) {
            reasons.add(mapMissingFieldLabel(field));
        }
        return reasons;
    }

    private String resolvePriorityLevel(int planHitCount, int gapCount) {
        if (planHitCount >= 3 && gapCount >= 1) {
            return "P0";
        }
        if ((planHitCount >= 1 && gapCount >= 1) || gapCount >= 3) {
            return "P1";
        }
        return "P2";
    }

    private String mapMissingFieldLabel(String missingField) {
        return switch (missingField) {
            case "tuitionInfoUrl" -> "缺收费标准链接";
            case "tuitionSummary" -> "缺收费摘要";
            case "admissionBrochureUrl" -> "缺招生章程";
            case "majorCatalogUrl" -> "缺专业目录";
            case "parsedContent" -> "结构化规则未完成";
            default -> "存在待补充字段";
        };
    }

    private int normalizeWindowDays(int requestedWindowDays) {
        return requestedWindowDays == 90 ? 90 : 30;
    }

    private boolean isMissingDetailUrl(String value, UniOfficialLink link) {
        if (link == null) {
            return true;
        }
        return !hasUsableDetailUrl(value, link.getSchoolSite(), link.getAdmissionSite());
    }

    private static boolean isEntryOnlyPath(URI uri) {
        String path = normalizePath(uri.getPath());
        String query = uri.getQuery();
        return ENTRY_ONLY_PATHS.contains(path) && isBlank(query);
    }

    private static boolean isBlockedHost(String host) {
        return BLOCKED_HOST_SUFFIXES.stream()
                .anyMatch(blocked -> host.equals(blocked) || host.endsWith("." + blocked));
    }

    private static String normalizeUrlForComparison(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = normalizeHost(uri.getHost());
        String path = normalizePath(uri.getPath());
        String query = uri.getQuery();
        return scheme + "://" + host + path + (isBlank(query) ? "" : "?" + query);
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized.toLowerCase(Locale.ROOT) : "/" + normalized.toLowerCase(Locale.ROOT);
    }

    private static URI tryParseUri(String value) {
        try {
            return URI.create(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PriorityContext(
            int activeWindowDays,
            boolean fallbackTriggered,
            int recentPlanCount,
            Map<String, Integer> planHitCountMap,
            int maxPlanHitCount
    ) {
    }

    public record PriorityMeta(
            int planHitCount,
            int hotScore,
            int gapCount,
            List<String> missingFields,
            String priorityLevel,
            List<String> priorityReasons
    ) {
    }

    private record PlanSample(
            int planCount,
            Map<String, Integer> planHitCountMap
    ) {
    }
}
