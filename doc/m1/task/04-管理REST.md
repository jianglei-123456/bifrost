# 04 管理 REST（/api/**）

> 目标：bifrost-api 全量端点 + 统一信封 + 认证接入（03）。依据：《通用功能说明》§4.1、§9；《音乐管理功能说明》§9。
> 完成标准：hurl 契约测试全量端点通过；错误码 1000–1004/1100/1200 正确；分页/排序/过滤符合约定。

## T4.1 系统

- `GET /api/ping` → `pong`（保留）。

## T4.2 库根

- `GET /api/library-roots`：列表（含扫描状态/上次统计）；
- `POST /api/library-roots`：新增（name/path/enabled）；
- `GET /api/library-roots/{id}`：详情；
- `PUT /api/library-roots/{id}`：编辑（路径变更需说明行为：仅更新，下一轮扫描生效）；
- `DELETE /api/library-roots/{id}`：删除 → 级联隐藏其曲目（不物理删除记录，标记 isAvailable=false；Q 决策见《音乐管理技术设计》§1.7）；
- `POST /api/library-roots/{id}/scan`：单根扫描（扫描中 → 1100）。

## T4.3 扫描

- `POST /api/scan`：全量扫描（全部启用库根，串行）；
- `GET /api/scan/status`：当前状态（IDLE/SCANNING、上次扫描时间与统计）。

## T4.4 浏览

- `GET /api/artists?page&size&q&indexLetter`：艺术家列表（支持索引分组过滤）；
- `GET /api/artists/{id}`：详情（含专辑列表、收藏/评分/播放统计）；
- `GET /api/albums?page&size&q&artistId&sort`：专辑列表（sort：year/title/playCount 等）；
- `GET /api/albums/{id}`：详情（含曲目列表）；
- `GET /api/tracks?page&size&q&albumId&artistId`：曲目列表；
- `GET /api/tracks/{id}`：详情；
- 缺失文件（isAvailable=false）：默认隐藏，`includeMissing=true` 可查；
- "未知艺术家"作为虚拟项出现在艺术家列表中（Q9）。

## T4.5 搜索

- `GET /api/search?q=&page&size`：全局搜索（艺术家/专辑/曲目）。

## T4.6 歌单

- `GET /api/playlists`、`POST /api/playlists`（name/comment）；
- `GET /api/playlists/{id}`（含条目）、`PUT /api/playlists/{id}`（改名/comment）、`DELETE /api/playlists/{id}`；
- `POST /api/playlists/{id}/entries`（songId、position 可空=追加）；
- `DELETE /api/playlists/{id}/entries/{entryId}`；
- 条目排序：position。

## T4.7 标注

- `POST /api/starred`（type=track/album/artist + id）与 `DELETE /api/starred`：三态收藏（Q14 starredAt）；
- `PUT /api/rating`（type + id + rating 1–5，0=取消）。

## T4.8 账号

- `GET /api/user`、`PUT /api/user/password`（03 阶段 T3.6）。

## 统一约定（贯穿）

- 信封 `{code, message, data}`；成功 code=0；
- 分页 `page`（0 起）/`size`（默认 20，最大 200）→ `{total, items}`；
- 时间 ISO-8601 UTC；排序/过滤参数按资源定义；
- 认证：HTTP Basic 或 t/s 令牌（03 阶段）。

## 验收清单

- [ ] hurl：`hurl/api/*.hurl` 全量端点通过（成功/400/401/404/403/1100 场景）
- [ ] 库根增删改 + 单根扫描 + 全量扫描 + 状态查询链路正确
- [ ] 浏览/搜索/歌单/收藏/评分/账号端点与契约一致
