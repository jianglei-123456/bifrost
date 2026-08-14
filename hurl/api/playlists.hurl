# 管理 REST：歌单

POST {{base_url}}/api/playlists
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "hurl歌单",
  "comment": "来自 hurl"
}

HTTP 200
[Captures]
playlist_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.data.name" == "hurl歌单"

GET {{base_url}}/api/playlists/{{playlist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.playlist.comment" == "来自 hurl"

# 取一个曲目 id
GET {{base_url}}/api/tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
track_id: jsonpath "$.data.items[0].id"

POST {{base_url}}/api/playlists/{{playlist_id}}/entries
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "trackId": {{track_id}}
}

HTTP 200
[Asserts]
jsonpath "$.data.position" == 1

GET {{base_url}}/api/playlists/{{playlist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
entry_id: jsonpath "$.data.entries[0].entryId"
[Asserts]
jsonpath "$.data.entries" count == 1

DELETE {{base_url}}/api/playlists/{{playlist_id}}/entries/{{entry_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

DELETE {{base_url}}/api/playlists/{{playlist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
