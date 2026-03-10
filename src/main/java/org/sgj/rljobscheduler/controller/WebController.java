package org.sgj.rljobscheduler.controller;

import org.sgj.rljobscheduler.service.TrainingService;
import org.sgj.rljobscheduler.dto.TrainingRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class WebController {
    @Autowired
    private TrainingService trainingService;

    @GetMapping("/")
    public String index(Model model) {
        // 1. 准备一个空的 Request 对象绑定到表单
        model.addAttribute("request", new TrainingRequest());

        // 2. 获取所有历史任务列表
        model.addAttribute("tasks", trainingService.getAllTasks());

        // 3. 返回模板文件名 (resources/templates/index.html)
        return "index";
    }

    // 处理表单提交
    @PostMapping("/submit")
    public String submitTask(@ModelAttribute TrainingRequest request) {
        // 调用 Service 启动任务
        trainingService.startTraining(request);

        // 重定向回首页 (刷新列表)
        return "redirect:/";
    }

}
