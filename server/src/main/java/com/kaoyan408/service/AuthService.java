package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号相关业务：注册、登录、按用户名取用户。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("LIMIT 1"));
    }

    public User login(String username, String password) {
        User user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误。");
        }
        return user;
    }

    public User register(String username, String email, String password) {
        if (findByUsername(username) != null) {
            throw new BusinessException(400, "该用户名已被注册。");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        userMapper.insert(user);
        return user;
    }

    public User requireByUsername(String username) {
        User user = findByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        return user;
    }
}