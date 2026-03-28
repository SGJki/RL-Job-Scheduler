package org.sgj.rljobscheduler.master.controller;

import org.sgj.rljobscheduler.master.service.TrainingService;
import org.sgj.rljobscheduler.master.dto.TrainingRequest;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.dto.TrainingResult;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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

        Long userId = getCurrentUserId();
        // 如果未登录且不是管理员，显示空列表 (或者可以在前端提示登录)
        if (userId == null && !isAdmin()) {
            model.addAttribute("taskPage", new Page<TrainingTask>());
        } else {
            IPage<TrainingTask> taskPage = trainingService.getTasksByPage(page, size, userId, isAdmin());
            model.addAttribute("taskPage", taskPage);
        }

        // 3. 返回模板文件名 (resources/templates/index.html)
        return "index";
    }

    // 处理表单提交
    @PostMapping("/submit")
    @ResponseBody // 直接返回 JSON 结果，不跳转页面
    public TrainingResult submitTask(@ModelAttribute TrainingRequest request) {
        return trainingService.startTraining(request, getCurrentUserId());
    }

    // 局部刷新接口：只返回表格片段 (Fragment)
    @GetMapping("/tasks/fragment")
    public String getTaskFragment(Model model,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "14") int size) {

        IPage<TrainingTask> taskPage = trainingService.getTasksByPage(page, size, getCurrentUserId(), isAdmin());
        model.addAttribute("taskPage", taskPage);

        // 只渲染表格片段
        return "index :: task-list";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // Helper: Get Current User ID
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }

    // Helper: Check if Admin
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }


}
