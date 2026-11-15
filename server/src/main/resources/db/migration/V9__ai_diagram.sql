-- ============================================================
-- V9 AI 图解缓存
--
-- 为什么要缓存：生成一张图解要 3-6 秒、上千 token。
-- 同一个知识点被反复追问（408 复习的常态）时，
-- 每次都重新生成既慢又费钱，而图的内容并不需要每次都不同。
--
-- 键用 question_hash 而不是用户 id：图是内容的属性，不是用户的属性。
-- 两个人问「进程三态转换」，应该共用同一张图。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_diagram (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  question_hash CHAR(64)     NOT NULL COMMENT 'sha256(归一化后的问题 + 知识点)，用于命中缓存',
  question      VARCHAR(500) NOT NULL COMMENT '原始问题，便于排查画错的情况',
  dsl_json      MEDIUMTEXT   NOT NULL COMMENT '已通过结构校验的图解 DSL',
  model         VARCHAR(80)  NOT NULL DEFAULT '',
  hit_count     INT          NOT NULL DEFAULT 0 COMMENT '被复用次数',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_diagram_hash (question_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 生成的图解（按问题去重缓存）';