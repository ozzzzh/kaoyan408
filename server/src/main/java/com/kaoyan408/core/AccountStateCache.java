package com.kaoyan408.core;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号状态缓存（存在性 / 是否启用 / token_version）。
 *
 * <p>独立成 bean 的原因：JwtAuthFilter 每次请求要核对账号状态，而登录成功后又必须
 * **立刻**让缓存失效——否则刚签发的 token 会带着新的 ver，却撞上缓存里的旧 ver，
 * 表现为「登录成功，但紧接着每个请求都 401」，最长持续一个缓存周期。
 *
 * <p>这个坑是在生产冒烟测试里撞到的：把 token_version 加一后重新登录，
 * 新 token 立刻被拒。所以缓存的失效入口必须和签发入口放在一起。
 */
@Component
public class AccountStateCache {

    /** 缓存时长：越短越安全、越长越省库。60 秒对单机部署是合理折中。 */
    private static final long TTL_MILLIS = 60_000L;

    /** 超过这个条数整体清空，避免用户名被大量构造时无限增长。 */
    private static final int MAX_ENTRIES = 5_000;

    public record State(boolean usable, int tokenVersion, long checkedAt) {
    }

    private final UserMapper userMapper;
    private final Map<String, State> cache = new ConcurrentHashMap<>();

    public AccountStateCache(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public State stateOf(String username) {
        long now = System.currentTimeMillis();
        State cached = cache.get(username);
        if (cached != null && now - cached.checkedAt() < TTL_MILLIS) {
            return cached;
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("LIMIT 1"));
        boolean usable = user != null && (user.getIsActive() == null || user.getIsActive() != 0);
        int version = user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        State fresh = new State(usable, version, now);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(username, fresh);
        return fresh;
    }

    /** 登录 / 注册成功后调用：让下一次请求重新读库，避免新 token 撞上旧缓存。 */
    public void evict(String username) {
        if (username != null) {
            cache.remove(username);
        }
    }
}
