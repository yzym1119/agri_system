# Repository Docs Cleanup Design

**Goal:** 将仓库文档整理为“当前可维护 + 历史可追溯”的结构，减少顶层噪音。

## Scope

- 重写根目录 `README.md`，保留项目总览、结构、启动方式与文档入口。
- 新增 `docs/README.md` 作为文档导航页。
- 将阶段性总结、交付记录、问题排查类文档迁移到 `docs/archive/2025/`。
- 保留 `ddl/` 与 `openapi/` 等仍有直接工程价值的资料。

## Decisions

### 1. 顶层精简

仓库根目录只保留对新接手开发者立即有用的核心入口，不再放大量过程性 Markdown。

### 2. 旧文档归档而非先删

考虑到历史文档可能仍有追溯价值，本轮优先迁移到归档目录，不直接大规模删除。

### 3. 用新导航覆盖旧入口

由于部分旧文档存在编码异常或内容重复，本轮新增干净入口文档，避免继续把旧文档暴露为主路径。

## Validation

- `git status` 确认迁移范围正确
- `rg --files docs` / `rg --files` 确认文档结构符合预期
- 抽查 `README.md` 与 `docs/README.md` 内容可读
