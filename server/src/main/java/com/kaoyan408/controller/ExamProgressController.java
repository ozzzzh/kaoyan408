package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.ExamProgressService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 真题卷进度：每卷的练习进度 + 模考成绩。
 *
 * <p>单独一个 controller 而不是塞进 ExamController：那边负责卷面内容读取，
 * 这里负责个人历史聚合，拆开以后两边的依赖不会互相牵扯。
 */
@RestController
@RequestMapping("/api/v1/exams")
public class ExamProgressController {

    private final ExamProgressService examProgressService;
    private final AuthService authService;

    public ExamProgressController(ExamProgressService examProgressService, AuthService authService) {
        this.examProgressService = examProgressService;
        this.authService = authService;
    }

    @GetMapping("/progress")
    public Result<List<Map<String, Object>>> progress(Authentication authentication) {
        return Result.ok(examProgressService.progressOf(userIdOf(authentication)));
    }

    /** 未登录返回 null（游客也能看卷子，只是没有进度）。 */
    private Long userIdOf(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        if (user == null) {
            return null;
        }
        return user.getId();
    }
}