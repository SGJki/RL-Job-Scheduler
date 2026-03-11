package org.sgj.rljobscheduler.controller;

import org.sgj.rljobscheduler.dto.AuthRequest;
import org.sgj.rljobscheduler.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest, HttpServletResponse response) throws Exception {
        try {
            String token = authService.login(authRequest.getUsername(), authRequest.getPassword());

            // 设置 HttpOnly Cookie 方便前端 (Thymeleaf/AJAX) 使用
            Cookie cookie = new Cookie("jwt_token", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 1天
            response.addCookie(cookie);

            Map<String,Object> result = new HashMap<>();
            result.put("token",token);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
    }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt_token", null);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 立即删除
        response.addCookie(cookie);
        return ResponseEntity.ok("Logout successful");
        }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest authRequest) throws Exception {
        try {
            return  ResponseEntity.ok(authService.register(authRequest.getUsername(), authRequest.getPassword()));
        }catch (Exception e){
        return ResponseEntity.badRequest().body(e.getMessage());}
    }

}
