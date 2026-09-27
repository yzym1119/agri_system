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
2. 按「人工确认 + 无歧义半自动」完成该范围的历史初始化：批次事实 + 货位余额 +
   移动事实（对应 B5 的 `MISMATCH` 修复），使 Σ 批次余额 = Σ legacy 换算基准量。
3. 初始化完成后重跑 preflight：BLOCKER 必须为 0；随后切换到「验证」口径用
   `verify-retail-fact-chain.sh` 对同一范围跑一次 L1，必须 `[OK]`。
4. 注意（换算与投影口径）：全量迁移后的 `inventory` 无 `unit` 列（V6_1 起已删除），
   `inventory.quantity` 按**主单位**记录（见列注释）；激活后投影会把快照改写为
   基准单位量（`projectInventory` 回写 Σ `base_quantity`）。因此 verify 的 L1 口径为：
   - 未启用 SKU：factor = `product_unit` 中 `unit_name = product.main_unit` 且
     `status=1` 的启用行的 `base_quantity_per_unit`；商品无任何单位配置行时按
     legacy 投影语义主单位=基准单位（factor=1）。解析不到记为 `UNRESOLVED` 失败。
   - 已启用 SKU：快照已被投影改写为基准单位，factor 按 1 处理。
   - 历史初始化时须确保 Σ 批次余额 = legacy 存量 × 人工确认的换算系数，否则 L1
     会如实报差异；换算系数的确认属人工初始化步骤，不在本目录脚本内。

## 3. 单 SKU / 门店切换（唯一受控激活途径）

前提（全部满足才可执行）：preflight 该范围 BLOCKER=0；单位配置与历史初始化完成且
L1 干净；legacy 快照行存在（B7）；业务/运营对门店、SKU 集合人工确认并留证。

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
  主单位=基准（factor=1）；已启用 SKU 的快照已被投影改写为基准单位（factor=1）。
  解析不到会以 `UNRESOLVED` 报失败，绝不静默按 0 计。
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
- **任何一层出现非零差异即回滚并留证**（即回滚优先于继续观察）。
- 每次运行输出（stdout/stderr、退出码）归档留证。
- 频率、差异分级与容忍阈值当前均为占位建议：**真实阈值 defer 到上线时由业务与运营
  再次确认**（系统尚未上线，参数不得当作已定策略）。

## 7. 上线前置事项

- `scripts/test_frontend_backend_integration.js` 的 live-run 是**上线前置步骤**
  （Task 9b carry），需要真实前后端环境；本次交付未运行，上线前必须补跑。
- 上线前复核：preflight 全范围 BLOCKER=0、回填提案无遗留 `PENDING_CONFIRMATION`、
  对账频率/阈值已由人工确认并写入本文档（替换占位符）。
