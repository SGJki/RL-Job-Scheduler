package org.sgj.rljobscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 专门用于 RL 训练任务的线程池。
     * 考虑到训练任务非常耗 CPU 和内存，限制最大并发数，防止系统崩溃。
     */
    @Bean(name = "trainingTaskExecutor")
    public Executor trainingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据 CPU 核心数调整，RL 训练通常是 CPU 密集型
        executor.setCorePoolSize(4);
        // 最大线程数：防止创建过多进程导致 OOM
        executor.setMaxPoolSize(8);
        // 队列容量：允许排队等待的任务数
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("RL-Trainer-");
        // 优雅关闭：确保任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
