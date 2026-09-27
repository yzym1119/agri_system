# 零售事实链切换 Runbook（单位 / 批次 / 货位 / 成本 / 价格）

> **警示: 系统尚未上线，本文档的参数为占位；激活前必须再次由人工确认门店/SKU/周期。**
>
> 本文档中的 `<store_id>` / `<product_id>` / `<operator_staff_id>` / 门店与 SKU 列表、
> 对账频率与阈值全部为占位示例，不是真实业务决定。真正执行切换前，必须由业务与运营
> 人工再次确认门店、SKU 集合与执行周期，并留证（执行人 / 复核人 / 时间 / 前后查询输出）。

## 适用范围与工具

- 适用对象：`product_unit`（V119）、批次/货位余额（V119/V121/V123/V124）、
  切换状态（V121 `inventory_balance_feature_status`）、单位定价（V126）所构成的
  「零售事实链」。前置条件：目标库已执行 Flyway V119+ / V121+ 等全部迁移。
- 本目录三个操作工具（**全部只读 / 提案型**，不含任何 DDL 与写操作）：
  1. `scripts/retail-fact-chain-preflight.sql` — 切换前检查（只列不改，7 个检查块）。
  2. `scripts/retail-fact-chain-backfill-draft.sql` — 基准单位回填提案（只报告，
     不推断箱/瓶与换算方向；`apply_sql` 由人工复核后自行执行）。
  3. `scripts/verify-retail-fact-chain.sh` — 三层对账（L1/L2/L3，只读 SELECT）。
- 参数写法：两个 SQL 文件通过文件开头的 `SET @store_ids / @product_ids / @tolerance`
  会话变量限定范围（空字符串 = 覆盖所有行）；执行前按当前拟切换范围修改这几行，
  或在执行时按需覆写。`verify` 脚本用命令行参数限定范围。

```bash
# 示例（占位库名/主机，按实际环境替换）
mysql -h127.0.0.1 -uroot -t <数据库名> < scripts/retail-fact-chain-preflight.sql
mysql -h127.0.0.1 -uroot -t <数据库名> < scripts/retail-fact-chain-backfill-draft.sql
scripts/verify-retail-fact-chain.sh --host 127.0.0.1 --port 3306 --user root --password "" \
    --database <数据库名> --stores '<门店CSV>' --products '<SKU CSV>' --tolerance 0
```

## 1. 前置检查（preflight）

对拟切换范围执行 `scripts/retail-fact-chain-preflight.sql`（建议先用 `@store_ids` /
`@product_ids` 限定到该范围）。输出 7 个检查块，`severity` 语义：

| 块 | 检查 | severity | 处置 |
| --- | --- | --- | --- |
| B1 | 缺确认基准单位（`product_unit.status=1 AND is_base_unit=1` 不存在） | BLOCKER | 先在单位设置页配置基准单位 |
| B2 | legacy 主辅单位无效（有 `aux_unit` 但比例缺失/非正，或辅=主同名） | WARN | 人工确认 legacy 主辅换算语义 |
| B3 | 无销售单位的价格行（`sale_unit` 无启用可销售单位） | BLOCKER / WARN | BLOCKER：补录单位或修正价格行；WARN：确认 `sale_unit IS NULL` 行语义（V126 允许） |
| B4 | 日期未知批次（`date_status='UNKNOWN'`） | LIST | 设计允许；运营知情、向收货人复核，不自动阻断 |
| B5 | 聚合/批次不匹配（Σ 批次余额 vs Σ legacy×factor） | BLOCKER / WARN | 按 `status` 处置：`UNRESOLVED`（主单位→基准换算无法解析，先补/修单位配置）→ `MISSING_LEGACY`（补 legacy 快照行）→ `MISMATCH`（完成历史初始化后重跑） |
| B6 | 重复默认货位（同门店同类型 `code IS NULL` 多于 1 条） | BLOCKER | 先把 `inventory_batch_balance.location_id` 人工重指向保留货位，再删/改多余行 |
| B7 | 缺 legacy 库存快照行 | WARN | 激活前人工确认存量并补建 legacy 快照行（数量 = 主单位存量，可为 0）并留证 |

判定标准：**所有 BLOCKER 清零后才允许进入下一步**；WARN / LIST 必须逐条人工确认并留证。
（B7 的成因：该门店/SKU 在 legacy 账本中没有任何存量基线 —— 若历史初始化按真实库存
建了批次余额，B5/L1 会呈 `MISSING_LEGACY` 差异；且回滚/审计缺少对比基线。属既有
legacy 遗留缺口。注：全量迁移后的库中 `inventory` 无 `unit` 列（V6_1 起已删除），
激活后首次投影会自动补建快照行（数量 0 → 回写基准聚合），不会因缺列而失败。）

## 2. 历史初始化

1. 先用 `scripts/retail-fact-chain-backfill-draft.sql` 生成基准单位回填提案（范围 =
   `@product_ids`，可按 `@store_ids` 进一步限定），逐行复核 `proposal_status`：
   - `AUTO_READY`：单单位商品（无辅单位、无换算比例）且尚无 `product_unit` 配置；
     复核 `apply_sql`（一条现成 `INSERT INTO product_unit ...`）后人工执行。执行后重跑
     提案，该商品应变为 `SKIP_ALREADY_CONFIGURED`。
   - `SKIP_ALREADY_CONFIGURED`：已有单位配置（可能来自单位设置页），仅确认不重复录入。
   - `PENDING_CONFIRMATION`：存在辅单位/换算比例证据、比例非正或主单位空白；
     **必须在单位设置页人工录入**。脚本不会推断箱/瓶或任何换算方向，禁止照抄猜测换算。
2. 按「人工确认 + 无歧义半自动」完成该范围的历史初始化：批次事实 + 后仓余额
   （批次 `base_quantity` 与后仓余额相等），**不补写移动事实**（补写会造成 L3 双计），
   使 Σ 批次余额 = Σ legacy 换算基准量（对应 B5 的 `MISMATCH` 修复）。
3. 初始化完成后重跑 preflight：BLOCKER 必须为 0；随后切换到「验证」口径用
   `verify-retail-fact-chain.sh` 对同一范围跑一次 L1，必须 `[OK]`。
4. 注意（换算与投影口径）：全量迁移后的 `inventory` 无 `unit` 列（V6_1 起已删除），
   `inventory.quantity` 按**主单位**记录（见列注释）；激活后投影**经一次新链写入触发**会把快照改写为
   基准单位量（`projectInventory` 回写 Σ `base_quantity`）。因此 verify 的 L1 口径为：
   - 未启用 SKU：factor = `product_unit` 中 `unit_name = product.main_unit` 且
     `status=1` 的启用行的 `base_quantity_per_unit`；商品无任何单位配置行时按
     legacy 投影语义主单位=基准单位（factor=1）。解析不到记为 `UNRESOLVED` 失败。
   - 已启用 SKU：快照已被投影改写为基准单位**（投影完成后）**，factor 按 1 处理。
   - 历史初始化时须确保 Σ 批次余额 = legacy 存量 × 人工确认的换算系数，否则 L1
     会如实报差异；换算系数的确认属人工初始化步骤，不在本目录脚本内。

## 3. 单 SKU / 门店切换（唯一受控激活途径）

前提（全部满足才可执行）：preflight 该范围 BLOCKER=0；单位配置与历史初始化完成且
L1 干净；legacy 快照行存在（B7）；业务/运营对门店、SKU 集合人工确认并留证；
**该 SKU 不依赖未路由的 legacy 链路**（小程序支付回调 / 退款回库 / 售后回库，见 §7 ——
路由完成前激活会使这些业务硬失败）。

> **唯一受控的 `enable` 途径**：应用内的激活写路径已在 Task 10a（commit `950ebff`，
> `InventoryBalanceFeatureStatusRepository.enable` + 实现）落地，但**没有任何
> route/controller 绑定它**，不得通过任何 HTTP 接口或前端页面触发激活。
> 唯一被认可的激活/回滚途径是本节给出的受控 SQL 模板（等价于 10a 的
> `save`/`enable` 语义，由人工在目标库执行并留证）。

激活模板（占位符 `<...>` 必须逐条替换为人工确认后的真实值）：

```sql
-- 激活(等价于 10a enable): 按 (store_id, product_id) upsert 为 BATCH_LOCATION
-- 幂等: 重复执行会刷新 cutover_at/cutover_by (与 10a 文档一致)
-- <operator_staff_id> 必须为已存在的 staff.id (FK 约束), 且须与复核人分离
INSERT INTO inventory_balance_feature_status
    (store_id, product_id, balance_mode, cutover_at, cutover_by)
VALUES
    (<store_id>, <product_id>, 'BATCH_LOCATION', NOW(), <operator_staff_id>)
ON DUPLICATE KEY UPDATE
    balance_mode = 'BATCH_LOCATION',
    cutover_at   = NOW(),
    cutover_by   = <operator_staff_id>;
```

- 多 SKU / 单门店批量切换：在 `VALUES` 中逐行**显式列举**已人工确认的
  `(store_id, product_id, ...)` 组合，逐 SKU 复核；禁止用 `WHERE` 通配一次性批量激活。
- 执行前先查询留证：`SELECT * FROM inventory_balance_feature_status WHERE ...;`
- 执行后立即确认：`balance_mode='BATCH_LOCATION'` 且 `cutover_at`/`cutover_by` 已写入。
- 留证：执行人 / 复核人 / 时间 / 前后查询输出，一并归档。

## 4. 切换后验证

**投影前提与顺序**（激活 ≠ 投影完成，本节验证须按序执行）：

- 前提：`inventory.quantity` 快照在首次投影之前仍是 **legacy 主单位**口径；§3 的
  纯 SQL 激活**不会**触发投影 —— `projectInventory` 只由新链写入触发
  （`move` / `increaseFromReceipt` 两条路径），激活本身不产生任何事实。
- 对 base≠main 的 SKU（例：主单位箱、基准单位瓶），激活后立即跑本节验证会出现
  L1/L2 `[FAIL]`：这是**未投影窗口的预期形态，不是数据差异，不触发 §6 回滚**。
- 正确顺序：§3 激活 → 对该 SKU 执行**一次受控新链操作**（真实业务留痕的最小
  `move` 或收货）→ 确认快照已被改写为基准单位（`inventory.quantity` 等于
  Σ`inventory_batch_balance.base_quantity`）→ 再跑本节基线验证；此后（含 §6
  日常对账）任何 `[FAIL]` 均为真实差异，按 §6 回滚。
- 若窗口内无法立即安排受控新链操作：**不得把窗口内 L1/L2 FAIL 当作验收结论**；
  须待首次新链写入完成投影后重验，并在留证中注明「快照尚未投影（已知例外）」。

对刚切换的范围跑三层对账（只读）：

```bash
scripts/verify-retail-fact-chain.sh --host 127.0.0.1 --port 3306 --user root --password "" \
    --database <数据库名> --stores '<门店CSV>' --products '<SKU CSV>' --tolerance 0
```

- 期望结果：`L1` / `L2` / `L3` 均为 `[OK]`，退出码 0。
- `L2` 的 `[N/A] ... (LEGACY)` 行表示该 SKU 尚未启用新链，**不计失败**；
  若范围里混有未切换 SKU，属正常信息行。
- 任何层出现 `[FAIL]`（超出容差的差异）→ 按 §6 回滚并留证。
- L1 换算口径（真实 schema，与 preflight B5 同源）：未启用 SKU 按 `product_unit`
  （`unit_name = product.main_unit` 且 `status=1`）的 `base_quantity_per_unit`
  把主单位存量换算为基准单位；商品无任何单位配置行时按 legacy 投影语义
  主单位=基准（factor=1）；已启用 SKU 的快照**在投影完成后**才被改写为基准单位
  （factor=1，投影前提见本节「投影前提与顺序」）。
  解析不到会以 `UNRESOLVED` 报失败，绝不静默按 0 计。
- L3 事实重建口径（R9 修正）：`receipt_term` 按 (batch, location) 定义 —— 当且仅当该
  location 是该批次门店**启用的 `STORE_BACKROOM`**（`location_type='STORE_BACKROOM'
  AND status=1`）时，取 `inventory_receipt_batch.base_quantity`，否则为 0；L3 等式为
  `balance == receipt_term + net_movements`（`net_movements` = `to_location_id=loc` 加 /
  `from_location_id=loc` 减）。收货不补写 movement，补写会与收货项双计。运营约定：
  **每个门店只允许一个启用的 `STORE_BACKROOM`**（多启用会破坏记账货位映射）。
- 数据级验收证据（上线前执行）：10c 的 `RetailFactChainEndToEndTest`
  （`api-admin`，真实 MySQL，`-Dmysql.it=true` 门控）覆盖切换、守卫、L1–L3 对账与回滚；
  本 runbook 只负责运营侧对账。

## 5. 回滚

触发条件：任一层对账出现非零差异；或业务/运营要求终止新链；或出现数据损坏迹象。

1. **停用新链操作**：停止该（批）SKU 走新链的作业入口，避免继续产生事实。
2. 执行受控回滚 SQL（同一受控途径，占位符同 §3）：

   ```sql
   -- 回滚: 仅改 balance_mode, 保留 cutover_at/cutover_by 切换留痕
   -- updated_at 会自动记录回滚时间
   UPDATE inventory_balance_feature_status
   SET balance_mode = 'LEGACY'
   WHERE store_id = <store_id> AND product_id = <product_id>;
   ```

3. 确认：`SELECT` 该行 `balance_mode='LEGACY'` 且 `cutover_at`/`cutover_by` 仍在；
   应用守卫（`isBatchLocationEnabled`）随之解除，legacy adapter 恢复接管。
4. **绝不删除**已记录的收货 / 移动 / 归属 / 审计事实（`inventory_receipt*`、
   `inventory_batch_balance`、`inventory_movement`、批次归属与价格审计等全部保留），
   它们只用于复盘与审计，不因回滚而清理。
5. 留证：回滚触发原因、对账输出、执行的 SQL、执行人 / 复核人 / 时间。
6. 回滚后该 SKU 变为 `LEGACY`：L2 对其输出 `N/A (LEGACY)`；其新链余额/移动不再变化，
   仅供审计与复盘。日常对账范围须剔除已回滚 SKU（用 `--products` 显式限定仍启用的
   SKU），否则 L1 会如实报告其新旧账本不再同步的差异。

## 6. 对账节奏（建议默认，参数化）

- 建议默认：**每营业日营业前后各跑一次 `verify-retail-fact-chain.sh`**
  （范围 = 当前已启用的 SKU；`--tolerance` 默认 0）。
- **任何一层出现非零差异即回滚并留证**（即回滚优先于继续观察）；该触发适用于
  **投影完成后**的验证与日常对账，§4 定义的未投影窗口内 L1/L2 差异不作为回滚
  触发（仍须尽快完成一次新链操作完成投影并复验）。
- 每次运行输出（stdout/stderr、退出码）归档留证。
- 频率、差异分级与容忍阈值当前均为占位建议：**真实阈值 defer 到上线时由业务与运营
  再次确认**（系统尚未上线，参数不得当作已定策略）。

## 7. 上线前置事项

- `scripts/test_frontend_backend_integration.js` 的 live-run 是**上线前置步骤**
  （Task 9b carry），需要真实前后端环境；本次交付未运行，上线前必须补跑。
- 上线前复核：preflight 全范围 BLOCKER=0、回填提案无遗留 `PENDING_CONFIRMATION`、
  对账频率/阈值已由人工确认并写入本文档（替换占位符）。
- **未路由 legacy 链路披露（最终审查 Final-I1）——路由完成前不得激活会走这些链路的 SKU**：
  以下生产链路仍直接调用 legacy `inventoryService.inbound/outbound`，对已激活 SKU 会触发
  切换守卫（`assertNotCutOver`）硬失败，使对应业务不可用（守卫为 fail-safe：拒绝执行且
  事务回滚，不会产生脏写）：
  1. 小程序支付回调出库 `OrderApplicationService.finalizeClientOrderPayment`
     （`core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java:1351`）
  2. 整单退款回库 `OrderApplicationService.restockOrderItemsForRefund`（同文件 :1375）
  3. 部分退款回库 `OrderApplicationService.restockOrderItemsForRefund`（同文件 :1404）
  4. 售后审核回库 `AfterSalesApplicationService.triggerRestock`
     （`core-application/src/main/java/com/agri_system/application/after_sales/service/AfterSalesApplicationService.java:465`）

  因此激活范围必须排除会走上述链路的 SKU（如支持小程序支付 / 退款 / 售后的 SKU），或明确
  接受对应业务在该 SKU 上不可用；把这些链路路由到新链（批次归属）需先补业务决策（退款 /
  售后退回批次的分配规则），属后续计划。
- **非超管角色授权（最终审查 M2）**：V127 新菜单（2703 单位设置 / 2704 收货 / 2705 货位 /
  2706 取货）仅默认授予 SUPER_ADMIN（role 1）；其他角色需由管理员在角色管理中按需授权，
  否则菜单不可见或接口 403。各页所需权限码：
  - 单位设置：菜单 `product:unit:query`，保存另需 `product:unit:create`
  - 收货：`purchase:inbound`
  - 货位：`inventory:view`
  - 取货：`order:pickup:verify`
