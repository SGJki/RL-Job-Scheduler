package org.sgj.rljobscheduler.master.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.time.LocalDateTime;

/**
 * 实体类 (Entity)
 * 对应数据库中的一张表 "training_task"
 */
@TableName("training_task")
public class TrainingTask {

    @TableId(type = IdType.AUTO)
    private Long id; // 任务 ID (主键)，数据库自增

    private String algorithm;
    private int episodes;
    private double learningRate;

    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private double finalReward;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Long userId;

    // 必须有无参构造函数
    public TrainingTask() {}

    public TrainingTask(String algorithm, int episodes, double learningRate) {
        this.algorithm = algorithm;
        this.episodes = episodes;
        this.learningRate = learningRate;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.errorMessage = "";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public int getEpisodes() { return episodes; }
    public void setEpisodes(int episodes) { this.episodes = episodes; }

    public double getLearningRate() { return learningRate; }
    public void setLearningRate(double learningRate) { this.learningRate = learningRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getFinalReward() { return finalReward; }
    public void setFinalReward(double finalReward) { this.finalReward = finalReward; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

