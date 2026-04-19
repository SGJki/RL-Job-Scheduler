package org.sgj.rljobscheduler.master.service;

import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import org.sgj.rljobscheduler.common.netty.MessageHeader;
import org.sgj.rljobscheduler.common.netty.MessageType;
import org.sgj.rljobscheduler.common.netty.NettyMessage;
import org.sgj.rljobscheduler.common.proto.ExecuteTaskRequest;
import org.sgj.rljobscheduler.master.annotation.Loggable;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.netty.ChannelManager;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.sgj.rljobscheduler.master.service.RedisWorkerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 调度中心：负责 Worker 的抢占与任务下发
 */
@Service
public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);
    private static final String DEFAULT_QUEUE_LIST_KEY = "scheduler:queue:tasks";
    private static final String DEFAULT_QUEUE_SET_KEY = "scheduler:queue:tasks:set";
    private static final String DEFAULT_TASK_TRACE_KEY_PREFIX = "task:";
    private static final String DEFAULT_TASK_WORKER_SUFFIX = ":workerId";
    private static final String DEFAULT_TASK_ATTEMPT_SUFFIX = ":currentAttempt";

    private static final DefaultRedisScript<Long> PREEMPT_ANY_SCRIPT;

    static {
        String script =
            "local taskId = ARGV[1]\n" +
            "local n = #KEYS / 2\n" +
            "for i = 0, n - 1 do\n" +
            "  local hbKey = KEYS[2*i+1]\n" +
            "  local taskKey = KEYS[2*i+2]\n" +
            "  if redis.call('get', hbKey) == 'alive' and redis.call('exists', taskKey) == 0 then\n" +
            "    redis.call('set', taskKey, taskId, 'EX', 120)\n" +
            "    return (i + 1)\n" +
            "  end\n" +
            "end\n" +
            "return 0\n";
        PREEMPT_ANY_SCRIPT = new DefaultRedisScript<>(script, Long.class);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private TrainingTaskMapper taskMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RedisWorkerRegistry workerRegistry;

    @Value("${scheduler.queue.enabled:false}")
    private boolean queueEnabled;

    @Value("${scheduler.queue.list-key:" + DEFAULT_QUEUE_LIST_KEY + "}")
    private String queueListKey;

    @Value("${scheduler.queue.set-key:" + DEFAULT_QUEUE_SET_KEY + "}")
    private String queueSetKey;

    /**
     * Master 启动时，从 Redis 重建任务状态
     * 扫描所有 worker:*:task key，恢复 RunningTaskRecovery 无法处理的边界情况
     */
    @PostConstruct
    @Loggable(level = Loggable.LogLevel.INFO, logExecutionTime = true)
    public void reconstructWorkerTasksFromRedis() {
        LOG.info(">>> [SchedulerService] 开始从 Redis 重建 Worker 任务状态...");
        try {
            Set<String> workerTaskKeys = redisTemplate.keys("worker:*:task");
            if (workerTaskKeys == null || workerTaskKeys.isEmpty()) {
                LOG.info(">>> [SchedulerService] 无活跃 Worker 任务，跳过重建");
                return;
            }

            for (String workerTaskKey : workerTaskKeys) {
                // worker:{workerId}:task
                String[] parts = workerTaskKey.split(":");
                if (parts.length < 3) {
                    continue;
                }
                String workerId = parts[1];
                String taskId = redisTemplate.opsForValue().get(workerTaskKey);
                if (taskId == null || taskId.isBlank()) {
                    continue;
                }

                // 检查 task:{taskId}:workerId 是否存在（调度时 Master 写入）
                String ownerKey = taskWorkerKey(taskId);
                String ownerWorkerId = redisTemplate.opsForValue().get(ownerKey);
                if (ownerWorkerId == null || !ownerWorkerId.equals(workerId)) {
                    // taskOwnerKey 不存在或指向不同 Worker → 调度被中断
                    TrainingTask task = taskMapper.selectById(taskId);
                    if (task != null && "RUNNING".equals(task.getStatus())) {
                        task.setStatus("PENDING");
                        taskMapper.updateById(task);
                        enqueueIfEnabled(taskId);
                        LOG.info(">>> [Recovery] 启动重建：任务 [{}] 调度中断，标记为 PENDING", taskId);
                    }
                }
            }
            LOG.info(">>> [SchedulerService] Worker 任务状态重建完成");
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 重建 Worker 任务状态失败: {}", e.getMessage());
        }
    }

    /**
     * 抢占并调度任务到合适的 Worker
     */
    public boolean scheduleTask(TrainingTask task) {
        return scheduleTask(task, null);
    }

    @Loggable(level = Loggable.LogLevel.INFO, logParams = true, logExecutionTime = true)
    public boolean scheduleTask(TrainingTask task, String traceId) {
        try {
            String effectiveTraceId = (traceId == null || traceId.isBlank()) ? "unknown" : traceId;
            redisTemplate.opsForValue().set(taskTraceKey(String.valueOf(task.getId())), effectiveTraceId, 1, TimeUnit.DAYS);

            // 1. 获取所有在线 Worker
            Set<String> workerIds = workerRegistry.getActiveWorkerIds();
            if (workerIds == null || workerIds.isEmpty()) {
                LOG.warn(">>> 没有在线的 Worker，无法调度任务: {}", task.getId());
                enqueueIfEnabled(String.valueOf(task.getId()));
                return false;
            }

            String winnerId = tryPreemptAnyWorker(String.valueOf(task.getId()));
            if (winnerId != null) {
                registerTaskOwner(winnerId, String.valueOf(task.getId()));
                // 抢占成功，通过 Netty 下发任务
                return dispatchTask(winnerId, task, effectiveTraceId);
            }

            LOG.warn(">>> 所有在线 Worker 均在运行中，任务进入等待队列: {}", task.getId());
            enqueueIfEnabled(String.valueOf(task.getId()));
            return false;
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 调度异常 (可能是 Redis 未启动或连接失败): {}", e.getMessage());
            // 调度失败，返回 false，让任务保持 PENDING 状态
            enqueueIfEnabled(String.valueOf(task.getId()));
            return false;
        }
    }

    @Loggable(level = Loggable.LogLevel.INFO, logParams = true, logExecutionTime = true)
    public boolean tryDispatchQueuedTaskToWorker(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return false;
        }
        if (!queueEnabled) {
            return false;
        }

        try {
            for (int i = 0; i < 10; i++) {
                String taskId = redisTemplate.opsForList().leftPop(queueListKey);
                if (taskId == null || taskId.isBlank()) {
                    return false;
                }

                redisTemplate.opsForSet().remove(queueSetKey, taskId);

                TrainingTask task = taskMapper.selectById(taskId);
                if (task == null || !"PENDING".equals(task.getStatus())) {
                    continue;
                }

                if (!tryPreemptWorker(workerId, taskId)) {
                    enqueueIfEnabled(taskId);
                    return false;
                }

                registerTaskOwner(workerId, taskId);

                String traceId = redisTemplate.opsForValue().get(taskTraceKey(taskId));
                if (traceId == null || traceId.isBlank()) {
                    traceId = "unknown";
                }

                boolean dispatched = dispatchTask(workerId, task, traceId);
                if (!dispatched) {
                    releaseTaskOwner(taskId);
                    enqueueIfEnabled(taskId);
                    return false;
                }

                task.setStatus("RUNNING");
                taskMapper.updateById(task);
                messagingTemplate.convertAndSend("/topic/tasks", task);
                return true;
            }

            return false;
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 队列任务下发异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Called when we need to find ANY idle worker to take a queued task.
     * Used as fallback when tryDispatchQueuedTaskToWorker returns false.
     */
    public void dispatchOneFromQueueToAnyIdleWorker() {
        dispatchOneFromQueue();
    }

    /**
     * Attempt to drain one task from the queue to any available idle worker.
     * Called immediately after enqueue and when a Worker reports idle via heartbeat.
     */
    private void dispatchOneFromQueue() {
        if (!queueEnabled) {
            return;
        }
        Set<String> workerIds = workerRegistry.getActiveWorkerIds();
        for (String workerId : workerIds) {
            String taskId = redisTemplate.opsForList().leftPop(queueListKey);
            if (taskId == null || taskId.isBlank()) {
                // Queue empty
                return;
            }

            // Verify task is still PENDING in DB
            TrainingTask task = taskMapper.selectById(taskId);
            if (task == null || !"PENDING".equals(task.getStatus())) {
                redisTemplate.opsForSet().remove(queueSetKey, taskId);
                continue;  // try next task
            }

            // Check if already assigned
            String ownerWorkerId = redisTemplate.opsForValue().get(taskWorkerKey(taskId));
            if (ownerWorkerId != null) {
                redisTemplate.opsForSet().remove(queueSetKey, taskId);
                continue;
            }

            if (tryPreemptWorker(workerId, taskId)) {
                registerTaskOwner(workerId, taskId);
                String traceId = redisTemplate.opsForValue().get(taskTraceKey(taskId));
                if (traceId == null || traceId.isBlank()) {
                    traceId = "unknown";
                }
                boolean dispatched = dispatchTask(workerId, task, traceId);
                if (dispatched) {
                    try {
                        task.setStatus("RUNNING");
                        taskMapper.updateById(task);
                        redisTemplate.opsForSet().remove(queueSetKey, taskId);
                        messagingTemplate.convertAndSend("/topic/tasks", task);
                        return;  // Successfully dispatched one task
                    } catch (Exception e) {
                        LOG.error(">>> [SchedulerService] 任务 [{}] 状态更新失败: {}", taskId, e.getMessage());
                        releaseTaskOwner(taskId);
                        enqueueIfEnabled(taskId);
                        return;
                    }
                }
                releaseTaskOwner(taskId);
            }
            // Worker busy or dispatch failed, re-enqueue at back
            enqueueIfEnabled(taskId);
            return;
        }
    }

    public void enqueueTask(String taskId) {
        enqueueIfEnabled(taskId);
    }

    public void registerTaskOwner(String workerId, String taskId) {
        if (workerId == null || workerId.isBlank() || taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(taskWorkerKey(taskId), workerId, 120, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 记录任务 owner 失败: {}", e.getMessage());
        }
    }

    public void releaseTaskOwner(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(taskWorkerKey(taskId));
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 释放任务 owner 失败: {}", e.getMessage());
        }
    }

    public void renewTaskOwnerTtl(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            redisTemplate.expire(taskWorkerKey(taskId), 120, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 续期任务 owner 失败: {}", e.getMessage());
        }
    }

    private void enqueueIfEnabled(String taskId) {
        if (!queueEnabled) {
            return;
        }
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            // SET NX semantics: only add if not already present
            Long added = redisTemplate.opsForSet().add(queueSetKey, taskId);
            if (added != null && added > 0) {
                redisTemplate.opsForList().rightPush(queueListKey, taskId);
                LOG.info(">>> 任务 [{}] 入队，立即触发调度", taskId);
                // Try dispatch to any idle worker immediately — don't wait for Reconciler
                dispatchOneFromQueue();
            }
        } catch (Exception e) {
            LOG.error(">>> [SchedulerService] 入队失败: {}", e.getMessage());
        }
    }

    private String taskTraceKey(String taskId) {
        return DEFAULT_TASK_TRACE_KEY_PREFIX + taskId + ":traceId";
    }

    private String taskWorkerKey(String taskId) {
        return DEFAULT_TASK_TRACE_KEY_PREFIX + taskId + DEFAULT_TASK_WORKER_SUFFIX;
    }

    private String taskAttemptKey(String taskId) {
        return DEFAULT_TASK_TRACE_KEY_PREFIX + taskId + DEFAULT_TASK_ATTEMPT_SUFFIX;
    }

    private boolean tryPreemptWorker(String workerId, String taskId) {
        String script = 
                "if redis.call('get', KEYS[1]) == 'alive' and redis.call('exists', KEYS[2]) == 0 then " +
                "  redis.call('set', KEYS[2], ARGV[1], 'EX', 120); " + // 2 分钟过期 (TaskIDKey)
                "  return 1; " +
                "else " +
                "  return 0; " +
                "end";
        
        String hbKey = "worker:" + workerId + ":hb";
        String taskKey = "worker:" + workerId + ":task";
        
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                java.util.Arrays.asList(hbKey, taskKey),
                taskId
        );
        
        return result != null && result == 1;
    }

    /**
     * Batch preempt — one Lua script checks all workers at once.
     * Returns the first workerId that successfully preempts, or null if all busy.
     * Redis: SMEMBERS → one Lua call → result
     */
    public String tryPreemptAnyWorker(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        Set<String> workerIds = workerRegistry.getActiveWorkerIds();
        if (workerIds == null || workerIds.isEmpty()) {
            return null;
        }

        // CRITICAL: use ordered List so index mapping is stable
        List<String> workerIdList = new ArrayList<>(workerIds);

        // Build KEYS list in the SAME order as workerIdList
        List<String> keys = new ArrayList<>();
        for (String workerId : workerIdList) {
            keys.add("worker:" + workerId + ":hb");    // KEYS[2*i]
            keys.add("worker:" + workerId + ":task");   // KEYS[2*i+1]
        }

        // Single Lua script: iterate all pairs, return 1-based index of first winner
        Long result = redisTemplate.execute(PREEMPT_ANY_SCRIPT, keys, taskId);

        if (result == null || result == 0) {
            return null;
        }

        // Use the SAME workerIdList for index → workerId conversion
        int idx = (int) (result - 1);
        return idx >= 0 && idx < workerIdList.size() ? workerIdList.get(idx) : null;
    }

    private boolean dispatchTask(String workerId, TrainingTask task, String traceId) {
        Channel channel = channelManager.getChannel(workerId);
        if (channel == null || !channel.isActive()) {
            LOG.error(">>> Worker [{}] 已掉线，抢占失败", workerId);
            // 清理已设置的任务 Key
            redisTemplate.delete("worker:" + workerId + ":task");
            releaseTaskOwner(String.valueOf(task.getId()));
            return false;
        }

        int attempt = allocateAttempt(String.valueOf(task.getId()));
        ExecuteTaskRequest req = ExecuteTaskRequest.newBuilder()
                .setTaskId(String.valueOf(task.getId()))
                .setAlgorithm(task.getAlgorithm())
                .setEpisodes(task.getEpisodes())
                .setLearningRate(task.getLearningRate())
                .setTraceId(traceId)
                .setAttempt(attempt)
                .build();

        NettyMessage message = new NettyMessage();
        message.setHeader(new MessageHeader(0, MessageType.EXECUTE_TASK.getCode()));
        message.setBody(req);

        channel.writeAndFlush(message);
        LOG.info(">>> 任务 [{}] 已下发给 Worker [{}], attempt={}", task.getId(), workerId, attempt);
        return true;
    }

    public int getCurrentAttempt(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return 0;
        }
        try {
            String raw = redisTemplate.opsForValue().get(taskAttemptKey(taskId));
            if (raw == null || raw.isBlank()) {
                return 0;
            }
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return 0;
        }
    }

    private int allocateAttempt(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return 0;
        }
        try {
            Long attempt = redisTemplate.opsForValue().increment(taskAttemptKey(taskId));
            if (attempt != null && attempt == 1L) {
                redisTemplate.expire(taskAttemptKey(taskId), 1, TimeUnit.DAYS);
            }
            if (attempt == null) {
                return 0;
            }
            if (attempt > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return attempt.intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
