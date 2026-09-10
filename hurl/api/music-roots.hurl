# 管理 REST：音乐目录与音乐扫描（/api/music-roots，ADR-0005 媒体前缀命名）

GET {{base_url}}/api/music-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
root_id: jsonpath "$.data[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data[0].name" == "测试库"
jsonpath "$.data[0].enabled" == true
# 列表只含 MUSIC 目录（图书目录走 /api/book-roots）
jsonpath "$.data[0].mediaType" == "MUSIC"
# 图书目录（opds/setup 已先建）绝不出现在音乐列表 → 数量必须恰为 1
jsonpath "$.data" count == 1

GET {{base_url}}/api/music-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.path" contains "data-sample"

PATCH {{base_url}}/api/music-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "测试库-改名"
}

HTTP 200
[Asserts]
jsonpath "$.data.name" == "测试库-改名"

# 部分更新不接受改媒体类型（请求体里的 mediaType 被忽略，仍为 MUSIC）
PATCH {{base_url}}/api/music-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "mediaType": "BOOK"
}

HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.mediaType" == "MUSIC"

# 扫描全部（同步、串行；只扫启用的 MUSIC 目录）
POST {{base_url}}/api/music-roots/scan/all
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.added" == 0
jsonpath "$.data.error" == 0

GET {{base_url}}/api/music-roots/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.scanning" == false
jsonpath "$.data.roots[0].lastScanStats" contains "added"

# 不存在的 id → 404 + 1001（真实图书目录 id 走"类型拒绝"→ 400/1000，交叉断言见 book-roots.hurl 末尾）
GET {{base_url}}/api/music-roots/999999
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 404
[Asserts]
jsonpath "$.code" == 1001

# ==================== 启用开关往返（管理端「音乐库」页的核心行为）====================

# 停用
PATCH {{base_url}}/api/music-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "enabled": false
}

HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.enabled" == false

# 停用后仍须出现在列表里（否则管理端再也点不开"启用"开关）
GET {{base_url}}/api/music-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data[0].id" == {{root_id}}
jsonpath "$.data[0].enabled" == false
jsonpath "$.data[0].mediaType" == "MUSIC"

# 扫描状态只含启用的根 → 停用后为空
GET {{base_url}}/api/music-roots/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.scanning" == false
jsonpath "$.data.roots" isEmpty

# 复原（后续契约文件依赖曲目可见）
PATCH {{base_url}}/api/music-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "enabled": true
}

HTTP 200
[Asserts]
jsonpath "$.data.enabled" == true
