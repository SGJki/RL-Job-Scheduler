package org.sgj.rljobscheduler.master.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.sgj.rljobscheduler.master.dto.TimeResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot!";
    }

    // 原版接口 (简单字符串)
    @GetMapping("/time")
    public String time() {
        return "当前服务器时间: " + LocalDateTime.now();
    }

    /**
     * 个性化接口: 返回 JSON 格式
     * 访问示例: http://localhost:8081/time-custom?name=Trae
     * @param name URL 参数，默认值为 "Guest"
     */
    @GetMapping("/time-custom")
    public TimeResponse customTime(@RequestParam(value = "name", defaultValue = "Guest") String name) {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 自定义格式化 (比如: 2026年03月06日 18:30:00)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
        String timeStr = now.format(formatter);

        // 3. 构造业务逻辑 (根据时间判断问候语)
        String greeting;
        int hour = now.getHour();
        if (hour < 12) {
            greeting = "早上好! 🌞";
        } else if (hour < 18) {
            greeting = "下午好! ☕";
        } else {
            greeting = "晚上好! 🌙";
        }

        // 4. 返回对象 (Spring 会自动转成 JSON)
        return new TimeResponse(timeStr, greeting, name);
    }
}
