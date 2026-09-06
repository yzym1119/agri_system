# Task 4 交付报告：批次货位余额与库存移动

## 实现提交

- SHA: `62e54c257b3a026fc043d8459597d2712840908e`
- 提交信息: `feat: 增加批次货位余额与库存移动`

## 变更

- 将 Task3 的 application 临时 `BatchBalanceService` 收敛为 domain 层服务，收货批次持久化后通过该服务建立余额；不存在双接口或 application-to-domain 循环依赖。
- 新增位置、批次位置余额、不可变移动事实和仓储实现。源余额查询使用真实 `SELECT ... FOR UPDATE`，源扣减、目的增加、移动追加写和库存投影均由 application 的事务边界包裹。
- `inventory_balance_feature_status` 明确每个门店 SKU 的权威模式。只有 `BATCH_LOCATION` 显式切换后才建立/使用新余额；余额行的存在不会触发切换。切换后的 `inventory` 仅为余额投影，旧 SKU 仍由旧链路负责。
- 所有移动记录原始单位 ID/名称/数量和基准数量。application 层使用 `ProductUnitService` 的启用单位校验和换算快照，未写死任何箱、瓶、件规则。
- 位置类型限定为 `SALES_FLOOR`、`STORE_BACKROOM`、`CENTRAL_WAREHOUSE`、`OTHER_STORE`；具体位置编码允许为空。
- 同店补货保持批次和门店总量守恒。跨店调拨要求两个门店的已收货同谱系批次，拆为同一 `transferId` 的 outbound/inbound 两个移动事实，并校验 SKU、供应商批号和基准单位成本。
- `COUNT_ADJUSTMENT`、`RETURN` 必须有说明并关联非收货业务单据，避免用这两个目的静默覆盖收货事实。
- V121 增加切换状态表、调拨索引和移动追加写约束；同步更新 `ddl/schema.sql` 和迁移事实链测试。

## 关键取舍

- V119 的 `inventory_receipt_batch` 是新批次余额的谱系根，不读取或改写 `inventory_batch.quantity`。因此不会让 `inventory_batch` 和 `inventory_batch_balance` 在新 SKU 上并列为真相。
- 跨店目的端必须使用目的门店的收货批次，不能把源门店批次直接改到目的门店，借此保持 V119 的 `(batch_id, product_id, store_id)` 外键谱系。
- 旧 POS、销售批次选择、价格、营销和自动库存策略没有改动；本任务只暴露位置创建和批次位置移动 API。

## 测试证据

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  ./mvnw -pl core-domain,core-application \
  -Dtest=BatchBalanceServiceTest,InventoryApplicationServiceLocationTest test
```

- 结果：成功。领域测试 3 个、应用测试 1 个，失败数 0。

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  ./mvnw -pl core-infrastructure -am \
  -Dtest=RetailFactChainMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- 结果：成功。迁移事实链测试 11 个，验证 V118 -> V121、跨 SKU/跨门店外键与 schema 对齐。

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  ./mvnw -pl api-admin -am -DskipTests compile
```

- 结果：成功。

## 局限

- 未在真实 MySQL 8.2 实例上执行多事务并发压测；实现和 mapper 已使用数据库行锁，H2 迁移测试只验证了 SQL 迁移与约束。
- 尚未提供 SKU 切换状态的管理端写接口；切换必须由受控数据迁移/运维流程显式写入 `inventory_balance_feature_status`，这符合不根据余额行自动切换的约束。
