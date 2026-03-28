package org.sgj.rljobscheduler.master.service;

import org.sgj.rljobscheduler.common.netty.NettyMessage;
import org.sgj.rljobscheduler.common.proto.LogDataRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Master 端的日志管理器
 * 负责异步处理 Worker 发回的日志：持久化到本地文件 + 实时推送 WebSocket
 */
@Service
public class LogManager {

    private static final Logger LOG = LoggerFactory.getLogger(LogManager.class);
    private static final String LOG_DIR = "logs";
    private static final String SHARED_LOG = "logs/training.log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 异步处理队列
    private final BlockingQueue<LogDataRequest> logQueue = new LinkedBlockingQueue<>(10000);
    private volatile boolean running = true;
    private Thread consumerThread;

    @PostConstruct
    public void init() {
        // 确保日志目录存在
        File dir = new File(LOG_DIR);
        if (!dir.exists()) dir.mkdirs();

        // 启动消费者线程
        consumerThread = new Thread(this::processLogs, "Master-LogManager-Thread");
        consumerThread.setDaemon(true);
        consumerThread.start();
        LOG.info(">>> Master LogManager 已启动，等待日志流...");
    }

    /**
     * Netty Handler 调用此方法将日志放入队列
     */
    public void enqueueLog(LogDataRequest logData) {
        if (!logQueue.offer(logData)) {
            LOG.warn(">>> LogManager 队列已满，丢弃日志: {}", logData.getTaskId());
        }
    }

    private void processLogs() {
        while (running) {
            try {
                LogDataRequest logData = logQueue.poll(1, TimeUnit.SECONDS);
                if (logData != null) {
                    handleLog(logData);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error(">>> LogManager 处理日志异常", e);
            }
        }
    }

    private void handleLog(LogDataRequest logData) {
        String taskId = logData.getTaskId();
        String line = logData.getLogLine();
        String timestamp = DATE_FORMATTER.format(LocalDateTime.now());
        
        // 1. 持久化到本地独立日志文件 {taskId}.log
        writeToIndividualLog(taskId, line);

        // 2. 持久化到全局共享日志 (带前缀，供 Phase 8 的 Tailer 逻辑备份使用，虽然现在直接推送了)
        String formattedLine = String.format("[%s] [%s] %s", timestamp, taskId, line);
        writeToSharedLog(formattedLine);

        // 3. 旁路推送：直接通过 WebSocket 发给前端
        // 复用 Phase 8 的路径格式
        messagingTemplate.convertAndSend("/topic/logs/" + taskId, formattedLine);
    }

    private void writeToIndividualLog(String taskId, String line) {
        File file = new File(LOG_DIR, taskId + ".log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(line);
        } catch (IOException e) {
            LOG.error(">>> 写入独立日志失败: {}", taskId, e);
        }
    }

    private void writeToSharedLog(String formattedLine) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SHARED_LOG, true))) {
            writer.println(formattedLine);
        } catch (IOException e) {
            LOG.error(">>> 写入全局日志失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }
}
