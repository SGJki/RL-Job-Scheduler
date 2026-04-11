package org.sgj.rljobscheduler.worker.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class RedisLeaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(RedisLeaseManager.class);

    private final String workerId;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;

    private final long hbTtlSeconds;
    private final long taskTtlSeconds;

    public RedisLeaseManager(String workerId) {
        this.workerId = workerId;
        String host = getEnv("REDIS_HOST", "localhost");
        int port = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
        int db = Integer.parseInt(getEnv("REDIS_DB", "0"));
        String password = getEnv("REDIS_PASSWORD", "");

        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port).withDatabase(db).withTimeout(Duration.ofSeconds(5));
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
        RedisURI uri = builder.build();

        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        this.commands = connection.sync();

        this.hbTtlSeconds = Long.parseLong(getEnv("WORKER_HB_TTL_SECONDS", "30"));
        this.taskTtlSeconds = Long.parseLong(getEnv("WORKER_TASK_TTL_SECONDS", "120"));
    }

    public void renew(String currentTaskId, String lastTaskId) {
        try {
            commands.setex(hbKey(), hbTtlSeconds, "alive");

            if (currentTaskId != null && !currentTaskId.isBlank()) {
                commands.setex(taskKey(), taskTtlSeconds, currentTaskId);
                commands.setex(taskOwnerKey(currentTaskId), taskTtlSeconds, workerId);
                return;
            }

            commands.del(taskKey());
            if (lastTaskId != null && !lastTaskId.isBlank()) {
                commands.del(taskOwnerKey(lastTaskId));
            }
        } catch (Exception ignored) {
        }
    }

    public void persistTaskStart(String taskId) {
        try {
            commands.setex(taskKey(), taskTtlSeconds, taskId);
            commands.setex(taskOwnerKey(taskId), taskTtlSeconds, workerId);
        } catch (Exception e) {
            LOG.warn(">>> [LeaseManager] persistTaskStart failed for taskId={}: {}", taskId, e.getMessage());
        }
    }

    public void clearTask(String taskId) {
        try {
            commands.del(taskKey());
            if (taskId != null && !taskId.isBlank()) {
                commands.del(taskOwnerKey(taskId));
            }
        } catch (Exception e) {
            LOG.warn(">>> [LeaseManager] clearTask failed for taskId={}: {}", taskId, e.getMessage());
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (Exception ignored) {
        }
        try {
            client.shutdown();
        } catch (Exception ignored) {
        }
    }

    private String hbKey() {
        return "worker:" + workerId + ":hb";
    }

    private String taskKey() {
        return "worker:" + workerId + ":task";
    }

    private String taskOwnerKey(String taskId) {
        return "task:" + taskId + ":workerId";
    }

    private String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }
}

