package com.kaoyan408.service;

import com.kaoyan408.mapper.AuthRateLimitMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 限流计数。独立成一个 bean，是为了让 {@code REQUIRES_NEW} 真正生效。
 *
 * <p>为什么必须独立事务：调用方的典型用法是「累加计数 → 立刻抛业务异常」。
 * 若计数和业务在同一事务里，抛异常会连计数一起回滚，限流就永远不会触发。
 * 这条踩过：项目实测连续输错 6 次密码，计数表里始终是 0 行。
 *
 * <p>为什么单独成类而不是在 Mapper 上加注解：Mapper 是 MyBatis 动态代理，
 * 事务拦截器不保证作用在它上面；真实的 Spring bean 才能稳定拿到新事务。
 */
@Service
public class RateLimitService {

    private final AuthRateLimitMapper mapper;

    public RateLimitService(AuthRateLimitMapper mapper) {
        this.mapper = mapper;
    }

    /** 自增并立即提交，不受调用方事务回滚影响。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int increment(String scope, String key, String window) {
        mapper.increment(scope, key, window);
        Integer value = mapper.current(scope, key, window);
        return value == null ? 0 : value;
    }

    /** 只读当前计数，不开启新事务。 */
    public int current(String scope, String key, String window) {
        Integer value = mapper.current(scope, key, window);
        return value == null ? 0 : value;
    }

    /** 清零（登录成功后调用）。 */
    @Transactional
    public void reset(String scope, String key) {
        mapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                com.kaoyan408.entity.AuthRateLimit>()
                .eq("scope", scope)
                .eq("key_value", key));
    }
}