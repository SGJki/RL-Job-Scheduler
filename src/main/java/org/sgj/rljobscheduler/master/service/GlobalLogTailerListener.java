package org.sgj.rljobscheduler.master.service;

import org.apache.commons.io.input.TailerListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局日志监听器：监听 logs/training.log 的增量，
 * 提取 TaskID 并通过 WebSocket 推送至对应频道。
 */
public class GlobalLogTailerListener extends TailerListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalLogTailerListener.class);
    private final SimpMessagingTemplate messagingTemplate;

    // 匹配日志格式中的 [Task-ID] 部分，例如: [2026-03-28T...] [task-123] ...
    // taskId 的格式为 8 位 UUID 截取，包含字母和数字
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("\\[([a-fA-F0-9]{8})\\]");

    public GlobalLogTailerListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void handle(String line) {
        if (line == null || line.isEmpty()) return;

        // 提取方括号中的内容作为 TaskID
        Matcher matcher = TASK_ID_PATTERN.matcher(line);
        while (matcher.find()) {
            String taskId = matcher.group(1);
            // 推送到该任务的专属频道
            messagingTemplate.convertAndSend("/topic/logs/" + taskId, line);
        }
    }

    @Override
    public void handle(Exception ex) {
        LOG.error("Global Log Tailer Error", ex);
    }
}
