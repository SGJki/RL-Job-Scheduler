package org.sgj.rljobscheduler.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * Maintains the set of active worker IDs in Redis.
 * Replaces the O(N) KEYS worker:*:hb scan with O(N) SMEMBERS against a known small set.
 */
@Component
public class RedisWorkerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RedisWorkerRegistry.class);

    public static final String ACTIVE_WORKERS_KEY = "scheduler:active_workers";

    private final StringRedisTemplate redisTemplate;

    public RedisWorkerRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Registers a worker ID in the active workers set.
     *
     * @param workerId the worker ID to register
     * @return true if the worker was newly added, false if already present
     */
    public boolean register(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return false;
        }
        try {
            Long added = redisTemplate.opsForSet().add(ACTIVE_WORKERS_KEY, workerId);
            boolean result = added != null && added > 0;
            LOG.debug("Worker registration: workerId={}, newlyAdded={}", workerId, result);
            return result;
        } catch (Exception e) {
            LOG.error("Failed to register worker: workerId={}, error={}", workerId, e.getMessage());
            return false;
        }
    }

    /**
     * Removes a worker ID from the active workers set.
     *
     * @param workerId the worker ID to unregister
     */
    public void unregister(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(ACTIVE_WORKERS_KEY, workerId);
            LOG.debug("Worker unregistered: workerId={}", workerId);
        } catch (Exception e) {
            LOG.error("Failed to unregister worker: workerId={}, error={}", workerId, e.getMessage());
        }
    }

    /**
     * Retrieves all active worker IDs.
     *
     * @return the set of active worker IDs, or an empty set if none
     */
    public Set<String> getActiveWorkerIds() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(ACTIVE_WORKERS_KEY);
            return members != null ? members : Collections.emptySet();
        } catch (Exception e) {
            LOG.error("Failed to get active workers, returning empty set: error={}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
