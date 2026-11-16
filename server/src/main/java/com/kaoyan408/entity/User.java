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

    /**
     * 展示用昵称。username 是登录名、且被 JWT subject 引用，因此不允许修改；
     * 用户想改的「名字」落在这里。
     */
    private String displayName;

    /**
     * 头像标识：'auto'（按 username 生成首字母 + 配色）或 'emoji:<单个 emoji>'。
     * 刻意不存图片地址 —— 见 V12 迁移里的说明。
     */
    private String avatar;

    private String email;

    private String passwordHash;

    /** 1 启用 / 0 禁用。禁用后无法登录，已有 token 也会失效。 */
    private Integer isActive;

    /** 1 管理员。当前仅作标记，权限控制留待需要时启用。 */
    private Integer isAdmin;

    /**
     * 令牌版本，签发 JWT 时写进 ver claim，每次请求核对。
     * 把它加一即可让该用户所有旧 token 立刻失效（禁用账号、改密码、踢下线）。
     */
    private Integer tokenVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
