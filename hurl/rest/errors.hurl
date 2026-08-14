# Subsonic：错误码（协议 200 + failed + error）

# 40：密码错误（统一认证失败）
GET {{base_url}}/rest/ping.view?u=admin&p=wrong&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "failed"
jsonpath "$['subsonic-response'].error.code" == 40

# 10：缺必填参数（无 v）
GET {{base_url}}/rest/ping.view?u=admin&p=testpass&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 10

# 42：apiKey 未实现
GET {{base_url}}/rest/ping.view?u=admin&p=testpass&apiKey=xyz&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 42

# 43：t/s 与 p 冲突
GET {{base_url}}/rest/ping.view?u=admin&t=82b7df2e4394f5fc84ef9e683191fb2a&s=abcdef&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 43

# 30：客户端版本过高
GET {{base_url}}/rest/ping.view?u=admin&p=testpass&v=1.17.0&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 30

# 0：未实现端点（能力矩阵 ⏳/❌，Q3-A）
GET {{base_url}}/rest/getGenres.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 0
jsonpath "$['subsonic-response'].error.message" == "Not implemented"

GET {{base_url}}/rest/createUser.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].error.code" == 0

# 70：数据不存在（stream 缺失文件，二进制端点错误 text/xml）
GET {{base_url}}/rest/stream.view?id=tr-999999&u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Type" contains "text/xml"
body contains "code=\"70\""
