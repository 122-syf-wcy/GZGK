package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiQaSessionCodeServiceTest {

    private AiQaSessionCodeService service;

    @BeforeEach
    void setUp() {
        service = new AiQaSessionCodeService();
        ReflectionTestUtils.setField(service, "jwtSecret", "unit-test-secret-key-at-least-32-characters-long");
    }

    @Test
    void generateCode_shouldBeTwelveCharsFromSafeCharset() {
        String code = service.generateCode();
        assertThat(code).hasSize(12);
        assertThat(code).matches("[A-Z0-9]+");
    }

    @Test
    void hashThenVerify_shouldRoundTrip() {
        String code = service.generateCode();
        String hash = service.hash(code);
        assertThat(hash).isNotBlank().isNotEqualTo(code);
        assertThat(service.verify(code, hash)).isTrue();
        assertThat(service.verify(service.generateCode(), hash)).isFalse();
    }

    @Test
    void verify_shouldNormalizeBeforeCompare() {
        String code = "ABCD2345EFGH";
        String hash = service.hash(code);
        assertThat(service.verify(" abcd-2345-efgh ", hash)).isTrue();
    }

    @Test
    void fingerprint_shouldBeDeterministicForSameCode() {
        String code = "ABCD2345EFGH";
        assertThat(service.fingerprint(code)).isEqualTo(service.fingerprint(" abcd2345efgh "));
        assertThat(service.fingerprint(code)).isNotEqualTo(service.fingerprint("ZZZZ9999ZZZZ"));
    }

    @Test
    void normalize_shouldRejectInvalidLengthOrChars() {
        assertThatThrownBy(() -> service.normalize("ABC")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.normalize("ABCD234!EFGH")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.normalize(null)).isInstanceOf(BizException.class);
    }

    @Test
    void mask_shouldHideMiddle() {
        assertThat(service.mask("ABCD2345EFGH")).isEqualTo("AB****GH");
    }

    @Test
    void verify_withBlankHash_shouldReturnFalse() {
        assertThat(service.verify("ABCD2345EFGH", "")).isFalse();
        assertThat(service.verify("ABCD2345EFGH", null)).isFalse();
    }
}
