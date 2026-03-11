package org.sgj.rljobscheduler.service;

import org.sgj.rljobscheduler.entity.TrainingTask;
import org.sgj.rljobscheduler.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.PostConstruct;

/**
 * 专门负责执行异步任务的执行器
 * 把耗时逻辑放在这里，避免 Self-Invocation 问题
 */
@Service
public class TrainingExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(TrainingExecutor.class);
    private static final String LOG_FILE = "logs/training.log";
    @Autowired
    private TrainingTaskMapper taskMapper;
    @PostConstruct
    public void init() {
        // 启动时检查日志文件
        File logDir = new File("logs");
        if (!logDir.exists()) {
        boolean success = logDir.mkdir();

    }}

    @Async // 关键注解：告诉 Spring 这是一个异步方法，要丢给线程池跑
    public CompletableFuture<Double> executeTraining(String taskId, int episodes) {
//        System.out.println("Start training task: " + taskId +  Thread.currentThread().getName());
        LOG.info("Start training task: " + taskId);
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
        pb.redirectErrorStream(true);

        // 3. 启动进程
        try {
            process = pb.start();

            try {
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    // 写入共享日志文件 (带上 TaskID 前缀)
                    writeToSharedLog(taskId, line);

                    // 解析关键结果
                    if (line.startsWith("FINAL_REWARD:")) {
                        try {
                            finalReward = Double.parseDouble(line.split(":")[1]);
                        } catch (NumberFormatException e) {
                            writeToSharedLog(taskId, "[Error] 解析 Reward 失败: " + line);
                        }}}}catch (Exception e) {
                    e.printStackTrace();
                    error = true;
                }finally {
                reader.close();
                }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOG.info(">>> [后台线程] Python 脚本执行成功: {}", taskId);
                writeToSharedLog(taskId, ">>> Task Completed Successfully");
                // 更新数据库 (需要重新查询以确保数据最新)
                TrainingTask updateTask = taskMapper.selectById(taskId);
                if (updateTask != null) {
                    updateTask.setStatus("COMPLETED");
                    updateTask.setFinalReward(finalReward);
                    updateTask.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(updateTask);
                }}
             else {
                LOG.error(">>> [后台线程] Python 脚本异常退出, Code: {}", exitCode);
                writeToSharedLog(taskId, ">>> Task Failed with Exit Code: " + exitCode);
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
     * 线程安全地写入共享日志文件
     */
    private synchronized void writeToSharedLog(String taskId, String content) {


        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String time = LocalDateTime.now().toString();
            // 格式: [时间] [Task-ID] 内容
            writer.printf("[%s] [%s] %s%n", time, taskId, content);
        } catch (Exception e) {
            LOG.error("写入日志失败", e);
        }
    }

    private void updateStatus(String taskId, String status) {
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            taskMapper.updateById(task);
        }
    }
}

