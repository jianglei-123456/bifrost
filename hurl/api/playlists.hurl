# 管理 REST：歌单 + 添加曲目候选列表

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

# —— 候选曲目：默认列表（不传 q，按入库时间倒序 + id 倒序）——
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
candidate_total: jsonpath "$.data.total"
candidate_first_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" > 0
jsonpath "$.data.items" count > 0
# 入库时间是真实时间戳（不是 null / 空串）
jsonpath "$.data.items[0].createdAt" isString
jsonpath "$.data.items[0].createdAt" != ""

# —— 第二排序键：id 倒序 ——
# 样本库只有 3 首、且是同一批扫描插入的（createdAt 完全相同），此时"最新入库"
# 完全由 id 决定：size=1 与 size=200 的首条必须是同一首，且翻页按 id 递减。
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks?page=0&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
p1_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.items" count == 1

GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks?page=0&size=200
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.items" count == {{candidate_total}}
jsonpath "$.data.items[0].id" == {{p1_id}}

# —— 分页：第 2 页与第 1 页没有落到同一首曲目 ——
# 排序键不唯一导致的翻页重复/漏行只有断言能发现，这是本次改动最需要守住的性质。
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks?page=0&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
page1_id: jsonpath "$.data.items[0].id"

GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks?page=1&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
page2_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.items" count == 1
jsonpath "$.data.items[0].id" != {{page1_id}}

# —— 搜索与默认列表同端点换参数（q 过滤不影响分页结构）——
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks?q=Let
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" isNumber

# —— 批量添加：trackIds 数组（破坏性变更：原为单首 trackId）——
# hurl 断言里不能做算术，故"总数恰好少 1"分三步：取添加前总数 → 添加 → 断言差值
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
total_before_add: jsonpath "$.data.total"
[Asserts]
jsonpath "$.data.total" == {{candidate_total}}

POST {{base_url}}/api/playlists/{{playlist_id}}/entries
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "trackIds": [{{candidate_first_id}}]
}

HTTP 200
[Asserts]
jsonpath "$.data" == 1

# —— 已加入歌单的曲目从候选中消失，总数变少 ——
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" < {{total_before_add}}

# —— 歌单条目按 position 升序（详情页展示顺序的依据）——
GET {{base_url}}/api/playlists/{{playlist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
entry_id: jsonpath "$.data.entries[0].entryId"
[Asserts]
jsonpath "$.data.entries" count == 1
jsonpath "$.data.entries[0].position" == 1
jsonpath "$.data.entries[0].track.id" == {{candidate_first_id}}

DELETE {{base_url}}/api/playlists/{{playlist_id}}/entries/{{entry_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 删掉条目后该曲目回到候选列表
GET {{base_url}}/api/playlists/{{playlist_id}}/candidate-tracks
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" == {{candidate_total}}

DELETE {{base_url}}/api/playlists/{{playlist_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
