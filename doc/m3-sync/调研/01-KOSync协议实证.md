# KOSync 协议实证（面向服务端实现）

> 取证日期：2026-09-16。证据来源：客户端 `E:\Dev\jianglei\koreader` @ `062a844e`（`plugins/kosync.koplugin/`、`frontend/util.lua`、`frontend/readerrolling.lua`、`frontend/readerpaging.lua`）+ 官方服务端 `E:\Dev\jianglei\koreader-sync-server` @ `597e064`（黑盒可观测行为 + Lua 源码）。
> 用途：M3-sync 的实现依据。**本文档记录"事实"，我们的取舍记录在 `doc/m3-sync/task/` 各任务档。**
> ⚠️ 旧调研档 `doc/调研/KOReader图书管理后台调研.md` 与 `doc/m2-book/调研/01-KOReader生态与OPDS.md` 有三处错误，已在 §7 列出勘误——**以本文档为准**。

---

## 一、客户端请求构造

### 1.1 端点与字段

来源：客户端 `plugins/kosync.koplugin/api.json`（`api.json:5-53`）、`KOSyncClient.lua:66-178`。路径无版本前缀（版本靠 `Accept` 头协商），URL 为**根路径**。

| 端点 | 方法 | 认证头 | 请求体 | 客户端认可的成功码 | 备注 |
|---|---|---|---|---|---|
| `/users/create` | POST | **仅** `accept` | `{username, password}` | **201** | `password` 是**口令的 md5 小写 hex**（`main.lua:568`）；不传 `x-auth-*` |
| `/users/auth` | GET | `x-auth-user` `x-auth-key` | 无 | **200** | 客户端**不读响应体**，只看状态码（`KOSyncClient.lua:99`） |
| `/syncs/progress` | PUT | `x-auth-user` `x-auth-key` | `{document, metadata?, progress, percentage, device, device_id}` | **200** | `metadata` 为可选（默认不发） |
| `/syncs/progress/:document` | GET | `x-auth-user` `x-auth-key` | 无（无查询串） | **200** | `:document` 直接代入路径 |
| `/healthcheck` | GET | 仅 `accept` | 无 | 200 | 官方有，**不在客户端 `api.json`**，运维用 |

每个请求固定头（`KOSyncClient.lua:29-35`）：`accept: application/vnd.koreader.v1+json`；有 JSON body 时 `content-type: application/json`；`user-agent` 由 lua-Spore 设置为 `lua-Spore v0.4.2`。

### 1.2 字段类型（客户端实际发出的形态）

- `document`：**32 位小写 hex** 字符串（见 §4）。
- `progress`：**字符串**——固定版式是页码（`tostring(number)`），重排版是 CRE XPointer 串（`KOSyncClient.lua:130` 统一 `tostring`）。
- `percentage`：**JSON 数字**，0–1（不是 0–100），4 位小数截断（`optmath.lua:18-20` `math.floor(x*10000)/10000`）。
- `device`：字符串——`kosync_hostname` 或 `Device.model`（`main.lua:741`），用户可改成任意自由文本。
- `device_id`：字符串 UUID，客户端**每次安装生成一次并持久化**（`reader.lua:42-46`），不是硬件标识。
- `metadata`：`{filename, title, authors}`，**仅当用户开启 `send_metadata`**（默认 **false**，`main.lua:66,694-703`）。`title`/`authors` 可能为 null，服务端必须容忍缺键/多键。
- 客户端**不发送**：cookie、`Authorization`、`Accept-Encoding`、`If-Match`/ETag、查询串、文件大小、页数、阅读时长、书签/标注数据。

### 1.3 自定义服务器 URL 的规范化

`custom_server` 被**逐字**作为 `base_url`（`main.lua:463-466`、`KOSyncClient.lua:23-27`），随后由 lua-Spore 拼接方法路径并把**所有 `//` 折叠成 `/`**：

| 用户输入 | 方法路径 | 实际 URL |
|---|---|---|
| （未设置） | `/users/auth` | `https://sync.koreader.rocks/users/auth` |
| `http://192.168.1.10:18080` | `/syncs/progress` | `http://192.168.1.10:18080/syncs/progress` |
| `http://host:18080/kosync` 或 `.../kosync/` | `/syncs/progress` | `http://host:18080/kosync/syncs/progress` |
| `host:18080`（无 scheme） | — | **失败**（lua-Spore 断言 scheme） |

- **不补 scheme、不补端口、不处理尾斜杠**；菜单输入框预填 `"https://"`（`main.lua:216`），用户必须自己改成 `http://`。
- 结论：**路径前缀技术上是可行的**，但 Bifrost 选择挂根路径（端点少、用户少打一段、与官方一致），设备上填 `http://<host>:18080` 即可。

### 1.4 超时

`PROGRESS_TIMEOUTS = {2, 5}`、`AUTH_TIMEOUTS = {5, 10}`（秒，`KOSyncClient.lua:6-8`，仅约束 LuaSocket 同步路径）；异步推送走 Turbo，连接 10s / 请求 20s。**含义：`PUT /syncs/progress` 必须轻量**——任何秒级以上的服务端处理（例如在请求里扫全库）都会让设备端超时。

---

## 二、官方服务端的可观测契约（状态码 / 错误体矩阵）

官方所有响应：`Content-Type: application/json`，错误体恒为 `{"code": <int>, "message": "<str>"}`（`config/errors.lua:15-23`）。**下面是我们需要复刻的"形状"**（文案自拟，见 ADR-0006 的 AGPL 纪律）。

### 2.1 `POST /users/create`

| 分支 | 状态 | body |
|---|---|---|
| 新建成功 | **201** | `{"username": "<name>"}` |
| 用户名已存在 | **402** | `{code, message}` |
| 关闭注册 | **402** | `{code, message}`（如 "User registration is disabled."） |
| 用户名/口令非法 | 403 | `{code, message}` |
| 后端错误 | 502 | `{code, message}` |

### 2.2 `GET /users/auth`

| 分支 | 状态 | body |
|---|---|---|
| 凭据有效 | **200** | `{"authorized":"OK"}` |
| 缺头 / 空值 / 未知账号 / 口令不符 | **401** | `{code, message}`（官方不区分"未知用户"与"口令错"） |
| 后端错误 | 502 | `{code, message}` |

### 2.3 `PUT /syncs/progress`

| 分支 | 状态 | body |
|---|---|---|
| 成功（**无条件覆盖**） | **200** | `{"document":"<doc>","timestamp":<秒级 epoch>}` |
| 认证失败 | **401** | `{code, message}` |
| `document` 缺失/为空 | 403 | `{code, message}` |
| `percentage` 不可解析 / `progress` 空 / `device` 空 | 403 | `{code, message}` |
| 后端错误 | 502 | `{code, message}` |

**官方没有"拒绝旧进度"分支**：`73b9d53`（2016-07-24）删除了 `202 Not the furthest progress.`，现行写入不读旧值、不做任何比较，纯按到达顺序后写覆盖（`syncs_controller.lua:157-176`）。

### 2.4 `GET /syncs/progress/:document`

| 分支 | 状态 | body |
|---|---|---|
| 命中 | **200** | `{percentage, progress, device, device_id, timestamp, document}`，**每个字段仅在已存储且非空时出现** |
| **未知 document（首次查询）** | **200** | `{}`（空 JSON 对象——**不是 404**） |
| `document` 非法 | 403 | `{code, message}` |
| 认证失败 | 401 | `{code, message}` |
| 后端错误 | 502 | `{code, message}` |

> 路径参数在官方框架里只匹配 `[A-Za-z0-9_]+`，含 `-`/`.`/`/` 的 document 会变成 404——我们的 document 恒为 32 位 hex，不受影响。

### 2.5 `GET /healthcheck`

`200` + `{"state":"OK"}`（同样要求 `Accept` 头）。

### 2.6 官方"没有"的东西（决定了自研的必要性）

无用户列表、无删用户、无改口令、无进度列表、无删除进度、无 TTL/过期、无速率限制、无应用日志、无 metrics。`config/routes.lua:9-16` 即全部路由。

---

## 三、客户端硬约束（违反即坏，逐条都有源码依据）

1. **响应 `Content-Type` 必须是 `application/json`**。客户端被官方 patch 过的 JSON 中间件只认 `^%s*application/[%w.-]+/?json%s*$`（`koreader-base` `thirdparty/lua-Spore/detect-more-json-content-types.patch`）——**`application/vnd.koreader.v1+json` 里的 `+` 不在字符集内，不会被解码**。后果：body 保持字符串 → 拉取永远提示"没找到进度"、注册/登录失败提示"未知服务器错误"。大小写敏感，charset 只能放在 `;` 之后。
2. **`progress` 必须字节级原样回显**。重排版跳转是 `GotoXPointer(progress)` 直喂 crengine（`main.lua:705-712`、`readerrolling.lua:772-785`），**无校验、无修复、无百分比兜底**；固定版式走 `tonumber(progress)`，非数字则静默不动（`readerpaging.lua:1128-1145`）。**服务端改写/省略 XPointer = 位置丢失，无法恢复。**
3. **必须返回 `timestamp`**（秒级 epoch）。客户端拿它跟本机 `last_page_turn_timestamp`（`os.time()`）比新旧（`main.lua:897-903`）；**毫秒值会被判成"永远更新"**，导致设备每次拉取都往前跳。缺失时退化为 `body.percentage > 本地 percentage`（官方注释称为"old sync server"兼容路径）。
4. **推送成功只能回 200**；**202 会被判为失败并重投队列**（`api.json:44` 列了 202 所以不抛错，但 `KOSyncClient.lua:137` 只认 200）。
5. **注册成功只能回 201**（`KOSyncClient.lua:78`）；回 200 会让用户看到注册失败但账号已建。
6. **认证失败必须 401**——这是**唯一不会被重投队列**的失败码（`main.lua:771-775`）；其它任何失败（403/404/409/500/超时）都会被当作可重试并进离线队列。
7. **未知 document 回 200 `{}`，不要回 404**。404 会让客户端抛"404 not expected"→ 显示笼统的"同步出错"，而不是友好的"没找到进度"（`main.lua:844-859`）。
8. **200 响应体必须是合法 JSON 对象**，不能是 `null`、`[]` 或空 body。
9. **不要做任何 3xx 跳转**（未启用重定向中间件，3xx 不在 `expected_status` 里 → 抛错）；**不要 gzip**（客户端不发 `Accept-Encoding`）。
10. **`percentage` 必须是 JSON 数字且 0–1**；字符串会让 `Math.roundPercent` 里的 `percent * 10000` 崩。
11. **`device` / `device_id` 必须原样回显**（客户端用两者做"这是我自己"的判定，`main.lua:861-862`；`device` 还会拼进确认框文案）。任何规范化/转义都会让判定失效。注意客户端自身有个不对称 bug：推送发的是 `kosync_hostname or Device.model`，比较只对 `Device.model`——**设了自定义设备名的设备会对自己弹"是否同步"**，这不是服务端问题。
12. **错误体必须带 `message`**：注册/登录失败时客户端**原样把它显示给用户**（`main.lua:593,633`），没有 `message` 就显示"未知服务器错误"。
13. **服务端必须容忍重复 / 乱序 / 更旧的推送**。客户端离线队列的 drain 只要本地调用没抛异常就记为已发送（异步回调是空函数、`update_progress` 无返回值，`main.lua:1032-1045`），服务端不可用期间的推送会被丢弃；队列本身还有"每 document 每天一条、上限 200、4 周过期"的合并策略（`KOSyncQueue.lua:5-7,39-65`）。
14. **不要在请求内做重活**（见 §1.4 超时）。
15. **客户端不校验证书**（Turbo 路径 `verify_ca = false`），自签 HTTPS 与明文 HTTP 都能用；**没有任何请求签名/防重放**，`x-auth-key` 就是口令的 md5，等价 bearer。

---

## 四、文档指纹（`document`）算法

### 4.1 默认模式：文件内容 partial MD5（`checksum_method = BINARY`，默认，`main.lua:65`）

由 `util.partialMD5`（`frontend/util.lua:1094-1111`）计算，首次打开时算好并存进书籍侧车设置（`readerui.lua:497-500`），插件只读缓存值。

**算法**：在 **12 个采样点**各读 **1024 字节**，按顺序把读到的片段拼成一个最多 12,288 字节的字节流，**整体做一次 MD5**（不是分段 MD5 再合并）：

```
偏移 = 0, 1KB, 4KB, 16KB, 64KB, 256KB, 1MB, 4MB, 16MB, 64MB, 256MB, 1GB
     = 0 与 1024 << (2k)   (k = 0..10)
```

- 任一采样点**越过 EOF 即停止**（小文件只采前几段；不足 1024 字节的尾巴读到多少算多少）。
- 源码里的写法是 `for i = -1, 10 do file:seek("set", lshift(step, 2*i))`；`i = -1` 时在 LuaJIT 的 32 位位移语义下得到偏移 0（**此处为推断**，但"12 个点含 0"与现有各语言复刻实现的共同行为一致）。
- 输出为**小写 hex**。
- **Java 实现必须用二进制读**（`RandomAccessFile`/`FileChannel`），绝不能用 `Reader`——文本模式会做换行转换，改变字节流。
- 为什么不是 full MD5：KOReader 会往 PDF 尾部追加高亮数据，full MD5 会变；头部非均匀采样对"尾部被改"稳健。

参考测试向量（客户端自带的 `spec/unit/util_spec.lua:340,343`，其 fixture 不在本地 checkout，仅作对照）：
`tall.pdf → 41cce710f34e5ec21315e19c99821415`、`leaves.epub → 59d481d168cca6267322f150c5f6a2a3`。
本地验证手段：对 `data-sample/ebook/*` 生成黄金值做回归保护；另用一份独立脚本实现交叉比对；真实书库样本的真机核对由用户完成。

### 4.2 可选模式：文件名 MD5（`checksum_method = FILENAME`）

`md5(<basename>)`——**仅文件名，不含路径**（`main.lua:678-692` 用 `util.splitFilePathName` 取 basename 后 `md5`）。用户在设备上手动切换（菜单项 "Document matching method" → "Filename. Files with matching names will be kept in sync."）。

**影响**：切到该模式后同一本书的 `document` 与内容指纹模式**完全不同**，服务端所有已存进度都变成孤儿。Bifrost 的处理：不把文件名模式当主映射，只在孤儿页给"建议绑定"时用它算候选（候选来源：`md5(库内文件名)` 与 `md5(标题 + "." + 扩展名)`，后者对应 OPDS 下发时的文件名——见 `OpdsController` 的 `Content-Disposition` 命名规则）。

### 4.3 多格式 / 重打包的后果

同一本书的不同格式（EPUB vs PDF）、重新打包、OCR、改版 → partial MD5 **必然不同**。KOSync 协议只认指纹、不认书，因此**跨格式续读在客户端侧无法实现**（服务端兜底也不行：见硬约束 2）。Bifrost 的定位是：把指纹映射回"书"，让**管理端**具备书级视图，而不是伪装成跨格式续读。

---

## 五、客户端同步触发与用户设置（操作手册素材）

- `auto_sync` **默认 false**（`main.lua:61`）——用户必须在 **Tools → Progress sync → "Automatically keep documents in sync"** 打开。
- 触发点：打开文档（auto 时拉）、恢复联网（+0.5s 拷队列后拉）、从挂起恢复（+1s 拉）、断网（推）、挂起（推）、关书（在线推/离线入队）、翻页（防抖 10s，且"每 N 页"默认 Never）、手动菜单与 dispatcher 事件。
- 全局去抖：非交互式调用 **25 秒**（`main.lua:52,723,821`）。
- 新旧策略默认：**"Sync to a newer state" = Prompt**、**"Sync to an older state" = Never**（`main.lua:63-64`）；手动拉取不弹确认，直接跳。
- **"Document matching method" 默认 Binary**；"Send document metadata" 默认关。
- 关书时若无网络则入队；队列存 `<settings>/kosync_queue.lua`。

### 设备接入步骤（写进操作手册）

1. 管理端「阅读进度」页创建/查看**同步账号**（用户名 + 口令，口令可回显）。
2. KOReader：**Tools → Progress sync → Custom sync server**，填 `http://<host>:18080`（**必须带 `http://`**，输入框预填的是 `https://`）。
3. 回到 Progress sync → **Register / Login**：用同步账号的用户名与口令（协议内部转 md5）。
4. 打开 **"Automatically keep documents in sync"**；`Document matching method` 保持 **Binary**。
5. 多设备重复 2–4 步，使用**同一个同步账号**。

---

## 六、客户端不提供服务端可用信息（明确"拿不到"）

- 没有书签/高亮/笔记同步协议：标注只能导出到书籍旁的 `<book>.annotations.lua` 文件（`readerannotation.lua:255-277`），**不是 API**。
- 阅读统计（`statistics.koplugin`）是**整个 SQLite 文件**经云存储插件同步（`main.lua:3095-3105`、`cloudstorage.koplugin/main.lua:197-242`），目标是 WebDAV/Nextcloud 之类的文件服务，**不是 KOSync**。
- `opds.koplugin` 只**消费**服务端给的 `pse:lastRead`（`opdsbrowser.lua:840-859`），**从不把阅读位置推给 OPDS 服务端**；唯一的拉取是 Kavita 专用流程（`opdspse.lua:18-93`）。
- 因此：#1 之外，Bifrost 无法从 KOReader 侧获得"这本书读到哪"的任何其它形态。

---

## 七、对旧调研档的勘误（以本文档为准）

| # | 旧说法（`doc/调研/KOReader图书管理后台调研.md`、`doc/m2-book/调研/01-KOReader生态与OPDS.md`） | 实证结论 |
|---|---|---|
| 1 | "`PUT /syncs/progress` 成功 200（**202 = 旧进度拒绝覆盖**）" | **202 早已不存在**：官方 2016-07-24 `73b9d53` 删除该分支，现行是无条件后写覆盖。客户端把 202 当**失败**并重投离线队列。客户端 `api.json` 里残留的 `expected_status: [200,202,401]` 是历史遗留。 |
| 2 | 未提及响应 `Content-Type` 要求 | **必须回 `application/json`**；回 `application/vnd.koreader.v1+json` 会让客户端**无法解析任何响应体**（硬约束 1）。 |
| 3 | "第三方阅读器只能可靠地用 `percentage` 恢复位置" | 对**第三方阅读器**成立，但**KOReader 自身相反**：它只用 `progress` 跳转，`percentage` 仅用于判定与提示文案，**没有百分比→位置的兜底**。服务端因此必须原样保存 `progress`。 |

---

## 八、未取证 / 不确定项（诚实标注）

1. `util.partialMD5` 中 `i = -1` 时的 `lshift` 语义（偏移 0 的来源）为推断，§4.1 已标注；实现按"12 点含 0"落地，并用真实设备串行验证。
2. lua-Spore 与 `koreader-base` 的 `base/` 子模块**未在本 checkout 落地**（`base/` 为空，pin 在 `9f9b6640`），相关结论依据其上游 0.4.2 源码与该 pin 的 patch 说明。
3. Readest 的 KOSync 实现（checksum 算法、是否发送 `metadata`、是否校验 `Content-Type`）**未取证**——Readest 不进本里程碑验收门槛（Q4 决策），仅要求"不被我们破坏"。
4. 客户端同步路径的 TLS 细节（LuaSocket/LuaSec 同步分支）未在本地取证，但异步推送分支已确认 `verify_ca = false`。
