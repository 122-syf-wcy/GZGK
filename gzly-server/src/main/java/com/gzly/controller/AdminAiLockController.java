package com.gzly.controller;

import com.gzly.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin 维度的 AI 互斥锁运维接口。
 *
 * <p>用于在 SSE 流异常崩溃 / 客户端硬关浏览器导致 Redis 锁未被
 * onCompletion/onTimeout/onError 释放、但 TTL 未到时，提供手动清锁能力，
 * 防止"该方案的 AI 解读正在生成中"长时间误拦。</p>
 *
 * <p>路径前缀 {@code /admin/volunteer/ai-lock} 会自动被 WebMvcConfig 中
 * {@code AuthInterceptor(jwtUtil, "admin")} 拦截，无需在本控制器中再写鉴权。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/volunteer/ai-lock")
@RequiredArgsConstructor
public class AdminAiLockController {

    private static final String PLAN_LOCK_PREFIX = "active:ai-analysis:plan:";
    private static final String IP_LOCK_PREFIX = "active:ai-analysis:ip:";
    private static final String GLOBAL_LOCK_KEY = "active:ai-analysis:global";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 查看某个 planId 的 AI 锁状态。
     *
     * @param planId 必填，方案 id
     * @return key、当前值、剩余 TTL（秒，-1 表示无 TTL，-2 表示 key 不存在）
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam("planId") Long planId) {
        Map<String, Object> data = new LinkedHashMap<>();
        String key = PLAN_LOCK_PREFIX + planId;
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            Long ttl = stringRedisTemplate.getExpire(key);
            data.put("planId", planId);
            data.put("key", key);
            data.put("locked", value != null);
            data.put("value", value);
            data.put("ttlSeconds", ttl);
        } catch (Exception e) {
            log.warn("查询 AI 锁状态失败: planId={}", planId, e);
            data.put("planId", planId);
            data.put("error", "Redis 访问失败：" + e.getMessage());
        }
        return Result.ok(data);
    }

    /**
     * 手动释放某个 planId 的 AI 锁。幂等：未上锁也返回成功。
     *
     * @param planId 必填，方案 id
     * @return 是否真实删除了一个 key
     */
    @PostMapping("/release")
    public Result<Map<String, Object>> release(@RequestParam("planId") Long planId) {
        Map<String, Object> data = new LinkedHashMap<>();
        String key = PLAN_LOCK_PREFIX + planId;
        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            data.put("planId", planId);
            data.put("key", key);
            data.put("deleted", Boolean.TRUE.equals(deleted));
            log.warn("Admin 手动释放 AI 锁: planId={} deleted={}", planId, deleted);
        } catch (Exception e) {
            log.warn("释放 AI 锁失败: planId={}", planId, e);
            data.put("planId", planId);
            data.put("error", "Redis 访问失败：" + e.getMessage());
        }
        return Result.ok(data);
    }

    /**
     * 紧急复位：清掉全局并发计数器 + 指定 IP 计数器（可选） + 指定 planId 的锁（可选）。
     * 用于"全站 AI 解读都返回稍后再试，但实际后端没在跑"的极端情况。
     *
     * @param clientIp 可选，要清零的 IP 计数器；空则不清
     * @param planId   可选，要释放的方案锁；空则不释放
     */
    @PostMapping("/reset-counters")
    public Result<Map<String, Object>> resetCounters(@RequestParam(value = "clientIp", required = false) String clientIp,
                                                     @RequestParam(value = "planId", required = false) Long planId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            Boolean globalDeleted = stringRedisTemplate.delete(GLOBAL_LOCK_KEY);
            data.put("globalDeleted", Boolean.TRUE.equals(globalDeleted));
            if (clientIp != null && !clientIp.isBlank()) {
                String ipKey = IP_LOCK_PREFIX + clientIp.trim();
                Boolean ipDeleted = stringRedisTemplate.delete(ipKey);
                data.put("ipKey", ipKey);
                data.put("ipDeleted", Boolean.TRUE.equals(ipDeleted));
            }
            if (planId != null && planId > 0) {
                String planKey = PLAN_LOCK_PREFIX + planId;
                Boolean planDeleted = stringRedisTemplate.delete(planKey);
                data.put("planKey", planKey);
                data.put("planDeleted", Boolean.TRUE.equals(planDeleted));
            }
            log.warn("Admin 紧急复位 AI 并发计数器: clientIp={} planId={} result={}", clientIp, planId, data);
        } catch (Exception e) {
            log.warn("紧急复位 AI 计数器失败", e);
            data.put("error", "Redis 访问失败：" + e.getMessage());
        }
        return Result.ok(data);
    }
}
