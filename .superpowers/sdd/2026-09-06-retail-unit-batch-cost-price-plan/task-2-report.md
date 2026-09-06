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
