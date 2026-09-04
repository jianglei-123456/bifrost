# Bifrost 操作手册 · 连接 Subsonic 客户端

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v1.1 |
| 更新日期 | 2026-09-04 |
| 文档状态 | 定稿 |
| 关联文档 | 《音乐管理功能说明》§8、《Subsonic_API_参考》§2、[00-总览](00-总览.md) |

> 本章是连接客户端的**唯一权威入口**：回答"服务器地址 / 用户名 / 密码到底怎么填"。Bifrost 自身就是 Subsonic 服务端（Syrinx，协议基线 1.16.1 + OpenSubsonic 合规），Feishin、DSub、Symfonium、play:Sub、Supersonic、Ultrasonic 等客户端开箱即连。

---

## 1. 连接三要素

任何 Subsonic 客户端都只需要三样东西：

| 要素 | 填什么 | 示例 |
| --- | --- | --- |
| **服务器地址（URL）** | Bifrost 的访问地址，规则见 §2 | `http://192.168.1.10:18080` |
| **用户名** | Bifrost 账号（v1 唯一账号，默认 `admin`） | `admin` |
| **密码** | 该账号的密码（首次启动时设置的初始密码） | 你设置的密码 |

> 客户端**不需要**你手工计算任何 token——现代客户端自动走"令牌认证"（`t=md5(密码+salt)`），老客户端自动走"密码认证"，都由客户端内部完成，你只需要填真实用户名和密码。

## 2. 服务器地址怎么填（重点）

Bifrost 的 Subsonic 端点挂在 **`/rest/`** 路径下（如 `http://主机:18080/rest/ping.view`），**没有额外上下文路径**。填地址时只有一条规则要记住：

> **先填 `http://<主机>:18080`；若该客户端连不上，把地址末尾补上 `/rest` 再试。**

原因：有的客户端会自动在地址后拼接 `/rest`（此时填 `http://主机:18080` 即可），有的客户端要求完整路径（此时需填 `http://主机:18080/rest`）。两种写法指向同一组端点，没有正确与错误之分，只有客户端适配差异。

### 2.1 主机写什么

| 场景 | 地址 |
| --- | --- |
| 客户端与 Bifrost 在同一台机器 | `http://localhost:18080` |
| 局域网内其它设备（手机/另一台电脑） | `http://<Bifrost主机的局域网IP>:18080`，如 `http://192.168.1.10:18080`（查 IP：Windows `ipconfig`，Linux `ip addr`） |
| 经反向代理公网/HTTPS 访问 | `https://你的域名:端口`（如 `https://music.example.com`；443 端口可省略不写） |

### 2.2 常见填写错误

- ❌ 把 `18080` 写成别的端口（除非你改了 `server.port`）。
- ❌ 地址写成 `http://192.168.1.10:18080/rest/ping.view`——客户端会自动补方法名，**不要填到具体端点**。
- ❌ 漏掉协议头 `http://`（个别客户端允许省略，但建议写全）。
- ❌ 手机连不上却填了 `localhost`——`localhost` 指手机自己，要填电脑的局域网 IP。

## 3. 用户名与密码

- **用户名**：`admin`（v1 唯一账号；可用 `bifrost.auth.initial-username` 修改默认名，修改后以实际为准）。
- **密码**：首次启动时通过环境变量 `BIFROST_AUTH_INITIAL_PASSWORD` 设置的初始密码（见 [01-安装与启动](01-安装与启动.md) §3）。
- 修改密码后（`PUT /api/user/password`，见 [01](01-安装与启动.md) §6），**所有已连接客户端需用新密码重连**。
- 认证失败时服务器返回错误码 **40**（见 [03-常见问题](03-常见问题.md) §3）。

## 4. 分客户端填写指南

以下字段名以常见版本为准，**各版本界面措辞略有差异，按语义对应即可**。核心永远是 §1 的三要素。

| 客户端 | 平台 | 地址字段名 | 地址怎么填 | 用户名 | 密码 |
| --- | --- | --- | --- | --- | --- |
| **Feishin** / Sonixd | 桌面（Win/macOS/Linux） | Server Address | `http://192.168.1.10:18080`（自动拼 `/rest`，令牌认证，推荐首选验证客户端） | admin | 密码 |
| **Symfonium** | Android | URL 字段 + 独立 **Port** 字段 | URL 填主机（`192.168.1.10` 或域名，**不含端口**），Port 填 `18080`；若无独立端口字段则并入地址 | admin | 密码 |
| **DSub** | Android | Server Address | 先填 `http://192.168.1.10:18080`；连不上改成 `http://192.168.1.10:18080/rest`（老客户端，令牌/密码认证均支持，走文件结构浏览） | admin | 密码 |
| **play:Sub** | Android | Server URL | `http://192.168.1.10:18080` | admin | 密码 |
| **Supersonic** | 桌面 | Server URL | `http://192.168.1.10:18080`；设有 Legacy/令牌认证开关时保持默认（令牌）即可 | admin | 密码 |
| **Ultrasonic** | Android | Server URL | `http://192.168.1.10:18080` | admin | 密码 |
| **Sublime Music** | Linux | Server Address | `http://192.168.1.10:18080` | admin | 密码 |
| **Jam / iSub / OutPlayer / Soundwave / Substreamer** | iOS/Android | Server URL / 服务器 | `http://192.168.1.10:18080`（Substreamer 默认令牌认证，若失败找 Legacy auth 开关） | admin | 密码 |

> 兼容性矩阵与验证口径见《音乐管理功能说明》§8.4：Feishin / Sonixd 走 ID3 路径（getArtists/getAlbumList2/search3）是首选验证客户端；DSub 走文件结构浏览（getMusicDirectory）；Symfonium 是 OpenSubsonic 参与方、要求严格，若其通过即兼容性良好。

### 4.1 填完之后的通用检查清单

1. 地址：协议 + 主机 + 端口正确；局域网场景用 IP 而非 localhost（§2.2）。
2. 用户名：`admin` 大小写正确。
3. 密码：与启动时 `BIFROST_AUTH_INITIAL_PASSWORD` 一致；改过密码用新密码。
4. 服务在跑：`curl http://localhost:18080/api/ping` 应返回 `pong`（在 Bifrost 所在机器上执行）。
5. 端口可达：在客户端所在设备浏览器打开 `http://192.168.1.10:18080/api/ping` 能出结果；打不开先查防火墙/监听地址（[03](03-常见问题.md) §2）。

## 5. 连接验证（不依赖客户端）

先用 curl 验证认证与连通性，再排查客户端，能快速定位问题在哪一层：

```bash
# 密码认证（老客户端同款）→ 应返回 status="ok"
curl "http://localhost:18080/rest/ping.view?u=admin&p=你的密码&v=1.16.1&c=test"

# 令牌认证（现代客户端同款，salt 任意 ≥6 字符）→ 应返回 status="ok"
# token = md5(密码 + salt)
curl "http://localhost:18080/rest/ping.view?u=admin&t=<md5值>&s=mysalt&v=1.16.1&c=test"
```

- 返回 `status="ok"`：服务、地址、账号全部正确，问题在客户端填写细节（对照 §4）。
- 返回 `status="failed"` + `error code="40"`：认证失败，用户名/密码不对（[03](03-常见问题.md) §3）。
- 无法连接 / 超时：网络与端口问题（[03](03-常见问题.md) §2）。
- 返回 `error code="0"`：端点未实现（属能力裁剪，非配置错误，见 [03](03-常见问题.md) §5）。
- 端点路径带不带 `.view` 后缀均可（`/rest/ping` 与 `/rest/ping.view` 等价），自行实现客户端的 HTTP 调用两者都接受。

> Windows 下 curl 自带；也可在浏览器直接访问上面的 URL（密码认证那条），看到 XML 即通。

## 6. 连上之后能做什么

| 能力 | 说明 |
| --- | --- |
| 浏览 | 艺术家/专辑/曲目（ID3 路径）；文件目录树（getMusicDirectory，兼容 DSub 系） |
| 搜索 | search2/search3，中文拼音分组索引 |
| 播放 | `stream` 直出原文件（支持 Range/206 续传），**不转码**——客户端需支持你的音频格式（MP3/FLAC/M4A） |
| 歌单 | 创建/编辑/删除静态歌单 |
| 收藏与评分 | 三态收藏（曲目/专辑/艺术家）、1–5 星评分 |
| 播放统计 | 客户端上报 scrobble 后记录播放次数 |
| 封面 | getCoverArt 按需生成缩略图 |
| 触发扫描 | `startScan` 可从客户端触发增量扫描 |

未实现能力（如 getLyrics、Sharing/Podcast、转码等）客户端会显示"未实现"，见《音乐管理功能说明》§8.2 能力矩阵与 [03](03-常见问题.md) §5。
