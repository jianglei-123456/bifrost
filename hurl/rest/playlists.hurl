# Subsonic：歌单（静态歌单生命周期）

# 捕获曲目 id（叶惠美 专辑）
GET {{base_url}}/rest/getAlbumList2.view?type=alphabeticalByName&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
playlist_album_id: jsonpath "$['subsonic-response'].albumList2.album[?@.name=='叶惠美'].id"

GET {{base_url}}/rest/getAlbum.view?id={{playlist_album_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
track_id: jsonpath "$['subsonic-response'].album.song[0].id"

# createPlaylist：返回 playlist（Q20）
GET {{base_url}}/rest/createPlaylist.view?name=hurl-playlist&songId={{track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
playlist_id: jsonpath "$['subsonic-response'].playlist.id"
[Asserts]
jsonpath "$['subsonic-response'].playlist.name" == "hurl-playlist"
jsonpath "$['subsonic-response'].playlist.entry" count == 1

# getPlaylists
GET {{base_url}}/rest/getPlaylists.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].playlists.playlist[0].id" == {{playlist_id}}
jsonpath "$['subsonic-response'].playlists.playlist[0].owner" == "admin"

# getPlaylist（含条目）
GET {{base_url}}/rest/getPlaylist.view?id={{playlist_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].playlist.songCount" == 1
jsonpath "$['subsonic-response'].playlist.entry[0].id" == {{track_id}}

# updatePlaylist：改名
GET {{base_url}}/rest/updatePlaylist.view?playlistId={{playlist_id}}&name=hurl-renamed&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

GET {{base_url}}/rest/getPlaylist.view?id={{playlist_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].playlist.name" == "hurl-renamed"

# deletePlaylist
GET {{base_url}}/rest/deletePlaylist.view?id={{playlist_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

GET {{base_url}}/rest/getPlaylists.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].playlists.playlist" count == 0
