package org.sgj.rljobscheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 系统监控接口：实时查看线程池活跃度
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    @Qualifier("trainingTaskExecutor")
    private Executor executor;

    @GetMapping("/health")
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (executor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor threadPool = (ThreadPoolTaskExecutor) executor;
            
            status.put("poolName", "RL-Training-Pool");
            status.put("activeCount", threadPool.getActiveCount());
            status.put("corePoolSize", threadPool.getCorePoolSize());
            status.put("maxPoolSize", threadPool.getMaxPoolSize());
            status.put("poolSize", threadPool.getPoolSize());
            status.put("queueSize", threadPool.getThreadPoolExecutor().getQueue().size());
            status.put("completedTaskCount", threadPool.getThreadPoolExecutor().getCompletedTaskCount());
            status.put("status", "UP");
        } else {
            status.put("status", "UNKNOWN");
            status.put("error", "Executor is not an instance of ThreadPoolTaskExecutor");
        }
        
        return status;
    }
}
