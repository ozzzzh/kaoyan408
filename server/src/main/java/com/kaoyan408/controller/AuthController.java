package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.JwtUtil;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.MailCodeService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final MailCodeService mailCodeService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, MailCodeService mailCodeService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.mailCodeService = mailCodeService;
        this.jwtUtil = jwtUtil;
    }

    public record LoginRequest(@NotBlank(message = "不能为空") String username,
                               @NotBlank(message = "不能为空") String password) {
    }

    public record SendMailCodeRequest(@NotBlank(message = "不能为空")
                                      @Email(message = "格式不正确") String email) {
    }

    /**
     * 注册请求。字段长度只做「上限」兜底，具体规则在 AuthService 里判断，
     * 避免同一套规则在两个地方各写一遍、改一处漏一处。
     */
    public record RegisterRequest(@NotBlank(message = "不能为空")
                                  @Size(max = 50, message = "长度不能超过 50 位") String username,
                                  @NotBlank(message = "不能为空")
                                  @Email(message = "格式不正确") String email,
                                  @NotBlank(message = "不能为空")
                                  @Size(max = 128, message = "长度不能超过 128 位") String password,
                                  @NotBlank(message = "不能为空")
                                  @Size(min = 6, max = 6, message = "验证码为 6 位数字") String code) {
    }

    /** 发送邮箱验证码。 */
    @PostMapping("/auth/send-mail-code")
    public Result<Map<String, Object>> sendMailCode(@Valid @RequestBody SendMailCodeRequest request,
                                                    HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        int cooldown = mailCodeService.send(request.email(), ip);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cooldownSeconds", cooldown);
        // 让前端能如实提示：mock 模式下不真的发信，验证码在服务端日志里。
        // 演示与答辩时这一点必须说清楚，否则用户会一直等一封不会来的邮件。
        payload.put("mock", mailCodeService.isMockMode());
        payload.put("message", mailCodeService.isMockMode()
                ? "验证码已生成（当前为演示模式，验证码打印在服务端日志）"
                : "验证码已发送，请查收邮件");
        return Result.ok(payload);
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.username(), request.password());
        return Result.ok(tokenPayload(user));
    }

    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.username(), request.email(), request.password(), request.code());
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

    /** 取真实客户端 IP：经 nginx 反代时 remoteAddr 是容器地址，必须读 X-Forwarded-For。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
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
        profile.put("isAdmin", user.getIsAdmin() != null && user.getIsAdmin() == 1);
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }
}