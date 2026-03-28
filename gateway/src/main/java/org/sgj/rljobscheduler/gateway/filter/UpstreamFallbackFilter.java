package org.sgj.rljobscheduler.gateway.filter;

import org.sgj.rljobscheduler.gateway.config.FallbackProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class UpstreamFallbackFilter implements GlobalFilter, Ordered {

    private final FallbackProperties properties;

    public UpstreamFallbackFilter(FallbackProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (path == null) {
            return chain.filter(exchange);
        }

        for (String prefix : properties.getExcludePathPrefixes()) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange);
            }
        }

        long timeoutMs = Math.max(1, properties.getTimeoutMs());
        return chain.filter(exchange)
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> fallback(exchange));
    }

    private Mono<Void> fallback(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = "unknown";
        }
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);

        boolean isGet = HttpMethod.GET.equals(exchange.getRequest().getMethod());
        MediaType accept = exchange.getRequest().getHeaders().getAccept().stream().findFirst().orElse(null);
        boolean preferHtml = accept != null && accept.isCompatibleWith(MediaType.TEXT_HTML);

        if (isGet && preferHtml) {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            String target = properties.getErrorPagePath();
            if (target == null || target.isBlank()) {
                target = "/gateway-error.html";
            }
            exchange.getResponse().getHeaders().setLocation(URI.create(target + "?traceId=" + traceId));
            return exchange.getResponse().setComplete();
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = toJson(Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "message", "Upstream service unavailable",
                "traceId", traceId
        ));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public int getOrder() {
        return -700;
    }
}

