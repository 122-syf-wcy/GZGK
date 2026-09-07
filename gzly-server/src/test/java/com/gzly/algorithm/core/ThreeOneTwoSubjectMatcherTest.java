package com.gzly.algorithm.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 1 行为保真测试：严格/宽松两种既有语义必须与迁移前逐条一致，
 * 特别是两者**有意不同**的分支（"不限"匹配方式、无结构串的包含方向）。
 */
class ThreeOneTwoSubjectMatcherTest {

    @Test
    void toTrackLabel_mapsFirstSubject() {
        assertThat(ThreeOneTwoSubjectMatcher.toTrackLabel("物理")).isEqualTo("物理类");
        assertThat(ThreeOneTwoSubjectMatcher.toTrackLabel("历史")).isEqualTo("历史类");
        assertThat(ThreeOneTwoSubjectMatcher.toTrackLabel("综合")).isEqualTo("综合");
    }

    @Test
    void legacyEquivalent_isBidirectional() {
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("物理类")).isEqualTo("理科");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("历史类")).isEqualTo("文科");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("理科")).isEqualTo("物理类");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("文科")).isEqualTo("历史类");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("")).isEqualTo("");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent(null)).isEqualTo("");
        assertThat(ThreeOneTwoSubjectMatcher.legacyEquivalent("综合")).isEqualTo("综合");
    }

    @Test
    void strict_handlesAndOrChains() {
        List<String> chemBio = List.of("化学", "生物");
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学和生物", chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学和政治", chemBio)).isFalse();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学或政治", chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("政治或地理", chemBio)).isFalse();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学", chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict(null, chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学", List.of())).isFalse();
    }

    @Test
    void strict_unlimitedRequiresExactText() {
        // 贵州主链路语义："不限" 必须精确等于整串
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("不限", List.of("化学", "生物"))).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("再选不限", List.of("化学", "生物"))).isFalse();
    }

    @Test
    void lenient_unlimitedMatchesBySubstring() {
        // 专业组链路语义：包含"不限"即通过
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("不限", List.of("化学", "生物"))).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("再选不限", List.of("化学", "生物"))).isTrue();
    }

    @Test
    void plainRequirement_containmentDirectionDiffers() {
        // 无"和/或"结构时：严格语义要求列表精确包含整串；宽松语义只要求求串包含任一已选科目。
        List<String> chemBio = List.of("化学", "生物");
        assertThat(ThreeOneTwoSubjectMatcher.matchesStrict("化学,生物(2选1)", chemBio)).isFalse();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("化学,生物(2选1)", chemBio)).isTrue();
    }

    @Test
    void lenient_handlesAndOrChains() {
        List<String> chemBio = List.of("化学", "生物");
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("化学和生物", chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("化学和政治", chemBio)).isFalse();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("化学或政治", chemBio)).isTrue();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("政治或地理", chemBio)).isFalse();
        assertThat(ThreeOneTwoSubjectMatcher.matchesLenient("化学", List.of())).isFalse();
    }
}
