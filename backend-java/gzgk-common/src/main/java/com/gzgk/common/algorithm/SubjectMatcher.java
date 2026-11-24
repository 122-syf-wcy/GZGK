package com.gzgk.common.algorithm;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Unified 3+1+2 and 3+3 New Gaokao subject requirement matcher.
 */
public final class SubjectMatcher {

    private SubjectMatcher() {
    }

    public static boolean matches312(String firstSubject,
                                     Collection<String> resubjects,
                                     String targetSubjectType,
                                     String resubjectRequirement) {
        if (targetSubjectType != null && !targetSubjectType.isBlank() && firstSubject != null && !firstSubject.isBlank()) {
            String normFirst = firstSubject.replace("类", "").trim();
            String normTarget = targetSubjectType.replace("类", "").trim();
            if (!normTarget.equals("综合") && !normTarget.contains(normFirst)) {
                return false;
            }
        }
        return matchesResubjects(resubjects, resubjectRequirement);
    }

    public static boolean matchesResubjects(Collection<String> studentSubjects, String requirement) {
        if (requirement == null || requirement.isBlank()
                || requirement.contains("不限")
                || requirement.contains("不提")) {
            return true;
        }
        Set<String> owned = normalizeSet(studentSubjects);
        String req = requirement.replace("思想政治", "政治").replace(" ", "");

        // "化学或生物" / "2选1" / "/"
        if (req.contains("或") || req.contains("/")) {
            String[] parts = req.split("[或/]");
            for (String p : parts) {
                if (containsSubject(owned, p)) {
                    return true;
                }
            }
            return false;
        }
        // "化学和生物" / "化学+生物" / "化学,生物" -> ALL required
        String[] requiredAll = req.split("[和+、,，&]");
        for (String r : requiredAll) {
            if (!r.isBlank() && !containsSubject(owned, r)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> normalizeSet(Collection<String> subjects) {
        Set<String> out = new HashSet<>();
        if (subjects == null) {
            return out;
        }
        for (String s : subjects) {
            if (s != null && !s.isBlank()) {
                out.add(s.replace("思想政治", "政治").trim());
            }
        }
        return out;
    }

    private static boolean containsSubject(Set<String> owned, String token) {
        String clean = token.replace("必选", "").replace("选考", "").replace("均可", "").trim();
        if (clean.isEmpty() || clean.equals("不限")) {
            return true;
        }
        for (String s : owned) {
            if (clean.contains(s) || s.contains(clean)) {
                return true;
            }
        }
        return false;
    }
}
