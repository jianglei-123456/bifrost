# Subsonic：扫描与用户

# getScanStatus
GET {{base_url}}/rest/getScanStatus.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].scanStatus.scanning" == false
jsonpath "$['subsonic-response'].scanStatus.count" == 3

# startScan：立即返回状态，后台扫描
GET {{base_url}}/rest/startScan.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].status" == "ok"

# getUser：单用户语义（Q18 省略 email）
GET {{base_url}}/rest/getUser.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].user.username" == "admin"
jsonpath "$['subsonic-response'].user.adminRole" == true
jsonpath "$['subsonic-response'].user.email" not exists
jsonpath "$['subsonic-response'].user.folder[0]" exists

# getUsers：返回当前用户（Q19）
GET {{base_url}}/rest/getUsers.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].users.user[0].username" == "admin"
