package com.gzly.algorithm;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class CandidateFilterEngine {
    public <T> List<T> filter(List<T> candidates, Predicate<T> hardRule) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream().filter(hardRule).toList();
    }

    public FilterResult filterCandidates(FilterCriteria criteria, List<CandidatePlan> candidates) {
        List<CandidatePlan> accepted = new ArrayList<>();
        List<CandidatePlan> rejected = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            FilterResult result = new FilterResult();
            result.setAccepted(accepted);
            result.setRejected(rejected);
            return result;
        }
        FilterCriteria c = criteria == null ? new FilterCriteria() : criteria;
        for (CandidatePlan item : candidates) {
            List<String> reasons = rejectReasons(c, item);
            item.setFilterReasons(reasons);
            if (reasons.isEmpty()) {
                accepted.add(item);
            } else {
                rejected.add(item);
            }
        }
        FilterResult result = new FilterResult();
        result.setAccepted(accepted);
        result.setRejected(rejected);
        return result;
    }

    private List<String> rejectReasons(FilterCriteria c, CandidatePlan item) {
        List<String> reasons = new ArrayList<>();
        if (item == null) {
            reasons.add("候选为空");
            return reasons;
        }
        if (c.getYear() != null && item.getYear() != null && !c.getYear().equals(item.getYear())) reasons.add("年份不匹配");
        if (!blank(c.getProvince()) && !blank(item.getProvince()) && !c.getProvince().equals(item.getProvince())) reasons.add("省份不匹配");
        if (!batchEquivalent(c.getBatchCode(), item.getBatchCode())) reasons.add("批次不匹配");
        if (!blank(c.getCandidateType()) && !blank(item.getCandidateType()) && !c.getCandidateType().equals(item.getCandidateType())) reasons.add("考生类别不匹配");
        if (!blank(c.getSubjectType()) && !blank(item.getSubjectType()) && !c.getSubjectType().equals(item.getSubjectType())) reasons.add("首选科目不匹配");
        if (!subjectRequirementPass(c.getSelectedSubjects(), item.getSelectedSubjectRequirement())) reasons.add("再选科目不匹配");
        if (c.getMaxTuition() != null && item.getTuition() != null && item.getTuition() > c.getMaxTuition()) reasons.add("学费超过上限");
        if (!Boolean.TRUE.equals(c.getAcceptPrivateSchool()) && item.isPrivateSchool()) reasons.add("用户不接受民办");
        if (!Boolean.TRUE.equals(c.getAcceptChineseForeignCoop()) && item.isChineseForeignCoop()) reasons.add("用户不接受中外合作");
        if (containsAny(item.getMajorName(), c.getDislikedMajors())) reasons.add("命中排斥专业");
        if (containsAny(item.getSpecialLimit() + item.getRemarks(), c.getMedicalLimitations())) reasons.add("体检限制不匹配");
        if (!singleSubjectPass(c.getSingleSubjectScores(), item.getRemarks() + item.getSpecialLimit())) reasons.add("单科成绩限制不满足");
        if (!blank(c.getForeignLanguage()) && containsAny(item.getRemarks(), List.of("只招英语", "英语语种")) && !"英语".equals(c.getForeignLanguage())) reasons.add("外语语种限制不满足");
        if (!blank(c.getGender()) && containsAny(item.getRemarks(), List.of("只招男生", "只招女生")) && !item.getRemarks().contains(c.getGender())) reasons.add("性别限制不满足");
        if (containsAny(item.getRemarks(), List.of("专项计划", "国家专项", "地方专项")) && !containsAny("专项", c.getQualifications())) reasons.add("专项计划资格不满足");
        if (containsAny(item.getRemarks(), List.of("民族班", "预科", "定向", "免费医学", "优师计划"))
                && !containsAny(item.getRemarks(), c.getQualifications())) reasons.add("特殊资格不满足");
        if (containsAny(item.getRemarks(), c.getRejectedConditions())) reasons.add("招生备注存在明确不能接受条件");
        return reasons;
    }

    private boolean subjectRequirementPass(List<String> selectedSubjects, String requirement) {
        if (blank(requirement) || requirement.contains("不限")) return true;
        if (selectedSubjects == null || selectedSubjects.isEmpty()) return false;
        for (String token : requirement.split("[,，/、 ]+")) {
            String subject = token == null ? "" : token.trim();
            if (!subject.isEmpty() && List.of("物理", "历史", "化学", "生物", "政治", "地理").contains(subject)
                    && !selectedSubjects.contains(subject)) {
                return false;
            }
        }
        return true;
    }

    private boolean singleSubjectPass(Map<String, Integer> scores, String text) {
        if (scores == null || scores.isEmpty() || blank(text)) return true;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String subject = entry.getKey();
            Integer score = entry.getValue();
            if (subject != null && score != null && text.contains(subject) && text.matches(".*" + subject + ".{0,6}(不低于|不少于|≥|>=)\\s*\\d+.*")) {
                String digits = text.replaceAll(".*" + subject + ".{0,6}(不低于|不少于|≥|>=)\\s*(\\d+).*", "$2");
                try {
                    if (score < Integer.parseInt(digits)) return false;
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return true;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (blank(text) || keywords == null || keywords.isEmpty()) return false;
        return keywords.stream().anyMatch(k -> !blank(k) && text.contains(k.trim()));
    }

    private boolean containsAny(String text, String keyword) {
        return !blank(text) && !blank(keyword) && text.contains(keyword);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 批次等价判定：兼容 NORMAL_UNDERGRADUATE 等编码值与"普通本科批"等中文表示，
     * 与 ml-service/scripts/build_training_csv.py 中的 _batch_code() 保持反向一致。
     * 任一参数为空表示不参与比较，返回 true。
     */
    private boolean batchEquivalent(String a, String b) {
        if (blank(a) || blank(b)) return true;
        String na = normalizeBatch(a);
        String nb = normalizeBatch(b);
        if (na.equals(nb)) return true;
        // contains 双向兼容"普通本科批（统招）"等带后缀情况
        return na.contains(nb) || nb.contains(na);
    }

    private String normalizeBatch(String batch) {
        if (batch == null) return "";
        String b = batch.trim();
        return switch (b) {
            case "NORMAL_UNDERGRADUATE" -> "普通本科批";
            case "NORMAL_SPECIALTY" -> "普通专科批";
            case "EARLY_A_B" -> "本科提前批";
            case "EARLY_C" -> "专科提前批";
            default -> b;
        };
    }

    @Data
    public static class FilterCriteria {
        private Integer year;
        private String province;
        private String batchCode;
        private String candidateType;
        private String subjectType;
        private List<String> selectedSubjects;
        private Integer maxTuition;
        private Boolean acceptPrivateSchool = true;
        private Boolean acceptChineseForeignCoop = true;
        private List<String> dislikedMajors;
        private List<String> medicalLimitations;
        private Map<String, Integer> singleSubjectScores;
        private String foreignLanguage;
        private String gender;
        private List<String> qualifications;
        private List<String> rejectedConditions;
    }

    @Data
    public static class CandidatePlan {
        private Integer year;
        private String province;
        private String batchCode;
        private String candidateType;
        private String subjectType;
        private String selectedSubjectRequirement;
        private String majorName;
        private Integer tuition;
        private boolean privateSchool;
        private boolean chineseForeignCoop;
        private String remarks = "";
        private String specialLimit = "";
        private List<String> filterReasons = List.of();
    }

    @Data
    public static class FilterResult {
        private List<CandidatePlan> accepted;
        private List<CandidatePlan> rejected;
    }
}
