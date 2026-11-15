-- 考研 408 工作台：初始表结构（内容库 + 用户与做题数据）
-- 内容种子由 scripts/import-content.mjs 生成到 resources/seed/content.sql，
-- 由 ContentSeedConfig 在题库为空时加载（刻意不入 Flyway 版本链）。

CREATE TABLE exam_year (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    year   INT          NOT NULL UNIQUE,
    title  VARCHAR(255) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id          BIGINT       NOT NULL,
    exam_ref         VARCHAR(64)  NOT NULL UNIQUE,
    exam_number      INT          NOT NULL,
    subject          VARCHAR(8)   NOT NULL,
    chapter_name     VARCHAR(128) DEFAULT NULL,
    question_type    VARCHAR(16)  NOT NULL DEFAULT 'choice',
    score            INT          NOT NULL,
    stem             MEDIUMTEXT   NOT NULL,
    answer           VARCHAR(255) DEFAULT NULL,
    difficulty       TINYINT      DEFAULT NULL,
    explanation      MEDIUMTEXT   DEFAULT NULL,
    tags_json        VARCHAR(512) DEFAULT NULL,
    image_paths_json VARCHAR(512) DEFAULT NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    KEY idx_question_exam (exam_id),
    KEY idx_question_subject (subject)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question_option (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT      NOT NULL,
    option_key  VARCHAR(4)  NOT NULL,
    option_text MEDIUMTEXT  NOT NULL,
    UNIQUE KEY uq_option (question_id, option_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question_subquestion (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    sub_number  INT    NOT NULL,
    score       INT    DEFAULT NULL,
    description MEDIUMTEXT DEFAULT NULL,
    KEY idx_subquestion (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question_knowledge (
    question_id BIGINT       NOT NULL,
    block_id    VARCHAR(128) NOT NULL,
    PRIMARY KEY (question_id, block_id),
    KEY idx_qk_block (block_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE knowledge_point (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id   VARCHAR(128) NOT NULL UNIQUE,
    subject    VARCHAR(8)   NOT NULL,
    title      VARCHAR(255) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    KEY idx_point_subject (subject, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE knowledge_block (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id       VARCHAR(128) NOT NULL,
    block_id       VARCHAR(128) NOT NULL UNIQUE,
    subpoint_id    VARCHAR(128) DEFAULT NULL,
    subpoint_title VARCHAR(255) DEFAULT NULL,
    block_order    INT          NOT NULL DEFAULT 0,
    type           VARCHAR(32)  NOT NULL,
    content_json   MEDIUMTEXT   NOT NULL,
    KEY idx_block_point (point_id, block_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(128) DEFAULT NULL,
    password_hash VARCHAR(128) NOT NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question_attempt (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL,
    result      TINYINT     NOT NULL,
    hints_used  TINYINT     NOT NULL DEFAULT 0,
    duration_ms INT         DEFAULT NULL,
    answer      VARCHAR(255) DEFAULT NULL,
    answered_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_attempt (user_id, question_id),
    KEY idx_attempt_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE daily_stat (
    user_id   BIGINT NOT NULL,
    stat_date DATE   NOT NULL,
    questions INT    NOT NULL DEFAULT 0,
    correct   INT    NOT NULL DEFAULT 0,
    minutes   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE block_progress (
    user_id           BIGINT       NOT NULL,
    block_id          VARCHAR(128) NOT NULL,
    questions_total   INT          NOT NULL DEFAULT 0,
    questions_done    INT          NOT NULL DEFAULT 0,
    questions_correct INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, block_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    started_at  TIMESTAMP NOT NULL,
    finished_at TIMESTAMP DEFAULT NULL,
    total       INT       DEFAULT 0,
    correct     INT       DEFAULT 0,
    score       INT       DEFAULT 0,
    KEY idx_session_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_answer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer      VARCHAR(255) DEFAULT NULL,
    duration_ms INT          DEFAULT NULL,
    is_correct  TINYINT      NOT NULL DEFAULT 0,
    KEY idx_answer_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE plan (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    days       INT          NOT NULL DEFAULT 30,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plan_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE plan_task (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id      BIGINT       NOT NULL,
    day          INT          NOT NULL,
    subject      VARCHAR(8)   DEFAULT NULL,
    chapter_name VARCHAR(128) DEFAULT NULL,
    questions    INT          NOT NULL DEFAULT 0,
    done         TINYINT      NOT NULL DEFAULT 0,
    KEY idx_task_plan (plan_id, day)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE memory_card (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    block_id         VARCHAR(128) NOT NULL,
    difficulty       INT          NOT NULL DEFAULT 1,
    next_review_date DATE         DEFAULT NULL,
    KEY idx_card_user (user_id, next_review_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE feedback (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      DEFAULT NULL,
    type        VARCHAR(32) NOT NULL,
    block_id    VARCHAR(128) DEFAULT NULL,
    question_id BIGINT      DEFAULT NULL,
    body        TEXT        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'new',
    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    KEY idx_feedback_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;