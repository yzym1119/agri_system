# 数据库设计文件说明

## 文件清单

### 1. 完整 DDL 文件
- **`schema.sql`**: 完整的数据库 DDL 脚本，包含所有表结构、索引、外键约束
  - 用途：一次性创建整个数据库结构
  - 使用场景：初始化新数据库、数据库重建

### 2. Flyway 迁移脚本
按表拆分，按版本顺序执行：

- **`V1__store.sql`**: 门店表
- **`V2__staff.sql`**: 员工表
- **`V3__customer.sql`**: 客户表
- **`V4__product.sql`**: 商品表及相关表（商品表、单位换算表、套餐表、套餐明细表）
- **`V5__inventory.sql`**: 库存表及相关表（库存表、调拨表、调整表、流水表、预警表）
- **`V6__order.sql`**: 订单表及相关表（订单表、订单明细表、配送表）
- **`V7__price.sql`**: 价格体系相关表（商品价格表、提成规则表）
- **`V8__wallet.sql`**: 客户钱包相关表（钱包表、钱包交易记录表）
- **`V9__credit.sql`**: 赊销相关表（赊销台账表、对账历史表）
- **`V10__performance.sql`**: 员工绩效相关表（绩效记录表）

**位置**: `core-infrastructure/src/main/resources/db/migration/`

### 3. ER 图文件
- **`er-diagram.md`**: Mermaid 格式的 ER 图
  - 包含完整的表关系图
  - 包含所有枚举值说明
  - 可在支持 Mermaid 的 Markdown 查看器中查看

- **`er-diagram.puml`**: PlantUML 格式的 ER 图
  - 可在 PlantUML 工具中生成图片
  - 支持更详细的表结构展示

### 4. 设计文档
- **`DATABASE_DESIGN.md`**: 完整的数据库设计文档
  - 表结构详细说明
  - 索引设计原则
  - 外键关系说明
  - 性能优化建议
  - 数据迁移说明

## 使用说明

### 方式一：使用完整 DDL（推荐用于初始化）

```bash
mysql -u root -p agri_system < ddl/schema.sql
```

### 方式二：使用 Flyway 迁移脚本（推荐用于生产环境）

Flyway 会自动按版本顺序执行迁移脚本，确保数据库版本可控。

配置已在 `application.yml` 中：
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

## 表结构概览

### 基础数据表（3张）
1. `store` - 门店表
2. `staff` - 员工表
3. `customer` - 客户表

### 商品相关表（4张）
4. `product` - 商品表
5. `product_unit_conversion` - 商品单位换算表
6. `product_package` - 套餐/配方表
7. `product_package_item` - 套餐明细表

### 库存相关表（5张）
8. `inventory` - 库存表
9. `inventory_transfer` - 库存调拨表
10. `inventory_adjustment` - 库存调整表
11. `inventory_transaction` - 库存流水表
12. `inventory_warning` - 库存预警表

### 订单相关表（3张）
13. `order` - 订单表
14. `order_item` - 订单明细表
15. `order_delivery` - 配送表

### 价格体系相关表（2张）
16. `product_price` - 商品价格表
17. `commission_rule` - 提成规则表

### 客户钱包相关表（2张）
18. `customer_wallet` - 客户钱包表
19. `wallet_transaction` - 钱包交易记录表

### 赊销相关表（2张）
20. `credit_account` - 赊销台账表
21. `credit_statement` - 对账历史表

### 员工绩效相关表（1张）
22. `staff_performance` - 员工绩效记录表

**总计：22张表**

## 设计特点

1. **多门店支持**：所有库存、订单按门店隔离
2. **完整的业务闭环**：从商品到订单到绩效
3. **灵活的价格体系**：支持全局、门店、客户类型、VIP等级差异化定价
4. **三钱包系统**：本金、专用金、积分独立管理
5. **完整的库存管理**：调拨、调整、流水、预警
6. **赊销管理**：台账、对账历史
7. **绩效管理**：推荐行为、服务行为

## 注意事项

1. **外键约束**：`store.manager_id` 没有外键约束，避免与 `staff.store_id` 的循环依赖
2. **乐观锁**：`inventory` 和 `customer_wallet` 表使用 `version` 字段实现乐观锁
3. **唯一约束**：业务编码字段（如 `code`, `order_no`）都有唯一约束
4. **索引策略**：外键字段、常用查询字段都创建了索引

## 后续扩展

如需新增表或字段，请：
1. 明确业务需求
2. 设计表结构和关系
3. 创建新的 Flyway 迁移脚本（V11__xxx.sql）
4. 更新本文档和 ER 图











