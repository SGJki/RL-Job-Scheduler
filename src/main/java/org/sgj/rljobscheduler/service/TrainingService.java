package org.sgj.rljobscheduler.service;

import org.sgj.rljobscheduler.mapper.TrainingTaskMapper;
import org.sgj.rljobscheduler.entity.TrainingTask;
import org.sgj.rljobscheduler.dto.TrainingRequest;
import org.sgj.rljobscheduler.dto.TrainingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Service
public class TrainingService {

    @Autowired
    private TrainingTaskMapper taskMapper;

    @Autowired
    private TrainingExecutor trainingExecutor;

    /**
     * 启动训练任务 (持久化到数据库)
     */
    public TrainingResult startTraining(TrainingRequest request) {
        // 1. 生成任务 ID 并保存到数据库
        String taskId = UUID.randomUUID().toString().substring(0, 8);


        int episodes = (request.getEpisodes() == null) ? 0 : request.getEpisodes();
        double learningRate = (request.getLearningRate() == null) ? 0.1 : request.getLearningRate();

        TrainingTask task = new TrainingTask(taskId, request.getAlgorithm(),
                episodes, learningRate);

        // 存库! 状态 = PENDING
        taskMapper.insert(task);
        System.out.println(">>> [TrainingService] 任务已创建,准备后台执行: " + taskId);

        CompletableFuture<Double> future = trainingExecutor.executeTraining(taskId, episodes);

        future.exceptionally(e -> {
            System.out.println(">>> [TrainingService]"+ taskId + " 任务失败, 错误信息: " + e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            taskMapper.insert(task);
            return null;
        });
        future.thenAccept(reward -> {
            System.out.println(">>> [TrainingService]"+ taskId + " 任务完成, 收益: " + reward);

        });

        return new TrainingResult(taskId, "PENDING", 0, "please wait...","None");
    }

    public IPage<TrainingTask> getTasksByPage(int page, int size) {
        Page<TrainingTask> pageParam = new Page<>(page, size);
        // 按创建时间倒序排列 (最新的在最前面)
        pageParam.addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem.desc("created_at"));
        return taskMapper.selectPage(pageParam, null);
    }

    /**
     * 查询所有历史任务
     */
    public List<TrainingTask> getAllTasks() {
        return taskMapper.selectList(null);
    }
}
