package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.MemoryCardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 背诵卡片。所有接口都需要登录：卡片是用户私有数据。
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryCardController {

    private final MemoryCardService cardService;
    private final AuthService authService;

    public MemoryCardController(MemoryCardService cardService, AuthService authService) {
        this.cardService = cardService;
        this.authService = authService;
    }

    public record ReviewRequest(Long cardId, Boolean remembered) {
    }

    /** 到期复习队列。 */
    @GetMapping("/due")
    public Result<Map<String, Object>> due(@RequestParam(required = false) String subject,
                                           Authentication authentication) {
        return Result.ok(cardService.due(requireUserId(authentication), subject));
    }

    /** 背诵概览（总数 / 到期 / 牢固 / 分科目）。 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(Authentication authentication) {
        return Result.ok(cardService.summary(requireUserId(authentication)));
    }

    /** 复习一张卡片：记住了 / 没记住。 */
    @PostMapping("/review")
    public Result<Map<String, Object>> review(@RequestBody ReviewRequest request,
                                              Authentication authentication) {
        if (request == null || request.cardId() == null) {
            throw new BusinessException(400, "缺少 cardId。");
        }
        return Result.ok(cardService.review(requireUserId(authentication), request.cardId(),
                Boolean.TRUE.equals(request.remembered())));
    }

    /** 把知识点下的要点/公式加入背诵。 */
    @PostMapping("/add")
    public Result<Map<String, Object>> add(@RequestParam(required = false) String pointId,
                                           @RequestParam(required = false) String subject,
                                           @RequestParam(required = false) Integer limit,
                                           Authentication authentication) {
        return Result.ok(cardService.add(requireUserId(authentication), pointId, subject, limit));
    }

    /** 移除一张卡片。 */
    @PostMapping("/remove")
    public Result<Map<String, Object>> remove(@RequestParam Long cardId,
                                              Authentication authentication) {
        return Result.ok(cardService.remove(requireUserId(authentication), cardId));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "请先登录后再使用背诵功能。");
        }
        User user = authService.findByUsername(authentication.getName());
        if (user == null) {
            throw new BusinessException(401, "请先登录后再使用背诵功能。");
        }
        return user.getId();
    }
}