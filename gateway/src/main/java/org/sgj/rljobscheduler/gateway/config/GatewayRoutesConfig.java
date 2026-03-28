package org.sgj.rljobscheduler.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, CanaryProperties canaryProperties) {
        return builder.routes()
                .route("master-ws-canary", r -> r.path("/ws/**")
                        .and()
                        .predicate(exchange -> isCanary(exchange, canaryProperties))
                        .filters(f -> f.preserveHostHeader()
                                .addResponseHeader("X-Canary", "true")
                                .addResponseHeader("X-Upstream-Service", "rl-master-canary"))
                        .uri("lb:ws://rl-master-canary"))
                .route("master-ws", r -> r.path("/ws/**")
                        .filters(f -> f.preserveHostHeader()
                                .addResponseHeader("X-Canary", "false")
                                .addResponseHeader("X-Upstream-Service", "rl-master"))
                        .uri("lb:ws://rl-master"))
                .route("master-http-canary", r -> r.predicate(exchange -> {
                            String path = exchange.getRequest().getURI().getPath();
                            return path == null ||
                                    (!path.startsWith("/gateway-actuator") && !path.startsWith("/gateway-error.html"));
                        })
                        .and()
                        .predicate(exchange -> isCanary(exchange, canaryProperties))
                        .filters(f -> f.preserveHostHeader()
                                .addResponseHeader("X-Canary", "true")
                                .addResponseHeader("X-Upstream-Service", "rl-master-canary"))
                        .uri("lb://rl-master-canary"))
                .route("master-http", r -> r.predicate(exchange -> {
                            String path = exchange.getRequest().getURI().getPath();
                            return path == null ||
                                    (!path.startsWith("/gateway-actuator") && !path.startsWith("/gateway-error.html"));
                        })
                        .filters(f -> f.preserveHostHeader()
                                .addResponseHeader("X-Canary", "false")
                                .addResponseHeader("X-Upstream-Service", "rl-master"))
                        .uri("lb://rl-master"))
                .build();
    }

    private boolean isCanary(org.springframework.web.server.ServerWebExchange exchange, CanaryProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            return false;
        }

        String headerName = properties.getHeaderName();
        String headerValue = properties.getHeaderValue();
        if (headerName != null && !headerName.isBlank()) {
            String raw = exchange.getRequest().getHeaders().getFirst(headerName);
            if (raw != null && !raw.isBlank() && (headerValue == null || headerValue.isBlank() || raw.equalsIgnoreCase(headerValue))) {
                return true;
            }
        }

        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        List<String> userIds = properties.getUserIds();
        if (userId != null && !userId.isBlank() && userIds != null && userIds.contains(userId.trim())) {
            return true;
        }

        int percent = Math.max(0, Math.min(100, properties.getPercent()));
        if (percent <= 0) {
            return false;
        }

        String key = userId;
        if (key == null || key.isBlank()) {
            key = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        }
        if (key == null || key.isBlank()) {
            key = exchange.getRequest().getURI().getPath();
        }
        int bucket = Math.floorMod(key.hashCode(), 100);
        return bucket < percent;
    }
}
