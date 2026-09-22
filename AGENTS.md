# Bifrost — Agent 约定

## Agent skills

### Issue 追踪

issue 与 spec 以 markdown 文件存放在本仓库 `.scratch/<feature>/` 下。**全仓库只有一个 issue 池**——管理端（`bifrost-dashboard/`）来源的需求也记在这里，不加前缀、不另开目录。见 `docs/agents/issue-tracker.md`。

### Triage 标签

沿用五个默认角色标签（`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`）。见 `docs/agents/triage-labels.md`。

### 领域文档

单上下文：根 `CONTEXT.md`（含 §管理端（前端）的前端专有词）+ 根 `docs/adr/`。见 `docs/agents/domain.md`。

管理端源码在 `bifrost-dashboard/` 子目录，它自己的说明（README、设计系统、契约速查、页面设计）用**从子目录出发的相对路径**引用，不重复一套 `docs/agents/`。
