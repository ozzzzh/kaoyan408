-- 验证码增加「用途」，避免不同业务共用一个码。
--
-- 原来只有 (channel, target) 唯一：注册验证码和「修改密码」验证码会互相覆盖，
-- 更危险的是**同一个码可以跨用途使用** —— 攻击者用注册接口拿到明文码（他自己收的邮箱），
-- 就能拿来通过改密码校验。
--
-- 现在唯一键扩成 (channel, target, purpose)，且校验时必须带同样的 purpose。
--
-- 注意：MySQL 没有「CREATE UNIQUE KEY 表名 (...)" 这种独立语句，
-- 加索引必须写成 ALTER TABLE ... ADD UNIQUE KEY（第一版写错，导致本迁移执行失败，
-- 见 README「修改已应用的迁移会导致服务无法启动」一节的处理流程）。
ALTER TABLE verify_code ADD COLUMN purpose VARCHAR(24) NOT NULL DEFAULT 'register' AFTER channel;

-- 旧唯一键是 (channel, target)，必须先删掉才能建带 purpose 的新唯一键。
ALTER TABLE verify_code DROP INDEX uk_verify_target;

ALTER TABLE verify_code ADD UNIQUE KEY uk_verify_target_purpose (channel, target, purpose);
