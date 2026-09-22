# Bifrost Dashboard（彩虹桥 · 管理端）

Bifrost 家庭媒体库的**管理端 Web 应用**，与后端同属本仓库（位于根目录下的 `bifrost-dashboard/` 子目录），消费后端的管理 REST（`/api/**`）。仓库归属与取舍见 [ADR-0010](../docs/adr/0010-frontend-merged-into-core-repo.md)。

> **彩虹桥**：源于北欧神话联通九界的彩虹桥。这里，它联通你的音乐、影音与图书 —— 1.0.0 交付音乐与图书（含阅读进度同步），影音分区已在导航预留。

## 技术栈

Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus · ECharts（vue-echarts）· Vitest · ESLint + Prettier · pnpm

## 快速开始

```bash
pnpm install          # 安装依赖（npmmirror 源）
pnpm dev              # 开发服务器 http://localhost:5173（/api、/rest 代理到 :18080）
pnpm build            # 类型检查 + 生产构建（vue-tsc -b && vite build）
pnpm test             # Vitest 单测
pnpm lint / format    # ESLint / Prettier
```

**联调前置**：先启动后端（仓库根，`./mvnw -pl bifrost-bootstrap -am spring-boot:run`，端口 18080）。登录使用后端初始管理员口令（env `BIFROST_AUTH_INITIAL_PASSWORD` 或已有库的既有口令）。

## 关键约束：生产基址必须是 `/admin/`

管理端**不单独部署**：产物以 `/admin/` 为基址构建，随 fat jar 打进**单个镜像**，由后端托管在 `/admin/`（`/` 与 `/admin` 302 过去）。所以：

- 生产构建**必须**带 `--base=/admin/`（`pnpm build` 不带，只适合本地查看），否则打进镜像的是一份根路径 UI，**打开即白屏**；
- 路由 base 取 `import.meta.env.BASE_URL`（见 `src/router/index.ts`）。**不能**用无参的 `createWebHistory()`：它只认 `<base href>` 标签，会退化成 `/`，在 `/admin/` 下刷新任何页面都匹配不到路由；
- 开发期完全不受影响：`pnpm dev` 仍是 `http://localhost:5173/`（BASE_URL 为 `/`）；
- axios 的 `baseURL: '/'` 与封面/下载直链（`/rest/...`、`/opds/v1.2/...`）都是根路径绝对地址，不需要跟着 base 改。

出镜像请回到**仓库根**跑 `.\build-image.ps1`（它自带 `/admin/assets/` 基址守卫）；完整命令清单见 [操作手册 05](../doc/操作手册/05-Docker部署.md)，形态与取舍见 [ADR-0007](../docs/adr/0007-single-image-admin-under-admin.md)。

## 目录结构

```
src/
├── api/          # 契约层：axios 封装（信封解包/Basic 注入/401 处理）+ 按域模块 + 类型
├── stores/       # Pinia：auth（登录态，localStorage 持久化 1 天）、musicScan（音乐扫描状态，顶栏轮询）、book（图书 + 图书扫描）
├── utils/        # auth（Basic/盐）、subsonic（令牌/封面 URL）、format（格式化）
├── layout/       # AppLayout + 侧栏（彩虹桥签名/媒体分区）+ 顶栏（扫描呼吸灯）
├── components/   # CoverArt / StarButton / RatingStars / TrackTable / AlbumCard / StatCard
├── views/        # 登录 · 总览 · 音乐库 · 图书库 · 艺术家/专辑/曲目(含详情) · 搜索 · 歌单(含详情) · 图书(含详情) · 账号 · 系统
├── styles/       # tokens.css（设计 token，源自 docs/design.md）+ base.css
└── test/         # Vitest 全局 setup
```

## 关键决策（ADR 在仓库根 `docs/adr/`）

| 决策 | 说明 |
| --- | --- |
| [ADR-0008 封面走 Subsonic](../docs/adr/0008-cover-art-via-subsonic.md) | `/api` 无音乐封面端点；封面 URL 经 `/rest/getCoverArt.view?id=al-<id>`，用登录口令派生 Subsonic 令牌（`t=md5(口令+salt)`，salt 每会话随机） |
| [ADR-0009 认证模型](../docs/adr/0009-auth-model.md) | 无登录端点；登录页验证 `GET /api/user`，凭据持久化到 localStorage、有效期 1 天（刷新保持登录，过期/退出/401 清除），axios 拦截器注入 Basic 头；同源部署绕开后端零 CORS |
| [ADR-0007 单镜像 + `/admin/`](../docs/adr/0007-single-image-admin-under-admin.md) | 产物以 `vite build --base=/admin/` 构建、与后端同镜像，路由 base 走 `import.meta.env.BASE_URL`；理由与被否决方案见该 ADR |
| [ADR-0010 前端并入本仓库](../docs/adr/0010-frontend-merged-into-core-repo.md) | 为什么管理端从独立仓库搬进 `bifrost-dashboard/` 子目录、为什么构建仍独立、为什么 ADR 续号 |

## 文档

- [设计系统 docs/design.md](docs/design.md) —— 深空聆听室 · 彩虹桥（色板/字体/布局/签名元素）
- [管理端契约速查 docs/api-contract-notes.md](docs/api-contract-notes.md) —— **只记读源码不易看出的东西**：信封/错误码、认证行为、若干"代码与旧文档不符"的坑（端点与字段的权威来源是 `bifrost-api` 源码与 `hurl/`）
- [阅读进度同步设计 docs/book-sync-design.md](docs/book-sync-design.md) —— M3-sync 管理端页面设计
- 领域词表（含管理端专有词）：仓库根 [CONTEXT.md](../CONTEXT.md)；后端契约规划：`doc/m1/task/04-管理REST.md`
