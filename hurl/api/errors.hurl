# 管理 REST：错误场景（信封与 HTTP 状态）

# 未认证 → 401 + code 1002
GET {{base_url}}/api/user
HTTP 401
[Asserts]
jsonpath "$.code" == 1002

# 资源不存在 → 404 + code 1001
GET {{base_url}}/api/albums/999999
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 404
[Asserts]
jsonpath "$.code" == 1001

# 参数错误 → 400 + code 1000
GET {{base_url}}/api/search
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 400
[Asserts]
jsonpath "$.code" == 1000

# 重复库根路径 → 400 + code 1004
POST {{base_url}}/api/library-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "重复",
  "path": "E:/Dev/jianglei/bifrost-core/data-sample/music"
}

HTTP 400
[Asserts]
jsonpath "$.code" == 1004
