package org.sgj.rljobscheduler.master.service;

import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.apache.commons.io.input.Tailer;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PostConstruct;

/**
 * 专门负责执行异步任务的执行器
 */
@Service
public class TrainingExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(TrainingExecutor.class);
    private static final String LOG_FILE = "logs/training.log";
    private static final long PROCESS_TIMEOUT_HOURS = 2; // 超时设置为 2 小时

    @Autowired
    private TrainingTaskMapper taskMapper;
    @Autowired
    private SimpMessagingTemplate messageTemplate;

    private Tailer globalTailer;

    @PostConstruct
    public void init() {
        // 启动时检查日志文件
        File logDir = new File("logs");
        if (!logDir.exists()) {
            boolean success = logDir.mkdir();
        }

        File logFile = new File(LOG_FILE);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                LOG.error("创建日志文件失败", e);
            }
        }

        // 启动全局日志监听器
        GlobalLogTailerListener listener = new GlobalLogTailerListener(messageTemplate);
        // 每 1 秒轮询一次，从文件末尾开始读取 (true)
        globalTailer = new Tailer(logFile, listener, 1000, true);
        Thread tailerThread = new Thread(globalTailer);
        tailerThread.setDaemon(true); // 守护线程，随应用关闭而关闭
        tailerThread.setName("GlobalLogTailerThread");
        tailerThread.start();
        LOG.info(">>> 全局日志监控已启动: {}", LOG_FILE);
    }

    @Async("trainingTaskExecutor")
    public CompletableFuture<Double> executeTraining(String taskId, int episodes) {
        LOG.info(">>> [TrainingExecutor] 启动训练任务: {}, 线程: {}", taskId, Thread.currentThread().getName());
        updateStatus(taskId, "RUNNING");
        AtomicReference<Double> finalRewardRef = new AtomicReference<>(0.0);
        
        try {
            TrainingTask task = taskMapper.selectById(taskId);
            if (task == null) {
                LOG.error(">>> [TrainingExecutor] 任务不存在: {}", taskId);
                return CompletableFuture.completedFuture(0.0);
            }

            // 构建 Python 命令
            List<String> command = new ArrayList<>();
            command.add("uv");
            command.add("run");
            command.add("python");
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
            pb.directory(new File(System.getProperty("user.dir")));
            // 分离 stdout 和 stderr
            pb.redirectErrorStream(false);

            Process process = pb.start();
            LOG.info(">>> [TrainingExecutor] Python 进程已启动, PID: {}", process.pid());

            // 异步消费 stdout 并写入 {taskId}.log
            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                consumeStream(process.getInputStream(), taskId, taskId + ".log", true, finalRewardRef);
            });

            // 异步消费 stderr 并写入 {taskId}error.log
            CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                consumeStream(process.getErrorStream(), taskId, taskId + "error.log", false, null);
            });

            // 等待进程结束，设置 2 小时超时防止挂起
            boolean finished = process.waitFor(PROCESS_TIMEOUT_HOURS, TimeUnit.HOURS);
            
            // 确保流消费线程也结束
            CompletableFuture.allOf(stdoutFuture, stderrFuture).get(5, TimeUnit.SECONDS);

            if (finished) {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    LOG.info(">>> [TrainingExecutor] 任务执行成功: {}", taskId);
                    writeToSharedLog(taskId, ">>> 任务成功完成");
                    
                    TrainingTask updateTask = taskMapper.selectById(taskId);
                    if (updateTask != null) {
                        updateTask.setStatus("COMPLETED");
                        updateTask.setFinalReward(finalRewardRef.get());
                        updateTask.setCompletedAt(LocalDateTime.now());
                        updateTask(updateTask);
                    }
                } else {
                    LOG.error(">>> [TrainingExecutor] 进程异常退出, ExitCode: {}", exitCode);
                    writeToSharedLog(taskId, ">>> 任务失败，退出码: " + exitCode);
                    updateStatus(taskId, "FAILED");
                }
            } else {
                // 超时强制杀灭
                LOG.warn(">>> [TrainingExecutor] 任务执行超时（{} 小时），正在强制终止: {}", PROCESS_TIMEOUT_HOURS, taskId);
                process.destroyForcibly();
                writeToSharedLog(taskId, ">>> [Timeout] 任务因执行超过 " + PROCESS_TIMEOUT_HOURS + " 小时被系统强制终止");
                updateStatus(taskId, "FAILED");
            }
        } catch (Exception e) {
            LOG.error(">>> [TrainingExecutor] 任务运行异常: {}", taskId, e);
            writeToSharedLog(taskId, ">>> [Error] 系统异常: " + e.getMessage());
            updateStatus(taskId, "FAILED");
        }

        return CompletableFuture.completedFuture(finalRewardRef.get());
    }

    /**
     * 消费输入流并写入独立日志和共享日志
     */
    private void consumeStream(InputStream inputStream, String taskId, String fileName, boolean parseReward, AtomicReference<Double> rewardRef) {
        File individualLogFile = new File("logs", fileName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             PrintWriter individualWriter = new PrintWriter(new FileWriter(individualLogFile, true))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // 1. 写入独立日志文件 (不带 TaskID 前缀，保持干净)
                individualWriter.println(line);
                individualWriter.flush();

                // 2. 写入全局共享日志文件 (带 TaskID 前缀，用于全局 Tailer)
                writeToSharedLog(taskId, line);

                // 3. 解析奖励 (仅在 stdout 且需要时)
                if (parseReward && rewardRef != null && line.startsWith("FINAL_REWARD:")) {
                    try {
                        rewardRef.set(Double.parseDouble(line.split(":")[1]));
                    } catch (Exception e) {
                        LOG.warn(">>> [TrainingExecutor] 解析奖励值失败: {}", line);
                    }
                }
            }
        } catch (IOException e) {
            LOG.error(">>> [TrainingExecutor] 读取流异常: {}", fileName, e);
        }
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
            // WebSocket 推送
            messageTemplate.convertAndSend("/topic/tasks", task);
        }
    }

    private void updateTask(TrainingTask task) {
        if (task != null) {
            taskMapper.updateById(task);
            // WebSocket 推送
            messageTemplate.convertAndSend("/topic/tasks", task);
        }
    }
}

