-- 错题重做闭环：记录「曾经答错」与「重做后已掌握」，并支持移出错题本。
--
-- 背景：question_attempt 每个 (user, question) 只有一行，重复作答会覆盖 result。
-- 一旦答对，先前答错的证据就丢了，错题本无法区分
-- 「从没错过」和「错过但已订正」，也无法做「已掌握的错题」回顾。
--
-- 注意：MySQL 8.0 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（那是 MariaDB 语法），
-- 因此这里用普通 ADD COLUMN。Flyway 保证每个迁移只执行一次，无需幂等写法。

ALTER TABLE question_attempt
    ADD COLUMN wrong_count INT NOT NULL DEFAULT 0
        COMMENT '累计答错次数，答对不清零';

ALTER TABLE question_attempt
    ADD COLUMN retry_correct_count INT NOT NULL DEFAULT 0
        COMMENT '答错之后再次作答且答对的次数';

ALTER TABLE question_attempt
    ADD COLUMN mastered_at TIMESTAMP NULL DEFAULT NULL
        COMMENT '错题订正成功（移出错题本）的时间';

ALTER TABLE question_attempt
    ADD COLUMN first_wrong_at TIMESTAMP NULL DEFAULT NULL
        COMMENT '首次答错时间，用于错题本排序';

-- 依据既有数据回填：当前 result=0 的记录视为错过一次
UPDATE question_attempt
SET wrong_count = 1,
    first_wrong_at = COALESCE(first_wrong_at, answered_at)
WHERE result = 0;

CREATE INDEX idx_attempt_user_mastered
    ON question_attempt (user_id, mastered_at);