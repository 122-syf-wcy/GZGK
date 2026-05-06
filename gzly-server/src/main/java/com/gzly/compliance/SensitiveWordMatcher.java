package com.gzly.compliance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.ComplianceSensitiveWord;
import com.gzly.mapper.ComplianceSensitiveWordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SensitiveWordMatcher {

    private final ComplianceSensitiveWordMapper wordMapper;

    private static final Map<String, SensitiveWordRule> FALLBACK_RULES = fallbackRules();

    public List<SensitiveWordHit> scan(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, SensitiveWordRule> rules = new LinkedHashMap<>(FALLBACK_RULES);
        try {
            List<ComplianceSensitiveWord> rows = wordMapper.selectList(new LambdaQueryWrapper<ComplianceSensitiveWord>()
                    .eq(ComplianceSensitiveWord::getEnabled, 1));
            for (ComplianceSensitiveWord row : rows) {
                if (row.getWord() == null || row.getWord().isBlank()) {
                    continue;
                }
                rules.put(row.getWord(), new SensitiveWordRule(
                        row.getWord(),
                        blankToDefault(row.getSeverity(), "medium"),
                        blankToDefault(row.getReplacement(), "仅作为辅助参考")));
            }
        } catch (Exception ignored) {
            // 迁移未执行或数据库暂不可用时，仍使用内置兜底词表。
        }

        List<SensitiveWordHit> hits = new ArrayList<>();
        for (SensitiveWordRule rule : rules.values()) {
            if (text.contains(rule.getWord())) {
                SensitiveWordHit hit = new SensitiveWordHit();
                hit.setWord(rule.getWord());
                hit.setSeverity(rule.getSeverity());
                hit.setReplacement(rule.getReplacement());
                hits.add(hit);
            }
        }
        return hits;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, SensitiveWordRule> fallbackRules() {
        Map<String, SensitiveWordRule> rules = new LinkedHashMap<>();
        add(rules, "保证录取", "high", "仅作为辅助参考");
        add(rules, "保录", "high", "仅作为辅助参考");
        add(rules, "包录取", "high", "不可承诺录取结果");
        add(rules, "包上", "high", "不可承诺录取结果");
        add(rules, "稳上", "high", "风险相对较低");
        add(rules, "必上", "high", "具备一定参考优势");
        add(rules, "必录", "high", "具备一定参考优势");
        add(rules, "100%录取", "high", "机会指数较高");
        add(rules, "百分百录取", "high", "机会指数较高");
        add(rules, "一定能上", "high", "具备一定参考优势");
        add(rules, "一定录取", "high", "具备一定参考优势");
        add(rules, "绝对安全", "high", "风险相对较低，但仍需谨慎参考");
        add(rules, "没有风险", "high", "风险相对较低，但仍需谨慎参考");
        add(rules, "零风险", "high", "风险相对较低，但仍需谨慎参考");
        add(rules, "保证不滑档", "high", "仍需关注整体风险");
        add(rules, "确保录取", "high", "仅作为辅助参考");
        add(rules, "铁定录取", "high", "仅作为辅助参考");
        add(rules, "稳了", "medium", "风险相对较低");
        add(rules, "闭眼报", "high", "仍需逐条复核");
        add(rules, "随便报都能上", "high", "仍需结合官方数据谨慎参考");
        add(rules, "录取概率", "high", "机会指数");
        add(rules, "上岸概率", "high", "机会指数");
        add(rules, "命中率", "medium", "参考匹配度");
        add(rules, "成功率", "medium", "参考匹配度");
        add(rules, "录取率", "medium", "机会指数");
        return rules;
    }

    private static void add(Map<String, SensitiveWordRule> rules, String word, String severity, String replacement) {
        rules.put(word, new SensitiveWordRule(word, severity, replacement));
    }

    @Data
    public static class SensitiveWordHit {
        private String word;
        private String severity;
        private String replacement;
    }

    @Data
    private static class SensitiveWordRule {
        private final String word;
        private final String severity;
        private final String replacement;
    }
}
