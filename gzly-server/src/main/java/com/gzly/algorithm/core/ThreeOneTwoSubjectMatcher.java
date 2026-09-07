package com.gzly.algorithm.core;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 3+1+2 选科匹配器。
 *
 * <p>把此前散落在 VolunteerService / ProfessionalGroupVolunteerService / AlgorithmService
 * 的三份重复实现收敛到一处。两条链路的再选匹配语义**并不相同**，为保证阶段 1 行为不变，
 * 两种语义都保留并显式命名：</p>
 * <ul>
 *   <li>{@link #matchesStrict}：贵州主链路语义——"不限"精确匹配；无"和/或"结构时要求
 *       再选科目列表**精确包含**整个要求串；</li>
 *   <li>{@link #matchesLenient}：专业组链路语义——含"不限"即通过；无"和/或"结构时只要
 *       要求串**包含任一**已选科目名即通过。</li>
 * </ul>
 * <p>两种语义的统一（以及与 CandidateFilterEngine.subjectRequirementPass 的第三种 token
 * 语义合并）计划在阶段 2 完成，需回测护航后择一。</p>
 */
@Component
public class ThreeOneTwoSubjectMatcher implements SubjectMatcher {

    @Override
    public SubjectMode mode() {
        return SubjectMode.THREE_ONE_TWO;
    }

    @Override
    public String resolveTrackLabel(String firstSubject, List<String> selectedSubjects) {
        return toTrackLabel(firstSubject);
    }

    /** 接口默认采用严格语义（贵州主链路口径）。 */
    @Override
    public boolean matchesRequirement(String requirement, List<String> candidateSubjects) {
        return matchesStrict(requirement, candidateSubjects);
    }

    // ══════════════════ 纯函数（供既有调用点静态委派，避免构造器扩散） ══════════════════

    /** 首选科目 → 科类轨道标签。原 VolunteerService.mapSubjectType 的实现。 */
    public static String toTrackLabel(String firstSubject) {
        if ("物理".equals(firstSubject)) return "物理类";
        if ("历史".equals(firstSubject)) return "历史类";
        return firstSubject;
    }

    /**
     * 新旧高考科类互映射：物理类↔理科、历史类↔文科。
     * 原 AlgorithmService.compatibleSubjectType 的实现。
     */
    public static String legacyEquivalent(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return "";
        }
        return switch (subjectType) {
            case "物理类" -> "理科";
            case "历史类" -> "文科";
            case "理科" -> "物理类";
            case "文科" -> "历史类";
            default -> subjectType;
        };
    }

    /** 严格语义：原 VolunteerService.matchResubject 的实现，逐行等价迁移。 */
    public static boolean matchesStrict(String requirement, List<String> resubjects) {
        if (requirement == null || requirement.isBlank() || "不限".equals(requirement)) {
            return true;
        }
        if (resubjects == null || resubjects.isEmpty()) {
            return false;
        }
        String req = requirement.trim();
        if (req.contains("和")) {
            String[] parts = req.split("和");
            return Arrays.stream(parts).allMatch(p -> resubjects.contains(p.trim()));
        }
        if (req.contains("或")) {
            String[] parts = req.split("或");
            return Arrays.stream(parts).anyMatch(p -> resubjects.contains(p.trim()));
        }
        return resubjects.contains(req);
    }

    /** 宽松语义：原 ProfessionalGroupVolunteerService.matchResubject 的实现，逐行等价迁移。 */
    public static boolean matchesLenient(String requirement, List<String> resubjects) {
        String req = requirement == null ? "" : requirement.trim();
        if (req.isBlank() || req.contains("不限")) {
            return true;
        }
        if (resubjects == null || resubjects.isEmpty()) {
            return false;
        }
        if (req.contains("和")) {
            String[] parts = req.split("和");
            for (String part : parts) {
                if (!resubjects.contains(part.trim())) return false;
            }
            return true;
        }
        if (req.contains("或")) {
            String[] parts = req.split("或");
            for (String part : parts) {
                if (resubjects.contains(part.trim())) return true;
            }
            return false;
        }
        for (String subject : resubjects) {
            if (req.contains(subject)) return true;
        }
        return false;
    }
}
