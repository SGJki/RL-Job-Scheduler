package org.sgj.rljobscheduler.gateway.filter;

import org.sgj.rljobscheduler.gateway.config.RateLimitProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String KEY_PREFIX = "rl:gw:rlimit:";

    private final RateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RateLimitFilter(RateLimitProperties properties, ReactiveStringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(
                "local current = redis.call('INCR', KEYS[1])\n" +
                        "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                        "return current",
                Long.class
        );
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

        String method = exchange.getRequest().getMethod() == null ? "" : exchange.getRequest().getMethod().name();
        if (!properties.getMethods().isEmpty() && !properties.getMethods().contains(method)) {
            return chain.filter(exchange);
        }

        String matchedPrefix = null;
        for (String prefix : properties.getPathPrefixes()) {
            if (path.startsWith(prefix)) {
                matchedPrefix = prefix;
                break;
            }
        }
        if (matchedPrefix == null) {
            return chain.filter(exchange);
        }

        String principal = resolvePrincipal(exchange);
        long windowSeconds = Math.max(1, properties.getWindowSeconds());
        long bucket = Instant.now().getEpochSecond() / windowSeconds;
        String routeKey = matchedPrefix.replace('/', '_');
        String redisKey = KEY_PREFIX + principal + ":" + routeKey + ":" + bucket;

        return redisTemplate.execute(script, List.of(redisKey), String.valueOf(windowSeconds))
                .next()
                .flatMap(count -> {
                    if (count == null) {
                        return chain.filter(exchange);
                    }
                    int limit = Math.max(1, properties.getMaxRequests());
                    int remaining = (int) Math.max(0, (long) limit - count);

                    exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));

                    if (count > limit) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(windowSeconds));
                        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                        return exchange.getResponse().writeWith(Mono.just(
                                exchange.getResponse().bufferFactory().wrap("Too Many Requests".getBytes(StandardCharsets.UTF_8))
                        ));
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> properties.isFailOpen() ? chain.filter(exchange) : tooManyRequests(exchange));
    }

    private String resolvePrincipal(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "u" + userId.trim();
        }

        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("jwt_token");
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return "c" + Integer.toHexString(cookie.getValue().hashCode());
        }

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        return "ip" + ip;
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap("Service Unavailable".getBytes(StandardCharsets.UTF_8))
        ));
    }

    @Override
    public int getOrder() {
        return -800;
    }
}

