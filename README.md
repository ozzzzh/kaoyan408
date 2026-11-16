# 题知链 408 · 考研 408 备考平台

> 把 **846 道真题** 钉在 **147 个知识点** 上：刷题、看正文、查掌握度、问 AI，四件事共用一套知识索引。

[![CI](https://github.com/ozzzzh/kaoyan408/actions/workflows/ci.yml/badge.svg)](https://github.com/ozzzzh/kaoyan408/actions/workflows/ci.yml)
[![真题](https://img.shields.io/badge/真题-2009–2026%20·%2018%20卷-2f6fe4)]()
[![题量](https://img.shields.io/badge/题库-846%20题-2f6fe4)]()
[![知识点](https://img.shields.io/badge/知识点-147%20个-0b7c73)]()
[![后端](https://img.shields.io/badge/后端-Spring%20Boot%203%20·%20Java%2017-green)]()
[![前端](https://img.shields.io/badge/前端-Vue%203%20·%20Vite%20·%20Tailwind%204-42b883)]()
[![许可](https://img.shields.io/badge/内容许可-CC%20BY%204.0-lightgrey)]()

## 界面预览

**工作台** —— 四科进度、掌握度、热力图与今日待办在同一屏。

![工作台](docs/screenshots/01-dashboard.jpg)

**知识点正文** —— 侧栏知识点树 + 正文 + 本节真题入口 + 「问问 AI」。

![知识点正文](docs/screenshots/02-knowledge.jpg)

**AI 学习助手** —— 回答末尾给出知识点卡片，点击直接跳到正文；也能从正文反向提问。

![AI 学习助手](docs/screenshots/09-ai-chat.jpg)

<details>
<summary>展开更多截图（刷题 / 真题卷 / 模拟考试 / 学习看板 / 落地页）</summary>

**AI 学习助手（空态）** —— 游客可试用 3 次，登录后能结合错题与掌握度。

![AI 助手](docs/screenshots/06-ai.jpg)

**刷题** —— 逐题作答，交卷即出解析与关联知识块。

![刷题](docs/screenshots/03-practice.jpg)

**真题卷** —— 2009–2026 共 18 卷，可按年份整卷查看。

![真题卷](docs/screenshots/04-exams.jpg)

**模拟考试** —— 整卷计时，选择题自动判分，综合题自评。

![模拟考试](docs/screenshots/05-mock.jpg)

**学习看板** —— 按知识点掌握度排序，找出该补的章节。

![学习看板](docs/screenshots/07-stats.jpg)

**落地页** —— 站外介绍页，未登录可从 Logo 或「了解题知链」进入。

![落地页](docs/screenshots/08-landing.jpg)

</details>

## 功能

| 模块 | 说明 |
| --- | --- |
| **刷题** | 846 道真题，按科目 / 年份 / 未做 / 错题筛选；作答即判分，附解析与关联知识块 |
| **知识点正文** | 147 个知识点、1634 个知识块；KaTeX 公式就地渲染，42 个知识块配 manim 动画 |
| **真题卷** | 18 卷（2009–2026），每卷 47 题（选择 40 + 综合 7） |
| **模拟考试** | 整卷限时作答，选择题自动判分（80 分），综合题对照参考答案自评（70 分） |
| **背诵** | 间隔重复（Leitner 8 盒，0/1/2/4/7/15/30/60 天） |
| **错题本** | 错题重做闭环，订正后标记掌握并移出队列 |
| **学习看板** | 四科掌握度、逐知识点正确率、90 天热力图 |
| **AI 学习助手** | 流式对话，注入用户掌握度与错题；回答带知识点跳转卡片 |
| **搜索** | 知识点 / 题目 / 正文全文检索 |

**免注册即可试做**：游客能完成刷题、模拟考试与 3 次 AI 对话，只是不保存记录。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 8 + TypeScript + Tailwind CSS 4 + Pinia + vue-router |
| 渲染 | KaTeX（公式）、marked + DOMPurify（正文）、manim-web（动画） |
| 后端 | Spring Boot 3.4 + Spring Security + MyBatis-Plus + Flyway |
| 存储 | MySQL 8（内容与做题数据） |
| AI | Command Code Provider API（OpenAI 兼容），SSE 流式 |
| 部署 | Docker Compose（nginx + api + mysql） |

## 持续集成

每次推送与 PR 触发 `.github/workflows/ci.yml`，三个 job 并行：

| job | 内容 | 依赖 |
| --- | --- | --- |
| 构建 | 前端 `vite build` + 后端 `mvn compile` | 无 |
| 数据库验证 | 建库 → 应用迁移 → 灌内容种子 → 跑内容完整性与动画覆盖回归 | MySQL 8 |
| 接口验证 | 启动后端（Flyway 自动迁移 + 自动灌种子）→ 跑搜索、错题、背诵、试卷、知识点关联回归 | MySQL 8 |

本地可执行的回归脚本清单见 `scripts/verify-*.mjs`，均支持环境变量配置：

```bash
DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME   # 默认 127.0.0.1:3306 root/123456 kaoyan_db
API_BASE                                             # 默认 http://127.0.0.1:8080/api/v1
TEST_USERNAME / TEST_PASSWORD                        # 默认 tester01 / test123456
```

---

---

## 目录结构

```
408kaoyan/
├── client/                     # 前端
│   ├── public/exams/           # 18 卷 paper.json + 题目配图（静态资源）
│   └── src/
│       ├── api/                # 统一响应体封装与类型
│       ├── content/            # 知识树 knowledge-tree.ts（四科大纲）
│       │   └── knowledge-articles/   # 知识正文（按教材/章节分文件）
│       ├── animations/         # manim-web 动画模块（客户端渲染）
│       ├── store/ views/ router/ styles/
├── server/                     # 后端
│   └── src/main/
│       ├── java/com/kaoyan408/ # core / config / entity / mapper / service / controller
│       └── resources/
│           ├── application.yml
│           └── db/migration/   # V1 表结构，V2 内容种子（脚本生成）
├── scripts/
│   ├── content-bundle-entry.ts # 内容打包入口（TS → ESM）
│   └── import-content.mjs      # 校验 + 生成种子 SQL +（可选）入库
├── web/nginx.conf              # 静态资源 + /api 反向代理
└── docker-compose.yml
```

## 本地开发（原生 MySQL）

前置：JDK 17、Maven 3.9+、Node 18+、本地 MySQL 8（默认 3306）。

```bash
# 1) 建库（只需一次）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS kaoyan_db CHARACTER SET utf8mb4;"

# 2) 安装依赖
npm install                 # 根：esbuild + mysql2（内容导入用）
npm --prefix client install # 前端

# 3) 生成内容种子并写入数据库（V2__seed_content.sql 已随仓库提供，可跳过生成）
npm run import:content      # 仅生成 SQL 并打印校验报告
npm run import:apply        # 生成并直接灌入本地库（默认 127.0.0.1:3306）

# 4) 启动后端（Flyway 会自动建表并灌入种子）
cd server
DB_PASSWORD=你的密码 mvn spring-boot:run
# Windows PowerShell: $env:DB_PASSWORD="你的密码"; mvn spring-boot:run

# 5) 启动前端（默认 5173，/api 已代理到 8080）
npm run dev
```

浏览器打开 <http://127.0.0.1:5173>。

> IDEA 用户：直接以 Maven 项目打开 `server/`，运行 `KaoyanApplication`；
> 在 Database 工具里连 `127.0.0.1:3306/kaoyan_db` 即可查看表数据。

### 端口约定

| 用途 | 端口 |
| --- | --- |
| 前端 dev server | 5173 |
| 后端 API（本地） | 8080 |
| nginx（容器部署） | 18080（`WEB_PORT` 可覆盖） |
| MySQL（容器，映射到宿主） | 33061（`DB_PORT` 可覆盖） |

## Docker 部署

```bash
cp .env.example .env            # 修改 DB_PASSWORD 与 JWT_SECRET
npm --prefix client install
npm --prefix client run build   # 产出 client/dist，由 nginx 挂载
docker compose up -d --build
```

访问 <http://服务器地址:18080>（默认端口 18080，避开本机开发常用的 8080）。

首次启动 api 容器时，Flyway 应用表结构迁移（V1 表结构、V2 错题重做字段），
随后 `ContentSeedConfig` 在题库为空时灌入内容种子（约 3MB，实测耗时约 23 秒）。

### 部署实测结论（本机验证过一次完整流程）

以下三点是实际部署时踩到或需要预先知道的，不是推测：

1. **nginx 配置必须覆盖 `default.conf`，不能并排新增。**
   官方 nginx 镜像自带 `/etc/nginx/conf.d/default.conf`，同样声明
   `listen 80; server_name _;`。两个 server 块条件完全相同时 nginx 只采用
   **先加载**的那个（按文件名排序，`default.conf` 在前），
   结果是 `location /api/` 反代规则被整体绕过，`/api/v1/health` 被当作静态文件
   返回 404。compose 中因此挂载到 `default.conf` 而非 `kaoyan.conf`。

2. **内容种子不在 Flyway 版本链中。**
   种子由 `scripts/import-content.mjs` 生成到 `server/src/main/resources/seed/content.sql`，
   运行时由 `ContentSeedConfig` 加载（题库非空则跳过；
   `kaoyan.seed.force=true` 可强制重灌）。
   内容属于数据而非表结构，若作为迁移会因频繁重生成导致 checksum 漂移、应用无法启动。

3. **无外网时需自备基础镜像。**
   若环境无法访问 `registry-1.docker.io`（DNS 解析失败），可用镜像站拉取后重新打标签：

   ```bash
   docker pull docker.m.daocloud.io/library/eclipse-temurin:17-jre
   docker tag  docker.m.daocloud.io/library/eclipse-temurin:17-jre eclipse-temurin:17-jre
   docker pull docker.m.daocloud.io/library/nginx:1.27-alpine
   docker tag  docker.m.daocloud.io/library/nginx:1.27-alpine nginx:1.27-alpine
   ```

   构建阶段与数据库使用的 `maven:3.9.10-eclipse-temurin-17`、`mysql:8.0`
   通常已在本机镜像缓存中。

### 常用运维命令

```bash
docker compose -p kaoyan408 ps                    # 查看状态
docker compose -p kaoyan408 logs -f api           # 跟踪后端日志
docker compose -p kaoyan408 up -d --build api     # 只重建后端
docker compose -p kaoyan408 down                  # 停止（不加 -v，保留数据卷）
```

改过 `web/nginx.conf` 后必须重建 web 容器（挂载为只读文件，不会自动重载），
并确认经 nginx 的 `/api` 链路仍然可用：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/api/v1/health
```

### 修改已应用的迁移会导致服务无法启动

Flyway 对每个已应用的迁移记录 checksum，**文件内容任何改动都会使其失配**，
包括只改注释、改空行。此时启动直接失败：

```
Migration checksum mismatch for migration version N
-> Applied to database : ...
-> Resolved locally    : ...
```

本项目在 V1、V4、V5 上各踩到一次（都只是改了注释）。规则：

- **已应用（已入库）的迁移一律视为不可变**，需要调整就追加新迁移。
  例如删除某次迁移里多加的索引，正确做法是新增一个 `DROP INDEX` 迁移，
  而不是回头删掉那一行。
- 若确认**只改了注释或空白、DDL 完全没变**，可用 repair 对齐 checksum：

  ```bash
  cd server
  FLYWAY_URL='jdbc:mysql://127.0.0.1:3306/kaoyan_db?characterEncoding=UTF-8&useUnicode=true&allowPublicKeyRetrieval=true' \
  FLYWAY_USER=root FLYWAY_PASSWORD='<密码>' \
  mvn -B flyway:repair
  ```

  容器环境同理，把 URL 换成容器内可达的地址即可。
- **DDL 有实质变化时禁止用 repair 蒙混**：repair 只改记录、不动表结构，
  会让数据库与迁移文件长期不一致。这种情况必须新增迁移来纠正。
- 迁移失败会在 `flyway_schema_history` 留下 `success=0` 的记录，需先删除该行
  （确认表结构未被部分改动）后再重试。

## 运维与升级要点（2026-11 加固）

这一节记录本轮加固后**会改变运维动作**的地方。改动本身在 git 历史里，这里只写「你需要知道什么」。

### JWT 密钥校验与应急开关

密钥等于仓库公开默认值、或短于 32 字节时，服务**默认拒绝启动**：公开密钥意味着任何人都能
签出任意账号的 token，这时候站点「能用」比「停掉」更危险。

但配置错误导致的停站也是事故，所以留了一个显式应急通道：

```bash
KAOYAN_AUTH_ALLOW_INSECURE_JWT_SECRET=true docker compose -p kaoyan408 up -d api
```

打开后改为告警并照常启动（过短密钥会做 SHA-256 派生以满足 HS256 的长度要求，熵不变、仍是弱密钥）。
它是**临时恢复手段**：起来后请立刻换掉密钥并关掉开关。

### 升级后所有用户需要重新登录一次

JWT 现在带 `ver` claim，与 `app_user.token_version` 比对。升级前签发的 token 没有该字段，
会被判为失效 —— 这是一次性代价，换来的是「禁用账号 / 改密码 / 踢下线」能立即生效
（把 `token_version` 加一即可，60 秒内生效）。

### 背题模式需要登录

`GET /api/v1/questions/{ref}?memorize=true` 现在未登录返回 401。之前它对匿名开放，
等于把 846 道真题的答案做成公开接口，模拟考「考试中不下发答案」的约束被一个参数绕过。
普通做题路径不变：游客照样能作答、判分、看解析，只是不落库。

`reveal` 参数已移除（原条件 `answered && (reveal == 0 || reveal == 1)` 恒为真，从未生效）。

### 内容更新的正确流程

```bash
npm run import:content                          # 改完 client 侧 TS 内容后重新生成 seed
docker compose -p kaoyan408 up -d --build api   # 重启后按种子指纹自动判断是否重灌
```

种子加载现在有四个保护：事务、`GET_LOCK` 互斥、SHA-256 指纹、用户数据守卫。

- **指纹一致** → 跳过（不再每次启动写 3MB）。
- **指纹变化且无作答数据** → 自动重灌（实测 7 秒左右）。
- **指纹变化但已有作答数据** → **拒绝重灌**并打印指引。因为重灌会 DELETE 题库、
  `question.id` 重新自增，历史作答会变成孤儿记录。确认要丢弃历史时才设置：
  `KAOYAN_SEED_FORCE=true`（强制重灌）+ `KAOYAN_SEED_FORCE_WITH_USER_DATA=true`（确认丢弃历史）。
- 首次部署（题库为空）不需要任何开关。

### 后端取值前提：api 端口不要对外映射

限流用的客户端 IP 来自 nginx **无条件覆盖**写的 `X-Real-IP`（`X-Forwarded-For` 可被请求头伪造，
已不再采信）。这个前提成立的条件是 api 容器只有 `expose`、所有流量经 nginx。
若为了本地开发把 `ports: - "8080:8080"` 加到 api 上，直连流量可以伪造 `X-Real-IP`，
届时必须改成「按受信代理网段取 XFF 最后一跳」。

### 传输与安全头

- nginx 已开 gzip：`/practice/queue` 从 197KB 降到 8.8KB（4.4%），知识树 13KB→3.3KB。
- 下发 CSP、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`。
  这些头在 `web/snippets/security-headers.conf`，**每个带 `add_header` 的 location 都要 include**：
  nginx 的 `add_header` 是就近覆盖语义，只写在 server 级会被那些 location 静默丢掉。
- 首帧主题预设在 `client/public/theme-init.js`，在 `<head>` 里同步加载（否则暗色用户先闪白）。
  它是外链而不是内联脚本，就是为了让 CSP 保持 `script-src 'self'`。
- HSTS 没有在源站下发：线上入口是网关终止 TLS，写死在源站会在用 `IP:端口` 直连时把用户锁死。

### 测试与门禁

```bash
npm --prefix client run typecheck   # vue-tsc；build 已内置这一步，类型错不再能进构建
npm --prefix client test            # 渲染安全不变式（12 个用例，vitest + jsdom）
cd server && mvn -B test            # 接口契约测试（10 个用例）+ jacoco 行覆盖门限
```

- 后端测试需要可用的 MySQL：`DB_URL / DB_USER / DB_PASSWORD` 环境变量；
  Flyway 自动迁移，题库已存在时不重复灌种子。
- jacoco 门限是 0.10（实测约 21%），它是护栏不是目标：防止覆盖率继续往下掉。
- CI 已接入这两步（build job 跑前端单测，api job 在 MySQL 就绪后跑后端契约测试）。

### 生产机构建前端的前提（重要）

生产机（10.0.219.241）当前是 **Node v20.11.1**，而本项目依赖 **vite 8.3.5**，
它要求 Node `^20.19.0 || >=22.12.0`。实测在该机上直接 `npx vite build` 会报：

```
SyntaxError: The requested module 'node:util' does not provide an export named 'styleText'
```

所以**不能在服务器上构建前端**。当前流程是：本地（Node 22/24）`npm run build` →
把 `client/dist` 打包上传 → 解包到 `dist.new` → `mv dist dist.old.<ts> && mv dist.new dist`
→ `docker compose -p kaoyan408 up -d --force-recreate web`。

要把构建能力还给服务器，需要把 Node 升到 22 LTS（或 20.19+），否则每次发版都得在本地构建。

### 让回访更快：Cloudflare 缓存规则（面板操作，不在仓库里）

现状：nginx 已经对入口 HTML 下发 `public, max-age=0, s-maxage=60, stale-while-revalidate=300`，
但 **Cloudflare 默认不缓存 HTML**——实测两次请求都是 `cf-cache-status: DYNAMIC`，
意味着每次访问都要跨境回源一次（实测 TTFB 中位 1.5s）。

在 Cloudflare 面板里加一条 Cache Rule 即可（一次性操作）：

| 项 | 值 |
| --- | --- |
| 规则名 | `cache-html-60s` |
| 匹配条件 | `URI Path equals /` **或** `URI Path ends with index.html` |
| Cache eligibility | Eligible for cache |
| Edge TTL | Override origin → **60 seconds** |
| Browser TTL | Respect origin（浏览器端仍 `max-age=0`，不会拿到旧入口） |

验证：`curl -sI https://<域名>/ | grep -i cf-cache-status`，期望 `HIT`。

注意：发版后最多 60 秒全量生效（资源都是带哈希的 chunk，不会出现「入口旧、chunk 新」的白屏）；
需要立刻生效就在 Cloudflare 后台 Purge 一次。

顺带确认 Brotli 已开（Speed → Optimization → Brotli：On）：实测队列接口 `br` 后 991 字节。

### 前端性能结构（改动后长这样）

- **首屏不再加载 marked / KaTeX**：`utils/` 拆成 `htmlEscape`（无依赖）、`htmlSanitize`（DOMPurify）、
  `htmlMath`（KaTeX）、`html`（marked 编排）与 `stem`（题干轻量渲染）。
  刷题页只依赖 `stem` + `htmlEscape`；题干里真的出现 `$...$` 时才动态 `import('@/utils/htmlMath')`。
  改前刷题页会拉一个 328KB 的 chunk（marked + DOMPurify + KaTeX），现在首屏只有 30KB 的视图代码。
- **Service Worker**（`public/sw.js`）：`/assets/**` cache-first（文件名带哈希，不可变）、
  导航 network-first（3 秒超时回退缓存）、其它静态资源 SWR；`/api/**` 完全不缓存。
  只在生产构建注册。改名 `CACHE` 常量即可整体失效。
- **队列内联首题**：`/practice/queue` 在首页且非错题范围时会带 `firstQuestion`，
  刷题首屏因此只有 2 个请求（`stats/dash` + `queue`），不再单独打 `/questions/{ref}`。
- **知识点图片外置**：正文里内联的图片（含内联 SVG）改由
  `/api/v1/knowledge/asset/{blockId}?v=<指纹>` 提供，长缓存 + CSP `sandbox`。
  最重的一篇正文从 357KB 降到 23KB（gzip 6KB）。

### 已知取舍

- **刷题队列没有改成翻页**：接口已支持 `page/size` 并返回真实 `total` 与年份分组（`years`），
  但前端仍一次取全量。理由是 gzip 已把该响应压到 8.8KB，而翻页会破坏
  「搜索直达单题后还能在同年份继续上一题/下一题」的交互，收益递减。
- **CSP 放行 `style-src 'unsafe-inline'`**：KaTeX 与 Tailwind 任意值类会产生内联 style 属性，
  收紧会导致公式错版。脚本侧仍是严格 `script-src 'self'`。

---
## 接口约定

所有接口返回 **HTTP 200** + 统一响应体，业务语义由 `code` 表达：

```json
{ "code": 0, "msg": null, "data": {} }
```

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录 / 登录失效（`认证失败，无法访问系统资源。`） |
| 404 | 端点或资源不存在（`No endpoint GET /xxx.` 与 408os 一致） |
| 429 | 触发限流（登录失败锁定 / 验证码发送过频 / 游客试用次数用完） |
| 500 | 服务端异常 |
| 503 | 依赖服务未配置（如 AI 未配置 API Key） |

鉴权：`Authorization: Bearer <token>`（HS256 JWT）。

绝大部分接口返回上述统一 JSON；**唯一例外是 AI 对话** `POST /api/v1/ai/chat`，
它以 `text/event-stream` 流式返回，事件类型为 `thinking` / `delta` / `done` / `error`。

主要端点：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/health` | 健康检查 |
| POST | `/api/v1/auth/login` · `/auth/register` | 登录 / 注册 |
| GET | `/api/v1/user/current` | 当前用户 |
| GET | `/api/v1/exams` · `/exams/{year}` | 试卷列表 / 整卷 |
| GET | `/api/v1/questions/{examRef}` | 单题（未作答不下发答案与解析） |
| POST | `/api/v1/questions/{examRef}/answer` | 提交答案 |
| GET | `/api/v1/knowledge` · `/knowledge/point/{pointId}` · `/knowledge/block/{blockId}` | 知识树 / 知识点正文 / 知识块与关联真题 |
| GET | `/api/v1/search` · `/search/suggest` | 全文搜索（知识点 / 题目 / 正文片段）与输入联想 |
| GET | `/api/v1/practice/queue` | 刷题队列（scope：all / wrong / untouched / wrong-pending / wrong-mastered） |
| GET | `/api/v1/practice/wrong-book` · `/practice/entrances` | 错题本（待订正 / 已订正）、刷题入口汇总 |
| GET | `/api/v1/stats/dash` · `/stats/points` | 学习看板、知识点级掌握与薄弱点 |
| GET · POST | `/api/v1/memory/due` · `/summary` · `/review` · `/add` · `/remove` | 背诵卡片与间隔重复（需登录） |

## 内容数据来源与许可

- 真题与知识正文来源于 [simple408](https://github.com/liangbohan/postgraduate-exam-website)（**CC BY 4.0**）。
- 真题题面、选项、答案与解析以 **408os** 为官方参照进行校准。
- 本仓库对这些内容进行结构化二次组织（拆分为题库与知识块并建立关联），供复习用途使用。
- 详见 [LICENSE](./LICENSE)。

## 里程碑

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| M0 | 仓库骨架、表结构、内容入库（846 题 / 1634 知识块 / 1030 条关联）、认证与看板接口 | ✅ 完成 |
| M1 | 知识阅读页（KaTeX 公式 + 42 个 manim 动画）、全文搜索、刷题队列、错题重做闭环、真题卷整卷阅读 | ✅ 完成 |
| M2 | 学习看板深化（知识点级薄弱点）、间隔重复背诵卡片 | ✅ 完成 |
| — | Docker 部署实测跑通（含 nginx 反代与迁移流程） | ✅ 完成 |
| M3 | 整卷限时模拟考试（答题卡 / 自动评分 / 成绩单 / 综合题自评） | ✅ 完成 |
| M4 | 账号体系（邮箱验证码注册 / 登录锁定 / 禁用账号）、游客模式 | ✅ 完成 |
| M5 | AI 学习助手（流式对话 / 注入学习数据 / 知识点跳转）、营销落地页 | ✅ 完成 |
| 待定 | 自定义域名与 HTTPS、CC BY 署名策略复核 | 未开始 |

规划全文见 [`docs/考研408工作台规划书.md`](./docs/考研408工作台规划书.md)。