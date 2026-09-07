package com.gzly.algorithm.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3+3（海南）选科匹配语义：要求科目集合 ⊆ 考生 3 门选科；"或"任一命中；"不限"放行。
 */
class ThreeThreeSubjectMatcherTest {

    private final ThreeThreeSubjectMatcher matcher = new ThreeThreeSubjectMatcher();

    @Test
    void trackLabelIsComprehensive() {
        assertThat(matcher.mode()).isEqualTo(SubjectMode.THREE_THREE);
        assertThat(matcher.resolveTrackLabel(null, List.of("物理", "化学", "生物"))).isEqualTo("综合");
    }

    @Test
    void allRequiredSubjectsMustBeSelected() {
        List<String> selected = List.of("物理", "化学", "生物");
        assertThat(ThreeThreeSubjectMatcher.matches("物理", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches("物理,化学", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches("物理和化学", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches("物理,历史", selected)).isFalse();
        assertThat(ThreeThreeSubjectMatcher.matches("政治", selected)).isFalse();
    }

    @Test
    void orRequirementMatchesAny() {
        List<String> selected = List.of("物理", "化学", "生物");
        assertThat(ThreeThreeSubjectMatcher.matches("物理或历史", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches("政治或地理", selected)).isFalse();
    }

    @Test
    void unlimitedOrUnparseablePasses() {
        List<String> selected = List.of("物理", "化学", "生物");
        assertThat(ThreeThreeSubjectMatcher.matches("不限", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches("", selected)).isTrue();
        assertThat(ThreeThreeSubjectMatcher.matches(null, selected)).isTrue();
        // 无可识别科目的说明性文字不据此排除（交人工复核标签兜底）
        assertThat(ThreeThreeSubjectMatcher.matches("详见招生章程", selected)).isTrue();
    }

    @Test
    void emptySelectionFailsConcreteRequirement() {
        assertThat(ThreeThreeSubjectMatcher.matches("物理", List.of())).isFalse();
        assertThat(ThreeThreeSubjectMatcher.matches("物理", null)).isFalse();
    }
}
