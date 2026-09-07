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

    /**
     * 冲/稳/保/垫目标数量分配的唯一实现（阶段 2 消灭双实现后，
     * VolunteerService.targetCounts 与专业组链路均委派到此）。
     *
     * <p>96 / 60 为贵州口径的固定预设；其他志愿总数（专业组省份 30/40/45/48 等）
     * 按策略比例向下取整、余量全部给"垫"，保证总和恒等于 maxVolunteerCount，
     * 且兜底档不会被取整挤掉——该口径与重构前 VolunteerService 的通用分支逐值一致。</p>
     */
    public static Map<String, Integer> allocateCounts(int maxVolunteerCount, String riskPreference) {
        int max = Math.max(1, maxVolunteerCount);
        if (max == 96) {
            if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return ordered(10, 34, 34, 18);
            if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return ordered(29, 38, 19, 10);
            return ordered(19, 38, 29, 10);
        }
        if (max == 60) {
            if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return ordered(6, 21, 21, 12);
            if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return ordered(18, 24, 12, 6);
            return ordered(12, 24, 18, 6);
        }
        double[] ratios = ratios(riskPreference);
        int rush = (int) Math.floor(max * ratios[0]);
        int stable = (int) Math.floor(max * ratios[1]);
        int safe = (int) Math.floor(max * ratios[2]);
        int floor = Math.max(0, max - rush - stable - safe);
        return ordered(rush, stable, safe, floor);
    }

    public Map<String, Integer> allocate(int maxVolunteerCount, String riskPreference) {
        return allocateCounts(maxVolunteerCount, riskPreference);
    }

    /**
     * 冲/稳/保/垫的位次**比例**区间预设（相对考生位次的倍数），按策略模式返回
     * {冲,稳,保,垫} × {min,max}。此前同一组数值在 VolunteerService / BacktestService /
     * ProfessionalGroupVolunteerService 存在三份副本且注释要求"人工同步"，现收敛为单源。
     */
    public static double[][] ratioPreset(String strategyMode) {
        if ("保守型".equals(strategyMode)) {
            return new double[][]{{0.75, 0.95}, {0.95, 1.25}, {1.25, 2.00}, {2.00, 3.50}};
        }
        if ("冲刺型".equals(strategyMode)) {
            return new double[][]{{0.50, 0.95}, {0.95, 1.15}, {1.15, 1.65}, {1.65, 2.80}};
        }
        return new double[][]{{0.65, 0.95}, {0.95, 1.20}, {1.20, 1.80}, {1.80, 3.00}};
    }

    /**
     * 冲/稳/保/垫的位次**绝对偏移**区间预设（相对考生位次的名次差），
     * 与 ratioPreset 配合用于位次 > 20000 段的区间交集（贵州主链路与回测同口径）。
     */
    public static int[][] offsetPreset(String strategyMode) {
        if ("保守型".equals(strategyMode)) {
            return new int[][]{{-8000, -3000}, {-3000, 5000}, {5000, 15000}, {15000, 30000}};
        }
        if ("冲刺型".equals(strategyMode)) {
            return new int[][]{{-15000, -3000}, {-3000, 3000}, {3000, 10000}, {10000, 25000}};
        }
        return new int[][]{{-10000, -3000}, {-3000, 4000}, {4000, 12000}, {12000, 26000}};
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

    private static double[] ratios(String riskPreference) {
        if ("保守型".equals(riskPreference) || "保守".equals(riskPreference)) return new double[]{0.10, 0.35, 0.35, 0.20};
        if ("冲刺型".equals(riskPreference) || "激进".equals(riskPreference)) return new double[]{0.30, 0.40, 0.20, 0.10};
        return new double[]{0.20, 0.40, 0.30, 0.10};
    }

    private static Map<String, Integer> ordered(int rush, int stable, int safe, int floor) {
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
