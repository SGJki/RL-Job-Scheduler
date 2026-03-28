package org.sgj.rljobscheduler.service;

import org.apache.commons.io.input.TailerListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 监听日志文件变化，并将特定任务的日志推送至 WebSocket
 */
public class TaskLogTailerListener extends TailerListenerAdapter {

    private final String taskId;
    private final SimpMessagingTemplate messagingTemplate;
    private final String destination;

    public TaskLogTailerListener(String taskId, SimpMessagingTemplate messagingTemplate) {
        this.taskId = taskId;
        this.messagingTemplate = messagingTemplate;
        this.destination = "/topic/logs/" + taskId;
    }

    @Override
    public void handle(String line) {
        // 日志格式示例: [2026-03-28T10:40:28.123] [task-123] Some log message
        // 我们过滤出属于当前 taskId 的行
        if (line != null && line.contains("[" + taskId + "]")) {
            messagingTemplate.convertAndSend(destination, line);
        }
    }

    @Override
    public void handle(Exception ex) {
        messagingTemplate.convertAndSend(destination, "[Error] 日志读取异常: " + ex.getMessage());
    }
}
