package org.sgj.rljobscheduler.master.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PendingTaskReconciler {

    private final TrainingTaskMapper taskMapper;
    private final SchedulerService schedulerService;
    private final StringRedisTemplate redisTemplate;

    @Value("${scheduler.reconcile.enabled:true}")
    private boolean enabled;

    @Value("${scheduler.reconcile.batch-size:20}")
    private int batchSize;

    public PendingTaskReconciler(
            TrainingTaskMapper taskMapper,
            SchedulerService schedulerService,
            StringRedisTemplate redisTemplate
    ) {
        this.taskMapper = taskMapper;
        this.schedulerService = schedulerService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${scheduler.reconcile.fixed-delay-ms:2000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }

        List<String> idleWorkers = findIdleWorkers();
        if (idleWorkers.isEmpty()) {
            return;
        }

        int limit = Math.max(1, batchSize);
        List<TrainingTask> pending = taskMapper.selectList(
                new QueryWrapper<TrainingTask>()
                        .eq("status", "PENDING")
                        .orderByAsc("created_at")
                        .last("limit " + limit)
        );
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (TrainingTask task : pending) {
            if (task == null) {
                continue;
            }
            schedulerService.enqueueTask(task.getId());
        }
    }

    private List<String> findIdleWorkers() {
        Set<String> workerKeys = redisTemplate.keys("worker:*:hb");
        if (workerKeys == null || workerKeys.isEmpty()) {
            return List.of();
        }

        List<String> idle = new ArrayList<>();
        for (String key : workerKeys) {
            String[] parts = key.split(":");
            if (parts.length < 3) {
                continue;
            }
            String workerId = parts[1];
            String taskKey = "worker:" + workerId + ":task";
            Boolean busy = redisTemplate.hasKey(taskKey);
            if (busy == null || !busy) {
                idle.add(workerId);
            }
        }
        return idle;
    }
}
