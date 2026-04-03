package org.sgj.rljobscheduler.master.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunningTaskRecovery {

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
                continue;
            }

            Boolean hbAlive = redisTemplate.hasKey("worker:" + workerId + ":hb");
            Boolean taskKeyExists = redisTemplate.hasKey("worker:" + workerId + ":task");
            if ((hbAlive != null && hbAlive) && (taskKeyExists != null && taskKeyExists)) {
                continue;
            }

            task.setStatus("PENDING");
            taskMapper.updateById(task);
            schedulerService.enqueueTask(taskId);
        }
    }

    private String taskWorkerKey(String taskId) {
        return TASK_WORKER_KEY_PREFIX + taskId + ":workerId";
    }
}

