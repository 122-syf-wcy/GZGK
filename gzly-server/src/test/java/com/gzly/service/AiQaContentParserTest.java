package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQaContentParserTest {

    private final AiQaContentParser parser = new AiQaContentParser(new ObjectMapper());

    @Test
    void parse_shouldExtractEvidenceAndStripSourcesBlock() {
        String raw = "> 本内容由 AI 生成，仅供参考。\n\n这是正文建议。\n\n"
                + "```gzly-sources\n"
                + "[{\"title\":\"广东省教育考试院\",\"url\":\"https://eea.gd.gov.cn/x\",\"source\":\"省考试院\",\"summary\":\"招生计划\"}]\n"
                + "```";
        AiQaContentParser.ParsedReply parsed = parser.parse(raw);
        assertThat(parsed.getContent()).contains("这是正文建议。");
        assertThat(parsed.getContent()).doesNotContain("gzly-sources");
        assertThat(parsed.getContent()).doesNotContain("eea.gd.gov.cn");
        assertThat(parsed.getEvidence()).hasSize(1);
        assertThat(parsed.getEvidence().get(0).getUrl()).isEqualTo("https://eea.gd.gov.cn/x");
        assertThat(parsed.getEvidence().get(0).getSourceName()).isEqualTo("省考试院");
    }

    @Test
    void parse_emptyArray_shouldYieldNoEvidence() {
        String raw = "正文\n```gzly-sources\n[]\n```";
        AiQaContentParser.ParsedReply parsed = parser.parse(raw);
        assertThat(parsed.getContent()).isEqualTo("正文");
        assertThat(parsed.getEvidence()).isEmpty();
    }

    @Test
    void parse_noSourcesBlock_shouldReturnContentAndEmptyEvidence() {
        AiQaContentParser.ParsedReply parsed = parser.parse("只有正文，没有来源块");
        assertThat(parsed.getContent()).isEqualTo("只有正文，没有来源块");
        assertThat(parsed.getEvidence()).isEmpty();
    }

    @Test
    void parse_shouldDropEntriesWithoutValidHttpUrl() {
        String raw = "正文\n```gzly-sources\n"
                + "[{\"title\":\"无链接\",\"url\":\"\"},{\"title\":\"伪造\",\"url\":\"ftp://x\"},"
                + "{\"title\":\"有效\",\"url\":\"https://www.gov.cn/a\"}]\n```";
        AiQaContentParser.ParsedReply parsed = parser.parse(raw);
        assertThat(parsed.getEvidence()).hasSize(1);
        assertThat(parsed.getEvidence().get(0).getTitle()).isEqualTo("有效");
    }

    @Test
    void parse_malformedJson_shouldBeTreatedAsNoEvidence() {
        AiQaContentParser.ParsedReply parsed = parser.parse("正文\n```gzly-sources\nnot-json\n```");
        assertThat(parsed.getContent()).isEqualTo("正文");
        assertThat(parsed.getEvidence()).isEmpty();
    }

    @Test
    void estimateTokens_shouldBePositiveForText() {
        assertThat(AiQaContentParser.estimateTokens("")).isZero();
        assertThat(AiQaContentParser.estimateTokens(null)).isZero();
        assertThat(AiQaContentParser.estimateTokens("一二三四五")).isPositive();
    }
}
