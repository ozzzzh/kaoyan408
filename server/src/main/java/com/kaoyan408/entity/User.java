package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    /** 1 启用 / 0 禁用。禁用后无法登录，已有 token 也会失效。 */
    private Integer isActive;

    /** 1 管理员。当前仅作标记，权限控制留待需要时启用。 */
    private Integer isAdmin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
