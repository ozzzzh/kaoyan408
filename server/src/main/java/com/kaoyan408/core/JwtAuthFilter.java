package com.kaoyan408.core;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析 Authorization: Bearer &lt;token&gt;，校验通过则写入 SecurityContext。
 * token 非法时不抛异常，交由后续的 AccessDeniedHandler 统一返回 401 响应体。
 *
 * <p>这里**要看一次库**（带 60 秒缓存）。纯验签的写法有个隐蔽问题：
 * 账号被禁用、被删除、改过密码之后，手里的旧 token 依然能用到过期，
 * 而默认有效期是 7 天。现在每次请求核对 app_user 的存在性、启用状态与 token_version，
 * 缓存只是为了让高频请求不必每跳查一次库——代价是禁用生效有最多 60 秒延迟。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    /** 账号状态的缓存时长：越短越安全、越长越省库。60 秒对单机部署是合理折中。 */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    /** 缓存超过这个条数就整体清空，避免用户名被大量构造时无限增长。 */
    private static final int CACHE_MAX_ENTRIES = 5_000;

    private record AccountState(boolean usable, int tokenVersion, long checkedAt) {
    }

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final Map<String, AccountState> cache = new ConcurrentHashMap<>();

    public JwtAuthFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX) && header.length() > PREFIX.length()) {
            try {
                Claims claims = jwtUtil.parse(header.substring(PREFIX.length()));
                String username = claims.getSubject();
                AccountState state = stateOf(username);
                // 版本必须严格相等：签发时用的是当时的 token_version，
                // 服务端一旦把它加一（禁用、改密、主动踢下线），旧 token 立即不再匹配。
                if (username != null && state.usable() && state.tokenVersion() == jwtUtil.tokenVersion(claims)) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(new SimpleGrantedAuthority("USER")));
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

    private AccountState stateOf(String username) {
        long now = System.currentTimeMillis();
        AccountState cached = cache.get(username);
        if (cached != null && now - cached.checkedAt() < CACHE_TTL_MILLIS) {
            return cached;
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("LIMIT 1"));
        boolean usable = user != null && (user.getIsActive() == null || user.getIsActive() != 0);
        int version = user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        AccountState fresh = new AccountState(usable, version, now);
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(username, fresh);
        return fresh;
    }
}
