# KOSync 孤儿流程契约（M3-sync T4.3，R5/C1）
# 用一个人为的、库里不存在的文档指纹，走"孤儿 → 重新匹配 → 绑定 / 忽略"全流程。
#
# 注意两点（都是实证踩出来的）：
# 1. 未匹配指纹会触发一次后台扫描（auto-scan-on-unmatched），扫描期间 SQLite 写锁可能让紧接着的
#    写请求 500 —— 处理办法：先轮询 scan/status 到 IDLE，并给写请求加 [Options] retry。
# 2. hurl 的 [Options] 必须紧跟请求行，且**其后不能再有裸 header 行**——需要头就用
#    `header: Name: value` 选项表达（裸 header 会被当成选项名而报错；值**不能加引号**，
#    加了引号会把引号一起发出去，服务端直接 400）。
# 3. hurl 的变量不跨文件：本文件开头自备前置。

# ==================== 自备前置 ====================
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

GET {{base_url}}/api/books?page=0&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
book_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" >= 1

# ==================== 未匹配指纹 → 孤儿 ====================
PUT {{base_url}}/syncs/progress
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
{
  "document": "00112233445566778899aabbccddeeff",
  "progress": "12",
  "percentage": 0.05,
  "device": "hurl-kobo",
  "device_id": "hurl-dev-9"
}
HTTP 200

# 自动扫描是后台异步的：等它收敛到 IDLE，避免紧接着的写请求撞 SQLite 写锁
GET {{base_url}}/api/book-roots/scan/status
[Options]
delay: 1000
retry: 40
retry-interval: 500
header: Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.scanStatus" == "IDLE"

# 孤儿列表（默认不含已忽略）
GET {{base_url}}/api/book-sync/orphans
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
orphan_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.total" >= 1
jsonpath "$.data.items[0].documentFingerprint" == "00112233445566778899aabbccddeeff"

# 人工重新匹配：库里确实没有这本书 → matched=false（正常结果，不是错误）
POST {{base_url}}/api/book-sync/orphans/{{orphan_id}}/rematch
[Options]
retry: 10
retry-interval: 300
header: Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.matched" == false

# 人工绑定到库里第一本书
POST {{base_url}}/api/book-sync/orphans/{{orphan_id}}/bind
[Options]
retry: 10
retry-interval: 300
header: Authorization: Basic YWRtaW46dGVzdHBhc3M=
header: Content-Type: application/json
{
  "bookId": {{book_id}}
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.matchSource" == "MANUAL"
jsonpath "$.data.bookId" exists

# 绑定后不再是孤儿
GET {{base_url}}/api/book-sync/orphans
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" == 0

# ==================== 再来一条孤儿并忽略它 ====================
PUT {{base_url}}/syncs/progress
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
{
  "document": "cafebabecafebabecafebabecafebabe",
  "progress": "3",
  "percentage": 0.02,
  "device": "hurl-kobo",
  "device_id": "hurl-dev-9"
}
HTTP 200

GET {{base_url}}/api/book-sync/orphans
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
ignored_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" == 1

POST {{base_url}}/api/book-sync/orphans/{{ignored_id}}/ignore
[Options]
retry: 10
retry-interval: 300
header: Authorization: Basic YWRtaW46dGVzdHBhc3M=
header: Content-Type: application/json
{
  "ignored": true
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 默认隐藏已忽略；includeIgnored=true 才看得见
GET {{base_url}}/api/book-sync/orphans
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" == 0

GET {{base_url}}/api/book-sync/orphans?includeIgnored=true
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.total" == 1
