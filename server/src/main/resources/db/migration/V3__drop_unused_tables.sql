-- 清理建了但从未实现功能的空表。
--
-- 这些表在初始建模时预留，但对应的功能一直没做，且无任何代码引用
-- （已确认 entity/mapper/service 中零引用，表内 0 行）。
-- 保留「有表无功能」的状态会造成误导：后来者会以为功能已经实现，
-- 或在写查询时误用这些字段。需要时再随功能一起加回即可。
--
-- 保留的是：
--   memory_card    —— 背诵卡片，随后实现
--   block_progress —— 已有数据并被掌握度统计使用
--
-- 删除顺序：先删子表再删主表（plan_task 引用 plan，mock_answer 引用 mock_session）。

DROP TABLE IF EXISTS plan_task;
DROP TABLE IF EXISTS plan;
DROP TABLE IF EXISTS mock_answer;
DROP TABLE IF EXISTS mock_session;
DROP TABLE IF EXISTS feedback;