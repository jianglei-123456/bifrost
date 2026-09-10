# Subsonic：系统端点

# ping（JSON）
GET {{base_url}}/rest/ping.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"
jsonpath "$['subsonic-response'].version" == "1.16.1"
jsonpath "$['subsonic-response'].type" == "Bifrost"
jsonpath "$['subsonic-response'].openSubsonic" == true
# 服务器自身版本＝发布版本（BifrostVersion 唯一来源）；发版时与本文件一起改
jsonpath "$['subsonic-response'].serverVersion" == "1.0.0"

# ping（默认 XML 双格式）
GET {{base_url}}/rest/ping.view?u=admin&p=testpass&v=1.16.1&c=hurl
HTTP 200
[Asserts]
header "Content-Type" contains "xml"
body contains "<subsonic-response"

# 令牌认证 t=md5(password+salt)
GET {{base_url}}/rest/ping.view?u=admin&t=82b7df2e4394f5fc84ef9e683191fb2a&s=abcdef&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# getLicense：恒 valid
GET {{base_url}}/rest/getLicense.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].license.valid" == true

# getOpenSubsonicExtensions：免认证
GET {{base_url}}/rest/getOpenSubsonicExtensions.view?f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"
