package com.gzly.service;

import com.gzly.service.VolunteerService.AutoRebalanceResult;
import com.gzly.service.VolunteerService.PortfolioSafety;
import com.gzly.service.VolunteerService.VolunteerItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 列表仿真自动调平测试（贵州研究报告 R7 "列表仿真自动调"）。
 *
 * <p>当 portfolioSafetyProbability 低于阈值且当前策略不是"保守型"时，
 * VolunteerService 会按 chanceScore 把参考概率较高的"冲/稳"条目升档到"保/垫"，
 * 用整张志愿表自身吸收风险。</p>
 */
class VolunteerServiceAutoRebalanceTest {

    @Test
    void shouldPromoteHighChanceItemsWhenPortfolioSafetyIsLowAndStrategyIsNotConservative() {
        List<VolunteerItem> items = newPlanWithMisclassifiedSafeItems();

        AutoRebalanceResult result = VolunteerService.autoRebalanceForPortfolioSafety(
                items, new PortfolioSafety(60.0, "需加厚保/兜底", "low", 0), "均衡型");

        assertThat(result.applied()).isTrue();
        assertThat(result.rebalancedCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.note()).contains("自动升档").contains("参考概率").contains("整张志愿表");
        assertThat(result.note()).doesNotContain("录取概率").doesNotContain("命中率");

        long dianCount = items.stream().filter(i -> "垫".equals(i.getGradient())).count();
        long baoCount = items.stream().filter(i -> "保".equals(i.getGradient())).count();
        assertThat(dianCount).isGreaterThan(0);
        assertThat(baoCount).isGreaterThan(0);

        items.stream().filter(VolunteerItem::isAutoRebalanced).forEach(item -> {
            assertThat(item.getOriginalGradient()).isNotBlank();
            assertThat(item.getRebalanceReason()).contains("chanceScore=");
            assertThat(item.getRebalanceReason()).contains("自动从");
        });
    }

    @Test
    void shouldNotPromoteWhenStrategyIsAlreadyConservative() {
        List<VolunteerItem> items = newPlanWithMisclassifiedSafeItems();

        AutoRebalanceResult result = VolunteerService.autoRebalanceForPortfolioSafety(
                items, new PortfolioSafety(60.0, "需加厚保/兜底", "low", 0), "保守型");

        assertThat(result.applied()).isFalse();
        long touched = items.stream().filter(VolunteerItem::isAutoRebalanced).count();
        assertThat(touched).isZero();
    }

    @Test
    void shouldNotPromoteWhenPortfolioSafetyIsAlreadyHigh() {
        List<VolunteerItem> items = newPlanWithMisclassifiedSafeItems();

        AutoRebalanceResult result = VolunteerService.autoRebalanceForPortfolioSafety(
                items, new PortfolioSafety(99.5, "安全垫充足", "ok", 0), "均衡型");

        assertThat(result.applied()).isFalse();
        long touched = items.stream().filter(VolunteerItem::isAutoRebalanced).count();
        assertThat(touched).isZero();
    }

    @Test
    void shouldRecordCounterAndOriginalGradientPerPromotedItem() {
        List<VolunteerItem> items = List.of(
                item("冲", 92),
                item("冲", 76),
                item("稳", 95)
        );

        AutoRebalanceResult result = VolunteerService.autoRebalanceForPortfolioSafety(
                new ArrayList<>(items), new PortfolioSafety(50.0, "low", "low", 0), "均衡型");

        assertThat(result.applied()).isTrue();
        assertThat(result.rebalancedCount()).isEqualTo(3);
    }

    /** 构造若干被错放在"冲/稳"档的高 chanceScore 条目，触发升档。 */
    private static List<VolunteerItem> newPlanWithMisclassifiedSafeItems() {
        List<VolunteerItem> items = new ArrayList<>();
        items.add(item("冲", 92));
        items.add(item("冲", 80));
        items.add(item("稳", 95));
        items.add(item("稳", 50));
        items.add(item("冲", 30));
        items.add(item("保", 78));
        items.add(item("垫", 95));
        return items;
    }

    private static VolunteerItem item(String gradient, int chanceScore) {
        VolunteerItem item = new VolunteerItem();
        item.setGradient(gradient);
        item.setChanceScore(chanceScore);
        return item;
    }
}
