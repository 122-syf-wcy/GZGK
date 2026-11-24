package com.gzgk.common.api;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        long timestampMs
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, Instant.now().toEpochMilli());
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(0, "ok", data, traceId, Instant.now().toEpochMilli());
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, Instant.now().toEpochMilli());
    }
}
