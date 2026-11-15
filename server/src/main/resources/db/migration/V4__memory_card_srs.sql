-- 背诵卡片：补全记忆曲线所需的字段。
--
-- 原表只有 (user_id, block_id, difficulty, next_review_date)，不足以支持
-- 间隔重复：无法区分「第几次复习」「上次答得怎么样」，也就无法推进间隔。
--
-- 卡片来源：知识块中的 callout（提示/要点，共 188 个）与 formula（公式，22 个）——
-- 这两类本身就是「需要记住的结论」，比另造一套题库更贴近真实复习。
-- 卡片内容不冗余存文本：仍以 block_id 指向知识块，正文改动可自动同步。

ALTER TABLE memory_card
    ADD COLUMN box INT NOT NULL DEFAULT 0
        COMMENT '记忆盒序号 0-7，决定下次间隔';

ALTER TABLE memory_card
    ADD COLUMN review_count INT NOT NULL DEFAULT 0
        COMMENT '累计复习次数';

ALTER TABLE memory_card
    ADD COLUMN correct_count INT NOT NULL DEFAULT 0
        COMMENT '复习中自评「记住了」的次数';

ALTER TABLE memory_card
    ADD COLUMN last_reviewed_at TIMESTAMP NULL DEFAULT NULL
        COMMENT '上次复习时间';

ALTER TABLE memory_card
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        COMMENT '加入背诵的时间';

-- 同一用户同一块只能有一张卡片
ALTER TABLE memory_card
    ADD UNIQUE KEY uk_card_user_block (user_id, block_id);

-- 到期复习的查询走 (user_id, next_review_date)。
-- 注意：建表时已有同列的 idx_card_user，此处为历史遗留的重复索引，
-- 已应用故不修改本文件，由 V5 删除。
ALTER TABLE memory_card
    ADD KEY idx_card_user_due (user_id, next_review_date);