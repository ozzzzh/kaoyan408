-- 通用键值表：目前只用来记录内容种子的指纹（seed.content.sha256）。
--
-- 为什么需要它：种子加载原来靠「题库是否为空」判断，导致两个问题——
--   1) 内容更新后题库并不为空，除非手动加 force，否则永远不刷新；
--   2) force 重灌会让自增 id 变化，孤儿化用户作答记录。
-- 有了指纹，可以做到「内容变了自动重灌、没变就跳过」，
-- 并配合 ContentSeedConfig 的用户数据守卫避免误删历史。
CREATE TABLE app_meta (
    k          VARCHAR(64)  NOT NULL,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (k)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;