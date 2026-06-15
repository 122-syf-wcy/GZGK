package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.MajorPlannerResult;
import com.gzly.mapper.MajorPlannerResultMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MajorPlannerService {

    private static final int MAX_LIST_ITEMS = 20;
    private static final String DISCLAIMER =
            "专业规划结果仅供参考，请结合个人兴趣、家庭情况、院校招生章程、选科要求和官方信息综合判断。";

    private final MajorPlannerResultMapper resultMapper;
    private final MajorPlannerCodeService codeService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public MajorPlannerView evaluate(EvaluateRequest req) {
        EvaluateRequest normalized = normalizeAndValidate(req);
        EvaluationResult result = buildEvaluation(normalized);
        String planCode = codeService.generateCode();
        MajorPlannerResult row = new MajorPlannerResult();
        row.setPlanNo(nextPlanNo());
        row.setPlanCodeHash(codeService.hash(planCode));
        row.setPlanCodeFingerprint(codeService.fingerprint(planCode));
        row.setPlanCodeMasked(codeService.mask(planCode));
        row.setProvinceCode(blankToNull(normalized.getProvinceCode()));
        row.setSubjectCategory(blankToNull(normalized.getSubjectCategory()));
        row.setScore(normalized.getScore());
        row.setProvinceRank(normalized.getRank());
        row.setAnswersJson(writeJson(normalized));
        row.setResultJson(writeJson(result));
        row.setAiSummary("");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        resultMapper.insert(row);
        return toView(row, result, planCode, true, false, "");
    }

    public MajorPlannerView restore(RestoreRequest req) {
        if (req == null || isBlank(req.getPlanCode())) {
            throw new BizException(403, "请输入规划码");
        }
        String normalizedCode = codeService.normalize(req.getPlanCode());
        String fingerprint = codeService.fingerprint(normalizedCode);
        LambdaQueryWrapper<MajorPlannerResult> wrapper = new LambdaQueryWrapper<MajorPlannerResult>()
                .eq(MajorPlannerResult::getPlanCodeFingerprint, fingerprint)
                .isNull(MajorPlannerResult::getDeletedAt)
                .orderByDesc(MajorPlannerResult::getCreatedAt)
                .last("LIMIT 1");
        if (!isBlank(req.getPlanNo())) {
            wrapper.eq(MajorPlannerResult::getPlanNo, req.getPlanNo().trim());
        }
        MajorPlannerResult row = resultMapper.selectOne(wrapper);
        if (row == null || !codeService.verify(normalizedCode, row.getPlanCodeHash())) {
            throw new BizException(403, "规划码错误或结果不存在");
        }
        return toView(row, readResult(row), null, false, false, "");
    }

    public MajorPlannerView detail(Long id, String planCode) {
        MajorPlannerResult row = requireAuthorizedRow(id, planCode);
        return toView(row, readResult(row), null, false, false, "");
    }

    public AiAnalysisResult aiAnalysis(Long id, String planCode, boolean forceRefresh) {
        MajorPlannerResult row = requireAuthorizedRow(id, planCode);
        EvaluationResult result = readResult(row);
        if (!forceRefresh && row.getAiSummary() != null && !row.getAiSummary().isBlank()) {
            return AiAnalysisResult.ok(row.getAiSummary(), false, false, "");
        }
        String summary = "";
        boolean fallback = false;
        String fallbackReason = "";
        try {
            summary = aiService.chatForMajorPlanner(buildAiContext(row, result));
        } catch (Exception e) {
            fallback = true;
            fallbackReason = "AI 服务暂时不可用";
            log.warn("专业规划 AI 解读失败: {}", e.getMessage());
        }
        if (summary == null || summary.isBlank()) {
            fallback = true;
            fallbackReason = fallbackReason.isBlank() ? "AI 服务本次没有返回有效内容" : fallbackReason;
            summary = buildRuleAiFallback(result);
        }
        row.setAiSummary(summary);
        row.setUpdatedAt(LocalDateTime.now());
        resultMapper.updateById(row);
        return AiAnalysisResult.ok(summary, true, fallback, fallbackReason);
    }

    private MajorPlannerResult requireAuthorizedRow(Long id, String planCode) {
        if (id == null || id <= 0) {
            throw new BizException(400, "规划结果 id 不能为空");
        }
        if (isBlank(planCode)) {
            throw new BizException(403, "请先输入规划码");
        }
        MajorPlannerResult row = resultMapper.selectById(id);
        if (row == null || row.getDeletedAt() != null || !codeService.verify(planCode, row.getPlanCodeHash())) {
            throw new BizException(403, "规划码错误或无权访问该结果");
        }
        return row;
    }

    private EvaluateRequest normalizeAndValidate(EvaluateRequest req) {
        if (req == null) {
            throw new BizException(400, "问卷不能为空");
        }
        EvaluateRequest normalized = new EvaluateRequest();
        normalized.setProvinceCode(trimToUpper(req.getProvinceCode()));
        normalized.setSubjectCategory(safeTrim(req.getSubjectCategory()));
        normalized.setScore(req.getScore());
        normalized.setRank(req.getRank());
        normalized.setLikedSubjects(normalizeList(req.getLikedSubjects()));
        normalized.setDislikedSubjects(normalizeList(req.getDislikedSubjects()));
        normalized.setInterestDirections(normalizeList(req.getInterestDirections()));
        normalized.setPersonalityTraits(normalizeList(req.getPersonalityTraits()));
        normalized.setCareerExpectations(normalizeList(req.getCareerExpectations()));
        normalized.setAcceptMedicine(req.getAcceptMedicine());
        normalized.setAcceptTeacher(req.getAcceptTeacher());
        normalized.setAcceptAgriculture(req.getAcceptAgriculture());
        normalized.setAcceptSinoForeign(req.getAcceptSinoForeign());
        normalized.setAcceptPrivate(req.getAcceptPrivate());
        normalized.setFamilyBudget(safeTrim(req.getFamilyBudget()));
        normalized.setCityPreferences(normalizeList(req.getCityPreferences()));
        normalized.setAvoidDirections(normalizeList(req.getAvoidDirections()));

        if (isBlank(normalized.getSubjectCategory())) {
            throw new BizException(400, "请选择选科/科类");
        }
        if (normalized.getScore() != null && (normalized.getScore() < 1 || normalized.getScore() > 750)) {
            throw new BizException(400, "分数需在1-750之间");
        }
        if (normalized.getRank() != null && normalized.getRank() < 1) {
            throw new BizException(400, "位次需大于0");
        }
        if (normalized.getLikedSubjects().isEmpty()
                && normalized.getInterestDirections().isEmpty()
                && normalized.getCareerExpectations().isEmpty()) {
            throw new BizException(400, "请至少填写喜欢的学科、兴趣方向或职业期待中的一项");
        }
        return normalized;
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : raw) {
            String value = safeTrim(item);
            if (!value.isBlank()) {
                values.add(value);
            }
            if (values.size() >= MAX_LIST_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private EvaluationResult buildEvaluation(EvaluateRequest req) {
        List<ScoredMajor> scored = new ArrayList<>();
        for (MajorProfile profile : profiles()) {
            scored.add(scoreProfile(profile, req));
        }
        scored.sort(Comparator.comparingInt(ScoredMajor::getMatchScore).reversed()
                .thenComparing(ScoredMajor::getMajorName));

        EvaluationResult result = new EvaluationResult();
        result.setDisclaimer(DISCLAIMER);
        result.setProfile(buildStudentProfile(req, scored));
        result.setRadar(scored.stream()
                .limit(8)
                .map(item -> new RadarItem(item.getCategory(), item.getMatchScore()))
                .collect(Collectors.toList()));
        result.setTopMajors(scored.stream().limit(10).collect(Collectors.toList()));
        result.setNotRecommended(scored.stream()
                .filter(item -> item.getMatchScore() < 66 || !item.getConstraintWarnings().isEmpty())
                .sorted(Comparator.comparingInt(ScoredMajor::getMatchScore))
                .limit(5)
                .map(item -> new NotRecommendedDirection(item.getMajorName(),
                        item.getAvoidReason().isBlank() ? String.join("；", item.getConstraintWarnings()) : item.getAvoidReason(),
                        item.getAdvice()))
                .collect(Collectors.toList()));
        if (result.getNotRecommended().isEmpty()) {
            result.setNotRecommended(scored.stream()
                    .skip(Math.max(0, scored.size() - 3))
                    .map(item -> new NotRecommendedDirection(item.getMajorName(),
                            "当前匹配度相对靠后，建议先作为了解对象，不要因为热门或身边建议盲目优先。",
                            item.getAdvice()))
                    .collect(Collectors.toList()));
        }
        result.setRuleSummary(buildRuleSummary(req, result));
        result.setGeneratedAt(LocalDateTime.now().toString());
        return result;
    }

    private StudentProfile buildStudentProfile(EvaluateRequest req, List<ScoredMajor> scored) {
        StudentProfile profile = new StudentProfile();
        profile.setProvinceCode(req.getProvinceCode());
        profile.setSubjectCategory(req.getSubjectCategory());
        profile.setScore(req.getScore());
        profile.setRank(req.getRank());
        profile.setLikedSubjects(req.getLikedSubjects());
        profile.setDislikedSubjects(req.getDislikedSubjects());
        profile.setInterestDirections(req.getInterestDirections());
        profile.setPersonalityTraits(req.getPersonalityTraits());
        profile.setCareerExpectations(req.getCareerExpectations());
        profile.setFamilyBudget(req.getFamilyBudget());
        profile.setCityPreferences(req.getCityPreferences());
        profile.setAvoidDirections(req.getAvoidDirections());
        profile.setMainLine(scored.isEmpty() ? "" : scored.get(0).getCategory());
        profile.setBackupLine(scored.size() < 2 ? "" : scored.get(1).getCategory());
        return profile;
    }

    private ScoredMajor scoreProfile(MajorProfile profile, EvaluateRequest req) {
        int score = 46;
        List<String> reasons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> constraints = new ArrayList<>();

        int subjectHits = overlap(req.getLikedSubjects(), profile.subjects());
        if (subjectHits > 0) {
            score += Math.min(22, subjectHits * 9);
            reasons.add("喜欢的学科与该方向基础课程匹配");
        }
        int dislikedHits = overlap(req.getDislikedSubjects(), profile.subjects());
        if (dislikedHits > 0) {
            score -= Math.min(22, dislikedHits * 9);
            risks.add("包含你不太喜欢的学科基础，学习投入感可能偏低");
        }
        int interestHits = overlap(req.getInterestDirections(), profile.interests());
        if (interestHits > 0) {
            score += Math.min(24, interestHits * 12);
            reasons.add("兴趣方向有直接对应的专业大类");
        }
        int personalityHits = overlap(req.getPersonalityTraits(), profile.personalities());
        if (personalityHits > 0) {
            score += Math.min(18, personalityHits * 8);
            reasons.add("性格倾向与该方向常见学习/工作方式较贴合");
        }
        int careerHits = overlap(req.getCareerExpectations(), profile.careers());
        if (careerHits > 0) {
            score += Math.min(20, careerHits * 8);
            reasons.add("职业期待与该方向的升学或就业路径有交集");
        }

        String subjectCategory = (req.getSubjectCategory() == null ? "" : req.getSubjectCategory()).toLowerCase(Locale.ROOT);
        boolean hasPhysics = containsAny(subjectCategory, "物理", "physics") || containsAny(req.getLikedSubjects(), "物理");
        boolean hasChemistry = containsAny(subjectCategory, "化学", "chem") || containsAny(req.getLikedSubjects(), "化学");
        boolean hasHistoryTrack = containsAny(subjectCategory, "历史", "文科");
        if (profile.needsPhysics() && !hasPhysics) {
            score -= 28;
            constraints.add("多数院校对该方向要求物理基础或物理选科，需要重点核对选科要求");
        }
        if (profile.needsChemistry() && !hasChemistry) {
            score -= 20;
            constraints.add("部分院校要求化学，需逐校核对选科要求");
        }
        if (hasHistoryTrack && profile.needsPhysics()) {
            score -= 12;
            risks.add("历史类考生可选空间通常更窄，不宜只按热门方向判断");
        }

        if (profile.medical() && Boolean.FALSE.equals(req.getAcceptMedicine())) {
            score -= 58;
            constraints.add("你已选择不接受医学类，建议不要优先放在主线");
        }
        if (profile.teacher() && Boolean.FALSE.equals(req.getAcceptTeacher())) {
            score -= 42;
            constraints.add("你已选择不接受师范方向，师范类只建议作为了解对象");
        }
        if (profile.agriculture() && Boolean.FALSE.equals(req.getAcceptAgriculture())) {
            score -= 42;
            constraints.add("你已选择不接受农林方向，农林生命方向不宜优先");
        }
        if (containsAny(req.getAvoidDirections(), profile.category(), profile.name())) {
            score -= 40;
            constraints.add("该方向命中你想避开的专业方向");
        }
        if ("低预算".equals(req.getFamilyBudget()) && profile.highCost()) {
            score -= 10;
            risks.add("学习成本或培养周期偏高，低预算家庭需要提前核算学费、实习和深造成本");
        }

        score = Math.max(35, Math.min(98, score));
        if (reasons.isEmpty()) {
            reasons.add("作为备选方向可以了解，但当前问卷信号不算强");
        }
        if (risks.isEmpty()) {
            risks.add(profile.risk());
        }
        if (!constraints.isEmpty()) {
            risks.addAll(constraints);
        }

        ScoredMajor item = new ScoredMajor();
        item.setCategory(profile.category());
        item.setMajorName(profile.name());
        item.setMatchScore(score);
        item.setReasons(reasons);
        item.setLearningContent(profile.learningContent());
        item.setSuitableFor(profile.suitableFor());
        item.setEmploymentDirections(profile.employmentDirections());
        item.setPostgraduateAndCivil(profile.postgraduateAndCivil());
        item.setRisks(risks);
        item.setAdvice(profile.advice());
        item.setSubjectRequirement(profile.subjectRequirement());
        item.setConstraintWarnings(constraints);
        item.setAvoidReason(constraints.stream().filter(s -> s.contains("避开")).findFirst().orElse(""));
        return item;
    }

    private String buildRuleSummary(EvaluateRequest req, EvaluationResult result) {
        List<ScoredMajor> top = result.getTopMajors();
        String first = top.isEmpty() ? "暂未形成明显主线" : top.get(0).getCategory();
        String second = top.size() > 1 ? top.get(1).getCategory() : "相邻专业方向";
        String avoid = result.getNotRecommended().isEmpty() ? "问卷中明确排斥的方向" : result.getNotRecommended().get(0).getDirection();
        return "你的问卷信号更偏向「" + first + "」，可以把「" + second
                + "」作为备选方向。下一步建议先查这些方向的核心课程、选科要求、招生章程和近年培养特色；对「"
                + avoid + "」建议谨慎，不要只因热门或亲友建议优先选择。" + DISCLAIMER;
    }

    private String buildRuleAiFallback(EvaluationResult result) {
        String main = result.getTopMajors().isEmpty() ? "暂未形成明显主线" : result.getTopMajors().get(0).getCategory();
        return "> 本内容由 AI 生成，仅供参考。\n\n"
                + "## 你的优势画像\n"
                + "当前问卷更适合先围绕「" + main + "」做资料收集，同时保留 1-2 个相邻方向作备选。\n\n"
                + "## 推荐主线\n"
                + result.getRuleSummary() + "\n\n"
                + "## 需要避开的坑\n"
                + "不要把专业规划等同于录取或就业承诺；不要只看专业名称热度；务必核对院校招生章程、培养方案和选科要求。\n\n"
                + "> " + DISCLAIMER;
    }

    private String buildAiContext(MajorPlannerResult row, EvaluationResult result) throws JsonProcessingException {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("task", "major_planner_analysis");
        context.put("disclaimer", DISCLAIMER);
        context.put("planNo", row.getPlanNo());
        context.put("provinceCode", row.getProvinceCode());
        context.put("subjectCategory", row.getSubjectCategory());
        context.put("score", row.getScore());
        context.put("rank", row.getProvinceRank());
        context.put("result", result);
        return objectMapper.writeValueAsString(context);
    }

    private MajorPlannerView toView(MajorPlannerResult row,
                                    EvaluationResult result,
                                    String planCode,
                                    boolean codeVisibleOnce,
                                    boolean aiGenerated,
                                    String aiFallbackReason) {
        MajorPlannerView view = new MajorPlannerView();
        view.setId(row.getId());
        view.setPlanNo(row.getPlanNo());
        view.setPlanCode(planCode);
        view.setPlanCodeMasked(row.getPlanCodeMasked());
        view.setPlanCodeVisibleOnce(codeVisibleOnce);
        view.setProvinceCode(row.getProvinceCode());
        view.setSubjectCategory(row.getSubjectCategory());
        view.setScore(row.getScore());
        view.setRank(row.getProvinceRank());
        view.setResult(result);
        view.setAiSummary(row.getAiSummary() == null ? "" : row.getAiSummary());
        view.setAiGenerated(aiGenerated);
        view.setAiFallbackReason(aiFallbackReason);
        view.setDisclaimer(DISCLAIMER);
        view.setCreatedAt(row.getCreatedAt() == null ? "" : row.getCreatedAt().toString());
        return view;
    }

    private EvaluationResult readResult(MajorPlannerResult row) {
        if (row == null || row.getResultJson() == null || row.getResultJson().isBlank()) {
            throw new BizException("规划结果不存在");
        }
        try {
            return objectMapper.readValue(row.getResultJson(), EvaluationResult.class);
        } catch (Exception e) {
            throw new BizException("规划结果解析失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("规划结果保存失败");
        }
    }

    private String nextPlanNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = codeService.generateCode().substring(0, 4);
        return "MP" + date + suffix;
    }

    private int overlap(List<String> userValues, Set<String> profileValues) {
        int count = 0;
        for (String value : userValues == null ? List.<String>of() : userValues) {
            if (containsAny(profileValues, value)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(List<String> values, String... needles) {
        return containsAny(new LinkedHashSet<>(values == null ? List.of() : values), needles);
    }

    private boolean containsAny(String value, String... needles) {
        String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            String normalizedNeedle = needle == null ? "" : needle.toLowerCase(Locale.ROOT);
            if (!normalizedNeedle.isBlank() && normalizedValue.contains(normalizedNeedle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(Set<String> values, String... needles) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                String normalizedNeedle = needle == null ? "" : needle.toLowerCase(Locale.ROOT);
                if (!normalizedNeedle.isBlank()
                        && (normalizedValue.contains(normalizedNeedle) || normalizedNeedle.contains(normalizedValue))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String trimToUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private List<MajorProfile> profiles() {
        return List.of(
                profile("计算机类", "计算机科学与技术 / 软件工程 / 人工智能", true, false, false, false, false, true,
                        subjects("数学", "英语", "物理", "信息技术"),
                        tags("技术", "研究型", "创新挑战"),
                        tags("喜欢独立研究", "喜欢创新挑战", "喜欢动手实践"),
                        tags("高薪", "就业快", "考研", "创业"),
                        "程序设计、数据结构、算法、操作系统、数据库、人工智能基础。",
                        "数学基础较好、愿意持续学习、能接受项目实践和快速变化的人。",
                        "软件开发、算法工程、数据分析、网络安全、AI 应用开发。",
                        "考研方向充足，考公岗位存在但竞争较集中。",
                        "课程强度高，行业变化快，需要长期自学。",
                        "优先看培养方案、实验条件、实习城市和计算机类分流规则。",
                        "多数院校要求物理，部分方向看数学与信息技术基础。"),
                profile("电子信息类", "电子信息工程 / 通信工程 / 集成电路", true, false, false, false, false, true,
                        subjects("数学", "物理", "信息技术"),
                        tags("技术", "研究型", "创新挑战"),
                        tags("喜欢动手实践", "喜欢独立研究"),
                        tags("高薪", "考研", "就业快"),
                        "电路、信号系统、通信原理、嵌入式、芯片与硬件基础。",
                        "物理和数学基础好，能接受硬件实验和工程训练的人。",
                        "通信设备、芯片设计、嵌入式、智能硬件、电子制造。",
                        "考研提升明显，体制内技术岗位也有一定空间。",
                        "硬件方向前期门槛较高，城市和产业链影响就业机会。",
                        "重点看学校电子信息平台、实验室、产业城市和保研考研率。",
                        "通常要求物理，部分微电子/材料交叉方向可能涉及化学。"),
                profile("数据统计类", "数据科学 / 统计学 / 信息与计算科学", true, false, false, false, false, false,
                        subjects("数学", "英语", "信息技术"),
                        tags("技术", "财经", "研究型"),
                        tags("喜欢独立研究", "喜欢稳定工作"),
                        tags("高薪", "考研", "考公"),
                        "数学分析、概率统计、建模、Python/R、数据库与机器学习基础。",
                        "数学耐心好、愿意做数据分析和模型验证的人。",
                        "数据分析、金融风控、商业分析、统计调查、算法辅助岗位。",
                        "考研路径宽，统计/应用统计/计算机交叉空间较大，考公也有统计岗位。",
                        "纯工具型岗位竞争增加，需要行业理解和项目经验。",
                        "优先选择数学统计基础扎实、允许跨计算机课程的平台。",
                        "多数院校更偏好物理或数学基础，需核对专业选科。"),
                profile("临床医学类", "临床医学 / 医学影像 / 麻醉学", false, true, false, false, false, true,
                        subjects("化学", "生物", "物理"),
                        tags("医学", "稳定", "研究型"),
                        tags("喜欢稳定工作", "喜欢独立研究", "喜欢和人打交道"),
                        tags("稳定", "考研", "体制内"),
                        "人体解剖、生理、病理、药理、临床技能和医院实习。",
                        "能接受长学制、强记忆和高责任压力的人。",
                        "医院临床、影像、麻醉、规培后专科发展。",
                        "考研/规培几乎是长期路径，体制内医疗岗位稳定但周期长。",
                        "学习周期长、压力高、地域与医院平台影响大。",
                        "务必和家庭确认培养周期、城市医院资源和身体/心理承受度。",
                        "多数医学专业要求化学，部分还要求生物。"),
                profile("口腔医学类", "口腔医学", false, true, false, false, false, true,
                        subjects("化学", "生物", "物理"),
                        tags("医学", "技术", "稳定"),
                        tags("喜欢动手实践", "喜欢和人打交道", "喜欢稳定工作"),
                        tags("高薪", "稳定", "体制内"),
                        "口腔解剖、生理、材料、修复、正畸、临床技能训练。",
                        "动手能力强、沟通耐心好、能接受医学训练的人。",
                        "口腔医院、综合医院口腔科、基层医疗、合规民营口腔机构。",
                        "升学可增强平台竞争力，公立岗位数量有限且竞争较强。",
                        "分数通常较高，培养成本和实操训练要求高。",
                        "重点核对选科、学制、实习平台和当地口腔资源。",
                        "通常要求化学，部分院校要求化学+生物。"),
                profile("师范教育类", "汉语言师范 / 数学师范 / 英语师范", false, false, true, false, false, false,
                        subjects("语文", "数学", "英语", "政治", "历史"),
                        tags("教育", "师范", "稳定"),
                        tags("喜欢和人打交道", "喜欢稳定工作"),
                        tags("稳定", "考公", "体制内", "考研"),
                        "教育学、心理学、学科教学法、课堂实践和教师技能。",
                        "表达能力好、有耐心、愿意长期与学生和家长沟通的人。",
                        "中小学教师、教培合规岗位、教育管理、学科编辑。",
                        "考编是主路径，教育硕士能提升竞争力。",
                        "地区编制供给差异大，需看本地教师招聘趋势。",
                        "优先查师范认证、实习学校、当地招聘学科和公费师范政策。",
                        "不同学科师范选科要求差异较大，逐校核对。"),
                profile("汉语言文学类", "汉语言文学 / 新闻传播 / 秘书学", false, false, false, false, false, false,
                        subjects("语文", "历史", "政治", "英语"),
                        tags("语言", "教育", "管理"),
                        tags("喜欢和人打交道", "喜欢稳定工作", "喜欢独立研究"),
                        tags("考公", "体制内", "稳定", "考研"),
                        "现代汉语、古代文学、写作、传播、文化与文本分析。",
                        "语文基础强、表达写作好、愿意做文本和沟通工作的人。",
                        "教师、编辑、新媒体、行政文秘、公共文化、机关事业单位。",
                        "考公岗位适配度较高，考研可走文学、教育、新闻传播。",
                        "就业岗位泛化，需用实习和写作作品提高识别度。",
                        "如果目标考公/师范，优先看师范属性、中文学科实力和实习机会。",
                        "多数院校不限选科，但师范或新闻传播方向需核对。"),
                profile("外国语言文学类", "英语 / 翻译 / 商务英语", false, false, false, false, false, false,
                        subjects("英语", "语文", "历史"),
                        tags("语言", "教育", "出国", "管理"),
                        tags("喜欢和人打交道", "喜欢稳定工作"),
                        tags("出国", "考研", "就业快", "考公"),
                        "语言技能、翻译、跨文化沟通、商务写作和第二外语。",
                        "英语基础好、愿意长期练口语写作、沟通表达强的人。",
                        "翻译、本地化、外贸、教育、跨境运营、国际交流。",
                        "考研可走翻译、语言学、教育；考公岗位有但竞争集中。",
                        "通用语言岗位受 AI 和复合能力影响，需要叠加行业技能。",
                        "建议辅修财经、法律、计算机或国际传播，提高复合竞争力。",
                        "多数院校不限选科，但外语单科要求需核对招生章程。"),
                profile("财经类", "会计学 / 金融学 / 财务管理 / 经济学", false, false, false, false, false, false,
                        subjects("数学", "英语", "政治"),
                        tags("财经", "管理", "稳定"),
                        tags("喜欢稳定工作", "喜欢和人打交道"),
                        tags("高薪", "考公", "体制内", "稳定"),
                        "经济学、会计、金融、统计、财务报表、税法和数据分析。",
                        "数学和细致度较好，能接受证书、实习和长期规范训练的人。",
                        "银行、会计师事务所、企业财务、审计、税务、金融运营。",
                        "考公岗位适配较多，考研和证书能增强竞争力。",
                        "热门导致竞争大，学校层次、城市和实习影响明显。",
                        "优先看财经平台、城市实习机会、CPA/ACCA/金融科技课程。",
                        "多数不限选科或偏好物理/数学基础，逐校核对。"),
                profile("法学类", "法学 / 知识产权", false, false, false, false, false, false,
                        subjects("政治", "历史", "语文", "英语"),
                        tags("法律", "管理", "稳定"),
                        tags("喜欢独立研究", "喜欢和人打交道", "喜欢稳定工作"),
                        tags("考公", "体制内", "考研", "稳定"),
                        "宪法、民法、刑法、商法、诉讼法、法理和案例训练。",
                        "阅读写作强、逻辑表达好、愿意长期备考和积累案例的人。",
                        "律师、法务、公检法司、知识产权、合规风控。",
                        "法考是关键门槛，考公和考研需求都较强。",
                        "就业分化明显，院校层次、法考和实习质量影响大。",
                        "建议关注法学学科实力、法考通过支持、实习资源和城市法律市场。",
                        "多数不限选科，但政治/历史基础有帮助。"),
                profile("管理类", "工商管理 / 公共管理 / 人力资源", false, false, false, false, false, false,
                        subjects("语文", "数学", "英语", "政治"),
                        tags("管理", "财经", "创业"),
                        tags("喜欢和人打交道", "喜欢创新挑战"),
                        tags("创业", "考公", "就业快"),
                        "管理学、组织行为、市场营销、人力资源、运营和数据基础。",
                        "沟通组织能力强、愿意做项目协调和商业实践的人。",
                        "企业运营、人力资源、市场、行政、公共服务和基层管理。",
                        "公共管理考公方向明确，工商管理更依赖实习和行业选择。",
                        "专业名称宽泛，低年级需要尽早确定细分赛道。",
                        "建议叠加会计、数据分析、法律或行业实习，避免空泛。",
                        "多数不限选科。"),
                profile("公安司法类", "公安学 / 侦查学 / 司法警务", false, false, false, false, false, false,
                        subjects("政治", "历史", "语文"),
                        tags("公安", "法律", "稳定"),
                        tags("喜欢稳定工作", "喜欢和人打交道"),
                        tags("体制内", "考公", "稳定"),
                        "公安基础、法律、侦查、治安管理、体能与纪律训练。",
                        "纪律性强、身体条件符合要求、明确体制内目标的人。",
                        "公安政法系统、基层治理、司法行政相关岗位。",
                        "多数路径和招警/公务员考试绑定，考研不是唯一主线。",
                        "体检、政审、体能和提前批规则限制明显。",
                        "务必先核对招生章程、体检政审、提前批规则和就业去向。",
                        "部分专业要求政治，具体以院校章程为准。"),
                profile("农林生命科学类", "农学 / 动物医学 / 林学 / 食品科学", false, true, false, true, false, false,
                        subjects("化学", "生物", "地理"),
                        tags("农业", "医学", "研究型"),
                        tags("喜欢动手实践", "喜欢独立研究", "喜欢稳定工作"),
                        tags("考研", "考公", "稳定"),
                        "生物、化学、作物、动物、食品、生态和实验实践。",
                        "能接受实验、田野或行业实践，对生命科学有兴趣的人。",
                        "农林科研、食品企业、动植物检疫、基层农业、宠物医疗。",
                        "考研提升明显，农林水相关公职和事业单位有岗位。",
                        "社会认知存在偏差，行业城市和平台差异较大。",
                        "不要只看专业名称，重点查细分方向、就业城市和深造比例。",
                        "常见要求化学或生物。"),
                profile("艺术设计类", "视觉传达 / 数字媒体艺术 / 产品设计", false, false, false, false, true, true,
                        subjects("语文", "英语", "信息技术"),
                        tags("艺术", "技术", "创新挑战"),
                        tags("喜欢动手实践", "喜欢创新挑战"),
                        tags("创业", "就业快", "出国"),
                        "设计基础、软件工具、视觉表达、交互、影像和项目创作。",
                        "审美表达强、愿意持续做作品集和项目迭代的人。",
                        "品牌设计、UI/UX、数字媒体、产品设计、内容创意。",
                        "考研可走设计学/艺术设计，出国看作品集质量。",
                        "作品集和城市机会很关键，民办/中外合作比例可能较高。",
                        "先确认是否有艺考/专业基础要求，再看工作室资源和作品集支持。",
                        "普通类和艺术类要求差异大，必须逐校核对。"),
                profile("基础研究类", "物理学 / 化学 / 生物科学 / 数学", true, true, true, false, false, false,
                        subjects("数学", "物理", "化学", "生物"),
                        tags("研究型", "技术"),
                        tags("喜欢独立研究", "喜欢创新挑战"),
                        tags("考研", "出国", "稳定"),
                        "高等数学、专业基础理论、实验方法、科研训练和论文阅读。",
                        "基础学科兴趣强、能接受长期深造和理论训练的人。",
                        "科研、教育、交叉工程、数据分析、医药材料等深造后方向。",
                        "通常需要考研/读博提升平台，考公岗位相对间接。",
                        "本科直接就业面较窄，需要明确深造意愿。",
                        "适合把强基、拔尖班、师范或交叉培养一起比较。",
                        "按具体学科要求物理/化学/生物，逐校核对。")
        );
    }

    private MajorProfile profile(String category, String name, boolean needsPhysics, boolean needsChemistry,
                                 boolean teacher, boolean agriculture, boolean highCost, boolean medical,
                                 Set<String> subjects, Set<String> interests, Set<String> personalities,
                                 Set<String> careers, String learningContent, String suitableFor,
                                 String employmentDirections, String postgraduateAndCivil,
                                 String risk, String advice, String subjectRequirement) {
        return new MajorProfile(category, name, needsPhysics, needsChemistry, teacher, agriculture, highCost, medical,
                subjects, interests, personalities, careers, learningContent, suitableFor, employmentDirections,
                postgraduateAndCivil, risk, advice, subjectRequirement);
    }

    private Set<String> subjects(String... values) {
        return tags(values);
    }

    private Set<String> tags(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private record MajorProfile(String category, String name, boolean needsPhysics, boolean needsChemistry,
                                boolean teacher, boolean agriculture, boolean highCost, boolean medical,
                                Set<String> subjects, Set<String> interests, Set<String> personalities,
                                Set<String> careers, String learningContent, String suitableFor,
                                String employmentDirections, String postgraduateAndCivil,
                                String risk, String advice, String subjectRequirement) {
    }

    @Data
    public static class EvaluateRequest {
        private String provinceCode;
        private String subjectCategory;
        private Integer score;
        private Integer rank;
        private List<String> likedSubjects;
        private List<String> dislikedSubjects;
        private List<String> interestDirections;
        private List<String> personalityTraits;
        private List<String> careerExpectations;
        private Boolean acceptMedicine;
        private Boolean acceptTeacher;
        private Boolean acceptAgriculture;
        private Boolean acceptSinoForeign;
        private Boolean acceptPrivate;
        private String familyBudget;
        private List<String> cityPreferences;
        private List<String> avoidDirections;
    }

    @Data
    public static class RestoreRequest {
        private String planNo;
        private String planCode;
    }

    @Data
    public static class AiAnalysisRequest {
        private String planCode;
        private Boolean forceRefresh;
    }

    @Data
    public static class MajorPlannerView {
        private Long id;
        private String planNo;
        private String planCode;
        private String planCodeMasked;
        private boolean planCodeVisibleOnce;
        private String provinceCode;
        private String subjectCategory;
        private Integer score;
        private Integer rank;
        private EvaluationResult result;
        private String aiSummary;
        private boolean aiGenerated;
        private String aiFallbackReason;
        private String disclaimer;
        private String createdAt;
    }

    @Data
    public static class EvaluationResult {
        private StudentProfile profile;
        private List<RadarItem> radar = List.of();
        private List<ScoredMajor> topMajors = List.of();
        private List<NotRecommendedDirection> notRecommended = List.of();
        private String ruleSummary;
        private String disclaimer;
        private String generatedAt;
    }

    @Data
    public static class StudentProfile {
        private String provinceCode;
        private String subjectCategory;
        private Integer score;
        private Integer rank;
        private List<String> likedSubjects = List.of();
        private List<String> dislikedSubjects = List.of();
        private List<String> interestDirections = List.of();
        private List<String> personalityTraits = List.of();
        private List<String> careerExpectations = List.of();
        private String familyBudget;
        private List<String> cityPreferences = List.of();
        private List<String> avoidDirections = List.of();
        private String mainLine;
        private String backupLine;
    }

    @Data
    public static class RadarItem {
        private String category;
        private int score;

        public RadarItem() {
        }

        public RadarItem(String category, int score) {
            this.category = category;
            this.score = score;
        }
    }

    @Data
    public static class ScoredMajor {
        private String category;
        private String majorName;
        private int matchScore;
        private List<String> reasons = List.of();
        private String learningContent;
        private String suitableFor;
        private String employmentDirections;
        private String postgraduateAndCivil;
        private List<String> risks = List.of();
        private String advice;
        private String subjectRequirement;
        private List<String> constraintWarnings = List.of();
        private String avoidReason;
    }

    @Data
    public static class NotRecommendedDirection {
        private String direction;
        private String reason;
        private String advice;

        public NotRecommendedDirection() {
        }

        public NotRecommendedDirection(String direction, String reason, String advice) {
            this.direction = direction;
            this.reason = reason == null || reason.isBlank()
                    ? "当前匹配度相对靠后，建议谨慎优先。"
                    : reason;
            this.advice = advice;
        }
    }

    @Data
    public static class AiAnalysisResult {
        private String content;
        private boolean generated;
        private boolean fallbackUsed;
        private String fallbackReason;

        public static AiAnalysisResult ok(String content, boolean generated, boolean fallbackUsed, String fallbackReason) {
            AiAnalysisResult result = new AiAnalysisResult();
            result.setContent(content);
            result.setGenerated(generated);
            result.setFallbackUsed(fallbackUsed);
            result.setFallbackReason(fallbackReason);
            return result;
        }
    }
}
