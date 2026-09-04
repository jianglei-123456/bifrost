# 管理 REST：图书库根 CRUD + 扫描触发 + 扫描状态

# 捕获现有 BOOK 库根 id
GET {{base_url}}/api/book-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
ebook_root_id: jsonpath "$.data[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data" isCollection
jsonpath "$.data[0].mediaType" == "BOOK"

GET {{base_url}}/api/book-roots/{{ebook_root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.id" == {{ebook_root_id}}
jsonpath "$.data.mediaType" == "BOOK"

# 部分更新（仅改 name）
PATCH {{base_url}}/api/book-roots/{{ebook_root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "测试图书库-改"
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.name" == "测试图书库-改"

# 触发扫描
POST {{base_url}}/api/book-roots/{{ebook_root_id}}/scan
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.scanStatus" == "SCANNING"
jsonpath "$.data.rootId" == {{ebook_root_id}}

# 状态读
GET {{base_url}}/api/book-roots/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 误用：尝试查 VIDEO 库根（应被强制 BOOK 过滤 → 返回 BOOK 库根列表）
GET {{base_url}}/api/book-roots?mediaType=VIDEO
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
# 即便请求 VIDEO，因为 controller 硬编码 BOOK 过滤，VIDEO 应该不命中
# （返回 BOOK 库根，与默认行为一致）
jsonpath "$.data" isCollection
