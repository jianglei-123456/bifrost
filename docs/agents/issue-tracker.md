# Issue 追踪：本地 Markdown

本仓库的 issue 与 spec 以 markdown 文件形式存放在 `.scratch/`。

## 约定

- 一个 feature 一个目录：`.scratch/<feature-slug>/`
- spec 为 `.scratch/<feature-slug>/spec.md`
- 实现 issue 一票一文件：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 起编号；**不要**写单个合并的 tickets 文件
- triage 状态记在每个 issue 文件靠顶部的 `Status:` 行（角色字符串见 `docs/agents/triage-labels.md`）
- 评论与对话历史追加到文件底部 `## Comments` 标题下

## 当某个技能说「发布到 issue 追踪器」

在 `.scratch/<feature-slug>/` 下新建文件（目录不存在就创建）。

## 当某个技能说「取出相关票据」

读取所引用路径的文件。用户通常会直接给出路径或 issue 编号。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 一个文件，**child** 每票一个文件。

- **Map**：`.scratch/<effort>/map.md`（Notes / Decisions-so-far / Fog 正文）。
- **Child 票据**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 起编号，正文写问题本身。`Type:` 行记录票据类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **阻塞**：靠顶部写 `Blocked by: NN, NN`。列出的文件全部 `resolved` 后该票解锁。
- **Frontier**：扫描 `.scratch/<effort>/issues/`，取未关闭、无阻塞、未认领的文件；编号最小者优先。
- **认领**：先设 `Status: claimed` 并保存，再动手。
- **解决**：在 `## Answer` 标题下追加答案，设 `Status: resolved`，再把上下文指针（要点 + 链接）追加到 `map.md` 的 Decisions-so-far。
