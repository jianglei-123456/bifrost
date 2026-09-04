# KOReader 图书管理后台调研

> 调研日期：2026-09-03。所有关键论断均回到一手来源（GitHub 源码 / 官方 README / 官方 wiki / 协议规范 / 官方手册）核实，正文内附来源链接。
> 目标：为 KOReader（Android/iOS/墨水屏）自建图书管理后台，实现 ① 图书分发/管理 ② 阅读进度多端同步 ③ PC 端接入同一套同步。

## 概述（先说结论）

1. **进度同步有现成协议（KOSync）**：KOReader 内置 `kosync.koplugin` 插件，协议是极简 HTTP API（4 个端点、2 个自定义认证头），客户端可自由配置自定义服务器地址。自建后台只需实现这 4 个端点即可被所有 KOReader 设备直接使用，无需在设备上装任何东西。
2. **图书分发有现成协议（OPDS 1.x + WebDAV）**：KOReader 原生支持 OPDS 1.x 目录浏览/搜索/下载（并已支持 OPDS-PSE v1.2 按页流式阅读），以及 WebDAV/FTP/Dropbox 云存储。Calibre-Web 或 Calibre 官方 content server 可以直接当图书分发后台用。
3. **官方 sync server 依然活跃维护**：`koreader/koreader-sync-server`（Lua/OpenResty + Redis）未归档，2026 年仍有提交，Docker 一行命令可部署，可直接自建或作为自研后台的参考实现。
4. **PC 端有成熟方案**：**Readest**（24k★，全平台 + Web，Foliate 现代重写）原生支持 KOSync 协议且允许填自定义服务器地址，可接入与手机 KOReader 相同的自建同步服务；另外 **Koodo Reader** README 宣称支持与 KOReader 同步进度；**BookOrbit**（3.7k★）则是一体化自托管平台（Web 阅读器 + KOReader 插件 + OPDS，三端进度/标注同步）。
5. **不推荐**在 PC 上跑 KOReader 模拟器做日常阅读（官方 Windows 从未支持，仅 Linux/macOS 构建 + WSL 折中），PC 端首选 Readest。

---

## 一、进度同步协议（KOSync）

### 1.1 客户端插件位置与协议定义

插件位于 KOReader 主仓库 `plugins/kosync.koplugin/`（旧版曾是 `koplugin/Sync.lua`，现已独立成目录）：

- `main.lua`：UI、同步策略、触发时机
- `KOSyncClient.lua`：HTTP 客户端
- `api.json`：**协议描述文件（端点定义）**
- `KOSyncQueue.lua`：离线补传队列

来源：<https://github.com/koreader/koreader/tree/master/plugins/kosync.koplugin>

`api.json` 内容（一手来源，2026-09-03 抓取）：

```json
{
    "base_url" : "https://sync.koreader.rocks:443/",
    "name" : "koreader-sync-api",
    "methods" : {
        "register":        { "path": "/users/create",             "method": "POST" },
        "authorize":       { "path": "/users/auth",               "method": "GET"  },
        "update_progress": { "path": "/syncs/progress",           "method": "PUT"  },
        "get_progress":    { "path": "/syncs/progress/:document", "method": "GET"  }
    }
}
```

### 1.2 API 端点表

| 端点 | 方法 | 请求体/参数 | 成功状态码 | 说明 |
|---|---|---|---|---|
| `/users/create` | POST | `{ "username": "...", "password": "..." }`（password 已是客户端 MD5 值） | 201 | 注册（402 表示付费墙，兼容旧 poke636api） |
| `/users/auth` | GET | 认证头 | 200 | 校验账号 |
| `/syncs/progress` | PUT | `{ "document": "<32位MD5>", "progress": "...", "percentage": 0.32, "device": "...", "device_id": "...", "metadata": {...}(可选) }` | 200（202=旧进度拒绝覆盖） | 上传进度 |
| `/syncs/progress/:document` | GET | 认证头，`:document` 为文档 MD5 | 200 | 下载进度 |
| `/healthcheck` | GET | 头 `Accept: application/vnd.koreader.v1+json` | 200 `{"state":"OK"}` | 健康检查（server 端） |

来源：`api.json`（<https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/api.json>）；healthcheck 见官方 server README（<https://github.com/koreader/koreader-sync-server>）。

### 1.3 认证方式（HTTP 头）

`KOSyncClient.lua` 中间件源码（一手来源）：

```lua
require("Spore.Middleware.GinClient").call = function(_, req)
    req.headers["accept"] = "application/vnd.koreader.v1+json"
end
require("Spore.Middleware.KOSyncAuth").call = function(args, req)
    req.headers["x-auth-user"] = args.username
    req.headers["x-auth-key"]  = args.userkey
end
```

即所有接口带：

- `x-auth-user: <用户名>`
- `x-auth-key: <密码的 MD5 hex>`（**密码在客户端就 MD5 了**，`local userkey = md5(password)`，见 main.lua）
- `Accept: application/vnd.koreader.v1+json`
- `Content-Type: application/json`

来源：<https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/KOSyncClient.lua>

### 1.4 进度字段语义（自建后台必读）

来自 `main.lua`（<https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/main.lua>）：

- **`document`**：文档指纹（32 位 hex）。默认为文件的 **partial MD5**（见 1.5）；可切换为**文件名 MD5**（`checksum_method = FILENAME` 时 `md5(file_name)`）。
- **`progress`**：
  - 固定版式（PDF 等）：**页码字符串**（`GotoPage(tonumber(progress))`）
  - 重排版（EPUB 等）：**KOReader 的 XPointer 字符串**（如 `/body/DocFragment[20]/body/p[22]/img.0`，`GotoXPointer`）
  - ⇒ 第三方阅读器（PC/Web 端）**只能可靠地使用 `percentage`** 来恢复位置；`progress` 字符串跨阅读器不可移植。
- **`percentage`**：0~1浮点，客户端做了四舍五入。
- **`device`** / **`device_id`**：设备名/设备 ID，纯展示与区分来源。
- **`metadata`**（可选，新版）：`filename` / `title` / `authors`，受插件"发送元数据"开关控制。
- **`timestamp`**（响应侧）：新版客户端用响应里的 `timestamp` 与本地最后翻页时间比较判断新旧；代码注释明确 *"If we are working with an old sync server, we can only use the percentage field"*。**自建后台应返回 `timestamp`**。

同步行为（main.lua）：

- `auto_sync` 默认 **false**（需用户手动开启）；打开文档/恢复联网时拉取，翻页（防抖 10s + 全局 25s）、挂起、断网、关书时推送；离线推送进 `KOSyncQueue` 补传（非 401 错误才重试）。
- 前向/后向策略默认 PROMPT / DISABLE（不会静默跳转），手动拉取总是直接跳转。

### 1.5 partial MD5 文档指纹算法（源码级）

一手来源：`frontend/util.lua` 的 `util.partialMD5`（<https://github.com/koreader/koreader/blob/master/frontend/util.lua>）：

```lua
function util.partialMD5(filepath)
    local step, size = 1024, 1024
    local update = md5()          -- ffi/sha2 的流式 md5
    for i = -1, 10 do
        file:seek("set", lshift(step, 2*i))   -- 1024 << (2*i)
        local sample = file:read(size)        -- 每处读 1024 字节
        if sample then update(sample) else break end
    end
    file:close()
    return update()
end
```

**算法**：从偏移 `0, 1KB, 4KB, 16KB, 64KB, 256KB, 1MB, 4MB, 16MB, 64MB, 256MB, 1GB` 共 **12 个位置各采样 1024 字节**（4 倍递增的非均匀采样，文件头权重高；任一位置越过 EOF 即停止，小文件只采前几段），把采样段**按顺序拼接成最多 12,288 字节的字节流，做一次 MD5**（不是分段 md5 再合并）。

**为什么要 partial MD5**：KOReader 会往 PDF 文件尾追加高亮数据导致 full MD5 变化，故头部非均匀采样对"尾部被修改"稳健。源码注释警告：当文件大小恰好为 1024、4096、16384、65536、262144、1048576、4194304、16777216、67108864、268435456、1073741824 字节附近时，追加数据仍可能改变 digest。

**full vs partial 与"同一本书"匹配**：

- KOSync 里 `document` 用的是 **partial MD5**（打开文档时算好存在 doc_settings 的 `partial_md5_checksum`，插件直接读取）。
- **同一本书的不同格式（EPUB 版 vs PDF 版）、重新打包/OCR/更新的文件 → partial MD5 必然不同 → 无法匹配进度**。这是该协议最大的坑。
- 插件提供两种缓解：
  1. **文件名匹配模式**（`checksum_method=FILENAME`，对文件名做 MD5）：同文件名即同步，但换名/路径无关，跨设备要求同名；
  2. 自建后台在服务端做"书"与"文件指纹"的映射（一个书号对应多个文件指纹），但**协议本身只认指纹**，多格式合并需要客户端 UI 层（如 BookOrbit/Readest 的插件路径）解决。
- 主仓库源码中**没有** full MD5参与同步的逻辑（util.lua 无 getDocumentMD5 类函数）。

**第三方实现一致性**：partial MD5 计算只在客户端，服务端只存 hex 字符串；第三方 server（kosync-dotnet 等）不涉及算法复刻，兼容性好保证。自研后台自行实现 partial MD5（用于"按指纹找书"）时按上述 12 段采样即可。

### 1.6 官方 sync server（可直接自建）

仓库：<https://github.com/koreader/koreader-sync-server>

- **技术栈**：Gin JSON-API 框架（Lua），运行于 **OpenResty**，数据存 **Redis**（README 原文：*"built on top of the Gin JSON-API framework which runs on OpenResty and is entirely written in Lua"*）。注意：当前版本**不是** Node.js/CouchDB；社区另有 Node.js 实现（dobladov/koreader-node-sync-server，<https://github.com/dobladov/koreader-node-sync-server>）。
- **维护状态**：未归档，活跃（GitHub API：`pushed_at: 2026-07-02`；2026-05 有 #42/#45/#46 合入：Debian Trixie 基础镜像、双架构 Docker 发布、Makefile 测试目标）。
- **部署**：`docker run -d -p 7200:7200 koreader/kosync:latest` 一行即可；生产挂载 Redis 数据卷。7200 端口是 HTTPS（**自签证书**），走反向代理 TLS 终结时监听 17200。
- **新增能力**：`ENABLE_USER_REGISTRATION` 环境变量控制是否开放注册（#30，2026-01 合入）。
- **数据模型**（README 示例）：Redis key 形如 `user:chrox:document:<md5>:percentage / :progress / :device`，用户 key 形如 `user:chrox:key`。**不存书名与文件内容**。
- **许可**：AGPL v3。

### 1.7 第三方兼容 sync server 对比

| 项目 | 语言/存储 | API 兼容 | 亮点 | 最近活跃（2026-09-03 查） | 链接 |
|---|---|---|---|---|---|
| **官方 koreader-sync-server** | Lua / OpenResty + Redis | 协议基准 | Docker 一键、健康检查、可关注册 | 2026-07 | <https://github.com/koreader/koreader-sync-server> |
| **kosync-dotnet** | C# .NET 8 / LiteDB | 完全兼容 | **管理 API**（用户/文档增删查、禁用用户）、可关闭注册、可信代理日志 | 2026-08-20 | <https://github.com/jberlyn/kosync-dotnet> |
| **KoInsight** | -（自带面板） | 兼容（可充当 kosync server） | **阅读统计 Web 面板**、高亮同步、KOReader 统计库同步、Docker | 活跃（issue 2025-04 可见） | <https://github.com/GeorgeSG/KoInsight> |
| **AtjonTV/kosync** | Go / - | 兼容 | WebUI | 2026-08-27 | <https://github.com/AtjonTV/kosync> |
| **SolAstrius/kosync-rs** | Rust | 兼容 + 扩展 | 扩展**标注（annotation）同步**与配套 kosync-ext 插件 | 2026-04-14 | <https://github.com/SolAstrius/kosync-rs> |
| lzyor/kosync | Rust / sled | 兼容 | 单二进制 | 2024-07 停更 | <https://github.com/lzyor/kosync> |
| b1n4ryj4n/koreader-sync | Python | 兼容 | 轻量、arm/amd64 Docker | 2024-05 停更 | <https://github.com/b1n4ryj4n/koreader-sync> |
| dobladov/koreader-node-sync-server | Node.js | 兼容 | 老牌 Node 实现 | 停更 | <https://github.com/dobladov/koreader-node-sync-server> |

（活跃度均为 GitHub API `pushed_at`，2026-09-03 查询。）

社区还有公共实例（可作可用性参照，不建议做长期依赖）：`https://kosync.nickthesick.com/`、`https://sync.send2ereader.net/`。

### 1.8 KOReader 客户端如何指向自建 server

源码级结论（main.lua）：设置变量 `custom_server`，入口为插件菜单 **"Custom sync server"** 输入框（默认 `https://`），留空则回落官方 `sync.koreader.rocks`；持久化到 `kosync.lua`。设备上操作路径：**Tools（工具）→ Progress sync → 齿轮/ wrench 菜单 → Custom sync server**，再 Register/ Login 自建账号。

---

## 二、图书拉取/分发协议

### 2.1 OPDS（首选分发协议）

KOReader 的 OPDS 能力来自 `plugins/opds.koplugin/`（`opdsbrowser.lua` / `opdsparser.lua` / `opdspse.lua`），wiki：<https://github.com/koreader/koreader/wiki/OPDS-support>

核实要点：

- **版本支持：仅 OPDS 1.x（Atom XML）**。`opdsparser.lua` 是纯 XML 解析器（基于 luxl，见源码 <https://github.com/koreader/koreader/blob/master/plugins/opds.koplugin/opdsparser.lua>），无 JSON feed 分支 → **不支持 OPDS 2.0**。自建后台请输出 Atom 格式的 OPDS 1.x feed。
- **搜索**：支持两种搜索（源码 `opdsbrowser.lua` 第 42-43、538-597 行附近）：
  1. 目录 entry 带 `rel="search"` 链接，或 OpenSearch 描述文件（`application/opensearchdescription+xml`），模板中 `{searchTerms}` 会被替换；
  2. **自定义目录 URL 里直接写 `%s` 占位符**（`server.url:match("%%s")` 即视为可搜索）——即"search 前缀"用法，如 `https://后台/opds/search?q=%s`。
- **OPDS-PSE（Page Streaming Extension）v1.2**：已支持（插件文件 `opdspse.lua`，stream link rel `http://vaemendis.net/opds-pse/stream`，见 opdsbrowser.lua:46；v1.2 lastRead 支持于 PR #13357，2025-03-05 合入：<https://github.com/koreader/koreader/pull/13357>）。配合支持 PSE 的服务器（Komga/Stump 等）可**按页流式看 PDF/漫画，不必整本下载**。
- **下载与"同步"**：可配置每个目录的 Sync（Sync all 按钮、单次最大下载数默认 50、格式过滤、目标文件夹）（wiki）。
- **认证**：仅 **HTTP Basic**，**不支持 Digest**（wiki 明确警告 + issue #3953）。自建后台若开鉴权，务必用 Basic over HTTPS。
- **接 Calibre**：wiki 给出直连 Calibre content server OPDS 目录的步骤（`http://<host>:8080/opds`）；接 calibre-web 需注意 **URL 尾部斜杠**（`http://host:8083/opds/`）。

### 2.2 内置云存储协议

`plugins/cloudstorage.koplugin/providers/` 目录（一手来源）只有三个文件：**webdav.lua、ftp.lua、dropbox.lua**。
→ KOReader 内置支持 **WebDAV / FTP / Dropbox** 三种云存储（自建后台提供 **WebDAV** 即可被原生访问，无第三方客户端）。

来源：<https://github.com/koreader/koreader/tree/master/plugins/cloudstorage.koplugin>

### 2.3 Calibre 无线集成（Smart Device App 协议，端口 9090）

wiki：<https://github.com/koreader/koreader/wiki/calibre>

- KOReader 的 `calibre.koplugin`（含 `wireless.lua`）实现的是 Calibre **Smart Device App 协议的设备端（客户端）**：KOReader 作为"无线设备"连接电脑上 Calibre 开启的 *Wireless device connection*。
- 能做：浏览/接收 Calibre 推送的书、基于设备端元数据的搜索；收书目录可选 Inbox。
- 端口：**TCP 9090**（可改）；自动发现用 **UDP 54982/48123/39001/44044/59678** 探测（wiki 引用 calibre 源码 driver.py）。
- **对自建后台的意义**：理论上可以自研一个"模拟 Calibre 服务端"（实现 Smart Device App 协议服务端 + UDP 广播）让 KOReader 零配置连上拉书——但该协议复杂（驱动握手/书库元数据序列化/固定模板），社区没有现成的高质量服务端复刻项目，投入产出比远低于 OPDS。**建议只把 Calibre 协议当加分项，不作主线。**

### 2.4 Readest 的 OPDS 客户端能力（PC/Web 端共用分发通道）

来源：官方文档 Library 页（<https://readest.com/docs/library#opds>，2026-09-03 抓取）；issue <https://github.com/readest/readest/issues/2970>。

- **支持 OPDS 目录订阅**：`Import Menu → Online Library` 添加 root URL + 可选用户名/密码（HTTP Basic），在书架顶栏即可浏览目录；可把正在浏览的 feed（含深层子目录）"Add to My Catalogs" 收藏，无需重填地址和凭据。
- **目录侧过滤/排序**：跟随服务端提供的 feed（作者、语言、最新、热门等），宽屏显示 Filters 侧栏。
- **自动下载新条目**：每个已收藏目录可开 "Auto-download new items"，后台轮询目录的 "new" feed 自动拉新书入库，卡片显示上次同步时间与失败数。
- **OPDS-PSE 流式**：已支持——2026-01 还是 feature request（#2970），现已进入官方文档（"Streaming (OPDS-PSE)" 章节）：流式书以 transient 条目入书架，再次打开从上次位置续读，需要本地副本时手动下载。
- **官方点名的兼容服务端**：calibre Content Server（要求认证模式为 basic，否则 400）、Komga、Kavita、Standard Ebooks 等——全部是 OPDS 1.x 生态；文档**未提及 OPDS 2.0** JSON feed。
- **凭据云同步**：目录 URL 全设备同步；用户名/密码需单独开启 "Credentials" 同步类别（加密）。
- 另有 "From Web Browser" 导入：直接在 Readest 内浏览 Calibre-Web/Kavita 等网页版书库，下载即入库（对 OPDS 之外的第二条路）。

**意义**：自建后台只需输出一套 **OPDS 1.x（Atom）feed**，即可同时覆盖 KOReader（手机/墨水屏）和 Readest（PC/移动/Web）两端，分发协议无需做两遍。

### 2.5 现成的"图书后台"软件对比

| 软件 | OPDS | WebDAV | 在线阅读(Web) | KOReader 进度同步 | 状态 | 链接 |
|---|---|---|---|---|---|---|
| **Calibre-Web**（janeczku） | ✅（`/opds`，默认端口 8083，需注意尾部斜杠） | ❌（README 功能表无 WebDAV） | ✅（EPUB/PDF 等多格式） | ❌（无 KOSync；Kobo 同步是 Kobo 专用） | 活跃：18k★，未归档，2026-09-02 有推送 | <https://github.com/janeczku/calibre-web> |
| **Calibre 官方 content server** | ✅（源码 `src/calibre/srv/opds.py`，且 KOReader wiki 给了 `/opds` 接法） | ❌ | ✅（浏览器书库 + Read 按钮 + 离线缓存 + 多端记住位置） | ❌ | 官方（calibre 9.x） | <https://manual.calibre-ebook.com/server.html> |
| **BookOrbit** | ✅ | - | ✅（EPUB/KEPUB/MOBI/AZW3/FB2/PDF/CBZ/CBR/有声书） | ✅ **自有 KOReader 插件：进度+标注双向，Kobo+KOReader+Web 三端** | 年轻但活跃：3.7k★，2026-05 创建，v2.8.1（2026-08-29） | <https://github.com/bookorbit/bookorbit> |

Calibre content server 细节（官方手册）：浏览器访问书库、在线阅读、下载、用户名密码保护、HTTPS、`calibre-server` 命令行部署；手册 <https://manual.calibre-ebook.com/server.html>。OPDS 服务端源码：<https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/opds.py>。

---

## 三、PC 端选型（KOReader 无官方 PC 版）

### 3.1 KOReader 桌面/模拟器构建现状

官方构建文档 `doc/Building.md`（<https://github.com/koreader/koreader/blob/master/doc/Building.md>）：

- 模拟器/桌面构建仅面向 **Linux 与 macOS**；原文：*"Windows users are suggested to develop in a Linux VM or using the Windows Subsystem for Linux"*。
- Linux 可用官方 **AppImage**（`--appimage-extract` 即可跑前端调试）；也可用官方 Docker 开发环境（koreader/virdevenv）。
- WSL 路线：文档提示老 WSL 需自装 XServer，并指向 issue #6354（<https://github.com/koreader/koreader/issues/6354>）。Win11 的 WSL2/WSLg 自带图形，可跑模拟器，但定位是**开发调试工具**，UI 为触屏逻辑，不适合当日常 PC 阅读器。

### 3.2 第三方 PC 阅读器对 KOSync 的支持（逐个核实）

| 客户端 | 平台 | 与 KOReader 同步 | 核实结论 | 链接 |
|---|---|---|---|---|
| **Readest** | Windows/macOS/Linux/Android/iOS/**Web** | ✅ **两条路**：① 标准协议自建/官方 server；② 官方 KOReader 插件全量同步 | 24k★，AGPL v3，活跃（2026-09-03 有推送）；Foliate 的现代重写（Tauri2+Next.js）。官方文档 docs/sync 明确：Readest 端 *Book Menu → KOReader Sync* 可填**自定义服务器 URL**（对应自建 KOSync），Checksum method 选 *File Content*；KOReader 端 Document matching 选 *Binary*（即 partial MD5）。此路径仅同步进度；用 Readest 官方 KOReader 插件（登录同一账号）可同步书文件+进度+书签+高亮+笔记，其 sync server 亦可自建（Supabase schema 见官方 wiki）。还支持直连 BookOrbit 作为阅读同步后端。 | <https://github.com/readest/readest> ；<https://readest.com/docs/sync> ；<https://github.com/readest/readest/wiki/Sync-with-Koreader-devices> |
| **Koodo Reader** | Windows/macOS/Linux/Android/iOS/**Web** | ✅ README 明确 *"Sync reading progress with KOReader"* | 28k★，活跃。但官方文档（use-sync 页）只详述 Pro 云同步（WebDAV/网盘），**未公开 KOReader 同步的机制/可否填自建 server**，采纳前需实测。 | <https://github.com/koodo-reader/koodo-reader> ；<https://www.koodoreader.com/en/use-sync> |
| **Foliate** | 仅 Linux（GTK） | ❌ | 8.6k★，活跃；README/gtk4 分支均无任何同步功能描述。 | <https://github.com/johnfactotum/foliate> |

### 3.3 Web 端方案

- **自建 Web 阅读器 + 直调 KOSync API 完全可行**：协议就是 1.2 节的 4 个 HTTP 端点 + 2 个头；Web 端对 EPUB 用 `percentage` 恢复位置即可（`progress` XPointer 不可移植，见 1.4）。
- 可作为底子的开源 Web 阅读引擎：
  - **foliate-js**（Foliate 作者的浏览器端渲染库；Koodo Reader 官方文档亦推荐商业替代用它）：<https://github.com/johnfactotum/foliate-js>
  - epub.js / readium 等同样可用（本文档不再展开，属常规选型）。
- **更省事的现成品**：Readest 有 Web 版（同一账号同库）；**BookOrbit 自带 Web 阅读器且直接做了 KOReader 同步**（详见 2.4/3.4），是"图书管理后台 + Web 阅读 + 多端同步"最接近的现成整体方案。

### 3.4 折中方案：WSL/虚拟机跑 KOReader 模拟器

- **可行**（官方文档就是这么建议 Windows 用户做的，见 3.1），Win11 WSL2 + WSLg 可直接出图形界面；也可用 virdevenv（Docker）。
- 但它保留触屏 UI 与开发版定位，进度同步虽可用（可填自建 KOSync server），**不适合作为 PC 日常阅读方案**，仅适合验证后台联调。

---

## 四、推荐方案与架构

### 方案 A：全现成组件拼装（推荐起步）

```
                 ┌─────────────────────────────┐
   图书库文件 ──▶ │  Calibre-Web（或 Calibre     │◀── Web 管理/在线阅读（PC 浏览器）
                 │  content server）            │
                 │  - OPDS 1.x: /opds          │◀── KOReader 设备：OPDS 目录浏览/搜索/下载
                 └─────────────────────────────┘
                 ┌─────────────────────────────┐
                 │  kosync-dotnet（或官方       │◀── KOReader 设备：Tools→Progress sync→
                 │  koreader-sync-server）      │    Custom sync server 填本服务地址
                 │  - KOSync 4 端点             │◀── PC 端 Readest：Book Menu→KOReader Sync
                 └─────────────────────────────┘    填同一 server 地址
```

- 图书分发：**OPDS**（Calibre-Web 或 calibre content server 直接提供；均支持 HTTP Basic，KOReader 兼容）。
- 进度同步：**KOSync**（kosync-dotnet 带 Web 管理 API，适合自用；或官方 server）。
- PC 端：**Readest**（原生 KOSync 客户端 + 全平台 + Web 版）。
- 优点：全部开箱即用、各组件可独立升级；缺点：进度/图书数据在两套系统里，无统一"书"概念。

### 方案 B：一体化平台 BookOrbit（功能最贴合，项目较新）

自托管 **BookOrbit**（Docker + Postgres）：内置 Web 阅读器、OPDS、KOReader 专用插件（进度 + 标注双向）、Kobo 同步、阅读统计、多用户/SSO。KOReader 设备装其插件（从后台生成预配置包）即可；Web 端天然就是 PC 阅读器。
风险：项目 2026-05 才创建（v2.8.1），成熟度与长期维护需持续观察。

### 方案 C：自研后台（当用户确实要"自己写后台"时）

最小可用面 = **两组协议，共 8 个端点左右**：

1. **KOSync 4 端点**（`/users/create`、`/users/auth`、`PUT /syncs/progress`、`GET /syncs/progress/:document`）+ `x-auth-user`/`x-auth-key` 头 + 响应带 `timestamp`。实现细则照 1.2~1.4；参考实现读官方 server 的 Lua 源码或 kosync-dotnet。
2. **OPDS 1.x（Atom）feed**：顶级 acquisition feed + `rel="search"`（OpenSearch 模板，`{searchTerms}`→URL 参数）+ acquisition entry（`application/epub+zip` 等下载链接）；鉴权 HTTP Basic；可选加 **OPDS-PSE** stream 链接获得 PDF/漫画按页流式。
3. 文件存储直接本地盘/对象存储；WebDAV 可选（KOReader 原生客户端支持，方便整目录浏览）。
4. PC 端接 Readest（KOSync + OPDS 双协议接入），或基于 foliate-js 做 Web 阅读页并直调自家 KOSync。

**PC 端结论：有现成好方案（Readest），不需要放弃；只有"原生 KOReader PC 版"这一条路不存在。**

---

## 五、风险点

1. **partial MD5 匹配率**：`document` 指纹对"同一文件"稳定，但**同一本书的不同格式、重打包/重命名/新版文件**一律视为不同文档（详见 1.5）。多格式需求要么用文件名匹配模式，要么在 UI 层做书↔多指纹映射（BookOrbit/Readest 插件路径就是这么做的）。
2. **`progress` 字段不可移植**：EPUB 的 progress 是 KOReader XPointer，第三方阅读器/自研 Web 端**只能用 percentage** 恢复位置；按百分比跳转在章节边界附近会有页级偏差（协议固有）。
3. **协议只同步"最后阅读位置"**：官方 KOSync 不含书签/高亮/笔记；需要全量同步时只能走 Readest 官方插件（Readest 生态）或 BookOrbit 插件路线。
4. **认证强度低**：`x-auth-key` = 密码的 MD5，等于明文等效凭据且不可加盐；**必须全程 HTTPS**，并关闭公网开放注册（官方 server 有 `ENABLE_USER_REGISTRATION`，kosync-dotnet 有 `REGISTRATION_DISABLED`）。
5. **OPDS 2.0 不支持**：自建 feed 必须是 OPDS 1.x Atom；2.0 JSON feed 无法被 KOReader 消费（1.1/2.1 源码依据）。OPDS 认证只支持 Basic 不支持 Digest。
6. **官方 server 7200 端口是自签 HTTPS**：直连会有证书告警链路；生产建议反代 TLS 终结（监听 17200）。
7. **版本差异**：旧客户端/旧 server 无 `timestamp`、无 `metadata` 字段（客户端代码显式兼容 percentage 比较）；自建时按"带 timestamp 的完整版"实现并对旧字段做兼容即可向后兼容。
8. **Koodo Reader 的 KOReader 同步机制未文档化**：README 宣称支持但官方文档未说明可否填自建 server，采纳前需实测（3.2）。
9. **BookOrbit 项目较新**（2026-05 创建）：功能契合度最高但成熟度/存续待观察，重要数据做好导出备份。
10. **Calibre Smart Device App 协议不建议模拟**：无现成服务端复刻，实现成本高、收益低（2.3）。

---

## 参考链接（汇总）

**KOReader 主仓库 / 源码**
- https://github.com/koreader/koreader
- https://github.com/koreader/koreader/tree/master/plugins/kosync.koplugin
- https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/api.json
- https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/KOSyncClient.lua
- https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/main.lua
- https://github.com/koreader/koreader/blob/master/frontend/util.lua （util.partialMD5）
- https://github.com/koreader/koreader/tree/master/plugins/opds.koplugin （opdsparser.lua / opdspse.lua / opdsbrowser.lua）
- https://github.com/koreader/koreader/tree/master/plugins/cloudstorage.koplugin （providers: webdav/ftp/dropbox）
- https://github.com/koreader/koreader/tree/master/plugins/calibre.koplugin （wireless.lua）
- https://github.com/koreader/koreader/blob/master/doc/Building.md
- https://github.com/koreader/koreader/issues/6354

**官方 wiki**
- https://github.com/koreader/koreader/wiki/OPDS-support
- https://github.com/koreader/koreader/wiki/calibre

**同步 server**
- https://github.com/koreader/koreader-sync-server
- https://github.com/jberlyn/kosync-dotnet
- https://github.com/GeorgeSG/KoInsight
- https://github.com/AtjonTV/kosync
- https://github.com/SolAstrius/kosync-rs
- https://github.com/lzyor/kosync
- https://github.com/b1n4ryj4n/koreader-sync
- https://github.com/dobladov/koreader-node-sync-server

**OPDS / Calibre 生态**
- https://github.com/koreader/koreader/pull/13357 （OPDS-PSE v1.2 lastRead）
- https://github.com/anansi-project/opds-pse/blob/master/v1.2.md （PSE 规范）
- https://github.com/janeczku/calibre-web
- https://manual.calibre-ebook.com/server.html
- https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/opds.py
- https://github.com/kovidgoyal/calibre/blob/master/src/calibre/devices/smart_device_app/driver.py

**PC 端 / 一体化平台**
- https://github.com/readest/readest
- https://readest.com/docs/sync
- https://github.com/readest/readest/wiki/Sync-with-Koreader-devices
- https://github.com/readest/readest/discussions/1838
- https://github.com/koodo-reader/koodo-reader
- https://www.koodoreader.com/en/use-sync
- https://github.com/johnfactotum/foliate
- https://github.com/johnfactotum/foliate-js
- https://github.com/bookorbit/bookorbit
