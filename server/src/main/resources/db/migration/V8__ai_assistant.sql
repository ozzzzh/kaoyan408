-- ============================================================
-- V8 AI 学习助手：会话与消息
--
-- 设计取舍：
-- 1. 消息不走 JSON 大字段。一次对话可能几十轮，存成一行的 JSON 后
--    既没法分页，也没法按消息做索引；拆表后单条消息可独立删除与统计。
-- 2. conversation 记 user_id 与 title。title 由首条提问自动截取，
--    列表页就不用为了显示标题去扫全部消息。
-- 3. 游客会话不入库（与模拟考试一致），因此 user_id 允许为 NULL 且不建外键，
--    避免游客写库时被约束挡住。
-- 4. 只记 token 用量，不记原始请求体：排查成本够用，又不囤用户内容。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_conversation (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NULL COMMENT '登录用户；游客会话不入库，此列为空仅作兜底',
  title         VARCHAR(120) NOT NULL DEFAULT '新对话' COMMENT '首条提问截取',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_conv_user (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 会话';

CREATE TABLE IF NOT EXISTS ai_message (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT      NOT NULL,
  role            VARCHAR(16) NOT NULL COMMENT 'user / assistant',
  content         MEDIUMTEXT  NOT NULL COMMENT '正文；推理内容不落库',
  prompt_tokens   INT         NULL COMMENT '仅 assistant 消息记录，用于估算成本',
  completion_tokens INT       NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_msg_conv (conversation_id, id),
  CONSTRAINT fk_ai_msg_conv FOREIGN KEY (conversation_id)
    REFERENCES ai_conversation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 会话消息';

-- 游客试用次数：按 IP 记，与验证码限流共用 auth_rate_limit 表，
-- 不额外建表（scope='ai_guest'，key 为 IP，window 为 'all' 表示累计不重置）。
-- 登录用户的使用次数按 ai_message 统计，无需另设计数。