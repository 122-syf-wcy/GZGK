package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 志愿方案诊断引擎，输出 13 维结构化诊断（对齐需求文档第 13 节）。
 *
 * <p>诊断维度：</p>
 * <ol>
 *   <li>梯度是否均衡（gradient distribution）</li>
 *   <li>冲刺项是否过多</li>
 *   <li>稳妥主体是否足够</li>
 *   <li>兜底参考是否不足</li>
 *   <li>专业是否过度集中</li>
 *   <li>城市是否过度集中</li>
 *   <li>学校层次是否断档</li>
 *   <li>民办 / 中外合作 / 高收费占比是否过高</li>
 *   <li>数据参考度低的项目是否过多</li>
 *   <li>计划减少的项目是否过多</li>
 *   <li>是否存在高风险项连续堆叠</li>
 *   <li>是否存在用户排斥专业</li>
 *   <li>是否违反政策最大志愿数</li>
 * </ol>
 */
@Component
public class VolunteerDiagnosisEngine {

    private static final int CONSECUTIVE_HIGH_RISK_THRESHOLD = 5;

    public Map<String, Object> diagnose(List<VolunteerService.VolunteerItem> items) {
        return diagnose(items, 96, null);
    }

    public Map<String, Object> diagnose(List<VolunteerService.VolunteerItem> items, int policyMaxCount) {
        return diagnose(items, policyMaxCount, null);
    }

    public Map<String, Object> diagnose(List<VolunteerService.VolunteerItem> items,
                                        int policyMaxCount,
                                        DiagnosisContext context) {
        List<VolunteerService.VolunteerItem> safeItems = items == null ? List.of() : items;
        DiagnosisContext ctx = context == null ? new DiagnosisContext() : context;

        Map<String, Long> counts = safeItems.stream()
                .collect(Collectors.groupingBy(item -> nullTo(item.getGradient(), "未知"), LinkedHashMap::new, Collectors.counting()));
        List<String> diagnosis = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        long rush = counts.getOrDefault("冲", 0L);
        long stable = counts.getOrDefault("稳", 0L);
        long floor = counts.getOrDefault("垫", 0L);

        // 维度 13：政策最大志愿数
        if (safeItems.size() > policyMaxCount) {
            warnings.add("方案数量超过政策最大志愿数量");
        }

        // 维度 4：兜底参考是否不足
        if (floor < Math.max(1, policyMaxCount / 12)) {
            warnings.add("兜底参考数量偏少，建议放宽专业、城市、学校层次或费用限制。");
        }

        // 维度 2：冲刺项过多
        if (rush > policyMaxCount * 0.35) {
            diagnosis.add("冲刺参考项占比较高，建议避免高风险项连续堆叠。");
        }

        // 维度 3：稳妥主体是否足够
        if (stable < policyMaxCount * 0.30) {
            diagnosis.add("稳妥参考项占比偏少，建议补充与考生位次匹配度更高的志愿作为方案主体。");
        }

        // 维度 5：专业集中度
        addConcentrationDiagnosis("专业", safeItems.stream().map(VolunteerService.VolunteerItem::getMajorName).toList(), diagnosis);
        // 维度 6：城市集中度
        addConcentrationDiagnosis("城市", safeItems.stream().map(VolunteerService.VolunteerItem::getCity).toList(), diagnosis);

        // 维度 7：学校层次断档（无任一 985/211/双一流 → 层次单一提示）
        long elite = safeItems.stream().filter(this::isEliteSchool).count();
        if (safeItems.size() >= Math.max(20, policyMaxCount / 4) && elite == 0) {
            diagnosis.add("当前方案缺少 985 / 211 / 双一流层次院校，可适当加入冲刺参考以扩展层次梯度。");
        }

        // 维度 9：数据参考度低
        long lowConfidence = safeItems.stream().filter(item -> item.getDataConfidence() > 0 && item.getDataConfidence() < 55).count();
        if (lowConfidence > policyMaxCount * 0.2) {
            diagnosis.add("数据参考度较低的项目偏多，建议优先复核专业目录和招生章程。");
        }

        // 维度 10：计划减少
        long shrunk = safeItems.stream().filter(item -> "缩招".equals(item.getPlanTrend())).count();
        if (shrunk > policyMaxCount * 0.2) {
            diagnosis.add("招生计划减少的项目偏多，建议补充计划稳定的稳妥参考项。");
        }

        // 维度 8：民办/中外合作/高收费
        long privateOrCoop = safeItems.stream().filter(item -> contains(item.getSchoolNature(), "民办") || contains(item.getMajorName(), "中外")).count();
        if (privateOrCoop > policyMaxCount * 0.2) {
            diagnosis.add("民办、中外合作或高收费项目占比较高，请结合家庭预算复核。");
        }

        // 维度 11：高风险项连续堆叠（chanceScore < 50 连续 ≥ 阈值）
        int longestRun = longestHighRiskRun(safeItems);
        if (longestRun >= CONSECUTIVE_HIGH_RISK_THRESHOLD) {
            diagnosis.add("方案中出现高风险项连续堆叠（连续 " + longestRun + " 条机会指数偏低），建议穿插稳妥参考志愿。");
        }

        // 维度 12：用户排斥专业残留
        if (ctx.getDislikedMajors() != null && !ctx.getDislikedMajors().isEmpty()) {
            List<String> hits = new ArrayList<>();
            for (String word : ctx.getDislikedMajors()) {
                if (word == null || word.isBlank()) continue;
                long c = safeItems.stream().filter(it -> contains(it.getMajorName(), word)).count();
                if (c > 0) hits.add(word + "×" + c);
            }
            if (!hits.isEmpty()) {
                warnings.add("方案中仍残留用户标记的不希望专业：" + String.join("、", hits) + "，请人工复核。");
            }
        }

        if (diagnosis.isEmpty()) {
            diagnosis.add("当前方案整体梯度较均衡，主体志愿集中在稳妥参考区间。");
        }

        warnings.add("本推荐结果仅供辅助参考，不代表最终录取结果。");
        warnings.add("请以贵州省招生考试院公布的招生计划、高校招生章程和正式录取结果为准。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", safeItems.size());
        result.put("policyMaxCount", policyMaxCount);
        result.put("gradientCount", Map.of(
                "rush", counts.getOrDefault("冲", 0L),
                "stable", counts.getOrDefault("稳", 0L),
                "safe", counts.getOrDefault("保", 0L),
                "floor", counts.getOrDefault("垫", 0L)));
        result.put("overallRisk", overallRisk(rush, floor, longestRun));
        result.put("summary", diagnosis.get(0));
        result.put("diagnosis", diagnosis);
        result.put("warnings", warnings);
        result.put("longestHighRiskRun", longestRun);
        result.put("eliteCount", elite);
        return result;
    }

    private boolean isEliteSchool(VolunteerService.VolunteerItem item) {
        if (item.getTags() == null) return false;
        return item.getTags().stream().anyMatch(t -> t != null
                && (t.contains("985") || t.contains("211") || t.contains("双一流")));
    }

    private int longestHighRiskRun(List<VolunteerService.VolunteerItem> items) {
        int longest = 0;
        int current = 0;
        for (VolunteerService.VolunteerItem item : items) {
            if (item != null && item.getChanceScore() > 0 && item.getChanceScore() < 50) {
                current++;
                if (current > longest) longest = current;
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private String overallRisk(long rush, long floor, int longestRun) {
        if (longestRun >= CONSECUTIVE_HIGH_RISK_THRESHOLD || rush > floor * 3) return "中等偏高";
        if (rush > floor) return "中等";
        return "中等偏低";
    }

    private void addConcentrationDiagnosis(String label, List<String> values, List<String> diagnosis) {
        if (values == null || values.isEmpty()) return;
        Map<String, Long> counts = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v.replaceAll("[（(].*", ""), Collectors.counting()));
        counts.entrySet().stream()
                .filter(e -> e.getValue() >= Math.max(5, values.size() * 0.25))
                .findFirst()
                .ifPresent(e -> diagnosis.add(label + "集中度较高：" + e.getKey() + "相关项目较多，建议加入相近但不同方向分散风险。"));
    }

    private boolean contains(String text, String key) {
        return text != null && key != null && text.contains(key);
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 诊断上下文，可选；为空时不触发对应维度（如用户未提供排斥专业则跳过维度 12）。
     */
    @Data
    public static class DiagnosisContext {
        /** 用户排斥专业列表（与 CandidateFilterEngine 共用语义）。 */
        private List<String> dislikedMajors;
        /** 风险偏好（保守型/均衡型/冲刺型），用于解释文案。 */
        private String riskPreference;
    }
}
