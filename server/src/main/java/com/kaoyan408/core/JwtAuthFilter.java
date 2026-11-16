package com.kaoyan408.core;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer &lt;token&gt;，校验通过则写入 SecurityContext。
 * token 非法时不抛异常，交由后续的 AccessDeniedHandler 统一返回 401 响应体。
 *
 * <p>这里**要看一次库**（经 {@link AccountStateCache} 缓存 60 秒）。纯验签的写法有个隐蔽
 * 问题：账号被禁用、被删除、改过密码之后，手里的旧 token 依然能用到过期，而默认有效期是
 * 7 天。现在每次请求核对账号的存在性、启用状态与 token_version。
 *
 * <p>缓存失效由登录侧负责（{@link AccountStateCache#evict}）：签发新 token 后必须让缓存
 * 立即失效，否则新 token 的 ver 会撞上缓存里的旧 ver，表现为「登录成功但请求全 401」。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AccountStateCache accountStateCache;

    public JwtAuthFilter(JwtUtil jwtUtil, AccountStateCache accountStateCache) {
        this.jwtUtil = jwtUtil;
        this.accountStateCache = accountStateCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX) && header.length() > PREFIX.length()) {
            try {
                Claims claims = jwtUtil.parse(header.substring(PREFIX.length()));
                String username = claims.getSubject();
                AccountStateCache.State state = accountStateCache.stateOf(username);
                // 版本必须严格相等：签发时用的是当时的 token_version，
                // 服务端一旦把它加一（禁用、改密、主动踢下线），旧 token 立即不再匹配。
                if (username != null && state.usable() && state.tokenVersion() == jwtUtil.tokenVersion(claims)) {
                    // 管理员与普通用户用同一套 token，权限位来自数据库（缓存 60 秒）。
                    // 这样「授予/撤销管理员」不需要用户重新登录，最多 60 秒生效。
                    List<SimpleGrantedAuthority> authorities = state.admin()
                            ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
