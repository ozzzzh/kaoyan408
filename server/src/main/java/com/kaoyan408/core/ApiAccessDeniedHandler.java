package com.kaoyan408.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证 / 无权限访问受保护资源 → 统一 401 响应体（HTTP 仍为 200）。
 *
 * <p>Spring Security 6 把「匿名访问受保护资源」交给 AuthenticationEntryPoint，
 * 把「已登录但无权限」交给 AccessDeniedHandler，两者都要实现，
 * 否则匿名请求会落到默认处理器，返回空 body 的 403。
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler, AuthenticationEntryPoint {

    private static final String BODY =
            "{\"code\":401,\"msg\":\"认证失败，无法访问系统资源。\",\"data\":null}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response);
    }

    private void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(BODY);
    }
}