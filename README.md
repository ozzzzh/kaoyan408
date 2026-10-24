# 考研 408 工作台

> 计算机统考 408 复习平台：把 **真题**、**知识体系** 与 **掌握度看板** 连成一条复习主线。

[![CI](https://github.com/ozzzzh/kaoyan408/actions/workflows/ci.yml/badge.svg)](https://github.com/ozzzzh/kaoyan408/actions/workflows/ci.yml)
[![真题](https://img.shields.io/badge/真题-2009–2026%20·%2018%20卷-2f6fe4)]()
[![题量](https://img.shields.io/badge/题库-846%20题-2f6fe4)]()
[![后端](https://img.shields.io/badge/后端-Spring%20Boot%203%20·%20Java%2017-green)]()
[![前端](https://img.shields.io/badge/前端-Vue%203%20·%20Vite%20·%20Tailwind%204-42b883)]()

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

## 这是什么

考研 408 工作台面向计算机统考（408）考生，把三件事串成闭环：

1. **刷题** → 题目挂到知识点上（`question_knowledge` 关联表）；
2. **看正文** → 从错题直接跳到对应知识块，公式与动画就地展开；
3. **看掌握度** → 按知识块聚合正确率，薄弱点回流到复习计划。

真题范围为 2009–2026 共 18 卷 **846 题**，题面/选项/答案/解析以 **408os 为官方参照** 校准。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 8 + TypeScript + Tailwind CSS 4 + Pinia + vue-router |
| 渲染 | KaTeX（公式）、marked + DOMPurify（正文）、manim-web（动画） |
| 后端 | Spring Boot 3.4 + Spring Security + MyBatis-Plus + Flyway |
| 存储 | MySQL 8（内容与做题数据） |
| 部署 | Docker Compose（nginx + api + mysql） |

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
| 500 | 服务端异常 |

鉴权：`Authorization: Bearer <token>`（HS256 JWT）。

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
| 待定 | 计时模拟考试（答题卡 / 自动评分 / 成绩单） | 暂缓，按需再评估 |
| 待定 | 上线（域名 / HTTPS / 备案检查）、CC BY 署名策略复核 | 未开始 |

规划全文见 [`docs/考研408工作台规划书.md`](./docs/考研408工作台规划书.md)。