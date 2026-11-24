package com.gzgk.gateway.controller;

import com.gzgk.common.api.ApiResponse;
import com.gzgk.common.id.UlidGenerator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Issues short-lived (120s) one-time SSE tickets so browser EventSource requests
 * never expose long-lived tokens or safety codes in query strings.
 */
@RestController
@RequestMapping("/api/v2/gateway")
public class SseTicketController {

    private final ReactiveStringRedisTemplate redisTemplate;

    public SseTicketController(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/sse-ticket")
    public Mono<ApiResponse<Map<String, Object>>> issueTicket(@RequestBody Map<String, Object> req) {
        String planId = String.valueOf(req.getOrDefault("planId", ""));
        String ticket = UlidGenerator.nextUlid();
        String key = "gzgk:sse-ticket:" + ticket;
        return redisTemplate.opsForValue()
                .set(key, planId, Duration.ofSeconds(120))
                .timeout(Duration.ofMillis(300))
                .onErrorReturn(true)
                .map(ok -> ApiResponse.ok(Map.of(
                        "ticket", ticket,
                        "expiresInSeconds", 120
                )));
    }
}
