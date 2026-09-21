# 统一认证采用 Spring Security

《整体技术架构》§7 允许「Spring Security 或轻量 Filter」两种实现。已决定引入 `spring-boot-starter-security`：/api/** 使用 HTTP Basic 或 t/s 令牌（失败返回 401 + 信封 code=1002）；/rest/** 使用自定义过滤器解析 u/t/s/p（认证失败按 Subsonic 协议返回 HTTP 200 + status=failed + error 40，而非 401）；getOpenSubsonicExtensions 免认证放行；无状态会话、禁用 CSRF；容忍 DSub 附加的 Authorization 头。选择 Spring Security 而非轻量 Filter，换取成熟的过滤器链与认证抽象，代价是引入全家桶依赖——这是与架构「依赖最小化」倾向的一次明确取舍（Status: accepted）。
