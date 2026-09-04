# 01 KOReader 生态与 OPDS 协议（用户原始调研文档）

> 调研日期：2026-09-03。所有关键论断均回到一手来源（GitHub 源码 / 官方 README / 官方 wiki / 协议规范 / 官方手册）核实。
> 目标：为 KOReader（Android/iOS/墨水屏）自建图书管理后台，实现 ① 图书分发/管理 ② 阅读进度多端同步 ③ PC 端接入同一套同步。

---

> **本轮范围（Q1 决策）**：本调研文档描述的"进度同步（KOSync）"功能在 M2-book **Day-one 不实现**——本调研文档作为协议理解存档保留，进度同步留作后续里程碑（见 `05-延后项.md`）。

---

> **本节内容**：用户调研原文全文存档。下方文字是 `doc/调研/KOReader图书管理后台调研.md` 原文复制——**未做删改**。两份文档长期共存：本文档是 M2-book mini-milestone 的官方调研档；原 `doc/调研/KOReader图书管理后台调研.md` 保持不动供历史追溯。

---

# KOReader 图书管理后台调研

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

### 1.3 认证方式（HTTP 头）

```
- x-auth-user: <用户名>
- x-auth-key:  <密码的 MD5 hex>  （密码在客户端就 MD5 了）
- Accept: application/vnd.koreader.v1+json
- Content-Type: application/json
```

来源：<https://github.com/koreader/koreader/blob/master/plugins/kosync.koplugin/KOSyncClient.lua>

### 1.4 进度字段语义（自建后台必读）

- **`document`**：文档指纹（32 位 hex）。默认为文件的 **partial MD5**（12 段非均匀采样）；可切换为**文件名 MD5**。
- **`progress`**：固定版式（PDF）= 页码字符串；重排版（EPUB）= KOReader XPointer 字符串。**第三方阅读器只能可靠使用 `percentage` 恢复位置**。
- **`percentage`**：0~1 浮点。
- **`device` / `device_id`**：设备名/设备 ID。
- **`metadata`**（可选）：`filename` / `title` / `authors`。
- **`timestamp`**（响应侧）：新版客户端用响应里的 `timestamp` 与本地比较判断新旧。**自建后台应返回 `timestamp`**。

### 1.5 partial MD5 文档指纹算法

从偏移 `0, 1KB, 4KB, 16KB, 64KB, 256KB, 1MB, 4MB, 16MB, 64MB, 256MB, 1GB` 共 12 个位置各采样 1024 字节，**按顺序拼接后做一次 MD5**。

**为什么不存 full MD5**：KOReader 会往 PDF 文件尾追加高亮数据导致 full MD5 变化。

**同书多格式问题**：EPUB 版 vs PDF 版 / 重新打包 / OCR / 更新 → partial MD5 必然不同 → 无法匹配进度。缓解方式：① 文件名匹配模式；② 自建后台在服务端做"书"与"文件指纹"的映射（但协议本身只认指纹）。

### 1.6 官方 sync server（可直接自建）

仓库：<https://github.com/koreader/koreader-sync-server>

- 技术栈：Gin JSON-API 框架（Lua） + OpenResty + Redis
- 维护状态：2026-07-02 仍有推送
- 部署：`docker run -d -p 7200:7200 koreader/kosync:latest`
- 端口 7200 是 HTTPS（**自签证书**）
- 数据模型：Redis key `user:chrox:document:<md5>:percentage/:progress/:device`
- 许可：AGPL v3

### 1.8 KOReader 客户端如何指向自建 server

源码级结论：设置变量 `custom_server`，入口为插件菜单 **"Custom sync server"** 输入框；持久化到 `kosync.lua`。

---

## 二、图书拉取/分发协议

### 2.1 OPDS（首选分发协议）

- 版本支持：仅 OPDS 1.x（Atom XML）→ 自建后台请输出 Atom 格式
- 搜索：支持 OpenSearch 1.1 描述文件 + 模板 `{searchTerms}` 替换；也支持 KOReader 的 `%s` 占位符 URL
- OPDS-PSE v1.2：已支持（流式阅读，PR #13357，2025-03-05）
- 认证：**仅 HTTP Basic**，**不支持 Digest**
- 接 Calibre：`http://<host>:8080/opds`；接 calibre-web 注意 URL 尾部斜杠

### 2.2 内置云存储协议

KOReader 内置支持 **WebDAV / FTP / Dropbox** 三种云存储。来源：<https://github.com/koreader/koreader/tree/master/plugins/cloudstorage.koplugin>

### 2.3 Calibre 无线集成（Smart Device App 协议，端口 9090）

理论上可自研"模拟 Calibre 服务端"让 KOReader 零配置拉书，但协议复杂、社区无现成高质量复刻，**投入产出比低**——建议只作加分项。

### 2.4 Readest 的 OPDS 客户端能力

- 支持 OPDS 目录订阅 + 自动下载新条目 + OPDS-PSE 流式
- 兼容服务端：calibre Content Server / Komga / Kavita / Standard Ebooks
- 凭据云同步：目录 URL 全设备同步；用户名/密码单独开启 "Credentials" 同步
- 另有 "From Web Browser" 导入

### 2.5 现成的"图书后台"软件对比

| 软件 | OPDS | WebDAV | 在线阅读(Web) | KOReader 进度同步 |
|---|---|---|---|---|
| Calibre-Web | ✅ | ❌ | ✅ | ❌ |
| Calibre content server | ✅ | ❌ | ✅ | ❌ |
| BookOrbit | ✅ | - | ✅ | ✅（自有插件） |

---

## 三、PC 端选型（KOReader 无官方 PC 版）

### 3.1 KOReader 桌面/模拟器构建现状

仅 Linux/macOS；Windows 用户用 WSL 折中；定位开发调试工具，不适合日常 PC 阅读。

### 3.2 第三方 PC 阅读器对 KOSync 的支持

| 客户端 | 平台 | 与 KOReader 同步 | 核实结论 |
|---|---|---|---|
| Readest | Win/macOS/Linux/Android/iOS/**Web** | ✅ | Foliate 现代重写（Tauri2+Next.js）。原生支持 KOSync 自定义 server |
| Koodo Reader | 同上 | ✅ README 宣称 | 但官方文档未说明可否填自建 server |
| Foliate | 仅 Linux (GTK) | ❌ | 8.6k★，活跃；无任何同步功能 |

### 3.3 Web 端方案

- 自建 Web 阅读器 + 直调 KOSync API 完全可行（协议就是 4 端点 + 2 头）
- 浏览器端渲染库：foliate-js / epub.js / readium
- Readest 有 Web 版（同一账号同库）；BookOrbit 自带 Web 阅读器

### 3.4 折中方案：WSL/虚拟机跑 KOReader 模拟器

可行，但 UI 为触屏逻辑，进度同步虽可用，**不适合作为 PC 日常阅读方案**。

---

## 四、推荐方案与架构

### 方案 A：全现成组件拼装（推荐起步）

- 图书分发：Calibre-Web 或 Calibre content server
- 进度同步：kosync-dotnet 或官方 koreader-sync-server
- PC 端：Readest

### 方案 B：一体化平台 BookOrbit

自托管 BookOrbit（Docker + Postgres）：内置 Web 阅读器 + OPDS + KOReader 专用插件（进度+标注双向）+ 阅读统计 + 多用户/SSO。

### 方案 C：自研后台

最小可用面 = 两组协议，共 ~8 个端点：

1. **KOSync 4 端点** + 2 自定义头 + 响应带 `timestamp`
2. **OPDS 1.x Atom feed**：顶级 acquisition feed + `rel="search"` + acquisition entry + HTTP Basic

**PC 端结论**：有现成方案（Readest），不需要放弃 KOReader PC 版（不存在）。

---

## 五、风险点

1. **partial MD5 匹配率**：同书不同格式/重打包/重命名一律视为不同文档
2. **`progress` 字段不可移植**：EPUB 的 progress 是 XPointer，第三方阅读器只能用 percentage
3. **协议只同步"最后阅读位置"**：不含书签/高亮/笔记
4. **认证强度低**：`x-auth-key` = 密码 MD5 = 明文等效
5. **OPDS 2.0 不支持**：自建 feed 必须是 OPDS 1.x Atom
6. **官方 server 7200 端口是自签 HTTPS**
7. **版本差异**：旧客户端/旧 server 无 timestamp、metadata
8. **Koodo Reader 的 KOReader 同步机制未文档化**
9. **BookOrbit 项目较新**（2026-05 创建）
10. **Calibre Smart Device App 协议不建议模拟**

---

## 关键来源（汇总）

- KOReader 主仓库 / 源码：<https://github.com/koreader/koreader>
- 官方 wiki：<https://github.com/koreader/koreader/wiki/OPDS-support>
- 同步 server：<https://github.com/koreader/koreader-sync-server>
- OPDS / Calibre 生态：<https://manual.calibre-ebook.com/server.html>
- PC 端：<https://github.com/readest/readest>

---

> **本轮（M2-book Day-one）落地选择**：
> - 协议分发：OPDS 1.x（KOReader + Readest 双端），**`/opds/v1.2/catalog/...` 标准路径**
> - 进度同步：留作"已知未实现"边界（见 `05-延后项.md`）
> - 实体模型：物理隔开（不复用 music 表），见 `doc/adr/0004-book-physical-isolation.md`
