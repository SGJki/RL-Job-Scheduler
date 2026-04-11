package org.sgj.rljobscheduler.master.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InterviewController {

    @GetMapping("/interview")
    public String interview(Model model) {
        addUserInfo(model);
        return "interview";
    }

    @GetMapping("/training")
    public String training(Model model) {
        addUserInfo(model);
        return "training";
    }

    @GetMapping("/knowledge")
    public String knowledge(Model model) {
        addUserInfo(model);
        return "knowledge";
    }

    private void addUserInfo(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            Long userId = null;
            if (auth.getDetails() instanceof Long) {
                userId = (Long) auth.getDetails();
            }
            model.addAttribute("username", username);
            model.addAttribute("userId", userId);
        }
    }
}
