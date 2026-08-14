# Subsonic：专辑/歌曲列表

# getAlbumList2：字母排序（三种专辑）
GET {{base_url}}/rest/getAlbumList2.view?type=alphabeticalByName&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].albumList2.album" count == 3
jsonpath "$['subsonic-response'].albumList2.album[0].name" == "Let It Be"

# getAlbumList：文件结构（元素名 albumList）
GET {{base_url}}/rest/getAlbumList.view?type=newest&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].albumList.album" count == 3

# getAlbumList2：收藏排序（与 annotation 文件共享状态，仅验证端点可用）
GET {{base_url}}/rest/getAlbumList2.view?type=starred&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# getRandomSongs：size 过滤
GET {{base_url}}/rest/getRandomSongs.view?size=2&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].randomSongs.song" count == 2

# getAlbumList byGenre 缺 genre → 错误 10
GET {{base_url}}/rest/getAlbumList.view?type=byGenre&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 10

# getNowPlaying：初始为空
GET {{base_url}}/rest/getNowPlaying.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# getStarred / getStarred2：初始为空
GET {{base_url}}/rest/getStarred2.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].starred2.song" count == 0
