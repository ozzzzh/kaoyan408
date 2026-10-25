package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.UserDataService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人数据管理：查看条数、导出、清空。
 *
 * <p>全部要求登录——这些数据都挂在账号下，游客没有可管理的数据。
 */
@RestController
@RequestMapping("/api/v1/user/data")
public class UserDataController {

    private final UserDataService userDataService;
    private final AuthService authService;

    public UserDataController(UserDataService userDataService, AuthService authService) {
        this.userDataService = userDataService;
        this.authService = authService;
    }

    /** 各类数据的条数，供界面显示「将删除 N 条」并在为空时禁用操作。 */
    @GetMapping("/counts")
    public Result<Map<String, Object>> counts(Authentication authentication) {
        return Result.ok(userDataService.counts(requireUserId(authentication)));
    }

    /** 导出全部个人数据（JSON）。 */
    @GetMapping("/export")
    public Result<Map<String, Object>> export(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.ok(userDataService.export(userId, authentication.getName()));
    }

    /**
     * 清空指定类别的数据。
     *
     * @param scope attempts / memory / all
     */
    @DeleteMapping("/clear")
    public Result<Map<String, Object>> clear(@RequestParam String scope,
                                            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String target = scope == null ? "" : scope.trim();
        if (!"attempts".equals(target) && !"memory".equals(target) && !"all".equals(target)) {
            throw new BusinessException(400, "scope 只能是 attempts、memory 或 all。");
        }
        return Result.ok(userDataService.clear(userId, target));
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