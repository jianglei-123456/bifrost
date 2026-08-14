# 管理 REST：库根与扫描

GET {{base_url}}/api/library-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
root_id: jsonpath "$.data[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data[0].name" == "测试库"
jsonpath "$.data[0].enabled" == true

GET {{base_url}}/api/library-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.path" contains "data-sample"

PUT {{base_url}}/api/library-roots/{{root_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "测试库-改名"
}

HTTP 200
[Asserts]
jsonpath "$.data.name" == "测试库-改名"

GET {{base_url}}/api/scan/status
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.scanning" == false
jsonpath "$.data.roots[0].lastScanStats" contains "added"
