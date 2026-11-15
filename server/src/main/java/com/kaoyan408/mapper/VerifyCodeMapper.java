package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.VerifyCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VerifyCodeMapper extends BaseMapper<VerifyCode> {

    /**
     * 写入或覆盖验证码，并把失败次数清零、续期。
     *
     * <p>用 upsert 而不是「先查再改」：两个并发请求同时发码时，
     * 先查再改会都判定为不存在、都走 insert，最终撞唯一键报错。
     */
    @Insert("""
            INSERT INTO verify_code (target, channel, code, attempts, expires_at, created_at)
            VALUES (#{target}, #{channel}, #{code}, 0, #{expiresAt}, NOW())
            ON DUPLICATE KEY UPDATE
                code = VALUES(code),
                attempts = 0,
                expires_at = VALUES(expires_at),
                created_at = NOW()
            """)
    int upsert(@Param("target") String target,
               @Param("channel") String channel,
               @Param("code") String code,
               @Param("expiresAt") java.time.LocalDateTime expiresAt);
}