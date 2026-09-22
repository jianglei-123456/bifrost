# 认证：登录页 + Basic 凭据（登录态本地持久化 1 天），无服务端会话

后端无登录端点：`/api/**` 仅接受 `Authorization: Basic` 头或 `?u=&t=&s=` 令牌，且未配置 CORS、不托管静态资源。管理端决定：提供登录页（输入 admin 口令 → 调 `GET /api/user` 验证，200 通过 / 401 拒绝）；登录成功后凭据（用户名/口令/Subsonic 令牌盐）连同过期时间戳写入 localStorage，**有效期 1 天（24h）**——刷新页面保持登录，过期自动清除并要求重新登录，退出登录或收到 401 立即清除；每次请求由 axios 拦截器注入 Basic 头；封面 URL 由同一口令派生 Subsonic 令牌。备选方案：凭据放 `.env`（口令明文落盘，体验差一截）。后果：口令明文存活于浏览器 localStorage（单管理员语义可接受，后端 AES-GCM 可逆存储本就是为此设计）；持久化带来 XSS 窃取风险，故有效期限定 1 天、仅存储于同源 localStorage、退出/401 即清除；生产部署走同源（前端产物由 nginx 或后端同域托管），后端保持零 CORS 配置。

> 原为 `bifrost-dashboard` 仓库的 ADR-0002，2026 年该工程并入本仓库时按根序列续号为 ADR-0009（见 [ADR-0010](0010-frontend-merged-into-core-repo.md)）。
> 本 ADR 是 [ADR-0007](0007-single-image-admin-under-admin.md) 理由 1 的依据：这里定死的「生产部署走同源、后端保持零 CORS 配置」，正是后端单进程托管管理端（挂 `/admin/`）的正当性来源。注意原文写的是「由 nginx 或后端同域托管」，把同源的两条实现路径都留作可选；ADR-0007 选择了后者。
