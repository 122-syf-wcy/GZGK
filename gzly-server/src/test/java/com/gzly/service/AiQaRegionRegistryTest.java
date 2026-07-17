package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQaRegionRegistryTest {

    @Test
    void launchedRegions_shouldBeExcludedFromUnlaunched() {
        for (String launched : new String[]{"GZ", "SC", "AH", "HB", "GX", "HI", "YN", "HA", "CQ", "GS", "XJ"}) {
            assertThat(AiQaRegionRegistry.isLaunched(launched)).isTrue();
            assertThat(AiQaRegionRegistry.isUnlaunched(launched)).isFalse();
            assertThat(AiQaRegionRegistry.UNLAUNCHED_REGIONS).doesNotContainKey(launched);
        }
    }

    @Test
    void unlaunchedRegions_shouldContainExpectedSamplesWithCorrectCodes() {
        assertThat(AiQaRegionRegistry.isUnlaunched("GD")).isTrue();
        assertThat(AiQaRegionRegistry.isUnlaunched("JS")).isTrue();
        assertThat(AiQaRegionRegistry.isUnlaunched("CQ")).isFalse();
        assertThat(AiQaRegionRegistry.nameOf("GD")).isEqualTo("广东");
        assertThat(AiQaRegionRegistry.nameOf("SN")).isEqualTo("陕西");
        assertThat(AiQaRegionRegistry.nameOf("HN")).isEqualTo("湖南");
        assertThat(AiQaRegionRegistry.nameOf("HE")).isEqualTo("河北");
    }

    @Test
    void normalize_shouldTrimAndUppercase() {
        assertThat(AiQaRegionRegistry.normalize(" gd ")).isEqualTo("GD");
        assertThat(AiQaRegionRegistry.normalize("js")).isEqualTo("JS");
        assertThat(AiQaRegionRegistry.normalize(null)).isEmpty();
    }

    @Test
    void unknownRegion_isNeitherLaunchedNorUnlaunched() {
        assertThat(AiQaRegionRegistry.isLaunched("ZZZ")).isFalse();
        assertThat(AiQaRegionRegistry.isUnlaunched("ZZZ")).isFalse();
        assertThat(AiQaRegionRegistry.nameOf("ZZZ")).isEmpty();
    }

    @Test
    void unlaunchedRegions_shouldCoverTwentyRegions() {
        assertThat(AiQaRegionRegistry.UNLAUNCHED_REGIONS).hasSize(20);
    }
}
