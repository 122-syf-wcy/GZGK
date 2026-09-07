package com.gzly.algorithm.core;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 3+3 选科匹配器（海南）。无物理/历史轨道，科类轨道统一为"综合"；
 * 选科匹配判定为「专业组要求科目集合 ⊆ 考生 3 门选考科目集合」。
 *
 * <p>与 3+1+2 的差异：要求文本不区分首选/再选，一个专业组的要求可能写作
 * "物理"、"物理,化学"、"物理或化学" 等。语义规则：</p>
 * <ul>
 *   <li>空 / 含"不限" → 通过；</li>
 *   <li>含"或" → 任一要求科目在考生选科中即通过；</li>
 *   <li>其余（含"和"、顿号、逗号等分隔）→ 全部要求科目都在考生选科中才通过（保守口径）。</li>
 * </ul>
 */
@Component
public class ThreeThreeSubjectMatcher implements SubjectMatcher {

    /** 3+3 统一科类轨道标签，与一分一段/专业组表的 subject_type 存储值约定一致。 */
    public static final String TRACK_COMPREHENSIVE = "综合";

    private static final List<String> SUBJECTS = List.of("物理", "化学", "生物", "历史", "政治", "地理");

    @Override
    public SubjectMode mode() {
        return SubjectMode.THREE_THREE;
    }

    @Override
    public String resolveTrackLabel(String firstSubject, List<String> selectedSubjects) {
        return TRACK_COMPREHENSIVE;
    }

    @Override
    public boolean matchesRequirement(String requirement, List<String> candidateSubjects) {
        return matches(requirement, candidateSubjects);
    }

    /** 纯函数版本，供服务层静态调用。 */
    public static boolean matches(String requirement, List<String> candidateSubjects) {
        String req = requirement == null ? "" : requirement.trim();
        if (req.isBlank() || req.contains("不限")) {
            return true;
        }
        List<String> required = extractSubjects(req);
        if (required.isEmpty()) {
            // 要求文本里没有可识别科目（如纯说明性文字）→ 不据此排除，交由人工复核标签兜底
            return true;
        }
        if (candidateSubjects == null || candidateSubjects.isEmpty()) {
            return false;
        }
        if (req.contains("或")) {
            return required.stream().anyMatch(candidateSubjects::contains);
        }
        return candidateSubjects.containsAll(required);
    }

    private static List<String> extractSubjects(String text) {
        return SUBJECTS.stream().filter(text::contains).toList();
    }
}
