package com.gzly.compliance;

import com.gzly.entity.ComplianceSensitiveWord;
import com.gzly.mapper.ComplianceSensitiveWordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SensitiveWordMatcher 行为测试。
 *
 * <ul>
 *   <li>空文本返回空 hits。</li>
 *   <li>fallback 词库自动覆盖（数据库不可用时仍能扫描）。</li>
 *   <li>数据库词库合并并覆盖 fallback 同名词。</li>
 *   <li>命中返回 word/severity/replacement 完整 metadata。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordMatcherTest {

    @Mock ComplianceSensitiveWordMapper mapper;

    @InjectMocks SensitiveWordMatcher matcher;

    @Test
    void scan_blankText_returnsEmpty() {
        assertThat(matcher.scan(null)).isEmpty();
        assertThat(matcher.scan("")).isEmpty();
        assertThat(matcher.scan("   ")).isEmpty();
    }

    @Test
    void scan_usesFallbackRules_whenDbUnavailable() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db offline"));

        List<SensitiveWordMatcher.SensitiveWordHit> hits = matcher.scan("这个方案保证录取，稳上没问题。");

        // fallback 词库应命中 "保证录取"（high）和 "稳上"（high）
        assertThat(hits).extracting(SensitiveWordMatcher.SensitiveWordHit::getWord)
                .contains("保证录取", "稳上");
        assertThat(hits).allSatisfy(hit -> {
            assertThat(hit.getSeverity()).isNotBlank();
            assertThat(hit.getReplacement()).isNotBlank();
        });
    }

    @Test
    void scan_mergesDbAndFallback_dbOverridesSameWord() {
        ComplianceSensitiveWord dbWord = new ComplianceSensitiveWord();
        dbWord.setWord("保证录取");
        dbWord.setSeverity("critical"); // 覆盖 fallback 的 high
        dbWord.setReplacement("DB自定义替换");
        ComplianceSensitiveWord newWord = new ComplianceSensitiveWord();
        newWord.setWord("内幕消息");
        newWord.setSeverity("high");
        newWord.setReplacement("公开渠道信息");
        when(mapper.selectList(any())).thenReturn(List.of(dbWord, newWord));

        List<SensitiveWordMatcher.SensitiveWordHit> hits = matcher.scan("这是保证录取的内幕消息！");

        assertThat(hits).extracting(SensitiveWordMatcher.SensitiveWordHit::getWord)
                .contains("保证录取", "内幕消息");
        SensitiveWordMatcher.SensitiveWordHit overridden = hits.stream()
                .filter(h -> "保证录取".equals(h.getWord())).findFirst().orElseThrow();
        assertThat(overridden.getSeverity()).isEqualTo("critical");
        assertThat(overridden.getReplacement()).isEqualTo("DB自定义替换");
    }

    @Test
    void scan_blankFieldsFallbackToDefault() {
        ComplianceSensitiveWord row = new ComplianceSensitiveWord();
        row.setWord("特殊词");
        row.setSeverity("");
        row.setReplacement("");
        when(mapper.selectList(any())).thenReturn(List.of(row));

        List<SensitiveWordMatcher.SensitiveWordHit> hits = matcher.scan("命中特殊词");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getSeverity()).isEqualTo("medium");
        assertThat(hits.get(0).getReplacement()).isEqualTo("仅作为辅助参考");
    }
}
