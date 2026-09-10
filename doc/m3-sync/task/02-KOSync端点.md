# 02 KOSync 端点（`bifrost-adapter/kosync-server`）

> 阶段目标：让 KOReader 能 Register/Login、推拉进度。**实现纪律**：按 `doc/m3-sync/调研/01-KOSync协议实证.md` §3 的 15 条客户端硬约束逐条落地；只依据客户端 `api.json` + 黑盒可观测行为，**不转写官方 Lua**（ADR-0006）。

---

## T2.1 模块接线（仿 `opds-publisher`）

1. 新建 `bifrost-adapter/kosync-server/pom.xml`：parent `com.bifrost:bifrost-adapter:1.0.0-SNAPSHOT`，内部依赖**不写版本号**；依赖 `spring-boot-starter-web`（`bifrost-core` 经 adapter 父 POM 传递）。
2. 注册进 `bifrost-adapter/pom.xml` 的 `<modules>`。
3. `bifrost-bootstrap/pom.xml` 增加该 artifactId 依赖（否则类不在运行期 classpath）。
4. **不需要**改组件扫描：`BifrostApplication` 已是 `scanBasePackages="com.bifrost"` + `@EntityScan` + `@EnableJpaRepositories` + `@ConfigurationPropertiesScan`。

## T2.2 路径与安全链（**必做，否则裸奔**）

| 路径 | 方法 | 认证 |
|---|---|---|
| `/users/create` | POST | 无（注册本身） |
| `/users/auth` | GET | `x-auth-user` + `x-auth-key` |
| `/syncs/progress` | PUT | 同上 |
| `/syncs/progress/{document}` | GET | 同上 |
| `/healthcheck` | GET | **无**（探活；官方亦为匿名） |

- **新增第 4 条 `SecurityFilterChain`**（`bifrost-bootstrap/.../security/KosyncSecurityConfig.java`，仿 `OpdsSecurityConfig`）：
  ```java
  http.securityMatcher("/users/**", "/syncs/**", "/healthcheck")   // 多模式 → OrRequestMatcher
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())  // 鉴权在自定义过滤器内联，与 /api、/rest 同款
      .addFilterBefore(new KosyncAuthenticationFilter(syncAccountService),
                       UsernamePasswordAuthenticationFilter.class);
  ```
  **背景（必须写进代码注释）**：当前只有 `/api/**`、`/rest/**`、`/opds/**` 三条链命中各自前缀（`SecurityConfig.java:38-59`、`OpdsSecurityConfig.java:32-43`），Spring Boot 的默认链因已存在 `SecurityFilterChain` Bean 而被抑制——**未被任何 `securityMatcher` 命中的新前缀是完全不设防的**。
- 网关与控制器**用同一个开关**：`@ConditionalOnProperty(name = "bifrost.kosync.enabled", havingValue = "true", matchIfMissing = true)`（控制器在 adapter 模块的同名注解）。若两者不同步（例如链没建但控制器在），端点会裸奔——**测试里必须有一条"关闭开关后端点返回 404"的用例**。
- **不开 CORS**（KOReader 不是浏览器，不发 `Origin`）。Readest **Web 版**若要直连需另开——记入 `05-延后项.md`。

## T2.3 认证过滤器（`KosyncAuthenticationFilter`）

仿 `ApiAuthenticationFilter`（`OncePerRequestFilter`，内联鉴权 + 自行写失败响应）：

- 取 `x-auth-user`、`x-auth-key`；`/healthcheck` **直接放行**（豁免名单是硬编码路径，与 `ApiAuthenticationFilter.isExempt` 同风格）。
- 校验：`SyncAccountService.verify(username, key)`（T1.4：解密口令 → md5 → **常量时间比较**）。
- 失败 → **HTTP 401** + `application/json`，body `{"code":3001,"message":"..."}`。
  **401 是唯一不会被客户端重投队列的失败码**（`main.lua:771-775`），所以认证失败绝不能返回 403/400。
- 成功 → 把同步账号 id 写进 request attribute（`KosyncAuthAttributes.ATTR_SYNC_ACCOUNT_ID`，常量放 adapter 模块，与 `SubsonicAuthAttributes` 同款），供控制器读取。
- 401 响应**不设** `WWW-Authenticate: Basic`（那是浏览器语义；避免某些客户端误触发 Basic 认证流程）。

## T2.4 五个端点

统一：`produces = MediaType.APPLICATION_JSON_VALUE`。Spring 写出的 `application/json` 或 `application/json;charset=UTF-8` 都满足客户端正则（**关键：绝不能回 `application/vnd.koreader.v1+json`**，`+` 不在其字符类里，会导致客户端无法解析任何响应体）。**不设置** `X-Framework` 之类的伪装头。

### ① `POST /users/create`（幂等自助注册，R2b）

请求体 `{username, password}`，其中 `password` = **口令的 md5 小写 hex**。

| 分支 | 状态 | body |
|---|---|---|
| 用户名与配置的同步账号**一致**且 `password` 与其口令 md5 相等（**幂等成功**） | **201** | `{"username":"<name>"}` |
| 用户名与配置账号不一致 | **402** | `{"code":3002,"message":"用户名已被占用"}` |
| 用户名一致但口令不符 | **402** | `{"code":3002,"message":"同步账号口令不匹配"}` |
| `bifrost.kosync.registration-enabled=false` | **402** | `{"code":3004,"message":"已关闭注册"}` |
| 用户名/口令缺失或格式非法 | **403** | `{"code":3003,"message":"..."}` |
| 内部错误 | **502** | `{"code":3000,"message":"..."}` |

- **必须回 201**：客户端注册成功只认 201（回 200 = 用户看到失败但账号已建）。
- **402 是 `expected_status` 里的码**，所以这些失败不会让客户端抛异常，且 `message` 会**原样显示给用户**（`main.lua:593`）——文案必须是人话。
- 用户名规则：非空、不含 `:`、≤64（与官方一致地拒绝 `:`，避免歧义）；口令 md5：非空字符串。
- **不泄露账号是否存在**：用户名不一致时统一回"用户名已被占用"，与官方对"已注册"的语义一致。

### ② `GET /users/auth`

| 分支 | 状态 | body |
|---|---|---|
| 凭据有效 | **200** | `{"authorized":"OK"}` |
| 无效/缺失 | **401** | `{"code":3001,"message":"未授权"}` |

客户端**不读响应体**，只看状态码。

### ③ `PUT /syncs/progress`

请求体 `{document, progress, percentage, device, device_id, metadata?}`（`metadata` **接收但忽略**，见 T1.4）。

| 分支 | 状态 | body |
|---|---|---|
| 成功（**无条件覆盖**，绝不返回 202） | **200** | `{"document":"<document>","timestamp":<秒级 epoch>}` |
| 认证失败（过滤器拦截） | **401** | `{"code":3001,...}` |
| `document` 缺失/空/含 `:`/超 64 字符 | **403** | `{"code":3003,"message":"..."}` |
| `percentage` 缺失或非数字、`progress` 缺失或空白、`device` 缺失或空白、`device_id` 缺失或空白 | **403** | `{"code":3003,"message":"..."}` |
| 内部错误 | **502** | `{"code":3000,...}` |

> **与官方的一处有意偏离**：官方只校验 `document`/`percentage`/`progress`/`device`，**不校验** `device_id`。
> 我们要求 `device_id` 非空（403）——客户端把它列为 `required_params`，真实设备**永远会发**；而库里
> `device_id` 是设备表的唯一键，让它为空会把不同设备混成一行。

- `percentage` 接受 0（0 是合法值）；**超范围（<0 或 >1）不拒绝**，按原值存储（客户端已截断到 4 位小数；服务端不做"聪明"的修正）。
- 响应 `timestamp` = 落库的 `reportedAt.getEpochSecond()`，**秒级**（毫秒会让客户端判定"永远更新"从而反复跳转）。
- 端点内只做 upsert + **即时匹配（一次 DB 查询）** + 设备计数 + **异步**触发扫描（T1.5），绝不同步扫库（客户端超时 2/5 秒）。

### ④ `GET /syncs/progress/{document}`

| 分支 | 状态 | body |
|---|---|---|
| 命中 | **200** | `{"document","percentage","progress","device","device_id","timestamp"}` |
| **未知 document** | **200** | `{}`（空对象，**不是 404**） |
| 认证失败 | **401** | `{"code":3001,...}` |
| `document` 非法 | **403** | `{"code":3003,...}` |

- 字段名：`device_id` 用 `@JsonProperty("device_id")`。
- `progress` **原样返回**（字节级；不得 trim/规范化/重编码）——客户端把它直接喂给引擎，且**没有百分比兜底**。
- `device` / `device_id` **原样返回**（客户端用它们做"这是我自己"的判定，任何规范化都会让判定失效）。
- `percentage` 用 JSON **数字**（`double`），不能是字符串。
- **不要在响应里附加任何额外字段**（客户端容忍多余字段，但保持契约最小面）。

### ⑤ `GET /healthcheck`

`200` + `{"state":"OK"}`，匿名。供运维/反代探活（官方 Docker healthcheck 也打这个）。

### 关于 `Accept` 头（有意偏离官方）

官方缺 `Accept` 头或版本不认时返回 **412**。**我们不校验**：客户端总是发送 `application/vnd.koreader.v1+json`，而我们只实现 v1；加这条校验只会凭空多一个失败面。发送 `...v2+json` 的客户端也会被按 v1 服务——记录在案，不视为缺陷。

## T2.5 协议错误处理（`KosyncExceptionHandler`）

- `@RestControllerAdvice(basePackages = "com.bifrost.adapter.kosync")`（与现有两个 advice 隔离，绝不返回 `/api` 的 `ApiResponse` 信封）。
- 全部错误体形如 `{"code":<int>,"message":"<str>"}`，`Content-Type: application/json`。
- 错误码用 **Bifrost 的图书保留段 3000+**（`ErrorCodes` 注释里 2000+ 归音乐、3000+ 归视频/图书），**不照抄官方的 2000-2005**：客户端只按 HTTP 状态码判定，`message` 仅用于展示（调研档 §3 硬约束 12）。

| 码 | 语义 | HTTP |
|---|---|---|
| 3000 | 内部错误 | 502 |
| 3001 | 未授权 | 401 |
| 3002 | 账号/注册冲突 | 402 |
| 3003 | 请求字段非法 | 403 |
| 3004 | 注册已关闭 | 402 |

- 请求体不是合法 JSON / 空 body → **400** + `{"code":3003,...}`（官方此处是 Lua 报错 500；我们给明确错误，KOReader 不会发这种请求）。
- 未注册路径 / 方法不匹配 → Spring 默认（404/405），无需干预。
- 兜底 `Exception` → 502（**不是 500**：与官方"服务端故障"的可观测状态一致，且客户端对两者都是"重试"）。

## T2.6 控制器骨架

```
com.bifrost.adapter.kosync
├── KosyncConstants.java          # 路径常量、响应字段名、Content-Type
├── KosyncAuthAttributes.java     # ATTR_SYNC_ACCOUNT_ID（过滤器与控制器共享）
├── KosyncException.java          # code + HttpStatus
├── KosyncExceptionHandler.java   # @RestControllerAdvice(basePackages)
├── controller/KosyncController.java   # 5 个方法级映射（路径无公共前缀）
└── dto/                          # CreateUserRequest / UserCreatedResponse / AuthorizedResponse /
                                  # ProgressPutRequest / ProgressPutResponse / ProgressGetResponse /
                                  # HealthResponse / KosyncErrorResponse（全部 record）
```

- 控制器**不直接碰仓储**：只调 `bifrost-core` 的 `SyncAccountService` / `ReadingProgressService` / `UnmatchedProgressScanTrigger`。
- `ProgressGetResponse` 用 `@JsonInclude(NON_NULL)` + 静态 `notFound()` 返回全空对象 → 序列化为 `{}`。
- `ProgressPutRequest` 加 `@JsonIgnoreProperties(ignoreUnknown = true)`（容忍 `metadata` 与未来字段）。
- 账号 id 从 request attribute 取（过滤器已鉴权），控制器不再重复校验。

## T2.7 测试

| 测试 | 类型 | 覆盖 |
|---|---|---|
| `KosyncControllerTest` | `@SpringBootTest` + MockMvc | 5 端点全分支矩阵；**`Content-Type` 断言为 `application/json` 开头**；注册 201/402；认证 401；未知 document → 200 `{}`；`device`/`device_id`/`progress` 原样回显；`timestamp` 是秒级整数；错误体带 `message` |
| `KosyncSecurityTest` | 集成 | 未带凭据 → 401（不是 403/404）；`/healthcheck` 匿名；**`bifrost.kosync.enabled=false` → 5 个端点 404（验证"裸奔"不会发生）** |
| `KosyncContractTest` | 集成 | 完整"设备握手"：create → auth → put → get → 另一设备 get 到同一进度；重复/乱序/更旧的推送照样覆盖 |
| hurl `hurl/kosync/*.hurl` | 契约 | 04 阶段落地（对真实 jar 跑，与 opds/api/rest 同级） |

完成后：`./mvnw -pl bifrost-adapter/kosync-server -am test` 通过 → 提交。
