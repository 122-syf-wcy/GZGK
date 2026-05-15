package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GradientAllocationEngine {

    public Map<String, Integer> allocate(int maxVolunteerCount, String riskPreference) {
        int max = Math.max(1, maxVolunteerCount);
        // 标杆比例来自《贵州省高考志愿辅助系统推荐算法与梯度规则优化研究报告》
        // “梯度规则与伪代码”一节：均衡 9/27/18/6（60 志愿）= 15/45/30/10 比例，
        // 求稳 6/21/24/9、冲高 15/24/15/6 同样按 60 志愿基线给出。
        // 96 志愿是 60 志愿基线 ×1.6 等比放大，再做整数化与守恒补齐。
        if (max == 96) {
            if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return ordered(10, 34, 38, 14);
            if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return ordered(24, 38, 24, 10);
            return ordered(14, 43, 29, 10);
        }
        if (max == 60) {
            if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return ordered(6, 21, 24, 9);
            if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return ordered(15, 24, 15, 6);
            return ordered(9, 27, 18, 6);
        }
        double[] ratios = ratios(riskPreference);
        int rush = (int) Math.round(max * ratios[0]);
        int stable = (int) Math.round(max * ratios[1]);
        int safe = (int) Math.round(max * ratios[2]);
        int floor = Math.max(0, max - rush - stable - safe);
        int total = rush + stable + safe + floor;
        while (total > max && rush > 0) {
            rush--;
            total--;
        }
        while (total < max) {
            stable++;
            total++;
        }
        return ordered(rush, stable, safe, floor);
    }

    public AllocationResult allocateItems(List<VolunteerService.VolunteerItem> candidates,
                                          int maxVolunteerCount,
                                          String riskPreference) {
        Map<String, Integer> targets = allocate(maxVolunteerCount, riskPreference);
        List<VolunteerService.VolunteerItem> source = candidates == null ? List.of() : candidates;
        for (VolunteerService.VolunteerItem item : source) {
            item.setGradient(assignGradient(item.getChanceScore(), item.getRankDiff()));
        }
        List<VolunteerService.VolunteerItem> selected = new ArrayList<>();
        for (String gradient : List.of("冲", "稳", "保", "垫")) {
            int target = targets.getOrDefault(gradient, 0);
            source.stream()
                    .filter(item -> gradient.equals(item.getGradient()))
                    .filter(item -> !"垫".equals(gradient) || item.getDataConfidence() >= 55)
                    .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getChanceScore)
                            .thenComparing(VolunteerService.VolunteerItem::getDataConfidence).reversed())
                    .limit(target)
                    .forEach(selected::add);
        }
        if (selected.size() < maxVolunteerCount) {
            source.stream()
                    .filter(item -> !selected.contains(item))
                    .sorted(Comparator.comparingInt(VolunteerService.VolunteerItem::getChanceScore).reversed())
                    .limit(maxVolunteerCount - selected.size())
                    .forEach(selected::add);
        }
        AllocationResult result = new AllocationResult();
        result.setItems(selected.stream().limit(maxVolunteerCount).toList());
        result.setTargets(targets);
        long floorCount = selected.stream().filter(item -> "垫".equals(item.getGradient())).count();
        if (floorCount < targets.getOrDefault("垫", 0)) {
            result.getWarnings().add("兜底参考志愿数量不足，建议放宽专业、城市、学校层次或费用限制。");
        }
        return result;
    }

    private String assignGradient(int chanceScore, int rankDiff) {
        if (chanceScore >= 90 || rankDiff >= 20_000) return "垫";
        if (chanceScore >= 75 || rankDiff >= 8_000) return "保";
        if (chanceScore >= 50 || rankDiff >= 0) return "稳";
        return "冲";
    }

    private double[] ratios(String riskPreference) {
        // 与 96/60 标杆配额一致：均衡 15%/45%/30%/10%、求稳 10%/35%/40%/15%、冲高 25%/40%/25%/10%。
        if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return new double[]{0.10, 0.35, 0.40, 0.15};
        if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return new double[]{0.25, 0.40, 0.25, 0.10};
        return new double[]{0.15, 0.45, 0.30, 0.10};
    }

    private Map<String, Integer> ordered(int rush, int stable, int safe, int floor) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("冲", rush);
        map.put("稳", stable);
        map.put("保", safe);
        map.put("垫", floor);
        return map;
    }

    @Data
    public static class AllocationResult {
        private List<VolunteerService.VolunteerItem> items = List.of();
        private Map<String, Integer> targets = Map.of();
        private List<String> warnings = new ArrayList<>();
    }
}
