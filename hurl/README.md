# Bifrost hurl 端点契约测试

基于 [hurl](https://hurl.dev) 的端点契约测试（Q6-A：先写 hurl 文件再跑）。

## 目录

- `setup.hurl`：初始化（创建音乐目录 + 扫描样本库）
- `opds/setup.hurl`：建图书目录 + 扫描 + OPDS 契约
- `kosync/01-setup.hurl` → `02-protocol.hurl` → `03-orphan.hurl`：阅读进度同步契约（run.ps1 按此顺序调用；**每个文件自备前置**——hurl 的变量不跨文件，02/03 各自从 `/api/books` 取文档指纹与图书 id）
- `api/*.hurl`：管理 REST（`/api/music-roots`、`/api/book-roots`、`/api/books`、`/api/book-sync` 等）契约
- `rest/*.hurl`：Subsonic（/rest/**）契约

## 运行

```powershell
# 一键运行（生成样本音乐 → 启动应用 → 跑全部 hurl 断言）
powershell -ExecutionPolicy Bypass -File hurl\run.ps1

# 手动方式
# 1) 打包（若无 jar）
.\mvnw -q -DskipTests package
# 2) 生成样本音乐
powershell -File hurl\gen-sample-music.ps1
# 3) 启动应用（清空旧 hurl 数据库）
Remove-Item -Recurse -Force .\data\hurl.db, .\data\hurl-covers -ErrorAction SilentlyContinue
$env:BIFROST_AUTH_INITIAL_PASSWORD = 'testpass'
$env:BIFROST_AUTH_SECRET = 'hurl-test-secret'
java -jar bifrost-bootstrap\target\bifrost-bootstrap-1.0.0-SNAPSHOT.jar `
  --server.port=18080 --bifrost.db.path=./data/hurl.db --bifrost.media.cover-cache-dir=./data/hurl-covers
# 4) 另开终端
hurl --test --variable base_url=http://localhost:18080 hurl\setup.hurl
hurl --test --variable base_url=http://localhost:18080 hurl\api\*.hurl hurl\rest\*.hurl
```

## 认证约定（测试账号）

- 初始密码：`testpass`（环境变量 `BIFROST_AUTH_INITIAL_PASSWORD`）
- 管理 REST：HTTP Basic `YWRtaW46dGVzdHBhc3M=`（admin:testpass）
- Subsonic：密码认证 `p=testpass`；令牌认证示例
  `t=82b7df2e4394f5fc84ef9e683191fb2a`（= md5("testpass" + salt "abcdef")）
- KOSync 同步账号：用户名 `reader` / 口令 `synctest`（**必须与管理员口令不同**，后端会拒绝相同口令）；
  协议头 `x-auth-key` = md5("synctest") = `cfa90411bb0e47abdfffc42c21508bdf`（hurl 没有 md5 函数，按 Subsonic 令牌的既有做法钉死 hex）。`kosync/01-setup.hurl` 会把这个账号写进库里，`api/book-sync.hurl` 自带同样的前置。

## 样本库

`gen-sample-music.ps1` 用 ffmpeg 生成（需本机安装 ffmpeg）：

| 文件 | 标签 |
| --- | --- |
| `周杰伦/叶惠美/01 - 以父之名.mp3` | 周杰伦 / 叶惠美 / 2003 |
| `Beatles/Let It Be/01 - Let It Be.flac` | The Beatles / Let It Be / 1970 |
| `solo/01 - 无题.m4a` | 无标签（未知艺术家/未知专辑） |
| `周杰伦/叶惠美/cover.jpg` | 目录封面 |

目录 `data-sample/` 为运行产物（已 gitignore）。
