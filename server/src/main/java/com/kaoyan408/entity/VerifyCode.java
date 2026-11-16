package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱验证码。同一目标只保留一条有效记录，重新发送即覆盖。
 *
 * <p>不用 Redis 存：本项目没有 Redis，为两处计数引入一整个中间件不划算。
 * 用一张小表 + 过期时间实现同样的语义，单机部署足够。
 */
@Data
@TableName("verify_code")
public class VerifyCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收件目标（当前是邮箱）。 */
    private String target;

    /** email / sms */
    private String channel;

    /** 验证码用途：register（注册）/ reset-password（修改密码）。校验必须带同样的用途。 */
    private String purpose;

    private String code;

    /** 已校验失败次数，达到上限即作废。 */
    private Integer attempts;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}