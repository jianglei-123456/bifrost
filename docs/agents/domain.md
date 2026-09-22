# 领域文档（Domain Docs）

本仓库的工程技能在探索代码库时应如何消费领域文档。

## 开始探索前，先读这些

- 仓库根目录的 **`CONTEXT.md`**（本仓库为中文词汇表：`**术语**：定义` + `_Avoid_: 反例` 的行内格式），或
- 若存在根目录的 **`CONTEXT-MAP.md`**：它指向每个上下文各自的 `CONTEXT.md`，读与当前主题相关的那几个。
- **`docs/adr/`**：读与你即将改动区域相关的 ADR。

上述文件若不存在，**静默继续**：不要指出缺失，也不要提议先建。`/domain-modeling`（经 `/grill-with-docs` 与 `/improve-codebase-architecture` 触达）会在术语或决策真正定下来时惰性创建。

## 目录结构

本仓库是**单上下文**：

```
/
├── CONTEXT.md
├── docs/
│   ├── adr/          ← 0001–0010，跨模块决策（0008/0009 原属管理端仓库，0010 记录合并）
│   └── agents/       ← 本目录：技能消费规则
├── bifrost-*/        ← Maven 多模块（adapter / api / common / domain / bootstrap / core）
└── bifrost-dashboard/ ← 管理端前端（Vue）子工程，见其 README
```

`bifrost-*` 是同一服务内部的 Maven 模块，**不是**多上下文：领域词汇只有一份，放在根 `CONTEXT.md`，管理端专有词是该文件的 §管理端（前端）一节。

`bifrost-dashboard/` 是同一个工程的前端子目录（不是第二个仓库、也不是第二个上下文）：它有自己的 README/设计文档，相对链接从该子目录出发；**不要**在它下面再建 `AGENTS.md`、`docs/agents/` 或 `CONTEXT.md`——规则与词汇只有根这一份。

## 使用词汇表的词汇

输出中出现领域概念时（issue 标题、重构提案、假设、测试名），用 `CONTEXT.md` 定义的术语，不要漂移到词汇表明确 `_Avoid_` 的同义词。

若需要的概念尚未入表，这是信号：要么你在发明项目不用的语言（重新考虑），要么确有缺口（记下来交给 `/domain-modeling`）。

## 标出 ADR 冲突

若你的输出与既有 ADR 矛盾，明确摆出来，不要静默覆盖：

> _与 ADR-0007（单镜像 + `/admin/`）矛盾，但值得重开，因为……_
