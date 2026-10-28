-- 模拟考试：会话与作答。
--
-- 设计要点：
-- 1) 会话记录 started_at 与 duration_minutes，剩余时间由服务端算，
--    不依赖前端倒计时——刷新页面或换设备都能回到正确的剩余时间。
-- 2) 作答单独成表，支持考试中途随时保存，交卷前也不会丢答案。
-- 3) 选择题在交卷时自动判分；综合题是主观题，交卷后由用户自评，
--    因此分别记录 auto_score（系统判）与 self_score（用户评）。

CREATE TABLE mock_session (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    exam_year        INT          NOT NULL,
    started_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 限时（分钟）。0 表示不限时。
    duration_minutes INT          NOT NULL DEFAULT 180,
    -- ongoing 进行中 / submitted 已交卷
    status           VARCHAR(16)  NOT NULL DEFAULT 'ongoing',
    submitted_at     TIMESTAMP    NULL,
    -- 选择题自动得分（满分 80）
    auto_score       INT          NOT NULL DEFAULT 0,
    -- 综合题用户自评得分（满分 70）
    self_score       INT          NOT NULL DEFAULT 0,
    -- 交卷时是否因超时被强制交卷
    timed_out        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_mock_user_status (user_id, status),
    KEY idx_mock_user_started (user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mock_answer (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL,
    -- 选择题存选项键（A/B/C/D）；综合题存用户自己写的要点（可为空）
    answer      VARCHAR(512) NULL,
    -- 选择题：交卷时写入 1/0；综合题：留空
    correct     TINYINT     NULL,
    -- 综合题自评分（0..该题满分）
    self_score  INT         NULL,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一场考试同一道题只留一行，重复作答走更新
    UNIQUE KEY uk_mock_answer (session_id, question_id),
    KEY idx_mock_answer_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;