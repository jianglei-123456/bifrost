# Subsonic：标注（star / setRating / scrobble）

# 捕获 flac 曲目与其专辑/艺术家（Let It Be，与 media 测试的 mp3 曲目隔离）
GET {{base_url}}/rest/getAlbumList2.view?type=alphabeticalByName&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
ann_album_id: jsonpath "$['subsonic-response'].albumList2.album[0].id"

GET {{base_url}}/rest/getAlbum.view?id={{ann_album_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
ann_track_id: jsonpath "$['subsonic-response'].album.song[0].id"

GET {{base_url}}/rest/getSong.view?id={{ann_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
ann_artist_id: jsonpath "$['subsonic-response'].song.artistId"

# star：三态（曲目/专辑/艺术家）
GET {{base_url}}/rest/star.view?id={{ann_track_id}}&albumId={{ann_album_id}}&artistId={{ann_artist_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# getStarred2：三态可见（收藏时间 dateTime，Q14）
GET {{base_url}}/rest/getStarred2.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].starred2.song" count == 1
jsonpath "$['subsonic-response'].starred2.album" count == 1
jsonpath "$['subsonic-response'].starred2.artist" count == 1
jsonpath "$['subsonic-response'].starred2.album[0].starred" matches "^20[0-9]{2}-.*"

# setRating
GET {{base_url}}/rest/setRating.view?id={{ann_track_id}}&rating=5&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# scrobble submission=true → playCount 落库
GET {{base_url}}/rest/scrobble.view?id={{ann_track_id}}&submission=true&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

GET {{base_url}}/rest/getSong.view?id={{ann_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].song.playCount" == 1
jsonpath "$['subsonic-response'].song.userRating" == 5

# unstar
GET {{base_url}}/rest/unstar.view?id={{ann_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

GET {{base_url}}/rest/getStarred2.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].starred2.song" count == 0
