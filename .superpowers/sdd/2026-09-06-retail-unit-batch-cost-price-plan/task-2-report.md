# Task 2 交付报告：SKU 多单位与旧主辅单位兼容

## 变更

- 新增 `ProductUnit`、`ProductUnitRepository` 及 MyBatis-Flex mapper/repository，字段与 V119 `product_unit` 保持一致。
- 新增 `ProductUnitService`：单位启用与采购/销售/库存/调拨权限校验，所有新模型单位直接换算到库存基准单位，完整 PUT 在写入前校验唯一名称、正系数和唯一启用基准单位。
- 新增 `UnitConversionSnapshot`、`ProductUnitUpsertCommand`、`ProductUnitVO`。
- 无启用新单位时，从 `Product.mainUnit/auxUnit/mainToAuxRatio` 生成内存只读 `LEGACY_UNCONFIRMED` 投影，不写入 `product_unit`；新模型不读取 `ProductUnitConversion`。
- 商品详情增加 `unitModelStatus` 和单位列表；新增 `GET/PUT /api/admin/products/{productId}/units`，保留旧 `/unit-conversions` 接口。
- 未修改 `api-client/src/main/resources/application-dev.yml`（该文件原有工作区改动保持不动）。

## 测试

环境：JDK 17（`/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`）。

命令：

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-application,api-admin -Dtest=ProductUnitServiceTest,ProductControllerTest test
```

结果：退出码 `0`；`ProductUnitServiceTest` 4/4 通过，`ProductControllerTest` 3/3 通过。另行运行 `ProductApplicationServiceTest` 2/2 通过；`git diff --check` 退出码 `0`。

首次直接运行原命令时因 Maven 使用本地旧版 `core-domain` jar，新增类型不可见而退出码 `1`；先以 `-am install -DskipTests` 安装本工作区依赖后，原命令如上退出码 `0`。

## 提交

- 代码提交：`b4076c9f870bd38e29cc5c80ee3723de5654fa0b`（`feat: 支持商品库存基准单位与多单位换算`）
- 报告提交：本文件随后单独提交。

## 限制与关切

- 本任务未接入采购收货、位置移动、POS、成本或价格主链路；这些模块仍需调用 `ProductUnitService` 才能完成新链路切换。
- 未做真实数据库写入/E2E 验证；唯一基准单位依赖应用校验与 V119 数据库唯一索引共同保证。
- 旧主辅单位的历史语义存在不一致，兼容投影按 `1 主单位 = ratio 辅单位` 解释，且始终标记为未确认只读数据。

## 修复第1轮

审查指出的 Critical/Important 已逐项处理：

- **单位生命周期与外键**：`replaceUnits` 先读取当前 SKU 集合，带 ID 的请求行严格校验归属并原位更新；缺失的既有行改为停用，绝不 `deleteByProductId`；ID 为空才插入。停用行若以同名且不带 ID 提交，会原位重启用并保留 ID。
- **完整管理 GET**：管理读取改为 `findByProductId`，返回包含停用行的完整持久化集合；仅无新记录时返回 `LEGACY_UNCONFIRMED` 投影，业务换算仍使用 enabled 查询，模型状态按是否存在持久化记录判断。
- **基准系数**：任意 `isBaseUnit=1`（含停用行）均要求系数严格为 1，并继续要求恰好一个启用基准单位。
- **ID 契约**：未知 ID、跨 SKU ID、重复 ID 均在首次写入前拒绝；补充无写入断言。
- **权限兼容**：PUT 改用 V49 已初始化的 `product:unit:create`，未新增迁移权限。
- **测试**：补充原位更新、空 ID 插入、移除即停用保留 ID、停用同名原位重启用、跨 SKU/未知/重复 ID、禁用基准系数、完整 GET、采购/库存/调拨 operation flags；控制器验证服务调用和权限注解。

修复提交：`f805402e70dd2f6c2100a9b404627652bc405929`。

验证命令（JDK17）：

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -pl core-application,api-admin -Dtest=ProductUnitServiceTest,ProductControllerTest test
```

结果：退出码 `0`；服务测试 11/11、控制器测试 4/4 通过。`git diff --check` 退出码 `0`。工作区仍保留用户已有的 `api-client/src/main/resources/application-dev.yml` 改动。
