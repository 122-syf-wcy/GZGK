package com.gzly.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 四川（以及 HB/AH 等 PROFESSIONAL_GROUP_45 省份）非主流程批次的"院校专业组候选 listing"服务。
 *
 * <p>非主流程批次（艺术 / 体育 / 顺序志愿 / 8 类专项 + 高水平运动队）当前在
 * {@link com.gzly.controller.VolunteerRecommendController#recommendForProfessionalGroupProvince}
 * 中走 {@link com.gzly.service.recommend.QueryOnlyRecommendEngine} 兜底，items 为空。</p>
 *
 * <p>本服务等价于贵州 {@link BatchListingRecommendationService} 的精简版：</p>
 * <ul>
 *   <li>数据源：{@code data_admission_group_line}（与 SC 主流程同表，按批次关键词 LIKE 过滤）</li>
 *   <li>策略：按 {@link SichuanBatchRuleRegistry.BatchRule#aliases()} + batchName 拼 SQL OR LIKE</li>
 *   <li>结果：每条 VolunteerItem 含 universityName / groupCode / minRank / minScore / planCount / batch / dataSourceType</li>
 *   <li>排序：按 subject_type 物理类优先（与考生 firstSubject 一致），再按 minRank 升序 → 历史录取从高到低</li>
 *   <li>当 SC 该批次 0 行时返回 emptyResult + 显式 dataQualityWarning</li>
 * </ul>
 *
 * <p>边界：本服务**不出概率、不出梯度**（不是主链路），仅作为"批次规则 + 候选清单"展示。
 * 主流程批次（SC_BENKE_B 普通类）继续走 {@link ProfessionalGroupVolunteerService}。</p>
 */
@Service
@Slf4j
public class SichuanBatchListingService {

    private static final int DEFAULT_LIMIT = 60;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;

    public SichuanBatchListingService(JdbcTemplate jdbcTemplate,
                                       AdmissionYearService admissionYearService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
    }

    /**
     * 为四川非主流程批次返回院校专业组候选列表 + dataQualityWarning。
     *
     * @param request 用户提交的生成请求
     * @param rule 四川批次规则（来自 SichuanBatchRuleRegistry）
     * @return 包含 items + message + warnings 的结果对象（never null）
     */
    public BatchListingResult listForBatch(VolunteerService.GenerateRequest request,
                                           SichuanBatchRuleRegistry.BatchRule rule) {
        if (request == null || rule == null) {
            return BatchListingResult.empty("批次规则上下文缺失，无法返回候选列表");
        }
        String provinceCode = request.getProvinceCode() == null ? ProvincePolicyService.SC : request.getProvinceCode();
        int year = request.getYear() == null || request.getYear() <= 0
                ? admissionYearService.getActiveAdmissionYear() : request.getYear();
        try {
            return switch (rule.category()) {
                case EARLY, OTHER -> listSequentialCollegeBatch(rule, provinceCode, year, request);
                case ART, SPORTS -> listCompositeBatch(rule, provinceCode, year, request);
                case SPECIAL_PROGRAM -> listSpecialProgramBatch(rule, provinceCode, year, request);
                default -> BatchListingResult.empty(rule.supportNote());
            };
        } catch (Exception ex) {
            log.warn("[SichuanBatchListing] listForBatch fail province={} batch={}: {}",
                    provinceCode, rule.batchCode(), ex.getMessage());
            return BatchListingResult.empty("批次查询服务暂不可用：" + ex.getMessage());
        }
    }

    /**
     * 顺序志愿批次（提前 A、提前批高校专项、高水平运动队、专科提前、艺术本科提前）：
     * 按 group_line 关键词查最近一年的院校 + 调档线，文化分降序作为顺序志愿建议。
     */
    private BatchListingResult listSequentialCollegeBatch(SichuanBatchRuleRegistry.BatchRule rule,
                                                          String provinceCode, int year,
                                                          VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, subjectType,
                SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, subjectType,
                        SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName()
                    + "院校专业组顺序志愿候选暂未导入。\n按四川省 2026 实施规定："
                    + rule.supportNote() + "\n本批次按\"根据志愿、从高分到低分、按比例投档\"录取，请按四川考试院公告与高校招生章程手动核对。");
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, "院校顺序志愿");
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；本批次按顺序志愿投档，请按文化分排序选择。",
                        items.size(), effectiveYear, rule.batchName()),
                List.of(rule.supportNote()));
    }

    /**
     * 艺术 / 体育批次：按 group_line 关键词查院校专业组，附"综合分公式"说明。
     * 与贵州不同：四川艺体走 PARALLEL_GROUP 综合分平行志愿（45 院校专业组）。
     */
    private BatchListingResult listCompositeBatch(SichuanBatchRuleRegistry.BatchRule rule,
                                                  String provinceCode, int year,
                                                  VolunteerService.GenerateRequest request) {
        String compositeLabel = rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ART
                ? "艺术综合分" : "体育综合分";
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, null,
                SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, null,
                        SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        String formulaNote = rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ART
                ? "四川艺术综合分公式（教育考试院 2026-05 公告）：\n"
                + "- 美术/设计/戏剧编导/表演/导演/服装表演/播音：综合 = 文化×50% + 统考×(750/300)×50%\n"
                + "- 音乐表演/音乐教育/舞蹈/书法/航空服务艺术：综合 = 文化×30% + 统考×(750/300)×70%\n"
                + "前端表单已提供综合分实时预览（候选类别下拉），最终录取按高校招生章程为准。"
                : "四川体育综合分公式（2026 实施规定）：\n"
                + "综合 = 文化×30% + 体育统考×(750/100)×70%\n"
                + "体育平行志愿按统考成绩排序投档（文化、专业双达线后）。前端表单已提供综合分实时预览。";
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName()
                    + "院校专业组数据暂未导入。\n" + formulaNote);
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, compositeLabel);
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；按%s位次投档，公式见说明。",
                        items.size(), effectiveYear, rule.batchName(), compositeLabel),
                List.of(rule.supportNote(), formulaNote));
    }

    /**
     * 专项 / 高水平运动队 / 区域均衡 / 民族预科 / 高校专项 等批次：
     * 按 group_line 关键词查院校 + 加资格审核提示。
     */
    private BatchListingResult listSpecialProgramBatch(SichuanBatchRuleRegistry.BatchRule rule,
                                                       String provinceCode, int year,
                                                       VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, subjectType,
                SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, subjectType,
                        SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        String eligibilityNote = "本批次需先按四川省教育考试院公布的户籍 / 学籍 / 综合素质 / 履约协议等条件做资格审核；"
                + "本系统不替代资格审核与单独投档程序，请到对应高校招生章程和四川省教育考试院专项公告核验。";
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + "年" + rule.batchName()
                    + "院校专业组数据暂未导入。\n" + eligibilityNote);
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, rule.batchName());
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；通过资格审查后参考填报。",
                        items.size(), effectiveYear, rule.batchName()),
                List.of(rule.supportNote(), eligibilityNote));
    }

    /**
     * 按批次关键词在 data_admission_group_line 中 OR LIKE 匹配。
     */
    private List<Map<String, Object>> queryGroupLineByKeywords(String provinceCode, int year, String subjectType,
                                                                List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        List<String> validKeywords = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
        if (validKeywords.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT school_id, university_name, group_code, group_name, subject_type, "
                        + "first_subject_requirement, resubject_requirement, "
                        + "min_score, min_rank, plan_count, batch, "
                        + "source_name, source_url, source_page_url, source_level, year "
                        + "FROM data_admission_group_line "
                        + "WHERE province_code = ? AND year = ? AND (");
        List<Object> params = new ArrayList<>();
        params.add(provinceCode);
        params.add(year);
        boolean first = true;
        for (String kw : validKeywords) {
            if (!first) sql.append(" OR ");
            sql.append("batch LIKE ?");
            params.add("%" + kw + "%");
            first = false;
        }
        sql.append(")");
        if (subjectType != null && !subjectType.isBlank()) {
            sql.append(" AND subject_type = ?");
            params.add(subjectType);
        }
        sql.append(" ORDER BY ");
        if (subjectType != null && !subjectType.isBlank()) {
            sql.append("min_rank ASC");
        } else {
            sql.append("subject_type, min_rank ASC");
        }
        sql.append(" LIMIT ?");
        params.add(DEFAULT_LIMIT);
        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            log.warn("[SichuanBatchListing] queryGroupLineByKeywords fail: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<VolunteerService.VolunteerItem> mapRowsToItems(SichuanBatchRuleRegistry.BatchRule rule,
                                                                 List<Map<String, Object>> rows,
                                                                 int year, String dataSourceLabel) {
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> row : rows) {
            VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
            item.setIndex(index++);
            item.setUniversityName(stringValue(row, "university_name"));
            item.setSchoolId(stringValue(row, "school_id"));
            item.setGroupCode(stringValue(row, "group_code"));
            item.setGroupName(stringValueOrDefault(row, "group_name", stringValue(row, "group_code")));
            item.setMajorName(stringValueOrDefault(row, "group_name", stringValue(row, "group_code")));
            item.setProvince("四川");
            item.setGradient("查询参考");
            item.setReferenceYear(intValueOrDefault(row, "year", year));
            item.setHistoryMinScore(intValueOrDefault(row, "min_score", 0));
            item.setHistoryMinRank(intValueOrDefault(row, "min_rank", 0));
            item.setLatestPlanCount(intValueOrDefault(row, "plan_count", 0));
            item.setResubjectRequirement(stringValueOrDefault(row, "resubject_requirement", "需复核"));
            item.setSubjectRequirementSource("official_group");
            item.setDataSourceType("院校专业组（" + dataSourceLabel + "）");
            item.setProbLevel("参考匹配");
            item.setAdmissionProb(0);
            item.setRiskLevel("需复核");
            item.setRiskColor("yellow");
            item.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
            item.setVolunteerUnitLabel("院校专业组");
            item.setConfidenceLabel("需复核");
            item.setNeedsManualReview(true);
            item.setReviewFlags(List.of("non_main_pipeline_listing"));
            item.setRecommendReason(String.format(
                    "%s 候选 | %d 年四川 %s 已检索 | 风险：%s，需结合官方目录与资格审核 | 调节：按四川考试院最新公告复核",
                    rule.batchName(), item.getReferenceYear(), rule.batchName(), rule.supportNote()));
            item.setRiskReason(rule.supportNote());
            item.setSuitableFor("符合" + rule.batchName() + "报考条件的考生");
            item.setMajorCatalogUrl(stringValue(row, "source_url"));
            item.setAdmissionSiteUrl(stringValue(row, "source_page_url"));
            items.add(item);
        }
        return items;
    }

    private String normalizeSubjectType(VolunteerService.GenerateRequest request) {
        if (request == null) return null;
        String firstSubject = request.getFirstSubject();
        if (firstSubject == null || firstSubject.isBlank()) return null;
        return "历史".equals(firstSubject.trim()) ? "历史类" : "物理类";
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }

    private static String stringValueOrDefault(Map<String, Object> row, String key, String fallback) {
        String v = stringValue(row, key);
        return v.isBlank() ? fallback : v;
    }

    private static int intValueOrDefault(Map<String, Object> row, String key, int fallback) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Data
    public static class BatchListingResult {
        private final List<VolunteerService.VolunteerItem> items;
        private final String message;
        private final List<String> warnings;

        public static BatchListingResult empty(String message) {
            return new BatchListingResult(List.of(), message, List.of());
        }
    }
}
