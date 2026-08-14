# 初始化：创建库根（指向样本音乐目录）并触发扫描（前置步骤，需先运行）
# 样本目录由 gen-sample-music.ps1 生成；路径用正斜杠避免 JSON 转义

POST {{base_url}}/api/library-roots
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "name": "测试库",
  "path": "E:/Dev/jianglei/bifrost-core/data-sample/music",
  "enabled": true
}

HTTP 200
[Captures]
root_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.code" == 0

# 触发全量扫描
POST {{base_url}}/api/library-roots/{{root_id}}/scan
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.added" == 3
jsonpath "$.data.error" == 0
