package org.sgj.rljobscheduler.master.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunningTaskRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(RunningTaskRecovery.class);
    private static final String TASK_WORKER_KEY_PREFIX = "task:";

    private final TrainingTaskMapper taskMapper;
    private final SchedulerService schedulerService;
    private final StringRedisTemplate redisTemplate;

    @Value("${scheduler.recover.enabled:true}")
    private boolean enabled;

    @Value("${scheduler.recover.fixed-delay-ms:5000}")
    private long fixedDelayMs;

    @Value("${scheduler.recover.batch-size:20}")
    private int batchSize;

    public RunningTaskRecovery(
            TrainingTaskMapper taskMapper,
            SchedulerService schedulerService,
            StringRedisTemplate redisTemplate
    ) {
        this.taskMapper = taskMapper;
        this.schedulerService = schedulerService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${scheduler.recover.fixed-delay-ms:5000}")
    public void recoverOrphanRunningTasks() {
        if (!enabled) {
            return;
        }

        int limit = Math.max(1, batchSize);
        List<TrainingTask> running = taskMapper.selectList(
                new QueryWrapper<TrainingTask>()
                        .eq("status", "RUNNING")
                        .orderByAsc("created_at")
                        .last("limit " + limit)
        );
        if (running == null || running.isEmpty()) {
            return;
        }

        for (TrainingTask task : running) {
            if (task == null || task.getId() == null || task.getId().isBlank()) {
                continue;
            }

            String taskId = task.getId();
            String workerId = redisTemplate.opsForValue().get(taskWorkerKey(taskId));
            if (workerId == null || workerId.isBlank()) {
                // task:{taskId}:workerId 不存在 → Master 在调度前或调度期间宕机，任务从未真正开始
                // 标记为 PENDING 并重新入队
                task.setStatus("PENDING");
                taskMapper.updateById(task);
                schedulerService.enqueueTask(taskId);
                LOG.info(">>> [Recovery] 任务 [{}] 从未分发（无 owner key），标记为 PENDING", taskId);
                continue;
            }

            Boolean hbAlive = redisTemplate.hasKey("worker:" + workerId + ":hb");
            Boolean taskKeyExists = redisTemplate.hasKey("worker:" + workerId + ":task");
            if (hbAlive != null && hbAlive) {
                // Worker 心跳存活 → 任务正在运行或刚完成（但通知可能丢失）
                // 保持 RUNNING，由心跳处理器判断任务是否实际已完成
                continue;
            }

            // Worker 心跳不存在 → Worker 已死，任务孤立
            task.setStatus("PENDING");
            taskMapper.updateById(task);
            schedulerService.enqueueTask(taskId);
            schedulerService.releaseTaskOwner(taskId);
            LOG.info(">>> [Recovery] 任务 [{}] 的 Worker [{}] 已失活，标记为 PENDING", taskId, workerId);
        }
    }

    private String taskWorkerKey(String taskId) {
        return TASK_WORKER_KEY_PREFIX + taskId + ":workerId";
    }
}

