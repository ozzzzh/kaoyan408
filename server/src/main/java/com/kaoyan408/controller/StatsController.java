package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.StatsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;
    private final AuthService authService;

    public StatsController(StatsService statsService, AuthService authService) {
        this.statsService = statsService;
        this.authService = authService;
    }

    /** 学习看板：题库总量、完成度、四科掌握度、连续打卡、近 14 天曲线。 */
    @GetMapping("/dash")
    public Result<Map<String, Object>> dash(Authentication authentication) {
        Long userId = null;
        if (authentication != null) {
            User user = authService.findByUsername(authentication.getName());
            userId = user == null ? null : user.getId();
        }
        return Result.ok(statsService.dashboard(userId));
    }
}