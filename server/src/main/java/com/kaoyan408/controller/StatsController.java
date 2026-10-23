package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.StatsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return Result.ok(statsService.dashboard(userIdOf(authentication)));
    }

    /** 知识点级掌握：找出该补的章节（薄弱点 + 未开始）。 */
    @GetMapping("/points")
    public Result<Map<String, Object>> points(@RequestParam(required = false) String subject,
                                              Authentication authentication) {
        return Result.ok(statsService.pointMastery(userIdOf(authentication), subject));
    }

    private Long userIdOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }
}