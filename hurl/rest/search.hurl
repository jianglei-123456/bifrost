# Subsonic：搜索

# search2：关键字命中艺术家（文件结构）
GET {{base_url}}/rest/search2.view?query=%E5%91%A8%E6%9D%B0%E4%BC%A6&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].searchResult2.artist[0].name" == "周杰伦"
jsonpath "$['subsonic-response'].searchResult2.album[0].name" == "叶惠美"
jsonpath "$['subsonic-response'].searchResult2.song[0].title" == "以父之名"

# search3：空查询返回全部（OS 澄清，离线同步）
GET {{base_url}}/rest/search3.view?u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].searchResult3.artist" count == 2
jsonpath "$['subsonic-response'].searchResult3.song" count == 3

# search3：按歌名搜索（ID3 结构）
GET {{base_url}}/rest/search3.view?query=Let%20It%20Be&u=admin&p=testpass&v=1.16.1&c=hurl&f=json
HTTP 200
[Asserts]
jsonpath "$['subsonic-response'].searchResult3.song[0].title" == "Let It Be"
