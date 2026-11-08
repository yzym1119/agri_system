> 文档状态：历史阶段设计/计划，非当前全量事实；按文件日期和提交阅读。被覆盖部分以[统一文档中心](../../current/README.md)为准；未覆盖且仍有效的规则须对照当前代码确认，不直接执行旧迁移/模式切换。

# 农资零售单位、批次、成本与价格事实链 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每个 SKU 以自主选择的库存基准单位运行，并在收货、货位、拣货、销售之间保存可审计的批次、成本、价格与人工决定事实。

**Architecture:** 新事实链采用“商品单位 -> 实际收货批次 -> 批次位置余额 -> 销售行批次归属”的单向模型；原 `inventory_batch`、`inventory` 和主辅单位接口在迁移期继续可读可写。新链路先通过适配器与现有订单、库存、采购服务并行，再以开关按 SKU/门店启用，避免把历史数据或混批货架伪造为精确追溯。

**Tech Stack:** Java 17, Spring Boot 3, Maven multi-module, MyBatis-Flex, Flyway, MySQL, Vue 3/TypeScript, JUnit 5, Mockito。

**Spec:** `docs/superpowers/specs/2026-09-06-retail-unit-batch-cost-price-design.md`

## Global Constraints

- 每个 SKU 自主选择一个库存基准单位；不得写死箱、件、瓶、袋或最小单位。
- 采购、销售、盘点、调拨、退货、报损保留原始单位、原始数量、原始单价和当时换算系数。
- POS 不要求收银员选择批次；只有扫码、货位/批次确认或明确分摊规则能产生批次归属证据。
- `SCANNED`、`FULFILLMENT_CONFIRMED`、`RULE_ALLOCATED`、`UNKNOWN` 必须区分，未知不得伪装为精确追溯。
- 日期、库存、成本、毛利和参考底价仅产生提示和建议；不得自动禁售、报损、调拨、采购或改价。
- 人工改价、风险处置、例外发货与阈值采纳均保存操作者、决定、理由和时间。
- 本期不得改造营销、AI、支付或提成规则；这些模块后续只消费可信的销售、成本、库存和风险事实。
- 新迁移从 `V119` 起；先检查生产库 Flyway 历史和现有迁移 ID，禁止复用 `V115`--`V118` 的菜单 ID。
- 所有 Maven 验证使用 JDK 17：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH`。

---

## 文件结构

| 范围 | 文件 | 职责 |
|---|---|---|
| 迁移 | `core-infrastructure/src/main/resources/db/migration/V119__retail_unit_and_receipt_foundation.sql` | 建立单位、收货、位置、批次位置余额和兼容字段，不切换旧写入。 |
| 迁移 | `V120__retail_sales_cost_price_audit.sql` | 销售批次归属、销售成本快照、定价规则版本和人工决定审计。 |
| 商品单位 | `core-domain/.../product/model/ProductUnit.java`、`ProductUnitRepository.java` | 单 SKU 直接到库存基准单位的单位事实。 |
| 商品单位 | `core-application/.../product/service/ProductUnitService.java` | 单位校验、换算和旧主辅单位适配。 |
| 收货 | `core-domain/.../inventory/model/{InventoryReceipt,InventoryReceiptLine,InventoryReceiptBatch}.java` | 采购计划之外的实际到货事实，支持一行多批、多次收货。 |
| 位置库存 | `core-domain/.../inventory/model/{InventoryLocation,InventoryBatchBalance,InventoryMovement}.java` | 批次主数据、货位余额和位置移动审计。 |
| 销售归属 | `core-domain/.../order/model/OrderItemBatchAllocation.java` | 一销售行多批次成本及证据等级。 |
| 应用服务 | `InventoryApplicationService`、`PurchaseApplicationService`、`OrderApplicationService` | 用新链路处理入库、移动、POS、库房取货，并保留旧接口兼容路径。 |
| 定价 | `ProductPricingService`、`ProductPrice`、`OrderItem` | 统一建议价、人工成交价及不可变审计快照。 |
| API/UI | `ProductController`、`PurchaseController`、`InventoryController`、`OrderAdminController` 与 `admin-web/src/views/{product,purchase,inventory,order}` | 管理商品单位、收货批次、货位、取货任务、风险和改价原因。 |
| 风险 | `SmartThresholdService`、`InventoryWarningController` | 只输出可解释建议，明确数据不足、日期未知和可信度。 |

### Task 1: 建立迁移护栏与可部署数据库基础

**Files:**
- Create: `core-infrastructure/src/main/resources/db/migration/V119__retail_unit_and_receipt_foundation.sql`
- Create: `core-infrastructure/src/main/resources/db/migration/V120__retail_sales_cost_price_audit.sql`
- Create: `core-infrastructure/src/test/java/com/agri_system/infrastructure/migration/RetailFactChainMigrationTest.java`
- Modify: `ddl/schema.sql`
- Modify: `ddl/DATABASE_DESIGN.md`

**Interfaces:**
- Produces: `product_unit`, `inventory_receipt`, `inventory_receipt_line`, `inventory_receipt_batch`, `inventory_location`, `inventory_batch_balance`, `inventory_movement`, `order_item_batch_allocation`, `order_item_price_audit`, `inventory_decision_log`.
- Produces: additive `base_unit_id/base_quantity/unit_cost` snapshot columns; existing `product.main_unit`, `aux_unit`, `main_to_aux_ratio`, `inventory_batch`, `inventory` remain valid during rollout.

- [ ] **Step 1: Write a failing empty-database migration test**

```java
@Test
void retailFactChainMigrationCreatesUniqueUnitAndBatchBalanceConstraints() {
    assertTableExists("product_unit");
    assertTableExists("inventory_batch_balance");
    assertUniqueIndex("product_unit", "uk_product_unit_name", "product_id", "unit_name");
    assertUniqueIndex("inventory_batch_balance", "uk_batch_location", "batch_id", "location_id");
}
```

- [ ] **Step 2: Run the migration test to verify it fails**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-infrastructure -Dtest=RetailFactChainMigrationTest test`

Expected: FAIL because `product_unit` and the migration test support do not exist.

- [ ] **Step 3: Add additive, reversible schema migrations**

Create `product_unit` with `product_id`, `unit_name`, `base_quantity_per_unit DECIMAL(20,8)`, `is_base_unit`, operation flags, `barcode`, `status`, timestamps and a unique `(product_id, unit_name)`. Add a generated/validated uniqueness strategy so exactly one enabled base unit is enforced by application validation plus a database unique index on `(product_id, is_base_unit)` only for base rows (or an equivalent nullable unique key supported by the deployed MySQL version).

Create receipt header/line/batch tables where `inventory_receipt_batch` stores original unit/quantity/unit cost, base quantity/unit cost, supplier batch number, internal receipt batch number, date status `KNOWN|UNKNOWN`, production/expiry dates, inspection reference and receiver. Create `inventory_location` with the four initial types and `inventory_batch_balance` keyed by `(batch_id, location_id)`. Do not delete or reinterpret legacy data in either migration.

Create `order_item_batch_allocation` with allocation base quantity, batch cost, evidence level, allocation rule/version and `source_movement_id`; create `order_item_price_audit` and `inventory_decision_log` as append-only records. Extend `order_item` only with nullable snapshot fields needed for fast reporting, never overwrite historical values.

- [ ] **Step 4: Update the schema reference and run migration verification**

Run the focused migration test, then run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-infrastructure test
```

Expected: PASS; a fresh database reaches V120 and legacy tables/columns remain present.

- [ ] **Step 5: Commit**

```bash
git add core-infrastructure/src/main/resources/db/migration/V119__retail_unit_and_receipt_foundation.sql core-infrastructure/src/main/resources/db/migration/V120__retail_sales_cost_price_audit.sql core-infrastructure/src/test/java/com/agri_system/infrastructure/migration/RetailFactChainMigrationTest.java ddl/schema.sql ddl/DATABASE_DESIGN.md
git commit -m "feat: 增加零售事实链基础表"
```

### Task 2: 实现 SKU 多单位与旧主辅单位兼容适配

**Files:**
- Create: `core-domain/src/main/java/com/agri_system/domain/product/model/ProductUnit.java`
- Create: `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductUnitRepository.java`
- Create: `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductUnitMapper.java`
- Create: `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/repository/ProductUnitRepositoryImpl.java`
- Create: `core-application/src/main/java/com/agri_system/application/product/service/ProductUnitService.java`
- Create: `core-application/src/main/java/com/agri_system/application/product/dto/command/ProductUnitUpsertCommand.java`
- Create: `core-application/src/main/java/com/agri_system/application/product/dto/vo/ProductUnitVO.java`
- Modify: `core-application/src/main/java/com/agri_system/application/product/service/ProductApplicationService.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/ProductController.java`
- Test: `core-application/src/test/java/com/agri_system/application/product/service/ProductUnitServiceTest.java`
- Test: `api-admin/src/test/java/com/agri_system/api/admin/controller/ProductControllerTest.java`

**Interfaces:**
- Produces: `ProductUnitService.requireEnabledUnit(Long productId, String unitName, UnitOperation operation): ProductUnit`.
- Produces: `ProductUnitService.toBaseQuantity(Long productId, String unitName, BigDecimal originalQuantity): UnitConversionSnapshot`.
- Produces: `UnitConversionSnapshot(String unitName, BigDecimal originalQuantity, BigDecimal baseQuantityPerUnit, BigDecimal baseQuantity)`.
- Consumes: `Product.mainUnit/auxUnit/mainToAuxRatio` only when a SKU has not enabled the new model.

- [ ] **Step 1: Write failing domain/application tests**

```java
@Test
void convertsOnePurchaseCaseTo288BaseBottlesWithoutChangingOriginalUnit() {
    when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(base("瓶"), unit("件", "288", true, false)));
    UnitConversionSnapshot snapshot = service.toBaseQuantity(10L, "件", new BigDecimal("1"));
    assertThat(snapshot.baseQuantity()).isEqualByComparingTo("288");
    assertThat(snapshot.unitName()).isEqualTo("件");
}

@Test
void rejectsAUnitDisabledForSaleButDoesNotAssumeWhichUnitIsBase() {
    assertThatThrownBy(() -> service.requireEnabledUnit(10L, "件", UnitOperation.SALE))
        .hasMessageContaining("不允许销售");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=ProductUnitServiceTest test`

Expected: FAIL because `ProductUnitService` and `ProductUnit` do not exist.

- [ ] **Step 3: Implement direct-to-base units and compatibility adapter**

Implement one enabled base row with coefficient `1`; validate positive coefficients, unique names, exactly one base unit, and operation flags. Do not use `ProductUnitConversion` as a new-chain source of truth; retain it for old API compatibility. When a product is still in legacy mode, create a read-only in-memory compatibility projection from `main_unit/aux_unit/main_to_aux_ratio`, label it `LEGACY_UNCONFIRMED`, and do not silently persist it as confirmed unit data.

Expose `GET/PUT /api/admin/products/{productId}/units`; PUT accepts the complete set so server-side validation can reject missing/duplicate base units atomically. Extend product detail VO with `unitModelStatus` and unit list, while existing create/update fields remain accepted.

- [ ] **Step 4: Run focused and controller tests**

Run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-application,api-admin -Dtest=ProductUnitServiceTest,ProductControllerTest test
```

Expected: PASS for conversion, disabled operation, duplicate-base rejection and legacy read compatibility.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/product core-infrastructure/src/main/java/com/agri_system/infrastructure/product core-application/src/main/java/com/agri_system/application/product api-admin/src/main/java/com/agri_system/api/admin/controller/ProductController.java core-application/src/test/java/com/agri_system/application/product/service/ProductUnitServiceTest.java api-admin/src/test/java/com/agri_system/api/admin/controller/ProductControllerTest.java
git commit -m "feat: 支持商品库存基准单位与多单位换算"
```

### Task 3: 用收货事实替代“采购单号即批次号”

**Files:**
- Create: `core-domain/src/main/java/com/agri_system/domain/inventory/model/{InventoryReceipt,InventoryReceiptLine,InventoryReceiptBatch}.java`
- Create: matching repository interfaces under `core-domain/.../inventory/repository/`
- Create: matching MyBatis-Flex mappers/repositories under `core-infrastructure/.../inventory/`
- Create: `core-application/src/main/java/com/agri_system/application/purchase/dto/command/ReceivePurchaseCommand.java`
- Create: `core-application/src/main/java/com/agri_system/application/purchase/dto/command/ReceiptBatchCommand.java`
- Modify: `core-application/src/main/java/com/agri_system/application/purchase/service/PurchaseApplicationService.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/PurchaseController.java`
- Test: `core-application/src/test/java/com/agri_system/application/purchase/service/PurchaseApplicationServiceTest.java`

**Interfaces:**
- Produces: `PurchaseApplicationService.receive(ReceivePurchaseCommand command, Long receiverId): Long`.
- Produces: receipt batch snapshots containing original and base quantities/costs plus date status.
- Consumes: `ProductUnitService.toBaseQuantity`; a receipt line may reference one purchase line and contain many `ReceiptBatchCommand` values.

- [ ] **Step 1: Write failing receipt tests**

```java
@Test
void receivesOnePurchaseLineAsTwoSupplierBatchesWithDifferentDatesAndCosts() {
    Long receiptId = service.receive(commandWithBatches("X", "6", "Y", "4"), 9L);
    assertThat(receiptBatchRepository.findByReceiptId(receiptId)).hasSize(2);
    verify(batchBalanceService).increaseFromReceipt(eq(receiptId), any());
}

@Test
void recordsUnknownDateInsteadOfTreatingMissingExpiryAsSafe() {
    service.receive(commandWithUnknownDates(), 9L);
    assertThat(receiptBatchRepository.findAll().get(0).getDateStatus()).isEqualTo("UNKNOWN");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=PurchaseApplicationServiceTest test`

Expected: FAIL because `receive` and receipt batch commands do not exist.

- [ ] **Step 3: Implement partial and multi-batch receipt**

Add `POST /api/admin/purchases/receipts`. Validate each receipt batch against its purchase line and enabled purchase unit; persist the original values and a conversion snapshot before changing any balance. Allow multiple receipts until purchased quantity is reached, permit a conscious over-receipt only with an explicit exception reason/audit entry, and never build a `Map<productId, InboundItem>`.

Use supplied supplier batch number where available; otherwise generate an internal receipt-batch identifier that is explicitly not a manufacturer batch. Set `dateStatus=UNKNOWN` when dates were omitted. Keep the existing `/inbound` endpoint operational as a legacy adapter that creates one receipt batch only when all information is unambiguous.

Fix moving-average cache calculation by taking the quantity/value snapshot before balance insertion, or compute it from pre-receipt balances in the same transaction. The result is a non-authoritative display cache; no sales margin calculation may depend on it.

- [ ] **Step 4: Run receipt and regression tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=PurchaseApplicationServiceTest test`

Expected: PASS for partial receipt, two real batches for one purchase line, unknown date, retained original unit snapshot and non-double-counted average-cost cache.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/inventory core-infrastructure/src/main/java/com/agri_system/infrastructure/inventory core-application/src/main/java/com/agri_system/application/purchase api-admin/src/main/java/com/agri_system/api/admin/controller/PurchaseController.java core-application/src/test/java/com/agri_system/application/purchase/service/PurchaseApplicationServiceTest.java
git commit -m "feat: 支持采购收货多批次与部分收货"
```

### Task 4: 建立批次加位置余额与移动事实

**Files:**
- Create: `core-domain/src/main/java/com/agri_system/domain/inventory/model/{InventoryLocation,InventoryBatchBalance,InventoryMovement}.java`
- Create: `core-domain/src/main/java/com/agri_system/domain/inventory/service/BatchBalanceService.java`
- Create: location/balance/movement repositories and infrastructure implementations
- Create: `core-application/src/main/java/com/agri_system/application/inventory/dto/command/{InventoryLocationCommand,InventoryMoveCommand}.java`
- Modify: `core-application/src/main/java/com/agri_system/application/inventory/service/InventoryApplicationService.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/InventoryController.java`
- Test: `core-domain/src/test/java/com/agri_system/domain/inventory/service/BatchBalanceServiceTest.java`
- Test: `core-application/src/test/java/com/agri_system/application/inventory/service/InventoryApplicationServiceLocationTest.java`

**Interfaces:**
- Produces: `BatchBalanceService.move(MoveRequest request): InventoryMovement` and `availableBaseQuantity(Long locationId, Long productId): BigDecimal`.
- Produces: location types `SALES_FLOOR`, `STORE_BACKROOM`, `CENTRAL_WAREHOUSE`, `OTHER_STORE`.
- Consumes: receipt batches from Task 3; all movement quantities are base quantities with original-unit snapshots.

- [ ] **Step 1: Write failing balance conservation tests**

```java
@Test
void replenishmentMovesOneKnownBatchFromBackroomToSalesFloorWithoutChangingStoreTotal() {
    movementService.move(move(batchId, backroomId, floorId, "箱", "2"));
    assertThat(balance(batchId, backroomId)).isEqualByComparingTo("24");
    assertThat(balance(batchId, floorId)).isEqualByComparingTo("48");
    assertThat(totalByStore(productId)).isEqualByComparingTo("72");
}

@Test
void rejectsMovingMoreThanTheSourceBatchLocationBalance() {
    assertThatThrownBy(() -> movementService.move(move(batchId, backroomId, floorId, "瓶", "73")))
        .hasMessageContaining("货位库存不足");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-domain,core-application -Dtest=BatchBalanceServiceTest,InventoryApplicationServiceLocationTest test`

Expected: FAIL because batch location balance services do not exist.

- [ ] **Step 3: Implement locked balance movements**

Lock source balance rows with `SELECT ... FOR UPDATE`; deduct source and add destination in one transaction; write one immutable movement row with purpose `REPLENISHMENT|PICK|TRANSFER|COUNT_ADJUSTMENT|RETURN`. A cross-store transfer uses an explicit outbound and inbound movement pair linked by a transfer ID, preserving supplier batch and unit cost. Do not force a concrete shelf code: the four types are sufficient until a store opts into detailed bins.

Keep `inventory` as legacy aggregate snapshot while new-enabled SKU balances are projected to it after movement. Do not make `inventory_batch.quantity` and `inventory_batch_balance` competing sources: record an explicit feature status and designate the new balance as source only after that SKU's cutover.

- [ ] **Step 4: Run movement tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-domain,core-application -Dtest=BatchBalanceServiceTest,InventoryApplicationServiceLocationTest test`

Expected: PASS for replenishment, warehouse pick, cross-store transfer, concurrent source locking and batch/store total conservation.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/inventory core-infrastructure/src/main/java/com/agri_system/infrastructure/inventory core-application/src/main/java/com/agri_system/application/inventory api-admin/src/main/java/com/agri_system/api/admin/controller/InventoryController.java core-domain/src/test/java/com/agri_system/domain/inventory/service/BatchBalanceServiceTest.java core-application/src/test/java/com/agri_system/application/inventory/service/InventoryApplicationServiceLocationTest.java
git commit -m "feat: 增加批次货位余额与库存移动"
```

### Task 5: 实现 POS 扫 SKU、库房取货和批次证据归属

**Files:**
- Create: `core-domain/src/main/java/com/agri_system/domain/order/model/OrderItemBatchAllocation.java`
- Create: `core-domain/src/main/java/com/agri_system/domain/order/enums/BatchEvidenceLevel.java`
- Create: `core-application/src/main/java/com/agri_system/application/order/dto/command/{PickupRequestCommand,PickupFulfillmentCommand}.java`
- Create: `core-application/src/main/java/com/agri_system/application/order/service/PickupFulfillmentService.java`
- Modify: `core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java`
- Modify: `core-application/src/main/java/com/agri_system/application/order/dto/command/OrderItemDTO.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/OrderAdminController.java`
- Test: `core-application/src/test/java/com/agri_system/application/order/service/PickupFulfillmentServiceTest.java`
- Test: `core-application/src/test/java/com/agri_system/application/order/service/OrderApplicationServicePaymentFlowTest.java`

**Interfaces:**
- Produces: `PickupFulfillmentService.createRequest(orderId, itemId, sourceLocationId): Long` and `fulfill(requestId, PickupFulfillmentCommand, operatorId): List<OrderItemBatchAllocation>`.
- Produces: allocation evidence values `SCANNED`, `FULFILLMENT_CONFIRMED`, `RULE_ALLOCATED`, `UNKNOWN`.
- Consumes: Task 4 batch balances; POS accepts SKU barcode/product lookup, not a required batch selector.

- [ ] **Step 1: Write failing sales-allocation tests**

```java
@Test
void directMixedShelfSaleCanRemainUnknownWithoutBlockingCheckout() {
    Long orderId = orderService.createPOSOrder(commandForSkuOnly(), 7L);
    assertThat(allocations.findByOrderId(orderId).get(0).getEvidenceLevel()).isEqualTo(UNKNOWN.name());
}

@Test
void backroomPickByBatchLabelCreatesScannedEvidenceAndActualBatchCost() {
    fulfillmentService.fulfill(requestId, scannedBatch(batchBarcode), 8L);
    assertThat(allocations.findByRequestId(requestId).get(0).getEvidenceLevel()).isEqualTo(SCANNED.name());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=PickupFulfillmentServiceTest,OrderApplicationServicePaymentFlowTest test`

Expected: FAIL because pickup fulfillment and allocation persistence do not exist.

- [ ] **Step 3: Implement two-stage fulfillment without cashier batch selection**

POS resolves barcode to SKU and sale unit. If floor location can immediately fulfill, create allocation according to a configured `RULE_ALLOCATED` rule version or `UNKNOWN`; both allow checkout and are visible on the order. If floor stock is absent or operator chooses warehouse delivery, create a pickup request. The picker scans an internal batch/location label to create `SCANNED`, or explicitly confirms a batch/location to create `FULFILLMENT_CONFIRMED`; this movement supplies actual costs.

EAN/GS1 that only represents SKU must never set `SCANNED`. A received batch may have a printable internal warehouse label, but labels are optional by SKU risk policy and can be affixed to outer cases/totes rather than every retail unit. Preserve manual SKU lookup for unlabeled goods.

- [ ] **Step 4: Run sales-allocation regression tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application,api-admin -Dtest=PickupFulfillmentServiceTest,OrderApplicationServicePaymentFlowTest,PickupTest test`

Expected: PASS for no cashier batch prompt, scanned warehouse pick, confirmed pick, explicitly unknown mixed shelf sale and SKU-only barcode handling.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/order core-application/src/main/java/com/agri_system/application/order api-admin/src/main/java/com/agri_system/api/admin/controller/OrderAdminController.java core-application/src/test/java/com/agri_system/application/order/service/PickupFulfillmentServiceTest.java core-application/src/test/java/com/agri_system/application/order/service/OrderApplicationServicePaymentFlowTest.java
git commit -m "feat: 支持库房取货与销售批次证据"
```

### Task 6: 以销售批次成本计算毛利，并保留历史估算边界

**Files:**
- Create: `core-application/src/main/java/com/agri_system/application/order/service/OrderCostAllocationService.java`
- Create: `core-application/src/main/java/com/agri_system/application/order/dto/vo/OrderMarginVO.java`
- Modify: `core-domain/src/main/java/com/agri_system/domain/order/model/OrderItem.java`
- Modify: `core-infrastructure/src/main/java/com/agri_system/infrastructure/order/mapper/OrderItemMapper.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/ReportSalesController.java`
- Test: `core-application/src/test/java/com/agri_system/application/order/service/OrderCostAllocationServiceTest.java`

**Interfaces:**
- Produces: `OrderCostAllocationService.calculate(Long orderItemId): OrderMarginVO`.
- Produces: actual cost only for scanned/confirmed/rule-allocated batches; `UNKNOWN` returns `costConfidence=ESTIMATED_OR_UNAVAILABLE` and must not be merged into actual margin totals.

- [ ] **Step 1: Write failing cost-confidence tests**

```java
@Test
void marginSumsTwoAllocatedBatchCostsRatherThanProductAverageCost() {
    OrderMarginVO margin = service.calculate(orderItemIdWithTwoAllocations());
    assertThat(margin.actualCost()).isEqualByComparingTo("156.00");
    assertThat(margin.costConfidence()).isEqualTo("ACTUAL");
}

@Test
void unknownBatchDoesNotClaimActualMargin() {
    assertThat(service.calculate(orderItemIdWithUnknownAllocation()).costConfidence())
        .isEqualTo("ESTIMATED_OR_UNAVAILABLE");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=OrderCostAllocationServiceTest test`

Expected: FAIL because allocation-derived costs and confidence fields do not exist.

- [ ] **Step 3: Implement allocation-derived costs and reporting boundary**

Persist per-allocation base quantity and immutable unit cost at fulfillment. Aggregate these values into nullable order-item display snapshots after allocations are final. `avg_cost_price` remains a cache for item browsing only. Reporting shows separate columns/totals for actual-cost margin, estimated-cost margin and cost-unavailable sales; it never labels the latter two as actual margin. Existing orders retain their historical `cost_price_snapshot` and show `HISTORICAL_ESTIMATE` instead of being silently recalculated.

- [ ] **Step 4: Run tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application,api-admin -Dtest=OrderCostAllocationServiceTest test`

Expected: PASS for multi-batch cost, unknown cost confidence and historical-estimate segregation.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/order core-application/src/main/java/com/agri_system/application/order core-infrastructure/src/main/java/com/agri_system/infrastructure/order api-admin/src/main/java/com/agri_system/api/admin/controller/ReportSalesController.java core-application/src/test/java/com/agri_system/application/order/service/OrderCostAllocationServiceTest.java
git commit -m "feat: 基于批次归属核算销售成本与毛利"
```

### Task 7: 收敛为一个按销售单位生效的价格引擎，改价只留痕不自动拒单

**Files:**
- Modify: `core-domain/src/main/java/com/agri_system/domain/product/model/ProductPrice.java`
- Modify: `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductPriceRepository.java`
- Modify: `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductPriceMapper.java`
- Modify: `core-application/src/main/java/com/agri_system/application/product/service/ProductPricingService.java`
- Create: `core-application/src/main/java/com/agri_system/application/product/dto/vo/PriceQuote.java`
- Create: `core-application/src/main/java/com/agri_system/application/order/service/ManualPriceAuditService.java`
- Modify: `core-application/src/main/java/com/agri_system/application/order/service/OrderApplicationService.java`
- Modify: `admin-web/src/utils/posPricing.ts`
- Test: `core-application/src/test/java/com/agri_system/application/product/service/ProductPricingServiceTest.java`
- Test: `core-application/src/test/java/com/agri_system/application/order/service/ManualPriceAuditServiceTest.java`

**Interfaces:**
- Produces: `ProductPricingService.quote(PriceContext context): PriceQuote` where context is `(productId, storeId, channel, customerType, vipLevel, saleUnit, businessDate)`.
- Produces: `ManualPriceAuditService.record(PriceQuote quote, BigDecimal dealPrice, String reason, Long operatorId, Long confirmerId): Long`.
- Consumes: `ProductUnitService.requireEnabledUnit(..., SALE)`; product price rows include `sale_unit`, `channel`, effective range and immutable rule version.

- [ ] **Step 1: Write failing pricing tests**

```java
@Test
void boxPriceCanBe220WhileBottlePriceIs10() {
    assertThat(service.quote(context("箱")).suggestedUnitPrice()).isEqualByComparingTo("220");
    assertThat(service.quote(context("瓶")).suggestedUnitPrice()).isEqualByComparingTo("10");
}

@Test
void manualPriceRecordsQuoteDifferenceAndReasonButReturnsTheChosenDealPrice() {
    assertThat(audit.record(quote("220"), new BigDecimal("205"), "老客户议价", 7L, 7L)).isPositive();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=ProductPricingServiceTest,ManualPriceAuditServiceTest test`

Expected: FAIL because unit/channel price quote and manual-price audit are absent.

- [ ] **Step 3: Implement versioned quote and audit flow**

Replace fallback multiplication in `ProductPricingService` with deterministic selection ordered by exact SKU, store scope, channel, customer scope, sale unit and effective range. A box price is independent from bottle price. The server always creates a quote; it does not trust a POS `unitPrice` as an unrecorded price. When the submitted price differs, persist quote ID/version, suggested price, deal price, difference, reason, operator and confirmer, then use the human-selected deal price. A reference floor produces `BELOW_REFERENCE_PRICE` warning and may require a configured confirmation, but never auto-rejects.

Make `admin-web/src/utils/posPricing.ts` call/display the server quote rather than duplicate selection rules. Remove no API until all client paths have migrated and regression-tested.

- [ ] **Step 4: Run pricing tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=ProductPricingServiceTest,ManualPriceAuditServiceTest test`

Expected: PASS for separate unit prices, one server pricing path, effective-date scope, and auditable manual deal price.

- [ ] **Step 5: Commit**

```bash
git add core-domain/src/main/java/com/agri_system/domain/product core-infrastructure/src/main/java/com/agri_system/infrastructure/product core-application/src/main/java/com/agri_system/application/product core-application/src/main/java/com/agri_system/application/order admin-web/src/utils/posPricing.ts core-application/src/test/java/com/agri_system/application/product/service/ProductPricingServiceTest.java core-application/src/test/java/com/agri_system/application/order/service/ManualPriceAuditServiceTest.java
git commit -m "feat: 统一按销售单位报价并记录人工改价"
```

### Task 8: 将日期、库存和阈值做成可解释建议与人工决定记录

**Files:**
- Create: `core-application/src/main/java/com/agri_system/application/inventory/service/InventoryRiskService.java`
- Create: `core-application/src/main/java/com/agri_system/application/inventory/dto/vo/InventoryRiskVO.java`
- Create: `core-application/src/main/java/com/agri_system/application/inventory/dto/command/InventoryDecisionCommand.java`
- Modify: `core-application/src/main/java/com/agri_system/application/inventory/service/SmartThresholdService.java`
- Modify: `core-infrastructure/src/main/java/com/agri_system/infrastructure/inventory/mapper/InventoryMapper.java`
- Modify: `api-admin/src/main/java/com/agri_system/api/admin/controller/InventoryWarningController.java`
- Test: `core-application/src/test/java/com/agri_system/application/inventory/service/InventoryRiskServiceTest.java`
- Test: `core-application/src/test/java/com/agri_system/application/inventory/service/SmartThresholdServiceTest.java`

**Interfaces:**
- Produces: `InventoryRiskService.list(Long storeId): List<InventoryRiskVO>` and `recordDecision(InventoryDecisionCommand, Long operatorId): Long`.
- Produces: risk kinds `DATE_UNKNOWN`, `EXPIRING`, `EXPIRED`, `LOW_STOCK`, `OVERSTOCK`, `LOW_MARGIN`, `DATA_INSUFFICIENT`.
- Consumes: location balances, receipt dates, allocations and sales history; recommendation output contains observation window and confidence.

- [ ] **Step 1: Write failing risk/threshold tests**

```java
@Test
void dateUnknownIsVisibleAsUnknownRatherThanSafeOrBlocked() {
    assertThat(riskService.list(1L)).extracting(InventoryRiskVO::kind).contains("DATE_UNKNOWN");
    assertThat(orderService.createPOSOrder(commandForUnknownDateSku(), 7L)).isNotNull();
}

@Test
void recommendationUsesRealCurrentStockAndMarksInsufficientHistory() {
    ThresholdRecommendation recommendation = service.calculateRecommendations(1L, 90).get(0);
    assertThat(recommendation.getCurrentStock()).isEqualByComparingTo("24");
    assertThat(recommendation.getConfidence()).isEqualTo("INSUFFICIENT_DATA");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application -Dtest=InventoryRiskServiceTest,SmartThresholdServiceTest test`

Expected: FAIL because risks, decision log and real stock loading are unavailable.

- [ ] **Step 3: Implement advisory-only risks and decisions**

Query balances in batch rather than per product, calculate date state from receipt facts, and expose risk source/count/date rather than an opaque red flag. Replace `SmartThresholdService.loadCurrentStock()` empty-map stub with a grouped balance query. Preserve data-window, cancelled/refunded-order exclusion, observation count and confidence; no threshold is treated as “safe.”

`POST /api/admin/inventory/decisions` records a selected human action such as continue sale, inspect, replenish, transfer, return-to-vendor, write-off, pause replenishment or no action, plus reason and actor. The endpoint records the decision only; it does not invoke sales, adjustment, procurement or transfer services.

- [ ] **Step 4: Run tests**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl core-application,api-admin -Dtest=InventoryRiskServiceTest,SmartThresholdServiceTest,InventoryWarningApplicationServiceTest test`

Expected: PASS for unknown dates, advisory expired/near-expiry state, non-blocking POS, real current stock, data-insufficient recommendations and decision audit.

- [ ] **Step 5: Commit**

```bash
git add core-application/src/main/java/com/agri_system/application/inventory core-infrastructure/src/main/java/com/agri_system/infrastructure/inventory api-admin/src/main/java/com/agri_system/api/admin/controller/InventoryWarningController.java core-application/src/test/java/com/agri_system/application/inventory/service/InventoryRiskServiceTest.java core-application/src/test/java/com/agri_system/application/inventory/service/SmartThresholdServiceTest.java
git commit -m "feat: 提供库存风险建议与人工决策留痕"
```

### Task 9: 管理端作业界面、标签和渐进式扫码投入

**Files:**
- Create: `admin-web/src/views/product/units/index.vue`
- Create: `admin-web/src/views/purchase/receipt/index.vue`
- Create: `admin-web/src/views/inventory/locations/index.vue`
- Create: `admin-web/src/views/inventory/pickup/index.vue`
- Modify: `admin-web/src/views/order/pos/index.vue`
- Modify: `admin-web/src/views/inventory/warning/index.vue`
- Modify: `admin-web/src/api/{product,purchase,inventory,order}.ts`
- Modify: `admin-web/src/types/{product,purchase,inventory,order}.ts`
- Modify: `admin-web/src/router/registry.ts`
- Test: `scripts/test_frontend_backend_integration.js`

**Interfaces:**
- Consumes Task 2--8 endpoints.
- Produces: barcode input that accepts SKU or internal batch/location label, displays parsed fact source, and never opens a cashier batch-selection dialog.

- [ ] **Step 1: Write failing browser/API integration cases**

```javascript
it('POS accepts a SKU scan and completes without requesting a batch choice', async () => {
  await pos.scan('SKU-0001');
  expect(await pos.hasText('请选择批次')).toBe(false);
});

it('receipt line adds two batches with their own units, dates and costs', async () => {
  await receipt.addBatch({ unit: '件', quantity: '6', supplierBatchNo: 'X' });
  await receipt.addBatch({ unit: '件', quantity: '4', supplierBatchNo: 'Y' });
  expect(await receipt.batchRows()).toHaveLength(2);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `node scripts/test_frontend_backend_integration.js`

Expected: FAIL because new pages, APIs and pickup workflow are not exposed.

- [ ] **Step 3: Implement focused operating screens**

Build unit setup as a compact SKU table with operation permissions, direct base ratio and barcode; do not expose arbitrary multi-hop conversion chains. Build receipt lines with expandable batches and an explicit `日期未知` choice. Build location/pick pages for staff to scan or confirm batch/location. POS keeps SKU scan/search first and renders a pickup-needed state for backroom fulfillment.

Provide label printing only for receipt batches/outer cases/totes that a merchant opts to manage. The UI must state label type (`SKU` versus `批次/货位`) through its field label and parsed scan result, not through a mandatory per-piece process. Risk screen shows recommendation confidence and requires reason before saving a decision; it offers no automatic action button.

- [ ] **Step 4: Run frontend checks**

Run:

```bash
cd admin-web && npm run build
cd .. && node scripts/test_frontend_backend_integration.js
```

Expected: PASS; desktop POS never requires batch selection, warehouse picking can create scan evidence, and a unit can be configured for both purchase and sale.

- [ ] **Step 5: Commit**

```bash
git add admin-web/src scripts/test_frontend_backend_integration.js
git commit -m "feat: 增加收货货位取货与多单位作业界面"
```

### Task 10: 历史数据确认、灰度切换和端到端验收

**Files:**
- Create: `scripts/retail-fact-chain-preflight.sql`
- Create: `scripts/retail-fact-chain-backfill-draft.sql`
- Create: `scripts/verify-retail-fact-chain.sh`
- Create: `scripts/test-retail-fact-chain-e2e.py`
- Create: `docs/runbooks/retail-fact-chain-rollout.md`
- Modify: `docs/WORKING_STATE.md`
- Test: `api-admin/src/test/java/com/agri_system/application/order/service/RetailFactChainEndToEndTest.java`

**Interfaces:**
- Consumes all previous tasks.
- Produces: a dry-run confirmation report for unit/date/price ambiguity, per-SKU migration state `LEGACY|PENDING_CONFIRMATION|NEW_CHAIN_ENABLED`, and rollback procedure that disables new writes without deleting factual records.

- [ ] **Step 1: Write failing end-to-end test**

```java
@Test
void casePurchaseBoxSellBottleAndBoxThenBackroomPickRetainsFactsAndMarginConfidence() {
    // Receive 1 case = 288 bottles, move one box to floor, sell one box,
    // create a warehouse pickup for bottles, then assert original/base units,
    // allocation evidence and actual/unknown cost confidence.
    RetailFactChainScenario scenario = fixture.runMixedUnitScenario();
    assertThat(scenario.storeTotalBaseQty()).isEqualByComparingTo("240");
    assertThat(scenario.sales()).allMatch(sale -> sale.hasTraceableEvidenceOrExplicitUnknown());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./mvnw -pl api-admin -Dtest=RetailFactChainEndToEndTest test`

Expected: FAIL until all fact-chain APIs and fixtures are connected.

- [ ] **Step 3: Implement dry-run, confirmation and cutover safeguards**

Preflight must list, not mutate: products lacking a confirmed base unit; legacy ratios invalid for conversion; existing price rows without an identified sale unit; inventory batches with unknown date; and aggregate/batch mismatches. The backfill draft creates `product_unit` proposals from old fields but leaves every ambiguous SKU `PENDING_CONFIRMATION`; no script infers whether a historical unit is a box or bottle.

Enable the new chain only after an authorized confirmation per SKU/store. Preserve original migration source and conversion snapshot. Rollback disables new-chain operations for future documents and returns the SKU to the legacy adapter; it does not delete receipts, movements, allocations or audits already recorded. Reconciliation reports compare base-balance totals with legacy aggregate snapshots before and after each pilot-store day.

- [ ] **Step 4: Run complete verification**

Run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw test -q
node scripts/test_frontend_backend_integration.js
python3 scripts/test-retail-fact-chain-e2e.py
```

Expected: all Maven tests pass under JDK 17; E2E covers box purchase, box/bottle sale, floor/backroom movement, scanned and unknown evidence, manual price audit, date-unknown advisory and no automatic blocking.

- [ ] **Step 5: Commit**

```bash
git add scripts/retail-fact-chain-preflight.sql scripts/retail-fact-chain-backfill-draft.sql scripts/verify-retail-fact-chain.sh scripts/test-retail-fact-chain-e2e.py docs/runbooks/retail-fact-chain-rollout.md docs/WORKING_STATE.md api-admin/src/test/java/com/agri_system/application/order/service/RetailFactChainEndToEndTest.java
git commit -m "docs: 增加零售事实链灰度上线与验收手册"
```

## 计划自检

- 规格覆盖：Task 2 覆盖多单位与旧模型兼容；Task 3 覆盖采购行多批次、部分/多次收货和日期未知；Task 4 覆盖前台/后仓/主仓/跨店位置余额；Task 5 覆盖 SKU 扫码、库房取货与四种证据；Task 6 覆盖批次成本与毛利可信度；Task 7 覆盖按销售单位统一价格和人工改价；Task 8 覆盖非阻断风险与阈值建议；Task 10 覆盖历史迁移、确认、灰度和端到端验收。
- 明确排除：营销、AI、支付、提成不在本次实现内；后续可消费 `order_item_batch_allocation`、`order_item_price_audit`、`inventory_decision_log` 和可信毛利数据，不得反向重写事实。
- 人工决策边界：任何风险、参考底价、日期状态或库存状态均不会在本计划中变成自动停售、自动处置或自动经营动作。
- 占位符扫描：每项测试、实现、验证与提交步骤均给出了具体文件、接口或命令，没有留空的后续事项。
- 类型一致性：单位转换仅通过 `ProductUnitService` 产生 `UnitConversionSnapshot`；收货、移动、销售分配均使用其 `baseQuantity`，销售价格统一通过 `PriceQuote`，人工成交价统一经 `ManualPriceAuditService` 留痕。
