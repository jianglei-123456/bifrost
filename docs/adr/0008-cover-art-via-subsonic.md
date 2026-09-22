# 管理端封面图经 Subsonic getCoverArt 获取

管理 REST（`/api/**`）没有封面端点（已核实：`bifrost-api` 全部控制器均不提供封面；封面只存在于 Subsonic `/rest/getCoverArt.view` 与磁盘缓存目录 `data/covers`）。管理端需要专辑/曲目封面缩略图作为视觉主角（深空聆听室设计），因此决定：封面 URL 走 Subsonic 端点 `/rest/getCoverArt.view?id=al-<专辑id>&size=<档位>`，用登录时持有的 admin 口令按协议公式 `t=md5(口令+salt)` 生成每会话随机的令牌参数（与 DSub/Feishin 同款认证）。备选方案：后端新增 `/api/covers`（需改后端、阻塞前端）、v1 无封面（毁掉视觉方向）。后果：前端依赖 Subsonic 令牌算法（md5 小写 hex）与 salt 会话随机化；若后端未来补 `/api` 封面端点，替换为同源路径即可，令牌逻辑可整体移除。

> 原为 `bifrost-dashboard` 仓库的 ADR-0001，2026 年该工程并入本仓库时按根序列续号为 ADR-0008（见 [ADR-0010](0010-frontend-merged-into-core-repo.md)）。
> 范围提示：本 ADR 写于图书里程碑之前，当时 `/api` 全面没有封面端点。图书侧后来有了 `/api/books/{id}/cover`（写）与 OPDS 封面读；**仍然成立的部分是音乐侧**——专辑/艺术家封面至今只能走 Subsonic。
