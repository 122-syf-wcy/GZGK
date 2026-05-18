package com.gzly.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 安徽非主流程批次的「院校专业组候选 listing」服务。
 *
 * <p>非主流程批次（提前批顺序 / 高校专项顺序 / 艺术校考顺序 / 艺术体育统考综合分 / 提前批平行子类 等）
 * 当前在 {@link com.gzly.controller.VolunteerRecommendController#recommendForProfessionalGroupProvince}
 * 中走 {@link com.gzly.service.recommend.QueryOnlyRecommendEngine} 兜底骨架，items 为空。</p>
 *
 * <p>本服务与 {@link SichuanBatchListingService} 同款架构：</p>
 * <ul>
 *   <li>数据源：{@code data_admission_group_line}（按 province_code=AH + year + 批次关键词 LIKE 过滤）</li>
 *   <li>策略：按 {@link AnhuiBatchRuleRegistry#batchKeywords(String)} 拼 SQL OR LIKE</li>
 *   <li>结果：每条 VolunteerItem 含 universityName / groupCode / minRank / minScore / planCount / batch / dataSourceType</li>
 *   <li>排序：subject_type 物理类优先（与考生 firstSubject 一致），再按 minRank 升序</li>
 *   <li>空数据时返回 empty + dataQualityWarning（带公式 / 规则 / 资格审核说明）</li>
 * </ul>
 *
 * <p>边界：不出概率、不出梯度，仅作为「批次规则 + 候选清单」展示。
 * 主流程批次（{@code AH_BENKE} / {@code AH_ZHUANKE} 普通类）继续走 {@link ProfessionalGroupVolunteerService}。</p>
 */
@Service
@Slf4j
public class AnhuiBatchListingService {

    private static final int DEFAULT_LIMIT = 60;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;

    public AnhuiBatchListingService(JdbcTemplate jdbcTemplate,
                                    AdmissionYearService admissionYearService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
    }

    /**
     * 为安徽非主流程批次返回院校专业组候选列表 + dataQualityWarning。
     *
     * @param request 用户提交的生成请求
     * @param rule 安徽批次规则（来自 AnhuiBatchRuleRegistry）
     * @return 包含 items + message + warnings 的结果对象（never null）
     */
    public BatchListingResult listForBatch(VolunteerService.GenerateRequest request,
                                           AnhuiBatchRuleRegistry.BatchRule rule) {
        if (request == null || rule == null) {
            return BatchListingResult.empty("批次规则上下文缺失，无法返回候选列表");
        }
        String provinceCode = request.getProvinceCode() == null ? ProvincePolicyService.AH : request.getProvinceCode();
        int year = request.getYear() == null || request.getYear() <= 0
                ? admissionYearService.getActiveAdmissionYear() : request.getYear();
        try {
            return switch (rule.category()) {
                case EARLY, OTHER -> rule.recommendMode() == AnhuiBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                        ? listParallelEarlyBatch(rule, provinceCode, year, request)
                        : listSequentialCollegeBatch(rule, provinceCode, year, request);
                case ART, SPORTS -> listCompositeBatch(rule, provinceCode, year, request);
                case SPECIAL_PROGRAM -> listSpecialProgramBatch(rule, provinceCode, year, request);
                default -> BatchListingResult.empty(rule.supportNote());
            };
        } catch (Exception ex) {
            log.warn("[AnhuiBatchListing] listForBatch fail province={} batch={}: {}",
                    provinceCode, rule.batchCode(), ex.getMessage());
            return BatchListingResult.empty("批次查询服务暂不可用：" + ex.getMessage());
        }
    }

    /**
     * 顺序志愿批次（本科提前批司法、高校专项、高职提前批司法、艺术校考本科）：
     * 按 group_line 关键词查最近一年的院校 + 调档线，按文化分降序展示。
     */
    private BatchListingResult listSequentialCollegeBatch(AnhuiBatchRuleRegistry.BatchRule rule,
                                                          String provinceCode, int year,
                                                          VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, subjectType,
                AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, subjectType,
                        AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + " 年" + rule.batchName()
                    + "院校专业组顺序志愿候选暂未导入。\n按安徽省 2026 实施办法："
                    + rule.supportNote()
                    + "\n本批次按「根据志愿、从高分到低分、按比例投档」录取，"
                    + "调档比例原则上控制在 120% 以内；请按安徽考试院公告与高校招生章程手动核对。");
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, "院校顺序志愿");
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；本批次按顺序志愿投档，建议按文化分排序选择。",
                        items.size(), effectiveYear, rule.batchName()),
                List.of(rule.supportNote()));
    }

    /**
     * 平行志愿提前批（军公师范优师定向 6 子类合并 20 平行 / 高职提前批 4 子类合并 20 平行）：
     * 与主流程一样按位次/分数取候选，但展示口径明确「考生只能选 1 子类」。
     */
    private BatchListingResult listParallelEarlyBatch(AnhuiBatchRuleRegistry.BatchRule rule,
                                                      String provinceCode, int year,
                                                      VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, subjectType,
                AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, subjectType,
                        AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        String subTypeNote = rule.subTypes().isEmpty()
                ? ""
                : "本提前批 " + String.join(" / ", rule.subTypes()) + " 子类共用 20 个平行院校专业组，考生只能选 1 子类；"
                + "请在前端 subType 下拉中选择目标子类后再生成。";
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + " 年" + rule.batchName()
                    + "院校专业组数据暂未导入。\n" + rule.supportNote()
                    + (subTypeNote.isBlank() ? "" : "\n" + subTypeNote));
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, "院校专业组平行志愿（提前批）");
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；调档比例 105%%（省属 100%%），"
                                + "按综合分（含政策加分）位次优先、遵循志愿、一轮投档。%s",
                        items.size(), effectiveYear, rule.batchName(),
                        subTypeNote.isBlank() ? "" : "\n" + subTypeNote),
                subTypeNote.isBlank() ? List.of(rule.supportNote()) : List.of(rule.supportNote(), subTypeNote));
    }

    /**
     * 艺术 / 体育批次：按 group_line 关键词查院校专业组，附「综合分公式 / 文化线」说明。
     * 与四川综合分公式不同：安徽体育本科文化线为本科控线 ×65%。
     */
    private BatchListingResult listCompositeBatch(AnhuiBatchRuleRegistry.BatchRule rule,
                                                  String provinceCode, int year,
                                                  VolunteerService.GenerateRequest request) {
        String compositeLabel = rule.category() == AnhuiBatchRuleRegistry.CandidateCategory.ART
                ? "艺术综合分" : "体育综合分";
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, null,
                AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, null,
                        AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        String formulaNote = buildCompositeFormulaNote(rule);
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + " 年" + rule.batchName()
                    + "院校专业组数据暂未导入。\n" + formulaNote);
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, compositeLabel);
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；按%s位次（综合分优先）投档，投档比例 100%%。",
                        items.size(), effectiveYear, rule.batchName(), compositeLabel),
                List.of(rule.supportNote(), formulaNote));
    }

    /**
     * 专项 / 高校专项 等批次：按 group_line 关键词查院校 + 加资格审核提示。
     */
    private BatchListingResult listSpecialProgramBatch(AnhuiBatchRuleRegistry.BatchRule rule,
                                                       String provinceCode, int year,
                                                       VolunteerService.GenerateRequest request) {
        String subjectType = normalizeSubjectType(request);
        List<Map<String, Object>> rows = queryGroupLineByKeywords(provinceCode, year, subjectType,
                AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
        int effectiveYear = year;
        if (rows.isEmpty()) {
            for (int fb : new int[]{2025, 2024, 2023}) {
                if (fb == year) continue;
                rows = queryGroupLineByKeywords(provinceCode, fb, subjectType,
                        AnhuiBatchRuleRegistry.batchKeywords(rule.batchCode()));
                if (!rows.isEmpty()) {
                    effectiveYear = fb;
                    break;
                }
            }
        }
        String eligibilityNote = "本批次需先按安徽省教育招生考试院公布的户籍 / 学籍 / 综合素质 / 履约协议 / 农村脱贫地区认定 等条件做资格审核；"
                + "往年被专项计划录取后放弃入学资格或退学的考生，不再具有专项计划报考资格；"
                + "本系统不替代资格审核与单独投档程序，请到对应高校招生章程和安徽考试院专项公告核验。";
        if (rows.isEmpty()) {
            return BatchListingResult.empty(year + " 年" + rule.batchName()
                    + "院校专业组数据暂未导入。\n" + eligibilityNote);
        }
        List<VolunteerService.VolunteerItem> items = mapRowsToItems(rule, rows, effectiveYear, rule.batchName());
        return new BatchListingResult(items,
                String.format("已检索 %d 条 %d 年%s候选；通过资格审查后参考填报，调档比例 105%% 内。",
                        items.size(), effectiveYear, rule.batchName()),
                List.of(rule.supportNote(), eligibilityNote));
    }

    private String buildCompositeFormulaNote(AnhuiBatchRuleRegistry.BatchRule rule) {
        if (rule.category() == AnhuiBatchRuleRegistry.CandidateCategory.SPORTS) {
            return "安徽 2026 体育综合分参考口径（按《2025 年实施办法》第 27 条）：\n"
                    + "- 体育类本科文化课录取控制分数线 = 普通类本科录取控制分数线 × 65%（分历史 / 物理）；\n"
                    + "- 体育类高职专科文化课录取控制分数线 = 普通高职专科批控制分数线；\n"
                    + "- 投档按「综合分优先，遵循志愿」，投档比例 100%；\n"
                    + "- 综合分具体公式以 2026 年安徽省体育类考试招生工作通知为准（6 月下旬公布前以 2025 同款公式预览）。";
        }
        if (rule.batchCode().equals("AH_ART_TONGKAO_BENKE")) {
            return "安徽 2026 艺术类统考本科批投档要点（按《2025 年实施办法》第 26 条）：\n"
                    + "- 投档按「综合分优先，遵循志愿」，投档比例 100%；\n"
                    + "- 播音与主持类、表（导）演类、美术与设计类分 A、B 段录取（条件考生可兼报 A/B）；\n"
                    + "- 音乐类乐器主副项 / 声器乐特殊要求的可单独投档（1 专业组 1 专业志愿），再进行平行投档；\n"
                    + "- 综合分公式以 2026 年安徽省艺术类专业招生考试工作通知（皖招委文件）为准。";
        }
        if (rule.batchCode().equals("AH_ART_TONGKAO_ZHUANKE")) {
            return "安徽 2026 艺术类统考高职（专科）批：使用省统考专业成绩录取，"
                    + "投档按「综合分优先，遵循志愿」，投档比例 100%；考生在每个批次只能选择报考一个艺术类别的志愿。";
        }
        return rule.supportNote();
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
            log.warn("[AnhuiBatchListing] queryGroupLineByKeywords fail: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<VolunteerService.VolunteerItem> mapRowsToItems(AnhuiBatchRuleRegistry.BatchRule rule,
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
            item.setProvince("安徽");
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
                    "%s 候选 | %d 年安徽 %s 已检索 | 风险：%s，需结合官方目录与资格审核 | "
                            + "调节：按安徽考试院最新公告复核",
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
