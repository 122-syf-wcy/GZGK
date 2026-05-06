package com.gzly.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeUrlUtilTest {

    @Test
    void shouldAllowHttpAndUploadUrls() {
        assertThat(SafeUrlUtil.sanitizePublicUrl("https://example.com/a.pdf")).isEqualTo("https://example.com/a.pdf");
        assertThat(SafeUrlUtil.sanitizePublicUrl("/uploads/media/file.pdf")).isEqualTo("/uploads/media/file.pdf");
    }

    @Test
    void shouldRejectScriptAndTraversalUrls() {
        assertThat(SafeUrlUtil.sanitizePublicUrl("javascript:alert(1)")).isEmpty();
        assertThat(SafeUrlUtil.sanitizePublicUrl("/uploads/../application.yml")).isEmpty();
    }
}
