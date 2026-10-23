# 考研 408 工作台

> 计算机统考 408 复习平台：把 **真题**、**知识体系** 与 **掌握度看板** 连成一条复习主线。

[![真题](https://img.shields.io/badge/真题-2009–2026%20·%2018%20卷-2f6fe4)]()
[![题量](https://img.shields.io/badge/题库-846%20题-2f6fe4)]()
[![后端](https://img.shields.io/badge/后端-Spring%20Boot%203%20·%20Java%2017-green)]()
[![前端](https://img.shields.io/badge/前端-Vue%203%20·%20Vite%20·%20Tailwind%204-42b883)]()

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
| 存储 | MySQL 8（内容与做题数据）、Redis 7（预留） |
| 部署 | Docker Compose（nginx + api + mysql + redis） |

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
| nginx（容器部署） | 8080 |
| MySQL（容器，映射到宿主） | 33061 |
| Redis（容器，映射到宿主） | 63791 |

## Docker 部署

```bash
cp .env.example .env         # 修改 DB_PASSWORD 与 JWT_SECRET
npm --prefix client install
npm --prefix client run build   # 产出 client/dist，由 nginx 挂载
docker compose up -d --build
```

访问 <http://服务器地址:8080>。数据库表结构与内容种子由 Flyway 在 api 容器启动时自动应用。

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
| GET | `/api/v1/stats/dash` | 学习看板 |

## 内容数据来源与许可

- 真题与知识正文来源于 [simple408](https://github.com/liangbohan/postgraduate-exam-website)（**CC BY 4.0**）。
- 真题题面、选项、答案与解析以 **408os** 为官方参照进行校准。
- 本仓库对这些内容进行结构化二次组织（拆分为题库与知识块并建立关联），供复习用途使用。
- 详见 [LICENSE](./LICENSE)。

## 里程碑

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| M0 | 仓库骨架、表结构、内容入库（846 题 / 知识块 / 关联）、认证与看板接口、可跑通的前后端 | ✅ 本次 |
| M1 | 知识阅读页（公式 + 动画）、搜索、刷题队列与错题本、整卷模拟 | 计划中 |
| M2 | 掌握度聚合与复习计划、间隔重复卡片、综合题评分 | 计划中 |
| M3 | 上线（域名 / HTTPS / 备案检查）、CC BY 署名策略复核 | 计划中 |

规划全文见 [`docs/考研408工作台规划书.md`](./docs/考研408工作台规划书.md)。