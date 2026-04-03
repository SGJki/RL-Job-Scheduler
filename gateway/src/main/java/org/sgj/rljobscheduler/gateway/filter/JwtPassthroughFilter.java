package org.sgj.rljobscheduler.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.sgj.rljobscheduler.common.SignatureUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.Key;
import java.time.Instant;
import java.util.List;

@Component
public class JwtPassthroughFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/login",
            "/error",
            "/ws",
            "/gateway-actuator",
            "/gateway-error.html"
    );

    private final Key secretKey;
    private final boolean enforce;
    private final byte[] gatewaySharedSecretBytes;

    public JwtPassthroughFilter(
            @Value("${security.jwt.secret:}") String configuredSecret,
            @Value("${security.jwt.enforce:true}") boolean enforce,
            @Value("${security.gateway.shared-secret:}") String gatewaySharedSecret
    ) {
        String secret = configuredSecret;
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("JWT_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            secret = "rl-job-scheduler-dev-secret-rl-job-scheduler-dev-secret";
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.enforce = enforce;

        String gatewaySecret = gatewaySharedSecret;
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            gatewaySecret = System.getenv("GATEWAY_SHARED_SECRET");
        }
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            gatewaySecret = "rl-gateway-dev-shared-secret";
        }
        this.gatewaySharedSecretBytes = gatewaySecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path == null || isPublicPath(path) || !enforce) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange);
        }

        Claims claims = parseToken(token);
        if (claims == null) {
            return unauthorized(exchange);
        }

        String rawTraceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        String traceId = (rawTraceId == null || rawTraceId.isBlank()) ? "no-trace" : rawTraceId;
        String ts = String.valueOf(Instant.now().getEpochSecond());

        ServerHttpRequest mutated = exchange.getRequest()
                .mutate()
                .headers(h -> {
                    String username = claims.getSubject();
                    String role = claims.get("role", String.class);
                    Object userIdObj = claims.get("userId");
                    String userId = userIdObj == null ? "" : String.valueOf(userIdObj);
                    if (username != null) h.set("X-User-Name", username);
                    if (role != null) h.set("X-User-Role", role);
                    if (!userId.isBlank()) h.set("X-User-Id", userId);
                    h.set("X-Gateway-Auth", "jwt-parsed");
                    h.set("X-Gateway-Timestamp", ts);
                    String payload = exchange.getRequest().getMethod() + "\n" + exchange.getRequest().getURI().getPath() + "\n" + traceId + "\n" + ts;
                    h.set("X-Gateway-Signature", SignatureUtils.hmacSha256Hex(gatewaySharedSecretBytes, payload));
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublicPath(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        MediaType accept = exchange.getRequest().getHeaders().getAccept().stream().findFirst().orElse(null);
        boolean preferHtml = accept != null && accept.isCompatibleWith(MediaType.TEXT_HTML);

        if (HttpMethod.GET.equals(exchange.getRequest().getMethod()) && preferHtml) {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/login"));
            return exchange.getResponse().setComplete();
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap("Unauthorized".getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String extractToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("jwt_token");
        if (cookie != null) {
            return cookie.getValue();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -900;
    }
}
