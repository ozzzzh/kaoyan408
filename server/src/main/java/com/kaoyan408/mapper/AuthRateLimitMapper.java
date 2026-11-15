package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.AuthRateLimit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthRateLimitMapper extends BaseMapper<AuthRateLimit> {

    /**
     * 原子自增：不存在则建行计 1，存在则 +1。避免并发下「读到旧值再写」的丢失更新。
     *
     * <p>事务边界由 {@code RateLimitService} 控制（REQUIRES_NEW），
     * 不在 Mapper 上加注解——MyBatis 动态代理上不保证生效。
     */
    @Insert("""
            INSERT INTO auth_rate_limit (scope, key_value, window_key, counter)
            VALUES (#{scope}, #{keyValue}, #{windowKey}, 1)
            ON DUPLICATE KEY UPDATE counter = counter + 1
            """)
    int increment(@Param("scope") String scope,
                  @Param("keyValue") String keyValue,
                  @Param("windowKey") String windowKey);

    @Select("""
            SELECT IFNULL(counter, 0) FROM auth_rate_limit
            WHERE scope = #{scope} AND key_value = #{keyValue} AND window_key = #{windowKey}
            """)
    Integer current(@Param("scope") String scope,
                    @Param("keyValue") String keyValue,
                    @Param("windowKey") String windowKey);
}