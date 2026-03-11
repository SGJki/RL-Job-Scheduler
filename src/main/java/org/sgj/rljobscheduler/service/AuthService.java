package org.sgj.rljobscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.sgj.rljobscheduler.entity.User;
import org.sgj.rljobscheduler.mapper.UserMapper;
import org.sgj.rljobscheduler.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 注册
     */
    public String register(String username, String password) {
        if (userMapper.selectCount(new QueryWrapper<User>().eq("username", username)) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User(username, passwordEncoder.encode(password), "USER");
        userMapper.insert(user);
        return "注册成功";
    }

    /**
     * 登录
     */
    public String login(String username, String password) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        // 签发 Token
        return jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
    }
}
