package org.sgj.rljobscheduler.master.service;

import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.dto.TrainingRequest;
import org.sgj.rljobscheduler.master.dto.TrainingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Service
public class TrainingService {

    @Autowired
    private TrainingTaskMapper taskMapper;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 启动训练任务 (分布式调度版本)
     */
    public TrainingResult startTraining(TrainingRequest request, Long userId, String traceId) {
        // 1. 生成任务 ID 并保存到数据库
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        int episodes = (request.getEpisodes() == null) ? 0 : request.getEpisodes();
        double learningRate = (request.getLearningRate() == null) ? 0.1 : request.getLearningRate();

        TrainingTask task = new TrainingTask(taskId, request.getAlgorithm(),
                episodes, learningRate);
        task.setUserId(userId);
        task.setStatus("PENDING");

        // 存库!
        taskMapper.insert(task);

        initTaskLogWithTraceId(taskId, traceId);

        // 2. 尝试分布式调度
        boolean scheduled = schedulerService.scheduleTask(task, traceId);
        
        if (scheduled) {
            // 更新数据库状态为 RUNNING
            task.setStatus("RUNNING");
            int rows = taskMapper.updateById(task);
            System.out.println(">>> [TrainingService] 任务已调度并更新为 RUNNING: " + taskId + ", rows=" + rows);
            
            // 推送 WebSocket 状态更新
            messagingTemplate.convertAndSend("/topic/tasks", task);
            
            return new TrainingResult(taskId, "RUNNING", 0, "Task scheduled to worker...","None");
        } else {
            System.out.println(">>> [TrainingService] 任务调度失败，保持 PENDING: " + taskId);
            return new TrainingResult(taskId, "PENDING", 0, "No available worker, task queued...","None");
        }
    }

    private void initTaskLogWithTraceId(String taskId, String traceId) {
        String effectiveTraceId = (traceId == null || traceId.isBlank()) ? "unknown" : traceId;
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File file = new File(logDir, taskId + ".log");
            if (!file.exists()) {
                file.createNewFile();
            }
            if (file.length() == 0) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                    writer.println("TRACE_ID:" + effectiveTraceId);
                }
            }
        } catch (Exception e) {
        }
    }

    public IPage<TrainingTask> getTasksByPage(int page, int size, Long userId, boolean isAdmin) {
        Page<TrainingTask> pageParam = new Page<>(page, size);
        // 按创建时间倒序排列 (最新的在最前面)
        pageParam.addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem.desc("created_at"));
        QueryWrapper<TrainingTask> queryWrapper = new QueryWrapper<>();
        if (!isAdmin && userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        return taskMapper.selectPage(pageParam, queryWrapper);
    }

    /**
     * 查询所有历史任务
     */
    public List<TrainingTask> getAllTasks() {
        return taskMapper.selectList(null);
    }
}
