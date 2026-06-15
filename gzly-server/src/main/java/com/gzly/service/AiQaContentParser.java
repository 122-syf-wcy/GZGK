package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 问答输出解析器（纯逻辑，可单测）。
 *
 * <p>约定 AI 在正文末尾用独立代码块输出来源 JSON 数组：</p>
 * <pre>
 * ```gzly-sources
 * [{"title":"...","url":"https://...","source":"...","summary":"..."}]
 * ```
 * </pre>
 * <p>解析后从展示正文中剔除该代码块，并把来源转成结构化卡片。
 * 没有来源（空数组或缺失代码块）时返回空列表，由上层补“未返回联网来源”提示。</p>
 */
public class AiQaContentParser {

    private static final Pattern SOURCES_BLOCK = Pattern.compile(
            "```\\s*gzly-sources\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int MAX_EVIDENCE = 6;
    private static final int MAX_TITLE = 200;
    private static final int MAX_URL = 600;
    private static final int MAX_SOURCE = 100;
    private static final int MAX_SUMMARY = 500;

    private final ObjectMapper objectMapper;

    public AiQaContentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public ParsedReply parse(String rawReply) {
        ParsedReply parsed = new ParsedReply();
        String raw = rawReply == null ? "" : rawReply;
        List<EvidenceItem> evidence = new ArrayList<>();

        Matcher matcher = SOURCES_BLOCK.matcher(raw);
        StringBuilder cleaned = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            cleaned.append(raw, last, matcher.start());
            last = matcher.end();
            evidence.addAll(parseEvidenceJson(matcher.group(1)));
        }
        cleaned.append(raw.substring(last));

        if (evidence.size() > MAX_EVIDENCE) {
            evidence = new ArrayList<>(evidence.subList(0, MAX_EVIDENCE));
        }
        parsed.setContent(cleaned.toString().trim());
        parsed.setEvidence(evidence);
        return parsed;
    }

    private List<EvidenceItem> parseEvidenceJson(String json) {
        List<EvidenceItem> items = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return items;
        }
        try {
            JsonNode root = objectMapper.readTree(json.trim());
            if (!root.isArray()) {
                return items;
            }
            for (JsonNode node : root) {
                String url = clean(node.path("url").asText(""), MAX_URL);
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    // 仅保留带可点击链接的来源，避免凭空标题
                    continue;
                }
                EvidenceItem item = new EvidenceItem();
                item.setTitle(clean(node.path("title").asText(""), MAX_TITLE));
                item.setUrl(url);
                item.setSourceName(clean(firstNonBlank(
                        node.path("source").asText(""),
                        node.path("sourceName").asText(""),
                        node.path("publisher").asText("")), MAX_SOURCE));
                item.setSummary(clean(node.path("summary").asText(""), MAX_SUMMARY));
                if (item.getTitle().isBlank()) {
                    item.setTitle(item.getUrl());
                }
                items.add(item);
            }
        } catch (Exception ignored) {
            // 容错：来源块非法 JSON 时按“无来源”处理
        }
        return items;
    }

    /** 估算 token 数（CJK 友好的粗略估计），用于压缩阈值判断。 */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int chars = text.length();
        // 中文约 1 字 ~ 1.6 token，英文约 4 字符 ~ 1 token；统一用 0.6 倍字符数粗估。
        return Math.max(1, (int) Math.round(chars * 0.6D));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    @Data
    public static class ParsedReply {
        private String content;
        private List<EvidenceItem> evidence;
    }

    @Data
    public static class EvidenceItem {
        private String title;
        private String url;
        private String sourceName;
        private String summary;
    }
}
