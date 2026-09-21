# 单镜像：管理端 SPA 挂 `/admin/`，由后端托管；镜像只打包宿主构建好的 fat jar

1.0.0 起，**Bifrost 的交付形态是一个镜像**：一个 JVM 进程、一个端口 `18080`，同时提供管理端页面与 Subsonic / OPDS / KOSync 协议服务。具体做法：管理端（Vue Dashboard，独立仓库 `../bifrost-dashboard`）以 `vite build --base=/admin/` 构建成静态产物，构建期拷进 `bifrost-bootstrap/src/main/resources/static/admin/`（gitignore），**打进 fat jar**；后端用一条资源处理器把它挂在 **`/admin/` 子路径**（未知的无扩展名路径回 `index.html`）；`/` 与 `/admin` 302 到 `/admin/`；其它未匹配的根路径**保持 404**。镜像本身**不做源码构建**（Status: accepted）。

## 理由

1. **同源是管理端已经定死的约束**：dashboard 的 axios `baseURL: '/'`、Basic 凭据存 localStorage、401 走 `router.push`，其 ADR-0002 明确"生产部署走同源，后端保持零 CORS 配置"。单进程托管是这条约束最短的实现路径——不需要反代、不需要 CORS、不需要 cookie 策略。
2. **协议端点占着根命名空间，SPA 不能铺在根上**：`/rest/**`（Subsonic）、`/opds/**`（OPDS 1.2）、`/users`、`/syncs`、`/healthcheck`（KOSync）都在根路径。若 SPA 铺在根路径并把未知路径兜底成 `index.html`，客户端把 `/users/creat` 拼错就会拿到 **200 + 一页 HTML**——一个"看起来成功"的垃圾响应，排查成本极高。挂 `/admin/` 后，兜底的作用域被限制在 `/admin/**` 内，根命名空间的 404 语义原样保留（已有集成测试锁住这条：`AdminSpaIntegrationTest`）。
3. **一个进程 = 一份日志、一份生命周期**：不需要 supervisor/s6 管两个进程，`docker stop` 的语义、日志落盘位置、健康检查都只有一个对象。双进程同容器还会把后端路由表在 nginx 里抄第二份。
4. **镜像只打包、不构建**：Dockerfile 只做三件事——装 curl/tzdata、建非 root 用户、`COPY` 那个 fat jar。好处是镜像里不带 Maven/Node 工具链、不带依赖镜像源配置、不带任何凭据；构建失败的定位点也从"镜像内某一步"退回宿主上肉眼可见的两条命令（`vite build` / `mvnw verify`）。

## 取舍

- **代价 1**：`.gitignore` 掉的那个产物目录让"发布 jar"与"随手 `mvnw package` 的 jar"行为不同——后者访问 `/` 会 302 到 `/admin/` 然后 404。这是确定性行为（不做"资源存在才跳转"的条件分支），已写进操作手册 05 与 FAQ。
- **代价 2**：换 UI 必须重建镜像（没做"挂载覆盖静态目录"的后门）。家用单机场景下，重建镜像比多一个可漂移的挂载点更省心。
- **代价 3**：`/admin/` 是 URL 契约的一部分，将来要挪路径得两处一起改（vite `base` + 后端资源模式）并同步文档。
- **收益**：协议客户端的连接地址、端口、命名空间不受任何影响；管理端与后端版本天然一致（同一个 jar、同一个镜像 tag）。

## 被否决的方案

| 方案 | 否决理由 |
|---|---|
| nginx + JVM 双进程同容器 | 两个进程要 supervisor/s6 管；后端路由表（含 `/users`、`/syncs` 这类根路径）要在 nginx 里抄一份，抄错就是协议级故障；日志与生命周期分裂（理由 1、3） |
| 前端单独一个镜像 + compose 两服务 | 用户目标是"一个镜像"；且为满足同源约束，反代仍要复制一份路由表，等于把上一条的缺点搬到一个更显眼的位置 |
| SPA 铺根路径 + 排除清单兜底 | 排除清单是"默认放行"的写法，新增协议端点时容易漏排除；一旦漏了，拼错路径会被吃成 200 + HTML（理由 2） |
| 镜像内多阶段构建（node → maven → jre） | 构建上下文要同时包含两个仓库（BuildKit 命名上下文/父目录上下文），镜像内还要配 Maven/npm 镜像源；换来的是"一条命令出镜像"，但构建变慢、失败面变大、凭据有进镜像层的风险 |
| SPA 放镜像文件系统 `/app/admin/` 而非 jar 内 | 镜像里要维护两份产物；jar 单独 `java -jar` 时完全没有管理端。选"全部进 jar"换来"发布物是一个自洽文件" |
| 引入 `/api/version` 之外再做前端版本漂移检测 | 1.0.0 起 master 常驻发布号，本地构建与发布 jar 同名——靠 OCI label 的 `revision`（git sha）区分即可，不必新增机制 |

## 影响

- 新增：`AdminWebConfig`（`/admin/**` 资源处理器 + SPA 兜底 + `/admin/` → `index.html` 的显式转发）、`AdminEntryController`（`/`、`/admin` → 302）、集成测试 `AdminSpaIntegrationTest` 与测试夹具 `src/test/resources/static/admin/index.html`。
- **一处必须记住的框架行为**：`/admin/`（带尾斜杠）不能交给资源解析器——`ResourceHttpRequestHandler` 对**空资源路径直接 404、根本不调用解析器链**（实测解析器一次都没被调用）。所以 `/admin/` 由 `addViewControllers` 显式 `forward:/admin/index.html` 认领；解析器只负责带内容的路径（深链兜底、静态资源、带扩展名资源 404）。MockMvc 不执行内部转发，测试对 `/admin/` 断言 `forwardedUrl`，内容由 `/admin/index.html` 与深链两条用例覆盖。
- dashboard 仓库：`src/router/index.ts` 改用 `createWebHistory(import.meta.env.BASE_URL)`（vue-router 无参时只认 `<base href>`，不读 `BASE_URL`，否则 `/admin/` 下路由全挂）。
- 端口固定 `18080`：`整体技术架构` §9 里历史的 `8080` 写法一并改正（客户端连接文档、FAQ 一直是 18080）。
- 数据目录收敛为 `bifrost.data.dir`（`db` / 封面 / 日志从它派生，日志不再固定 `${user.dir}`），镜像里 `BIFROST_DATA_DIR=/data` —— 备份 = 备份一个卷。
- 定时扫描接线（`bifrost.scan.cron`，6 段含秒），容器时区由 `TZ` 决定。
- 交付物：`docker/Dockerfile`、`docker/docker-compose.yml`、`.dockerignore`、`.github/workflows/release.yml`、操作手册 05（命令清单）。
