package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.JwtUtil;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    public record LoginRequest(@NotBlank(message = "不能为空") String username,
                               @NotBlank(message = "不能为空") String password) {
    }

    public record RegisterRequest(@NotBlank(message = "不能为空")
                                  @Size(min = 3, max = 32, message = "长度需为 3-32 位") String username,
                                  @Email(message = "格式不正确") String email,
                                  @NotBlank(message = "不能为空")
                                  @Size(min = 6, max = 64, message = "长度需为 6-64 位") String password) {
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.username(), request.password());
        return Result.ok(tokenPayload(user));
    }

    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.email(), request.password());
        return Result.ok(tokenPayload(user));
    }

    @GetMapping("/user/current")
    public Result<Map<String, Object>> current(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        User user = authService.requireByUsername(authentication.getName());
        return Result.ok(profile(user));
    }

    private Map<String, Object> tokenPayload(User user) {
        String token = jwtUtil.sign(user.getUsername(), user.getEmail());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("expiresAt", jwtUtil.expiresAt(token));
        payload.put("expiresIn", jwtUtil.getExpireSeconds());
        payload.put("user", profile(user));
        return payload;
    }

    private Map<String, Object> profile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }
}