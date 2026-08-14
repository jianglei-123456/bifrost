# 管理 REST：媒体浏览与搜索

GET {{base_url}}/api/artists
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.total" == 2

GET {{base_url}}/api/artists?indexLetter=Z
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
artist_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.items[0].name" == "周杰伦"

GET {{base_url}}/api/artists/{{artist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.artist.name" == "周杰伦"
jsonpath "$.data.albums[0].title" == "叶惠美"

GET {{base_url}}/api/albums
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
album_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" == 3

GET {{base_url}}/api/albums/{{album_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.album.id" == {{album_id}}
jsonpath "$.data.tracks" count > 0

GET {{base_url}}/api/tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" == 3

GET {{base_url}}/api/search?q=%E5%91%A8%E6%9D%B0%E4%BC%A6
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.artists[0].name" == "周杰伦"
