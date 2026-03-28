package org.sgj.rljobscheduler.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean failOpen = true;
    private int maxRequests = 3;
    private long windowSeconds = 10;
    private List<String> methods = new ArrayList<>(List.of("POST"));
    private List<String> pathPrefixes = new ArrayList<>(List.of("/submit", "/api/train"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public List<String> getPathPrefixes() {
        return pathPrefixes;
    }

    public void setPathPrefixes(List<String> pathPrefixes) {
        this.pathPrefixes = pathPrefixes;
    }
}

