# 管理 REST：图书目录 CRUD + 扫描触发 + 扫描状态

# 捕获现有 BOOK 目录 id
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

# 状态读：异步扫描的收敛等待
# 触发后扫描线程仍可能在写库；不等它收敛就去跑后面的写操作（PATCH book / 上传封面）会撞 SQLite 写锁 → 500。
# delay 先给异步任务启动留时间（否则可能读到"尚未开始"的 IDLE）。
GET {{base_url}}/api/book-roots/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
[Options]
delay: 500
retry: 40
retry-interval: 500
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.scanStatus" == "IDLE"

# 交叉边界：图书目录 id 不得经音乐端点操作 → 400/1000（该目录非音乐类型）
GET {{base_url}}/api/music-roots/{{ebook_root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 400
[Asserts]
jsonpath "$.code" == 1000

# 误用：尝试查 VIDEO 目录（应被强制 BOOK 过滤 → 返回图书目录列表）
GET {{base_url}}/api/book-roots?mediaType=VIDEO
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
# 即便请求 VIDEO，因为 controller 硬编码 BOOK 过滤，VIDEO 应该不命中
# （返回图书目录，与默认行为一致）
jsonpath "$.data" isCollection
