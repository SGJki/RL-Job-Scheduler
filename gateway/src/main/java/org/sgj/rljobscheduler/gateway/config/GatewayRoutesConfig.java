package org.sgj.rljobscheduler.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("master-ws-canary", r -> r.path("/ws/**")
                        .and()
                        .header("X-Canary", "true")
                        .filters(f -> f.preserveHostHeader())
                        .uri("lb:ws://rl-master-canary"))
                .route("master-ws", r -> r.path("/ws/**")
                        .filters(f -> f.preserveHostHeader())
                        .uri("lb:ws://rl-master"))
                .route("master-http-canary", r -> r.predicate(exchange -> {
                            String path = exchange.getRequest().getURI().getPath();
                            return path == null ||
                                    (!path.startsWith("/gateway-actuator") && !path.startsWith("/gateway-error.html"));
                        })
                        .and()
                        .header("X-Canary", "true")
                        .filters(f -> f.preserveHostHeader())
                        .uri("lb://rl-master-canary"))
                .route("master-http", r -> r.predicate(exchange -> {
                            String path = exchange.getRequest().getURI().getPath();
                            return path == null ||
                                    (!path.startsWith("/gateway-actuator") && !path.startsWith("/gateway-error.html"));
                        })
                        .filters(f -> f.preserveHostHeader())
                        .uri("lb://rl-master"))
                .build();
    }
}
