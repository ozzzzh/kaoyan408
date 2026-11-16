package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AdminService;
import com.kaoyan408.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员接口。
 *
 * <p>整个前缀在 SecurityConfig 里要求 {@code hasRole("ADMIN")}，
 * 所以这里不需要再逐个方法判断权限；唯一要注意的是「操作自己」这类业务规则，
 * 那部分在 {@link AdminService} 里。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    public AdminController(AdminService adminService, AuthService authService) {
        this.adminService = adminService;
        this.authService = authService;
    }

    public record ActiveRequest(boolean active) {
    }

    public record AdminRequest(boolean admin) {
    }

    public record PasswordRequest(String password) {
    }

    /** 站点概览。响应里的 readOnly 告诉前端「当前是只读演示管理员」。 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(Authentication authentication) {
        return Result.ok(adminService.overview(authentication == null ? null : authentication.getName()));
    }

    /** 账号列表（分页 + 关键字）。 */
    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.users(keyword, page, size));
    }

    /** 启用 / 禁用账号。 */
    @PatchMapping("/users/{id}/active")
    public Result<Map<String, Object>> setActive(@PathVariable Long id, @RequestBody ActiveRequest request,
                                                 Authentication authentication) {
        return Result.ok(adminService.setActive(operatorName(authentication), operatorId(authentication), id, request.active()));
    }

    /** 授予 / 撤销管理员。 */
    @PatchMapping("/users/{id}/admin")
    public Result<Map<String, Object>> setAdmin(@PathVariable Long id, @RequestBody AdminRequest request,
                                                Authentication authentication) {
        return Result.ok(adminService.setAdmin(operatorName(authentication), operatorId(authentication), id, request.admin()));
    }

    /** 重置密码（不传则生成随机密码并在响应里返回一次）。 */
    @PostMapping("/users/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                     @RequestBody(required = false) PasswordRequest request,
                                                     Authentication authentication) {
        return Result.ok(adminService.resetPassword(
                operatorName(authentication), id, request == null ? null : request.password()));
    }

    /** 清空某个账号的练习数据（演示账号复位用）。 */
    @PostMapping("/users/{id}/clear-data")
    public Result<Map<String, Object>> clearData(@PathVariable Long id, Authentication authentication) {
        return Result.ok(adminService.clearUserData(operatorName(authentication), id));
    }


    private String operatorName(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "认证失败，无法访问系统资源。");
        }
        return authentication.getName();
    }

    private Long operatorId(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "认证失败，无法访问系统资源。");
        }
        User operator = authService.findByUsername(authentication.getName());
        if (operator == null) {
            throw new BusinessException(401, "认证失败，无法访问系统资源。");
        }
        return operator.getId();
    }
}
