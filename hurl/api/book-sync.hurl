# 管理端 book-sync 契约（M3-sync T3.6）
# 自给自足：自己造一条进度再断言列表/统计/删除，不依赖 kosync/ 目录是否先跑过。
# 同步账号：reader / synctest（口令由 kosync/01-setup.hurl 落库；随机口令时本文件会先设成 synctest）。

# 同步账号读：明文口令回显 + 可抄进设备的地址
PUT {{base_url}}/api/book-sync/account
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "username": "reader",
  "password": "synctest"
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.password" == "synctest"
jsonpath "$.data.serverUrl" contains "://"

# R2c：同步口令不得与管理员口令相同
PUT {{base_url}}/api/book-sync/account
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "password": "testpass"
}
HTTP 400
[Asserts]
jsonpath "$.code" == 1000
jsonpath "$.message" exists

# 未认证访问 → 401 + 信封 1002
GET {{base_url}}/api/book-sync/progress
HTTP 401
[Asserts]
jsonpath "$.code" == 1002

# 造一条进度（关联库里第一本书）
GET {{base_url}}/api/books?page=0&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
document: jsonpath "$.data.items[0].partialMd5"
book_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" >= 1

PUT {{base_url}}/syncs/progress
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
{
  "document": "{{document}}",
  "progress": "42",
  "percentage": 0.42,
  "device": "hurl-kobo",
  "device_id": "hurl-dev-1"
}
HTTP 200

# 进度列表：按书展示
GET {{base_url}}/api/book-sync/progress
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
progress_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.total" >= 1
jsonpath "$.data.items[0].bookId" exists
jsonpath "$.data.items[0].bookTitle" exists
jsonpath "$.data.items[0].device" == "hurl-kobo"
jsonpath "$.data.items[0].reportedAt" exists

# 仅看孤儿
GET {{base_url}}/api/book-sync/progress?onlyOrphans=true
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 概览统计
GET {{base_url}}/api/book-sync/stats
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.progressCount" >= 1
jsonpath "$.data.deviceCount" >= 1
jsonpath "$.data.lastReportedAt" exists

# 设备列表（只读；没有"踢设备"）
GET {{base_url}}/api/book-sync/devices
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.total" >= 1
jsonpath "$.data.items[0].deviceId" exists

# 重置（删除）进度
DELETE {{base_url}}/api/book-sync/progress/{{progress_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 再删同一条 → 1001
DELETE {{base_url}}/api/book-sync/progress/{{progress_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 404
[Asserts]
jsonpath "$.code" == 1001

# 生成随机口令（返回明文），随后恢复成固定口令，保证本文件可重复执行
POST {{base_url}}/api/book-sync/account/password
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.password" exists
jsonpath "$.data.password" != "synctest"

PUT {{base_url}}/api/book-sync/account
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "password": "synctest"
}
HTTP 200
[Asserts]
jsonpath "$.data.password" == "synctest"
