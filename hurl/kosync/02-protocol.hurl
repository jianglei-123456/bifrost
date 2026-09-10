# KOSync 协议契约（M3-sync T4.3）
# 同步账号：reader / synctest → x-auth-key = md5("synctest") = cfa90411bb0e47abdfffc42c21508bdf
# （hurl 没有 md5 函数，按 hurl/README.md 里 Subsonic 令牌的既有做法钉死 hex）
# 关键：所有请求都带真实客户端的 Accept 头；响应必须是 application/json（客户端只解析这个）。

# ==================== 自备前置（hurl 变量不跨文件，本文件可单独运行）====================
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

# 取一本已入库图书的文档指纹（扫描时算出的 partial MD5）
GET {{base_url}}/api/books?page=0&size=1
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
document: jsonpath "$.data.items[0].partialMd5"
[Asserts]
jsonpath "$.data.items[0].partialMd5" matches "^[0-9a-f]{32}$"

# ==================== 注册：幂等自助注册，成功只能 201 ====================
POST {{base_url}}/users/create
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
{
  "username": "reader",
  "password": "cfa90411bb0e47abdfffc42c21508bdf"
}
HTTP 201
[Asserts]
jsonpath "$.username" == "reader"
header "Content-Type" contains "application/json"

# 用户名不符 → 402（在客户端 expected_status 里，message 会原样展示给用户）
POST {{base_url}}/users/create
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
{
  "username": "someone-else",
  "password": "cfa90411bb0e47abdfffc42c21508bdf"
}
HTTP 402
[Asserts]
jsonpath "$.code" == 3002
jsonpath "$.message" exists
header "Content-Type" contains "application/json"

# ==================== 登录：200 / 401 ====================
GET {{base_url}}/users/auth
Accept: application/vnd.koreader.v1+json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
HTTP 200
[Asserts]
jsonpath "$.authorized" == "OK"

# 无凭据 → 401（唯一不会被客户端重投队列的失败码），且带可读 message
GET {{base_url}}/users/auth
Accept: application/vnd.koreader.v1+json
HTTP 401
[Asserts]
jsonpath "$.code" == 3001
jsonpath "$.message" exists

# ==================== 上报进度：只有 200（永不 202）====================
PUT {{base_url}}/syncs/progress
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
{
  "document": "{{document}}",
  "progress": "/body/DocFragment[2]/body/p[1].0",
  "percentage": 0.4231,
  "device": "hurl-kobo",
  "device_id": "hurl-dev-1"
}
HTTP 200
[Asserts]
jsonpath "$.document" == "{{document}}"
# 秒级 epoch：毫秒值会被客户端判成"永远更新"
jsonpath "$.timestamp" isInteger
header "Content-Type" contains "application/json"

# 字段缺失 → 403（客户端不会发这种请求，但契约要钉住）
PUT {{base_url}}/syncs/progress
Accept: application/vnd.koreader.v1+json
Content-Type: application/json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
{
  "progress": "12",
  "percentage": 0.1,
  "device": "hurl-kobo",
  "device_id": "hurl-dev-1"
}
HTTP 403
[Asserts]
jsonpath "$.code" == 3003

# ==================== 读取进度：字段原样回显 ====================
GET {{base_url}}/syncs/progress/{{document}}
Accept: application/vnd.koreader.v1+json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
HTTP 200
[Asserts]
jsonpath "$.document" == "{{document}}"
jsonpath "$.percentage" == 0.4231
jsonpath "$.progress" == "/body/DocFragment[2]/body/p[1].0"
jsonpath "$.device" == "hurl-kobo"
jsonpath "$.device_id" == "hurl-dev-1"
jsonpath "$.timestamp" isInteger
header "Content-Type" contains "application/json"

# 未知 document → 200 + 空对象（**不是 404**：404 会让客户端报笼统错误而不是"没找到进度"）
GET {{base_url}}/syncs/progress/ffffffffffffffffffffffffffffffff
Accept: application/vnd.koreader.v1+json
x-auth-user: reader
x-auth-key: cfa90411bb0e47abdfffc42c21508bdf
HTTP 200
[Asserts]
body == "{}"

# 未认证读取 → 401
GET {{base_url}}/syncs/progress/{{document}}
Accept: application/vnd.koreader.v1+json
HTTP 401
[Asserts]
jsonpath "$.code" == 3001
