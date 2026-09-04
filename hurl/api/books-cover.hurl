# 管理 REST：图书封面上传/删除

# 取第一本书
GET {{base_url}}/api/books
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
target_book_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.data.total" >= 1

# POST 上传：image/jpeg
POST {{base_url}}/api/books/{{target_book_id}}/cover
Authorization: Basic YWRtaW46dGVzdHBhc3M=
[Multipart]
file: file,cover-test.jpg; image/jpeg
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 验证：detail 中 coverSource=UPLOADED
GET {{base_url}}/api/books/{{target_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.coverSource" == "UPLOADED"
jsonpath "$.data.coverUrl" contains "/opds/v1.2/catalog/"

# DELETE 封面
DELETE {{base_url}}/api/books/{{target_book_id}}/cover
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 验证：coverSource=null
GET {{base_url}}/api/books/{{target_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.coverSource" not exists
