package com.gzly.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;

public final class SafeUrlUtil {

    private SafeUrlUtil() {
    }

    public static String sanitizePublicUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500 || containsControlCharacter(trimmed)) {
            return "";
        }
        if (trimmed.startsWith("/uploads/")) {
            Path normalized = Path.of(trimmed).normalize();
            String normalizedValue = normalized.toString().replace('\\', '/');
            return normalizedValue.startsWith("/uploads/") ? normalizedValue : "";
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return "";
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!("http".equals(lowerScheme) || "https".equals(lowerScheme))) {
                return "";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static String requirePublicUrl(String value, String fieldName) {
        String sanitized = sanitizePublicUrl(value);
        if (sanitized.isEmpty()) {
            throw new com.gzly.common.exception.BizException(fieldName + "不是有效链接");
        }
        return sanitized;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
