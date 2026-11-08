> 文档状态：历史阶段设计/计划，非当前全量事实；按文件日期和提交阅读。被覆盖部分以[统一文档中心](../../current/README.md)为准；未覆盖且仍有效的规则须对照当前代码确认，不直接执行旧迁移/模式切换。

# 零售事实链续作路线图：Task5–10 范围拆解与验收基线（2026-09-12）

> **定位**：本文是 codex 原计划 `docs/superpowers/plans/2026-09-06-retail-unit-batch-cost-price-plan.md`（Task1–10）与审查报告 `docs/superpowers/specs/2026-09-06-retail-unit-batch-cost-price-review.md` 的**续作补充**，不重复原 plan 的逐步骤，只回答一个问题：**从「Task4 遗留已修复并合并」的当前基线出发，如何安全走到可灰度上线。**
>
> 原 plan 的设计被审查评为「优秀」，本文沿用其 Task5–10 的 Files/Interfaces/Steps，补充依赖顺序、当前代码起点、激活安全前置、业务决策点与验收清单。

## 一、当前基线（截至 2026-09-12）

### 已完成
- **Task1–3（codex，review clean）**：迁移护栏 V119/V120、SKU 多单位与旧主辅单位兼容、收货事实替代「采购单号即批次号」。
- **Task4（codex 半成品 + 本轮修复）**：批次货位余额与移动事实。本轮 5 个修复已合并进本地 `master`（merge commit `eac5be4`，**未 push**）；合并后经专业代码审查又补 1 个闸门缺口（`7392f92`）：

| commit | 内容 | 对应审查项 |
|---|---|---|
| `1c393d7` | 批次货位移动按 purpose 约束端点，堵住凭空增删库存 | Critical 1 |
| `f8beeb9` | 移动幂等键唯一约束（V124），防重试重复增减余额 | Critical 1 收尾 |
| `5c9e22b` | legacy inbound/outbound/transfer/adjust 加切换守卫 | Critical 2 安全闸门 |
| `9109c82` | 默认后仓唯一约束（V123）+ 库存投影 FOR UPDATE 加锁 | Important 4/5 |
| `d373c2d` | 真实 MySQL/InnoDB 集成验证（`-Dmysql.it=true` 开启） | Important 6 |
| `7392f92` | 切换守卫补全 `getInventoryDetail` 读路径惰性物化（审查发现的遗漏旁路） | Critical 2 补全 |

- **迁移号协调**：master 提成索引停在 V122（`6f0bbd1` 主动避让），零售链占 V119–V121/V123–V124，合并后 **V118→V124 连续无缺口**。

### 合并状态与安全论证
- 代码已合并本地 `master`（用户决策：当前为开发阶段、不运行代码、后续统一验收）。
- **休眠态（已代码核实）**：`InventoryBalanceFeatureStatusRepository` 仍**只读、无 save/enable 写入路径** → 没有任何途径把 SKU 切到 `BATCH_LOCATION` → 新链路无法激活 → legacy `inventory`/`inventory_batch` 行为完全不变 → **合并对运行时无害**。
- ⚠️ **激活前必须先闭合下方「三、激活安全前置」**，否则触发审查报告 L52（新旧双写互相覆盖）/ L54（首次投影抹掉历史库存）。

### 仍缺失（= 剩余 Task）
- **Critical 2 完整路由**：新链路只是并行记账，**未接管真实扣减**（尤其 POS `outbound` 主路径）。→ 需 **Task5 + Task6**。
- **Critical 3 cutover**：无历史库存初始化、无 featureStatus 写入、无对账。→ 需 **Task10**。

### 代码审查结论（2026-09-12，已逐条对真实代码核实）
对上述修复做了专业 CodeReview 并亲自复核，结论：**方向正确、可安全保留在 master（休眠态运行时无变化）**。要点：
- **Critical 1 / Important 4/5 / Important 6 实现正确**：`move` 按 purpose 约束端点、V124 幂等键 append-only 失败回滚、V123 默认货位唯一约束、`projectInventory` 的 `createIfNotExists` + `SELECT…FOR UPDATE` 配合唯一键 `uk_inventory_store_product(store_id, product_id)` 正确串行化投影。
- **驳回一条误判**：审查曾称“并发首次投影会产生重复快照行”，依据是 `inventory` 唯一键含 `batch_no`、插 NULL 互不相同——**错误**：V6_1 已删除 `inventory.batch_no` 列并把唯一键重建为 `(store_id, product_id)`，并发首插会撞唯一键回滚，无重复行。
- **Important #1（已修，`7392f92`）**：Critical 2 闸门曾遗漏读路径 `getInventoryDetail` 的惰性 `refreshTotalStock`（从 legacy `inventory_batch` 聚合物化快照，未受守卫）→ 已加 `isBatchLocationEnabled` 守卫跳过。**注**：`getInventoryDetail` 的批次列表仍读 legacy，完整的 featureStatus-aware 读路径属 Task5–10。
- **Important #2（记入 Task10 preflight，暂不改代码）**：V123 `ADD UNIQUE KEY` 前未去重，若目标库已存在重复默认货位会中断迁移。**实际风险低**（`inventory_location` 为 V119 新表、唯一创建者 `createBackroom` 休眠）。**处置**：由 Task10 preflight 先校验目标库无重复默认货位；**注意后续 V125 去重脚本无效**（V123 的 ADD UNIQUE KEY 先执行），若真有重复须在 V123 内/前去重（改已提交的 V123 需 `flyway repair`）或先做 FK 重指向再删。

## 二、剩余工作映射（关键澄清）

审查报告用「Critical 2/3」描述遗留风险，落到 codex 原 plan 其实是**未开工的整块 Task**：

| 审查遗留 / 能力 | 对应 Task | 本质 | 触及层 |
|---|---|---|---|
| C2 完整路由：新链路接管销售出库扣减 + 批次证据 | **Task5** | 净新增 | domain/order、application/order、api-admin |
| 批次成本核算毛利 + 历史估算边界 | **Task6** | 净新增 | application/order、report、infrastructure/order |
| 按销售单位统一报价 + 人工改价留痕 | **Task7** | 净新增 | product/price、order、admin-web/posPricing |
| 日期/库存/阈值可解释建议 + 人工决定留痕 | **Task8** | 净新增 | application/inventory、api-admin |
| 管理端作业界面 / 标签 / 扫码 | **Task9** | 净新增 | admin-web (Vue) |
| **C3 cutover**：历史确认 + 灰度切换 + 三层对账 + E2E | **Task10** | 净新增 | scripts、docs/runbooks、api-admin e2e |

## 三、激活安全前置（贯穿全程的红线）

新链路的「激活开关」是 `featureStatus`（per SKU/store 写 `BATCH_LOCATION`）。**在下列两项都就绪前，Task10 绝不允许 enable 任何 SKU：**

1. **Task5/6 已让新链路真正接管销售出库的扣减与成本归属**（否则激活后 POS 仍扣旧 `inventory_batch`、不扣新余额 → 双写背离）。
2. **Task10 的 cutover 已实现历史库存初始化 + 可回滚 + 三层对账**（否则激活后首次 `projectInventory` 用 `sumBaseQuantityByStoreAndProduct` 覆盖快照 → 抹掉仍在 `inventory_batch` 的历史库存）。

本轮已交付的 **C2 安全闸门（`5c9e22b`）是这层保护的前半**：已切换的 SKU 被守卫拦下不走 legacy。**后半是 Task5/6 的新链路接管**。两者合起来才使「激活」安全。

## 四、续作依赖顺序与里程碑

```
Task5 ──> Task6 ──┬──> Task9 ──> Task10(灰度上线,最后)
                  │
   Task7 ─────────┤   (Task7/8 与 Task6 后段可并行)
   Task8 ─────────┘
```

- **强制串行**：Task5 → Task6（成本依赖 allocation）；Task10 必须最后（激活闸门）。
- **可并行**：Task7（价格）、Task8（风险建议）在 Task6 之后可并行推进；Task9（前端）依赖 Task2–8 的后端端点。
- **里程碑**：
  - M1 = Task5+6 完成 → 新链路可接管销售扣减与成本（仍不激活，灰度前）。
  - M2 = Task7+8 完成 → 价格/风险能力齐备。
  - M3 = Task9 完成 → 管理端可操作。
  - M4 = Task10 完成 → 单试点门店可灰度激活并通过对账验收。

## 五、逐 Task 范围拆解（目标 / 关键产出 / 依赖 / 验收 / 风险）

### Task5：POS 扫 SKU、库房取货与批次证据归属
- **目标**：销售出库经新链路产生批次归属证据，POS **不强制**收银员选批次。
- **关键产出**：`OrderItemBatchAllocation`（表已由 V120 建好，仅需 domain model + repo + mapper）、`BatchEvidenceLevel` enum（`SCANNED/FULFILLMENT_CONFIRMED/RULE_ALLOCATED/UNKNOWN`）、`PickupFulfillmentService.createRequest/fulfill`、`OrderApplicationService` 接入两阶段履约。
- **依赖**：Task4 批次余额（已就绪 + 已修端点约束/幂等键）。
- **验收**：混批货架销售可 `UNKNOWN` 且不阻断结账；后仓扫码取货产生 `SCANNED` + 实际批次成本；仅表示 SKU 的 EAN/GS1 **不得**置 `SCANNED`。
- **风险**：`OrderApplicationService` 是核心，改造面大。合并后直接在 master 改，**必须保证 `OrderApplicationServicePaymentFlowTest`/`OrderRefundFlowTest` 等既有订单流程测试不回归**（这些测试当前 `@Mock InventoryApplicationService`，改造后需复核 mock 契约）。

### Task6：以销售批次成本核算毛利，保留历史估算边界
- **目标**：毛利来自 allocation 的不可变批次成本；`UNKNOWN` 归 `ESTIMATED_OR_UNAVAILABLE`，**不并入实际毛利**；`avg_cost_price` 降为浏览缓存。
- **关键产出**：`OrderCostAllocationService.calculate(orderItemId): OrderMarginVO`、报表分列（实际成本毛利 / 估算毛利 / 成本不可得销售额）。
- **依赖**：Task5 的 allocation。
- **验收**：多批次成本求和正确；`UNKNOWN` 不 claim 实际毛利；历史订单保留 `cost_price_snapshot` 显示 `HISTORICAL_ESTIMATE`，不被静默重算。
- **风险**：审查报告 L78——**须先替换 legacy `avg_cost_price` 的所有消费方再 rollout**，否则毛利报表口径断裂。开工前先 grep `avg_cost_price`/`avgCost` 消费点并列清单。

### Task7：按销售单位统一报价 + 人工改价留痕（不自动拒单）
- **目标**：一个按销售单位生效的价格引擎；改价只留痕，参考底价只告警不拒单。
- **关键产出**：`ProductPricingService.quote(PriceContext): PriceQuote`（箱价独立于瓶价）、`ManualPriceAuditService.record(...)`、`admin-web/src/utils/posPricing.ts` 改调服务端 quote。
- **依赖**：`ProductUnitService.requireEnabledUnit(..., SALE)`（Task2 已就绪）。
- **验收**：箱 220 / 瓶 10 各自独立；服务端始终生成 quote、不信任 POS 传入 `unitPrice`；改价记录 quote 版本/建议价/成交价/差额/理由/操作者/确认者。
- **风险**：前端 `posPricing.ts` 去重选择规则；「所有客户端路径迁移并回归前不得删旧 API」。

### Task8：日期/库存/阈值做成可解释建议 + 人工决定记录
- **目标**：只输出可解释建议（含数据窗口/置信度），人工决定只留痕不触发经营动作。
- **关键产出**：`InventoryRiskService.list/recordDecision`、风险类型 `DATE_UNKNOWN/EXPIRING/EXPIRED/LOW_STOCK/OVERSTOCK/LOW_MARGIN/DATA_INSUFFICIENT`；**替换 `SmartThresholdService.loadCurrentStock()` 的空 map stub 为分组余额查询**。
- **依赖**：Task4 货位余额、Task3 收货日期、Task5 allocation。
- **验收**：日期未知显示为 UNKNOWN（非「安全」也非阻断）且不拦 POS；建议用真实当前库存并标注 `INSUFFICIENT_DATA`；`POST /inventory/decisions` 只记录不调用销售/调整/采购/调拨服务。
- **风险**：批量查询余额（勿 per-product N+1）；排除已取消/退款订单。

### Task9：管理端作业界面、标签与渐进式扫码
- **目标**：单位设置 / 收货多批次 / 货位取货 / POS 扫码 的作业界面；标签按 SKU 风险策略可选。
- **关键产出**：`admin-web/src/views/{product/units,purchase/receipt,inventory/locations,inventory/pickup}`、POS 与 warning 页改造、`api/types/router` 配套。
- **依赖**：Task2–8 端点。
- **验收**：`cd admin-web && npm run build` 通过；`node scripts/test_frontend_backend_integration.js` 通过；桌面 POS 从不要求选批次；收货行可加两批各自单位/日期/成本。
- **风险**：纯前端量大；扫码需区分 `SKU` 与 `批次/货位` 标签类型（字段标签 + 解析结果表达，非强制逐件流程）。

### Task10：历史数据确认、灰度切换与端到端验收（= C3 cutover 落点）
- **目标**：受控、可回滚地把单 SKU/门店从 legacy 切到新链路，并三层对账。
- **关键产出**：
  - `scripts/retail-fact-chain-preflight.sql`：**只列不改**——缺确认基准单位的商品、legacy 比例无效、无销售单位的价格行、日期未知批次、聚合/批次不匹配、**重复默认货位（同 store+type 的 code IS NULL 多于 1 条 → V123 会失败，须先人工重指向 `inventory_batch_balance.location_id` 再去重）**。
  - `scripts/retail-fact-chain-backfill-draft.sql`：从旧字段建 `product_unit` **提案**，每个歧义 SKU 留 `PENDING_CONFIRMATION`；**脚本绝不推断历史单位是箱还是瓶**。
  - **featureStatus 写入（当前缺失，本 Task 补）**：`InventoryBalanceFeatureStatusRepository.save/enable`，per SKU/store 授权确认后写 `BATCH_LOCATION` + `cutover_at/cutover_by`（V121 已留列）。
  - `scripts/verify-retail-fact-chain.sh` + `docs/runbooks/retail-fact-chain-rollout.md`：回滚 = 禁用新链操作、退回 legacy adapter、**不删**已记录的收货/移动/归属/审计。
  - 三层对账：每个试点门店日的前后，对比 base-balance 总额 vs legacy 聚合快照。
  - `RetailFactChainEndToEndTest`：箱采购 → 瓶/箱销售 → 前/后仓移动 → scanned/unknown 证据 → 改价留痕 → 日期未知建议 → 无自动阻断。
- **依赖**：Task5–9 全部；**且必须满足「三、激活安全前置」**。
- **验收**：preflight 报告可解释每处歧义；backfill 不自动确认歧义 SKU；enable 后新链路接管、legacy 守卫拦截、对账三层一致；回滚可逆且事实不丢。
- **风险**：这是唯一会**真正改变运行时行为**的 Task（激活开关）。必须最后做、单门店灰度、可随时回滚。

## 六、业务决策点（需你拍板，主要影响 Task10）

1. **历史库存初始化策略**：codex 设计已选定「**人工确认 per SKU**」（backfill 只建提案、歧义留 `PENDING_CONFIRMATION`、脚本不猜箱/瓶）。
   - 采纳此路线？还是额外要一条「按 legacy 比例 1:1 基准近似」的**批量快速通道**（快但丢真实批次成本谱系，且与设计「不伪造追溯」原则冲突）？
   - **建议**：采纳人工确认；如需加速，仅对「无歧义单一单位」的 SKU 提供半自动确认，歧义 SKU 一律人工。
2. **试点范围**：首个灰度门店 / SKU 数量 / 灰度周期。
3. **对账与回滚阈值**：对账频率（建议每营业日）、触发回滚的偏差阈值。

## 七、工程纪律（续作遵守）

- **迁移号**：续作若需新迁移从 **V125** 起（V118–V124 已占）；新增前先 `git worktree list` 核对各活跃分支最高号。
- **SDD 节奏**：每 Task 遵循「先写失败测试 → 实现 → 验证 → commit」，commit 消息沿用现有风格。
- **JDK17 Maven**：`env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw ...`。
- **真实 MySQL 验证**：涉及锁/触发器/生成列的迁移或并发路径，用 `-Dmysql.it=true` 跑 `MysqlRetailFactChainIntegrationTest`（root 无密码、独立库 `agri_retail_it`、自动建删）。
- **不回归既有测试**：Task5/6/7 改 `OrderApplicationService`/`OrderItem`，每步跑 order/aftersales 相关测试。
- **激活闸门**：`featureStatus.enable` 是全链最后一步，必须可回滚。

## 八、验收清单（供你验收本文档 / 后续每 Task）

- [ ] 本文档明确了当前基线、剩余 Task 映射、激活安全前置、依赖顺序、决策点。
- [ ] 你已就「六、业务决策点」给出选择（尤其历史初始化策略）。
- [ ] 认可续作顺序 Task5→6→(7/8)→9→10 与里程碑 M1–M4。
- [ ] 每个 Task 交付时：失败测试先行、聚焦测试 + 相关回归绿、commit 清晰、必要时真实 MySQL 验证。
- [ ] Task10 激活前复核「三、激活安全前置」两条红线均已闭合。
