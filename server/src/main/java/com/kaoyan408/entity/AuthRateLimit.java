package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限流计数。按 (scope, key, window) 唯一，每次写用原子 upsert 自增。
 *
 * <p>用数据库而不是内存 Map：内存计数在多线程下要自己加锁，
 * 且重启即清零，等于限流失效。表结构下重启不会绕过限制。
 */
@Data
@TableName("auth_rate_limit")
public class AuthRateLimit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** target（按邮箱）或 ip（按来源 IP）。 */
    private String scope;

    private String keyValue;

    /** 时间窗标识，如 2026102815。 */
    private String windowKey;

    private Integer counter;

    private LocalDateTime updatedAt;
}