# 管理端前端并入本仓库：`bifrost-dashboard/` 子目录，仓库内一条命令出镜像

1.0.0 交付时管理端是**独立仓库** `../bifrost-dashboard`（ADR-0007 的形态）。本次决定把它**并入本仓库**，作为根目录下的 `bifrost-dashboard/` 子目录：源码全量导入（含其两个从未提交的 agent 文档，**不保留其 git 历史**）；重复的规则与文档按"一个仓库一份"合并——前端 ADR 迁入根 `docs/adr/` 续号为 [0008](0008-cover-art-via-subsonic.md) / [0009](0009-auth-model.md)，前端 `CONTEXT.md` 的管理端专有词并入根 `CONTEXT.md`，前端 `AGENTS.md` / `docs/agents/` / `.gitignore` / `.codegraph/` 删除；构建侧新增根 `build-image.ps1`（一条命令从 `pnpm build` 到镜像）并重写 `.github/workflows/release.yml` 为单 checkout，两者共用同一套 `/admin/` 基址守卫。**本 ADR 取代 ADR-0007 关于仓库归属的那一句（"独立仓库、独立构建"）；ADR-0007 的其余内容继续有效**（Status: accepted）。

## 理由

1. **双仓库的真实代价落在"跨仓库对齐"上，而不是构建上**：管理端消费本仓库的 `/api/**` 契约，两边的改动天然成对（`doc/m3-sync/task/06-前端对接.md` 与 dashboard 侧设计文档互为引用）。双仓库下每次对齐都要跨仓库读代码、跨仓库改文档，且发布要往两个仓库打同名 tag——`release.yml` 里那个 `ref: push ? tag : master` 的三目表达式就是这个成本的化石。
2. **合并后"前端"不再是一个需要靠文档指路的外部对象**：`CONTEXT.md` 里"兄弟目录 `../bifrost-dashboard`（勿记绝对路径）"、`doc/m3-sync/task/00-总览.md` 里"Dashboard 侧设计（另一仓库）"这类写法，都是双仓库逼出来的。一个仓库内所有路径都是相对的、可点击的、可被工具索引的。
3. **`/admin/` 基址与"前后端同镜像"的约束一字不改**：本次只改**源码放哪**，不改**产物形态**。ADR-0007 的三条理由（同源免 CORS、协议端点占根命名空间所以 SPA 不能铺根、一个进程一份日志）与三条代价（jar 行为差异、换 UI 要重建镜像、`/admin/` 进 URL 契约）**全部原样成立**。
4. **镜像仍然只打包、不构建**（ADR-0007 理由 4 不变）：`docker build` 依旧只 `COPY` 一个 fat jar，镜像里没有 Node/pnpm。合并带来的新能力是**构建脚本可以放在仓库内**——不再需要"先 cd 到兄弟目录、再拷回本仓库的 gitignore 目录"这种手工三段式。

## 被否决的方案

| 方案 | 否决理由 |
|---|---|
| `git submodule add bifrost-dashboard` | 仍是两个仓库（clone 要 `--recursive`、发布仍要两次 tag），只是把"兄弟目录"换成"子模块"，没解决理由 1 的成本 |
| 保留前端 git 历史（subtree / filter-repo 后 merge） | 前端只有 9 个提交，且其历史脉络（独立仓库、被迫同源、当时无 CORS）在 ADR-0008/0009 的注记里已经保存；换来的是本仓库历史里混入一批 `bifrost-dashboard/` 前缀的提交。本次判定不值得 |
| 前端并入根 `CONTEXT.md` 之外再建 `CONTEXT-MAP.md` 声明双上下文 | 前端 `CONTEXT.md` 的定义本就是"管理端专有词 + 把共享词让渡给根文件"，不是第二套领域语言；再声明一个上下文等于人为制造第二个词汇入口，与理由 1 的动机相反 |
| 前端构建接入 Maven 生命周期（`frontend-maven-plugin`） | 会让 `mvnw verify` 在没有 Node 的机器上失败、扩大失败面、把 Node 工具链拉进构建链；而"一条命令"用仓库内脚本就能达到 |
| 提交 `dist/` 进仓库（克隆后免装 Node 即可出 jar） | 同一份产物会在仓库里存两遍（`bifrost-dashboard/dist/` 与 `bifrost-bootstrap/.../static/admin/`），且 125 个带哈希文件每次构建全量 churn |
| 前端文档整体搬到根 `docs/` | 前端各文档之间的相对链接（`design.md` ↔ `book-sync-design.md` ↔ 源码）会成片失效；根 `docs/` 也不是"后端文档"而是"产品与技术文档"，管理端设计放子目录更贴近它的读者 |

## 代价与影响

- **代价 1：仓库体积的观感**。`bifrost-dashboard/node_modules` 约 302 MB，占前端目录的 98%。它不进 git、不进 jar、不进 Docker 上下文（`.dockerignore` 与 `.gitignore` 均已排除），但 `docker build` 的上下文与任何 `grep -r` 都必须显式跳过它。
- **代价 2：Node 工具链成为"本仓库的一部分"**。前端在 `bifrost-dashboard/package.json` 里钉 `packageManager: pnpm@11` 与 `engines.node >=24`（此前只在操作手册与 CI YAML 里隐含）。后端开发者不装 Node 仍可 `mvnw verify` 通过，但**出镜像的脚本需要 Node**——这是理由 4 的直接推论。
- **代价 3：ADR 编号出现一次续号断层**。ADRs 0001–0007 全部属于后端；0008/0009 原属 `bifrost-dashboard`；0010 起回到本仓库。根 `docs/adr/` 仍是唯一序列，但读 0008/0009 时会遇到"原为另一仓库"的历史语境——已在两份文件顶部注明。
- **影响面**：管理与契约引用共 37 处（根 `README.md`、`CONTEXT.md`、`AGENTS.md`、`docs/agents/domain.md`、`docker/Dockerfile`、`.dockerignore`、`.gitignore`、`doc/操作手册/05-Docker部署.md`、`doc/技术设计/整体技术架构.md`、`doc/m2-book|m3-sync/task/*`，以及 `bifrost-dashboard/` 内的 README、`docs/book-sync-design.md`、`src/api/*.ts`、`src/router/index.ts` 注释）。
- **安全动作**：源仓库 `github.com/jianglei-123456/bifrost-dashboard` 归档为只读，并在其 README 顶部指向本仓库的 `bifrost-dashboard/`；本地兄弟目录在验证通过后移除。
- **一处顺带修掉的真 bug**：迁移前 `bifrost-bootstrap/src/main/resources/static/admin/`（gitignore 目录）里的产物**早于** `src/router/index.ts` 的 `createWebHistory(import.meta.env.BASE_URL)` 修复，带着"`/admin/` 下刷新子页面匹配不到路由"的缺陷。迁移后按下节「构建」重建产物，该缺陷随之消失；操作手册 05 的命令也一并更新。

## 构建（一条命令）

```powershell
# 仓库根
.\build-image.ps1 -Version 1.0.0
```

脚本依次做：`pnpm install --frozen-lockfile` → `vue-tsc -b` → `vite build --base=/admin/` → **守卫**（`dist/index.html` 必须含 `/admin/assets/`，否则抛错——少了它打进镜像的就是一份根路径 UI，打开即白屏）→ 清空并拷入 `bifrost-bootstrap/src/main/resources/static/admin/` → `mvnw clean verify` → `docker build`。`release.yml` 在 CI 上跑同一套步骤（Linux 版命令），并保留原有的容器冒烟断言。
