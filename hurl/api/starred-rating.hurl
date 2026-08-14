# 管理 REST：收藏与评分

GET {{base_url}}/api/albums
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
album_id: jsonpath "$.data.items[0].id"

GET {{base_url}}/api/tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
track_id: jsonpath "$.data.items[0].id"

POST {{base_url}}/api/starred
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "type": "album",
  "id": {{album_id}}
}

HTTP 200
[Asserts]
jsonpath "$.code" == 0

GET {{base_url}}/api/albums/{{album_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.album.starredAt" != null

PUT {{base_url}}/api/rating
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "type": "track",
  "id": {{track_id}},
  "rating": 4
}

HTTP 200
[Asserts]
jsonpath "$.code" == 0

GET {{base_url}}/api/tracks/{{track_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.rating" == 4

DELETE {{base_url}}/api/starred
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "type": "album",
  "id": {{album_id}}
}

HTTP 200
[Asserts]
jsonpath "$.code" == 0
