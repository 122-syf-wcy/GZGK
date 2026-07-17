package com.gzly.service;

import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Normalizes common OpenAI-compatible provider errors for admin diagnostics.
 *
 * <p>Never pass API keys or Authorization headers into this helper.</p>
 */
public final class AiProviderErrorClassifier {

    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String GROUP_NOT_ALLOWED = "GROUP_NOT_ALLOWED";
    public static final String KEY_OR_PERMISSION = "KEY_OR_PERMISSION";
    public static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
    public static final String HANDSHAKE_ERROR = "HANDSHAKE_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NO_VALID_RESPONSE = "NO_VALID_RESPONSE";
    public static final String UNSUPPORTED_ENDPOINT = "UNSUPPORTED_ENDPOINT";
    public static final String EXCEPTION = "EXCEPTION";

    private AiProviderErrorClassifier() {
    }

    public static String classifyHttp(int status, String providerCode, String body) {
        String code = trim(providerCode);
        String blob = (code + " " + trim(body)).toLowerCase(Locale.ROOT);
        if (containsAny(blob, "insufficient_balance", "insufficient balance", "余额", "balance", "quota")) {
            return INSUFFICIENT_BALANCE;
        }
        if (containsAny(blob, "group_not_allowed", "group not allowed", "分组", "无权使用", "no permission for group")) {
            return GROUP_NOT_ALLOWED;
        }
        if (containsAny(blob, "model_not_found", "model not found", "model_not_exist", "does not exist",
                "模型不存在", "模型不可用", "model unavailable")) {
            return MODEL_NOT_FOUND;
        }
        if (status == 404 || status == 405 || status == 501) {
            return UNSUPPORTED_ENDPOINT;
        }
        if (status == 401 || status == 403) {
            return KEY_OR_PERMISSION;
        }
        return code.isBlank() ? "HTTP_" + status : code;
    }

    public static String classifyException(Throwable e) {
        if (isTimeout(e)) {
            return TIMEOUT;
        }
        String blob = exceptionBlob(e);
        if (containsAny(blob, "handshake", "ssl_error", "sslconnect", "remote host terminated",
                "connection reset", "connection shutdown", "certificate", "certpath", "pkix")) {
            return HANDSHAKE_ERROR;
        }
        return EXCEPTION;
    }

    public static String userMessage(String code, Integer httpStatus, String protocol) {
        String prefix = protocol == null || protocol.isBlank() ? "AI 服务连接失败" : protocol + " 连接失败";
        if (INSUFFICIENT_BALANCE.equals(code)) {
            return prefix + "：余额不足，请充值中转账户后重试。";
        }
        if (GROUP_NOT_ALLOWED.equals(code)) {
            return prefix + "：当前账号/分组无权使用该模型，请更换模型或开通权限。";
        }
        if (KEY_OR_PERMISSION.equals(code)) {
            return prefix + "：API Key 无效、无权限或账户受限。";
        }
        if (MODEL_NOT_FOUND.equals(code)) {
            return prefix + "：模型名不可用或当前账号无权使用该模型。";
        }
        if (HANDSHAKE_ERROR.equals(code)) {
            return prefix + "：TLS 握手被服务商或网络链路中断，请检查 Base URL，或更换中转地址后重试。";
        }
        if (TIMEOUT.equals(code)) {
            return prefix + "：请求超时，请稍后重试或更换服务商线路。";
        }
        if (NO_VALID_RESPONSE.equals(code)) {
            return prefix + "：AI 未返回有效内容，请检查模型名或协议兼容性。";
        }
        if (UNSUPPORTED_ENDPOINT.equals(code)) {
            return prefix + "：该服务商可能不支持当前接口，可手动填写模型名后使用测试对话验证。";
        }
        if (httpStatus != null) {
            return prefix + "：HTTP " + httpStatus + "，请检查服务商状态、模型权限和账户配置。";
        }
        return prefix + "：请检查服务商线路、Base URL 和模型配置。";
    }

    public static boolean isAccountOrPermission(String code) {
        return INSUFFICIENT_BALANCE.equals(code)
                || GROUP_NOT_ALLOWED.equals(code)
                || KEY_OR_PERMISSION.equals(code)
                || MODEL_NOT_FOUND.equals(code);
    }

    public static boolean isUnsupportedEndpoint(String code) {
        return UNSUPPORTED_ENDPOINT.equals(code);
    }

    public static boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            String message = cur.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String exceptionBlob(Throwable e) {
        StringBuilder builder = new StringBuilder();
        Throwable cur = e;
        while (cur != null) {
            builder.append(cur.getClass().getName()).append(' ');
            if (cur.getMessage() != null) {
                builder.append(cur.getMessage()).append(' ');
            }
            cur = cur.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
