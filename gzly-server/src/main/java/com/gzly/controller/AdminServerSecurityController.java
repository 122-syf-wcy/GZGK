package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.SecurityAuditCounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台服务器安全防护状态。
 *
 * <p>路径由 {@code /admin/**} AuthInterceptor 保护；本控制器只做只读采集，
 * 不返回 authorized_keys 内容、密钥、环境变量或日志原文。</p>
 */
@RestController
@RequestMapping("/admin/security/server")
public class AdminServerSecurityController {

    private static final Path ROOT_SSH_DIR = Path.of("/root/.ssh");
    private static final Path AUTHORIZED_KEYS = ROOT_SSH_DIR.resolve("authorized_keys");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    @Autowired(required = false)
    private SecurityAuditCounterService securityAuditCounterService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> sshd = sshdConfig();
        Map<String, Object> keyFile = authorizedKeysStatus();
        Map<String, Object> fail2ban = fail2banStatus();
        Map<String, Object> firewall = firewallStatus();
        Map<String, Object> attacks = attackStatus();
        Map<String, Object> metrics = securityMetrics();
        List<Map<String, Object>> recommendations = recommendations(sshd, keyFile, fail2ban, firewall, metrics);

        String overallLevel = overallLevel(sshd, keyFile, fail2ban, firewall, metrics);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkedAt", LocalDateTime.now().toString());
        data.put("overallLevel", overallLevel);

        data.put("sshPasswordAuthentication", sshd.getOrDefault("passwordauthentication", "unknown"));
        data.put("sshPubkeyAuthentication", sshd.getOrDefault("pubkeyauthentication", "unknown"));
        data.put("sshPermitRootLogin", sshd.getOrDefault("permitrootlogin", "unknown"));
        data.put("sshKbdInteractiveAuthentication", sshd.getOrDefault("kbdinteractiveauthentication", "unknown"));
        data.put("sshChallengeResponseAuthentication", sshd.getOrDefault("challengeresponseauthentication", "unknown"));

        data.put("authorizedKeysExists", keyFile.get("exists"));
        data.put("authorizedKeysNotEmpty", keyFile.get("notEmpty"));
        data.put("authorizedKeysPermissionOk", keyFile.get("filePermissionOk"));
        data.put("sshDirPermissionOk", keyFile.get("dirPermissionOk"));
        data.put("authorizedKeysOwnerOk", keyFile.get("ownerOk"));

        data.put("fail2banActive", fail2ban.get("active"));
        data.put("fail2banSshdActive", fail2ban.get("sshdActive"));
        data.put("bannedIps", fail2ban.get("bannedIps"));
        data.put("bannedIpCount24h", attacks.get("fail2banBanCount24h"));

        data.put("firewalldActive", firewall.get("firewalldActive"));
        data.put("nftablesActive", firewall.get("nftablesActive"));
        data.put("firewallActive", firewall.get("firewallActive"));
        data.put("openPorts", firewall.get("openPorts"));
        data.put("publicListeningPorts", firewall.get("publicListeningPorts"));
        data.put("firewallAllowedPorts", firewall.get("firewallAllowedPorts"));
        data.put("requiredPortsOk", firewall.get("requiredPortsOk"));
        data.put("unexpectedPublicPorts", firewall.get("unexpectedPublicPorts"));
        data.put("blockedByFirewallPorts", firewall.get("blockedByFirewallPorts"));
        data.put("nginxLimitReqEnabled", firewall.get("nginxLimitReqEnabled"));

        data.put("loginFailCount24h", metrics.get("adminLoginFailures"));
        data.put("credentialFailCount24h", metrics.get("credentialFailures"));
        data.put("apiRateLimitCount24h", metrics.get("rateLimitTriggers"));
        data.put("nginx5xx24h", metrics.get("nginx5xx24h"));
        data.put("appError24h", metrics.get("appError24h"));
        data.put("sensitiveLogHitCount", metrics.get("sensitiveLogHitCount"));

        data.put("sshFailedIpTop10", attacks.get("sshFailedIpTop10"));
        data.put("fail2banRecentBans", attacks.get("fail2banRecentBans"));
        data.put("rateLimitTopEndpoints", attacks.get("rateLimitTopEndpoints"));
        data.put("recommendations", recommendations);

        data.put("sections", Map.of(
                "sshd", sshd,
                "authorizedKeys", keyFile,
                "fail2ban", fail2ban,
                "firewall", firewall,
                "attacks", attacks,
                "metrics", metrics
        ));
        return Result.ok(data);
    }

    private Map<String, Object> sshdConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        CommandResult result = run("sshd -T | egrep '^(port|passwordauthentication|pubkeyauthentication|permitrootlogin|kbdinteractiveauthentication|challengeresponseauthentication)'");
        map.put("commandOk", result.ok());
        map.put("error", result.ok() ? "" : result.safeError());
        for (String line : result.lines()) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2) {
                map.put(parts[0].toLowerCase(Locale.ROOT), parts[1].trim());
            }
        }
        return map;
    }

    private Map<String, Object> authorizedKeysStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean dirExists = Files.isDirectory(ROOT_SSH_DIR);
        boolean fileExists = Files.isRegularFile(AUTHORIZED_KEYS);
        long size = size(AUTHORIZED_KEYS);
        map.put("dirExists", dirExists);
        map.put("exists", fileExists);
        map.put("notEmpty", fileExists && size > 0);
        map.put("sizeBytes", fileExists ? size : 0L);
        map.put("dirPermission", permissions(ROOT_SSH_DIR));
        map.put("filePermission", permissions(AUTHORIZED_KEYS));
        map.put("dirPermissionOk", "700".equals(permissions(ROOT_SSH_DIR)));
        map.put("filePermissionOk", "600".equals(permissions(AUTHORIZED_KEYS)));
        map.put("ownerOk", ownerIsRoot(ROOT_SSH_DIR) && ownerIsRoot(AUTHORIZED_KEYS));
        return map;
    }

    private Map<String, Object> fail2banStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        CommandResult active = run("systemctl is-active fail2ban 2>/dev/null || true");
        CommandResult sshd = run("fail2ban-client status sshd 2>/dev/null || true");
        List<String> bannedIps = new ArrayList<>();
        int totalBanned = 0;
        int currentBanned = 0;
        for (String line : sshd.lines()) {
            String normalized = line.trim();
            if (normalized.contains("Currently banned:")) {
                currentBanned = parseIntAfterColon(normalized);
            } else if (normalized.contains("Total banned:")) {
                totalBanned = parseIntAfterColon(normalized);
            } else if (normalized.contains("Banned IP list:")) {
                Matcher matcher = IPV4.matcher(normalized);
                while (matcher.find() && bannedIps.size() < 20) {
                    bannedIps.add(matcher.group());
                }
            }
        }
        map.put("active", active.stdout().trim().equals("active"));
        map.put("sshdActive", sshd.stdout().contains("Status for the jail: sshd"));
        map.put("currentBanned", currentBanned);
        map.put("totalBanned", totalBanned);
        map.put("bannedIps", bannedIps);
        map.put("rawAvailable", sshd.ok());
        return map;
    }

    private Map<String, Object> firewallStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        Set<Integer> openPorts = parseListeningPorts(false);
        Set<Integer> publicListeningPorts = parseListeningPorts(true);
        Set<Integer> firewallAllowed = parseFirewallAllowedPorts();
        Set<Integer> required = new TreeSet<>(Set.of(22, 80, 443));
        Set<Integer> unexpectedPublic = new TreeSet<>(firewallAllowed);
        unexpectedPublic.removeAll(required);
        Set<Integer> blockedByFirewall = new TreeSet<>(publicListeningPorts);
        blockedByFirewall.removeAll(firewallAllowed);

        boolean hardenChain = run("iptables -S GZLY-HARDEN-INPUT >/dev/null 2>&1").ok();
        boolean requiredPortsOk = firewallAllowed.containsAll(required);
        boolean nginxLimitReqEnabled = run("grep -R \"limit_req\" -n /etc/nginx/conf.d /etc/nginx/nginx.conf 2>/dev/null | grep -q \"gzly_\"").ok();

        map.put("firewalldActive", run("systemctl is-active firewalld 2>/dev/null || true").stdout().trim().equals("active"));
        map.put("nftablesActive", run("systemctl is-active nftables 2>/dev/null || true").stdout().trim().equals("active"));
        map.put("iptablesHardenChain", hardenChain);
        map.put("firewallActive", hardenChain || Boolean.TRUE.equals(map.get("firewalldActive")) || Boolean.TRUE.equals(map.get("nftablesActive")));
        map.put("openPorts", new ArrayList<>(openPorts));
        map.put("publicListeningPorts", new ArrayList<>(publicListeningPorts));
        map.put("firewallAllowedPorts", new ArrayList<>(firewallAllowed));
        map.put("requiredPortsOk", requiredPortsOk);
        map.put("unexpectedPublicPorts", new ArrayList<>(unexpectedPublic));
        map.put("blockedByFirewallPorts", new ArrayList<>(blockedByFirewall));
        map.put("nginxLimitReqEnabled", nginxLimitReqEnabled);
        return map;
    }

    private Map<String, Object> securityMetrics() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("adminLoginFailures", countAudit(SecurityAuditCounterService.ADMIN_LOGIN_FAILURE));
        map.put("credentialFailures", countAudit(SecurityAuditCounterService.CREDENTIAL_FAILURE));
        map.put("rateLimitTriggers", countAudit(SecurityAuditCounterService.RATE_LIMIT));
        map.put("adminForbidden", countAudit(SecurityAuditCounterService.ADMIN_FORBIDDEN));
        long app5xxCounter = countAudit(SecurityAuditCounterService.APP_5XX);
        long appErrorLog = countLong("journalctl -u gzly --since '24 hours ago' --no-pager 2>/dev/null | grep -Eic 'ERROR|SQLSyntax|Unknown column' || true");
        map.put("appError24h", Math.max(app5xxCounter, appErrorLog));
        map.put("nginx5xx24h", countLong("awk '$9 ~ /^5/ {c++} END{print c+0}' /var/log/nginx/access.log /var/log/nginx/access_gzly.log 2>/dev/null"));
        map.put("sensitiveLogHitCount", countLong("journalctl -u gzly --since '24 hours ago' --no-pager 2>/dev/null | grep -Eic 'Authorization:|Bearer |api[_-]?key|accessCode|planCode|conversationCode|password=' || true"));
        return map;
    }

    private Map<String, Object> attackStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sshFailedIpTop10", sshFailedTop());
        map.put("fail2banRecentBans", recentBans());
        map.put("fail2banBanCount24h", countLong("journalctl -u fail2ban --since '24 hours ago' --no-pager 2>/dev/null | grep -Ec ' Ban ' || true"));
        map.put("rateLimitTopEndpoints", rateLimitTopEndpoints());
        return map;
    }

    private List<Map<String, Object>> sshFailedTop() {
        CommandResult result = run("journalctl -u sshd --since '24 hours ago' --no-pager 2>/dev/null "
                + "| grep -E 'Failed password|Invalid user|authentication failure' "
                + "| grep -Eo '([0-9]{1,3}\\.){3}[0-9]{1,3}' | sort | uniq -c | sort -nr | head -10");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : result.lines()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                rows.add(Map.of("ip", parts[1], "count", parseLong(parts[0])));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> recentBans() {
        CommandResult result = run("journalctl -u fail2ban --since '24 hours ago' --no-pager 2>/dev/null "
                + "| grep ' Ban ' | tail -20 | grep -Eo '([0-9]{1,3}\\.){3}[0-9]{1,3}'");
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (String line : result.lines()) {
            if (!line.isBlank()) {
                rows.add(Map.of("index", index++, "ip", line.trim()));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> rateLimitTopEndpoints() {
        CommandResult result = run("grep -h 'limiting requests' /var/log/nginx/error.log 2>/dev/null "
                + "| grep -Eo 'request: \"[A-Z]+ [^ ]+' | awk '{print $3}' | sort | uniq -c | sort -nr | head -10");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : result.lines()) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2) {
                rows.add(Map.of("path", parts[1], "count", parseLong(parts[0])));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> recommendations(Map<String, Object> sshd,
                                                       Map<String, Object> keyFile,
                                                       Map<String, Object> fail2ban,
                                                       Map<String, Object> firewall,
                                                       Map<String, Object> metrics) {
        List<Map<String, Object>> list = new ArrayList<>();
        addRecommendation(list, isNo(sshd.get("passwordauthentication")) ? "ok" : "risk",
                isNo(sshd.get("passwordauthentication")) ? "已关闭 SSH 密码登录" : "建议关闭 SSH 密码登录");
        addRecommendation(list, isYes(sshd.get("pubkeyauthentication")) ? "ok" : "risk",
                isYes(sshd.get("pubkeyauthentication")) ? "已启用 SSH 密钥登录" : "建议启用 SSH 密钥登录");
        addRecommendation(list, Boolean.TRUE.equals(keyFile.get("filePermissionOk")) ? "ok" : "risk",
                Boolean.TRUE.equals(keyFile.get("filePermissionOk")) ? "authorized_keys 权限为 600" : "建议修正 authorized_keys 权限为 600");
        addRecommendation(list, Boolean.TRUE.equals(fail2ban.get("active")) ? "ok" : "warning",
                Boolean.TRUE.equals(fail2ban.get("active")) ? "已启用 fail2ban" : "建议启用 fail2ban");
        addRecommendation(list, Boolean.TRUE.equals(firewall.get("requiredPortsOk")) ? "ok" : "risk",
                Boolean.TRUE.equals(firewall.get("requiredPortsOk")) ? "防火墙保留 22/80/443 访问" : "请确认防火墙 22/80/443 访问规则");
        addRecommendation(list, ((Collection<?>) firewall.get("unexpectedPublicPorts")).isEmpty() ? "ok" : "risk",
                ((Collection<?>) firewall.get("unexpectedPublicPorts")).isEmpty()
                        ? "未发现防火墙额外放行端口"
                        : "发现防火墙额外放行端口，请复核");
        addRecommendation(list, (long) metrics.get("sensitiveLogHitCount") == 0L ? "ok" : "risk",
                (long) metrics.get("sensitiveLogHitCount") == 0L ? "敏感日志扫描未命中" : "敏感日志扫描有命中，请立即人工复核");
        addRecommendation(list, Boolean.TRUE.equals(firewall.get("nginxLimitReqEnabled")) ? "ok" : "warning",
                Boolean.TRUE.equals(firewall.get("nginxLimitReqEnabled")) ? "nginx 限流配置已启用" : "建议检查 nginx limit_req 配置");
        return list;
    }

    private String overallLevel(Map<String, Object> sshd,
                                Map<String, Object> keyFile,
                                Map<String, Object> fail2ban,
                                Map<String, Object> firewall,
                                Map<String, Object> metrics) {
        boolean risk = !isNo(sshd.get("passwordauthentication"))
                || !isYes(sshd.get("pubkeyauthentication"))
                || !Boolean.TRUE.equals(keyFile.get("exists"))
                || !Boolean.TRUE.equals(keyFile.get("notEmpty"))
                || !Boolean.TRUE.equals(keyFile.get("filePermissionOk"))
                || !Boolean.TRUE.equals(keyFile.get("dirPermissionOk"))
                || !Boolean.TRUE.equals(firewall.get("requiredPortsOk"))
                || !((Collection<?>) firewall.get("unexpectedPublicPorts")).isEmpty()
                || (long) metrics.get("sensitiveLogHitCount") > 0L;
        if (risk) {
            return "risk";
        }
        boolean warning = !Boolean.TRUE.equals(fail2ban.get("active"))
                || !Boolean.TRUE.equals(fail2ban.get("sshdActive"))
                || !Boolean.TRUE.equals(firewall.get("nginxLimitReqEnabled"))
                || (long) metrics.get("appError24h") > 0L
                || (long) metrics.get("nginx5xx24h") > 0L;
        return warning ? "warning" : "normal";
    }

    private void addRecommendation(List<Map<String, Object>> list, String level, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("text", text);
        list.add(item);
    }

    private Set<Integer> parseListeningPorts(boolean publicOnly) {
        CommandResult result = run("ss -ltnH 2>/dev/null");
        Set<Integer> ports = new TreeSet<>();
        for (String line : result.lines()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            String address = parts[3];
            if (publicOnly && !isPublicListener(address)) {
                continue;
            }
            int port = parsePort(address);
            if (port > 0) {
                ports.add(port);
            }
        }
        return ports;
    }

    private Set<Integer> parseFirewallAllowedPorts() {
        CommandResult result = run("iptables -S GZLY-HARDEN-INPUT 2>/dev/null || true");
        Set<Integer> ports = new TreeSet<>();
        for (String line : result.lines()) {
            if (!(line.contains("-j ACCEPT") || line.contains("-j RETURN"))) {
                continue;
            }
            Matcher single = Pattern.compile("--dport\\s+(\\d+)").matcher(line);
            while (single.find()) {
                ports.add((int) parseLong(single.group(1)));
            }
            Matcher multi = Pattern.compile("--dports\\s+([0-9,]+)").matcher(line);
            while (multi.find()) {
                for (String part : multi.group(1).split(",")) {
                    ports.add((int) parseLong(part));
                }
            }
        }
        return ports;
    }

    private boolean isPublicListener(String address) {
        return address.startsWith("0.0.0.0:")
                || address.startsWith("[::]:")
                || address.startsWith("*:")
                || address.startsWith(":::")
                || address.startsWith("[::ffff:0.0.0.0]:");
    }

    private int parsePort(String address) {
        int idx = address.lastIndexOf(':');
        if (idx < 0 || idx + 1 >= address.length()) {
            return -1;
        }
        return (int) parseLong(address.substring(idx + 1).replace("]", ""));
    }

    private long countAudit(String event) {
        if (securityAuditCounterService == null) {
            return 0L;
        }
        try {
            return securityAuditCounterService.countLast24Hours(event);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long countLong(String command) {
        String output = run(command).stdout().trim();
        if (output.contains("\n")) {
            output = output.substring(output.lastIndexOf('\n') + 1).trim();
        }
        return parseLong(output);
    }

    private int parseIntAfterColon(String line) {
        int idx = line.indexOf(':');
        return idx >= 0 ? (int) parseLong(line.substring(idx + 1).trim()) : 0;
    }

    private long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean isYes(Object value) {
        return "yes".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean isNo(Object value) {
        return "no".equalsIgnoreCase(String.valueOf(value));
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String ownerIsRootText(Path path) {
        try {
            return Files.getOwner(path).getName();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean ownerIsRoot(Path path) {
        return "root".equals(ownerIsRootText(path));
    }

    private String permissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            int owner = 0;
            int group = 0;
            int other = 0;
            if (permissions.contains(PosixFilePermission.OWNER_READ)) owner += 4;
            if (permissions.contains(PosixFilePermission.OWNER_WRITE)) owner += 2;
            if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) owner += 1;
            if (permissions.contains(PosixFilePermission.GROUP_READ)) group += 4;
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)) group += 2;
            if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) group += 1;
            if (permissions.contains(PosixFilePermission.OTHERS_READ)) other += 4;
            if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) other += 2;
            if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) other += 1;
            return "" + owner + group + other;
        } catch (Exception ignored) {
            return "";
        }
    }

    private CommandResult run(String command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean done = process.waitFor(4, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return new CommandResult(124, "", "timeout");
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            return new CommandResult(process.exitValue(), out.toString(), "");
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(1, "", e.getClass().getSimpleName());
        }
    }

    private record CommandResult(int exitCode, String stdout, String error) {
        boolean ok() {
            return exitCode == 0;
        }

        List<String> lines() {
            if (stdout == null || stdout.isBlank()) {
                return List.of();
            }
            return stdout.lines().toList();
        }

        String safeError() {
            return error == null ? "" : error;
        }
    }
}
