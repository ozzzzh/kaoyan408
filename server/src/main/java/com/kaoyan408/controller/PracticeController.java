package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.PracticeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 刷题队列与错题本。
 * 队列只下发题目的轻量信息（用于导航与列表），题干与解析仍走单题接口，
 * 保证「未作答不下发答案」的规则不被绕过。
 */
@RestController
@RequestMapping("/api/v1/practice")
public class PracticeController {

    private final PracticeService practiceService;
    private final AuthService authService;

    public PracticeController(PracticeService practiceService, AuthService authService) {
        this.practiceService = practiceService;
        this.authService = authService;
    }

    /**
     * 取刷题队列。
     * 参数均可组合：year / subject / blockId / pointId / scope。
     * scope: all（默认）| wrong（错题本）| untouched（未做）。
     */
    @GetMapping("/queue")
    public Result<Map<String, Object>> queue(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String blockId,
            @RequestParam(required = false) String pointId,
            @RequestParam(defaultValue = "all") String scope,
            Authentication authentication) {
        Long userId = userIdOf(authentication);
        return Result.ok(practiceService.queue(userId, year, subject, blockId, pointId, scope));
    }

    /** 错题本概览：总错题数 + 分科目分布。 */
    @GetMapping("/wrong-book")
    public Result<Map<String, Object>> wrongBook(Authentication authentication) {
        Long userId = userIdOf(authentication);
        return Result.ok(practiceService.wrongBook(userId));
    }

    /** 刷题入口的汇总（各入口的题量），用于工作台展示。 */
    @GetMapping("/entrances")
    public Result<Map<String, Object>> entrances(Authentication authentication) {
        Long userId = userIdOf(authentication);
        return Result.ok(practiceService.entrances(userId));
    }

    private Long userIdOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }
}