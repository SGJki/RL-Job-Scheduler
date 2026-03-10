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
        // 3. 日志分流配置
        // 确保 logs 目录存在
        File logDir = new File("logs");
        if (!logDir.exists()) logDir.mkdirs();

        // 标准输出 (stdout) -> logs/task_{id}.log
        File outFile = new File(logDir, "task_" + taskId + ".log");
        pb.redirectOutput(outFile);

        // 错误输出 (stderr) -> logs/task_{id}_error.log
        File errFile = new File(logDir, "task_" + taskId + "_error.log");
        pb.redirectError(errFile);

        // 3. 启动进程
        try {
            process = pb.start();
            System.out.println(">>> [Process] 日志文件已创建: " + outFile.getAbsolutePath());
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                finalReward = parseRewardFromLog(outFile);
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
                System.err.println(">>> [Error Log] 请查看: " + errFile.getAbsolutePath());
                updateStatus(taskId, "FAILED");
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }finally {
            // 4. 释放资源
            if(process!= null){
                process.destroy();
            }}
        }catch (Exception e) {
            e.printStackTrace();
            error = true;
        }

        return CompletableFuture.completedFuture(finalReward);
    }
    /**
     * 辅助方法：从日志文件中读取 FINAL_REWARD
     */
    private double parseRewardFromLog(File logFile) {
        double reward = 0.0;
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("FINAL_REWARD:")) {
                    try {
                        reward = Double.parseDouble(line.split(":")[1]);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return reward;
    }
    private void updateStatus(String taskId, String status) {
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            taskMapper.updateById(task);
        }
    }
}

