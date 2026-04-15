package org.sgj.rljobscheduler.master.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisWorkerRegistryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOps;

    private RedisWorkerRegistry registry;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        registry = new RedisWorkerRegistry(redisTemplate);
    }

    @Test
    @DisplayName("register adds worker ID to active set")
    void register_addsWorkerIdToActiveSet() {
        when(setOps.add(RedisWorkerRegistry.ACTIVE_WORKERS_KEY, "worker-1")).thenReturn(1L);

        boolean result = registry.register("worker-1");

        assertThat(result).isTrue();
        verify(setOps).add(RedisWorkerRegistry.ACTIVE_WORKERS_KEY, "worker-1");
    }

    @Test
    @DisplayName("register is idempotent — returns false when already present")
    void register_idempotent_returnsFalseWhenAlreadyPresent() {
        when(setOps.add(RedisWorkerRegistry.ACTIVE_WORKERS_KEY, "worker-1")).thenReturn(0L);

        boolean result = registry.register("worker-1");

        assertThat(result).isFalse();
        verify(setOps).add(RedisWorkerRegistry.ACTIVE_WORKERS_KEY, "worker-1");
    }

    @Test
    @DisplayName("unregister removes worker ID from active set")
    void unregister_removesWorkerIdFromActiveSet() {
        registry.unregister("worker-1");

        verify(setOps).remove(RedisWorkerRegistry.ACTIVE_WORKERS_KEY, "worker-1");
    }

    @Test
    @DisplayName("getActiveWorkerIds returns all members from set")
    void getActiveWorkerIds_returnsAllMembers() {
        Set<String> expected = Set.of("worker-1", "worker-2", "worker-3");
        when(setOps.members(RedisWorkerRegistry.ACTIVE_WORKERS_KEY)).thenReturn(expected);

        Set<String> result = registry.getActiveWorkerIds();

        assertThat(result).isEqualTo(expected);
        verify(setOps).members(RedisWorkerRegistry.ACTIVE_WORKERS_KEY);
    }

    @Test
    @DisplayName("getActiveWorkerIds returns empty set when Redis returns null")
    void getActiveWorkerIds_returnsEmptySetOnNull() {
        when(setOps.members(RedisWorkerRegistry.ACTIVE_WORKERS_KEY)).thenReturn(null);

        Set<String> result = registry.getActiveWorkerIds();

        assertThat(result).isEmpty();
        verify(setOps).members(RedisWorkerRegistry.ACTIVE_WORKERS_KEY);
    }
}
