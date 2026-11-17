# 协作约定

## 一、推送与部署必须逐次获得授权

**默认不推送、不部署。** 每次开发完成后：

1. 我在**本地**完成改动并自测（构建通过、浏览器验证、回归脚本）
2. 我**展示改动摘要**：改了什么、为什么、验证结果
3. **你明确同意后**，我才执行：
   - `git push` 到 GitHub
   - 以及（如需要）部署到服务器

**"开始做" 不等于 "允许推送"。** 未经你针对本次改动的明确同意，我不推送、不部署。

### 提交与推送可以是两个阶段

本地 `git commit` 是你的代码存档，不等于对外发布。所以：

- **默认做法**：本地完成 → 你说可以 → 我一次 commit + push
- **如果你希望更细**：我可以先只 commit（不 push），等你确认后再 push

### 部署同理

- 推到 GitHub ≠ 部署到服务器
- 部署（`git pull` + `docker compose up -d --build`）需要**单独授权**
- 一次授权只对本次改动有效，不延续到下次

---

## 二、每次需要授权的动作清单

明确列出哪些动作需要你点头：

| 动作 | 是否需要授权 |
| --- | --- |
| 改本地代码/文件 | 不需要（"开始做"即授权） |
| 本地构建、启动本地服务、跑回归脚本 | 不需要 |
| `git commit`（仅本地存档） | 不需要 |
| **`git push`** | **需要** |
| **部署到服务器**（pull / build / up） | **需要** |
| 在服务器上执行只读排查（`ps`/`df`/`curl` 等） | 不需要 |
| 在服务器上执行写操作（重启、改配置、删数据） | **需要** |

---

## 三、部署相关的事实（避免每次重复确认）

- 服务器：`10.0.219.241`，**SSH 端口 17080**（不是 22）
- 项目目录：`/mnt/workspace/kaoyan408`（独立目录，不在 ipeagent 里）
- Compose 项目名：`kaoyan408`（**必须带 `-p kaoyan408`**，避免影响同机 ipeagent）
- 访问地址：`http://10.0.219.241:17073`
- 数据卷：`kaoyan408_kaoyan_db_data`
- **禁止** `docker compose down -v`（会删数据卷）
- 同机还有 ipeagent（13 容器）、firecrawl（5 容器）等，**不得影响**

---

## 四、当前进行中的工作

M2 模拟考试功能，已确认的设计：

- 综合题：选择题自动判分（80 分）+ 综合题自评（70 分）
- 限时：默认 180 分钟，可自选
- 范围：只做整卷模拟

**状态**：方案已确认，尚未开始编码。

---

## 五、多会话并行作业约定（2026-11 新增）

背景：曾经有两个会话在**同一个工作目录**里并行工作，各自执行 `git add -A`，
结果 A 未提交的改动被卷进 B 的提交里（`AdminView.vue`、`AdminService.java` 都发生过），
提交信息与实际内容完全对不上；更糟的是两边都能对同一台生产机发版，
一次交错就没人说得清线上到底是哪一版。

### 5.1 一个会话一个工作目录（首选）

```bash
# 主工作区（保持 main 分支）
D:\kaoyan\kaoyan408-main        branch: main

# 第二个会话（独立目录，独立分支，共用同一个 .git）
git worktree add D:\kaoyan\kaoyan408-ai2 -b ai2
D:\kaoyan\kaoyan408-ai2         branch: ai2
```

- 两边各写各的目录，`git add -A` 再也卷不走对方文件；
- 提交进同一份历史但各在分支上，最后 `git merge ai2` 收口；
- 新建的 worktree **没有 node_modules / dist**，第一次用先 `npm --prefix client install`；
- 用完清理：`git worktree remove D:\kaoyan\kaoyan408-ai2`（分支保留）。

### 5.2 禁止 `git add -A` / `git add .` / `git commit -a`

即使是单目录作业，也只 add 自己这次改的**具体文件路径**。提交前必须跑：

```bash
git status --short      # 看到不是自己改的文件 -> 不要提交
```

### 5.3 部署锁（同一台机器只能一个人发版）

```bash
python scripts/deploy-lock.py status      # 谁在发、多久了
python scripts/deploy-lock.py acquire --owner <会话名> --ttl 900
python scripts/deploy-lock.py release     # 发版结束（无论成败）都要释放
```

- `acquire` 返回非零 + `LOCKED` 说明别人正在发版：**等它结束，不要抢**；
- 锁 15 分钟自动过期，避免某个会话崩了把部署永久锁死；
- 锁文件在服务器 `/mnt/workspace/kaoyan408/.deploy.lock`。

### 5.4 「整树同步」发版有排他性

当两边 git 历史对不上时，发版会用 `git archive HEAD` + 覆盖服务器工作树。
**谁后同步，线上就是谁的内容**，所以必须在锁保护下做，且：
发版前先 `git status` 确认自己这边是最新、`git log -1` 记录版本号，
发版后把 commit 短哈希写进交付说明（线上到底哪一版必须能回答）。

### 5.5 共享资源需要先声明

服务器 `.env`（含 `JWT_SECRET`、管理员密码）、`docker-compose.yml`、
`server/src/main/resources/db/migration/*`、内容种子 `seed/content.sql`
属于共享资源：改之前先在对话里说清楚，不要"顺手改一下"。

---

## 六、主线职责（2026-11-16 起）

背景：两条支线各自发版，20 分钟里线上的前端被互相覆盖了两次 —— 后发版的人
用自己 worktree 构建的 dist 覆盖前一个人的，前一个人的功能直接"消失"，
而所有验证（接口、后端）都还是好的，只有前端悄悄回到了旧版。

### 6.1 只有主线合入、只有主线发版

| 角色 | 可以做什么 |
| --- | --- |
| **主线**（`D:\kaoyan\kaoyan408-main`，分支 `main`） | 合入支线、跑全量回归、构建并发版、回滚 |
| **支线**（`ai2` / `next` / 新开的 worktree） | 写代码、提交到自己的分支、本地验证；**不发版** |

支线要把改动上线，只有一条路：**告诉主线「可以合了」**，由主线 merge → 回归 → 发版。
支线不再直接 `deploy.py` / 直接换服务器 dist。

### 6.2 支线开工前必做

```bash
git merge main        # 或 rebase；总之别在旧基线上开工
```

否则你构建的 dist 里没有主线的新功能——这次前端被覆盖就是这么发生的。

### 6.3 主线发版检查清单（每次照做）

1. `git status` 干净；`git log -1` 记下要发的提交
2. `python scripts/deploy-lock.py acquire --owner main`
3. 前端构建后**记下 entry 哈希**：`grep -o 'assets/index-[^"]*\.js' client/dist/index.html`
4. 同步源码 → 重建 api → 换 dist → `--force-recreate web`
5. **发版后自检**：`curl -s https://<域名>/ | grep -o 'assets/index-[^"]*\.js'`
   必须等于第 3 步记录的哈希。**不相等就说明被人覆盖了**，立即重发并查明来源
6. `python scripts/deploy-lock.py release`
7. 在交付说明里写明：线上版本（entry 哈希 + 服务器 HEAD）

### 6.4 合并约定

- 纯行尾/空白类提交（如「行尾归一」）与功能改动撞在同一文件时：**取主线内容，
  行尾统一为 LF**，不要为了消除冲突把功能改回去。
- 合并后必须重跑：`npm run typecheck` / `npm test` / `mvn test`，
  任何一项失败就不往下走。
- 分支合入后，支线那边要 `git merge main` 跟上，避免长期落后。

### 6.5 支线禁止事项（硬规则）

| 禁止 | 原因 |
| --- | --- |
| `deploy.py` / 任何直接改服务器 dist 的操作 | 会覆盖别人刚上线的前端，且没有哈希自检 |
| 改 `scripts/deploy.py`、`web/nginx.conf`、`docker-compose.yml` | 发版与反代属于主线；要改先提，由主线改 |
| 改 `server/src/main/resources/db/migration/*`、`seed/content.sql`、服务器 `.env` | 生产数据不可逆，必须由主线统一排期 |
| `git add -A` / `git commit -a` | 会把别人未提交的改动卷进自己的提交（已发生 3 次） |
| 在旧基线上构建 dist | 产物会缺主线功能（前端被覆盖事故的根因） |

支线要验证线上效果，只能用**只读**手段（`curl` 接口、浏览器看页面），
不能改服务器文件。要本地起服务就用独立的 compose 项目名和错开的端口
（前端 5174+、后端 8081+），不要占 18080/8080。

### 6.6 发版口径（对外说法统一）

- 支线说「做完了」≠ 已上线。上线要等主线合入并发布。
- 主线发布后才算上线，并且必须同时给出两个标识：**entry 哈希 + 服务器 HEAD**。
- 线上异常时先查三件事：`curl /` 的 entry 哈希是否等于最近一次主线构建、
  `/api/v1/health` 的 db 状态、浏览器是否还在用旧壳（Service Worker）。

### 6.7 静态资源与 Service Worker（2026-11-17 事故）

现象：用户点左上角 logo 打不开落地页，而其他页面正常。

原因：发版是**整包替换 `client/dist`**，旧哈希 chunk 被删；用户浏览器里的
Service Worker 还缓存着旧外壳，点开一个没缓存过的路由时去请求旧 chunk → 404 →
动态 import 失败，路由静默不跳转。

因此主线发版需要做到：**新旧 assets 并存**（旧文件保留两代再清理），
而不是覆盖式替换。支线不要试图自己"修一下 dist"，这类问题只能由发布流程解决。


