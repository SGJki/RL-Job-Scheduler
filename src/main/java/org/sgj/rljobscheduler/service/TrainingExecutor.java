package org.sgj.rljobscheduler.service;

import org.sgj.rljobscheduler.entity.TrainingTask;
import org.sgj.rljobscheduler.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
        double finalReward = 0.0;
        boolean error = false;
        // 2. 模拟耗时操作
        // 1. 获取任务详情
        try{
        TrainingTask task = taskMapper.selectById(taskId);
        if (task == null) return CompletableFuture.completedFuture(0.0);
        Process process = null;
        BufferedReader reader = null;

        // 2. 构建 Python 命令
        // python scripts/train.py --taskId xxx --algo PPO --episodes 1000 --lr 0.001
        List<String> command = new ArrayList<>();
        command.add("uv");
        command.add("run");
        command.add("python"); // 确保系统环境变量里有 python
        command.add("scripts/train.py");
        command.add("--taskId");
        command.add(taskId);
        command.add("--algo");
        command.add(task.getAlgorithm());
        command.add("--episodes");
        command.add(String.valueOf(episodes));
        command.add("--lr");
        command.add(String.valueOf(task.getLearningRate()));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("C:\\Users\\13253\\dataDisk\\java_code\\Welcome\\RL-Job-Scheduler"));
        pb.redirectErrorStream(true); // 合并 stderr 到 stdout

        // 3. 启动进程
        try {
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println("[Process Output] " + line);

                // 解析关键结果: "FINAL_REWARD:105.50"
                if (line.startsWith("FINAL_REWARD:")) {
                    try {
                        finalReward = Double.parseDouble(line.split(":")[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("解析 Reward 失败: " + line);
                    }
                }}
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println(">>> [后台线程] Python 脚本执行成功");
                // 更新数据库 (需要重新查询以确保数据最新)
                TrainingTask updateTask = taskMapper.selectById(taskId);
                if (updateTask != null) {
                    updateTask.setStatus("COMPLETED");
                    updateTask.setFinalReward(finalReward);
                    updateTask.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(updateTask);
                }}
             else {
                System.err.println(">>> [后台线程] Python 脚本异常退出, Code: " + exitCode);
                updateStatus(taskId, "FAILED");
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }finally {
            // 4. 释放资源
            if(reader!= null){
            try {
                reader.close();}catch (IOException e) {e.printStackTrace();}}
            if(process!= null){
                process.destroy();
            }}
        }catch (Exception e) {
            e.printStackTrace();
            error = true;
        }

        return CompletableFuture.completedFuture(finalReward);
    }

    private void updateStatus(String taskId, String status) {
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            taskMapper.updateById(task);
        }
    }
}

