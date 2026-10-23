-- 移除重复索引。
--
-- V4 新增的 idx_card_user_due (user_id, next_review_date) 与建表时已有的
-- idx_card_user 列完全相同，属于纯重复索引：不仅浪费空间，每次写入都要多维护一棵 B+ 树。
--
-- 为什么用新迁移而不是直接删掉 V4 里那一行：V4 已经应用到已有环境的数据库，
-- 修改历史迁移会改变 checksum 导致 Flyway 校验失败、应用无法启动
--（本项目已在 V1 与 V4 上各踩到一次）。因此按 Flyway 规则追加迁移清理。
--
-- 代价：全新部署时 V4 仍会先建出该索引（MySQL 会打一条 duplicate index 警告），
-- 紧接着由本迁移删除。警告仅在这一次迁移期间出现，属可接受代价。

DROP INDEX idx_card_user_due ON memory_card;