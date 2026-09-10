# KOSync hurl 前置（M3-sync）
# 前置：hurl/run.ps1 已跑过 setup.hurl + opds/（图书目录已建、扫描已收敛到 IDLE、库里有书）
# 作用：把同步账号设成固定口令（首启是随机口令），供后续协议文件使用。
#
# ⚠️ hurl 的变量**不跨文件**（每个文件独立校验/持有变量），所以这里不做 capture；
#    需要文档指纹 / 图书 id 的文件各自从 /api/books 取（见 02/03 的开头）。

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
jsonpath "$.data.username" == "reader"
jsonpath "$.data.password" == "synctest"
jsonpath "$.data.registrationEnabled" == true
jsonpath "$.data.autoScanOnUnmatched" == true
# 给用户抄进设备的地址必须带 scheme（客户端不自动补 http://）
jsonpath "$.data.serverUrl" contains "://"

# 探活（匿名）——官方 Docker healthcheck 也打这个端点
GET {{base_url}}/healthcheck
Accept: application/vnd.koreader.v1+json
HTTP 200
[Asserts]
jsonpath "$.state" == "OK"
header "Content-Type" contains "application/json"
