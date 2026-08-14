# Subsonic：媒体（stream / download / getCoverArt）

# 捕获 mp3 曲目与 叶惠美 专辑（有目录封面）
GET {{base_url}}/rest/getAlbumList2.view?type=alphabeticalByName&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
media_album_id: jsonpath "$['subsonic-response'].albumList2.album[?@.name=='叶惠美'].id"

GET {{base_url}}/rest/getAlbum.view?id={{media_album_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
media_track_id: jsonpath "$['subsonic-response'].album.song[0].id"

# stream：整文件 200 + MIME + Accept-Ranges
GET {{base_url}}/rest/stream.view?id={{media_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Type" == "audio/mpeg"
header "Accept-Ranges" == "bytes"
header "X-Content-Duration" exists
header "X-Content-Type-Options" == "nosniff"

# stream：Range → 206 + Content-Range
GET {{base_url}}/rest/stream.view?id={{media_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl
Range: bytes=0-99
HTTP 206
[Asserts]
header "Content-Range" matches "bytes 0-99/.*"
header "Content-Length" == "100"

# stream 不增加 playCount（OS 澄清）
GET {{base_url}}/rest/getSong.view?id={{media_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].song.playCount" == 0

# download：单曲 attachment
GET {{base_url}}/rest/download.view?id={{media_track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Disposition" contains "attachment"

# getCoverArt：目录封面（cover.jpg）
GET {{base_url}}/rest/getCoverArt.view?id={{media_album_id}}&u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Type" contains "image/jpeg"

# 缺失封面 → 70（二进制端点错误 text/xml）
GET {{base_url}}/rest/getCoverArt.view?id=al-999999&u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Type" contains "text/xml"
body contains "code=\"70\""

# stream 记录 nowPlaying（Q20）
GET {{base_url}}/rest/getNowPlaying.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].nowPlaying.entry[0].id" == {{media_track_id}}
jsonpath "$['subsonic-response'].nowPlaying.entry[0].username" == "admin"
jsonpath "$['subsonic-response'].nowPlaying.entry[0].playerId" == "hurl"
