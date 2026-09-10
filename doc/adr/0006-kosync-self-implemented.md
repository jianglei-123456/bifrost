# 阅读进度同步（KOSync）自研实现，不集成官方 sync-server

M3-sync 设计决定：**Bifrost 自己实现 KOSync 兼容服务端**（新模块 `bifrost-adapter/kosync-server`，5 个端点挂根路径），**不部署官方 `koreader/koreader-sync-server` 容器**、不做"官方容器 + 同步桥"、不换第三方实现（kosync-dotnet 等）。这是对 `doc/m2-book/task/05-延后项.md` §5.1 中"第三方参考实现成熟，自建价值低"这条延后理由的**正面推翻**（Status: accepted）。

取证依据：`E:\Dev\jianglei\koreader-sync-server` @ `597e064`（AGPL-3.0，Lua/OpenResty+Redis）、`E:\Dev\jianglei\koreader` @ `062a844e`（客户端 `plugins/kosync.koplugin/`）。协议事实见 `doc/m3-sync/调研/01-KOSync协议实证.md`。

## 理由

1. **官方实现给不了"图书同步管理"，只能给"进度能同步"**：它只存不透明 `document` 指纹 + 5 个字段（`app/controllers/1/syncs_controller.lua:160-166`），客户端上报的 `metadata`（filename/title/authors）在服务端**从未被读取**；没有"书"的概念、没有按书查询、没有列表接口——`config/routes.lua:9-16` 就是全部路由。书级视图、孤儿进度、重置某本书进度，它一条都提供不了。
2. **让它提供就得改它的 Lua，而它是 AGPL-3.0**（`COPYING:1-2`、`README.md:13`；网络交互条款 §13 见 `COPYING:540-551`）。改 `authorize`/`create_user` 接我们的账号体系、加书维度，都会把"修改版"的对应源码提供义务带进来；而 Lua 也无法直接搬进 Java 模块。
3. **它的运维面是净新增负担**：gin 框架构建时从 GitHub master **无 pin** 克隆（`Dockerfile:34`，镜像层不可复现）；自签证书 **365 天后过期**（`Dockerfile:26-28`）；Redis 硬编码 `127.0.0.1` 且无 AUTH（`db/redis.lua:4-25`、`config/redis.conf:61,480`），要外接 Redis 得改代码；应用层**零日志**（无 `ngx.log`/`print`）；无 keepalive（每次 GET 两条新连接）、无 metrics、无 TTL、无限流。
4. **需要复刻的服务端逻辑极小**：5 条路由、2 类 Redis key、一次字符串相等比较，且**没有任何服务端冲突/新旧判断逻辑**（官方自 2016 年 `73b9d53` 起即为无条件后写覆盖）。工作量与 M2-book 的「02 OPDS 发布」同级，不是新里程碑的量级。

## 取舍

- **代价**：协议变更需自己跟（该协议十年未变，风险低）；端点契约与指纹算法的兼容性由我们负责——客户端硬约束已逐条取证（见调研档 §3），转换为模块级硬性要求。
- **收益**：阅读进度与图书处在**同一个 SQLite、同一个备份域、同一个管理面**，并且能实现"书级"语义（指纹→书映射、孤儿进度、按书重置）。

## 被否决的方案

| 方案 | 否决理由 |
|---|---|
| 部署官方容器 | 理由 1、3：拿不到书级语义与管理面，且新增 OpenResty + Redis 运维域与第二份数据 |
| 官方容器 + 自研同步桥 | 它没有"列出某用户进度"的接口，桥只能直连其容器内 Redis 的私有 key 结构（`db/redis.lua:4-25` 硬编码 loopback）——跨容器耦合内部实现，最脆弱；且接自家账号仍需改它的 Lua |
| 换第三方（kosync-dotnet 等） | 管理的仍是"用户/文档"，**依然不认识书**；再引入一套 .NET + LiteDB 运行时与第二个数据域 |

## AGPL 边界（实现纪律）

自研走**按可观测协议行为独立实现**：依据 = KOReader 客户端侧 `api.json` + 客户端源码（客户端行为事实）+ 服务端**黑盒可观测行为**（状态码 / 错误码 / 响应体形态）。**不转写**官方 `syncs_controller.lua` 的代码，也不照抄其固定文案（`message` 用语义等价的自家文案——客户端只按状态码判定，`message` 仅原样展示给用户）。

## 影响

- `CONTEXT.md`：KOSync 条目由"v2 已知未实现"改写为"M3-sync 实装"，并新增「阅读进度 / 文档指纹 / 同步账号 / 孤儿进度 / 设备」词条。
- `doc/m2-book/task/05-延后项.md` §5.1 标记为**已由 M3-sync 承接**，避免两处说法打架。
- 新模块 `bifrost-adapter/kosync-server`（仿 `opds-publisher` 接线）；`bifrost-bootstrap` 必须新增一条 `SecurityFilterChain`——当前只有 `/api/**`、`/rest/**`、`/opds/**` 三条链（`SecurityConfig.java:38-59`、`OpdsSecurityConfig.java:32-43`），**未被任何 `securityMatcher` 命中的新前缀是完全不设防的**。
- 新增 `bifrost.kosync.*` 配置段（并入 `BifrostProperties`，与 `opds` 同模式）。
