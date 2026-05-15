package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchRuleRegistryTest {

    @Test
    void normalUndergraduate_shouldOnlyMatchOrdinaryUndergraduateBatch() {
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "普通本科批")).isTrue();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "普通类本科批")).isTrue();
    }

    @Test
    void normalUndergraduate_shouldNotMatchArtUndergraduateBatch() {
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "艺术类本科批")).isFalse();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "艺术本科批")).isFalse();
    }

    @Test
    void normalUndergraduate_shouldNotMatchSportsUndergraduateBatch() {
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "体育类本科批")).isFalse();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "体育本科批")).isFalse();
    }

    @Test
    void normalUndergraduate_shouldNotMatchEarlyBatch() {
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "普通类本科提前批A段")).isFalse();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_UNDERGRADUATE", "本科提前批C段")).isFalse();
    }

    @Test
    void normalSpecialty_shouldOnlyMatchOrdinarySpecialtyBatch() {
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_SPECIALTY", "普通类高职专科批")).isTrue();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_SPECIALTY", "普通专科批")).isTrue();
        assertThat(BatchRuleRegistry.batchMatches("NORMAL_SPECIALTY", "普通本科批")).isFalse();
    }

    @Test
    void earlyC_shouldNotMapToSpecialtyEarlyBatch() {
        assertThat(BatchRuleRegistry.batchMatches("EARLY_C", "普通类本科提前批C段")).isTrue();
        assertThat(BatchRuleRegistry.batchMatches("EARLY_C", "普通类高职专科提前批")).isFalse();
        assertThat(BatchRuleRegistry.batchMatches("SPECIALTY_EARLY", "普通类高职专科提前批")).isTrue();
    }

    @Test
    void earlyAB_shouldNotUseNormalUndergraduateMatch() {
        assertThat(BatchRuleRegistry.batchMatches("EARLY_A_B", "普通类本科提前批A段")).isTrue();
        assertThat(BatchRuleRegistry.batchMatches("EARLY_A_B", "普通本科批")).isFalse();
    }

    @Test
    void unsupportedBatch_shouldNotSilentlyFallbackToNormalUndergraduate() {
        assertThat(BatchRuleRegistry.find("UNKNOWN_BATCH")).isEmpty();
        assertThat(BatchRuleRegistry.normalizeBatchCode("UNKNOWN_BATCH")).isEqualTo("UNKNOWN_BATCH");
        assertThat(BatchRuleRegistry.batchMatches("UNKNOWN_BATCH", "普通本科批")).isFalse();
    }

    @Test
    void legacySpecialBatchCodes_shouldNormalizeToCanonicalCodes() {
        assertThat(BatchRuleRegistry.normalizeBatchCode("SPECIAL_NATIONAL")).isEqualTo("NATIONAL_SPECIAL");
        assertThat(BatchRuleRegistry.normalizeBatchCode("SPECIAL_LOCAL")).isEqualTo("LOCAL_SPECIAL");
        assertThat(BatchRuleRegistry.normalizeBatchCode("DIRECTED")).isEqualTo("ORIENTED");
        assertThat(BatchRuleRegistry.normalizeBatchCode("FREE_TEACHER")).isEqualTo("TEACHER_EXCELLENCE");
    }
}
