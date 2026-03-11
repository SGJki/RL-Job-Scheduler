package org.sgj.rljobscheduler.controller;

import org.sgj.rljobscheduler.service.TrainingService;
import org.sgj.rljobscheduler.entity.TrainingTask;
import org.sgj.rljobscheduler.dto.TrainingResult;
import org.sgj.rljobscheduler.dto.TrainingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/**
 * 控制器层 (Controller)
 * 只负责接收请求，不做复杂业务逻辑
 */
@RestController
@RequestMapping("/api") // 所有接口都以 /api 开头
public class TrainingController {

    @Autowired
    private TrainingService trainingService;

    /**
     * 启动训练接口
     */
    @PostMapping("/train")
    public TrainingResult train(@RequestBody TrainingRequest request) {
        System.out.println(">>> [Controller] 收到训练请求");
        return trainingService.startTraining(request, request.getUserId());
    }

    /**
     * 查询所有历史任务
     * GET /api/tasks
     */
    @GetMapping("/tasks")
    public List<TrainingTask> getAllTasks() {
        return trainingService.getAllTasks();
    }
}
