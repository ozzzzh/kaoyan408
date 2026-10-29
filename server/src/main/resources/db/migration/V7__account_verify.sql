-- 账号体系升级：邮箱验证码注册 + 三层限流 + 账号启用状态。
--
-- 三件事：
-- 1) 账号增加 is_active / is_admin，支持禁用与管理标记；
-- 2) 邮箱验证码注册所需的验证码存储表；
-- 3) 发码与登录的限流计数表。
--
-- 为什么不用 Redis：本项目没有 Redis 依赖，为两处计数引入一整个服务不划算。
-- 用 MySQL 表 + 时间窗字段实现同样的语义，单机部署完全够用。

-- ---------- 1. 账号字段 ----------

-- 已有用户全部视为启用，否则升级后会集体无法登录
ALTER TABLE app_user ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1;

ALTER TABLE app_user ADD COLUMN is_admin TINYINT NOT NULL DEFAULT 0;

-- 邮箱唯一：注册时按邮箱查重，重复邮箱会让验证码体系失去意义。
-- 历史数据里 email 可能为空串，MySQL 唯一索引下空串会互相冲突，
-- 所以先把空串统一成 NULL（NULL 不参与唯一约束比较）。
UPDATE app_user SET email = NULL WHERE email = '';

ALTER TABLE app_user ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uk_app_user_email ON app_user (email);

-- ---------- 2. 验证码 ----------

CREATE TABLE verify_code (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    -- 收件目标，当前只有邮箱；预留手机号复用这张表
    target      VARCHAR(254) NOT NULL,
    -- 渠道：email / sms
    channel     VARCHAR(16)  NOT NULL DEFAULT 'email',
    code        VARCHAR(6)   NOT NULL,
    -- 已校验失败次数，达到上限即作废
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一目标同一渠道只需一条有效记录，重新发送即覆盖
    UNIQUE KEY uk_verify_target (channel, target),
    KEY idx_verify_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- 3. 限流 ----------

CREATE TABLE auth_rate_limit (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    -- scope：target（按邮箱/手机号计）或 ip（按来源 IP 计）
    scope       VARCHAR(16)  NOT NULL,
    key_value   VARCHAR(190) NOT NULL,
    -- 时间窗标识，如 2026102815（按小时）或 20261028（按天）
    window_key  VARCHAR(32)  NOT NULL,
    counter     INT          NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rate_scope_key_window (scope, key_value, window_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 冷却不单独建表：用 verify_code.created_at 判断「60 秒内是否刚发过」，
-- 少一张表，且与验证码生命周期天然一致。