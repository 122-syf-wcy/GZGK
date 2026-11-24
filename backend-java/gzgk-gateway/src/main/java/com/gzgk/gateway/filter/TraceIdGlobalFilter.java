package com.gzgk.gateway.filter;

import com.gzgk.common.id.UlidGenerator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Injects W3C-compatible X-Request-Id for cross-language tracing across Gateway -> Java Core -> Python Agent.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        String traceId = (existing != null && !existing.isBlank()) ? existing : UlidGenerator.nextUlid();

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TRACE_HEADER, traceId)
                .build();
        exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
