package com.gzgk.common.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Regulatory compliance sanitizer backed by the shared single source of truth
 * `contracts/compliance-terms.json`. Hard terms are ordered longest-first to avoid
 * partial-substring leakage (e.g., "100%录取" before "录取").
 */
public final class ComplianceTextSanitizer {

    public static final String STANDARD_DISCLAIMER =
            "【公益辅助提示】以上分析及数据仅供志愿填报参考，不构成任何录取承诺，请以省教育考试院及高校官方招生章程为准。";

    private static final List<String> BUILTIN_HARD_FALLBACK = List.of(
            "保证不滑档", "随便报都能上", "百分百录取", "100%录取", "保证录取", "一定录取",
            "绝对录取", "确保录取", "铁定录取", "上岸概率", "录取概率", "绝对安全",
            "没有风险", "内部信息", "内部数据", "一定能上", "确定能上", "肯定能上",
            "保录取", "不滑档", "不脱档", "成功率", "录取率", "上榜率", "命中率",
            "包录取", "百分百", "零风险", "闭眼报", "稳上", "必上", "保送",
            "包过", "稳进", "稳过", "保录", "保上", "必录", "包上"
    );

    private final List<String> hardTerms;
    private final List<String> softTerms;

    public ComplianceTextSanitizer(List<String> hardTerms, List<String> softTerms) {
        this.hardTerms = hardTerms.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        this.softTerms = softTerms.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    public static ComplianceTextSanitizer fromJsonFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return defaultFallback();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Files.readString(path));
            List<String> hard = new ArrayList<>();
            root.path("hard").forEach(n -> hard.add(n.asText()));
            List<String> soft = new ArrayList<>();
            root.path("soft").forEach(n -> soft.add(n.asText()));
            if (hard.isEmpty()) {
                return defaultFallback();
            }
            return new ComplianceTextSanitizer(hard, soft);
        } catch (IOException e) {
            return defaultFallback();
        }
    }

    public static ComplianceTextSanitizer defaultFallback() {
        return new ComplianceTextSanitizer(BUILTIN_HARD_FALLBACK, List.of("一定能", "绝对能", "铁定", "稳了"));
    }

    public SanitizeResult sanitize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new SanitizeResult("", List.of(), false);
        }
        String sanitized = rawText;
        Set<String> hits = new LinkedHashSet<>();
        for (String term : hardTerms) {
            if (sanitized.contains(term)) {
                hits.add(term);
                sanitized = sanitized.replace(term, replacementFor(term));
            }
        }
        for (String term : softTerms) {
            if (sanitized.contains(term)) {
                hits.add(term);
                sanitized = sanitized.replace(term, "相对更有机会");
            }
        }
        return new SanitizeResult(sanitized, List.copyOf(hits), !hits.isEmpty());
    }

    public List<String> getHardTerms() {
        return hardTerms;
    }

    private static String replacementFor(String term) {
        if (term.contains("概率") || term.contains("率")) {
            return "机会参考度";
        }
        if (term.contains("滑档") || term.contains("脱档") || term.contains("风险") || term.contains("安全")) {
            return "风险控制参考";
        }
        return "历史参考评估";
    }

    public record SanitizeResult(String text, List<String> hitTerms, boolean modified) {
    }
}
