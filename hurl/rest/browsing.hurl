# Subsonic：浏览（音乐文件夹 / 索引 / 模拟目录树 / ID3）

GET {{base_url}}/rest/getMusicFolders.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
root_id: jsonpath "$['subsonic-response'].musicFolders.musicFolder[0].id"
[Asserts]
jsonpath "$['subsonic-response'].musicFolders.musicFolder[0].name" contains "测试库"
# 只暴露 MUSIC 目录：图书目录（opds/setup 已建"测试图书库"）不得出现在音乐文件夹里
jsonpath "$['subsonic-response'].musicFolders.musicFolder" count == 1

# getIndexes：字母分组（周杰伦→Z；未知艺术家→#）
GET {{base_url}}/rest/getIndexes.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].indexes.index[?@.name=='Z'].artist[0].name" == "周杰伦"
jsonpath "$['subsonic-response'].indexes.index[?@.name=='#'].artist[0].name" == "未知艺术家"

# getMusicDirectory 根 → 艺术家目录
GET {{base_url}}/rest/getMusicDirectory.view?id={{root_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
jay_dir_id: jsonpath "$['subsonic-response'].directory.child[?@.title=='周杰伦'].id"
unknown_dir_id: jsonpath "$['subsonic-response'].directory.child[?@.title=='未知艺术家'].id"
[Asserts]
jsonpath "$['subsonic-response'].directory.child[?@.title=='周杰伦'].isDir" == true

# 艺术家目录 → 专辑目录
GET {{base_url}}/rest/getMusicDirectory.view?id={{jay_dir_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
album_dir_id: jsonpath "$['subsonic-response'].directory.child[?@.title=='叶惠美'].id"

# 专辑目录 → 曲目
GET {{base_url}}/rest/getMusicDirectory.view?id={{album_dir_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
track_id: jsonpath "$['subsonic-response'].directory.child[0].id"
[Asserts]
jsonpath "$['subsonic-response'].directory.child[0].isDir" == false
jsonpath "$['subsonic-response'].directory.child[0].title" == "以父之名"

# 未知艺术家目录可导航（→ 未知专辑）
GET {{base_url}}/rest/getMusicDirectory.view?id={{unknown_dir_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].directory.child[0].isDir" == true

# getArtists / getArtist / getAlbum / getSong
GET {{base_url}}/rest/getArtists.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Captures]
artist_id: jsonpath "$['subsonic-response'].artists.index[?@.name=='Z'].artist[0].id"
[Asserts]
jsonpath "$['subsonic-response'].artists.index[?@.name=='Z'].artist[0].name" == "周杰伦"

GET {{base_url}}/rest/getArtist.view?id={{artist_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].artist.name" == "周杰伦"
jsonpath "$['subsonic-response'].artist.album[0].name" == "叶惠美"

GET {{base_url}}/rest/getAlbum.view?id={{album_dir_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].album.name" == "叶惠美"
jsonpath "$['subsonic-response'].album.song" count == 1
jsonpath "$['subsonic-response'].album.song[0].track" == 1

GET {{base_url}}/rest/getSong.view?id={{track_id}}&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].song.title" == "以父之名"
jsonpath "$['subsonic-response'].song.artist" == "周杰伦"
jsonpath "$['subsonic-response'].song.albumId" == {{album_dir_id}}
