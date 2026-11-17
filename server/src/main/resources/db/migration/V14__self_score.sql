-- 综合题自评得分。
--
-- 背景：408 综合题占 70 分（150 分制的 47%），但系统此前只能「标记已完成」，
-- 提交时甚至会因为标准答案为空而被判为正确（correct=true）。
--
-- 综合题本来就不该自动判分：答案是一段论述/代码，无法字符串比对。
-- 这里存考生自评的得分（0..题目满分），由 submit 之后的自评接口写入；
-- 统计口径上综合题不参与正确率，只参与「已覆盖分值」。
ALTER TABLE question_attempt ADD COLUMN self_score INT NULL AFTER answer;
