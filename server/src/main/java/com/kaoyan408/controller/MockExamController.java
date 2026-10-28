package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.MockExamService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟考试。全部需要登录——考试记录挂在账号下。
 */
@RestController
@RequestMapping("/api/v1/mock")
public class MockExamController {

    private final MockExamService mockExamService;
    private final AuthService authService;

    public MockExamController(MockExamService mockExamService, AuthService authService) {
        this.mockExamService = mockExamService;
        this.authService = authService;
    }

    /** 开始考试；若该年份已有进行中的场次则直接返回它。 */
    @PostMapping("/start")
    public Result<Map<String, Object>> start(@RequestParam Integer year,
                                             @RequestParam(required = false) Integer minutes,
                                             Authentication authentication) {
        return Result.ok(mockExamService.start(requireUserId(authentication), year, minutes));
    }

    /** 恢复考试现场（刷新页面/换设备后调用）。 */
    @GetMapping("/{sessionId}")
    public Result<Map<String, Object>> state(@PathVariable Long sessionId,
                                             Authentication authentication) {
        return Result.ok(mockExamService.state(requireUserId(authentication), sessionId));
    }

    /** 保存单题答案，可反复调用。 */
    @PostMapping("/{sessionId}/answer")
    public Result<Map<String, Object>> answer(@PathVariable Long sessionId,
                                              @RequestParam String examRef,
                                              @RequestParam(required = false) String answer,
                                              Authentication authentication) {
        return Result.ok(mockExamService.saveAnswer(
                requireUserId(authentication), sessionId, examRef, answer, null));
    }

    /** 交卷并判分。 */
    @PostMapping("/{sessionId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long sessionId,
                                              @RequestParam(required = false, defaultValue = "false")
                                              boolean timedOut,
                                              Authentication authentication) {
        return Result.ok(mockExamService.submit(
                requireUserId(authentication), sessionId, timedOut));
    }

    /** 综合题自评。 */
    @PostMapping("/{sessionId}/grade")
    public Result<Map<String, Object>> grade(@PathVariable Long sessionId,
                                             @RequestParam String examRef,
                                             @RequestParam Integer score,
                                             Authentication authentication) {
        return Result.ok(mockExamService.gradeComprehensive(
                requireUserId(authentication), sessionId, examRef, score));
    }

    /** 历史成绩。 */
    @GetMapping("/history")
    public Result<Map<String, Object>> history(@RequestParam(required = false, defaultValue = "20")
                                               int limit,
                                               Authentication authentication) {
        return Result.ok(mockExamService.history(requireUserId(authentication), limit));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "请先登录。");
        }
        User user = authService.findByUsername(authentication.getName());
        if (user == null) {
            throw new BusinessException(401, "请先登录。");
        }
        return user.getId();
    }
}