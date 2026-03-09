package org.sgj.rljobscheduler.dto;

import java.time.LocalDateTime;

/**
 * 训练结果
 * 返回给前端的数据
 */
public class TrainingResult {
    private String taskId;
    private String status;
    private double finalReward;
    private String completedAt;
    private String message;
    private String errormessage;

    public TrainingResult(String taskId, String status, double finalReward, String message, String errorMessage) {
        this.taskId = taskId;
        this.status = status;
        this.finalReward = finalReward;
        this.message = message;
        this.completedAt = LocalDateTime.now().toString();
        this.errormessage = errorMessage;
    }

    // Getters
    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public double getFinalReward() { return finalReward; }
    public String getCompletedAt() { return completedAt; }
    public String getMessage() { return message; }
    public String getErrormessage() { return errormessage; }
}

