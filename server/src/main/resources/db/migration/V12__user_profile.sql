-- 账号资料：昵称与头像。
--
-- 为什么加 display_name 而不是允许改 username：
--   JWT 的 subject 就是 username，改名会让所有已签发的 token 立刻失效；
--   而且日志、限流键、越权校验都在用 username。收益低、牵连广，所以
--   username 保持只读，用昵称承担「展示名」的职责。
--
-- avatar 存的是「图案标识」而不是图片地址：
--   'auto'      由前端按 username 哈希生成首字母 + 配色（零存储、零上传）
--   'emoji:🦊'  从服务端白名单里选一个 emoji
--   这样不需要对象存储、不需要图片上传接口，nginx 也不用多挂一个目录。
ALTER TABLE app_user ADD COLUMN display_name VARCHAR(32) NULL AFTER username;
ALTER TABLE app_user ADD COLUMN avatar VARCHAR(32) NOT NULL DEFAULT 'auto' AFTER display_name;
