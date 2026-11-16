package com.kaoyan408.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证 / 无权限访问受保护资源 → 统一响应体（HTTP 仍为 200）。
 *
 * <p>Spring Security 6 把「匿名访问受保护资源」交给 AuthenticationEntryPoint，
 * 把「已登录但无权限」交给 AccessDeniedHandler，两者都要实现，
 * 否则匿名请求会落到默认处理器，返回空 body 的 403。
 *
 * <p>两者的业务码要区分开：
 * <ul>
 *   <li>没登录 / token 失效 → 401，前端据此引导登录；</li>
 *   <li>已登录但没有该权限（例如普通用户访问 /api/v1/admin）→ 403，
 *       前端不能把它当「掉线」处理，否则会莫名其妙跳登录页。</li>
 * </ul>
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler, AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"msg\":\"认证失败，无法访问系统资源。\",\"data\":null}";

    private static final String FORBIDDEN_BODY =
            "{\"code\":403,\"msg\":\"权限不足，该操作仅限管理员。\",\"data\":null}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        // 走到这里说明认证已通过，只是权限不够
        write(response, FORBIDDEN_BODY);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 匿名访问受保护资源；但也可能是「已认证但缺权限」被转到这里，用当前身份区分
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        write(response, authenticated ? FORBIDDEN_BODY : UNAUTHORIZED_BODY);
    }

    private void write(HttpServletResponse response, String body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
