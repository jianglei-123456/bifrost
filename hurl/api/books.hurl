# 管理 REST：图书列表 / 详情 / PATCH / DELETE

# 列表（默认 page=0 size=20）
GET {{base_url}}/api/books
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Captures]
first_book_id: jsonpath "$.data.items[0].id"
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.total" >= 1
jsonpath "$.data.items[0].id" exists
jsonpath "$.data.items[0].title" exists

# 分页
GET {{base_url}}/api/books?page=0&size=10
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.items" isCollection

# 过滤：按 title
GET {{base_url}}/api/books?title=Test
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.items" isCollection

# 过滤：按 author
GET {{base_url}}/api/books?author=Test
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.data.items" isCollection

# 详情
GET {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.id" == {{first_book_id}}
jsonpath "$.data.title" exists
jsonpath "$.data.format" exists

# 不存在 → 404 + code 1001
GET {{base_url}}/api/books/99999999
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 404
[Asserts]
jsonpath "$.code" == 1001

# PATCH：更新 authors（string 形式）
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "authors": "New Author One & New Author Two"
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.authors" == "New Author One & New Author Two"

# PATCH：authors 数组形式（Q28：String.join(" & ", list)）
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "authors": ["Array Author A", "Array Author B", "Array Author C"]
}
HTTP 200
[Asserts]
jsonpath "$.code" == 0
jsonpath "$.data.authors" == "Array Author A & Array Author B & Array Author C"

# PATCH：subject 数组
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "subject": ["Fiction", "Mystery", "Adventure"]
}
HTTP 200
[Asserts]
jsonpath "$.data.subject" == "Fiction; Mystery; Adventure"

# PATCH：rating（Day-one 锁定 → 400 + code 1000）
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "rating": 5
}
HTTP 400
[Asserts]
jsonpath "$.code" == 1000
jsonpath "$.message" contains "rating"

# PATCH：filePath 不可写
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "filePath": "/etc/passwd"
}
HTTP 400
[Asserts]
jsonpath "$.code" == 1000

# PATCH：未知字段
PATCH {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
Content-Type: application/json
{
  "unknownField": "x"
}
HTTP 400
[Asserts]
jsonpath "$.code" == 1000

# DELETE：删 DB 行（不删文件）
DELETE {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 200
[Asserts]
jsonpath "$.code" == 0

# 之后查不到 → 404 + code 1001
GET {{base_url}}/api/books/{{first_book_id}}
Authorization: Basic YWRtaW46dGVzdHBhc3M=
HTTP 404
[Asserts]
jsonpath "$.code" == 1001
