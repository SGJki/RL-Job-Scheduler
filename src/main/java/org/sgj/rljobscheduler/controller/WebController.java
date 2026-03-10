package org.sgj.rljobscheduler.controller;

import org.sgj.rljobscheduler.service.TrainingService;
import org.sgj.rljobscheduler.dto.TrainingRequest;
import org.sgj.rljobscheduler.entity.TrainingTask;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebController {
    @Autowired
    private TrainingService trainingService;

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "14") int size) {
        // 1. 准备一个空的 Request 对象绑定到表单
        model.addAttribute("request", new TrainingRequest());

        // 分页查询
        IPage<TrainingTask> taskPage = trainingService.getTasksByPage(page, size);
        model.addAttribute("taskPage", taskPage);

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

    // 局部刷新接口：只返回表格片段 (Fragment)
    @GetMapping("/tasks/fragment")
    public String getTaskFragment(Model model,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "14") int size) {

        IPage<TrainingTask> taskPage = trainingService.getTasksByPage(page, size);
        model.addAttribute("taskPage", taskPage);

        // 只渲染表格片段
        return "index :: task-list";
    }

}
