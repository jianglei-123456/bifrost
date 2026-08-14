# Subsonic API 参考（Bifrost 服务端实现指南）

> 本文档面向为 Bifrost（音乐库管理器）实现 Subsonic 兼容服务端的开发者。
> 目标基线：**Subsonic REST API 1.16.1**（对应 Subsonic 服务端 6.1.4），并尽量兼容社区维护的 **OpenSubsonic** 扩展规范。
> 主要依据：官方文档 <https://www.subsonic.org/pages/api.jsp>（及 Wayback Machine 存档的旧版）与 OpenSubsonic 规范 <https://opensubsonic.netlify.app/docs/api-reference/>（源码仓库 opensubsonic/open-subsonic-api）。

---

## 1. 协议概述

**Subsonic API** 是 Subsonic 音乐流媒体服务器对外提供的 REST 风格 HTTP API。所有第三方客户端（手机 App、桌面播放器、Web 应用）都通过它访问媒体库、流式播放、管理播放列表与收藏等。Bifrost 只要实现该 API，就能直接兼容整个 Subsonic 客户端生态。

要点：

- **传输方式**：HTTP GET 为主，路径形式为 `/rest/<method>.view`（例如 `/rest/ping.view`）。OpenSubsonic 额外正式支持 `application/x-www-form-urlencoded` 的 **POST**（扩展名 `formPost`），用于突破 URL 长度限制（例如创建含大量歌曲的播放列表时）。
- **输出格式**：所有非二进制端点返回 XML 文档（默认），通过参数 `f` 切换：
  - `f=xml`（默认，自 1.0.0）
  - `f=json`（自 1.4.0）
  - `f=jsonp`（自 1.6.0，需额外指定 `callback` 参数，返回 JavaScript 回调包装）
  - XML 使用 UTF-8 编码，命名空间为 `http://subsonic.org/restapi`。OpenSubsonic 文档目前只展示 JSON 示例，但 XML 仍被要求完整支持（多数老客户端只解析 XML）。
- **内容协商**：规范没有正式的 HTTP 内容协商（`Accept` 头）机制，格式完全由 `f` 参数决定。二进制端点（`stream`、`download`、`getCoverArt`、`hls`）直接返回媒体数据；出错时返回 XML 文档，且 HTTP `Content-Type` 以 `text/xml` 开头（客户端据此判断错误）。
- **版本**：本参考以 1.16.1 为基线（OpenSubsonic 建议服务器至少支持 1.14.0，强烈建议 1.16.1）。

---

## 2. 通用请求参数

所有端点都接受以下公共参数（除个别标注外）：

| 参数 | 必填 | OpenSubsonic | 默认 | 说明 |
|---|---|---|---|---|
| `u` | 是\*\* | | | 用户名。 |
| `p` | 是\* | | | 密码，明文或带 `enc:` 前缀的十六进制编码。自 1.13.0 起仅建议用于测试。 |
| `t` | 是\* | | | （自 1.13.0）认证令牌，`t = md5(password + salt)`，见下文认证章节。 |
| `s` | 是\* | | | （自 1.13.0）随机字符串（salt），用于计算密码哈希，见下文。 |
| `apiKey` | 是\*\* | **是** | | [OS] API 密钥认证参数。若提供 `apiKey`，则 `u`、`p`、`t`、`s` 均**不得**提供。 |
| `v` | 是 | | | 客户端实现的协议版本（即客户端使用的 subsonic-rest-api.xsd 版本）。**必须提供**，最小 1.0.0。 |
| `c` | 是 | | | 唯一标识客户端应用的字符串（如 `Feishin`、`DSub`）。 |
| `f` | 否 | | `xml` | 返回格式：`xml`、`json`（1.4.0+）、`jsonp`（1.6.0+，需配合 `callback` 参数）。 |

> \* 认证参数二选一：要么给 `p`，要么同时给 `t` 和 `s`。
> \*\* 若使用 `apiKey`，则 `p`、`t`、`s`、`u` 都不可提供。

所有请求参数都应做 **URL 编码**。所有方法（除返回二进制数据的）返回符合 subsonic-rest-api.xsd 的 XML 文档。

### 2.1 认证规范（完整）

#### 密码认证（password auth，1.12.0 及更早）

密码可以明文发送，也可以十六进制编码后加 `enc:` 前缀：

```
http://your-server/rest/ping.view?u=joe&p=sesame&v=1.12.0&c=AwesomeClientName
http://your-server/rest/ping.view?u=joe&p=enc:736573616d65&v=1.12.0&c=AwesomeClientName
```

`736573616d65` 即 `sesame` 的十六进制。自 1.13.0 起官方文档明确说明 `p` **只应用于测试目的**。

#### 令牌认证（token auth，1.13.0 起推荐）

令牌是密码的**单向加盐哈希**，分两步：

1. 每次 REST 调用生成一个随机字符串作为 salt（**长度至少 6 个字符**），作为参数 `s` 发送；
2. 计算令牌：**`token = md5(password + salt)`**。`md5()` 输入字符串、输出 32 字节 ASCII 小写十六进制；`+` 表示字符串拼接；计算哈希时字符串按 **UTF-8** 处理。结果作为参数 `t` 发送。

官方示例（密码 `sesame`、salt `c19b2d`）：

```
token = md5("sesamec19b2d") = 26719a1196d2a940705a59634eb18eab
http://your-server/rest/ping.view?u=joe&t=26719a1196d2a940705a59634eb18eab&s=c19b2d&v=1.13.0&c=AwesomeClientName&f=json
```

验证时，服务端用自己保存的该用户密码重新计算 `md5(password + salt)` 并与 `t` 比较（小写、恒定时间比较更佳）。salt 每次请求不同，因此令牌不可重放。

#### API 密钥认证（OpenSubsonic 扩展 `apiKeyAuthentication`）

OpenSubsonic 引入 `apiKey` 参数：一个由服务器生成的、不透明的认证令牌（格式未规定，但必须短于 2048 字符以放进 URL）。提供 `apiKey` 时**必须**不带 `u`。服务器必须提供查看/吊销 API key 的机制；API key 不过期。规范**建议**支持 API key 的服务器不再支持 salt/token 认证（若移除 token 认证，必须对 token 请求返回错误 41；移除其他机制返回 42；同时传多种认证参数返回 43）。配套新增端点 `tokenInfo`（返回 API key 对应的用户名）。

#### POST 表单支持（OpenSubsonic 扩展 `formPost`）

参数可放在 `application/x-www-form-urlencoded` 的请求体中（键和值都要 URL 编码）。服务器通过 `getOpenSubsonicExtensions` 声明支持。官方示例：

```
curl -v -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
  'http://your-server/rest/ping.view' \
  --data 'c=AwesomeClientName&v=1.12.0&f=json&u=joe&p=sesame'
```

### 2.2 ⚠️ 关键：服务端如何存储密码以验证令牌

这是实现者最需要搞清楚的要点，结论如下（均已核实）：

1. **规范只定义了客户端侧公式**：`token = md5(password + salt)`（官方文档与 OpenSubsonic 文档原文一致，见上文引用）。官方文档（当前版、2018 年存档、2017 年存档均核对过）**没有**给出"服务端应如何存储密码"的显式语句。
2. **数学事实**：要验证 `md5(password + salt)`，服务端必须能拿到**明文密码**（或可逆还原出明文的存储形式）。仅保存不可逆的 `md5(password)` **无法**验证该令牌——无法从 `md5(password)` 推导出 `md5(password + salt)`。
3. **主流服务端的做法**：参考实现 Navidrome 将密码**加密**（可逆）存储，取用时解密得到明文再计算 `md5(明文 + salt)`（见其 `server/subsonic/middlewares.go` 的 `validateCredentials`：`t := fmt.Sprintf("%x", md5.Sum([]byte(user.Password+salt)))`，其中 `user.Password` 来自解密后的密码）。
4. **反例（为何不能只存哈希）**：Ampache 官方文档明确写道（引原文）：

   > "…it's stored encrypted (not hashed, since the server needs the plaintext back to compute the token)…"
   > （密码以**加密**形式存储，而非哈希，因为服务器需要还原出明文才能计算令牌。）

   Nextcloud Music 的情况更能说明问题（引 Substreamer 维护的 SERVERS.md 说明）：Nextcloud 把密码存为 SHA-256 哈希，导致 Subsonic 的 `md5(password + salt)` 令牌认证**数学上不可能**——服务器无法恢复或重算客户端发送的值，因此 Nextcloud Music 另建了 APIKEY 体系供 Subsonic 客户端使用。

5. **OpenSubsonic 的建议**：规范层面没有"存明文还是哈希"的条文，但它的安全方向是**弃用 token/salt 认证、改用 API key**（apiKeyAuthentication 扩展，见 2.1）。API key 是服务器自己生成的随机令牌，服务端可以按任意方式存储（甚至只存哈希），天然绕开了"需要明文"的问题。

**给 Bifrost 的落地建议**：密码以可逆加密（例如主密钥 AES-GCM）存储，验证令牌时解密后计算 `md5(password+salt)`；同时把 `apiKey` 认证作为可选加分项（若实现，token/salt 可继续保留，不强制移除）。绝不要只存 `md5(password)`。

---

## 3. 响应格式

### 3.1 顶层信封 `<subsonic-response>`

所有非二进制端点返回如下信封（XML/JSON 同构）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<subsonic-response xmlns="http://subsonic.org/restapi" status="ok" version="1.16.1"
                   type="Bifrost" serverVersion="0.1.0 (tag)" openSubsonic="true">
  ...业务数据...
</subsonic-response>
```

```json
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "Bifrost",
    "serverVersion": "0.1.0 (tag)",
    "openSubsonic": true
  }
}
```

信封字段：

| 字段 | 类型 | 必填 | OpenSubsonic | 说明 |
|---|---|---|---|---|
| `status` | string | 是 | | 命令结果：`ok` 或 `failed` |
| `version` | string | 是 | | 服务器支持的 Subsonic API 版本（**响应里应回服务器版本**，如 `1.16.1`） |
| `type` | string | 是 | **是** | 服务器实际名称（如 `Navidrome`、`gonic`、`Bifrost`），帮助客户端适配 |
| `serverVersion` | string | 是 | **是** | 服务器自身版本（如 `0.1.3 (beta)`），与 `version`（API 版本）不同 |
| `openSubsonic` | boolean | 是 | **是** | 服务器支持 OpenSubsonic API v1 时必须为 `true` |
| `error` | error | 否 | | `status=failed` 时的错误详情 |

### 3.2 错误处理

失败时 `status="failed"`，并携带 `<error>` 元素：

```xml
<subsonic-response xmlns="http://subsonic.org/restapi" status="failed" version="1.16.1">
  <error code="40" message="Wrong username or password"/>
</subsonic-response>
```

`error` 字段：`code`（int，必填）、`message`（string，可选）、`helpUrl`（string，可选，**OpenSubsonic 新增**，指向文档/配置页等补充信息）。

**错误码表**（OpenSubsonic 在官方 9 个码的基础上新增了 42/43/44，并给 41 补充了语义）：

| 码 | 含义 |
|---|---|
| 0 | 通用错误 |
| 10 | 缺少必填参数 |
| 20 | 不兼容的 Subsonic REST 协议版本——**客户端**需升级 |
| 30 | 不兼容的 Subsonic REST 协议版本——**服务器**需升级 |
| 40 | 用户名或密码错误 |
| 41 | 不支持令牌认证（原文"Token authentication not supported for LDAP users."；OpenSubsonic 澄清：它实际表示令牌认证因**任何**原因不支持。服务器在宣称版本 >1.13.0 却不支持 token 认证时必须返回 41） |
| 42 | [OS] 提供的认证机制不受支持 |
| 43 | [OS] 同时提供了多种互相冲突的认证参数 |
| 44 | [OS] API 密钥无效 |
| 50 | 用户无权执行该操作 |
| 60 | Subsonic 试用期已结束，需升级到 Subsonic Premium（第三方服务器一般直接返回 `valid=true` 的许可证，见 getLicense） |
| 70 | 请求的数据不存在 |

> 说明：任务背景中提到的"80–99"错误码区间**未在官方或 OpenSubsonic 规范中定义**（已核对 OpenSubsonic OpenAPI 的 `Error` schema，枚举只有上述 0–70 的 12 个值）。OpenSubsonic 的实际新增是 41–44 与 `helpUrl` 字段。

### 3.3 二进制端点

`stream`、`download`、`getCoverArt`、`hls`、`getAvatar` 等直接返回二进制数据；出错时返回 XML 文档且 HTTP `Content-Type` 以 `text/xml` 开头，客户端据此区分"数据"与"错误"。

---

## 4. 完整端点清单

以下按官方分类列出全部端点（标注 **since** 版本；`[OS]` 为 OpenSubsonic 新增）。除特别说明外，端点成功时都返回 `<subsonic-response>` 信封。方法路径一律为 `GET /rest/<name>.view`。

### 4.1 System（系统）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `ping` | 1.0.0 | 无 | 空 `subsonic-response`，用于连通性测试 |
| `getLicense` | 1.0.0 | 无 | `<license valid email licenseExpires trialExpires/>`。第三方服务器通常恒返回 `valid="true"` |
| `getOpenSubsonicExtensions` | [OS] | 无（**必须免认证可访问**） | `<openSubsonicExtensions><openSubsonicExtension name versions/></...>`，声明支持的 OS 扩展 |
| `tokenInfo` | [OS] | 无（用 `apiKey` 认证） | `<tokenInfo username/>`，返回 API key 对应的用户名；无效 key 返回错误 44 |

### 4.2 Browsing（浏览）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getMusicFolders` | 1.0.0 | 无 | `<musicFolders><musicFolder id name/></musicFolders>`，顶层音乐文件夹列表 |
| `getIndexes` | 1.0.0 | `musicFolderId`、`ifModifiedSince`（毫秒时间戳，未变化则返回空） | `<indexes lastModified ignoredArticles>`，内含 `<shortcut/>`、按字母分组的 `<index name><artist .../></index>` 以及根级 `<child .../>`（按文件结构组织） |
| `getMusicDirectory` | 1.0.0 | `id`（必填，来自 getIndexes/getMusicDirectory） | `<directory id name><child .../></directory>`，目录内容 |
| `getGenres` | 1.9.0 | 无 | `<genres><genre songCount albumCount value/></genres>` |
| `getArtists` | 1.8.0 | `musicFolderId` | `<artists ignoredArticles><index name><artist id name coverArt albumCount/></index></artists>`（按 **ID3 标签**组织） |
| `getArtist` | 1.8.0 | `id`（必填） | `<artist ...><album .../></artist>`（ArtistWithAlbumsID3） |
| `getAlbum` | 1.8.0 | `id`（必填） | `<album ...><song .../></album>`（AlbumID3WithSongs） |
| `getSong` | 1.8.0 | `id`（必填） | `<song .../>`（即 Child，见 4.13） |
| `getVideos` | 1.8.0 | 无 | `<videos><video .../></videos>`（Bifrost 可跳过/返回空） |
| `getVideoInfo` | 1.14.0 | `id` | 视频信息（可跳过） |
| `getArtistInfo` / `getArtistInfo2` | 1.11.0 | `id`、`count`（默认 20）、`includeNotPresent`（默认 false） | last.fm 艺术家资料（Bifrost 可返回空或 70） |
| `getAlbumInfo` / `getAlbumInfo2` | 1.14.0 | `id` | last.fm 专辑资料（同上） |
| `getSimilarSongs` / `getSimilarSongs2` | 1.11.0 | `id`、`count`（默认 50） | 相似歌曲列表 |
| `getTopSongs` | 1.13.0 | `artist`（必填）、`count`（默认 50） | `<topSongs><song/></topSongs>` |

### 4.3 Album/song lists（专辑/歌曲列表）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getAlbumList` | 1.2.0 | `type`（必填：`random/newest/highest/frequent/recent`；1.8.0 起 `alphabeticalByName/alphabeticalByArtist/starred`；1.10.1 起 `byYear/byGenre`）、`size`（默认 10，最大 500）、`offset`（默认 0）、`fromYear`/`toYear`（type=byYear 时必填）、`genre`（type=byGenre 时必填）、`musicFolderId`（1.11.0 起） | `<albumList><album .../></albumList>`（按文件结构） |
| `getAlbumList2` | 1.8.0 | 同上 | `<albumList2><album .../></albumList2>`（按 ID3 标签，AlbumID3 结构） |
| `getRandomSongs` | 1.2.0 | `size`（默认 10，最大 500）、`genre`、`fromYear`、`toYear`、`musicFolderId` | `<randomSongs><song .../></randomSongs>` |
| `getSongsByGenre` | 1.9.0 | `genre`（必填）、`count`（默认 10，最大 500）、`offset`（默认 0）、`musicFolderId`（1.12.0 起） | `<songsByGenre><song/></songsByGenre>` |
| `getNowPlaying` | 1.0.0 | 无 | `<nowPlaying><entry ... username minutesAgo playerId/>`（[OS] 支持 playbackReport 扩展时含 `state/positionMs/playbackRate`） |
| `getStarred` | 1.8.0 | `musicFolderId`（1.12.0 起） | `<starred><artist/><album/><song/></starred>`（按文件结构） |
| `getStarred2` | 1.8.0 | 同上 | 同上，但按 ID3 标签（ArtistID3/AlbumID3） |

### 4.4 Searching（搜索）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `search` | 1.0.0（**1.4.0 起废弃**） | `artist/album/title/any`、`count`（默认 20）、`offset`、`newerThan` | `<searchResult><match/></searchResult>`（按文件结构） |
| `search2` | 1.4.0 | `query`（必填）、`artistCount`（默认 20）/`artistOffset`、`albumCount`（默认 20）/`albumOffset`、`songCount`（默认 20）/`songOffset`、`musicFolderId`（1.12.0 起） | `<searchResult2><artist/><album/><song/></searchResult2>`（按文件结构） |
| `search3` | 1.8.0 | 同 search2 | `<searchResult3>` 同构，但按 ID3 标签（[OS] 澄清：**必须支持空 `query`** 并返回全部数据，供客户端离线同步） |

### 4.5 Playlists（播放列表）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getPlaylists` | 1.0.0 | `username`（1.8.0 起，需管理员） | `<playlists><playlist id name owner public created changed songCount duration/></playlists>` |
| `getPlaylist` | 1.0.0 | `id`（必填） | `<playlist ...><entry .../></playlist>`（PlaylistWithSongs） |
| `createPlaylist` | 1.2.0 | `playlistId`（更新时必填）、`name`（创建时必填）、`songId`（可多个，一次一个） | 1.14.0 前为空响应；**1.14.0 起返回 `<playlist>`** |
| `updatePlaylist` | 1.8.0 | `playlistId`（必填）、`name`、`comment`、`public`、`songIdToAdd`（可多个）、`songIndexToRemove`（可多个） | 空响应。仅所有者可更新 |
| `deletePlaylist` | 1.2.0 | `id`（必填） | 空响应 |

### 4.6 Media retrieval（媒体获取）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `stream` | 1.0.0 | `id`（必填）、`maxBitRate`（1.2.0，kbps，0=不限）、`format`（1.6.0，目标格式如 `mp3`；1.9.0 起 `raw` 表示不转码）、`timeOffset`（秒，默认仅视频；`transcodeOffset` 扩展使其对音乐也生效）、`size`（1.6.0，视频 WxH）、`estimateContentLength`（1.8.0，布尔）、`converted`（1.14.0，视频） | **二进制音频数据**（详见第 5 节） |
| `download` | 1.0.0 | `id`（必填） | **二进制原始数据**，`Content-Disposition: attachment`（详见第 5 节） |
| `hls` | 1.8.0 | `id`（必填）、`bitRate`（可重复，多个则生成自适应码率变体播放列表）、`audioTrack` | M3U8 播放列表，Content-Type `application/vnd.apple.mpegurl` |
| `getCaptions` | 1.14.0 | `id` | 视频字幕（Bifrost 可跳过） |
| `getCoverArt` | 1.0.0 | `id`（必填；[OS] 澄清：**只指 coverArt ID**，原版 Subsonic 中可指歌曲/专辑/艺术家）、`size`（缩放边长） | 图片二进制（JPEG/PNG） |
| `getLyrics` | 1.2.0 | `artist`、`title` | `<lyrics artist title><lyric/></lyrics>`，未找到返回空元素 |
| `getAvatar` | 1.8.0 | `username` | 头像图片二进制（可返回占位图） |
| `getLyricsBySongId` | [OS] | `id`（必填） | `<lyricsList><structuredLyrics ...>`（`songLyrics` 扩展，v1/v2） |

### 4.7 Media annotation（媒体标注）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `star` | 1.8.0 | `id`（可多个）、`albumId`（可多个）、`artistId`（可多个） | 空响应。ID3 客户端用 `albumId`/`artistId` 而非 `id` |
| `unstar` | 1.8.0 | 同 star | 空响应 |
| `setRating` | 1.6.0 | `id`（必填）、`rating`（必填，1–5，0 表示取消评分） | 空响应 |
| `scrobble` | 1.5.0 | `id`（必填，1.8.0 起可多个）、`time`（1.8.0，毫秒时间戳）、`submission`（默认 true；false 表示"正在播放"通知） | 空响应。作用：上报 last.fm、**更新播放次数与最后播放时间**（1.11.0 起）、出现在"正在播放"列表 |

### 4.8 Sharing（分享）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getShares` | 1.6.0 | 无 | `<shares><share ... url/></shares>` |
| `createShare` | 1.6.0 | `id`（必填，可多个）、`description`、`expires`（毫秒时间戳） | 返回 `<shares>`，内含新建的 `<share>` |
| `updateShare` | 1.6.0 | `id`（必填）、`description`、`expires`（0 表示移除过期） | 空响应 |
| `deleteShare` | 1.6.0 | `id`（必填） | 空响应 |

### 4.9 Podcast（播客）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getPodcasts` | 1.6.0 | `includeEpisodes`（默认 true）、`id`（1.9.0） | `<podcasts><channel ...><episode .../></channel></podcasts>` |
| `getNewestPodcasts` | 1.13.0 | `count`（默认 20） | `<newestPodcasts><episode/></newestPodcasts>` |
| `refreshPodcasts` | 1.9.0 | 无 | 空响应（需播客管理权限） |
| `createPodcastChannel` | 1.9.0 | `url`（必填） | 空响应 |
| `deletePodcastChannel` | 1.9.0 | `id`（必填） | 空响应 |
| `deletePodcastEpisode` | 1.9.0 | `id`（必填） | 空响应 |
| `downloadPodcastEpisode` | 1.9.0 | `id`（必填） | 空响应（请求服务端开始下载） |

### 4.10 Jukebox（点唱机）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `jukeboxControl` | 1.2.0 | `action`（必填：`get/status(1.7.0)/set(1.7.0)/start/stop/skip/add/clear/remove/shuffle/setGain`）、`index`、`offset`、`id`（可多个）、`gain`（0.0–1.0） | 非 `get` 返回 `<jukeboxStatus currentIndex playing gain position/>`；`get` 返回 `<jukeboxPlaylist>` |

### 4.11 Internet radio（网络电台）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getInternetRadioStations` | 1.9.0 | 无 | `<internetRadioStations><internetRadioStation id streamUrl name homepageUrl/></...>` |
| `createInternetRadioStation` | 1.16.0 | `streamUrl`（必填）、`name`（必填）、`homepageUrl` | 空响应（仅管理员） |
| `updateInternetRadioStation` | 1.16.0 | `id`、`streamUrl`、`name`、`homepageUrl` | 空响应（仅管理员） |
| `deleteInternetRadioStation` | 1.16.0 | `id`（必填） | 空响应（仅管理员） |

### 4.12 Chat（聊天）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getChatMessages` | 1.2.0 | `since`（毫秒时间戳） | `<chatMessages><chatMessage username time message/></chatMessages>` |
| `addChatMessage` | 1.2.0 | `message`（必填） | 空响应 |

### 4.13 User management（用户管理）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getUser` | 1.3.0 | `username`（必填；只能查自己，除非管理员） | `<user username email ...>`，含角色开关：`adminRole/settingsRole/downloadRole/uploadRole/playlistRole/coverArtRole/commentRole/podcastRole/streamRole/jukeboxRole/shareRole/scrobblingEnabled` 与 `folder`（可访问的音乐文件夹 ID） |
| `getUsers` | 1.8.0 | 无 | `<users><user .../></users>`，**仅管理员** |
| `createUser` | 1.1.0 | `username/password/email`（必填）、`ldapAuthenticated`（默认 false）、各 role 参数 | 空响应（仅管理员） |
| `updateUser` | 1.10.1 | `username`（必填）、`password/email`、role 参数 | 空响应（仅管理员） |
| `deleteUser` | 1.3.0 | `username`（必填） | 空响应（仅管理员） |
| `changePassword` | 1.1.0 | `username`、`password` | 空响应（仅自己或管理员） |

> 单用户服务端（Bifrost 很可能如此）：`getUser` 应返回当前用户（可忽略 `username` 参数，参考 Navidrome 行为）；`getUsers` 可只返回当前用户或返回 50 错误；创建/删除/改密等管理端点可返回 50（未授权）或 0（未实现）。

### 4.14 Bookmarks（书签）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getBookmarks` | 1.9.0 | 无 | `<bookmarks><bookmark position comment created changed><entry/></bookmark></bookmarks>` |
| `createBookmark` | 1.9.0 | `id`（必填）、`position`（必填，秒）、`comment` | 空响应 |
| `deleteBookmark` | 1.9.0 | `id`（必填） | 空响应 |
| `getPlayQueue` | 1.12.0 | 无 | `<playQueue current position username changed><entry/></playQueue>` |
| `savePlayQueue` | 1.12.0 | `id`（必填，可多个）、`current`（1.12.0 为当前歌曲 ID）、`position`（秒） | 空响应 |

### 4.15 Media library scanning（媒体库扫描）

| 端点 | since | 参数 | 响应 |
|---|---|---|---|
| `getScanStatus` | 1.15.0 | 无 | `<scanStatus scanning count/>`（`scanning` 布尔、`count` 已扫描数量；Navidrome 额外返回 `lastScan`、`folderCount`） |
| `startScan` | 1.15.0 | 无（Navidrome 额外接受 `fullScan` 布尔） | `<scanStatus>`，立即返回当前状态，扫描在后台进行 |

---

## 5. 流媒体细节（stream / download / getCoverArt）

### 5.1 `stream` 端点

- **返回**：媒体二进制数据（源文件或转码后）；HTTP `Content-Type` 为媒体 MIME（见下）。
- **转码参数**：
  - `maxBitRate`：服务端尽量把码率压到该值（kbps）；0 = 不限。
  - `format`：首选目标格式（如 `mp3`、`flv`），用于多转码方案存在时；`raw`（1.9.0+）表示**禁用转码**。
  - `timeOffset`：从第 N 秒开始播放；规范默认仅对视频生效，`transcodeOffset`（OpenSubsonic 扩展）使其对音乐生效。
- **MIME 类型**：来自歌曲元数据 `contentType`（如 `audio/mpeg`、`audio/flac`）；若转码，客户端应改用 `transcodedContentType`/`transcodedSuffix`。Navidrome 参考实现还会设 `X-Content-Type-Options: nosniff` 与 `X-Content-Duration` 头。
- **HTTP Range / 206 部分内容**：规范文本**未强制**要求，但这是客户端（尤其移动端与浏览器播放器）的普遍预期。参考实现 Navidrome 对可 seek 的源文件使用 `http.ServeContent`，完整支持 `Range`/`Accept-Ranges: bytes`/206 与 `Content-Length`；对不可 seek 的转码流则设 `Accept-Ranges: none`，且默认**不**发 `Content-Length`（仅当 `estimateContentLength=true` 时发估计值）。**建议 Bifrost 对源文件支持 Range，对转码流至少返回 `Accept-Ranges: none`**。
- **播放计数**：OpenSubsonic 澄清——服务端**不得**因 `stream` 调用而增加播放次数/标记已播放；应由客户端用 `scrobble`（`submission=true`）上报。

### 5.2 `download` 端点

- 与 `stream` 类似，但返回**原始媒体数据，不转码、不降采样**。
- **Content-Disposition**：规范未写明，但实际服务端都设为 `attachment`。Navidrome 参考实现：单曲 `attachment; filename="<文件名>"`；传专辑/艺术家/播放列表 ID 时打包为 **zip**（`Content-Type: application/zip`、文件名 `<name>.zip`）。Bifrost 至少应对歌曲 ID 返回附件下载。
- 注意：Navidrome 的 `download` 额外接受 `bitrate`/`format` 转码参数并受 `EnableDownloads` 配置控制（不可用时返回错误 50）。

### 5.3 `getCoverArt` 端点

- 返回封面图片（JPEG/PNG 均可），`size` 指定缩放边长。
- 若未找到封面：原版 Subsonic 会返回一个占位图（或按实现返回 70 错误/默认图）。多数客户端能容忍缺图。
- OpenSubsonic 澄清：`id` 仅指 coverArt ID（参见第 6 节 ID 约定）。

---

## 6. ID 约定

- **规范定义**：所有 ID 都是**服务器定义的不透明字符串**（XSD 中为 `string` 类型）。客户端必须原样回传，不得假定其含义或格式。
- **常见前缀约定**：以 Navidrome 为代表的服务器在 **coverArt/artwork ID** 中使用 `<前缀>-<实体ID>[_<内容哈希>]` 形式（已在 Navidrome `model/artwork_id.go` 中核实）：`mf-`（媒体文件/歌曲）、`ar-`（艺术家）、`al-`（专辑）、`pl-`（播放列表）、`dc-`（唱片 disc）、`ra-`（电台）。OpenSubsonic 文档示例中大量出现 `coverArt="ar-100000016"`、`"al-200000002"`、`"mf-<hash>_<hash>"` 等即由此而来。社区中常提到的 `tr-<id>`（track）前缀**未能在 Navidrome 源码中核实**，应视为个别实现的约定。**这些前缀都不是规范强制要求**，Bifrost 可以自由选择 ID 形式（内部数字、UUID、MD5 等）。
- **已知坑**：Navidrome 文档提醒——即使规范把 ID 定义为字符串，**有些客户端仍会把 ID 转成整数**，因此若 Bifrost 的 ID 是纯数字，需要确保它们能被解析为整数（例如不要用超出 int32 的纯数字串、或干脆用字母前缀）。
- **coverArt 与 ID 的关系**：`Child.coverArt`、`AlbumID3.coverArt` 等字段携带的 ID 可传给 `getCoverArt`。OpenSubsonic 下 `getCoverArt` 的 `id` 专指 coverArt ID（原版允许传歌曲/专辑/艺术家 ID，属于历史遗留）。
- **ID3 结构 vs 文件结构**：1.8.0 起有两条平行的浏览路径：ID3 标签路径（`getArtists/getArtist/getAlbum/getAlbumList2/search3/getStarred2`，ID 通常为 `ar-`/`al-` 前缀）与文件结构路径（`getIndexes/getMusicDirectory/getAlbumList/search2/getStarred`）。同一实体在两套路径下 ID 可能不同。现代客户端主要走 ID3 路径。

---

## 7. 版本与兼容性

### 7.1 官方版本里程碑（Subsonic 服务端版本 → REST API 版本）

| Subsonic 版本 | REST API 版本 | 主要新增 |
|---|---|---|
| 3.8 / 3.9 | 1.0.0 / 1.1.0 | 基础：ping、getLicense、浏览、stream/download、search、播放列表、changePassword/createUser |
| 4.0–4.2 | 1.2.0–1.4.0 | getAlbumList、getRandomSongs、jukebox、chat、search2、JSON 格式 |
| 4.4–4.6 | 1.5.0–1.7.0 | scrobble、分享/播客、setRating、JSONP、jukebox 扩展 |
| 4.7 | **1.8.0** | **ID3 组织**：getArtists/getArtist/getAlbum/getSong、getAlbumList2、getStarred(2)、**search3**、star/unstar、updatePlaylist、getVideos、getAvatar、hls |
| 4.8–4.9 | 1.9.0–1.10.2 | 书签、流派、网络电台、播客管理、updateUser |
| 5.1–5.2 | 1.11.0–1.12.0 | 艺术家/专辑信息（last.fm）、相似歌曲、getPlayQueue/savePlayQueue |
| 5.3 | **1.13.0** | **令牌认证（t+s）**、getTopSongs、getNewestPodcasts |
| 6.0–6.1 | 1.14.0–1.15.0 | getAlbumInfo、视频信息/字幕、createPlaylist 返回 playlist、**getScanStatus/startScan** |
| 6.1.2–6.1.4 | 1.16.0–**1.16.1** | 网络电台增删改 |

（修正一处常见误解：**search3 自 1.8.0 就有**，不是 1.16 才出现；1.16.x 只新增了网络电台管理端点。）

### 7.2 兼容性规则（官方原文要点）

> "一个 Subsonic 服务器与 REST 客户端向后兼容，**当且仅当**主版本号相同，且客户端次版本号 ≤ 服务器次版本号。例如服务器为 2.2，则支持客户端 2.0/2.1/2.2，不支持 1.x、2.3+ 或 3.x。版本号第三位不参与兼容性判断。"

因此客户端传 `v=1.16.1` 时，1.16.1 服务器应接受；`v` 低于服务器支持的最低版本时返回错误 20，高于时返回错误 30。

### 7.3 OpenSubsonic 与 `X-OpenSubsonic`

- **OpenSubsonic** 是社区（Navidrome、gonic、Ampache、Symfonium、Feishin 等参与者）维护的向后兼容扩展规范，基线即 Subsonic API 1.16.1。它通过三类方式扩展：**Clarification**（澄清）、**Extension**（非破坏性扩展，如新增响应字段/参数）、**Addition**（新端点）。OpenSubsonic 服务器应至少支持 1.14.0，强烈建议 1.16.1。
- **能力协商机制**：客户端靠 `v` 参数声明能力；服务器在响应信封中带 `openSubsonic: true`、`type`、`serverVersion`，并通过**免认证的 `getOpenSubsonicExtensions`** 端点枚举所支持的扩展及版本。客户端据 `type` 判断服务器实现，据扩展列表决定是否调用对应端点/传对应参数。
- **关于 `X-OpenSubsonic` 请求头**：在官方文档与 OpenSubsonic 规范（含 OpenAPI 定义）中**均未定义**名为 `X-OpenSubsonic` 的请求头；OpenSubsonic 的能力通告只通过响应字段与 `getOpenSubsonicExtensions` 完成（已核查 Navidrome、Feishin 等实现源码也未使用该头）。本任务背景中提到的"扩展头 X-OpenSubsonic"**未能从任何一手来源核实**，实现时不必支持。
- **服务器广告**：`type` 填服务器名（如 `Bifrost`），`serverVersion` 填自身版本（如 `0.1.0 (beta)`），`version` 填支持的 Subsonic API 版本（如 `1.16.1`）。

---

## 8. 客户端生态

> 本节为常见客户端的兼容要点（平台与认证方式已尽量核实；标注"未核实"的请以客户端文档为准）。

- **Feishin**（jeffvli/feishin，Sonixd 的后继者）：桌面端（Linux/Windows/macOS，Electron），同时支持 Subsonic/OpenSubsonic 与 Navidrome/Jellyfin API。现代客户端，走 ID3 路径（getArtists/getAlbumList2/search3），支持令牌认证；向 OpenSubsonic 服务器索取扩展（歌词、转码等）。
- **Sonixd**：Feishin 的前身，桌面端，同为现代客户端。
- **DSub**（daneren2005/DSub）：Android 老牌开源客户端。支持令牌认证与旧密码认证；兼容面广，常被用来测试老 API 行为。**已知怪癖**：DSub 会给每个 API 请求附带 HTTP `Authorization` 头（这不是规范内容），在 Nextcloud Music 等场景会引发冲突（Nextcloud 官方给出了 DSub 5.4.4 的规避方法；5.5.1 起可关闭该行为）。Bifrost 应容忍/忽略未预期的 `Authorization` 头。
- **Symfonium**：Android 付费客户端，OpenSubsonic 参与方，对服务器兼容性要求严格（有专门的 API 扩展指南与服务器参与度讨论页）；使用令牌认证，支持 API key 与 LDAP。
- **play:Sub**：Android 付费客户端。
- **Supersonic**（dweymouth/supersonic）：桌面端（Go/OpenGL，Linux/macOS/Windows），开源，支持 OpenSubsonic。
- **Sublime Music**：Linux 桌面端（Python/GTK）。
- **Jam**：iOS 客户端。
- **Soundwave**：Android（DSub 分支）。
- **iSub**：iOS 老牌客户端。
- **Substreamer**（ghenry22/substreamer）：iOS/Android（Flutter）。**默认使用令牌认证**（`t=md5(password+salt)`），登录页有 "Legacy auth" 开关（切到 `p=enc:HEX`）。对 Ampache/Nextcloud Music 这类无法做令牌认证的服务器，需要 API key + Legacy auth 组合（详见第 9 节）。
- **Ultrasonic**：Android 开源客户端。
- **OutPlayer**：iOS 客户端。
- 其他参与者：**Tempus**、**Amperfy**（iOS）、**tinysub**、**Airdrome** 等（见 OpenSubsonic 参与者列表）。服务器实现 LMS（epoupon/lms）在其文档中报告用 **DSub、Subsonic Player、Symfonium、Tempo、Tempus、Ultrasonic** 做过实测。

**给 Bifrost 的客户端兼容要点**：

- 同时支持令牌认证与 `p=enc:` 密码认证（有些老客户端只会用其中一种）；有 LDAP/外部认证需求的场景保留密码认证。
- 现代客户端期望 `getOpenSubsonicExtensions`、`search3` 空查询（离线同步）、`getAlbumList2`/`getArtist`/`getAlbum` 完整字段。
- 移动端播放器普遍依赖 `stream` 的 **Range 请求**；`download` 应带 `Content-Disposition: attachment`。
- 有些客户端（如 DSub 系）会调用 `getMusicDirectory` 浏览文件结构；Navidrome 的做法是用模拟目录树（`/Artist/Album/01 - Song.mp3`）支持它，Bifrost 可参考。

---

## 9. 认证与安全注意

### 9.1 令牌认证完整流程（可复现示例）

1. 客户端生成随机 salt（≥6 字符），例如 `c19b2d`；
2. 计算 `token = md5(password + salt)`，例如 `md5("sesamec19b2d") = 26719a1196d2a940705a59634eb18eab`；
3. 请求：`GET /rest/ping.view?u=joe&t=26719a1196d2a940705a59634eb18eab&s=c19b2d&v=1.16.1&c=MyClient&f=json`；
4. 服务端取出该用户密码（解密后），计算同样的 `md5(password + salt)` 并做（恒定时间）比较；失败返回 40，同时 `status="failed"`。

实现时注意：`md5` 输出**小写**十六进制；字符串按 **UTF-8** 拼接；大多数实现库（如 Java `MessageDigest`、Go `crypto/md5`、Python `hashlib`）默认即符合，但要小心十六进制大小写与字符编码问题。

### 9.2 为什么不要存明文 / 只存 md5(password)

- **明文**：数据库泄露即密码泄露；不推荐。
- **只存 `md5(password)`**：无法验证 `md5(password + salt)`（无法从前者推导后者），会直接破坏令牌认证（见 2.2 节 Nextcloud Music 的教训）。
- **推荐**：可逆加密存储（如 AES-GCM，密钥在配置中），验证时解密计算；或干脆采用 OpenSubsonic 的 **API key** 方案——API key 是服务器生成的随机令牌，服务端可以只存其哈希，不需要明文。

### 9.3 OpenSubsonic 的安全建议

- 支持 `apiKeyAuthentication` 扩展的服务器，**建议**不再支持 salt/token 认证；移除时对 token 请求返回 **41**，对不支持的机制返回 **42**，多参数冲突返回 **43**，无效 API key 返回 **44**（并可带 `helpUrl` 引导用户）。
- 服务器必须能对 "宣称版本 >1.13.0 但不支持令牌认证" 的情况返回 41。
- 全部认证都建议在 HTTPS 下进行（尤其 `p=enc:` 只是十六进制编码，不是加密）。

### 9.4 常见安全相关错误码用法

- 认证失败 → 40（**不要**用 HTTP 401，Subsonic 约定用 200 + `status=failed` + error 40）；
- 参数缺失 → 10；
- 权限不足 → 50（如普通用户调 getUsers、download 被禁用、非所有者更新播放列表）。

---

## 10. 参考来源列表

- 官方 Subsonic API 文档：<https://www.subsonic.org/pages/api.jsp>（另有 forum.subsonic.org 镜像 <https://www.forum.subsonic.org/pages/api.jsp>；旧版存档见 Wayback Machine 2017/2018 快照）
- OpenSubsonic 文档站：<https://opensubsonic.netlify.app/docs/api-reference/>、<https://opensubsonic.netlify.app/docs/opensubsonic-changes/>、<https://opensubsonic.netlify.app/docs/subsonic-versions/>
- OpenSubsonic 规范源码仓库：<https://github.com/opensubsonic/open-subsonic-api>（含 `content/en/docs/` 全部端点/响应 markdown 与 `openapi/` OpenAPI 定义）
- Navidrome（参考实现）源码：`server/subsonic/middlewares.go`（认证）、`server/subsonic/stream.go`、`core/stream/media_streamer.go`、`server/subsonic/opensubsonic.go`：<https://github.com/navidrome/navidrome>
- Navidrome 开发者文档（Subsonic API 兼容性）：<https://www.navidrome.org/docs/developers/subsonic-api/>
- Ampache Subsonic API 文档（含密码存储说明）：<https://ampache.org/docs/configuration/subsonic/>
- Substreamer SERVERS.md（认证/转码兼容性细节）：<https://github.com/ghenry22/substreamer/blob/master/SERVERS.md>
- Nextcloud Music OpenSubsonic 支持说明：<https://github.com/nc-music/music/wiki/OpenSubsonic-API>
- gonic 兼容性清单：<https://github.com/sentriz/gonic/wiki/subsonic-api-compatibility>
- LMS（epoupon/lms）Subsonic/OpenSubsonic 实现说明：<https://github.com/epoupon/lms/blob/master/SUBSONIC.md>
- Nextcloud Music × DSub 5.4.4 兼容性说明（Authorization 头怪癖）：<https://github.com/nc-music/oc-music/wiki/Subsonic,-Workaround-for-using-DSub-5.4.4>
- OpenSubsonic 讨论（认证、扩展提案）：<https://github.com/opensubsonic/open-subsonic-api/discussions>

---

## 附录：重点端点的 XML 结构速查（v1 服务端必做）

```xml
<!-- ping: 成功 -->
<subsonic-response xmlns="http://subsonic.org/restapi" status="ok" version="1.16.1"/>

<!-- getLicense -->
<subsonic-response status="ok" version="1.16.1">
  <license valid="true" email="user@example.com" licenseExpires="2030-01-01T00:00:00Z" trialExpires="2030-01-01T00:00:00Z"/>
</subsonic-response>

<!-- getMusicFolders -->
<subsonic-response status="ok" version="1.16.1">
  <musicFolders>
    <musicFolder id="1" name="Music"/>
  </musicFolders>
</subsonic-response>

<!-- getIndexes -->
<subsonic-response status="ok" version="1.16.1">
  <indexes lastModified="1699999999999" ignoredArticles="The El La Los Las Le Les">
    <shortcut id="10" name="Podcasts"/>
    <index name="A">
      <artist id="ar-1" name="ABBA"/>
    </index>
    <child id="tr-1" parent="al-1" title="Dancing Queen" isDir="false" album="Arrival" artist="ABBA"
           track="7" year="1978" genre="Pop" coverArt="24" size="8421341"
           contentType="audio/mpeg" suffix="mp3" duration="146" bitRate="128"
           path="ABBA/Arrival/Dancing Queen.mp3"/>
  </indexes>
</subsonic-response>

<!-- getArtists / getArtist / getAlbum / getSong / getAlbumList(2) / getRandomSongs / search2/3 / getStarred
     均返回对应实体元素的属性集合，核心属性见下 -->

<!-- Child（歌曲）常用属性 -->
<!-- id parent isDir title album artist track year genre coverArt size contentType suffix
     transcodedContentType transcodedSuffix duration bitRate bitDepth samplingRate channelCount
     path isVideo userRating averageRating playCount discNumber created starred albumId artistId
     type[music/podcast/audiobook/video] [OS] mediaType played bpm comment sortName musicBrainzId ... -->

<!-- getPlaylists -->
<subsonic-response status="ok" version="1.16.1">
  <playlists>
    <playlist id="pl-1" name="My List" owner="admin" public="true"
              created="2024-01-01T00:00:00Z" changed="2024-01-01T00:00:00Z" songCount="20" duration="3600"/>
  </playlists>
</subsonic-response>

<!-- getPlaylist -->
<subsonic-response status="ok" version="1.16.1">
  <playlist id="pl-1" name="My List" owner="admin" public="true" songCount="1" duration="146">
    <entry id="tr-1" parent="al-1" title="Dancing Queen" .../>
  </playlist>
</subsonic-response>

<!-- getScanStatus -->
<subsonic-response status="ok" version="1.16.1">
  <scanStatus scanning="false" count="1234"/>
</subsonic-response>

<!-- 空响应类（star/unstar/setRating/scrobble/updatePlaylist/deletePlaylist/addChatMessage 等） -->
<subsonic-response status="ok" version="1.16.1"/>
```
