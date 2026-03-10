package org.sgj.rljobscheduler.service;

import org.sgj.rljobscheduler.entity.TrainingTask;
import org.sgj.rljobscheduler.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 专门负责执行异步任务的执行器
 * 把耗时逻辑放在这里，避免 Self-Invocation 问题
 */
@Service
public class TrainingExecutor {

    @Autowired
    private TrainingTaskMapper taskMapper;

    @Async // 关键注解：告诉 Spring 这是一个异步方法，要丢给线程池跑
    public CompletableFuture<Double> executeTraining(String taskId, int episodes) {
        System.out.println("Start training task: " + taskId +  Thread.currentThread().getName());
        updateStatus(taskId, "RUNNING");
        // 2. 模拟耗时操作
        try {
            Thread.sleep(10000); // 睡5秒，更能看出异步效果
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 3. 模拟计算结果
        double mockReward = new Random().nextDouble() * 100;

        // 占位，为errorMessage
        boolean error = false;
        String errorMessage = "";


        // 4. 更新状态为 COMPLETED
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            if (error) {
                task.setErrorMessage("任务执行失败"+errorMessage);
            }
            task.setStatus("COMPLETED");
            task.setFinalReward(mockReward);
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            System.out.println(">>> [后台线程] 任务完成: " + taskId);
        }
        return CompletableFuture.completedFuture(mockReward);
    }

    private void updateStatus(String taskId, String status) {
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            taskMapper.updateById(task);
        }
    }
}

