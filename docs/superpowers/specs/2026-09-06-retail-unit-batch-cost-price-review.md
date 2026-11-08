> 文档状态：历史阶段设计/计划，非当前全量事实；按文件日期和提交阅读。被覆盖部分以[统一文档中心](../../current/README.md)为准；未覆盖且仍有效的规则须对照当前代码确认，不直接执行旧迁移/模式切换。

# 零售单位·批次·成本·价格事实链 —— 计划与实施审查报告

> 本报告是对 codex（superpowers SDD）生成的「农资零售单位、批次、成本与价格事实链」计划及其在 Orca worktree 中已实施部分的独立审查存档。

## 元信息

| 项 | 值 |
|---|---|
| 计划 | `docs/superpowers/plans/2026-09-06-retail-unit-batch-cost-price-plan.md`（654 行，10 Task）|
| 设计 | `docs/superpowers/specs/2026-09-06-retail-unit-batch-cost-price-design.md`（197 行）|
| 实施分支 | `setup-run-agri-system`（Orca worktree `cerith`）|
| 已实施 commit | `c799d55..b30fe81`（Task 1–4）|
| 审查日期 | 2026-09-06 |
| 审查方式 | plan/design 全文 + progress.md SDD 台账 + task-1~4 review/rereview + 逐行核实 Task 4 代码与 V119/V120/V121 迁移 |

## 结论先行

| 维度 | 评级 | 摘要 |
|---|---|---|
| 计划设计 | 优秀 | 单向事实链 + 灰度开关 + 系统只记录事实不替人决策 |
| 迁移 schema（Task1）| 优秀 | 生成列唯一约束、全链复合外键谱系、CHECK、append-only 触发器 |
| Task 2/3 | 可用 | review clean；Task3 埋「Task6 才闭合」的旧 avg-cost 依赖 |
| Task 4 | 危险半成品 | 3 Critical 经代码核实、未修，会致真实库存错乱/丢失 |
| Task 5–10 | 未开始 | codex 额度耗尽 |
| 合并 master | 禁止 | Task4 是 Task5–10 枢纽，且新旧库存双写会损坏数据 |

## 一、计划设计：值得采纳

- **单向事实链**：商品单位 → 收货批次 → 货位余额 → 销售归属 → 成本毛利；迁移期新旧并行，按 SKU/门店开关灰度。
- **系统只记录事实、不替经营者决策**：临期/日期未知/低毛利/低于底价只提示不禁令；不自动停售/报损/调拨/改价。
- **不伪造追溯**：批次归属分 `SCANNED / FULFILLMENT_CONFIRMED / RULE_ALLOCATED / UNKNOWN` 四级证据；`UNKNOWN` 不虚构成本，估算与实际毛利分开显示。
- **明确排除提成/营销/AI/支付**（plan L650）：与提成模块功能解耦，后续只消费可信成本。
- `progress.md` 的 preflight 有详尽的任务依赖矩阵 + self-consistency check，工程纪律远胜 08-09 commission-lifecycle（返工 10 轮）。

## 二、迁移 schema：精湛（Task1 review clean 名副其实）

- **product_unit**：生成列 `enabled_base_unit_key`（status=1 AND is_base_unit=1→1 else NULL）+ 唯一索引，实现「每商品恰好一个启用基准单位」；CHECK 强制基准单位系数=1。
- **全链复合外键谱系**：`(id,product_id)`/`(id,store_id)`/`(batch_id,product_id,store_id)` 贯穿收货批次→余额→移动→销售归属，DB 层杜绝错配门店/商品。
- **inventory_receipt_batch**：原始+基准双快照；`date_status` CHECK 强制日期一致性（UNKNOWN→全空 / KNOWN→齐全且生产≤有效期）。
- **append-only 触发器**（价格审计/决策日志/移动事实）用 `/*!80016*/` 条件注释，基线 MySQL 8.2。

小瑕疵（非阻塞）：① V119 `movement_type` CHECK 8 种、V121 改 5 种对齐 enum（Task1/Task4 类型契约未在 preflight 对齐，V121 已修正）；② V119 `inventory_location.code NOT NULL` 被 V121 放宽为 NULL，埋下 Important 4。

## 三、Task 4：三个 Critical 逐条代码核实（全部属实、全部未修）

> 文件路径相对 cerith worktree。codex 的 `task-4-review.md` 判定「不通过、不允许发版」，经核实准确诚实。

**Critical 1 — 通用 move 可凭空增删库存**
`BatchBalanceService.validateRequest`（L142-144）只要求「源或目的至少一个」，不按 purpose 约束端点数。`REPLENISHMENT/PICK` 只填目的 → `move`（L52-60）走 to 分支，`newBalance`（L175-194）从任意已收货批次建零余额再 increase 任意数量 = 凭空造库存；只填源 = deductAvailable 直接销毁库存。持 `inventory:adjust` 权限即可绕过所有业务单据。

**Critical 2 — 切换状态形同虚设，新旧双写互相覆盖**
`InventoryApplicationService` 中 `move`（L114-141）走新链路，但 `inbound`（L150-178）/`outbound`（L187-267）/`transfer`（L276-342）全是旧链路、无一检查 `BATCH_LOCATION` 状态。尤其 `outbound` 是 POS 销售出库主路径——即使 SKU 已切换，销售仍扣旧 `inventory_batch`、不扣新余额，两套余额并行写、`projectInventory` 与旧路径互相覆盖 `inventory` 快照。新链路目前只是并行记账，未接管真实扣减。

**Critical 3 — 无 cutover，首次投影抹掉历史库存**
`projectInventory`（L246-249）用 `sumBaseQuantityByStoreAndProduct` 覆盖 `inventory` 快照；历史库存在 `inventory_batch` 未进 `batch_balance`，切换后首次投影即把历史库存从快照抹掉。`featureStatusRepository` 全程只读（L264-269，无 save/enable），V121 `cutover_at/cutover_by` 允许 NULL——无可执行/可校验/可回滚的切换流程。

**Important 4/5/6 + Minor 7**（均属实）：并发首收货建重复后仓（createBackroom 无 upsert + code 可空无唯一约束）、projectInventory 无锁 SUM 并发覆盖、测试仅 4 个 Mockito 用例（无并发/回滚/单边拒绝/cutover）、H2 测不到 MySQL 条件触发器。

## 四、与 master 衔接

- **V119 撞号**：已解决——master 提成索引 V119→V122 避让（commit `6f0bbd1`），V119/V120/V121 留给零售线。
- **功能解耦**：plan 不碰提成；Task6 把 `avg_cost_price` 降为浏览缓存，只影响毛利报表，不影响提成（基于销售额 `staff_performance_log.amount`）。
- **接触面**：Task5/6/7 改 `OrderItem/OrderApplicationService`，将来合并有 git 文本冲突（可手动解），无逻辑冲突。
- **潜在再撞号**：零售线续作若新增 Flyway 迁移会从 V122 起（cerith 现到 V121），撞 master 的 V122；但 Task5–10 基本是 java/vue/scripts，schema 已在 V119–V121 建全，大概率不需新迁移；真需要时提成索引未入库可再让号。

## 五、续作 / 整合建议

1. 这条线值得续作——设计优秀、schema 已建全、Task1–3 基础扎实。
2. 必须先啃 Task4 的 3 Critical（非小修）：按 purpose 拆分 move 端点约束、所有库存写入口统一按状态路由、实现受控可回滚 cutover + 历史库存初始化 + 三层对账 + 真实 MySQL 并发测试。
3. 整合 master 前置条件：Task1–10 全部 review clean + 真实 MySQL E2E 通过 + 迁移号段最终协调。在此之前零售线继续在 cerith worktree 独立开发，绝不合并代码。
4. 设计文档（plan+design）可先纳入 master（设计资产，主线可见）；代码不合并。

## 附录：执行状态与产物位置

- **SDD 台账**：cerith worktree `.superpowers/sdd/2026-09-06-retail-unit-batch-cost-price-plan/`（progress.md + task-1~4 brief/report/review + task-2/3 rereview-1/2 + review-*.diff），被 `.gitignore=*` 忽略，未进 git。
- **Task 进度**：Task1（3轮fix）/Task2（2轮fix）/Task3（2轮fix）complete + review clean；Task4 卡在 fix round 1/5（3 Critical 未修）；Task5–10 未开始。
- **Task1 deferred**：完整 V1..V118 生产升级演练。
- **Task3 deferred**：真实 MySQL 并发收货演练；Task6 须先替换 legacy avg-cost 消费方再 rollout。
