# 05 Subsonic 服务（Syrinx，/rest/**）

> 目标：`bifrost-adapter/syrinx/subsonic-api` 模块，实现 Subsonic 1.16.1 + OpenSubsonic 合规服务端（Q3-A 口径：仅 ✅ 端点）。依据：《音乐管理技术设计》§6–§8；《音乐管理功能说明》§8；Subsonic_API_参考（速查：`C:\Users\Administrator\AppData\Local\Temp\subsonic-contract-digest.md`）。
> 完成标准：hurl 契约测试全绿（XML+JSON 双格式、认证、错误码、Range/206）；getOpenSubsonicExtensions 通告正确；启动后 `curl /rest/ping.view` 可用。

## T5.1 模块结构

- 移除 `bifrost-adapter/sonic-client`（Q2-A）；父 `bifrost-adapter/pom.xml` 更新模块列表；
- 新建 `bifrost-adapter/syrinx`（聚合）→ `syrinx/subsonic-api`，包 `com.bifrost.adapter.syrinx.subsonic`；
- 依赖：`bifrost-core`、`spring-boot-starter-web`、`spring-boot-starter-security`（认证过滤器）、`jackson-dataformat-xml`；
- bootstrap 扫描注册（`scanBasePackages="com.bifrost"` 已具备）；
- `bifrost.subsonic.enabled=false` 时不装配 Controller（可选）。

## T5.2 DTO 与双格式

- DTO 全集（XML 属性一一对应，`@JacksonXmlRootElement`/`@JacksonXmlProperty`，命名空间 `http://subsonic.org/restapi`）：
  - 信封 `subsonic-response`（status/version/type=Bifrost/serverVersion/openSubsonic=true/error）
  - ArtistID3（id/name/albumCount；coverArt **省略**，Q13）、AlbumID3（id/name/artist/artistId/songCount/duration/playCount/year/genre/created/starred/coverArt=al-<id>）、Child（id/parent/isDir/title/album/artist/track/discNumber/year/genre/coverArt/size/contentType/suffix/duration/bitRate/path/created/starred/userRating/playCount/artistId/albumId 等，按需输出）
  - Indexes/Directory/Artists/AlbumList/AlbumList2/SearchResult2/SearchResult3/Playlist/NowPlaying/ScanStatus/User/MusicFolders/License/OpenSubsonicExtensions/Starred/Starred2/RandomSongs
- 双格式：同一 DTO 由 `f=json` 切换（Jackson XML / JSON）；可选属性未设置 → 省略；
- `created`/`starred` 输出 ISO-8601（starred = starredAt，Q14）。

## T5.3 ID 编解码（id/ 包）

- 前缀编解码：`ar-`/`al-`/`tr-`/`pl-` + 数字主键；库根为**纯数字 ID**（Q15）；
- 容错：无法解析的 ID → error 70（数据不存在）。

## T5.4 认证接入

- 复用 03 阶段认证过滤器链（/rest/** 专用）；getOpenSubsonicExtensions 免认证（03/05 联调确认）。

## T5.5 端点实现（34 个，Q3-A 口径）

System：
- `ping`（空信封）、`getLicense`（恒 valid=true）、`getOpenSubsonicExtensions`（免认证；声明扩展按实现取舍，v1 建议空列表或 formPost 视实现）

Browsing：
- `getMusicFolders`：启用库根 → musicFolder（id=库根数字ID）；
- `getIndexes`：字母分组索引 + "未知艺术家"入 `#` 组（Q16）；`ignoredArticles=""`；支持 `musicFolderId`/`ifModifiedSince`（未变化返回空 indexes）；`lastModified`=最近扫描时间；根级不输出 child；
- `getMusicDirectory`：模拟目录树（Q15）：根=库根（musicFolderId）→ 艺术家目录（id=ar-，isDir=true）→ 专辑目录（id=al-，isDir=true）→ 曲目 Child（id=tr-，isDir=false）；`parent` 属性按层级；目录 id 可继续下发；
- `getArtists`（ID3 路径，同 getIndexes 分组但 ArtistID3 结构，省略 coverArt）；
- `getArtist`：ArtistID3 + 内嵌 AlbumID3（按年份 → 标题）；
- `getAlbum`：AlbumID3 + 内嵌 song Child（discNo → trackNo → 文件名）；
- `getSong`：Child。

Lists：
- `getAlbumList`/`getAlbumList2`：**10 种 type 全实现**（Q17）：random/newest/highest/frequent/recent/alphabeticalByName/alphabeticalByArtist/starred/byYear/byGenre；`size` 默认 10 最大 500；`offset`；`byYear` 缺 fromYear/toYear、`byGenre` 缺 genre → error 10；musicFolderId 过滤；getAlbumList2 用 AlbumID3 结构；
- `getRandomSongs`：size/genre/year 过滤；
- `getNowPlaying`：nowPlaying 列表（entry=Child+username/minutesAgo/playerId，playerId=客户端 `c` 参数，Q20）；
- `getStarred`/`getStarred2`：三态收藏（artist/album/song 分组；getStarred2 用 ID3 结构）。

Searching：
- `search2`（文件结构，query 必填）/`search3`（ID3 结构，**空查询返回全部**，OS 澄清）；counts/offsets、musicFolderId 过滤。

Playlists：
- `getPlaylists`/`getPlaylist`（entry 为 Child；songCount/duration）；
- `createPlaylist`（playlistId=更新/name=创建/songId；**返回 `<playlist>`**，Q20）；
- `updatePlaylist`（name/comment/public/songIdToAdd/songIndexToRemove）；
- `deletePlaylist`。

Media：
- `stream`：Range/206 支持（`Accept-Ranges: bytes`、`Content-Range`、整文件 200+`Content-Length`）；MIME 按 format（mp3→audio/mpeg、flac→audio/flac、m4a→audio/mp4、wav→audio/wav）；头 `X-Content-Type-Options: nosniff`、`X-Content-Duration`；maxBitRate/format/timeOffset **接收但忽略**（不转码）；**不增加 playCount**（OS 澄清）；stream 开始记录 nowPlaying（Q20）；
- `download`：单曲 attachment；专辑/艺术家/歌单 ID → **error 70**（Q20，zip 为增强）；
- `getCoverArt`：仅 `al-<id>`（Q13），size → 缩略图档位；未知 → 70。

Annotation：
- `star`/`unstar`（id/albumId/artistId 均可多个，三态）、`setRating`（1–5，0=取消）、`scrobble`（submission=true 落 playCount/lastPlayed；false 仅 nowPlaying；time 支持）。

Scanning：
- `getScanStatus`（scanning/count）、`startScan`（后台触发，立即返回状态）。

User mgmt：
- `getUser`（返回当前用户，忽略 username；adminRole 等全 true，省略 email 属性，Q18）、`getUsers`（**返回当前用户**，Q19）。

## T5.6 未实现端点（error 0 / 50）

- error 0（Not implemented）：getGenres、getArtistInfo(2)、getAlbumInfo(2)、getSimilarSongs(2)、getTopSongs、search(废弃)、getLyrics、getLyricsBySongId、hls、getAvatar、getPlayQueue、savePlayQueue、bookmark 系列、sharing/podcast/jukebox/radio/chat、tokenInfo 等（对齐能力矩阵 ❌/⏳，Q3-A）；
- error 50：createUser/updateUser/deleteUser/changePassword（单用户下无权限语义；如实现时已含 getUsers 当前用户，则这些仍按矩阵返回 50）。

## T5.7 错误码映射

- 协议错误码全表：0/10/20/30/40/41/42/43/44/50/60/70（无 80–99）；
- 20 = 客户端版本过低需升级；30 = 客户端版本过高（服务器升级）；40 = 认证失败；70 = 数据不存在（含缺失文件、非法 ID、download 非歌曲 ID）；
- 二进制端点（stream/download/getCoverArt）错误：XML 文档 + Content-Type 以 `text/xml` 开头；
- 认证失败 200 + status=failed + 40（非 401，03 阶段实现）。

## T5.8 事件订阅

- 订阅 `ScanCompletedEvent`：清除封面缩略图缓存、nowPlaying 失效、扫描时间戳更新。

## 验收清单

- [ ] `hurl/rest/*.hurl` 全绿（双格式/认证/错误码/Range/206）
- [ ] 34 个 ✅ 端点实现；⏳/❌ 端点按矩阵返回
- [ ] 模拟目录树三级导航正确；getIndexes 字母分组含"未知艺术家"# 组
- [ ] stream Range 请求返回 206 + Content-Range；stream 不增 playCount；scrobble(true) 后 getSong 可见 playCount 增长
- [ ] createPlaylist 返回 `<playlist>`；download 专辑 ID → 70
