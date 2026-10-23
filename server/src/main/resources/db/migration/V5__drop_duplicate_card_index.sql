-- 移除重复索引。
--
-- V4 新增的 idx_card_user_due (user_id, next_review_date) 与建表时已有的
-- idx_card_user 列完全相同，属于纯重复索引：不仅浪费空间，每次写入都要多维护一棵 B+ 树。
-- V4 已应用，按 Flyway 规则不修改历史迁移，因此在此单独清理。
-- 保留建表时就存在的 idx_card_user，删除 V4 新增的那个。

DROP INDEX idx_card_user_due ON memory_card;