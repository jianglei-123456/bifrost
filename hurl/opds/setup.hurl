# OPDS 端点 hurl 契约测试（M2-book，doc/m2-book/task/02-OPDS发布.md）
# 前置：运行 hurl/run.ps1 + gen-sample-ebook.ps1 生成样本
#       （data-sample/ebook/sample.epub + sample.pdf + cover-test.jpg）
# 扫描后存在 1 EPUB + 1 PDF

# 图书目录创建（/api/book-roots 强制 BOOK，不再接受 mediaType 覆盖）
POST {{base_url}}/api/book-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "测试图书库",
  "path": "E:/Dev/jianglei/bifrost-core/data-sample/ebook",
  "enabled": true
}
HTTP 200
[Captures]
ebook_root_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.mediaType" == "BOOK"

# 触发扫描（图书扫描是异步的：立即返回 SCANNING，须轮询 /api/book-roots/scan/status；
# 音乐端点 /api/music-roots/{id}/scan 是同步的，且拒绝非 MUSIC 目录）
POST {{base_url}}/api/book-roots/{{ebook_root_id}}/scan
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.scanStatus" == "SCANNING"

# ==================== catalog 根（navigation feed）====================
GET {{base_url}}/opds/v1.2/catalog
HTTP 200
[Asserts]
header "Content-Type" contains "application/atom+xml"
xpath "string(/*[local-name()='feed']/*[local-name()='title'])" == "Bifrost 图书库"
xpath "count(/*[local-name()='feed']/*[local-name()='entry'])" >= 2
xpath "string(/*[local-name()='feed']/*[local-name()='entry'][1]/*[local-name()='title'])" == "全部图书"
xpath "string(/*[local-name()='feed']/*[local-name()='entry'][2]/*[local-name()='title'])" == "最近添加"

# ==================== all（acquisition feed）====================
# 异步扫描：重试等待入库完成（2 本书出现）
GET {{base_url}}/opds/v1.2/catalog/all?page=1&count=50
[Options]
retry: 30
retry-interval: 500
HTTP 200
[Asserts]
header "Content-Type" contains "kind=acquisition"
xpath "count(/*[local-name()='feed']/*[local-name()='entry'])" >= 2
xpath "string(/*[local-name()='feed']/*[local-name()='entry'][1]/*[local-name()='title'])" exists
xpath "string(/*[local-name()='feed']/*[local-name()='entry'][1]/*[local-name()='link']/@type)" contains "/"

# 书可见 ≠ 扫描结束：扫描线程仍在写库时，后面 api/ 目录的写操作（PATCH book / 上传封面）
# 会撞 SQLite 写锁报 500。此处等扫描真正收敛到 IDLE 再继续。
GET {{base_url}}/api/book-roots/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
[Options]
retry: 40
retry-interval: 500
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.scanStatus" == "IDLE"

# recent 标 sort/new（Readest "Auto-download new items" 钩子）
GET {{base_url}}/opds/v1.2/catalog/recent
HTTP 200
[Asserts]
xpath "string(/*[local-name()='feed']/*[local-name()='link'][@*[local-name()='rel']='http://opds-spec.org/sort/new']/@href)" == "/opds/v1.2/catalog/recent"

# ==================== search ====================
GET {{base_url}}/opds/v1.2/search?q=Test
HTTP 200
[Asserts]
xpath "count(/*[local-name()='feed']/*[local-name()='entry'])" >= 1

# 空 q → 400
GET {{base_url}}/opds/v1.2/search
HTTP 400
[Asserts]
xpath "string(/*[local-name()='error']/*[local-name()='code'])" == "400"

# ==================== OSDD ====================
GET {{base_url}}/opds/v1.2/search.xml
HTTP 200
[Asserts]
header "Content-Type" contains "opensearchdescription"
xpath "string(/*[local-name()='OpenSearchDescription']/*[local-name()='ShortName'])" == "Bifrost 图书搜索"

# ==================== 404 错误 ====================
GET {{base_url}}/opds/v1.2/catalog/99999999/cover
HTTP 404
[Asserts]
xpath "string(/*[local-name()='error']/*[local-name()='code'])" == "404"

GET {{base_url}}/opds/v1.2/catalog/99999999/file
HTTP 404

# ==================== 分页钳位 ====================
# count=1 → 钳到 10
GET {{base_url}}/opds/v1.2/catalog/all?page=1&count=1
HTTP 200
[Asserts]
xpath "string(/*[local-name()='feed']/*[local-name()='itemsPerPage'])" == "10"

# count=9999 → 钳到 200
GET {{base_url}}/opds/v1.2/catalog/all?page=1&count=9999
HTTP 200
[Asserts]
xpath "string(/*[local-name()='feed']/*[local-name()='itemsPerPage'])" == "200"

# page=0 → 400
GET {{base_url}}/opds/v1.2/catalog/all?page=0
HTTP 400
