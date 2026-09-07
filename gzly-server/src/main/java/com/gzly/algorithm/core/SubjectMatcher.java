package com.gzly.algorithm.core;

import java.util.List;

/**
 * 选科匹配器（统一管线可插拔点之二，docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 4.4）。
 * 3+1+2 与 3+3 的差异全部收敛在实现内，管线不感知省份。
 */
public interface SubjectMatcher {

    SubjectMode mode();

    /**
     * 解析考生的科类轨道标签。
     * 3+1+2：物理→物理类 / 历史→历史类；3+3：统一返回"综合"。
     */
    String resolveTrackLabel(String firstSubject, List<String> selectedSubjects);

    /**
     * 考生选科是否满足候选的选科要求。
     * 3+1+2 传再选科目（2 门）；3+3 传全部选考科目（3 门）。
     */
    boolean matchesRequirement(String requirement, List<String> candidateSubjects);
}
