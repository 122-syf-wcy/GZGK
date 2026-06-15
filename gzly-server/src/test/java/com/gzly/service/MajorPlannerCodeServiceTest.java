package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorPlannerCodeServiceTest {

    private MajorPlannerCodeService service;

    @BeforeEach
    void setUp() {
        service = new MajorPlannerCodeService();
        ReflectionTestUtils.setField(service, "jwtSecret", "unit-test-secret-key-at-least-32-characters-long");
    }

    @Test
    void code_shouldHashVerifyAndMaskWithoutPlaintextLeak() {
        String code = service.generateCode();
        String hash = service.hash(code);

        assertThat(code).hasSize(12);
        assertThat(hash).startsWith("$2").doesNotContain(code);
        assertThat(service.verify(code, hash)).isTrue();
        assertThat(service.verify(service.generateCode(), hash)).isFalse();
        assertThat(service.mask(code)).startsWith(code.substring(0, 2)).endsWith(code.substring(code.length() - 2));
    }

    @Test
    void fingerprint_shouldNormalizeAndBeDeterministic() {
        assertThat(service.fingerprint("ABCD-2345-EFGH"))
                .isEqualTo(service.fingerprint(" abcd2345efgh "));
        assertThat(service.fingerprint("ABCD2345EFGH"))
                .isNotEqualTo(service.fingerprint("ZZZZ9999ZZZZ"));
    }

    @Test
    void normalize_shouldRejectInvalidCode() {
        assertThatThrownBy(() -> service.normalize("ABC")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.normalize("ABCD234!EFGH")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.normalize(null)).isInstanceOf(BizException.class);
    }
}
