# 管理 REST：账号

GET {{base_url}}/api/user
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.username" == "admin"
jsonpath "$.data.role" == "ADMIN"

GET {{base_url}}/api/ping
HTTP 200
[Asserts]
body == "pong"
