package com.gzly.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 统一的客户端 IP 解析组件。
 *
 * <p>安全口径：只有当直连对端 {@link #isTrustedProxy(String)} 判定为可信代理时，
 * 才采信其转发的 {@code X-Forwarded-For} / {@code X-Real-IP}；否则一律以
 * {@code request.getRemoteAddr()} 为准，避免外部请求伪造 XFF 绕过限流/风控。</p>
 *
 * <p>XFF 可能为逗号分隔的链路，取首个（最靠近客户端）地址。组件同时提供两种返回语义，
 * 以匹配各调用方既有用法：</p>
 * <ul>
 *   <li>{@link #resolve(HttpServletRequest)}：无法解析时返回 {@code null}；</li>
 *   <li>{@link #resolveOrUnknown(HttpServletRequest)}：无法解析时返回字面量 {@code "unknown"}。</li>
 * </ul>
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * 解析客户端 IP，无法解析时返回 {@code null}。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remoteAddr = request.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = remoteAddr;
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return (ip == null || ip.isBlank()) ? null : ip;
    }

    /**
     * 解析客户端 IP，无法解析时返回 {@code "unknown"}。
     */
    public static String resolveOrUnknown(HttpServletRequest request) {
        String ip = resolve(request);
        return ip == null ? "unknown" : ip;
    }

    /**
     * 判断直连对端是否为可信代理（本机回环或内网私网网段）。
     */
    public static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.")
                || remoteAddr.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
