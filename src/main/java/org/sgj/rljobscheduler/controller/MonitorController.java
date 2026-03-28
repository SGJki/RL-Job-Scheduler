package org.sgj.rljobscheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 系统监控接口：实时查看线程池活跃度及历史日志
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

    /**
     * 获取指定任务的历史日志内容
     */
    @GetMapping("/logs/{taskId}")
    public Map<String, Object> getTaskLogs(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        File logFile = new File("logs", taskId + ".log");
        
        if (!logFile.exists()) {
            result.put("status", "NOT_FOUND");
            result.put("content", new ArrayList<>());
            return result;
        }

        try {
            // 读取所有行（生产环境建议只读取最后 N 行以防内存溢出）
            List<String> lines = Files.readAllLines(logFile.toPath());
            result.put("status", "SUCCESS");
            result.put("content", lines);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        
        return result;
    }
}
