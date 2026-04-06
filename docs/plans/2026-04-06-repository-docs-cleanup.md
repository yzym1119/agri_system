# Repository Docs Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 精简仓库文档结构，保留核心入口并归档历史过程文档。

**Architecture:** 通过“重写入口 + 迁移历史文档 + 新增归档说明”的方式整理文档层级。保留 `ddl/`、`openapi/` 等结构化资料，把阶段性过程文档从根目录移到 `docs/archive/2025/`，避免继续污染主视图。

**Tech Stack:** Markdown、Git、PowerShell 文件迁移

---

### Task 1: 重写主入口文档

**Files:**
- Modify: `README.md:1`
- Create: `docs/README.md:1`

**Step 1: 明确入口职责**

- `README.md` 只保留项目介绍、结构、启动方式、文档入口
- `docs/README.md` 只保留文档导航与归档说明

**Step 2: 写入最小可维护内容**

- 删除乱码与过程性说明
- 增加对 `ddl/`、`openapi/`、`docs/archive/2025/` 的引用

**Step 3: 抽查可读性**

Run: `Get-Content README.md`
Expected: 中文正常、结构清晰、无阶段性汇报内容

### Task 2: 迁移历史过程文档

**Files:**
- Modify: `AFTER_SALES_MODULE_DELIVERY.md`
- Modify: `AI_GENERATION_RULES.md`
- Modify: `INFRASTRUCTURE_MODULE_DELIVERY.md`
- Modify: `MODULE_STATUS.md`
- Modify: `PACKAGE_MODULE_DELIVERY.md`
- Modify: `PERFORMANCE_MODULE_DELIVERY.md`
- Modify: `PRODUCT_MODULE_DELIVERY.md`
- Modify: `PURCHASE_MODULE_DELIVERY.md`
- Modify: `STORE_MODULE_DELIVERY.md`
- Modify: `TEST_ISSUES_REPORT.md`
- Modify: `wallet_auth_code_lock_issue.md`
- Modify: `docs/FRONTEND_GENERATION_RULES.md`
- Modify: `docs/SYSTEM_INTERFACE_SUMMARY.md`
- Modify: `docs/VIP_LIFECYCLE_AND_POINTS_CLEAR_AUDIT.md`
- Create: `docs/archive/2025/`

**Step 1: 建立归档目录**

Run: `New-Item -ItemType Directory -Force docs\archive\2025`
Expected: 目录存在

**Step 2: 迁移旧文档**

- 将根目录阶段性文档移动到 `docs/archive/2025/`
- 将 `docs/` 下旧专题文档也统一并入归档

**Step 3: 校验顶层精简结果**

Run: `Get-ChildItem -File *.md`
Expected: 仅剩核心文档入口

### Task 3: 校验与提交

**Files:**
- Modify: `docs/plans/2026-04-06-repository-docs-cleanup-design.md:1`
- Modify: `docs/plans/2026-04-06-repository-docs-cleanup.md:1`

**Step 1: 检查目录结果**

Run: `git status --short`
Expected: 仅出现预期的新增、修改、重命名

**Step 2: 检查归档是否生效**

Run: `git diff --cached --stat`
Expected: 根目录文档减少，`docs/archive/2025/` 增加

**Step 3: 提交**

```bash
git add README.md docs
git commit -m "chore: 精简仓库文档结构"
```
