# Task 3 交付报告：采购收货事实、多批次与部分收货

## 修改

- 新增 `InventoryReceipt`、`InventoryReceiptLine`、`InventoryReceiptBatch` 领域实体，以及三组 MyBatis-Flex mapper/repository。模型直接对应 V119 收货事实表，批次保留原始单位/数量/单位成本、基准单位/数量/单位成本、供应商批号、内部收货批次号、日期状态和收货人。
- 新增 `ReceivePurchaseCommand` 与 `ReceiptBatchCommand`。一采购明细行允许多个实际批次，禁止使用按 `productId` 的入库聚合。
- `PurchaseApplicationService.receive` 依次校验采购单状态、采购明细归属、SKU、采购单位启用状态、日期完整性、累计收货基准数量和超收理由；先持久化收货单、收货行、收货批次，再调用可选 `BatchBalanceService` 适配点。
- 超收仅在 `exceptionReason` 非空时允许，并把 `OVER_RECEIPT` 理由写入收货单事实备注；无理由时在任何收货事实写入前拒绝。
- `UNKNOWN` 只代表日期资料缺失，生产日期和有效期同时缺失时保存为 `UNKNOWN`；只提供其中一个日期或日期倒置会拒绝。
- 原 `/inbound` 保留为 legacy adapter，但仅允许单采购明细和单实际批次。它不推断供应商/厂商批号，改为生成显式 `INTERNAL-RECEIPT-*` 内部收货批次号。
- 平均成本缓存暂不由 Task 3 更新。Task 4 尚未提供受事务保护的门店余额事实，继续读取 legacy `inventory_batch` 全门店数量会造成跨门店、重复累计和并发丢失，因此本任务主动禁用该写入。
- 新增/更新 `PurchaseApplicationServiceTest`：双供应商批次、UNKNOWN 日期、1 箱=10 基准单位换算、部分收货累计（首收 6 后再收 4 才完成）、无理由超收拒绝、生产日期/有效期拒绝、事实写入先于余额适配、采购单锁查询及 legacy adapter 行为。

## 设计取舍

- Task 4 的真实批次余额服务尚不存在，因此本任务只定义 `BatchBalanceService` 接口并通过 `ObjectProvider` 按需调用。该接口拿到的只能是已持久化的 `InventoryReceiptBatch`；未配置实现时收货事实仍可独立保存，不伪造余额表写入。
- 新收货要求持久化 SKU 单位，不能把 Task 2 的 `LEGACY_UNCONFIRMED` 内存投影写成外键事实。这样避免把未确认的旧主辅单位解释固化为收货事实。
- 采购订单在所有采购明细累计收到计划基准数量后才更新为已入库；部分收货保持待入库状态。

## 测试

环境：JDK 17，`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-domain,core-infrastructure -am install -DskipTests
```

结果：退出码 `0`，用于刷新本工作区依赖。

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-application -Dtest=PurchaseApplicationServiceTest test
```

结果：退出码 `0`，`PurchaseApplicationServiceTest` 17/17 通过。

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl api-admin -am -DskipTests package
```

结果：退出码 `0`，含新 `/api/admin/purchases/receipts` 路由的管理端模块编译打包通过。`git diff --check` 退出码 `0`。

## 提交

- 代码提交：`5eacea4543049b232389d6f69b1c9afee0c34175`（`fix: 修正收货累计锁与成本缓存边界`）。
- 报告提交：本文件随后单独提交。

## 关切

- 未执行真实数据库迁移后的收货写入或端到端验证；目前验证范围为编译与应用服务单元测试。
- 并发保护已改为 `purchase_order` 的 `SELECT ... FOR UPDATE` 行锁，服务测试验证锁查询先于状态/累计读取；当前测试设施未执行真实 MySQL 并发事务演练，因此“两次并行收 6/计划 10 至多一笔成功”的数据库级证明仍是上线前阻塞项。
- Task 4 尚未提供实际余额实现，因此新收货事实不会在本任务中直接写入 `inventory_batch_balance` 或旧 `inventory_batch`。
- 本任务不更新 `avgCostPrice`。既有订单服务仍使用历史 `Product.avgCostPrice` 建立成本快照，绩效/佣金也存在遗留读取；按范围未修改 POS、出库、价格、营销或绩效。Task 5/6 必须在上线前以批次分配事实替代这些消费者，不能把本任务的展示缓存用于销售毛利或佣金。
- 用户已有的 `api-client/src/main/resources/application-dev.yml` 修改未触碰、未暂存、未提交。
