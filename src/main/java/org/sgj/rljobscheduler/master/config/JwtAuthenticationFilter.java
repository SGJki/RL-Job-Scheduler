package org.sgj.rljobscheduler.master.config;

import org.sgj.rljobscheduler.master.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Value("${security.gateway.enabled:true}")
    private boolean gatewayEnabled;

    @Value("${security.gateway.shared-secret:}")
    private String gatewaySharedSecret;

    @Value("${security.gateway.max-skew-seconds:300}")
    private long gatewayMaxSkewSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tryGatewayHeaderAuthentication(request);
        }

        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("jwt_token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }
        if (token != null && jwtUtils.validateToken(token)) {
            Claims claims = jwtUtils.parseToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            Long userId = claims.get("userId", Long.class);

            // 构建 Authentication 对象
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

            // 将 userId 存入 details，方便后续 Service 获取
            authentication.setDetails(userId);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);

    }

    private void tryGatewayHeaderAuthentication(HttpServletRequest request) {
        if (!gatewayEnabled) {
            return;
        }

        String gatewayAuth = request.getHeader("X-Gateway-Auth");
        if (gatewayAuth == null || gatewayAuth.isBlank()) {
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String role = request.getHeader("X-User-Role");

        String traceId = request.getHeader("X-Trace-Id");
        String tsHeader = request.getHeader("X-Gateway-Timestamp");
        String sigHeader = request.getHeader("X-Gateway-Signature");

        if (userId == null || userId.isBlank() ||
                username == null || username.isBlank() ||
                role == null || role.isBlank() ||
                traceId == null || traceId.isBlank() ||
                tsHeader == null || tsHeader.isBlank() ||
                sigHeader == null || sigHeader.isBlank()) {
            return;
        }

        long tsSeconds;
        try {
            tsSeconds = Long.parseLong(tsHeader);
        } catch (NumberFormatException e) {
            return;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - tsSeconds) > gatewayMaxSkewSeconds) {
            return;
        }

        String secret = gatewaySharedSecret;
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("GATEWAY_SHARED_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            secret = "rl-gateway-dev-shared-secret";
        }

        String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n" + traceId + "\n" + tsHeader;
        String expected = hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), payload);
        if (expected.isBlank()) {
            return;
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sigHeader.getBytes(StandardCharsets.UTF_8))) {
            return;
        }

        Long userIdLong;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(userIdLong);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String hmacSha256Hex(byte[] secretBytes, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
