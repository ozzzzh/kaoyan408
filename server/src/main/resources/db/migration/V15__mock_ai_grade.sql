-- 综合题 AI 评改留痕。
--
-- 为什么存两列：ai_score 是模型给的「建议分」，self_score（已有）是用户最终确认的分。
-- 两者都留下，才能回答"AI 评得准不准"——将来要转全自动，得先有 MAE 数据。
ALTER TABLE mock_answer ADD COLUMN ai_score INT NULL AFTER self_score;
ALTER TABLE mock_answer ADD COLUMN ai_rubric MEDIUMTEXT NULL AFTER ai_score;
ALTER TABLE mock_answer ADD COLUMN ai_model VARCHAR(64) NULL AFTER ai_rubric;
ALTER TABLE mock_answer ADD COLUMN ai_at TIMESTAMP NULL AFTER ai_model;