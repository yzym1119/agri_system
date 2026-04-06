# 采购模块（Purchase Module）交付清单

## 模块概述

采购模块实现了完整的采购管理功能，包括采购单创建、提交审核、采购入库、商品平均成本自动更新等功能。所有接口仅限 ADMIN 和 MANAGER 角色访问。

## 交付文件清单

### 1. Controller层（api-admin模块）

- ✅ `api-admin/src/main/java/com/agri_system/api/admin/controller/PurchaseController.java`
  - 创建采购单接口
  - 提交采购单接口
  - 采购入库接口
  - 查询采购单详情接口
  - 完整的OpenAPI注解
  - Sa-Token鉴权（@SaCheckRole({"ADMIN", "MANAGER"})）

### 2. Application Service层（core-application模块）

- ✅ `core-application/src/main/java/com/agri_system/application/purchase/service/PurchaseApplicationService.java`
  - 业务流程编排
  - 事务管理（所有写操作加@Transactional）
  - DTO转换
  - 采购入库时自动更新商品平均成本
  - 调用库存模块进行入库操作

- ✅ `core-application/src/main/java/com/agri_system/application/purchase/exception/PurchaseErrorCode.java`
  - 错误码定义（purchase-3001 ~ purchase-3005）

#### DTO类

**Command类：**
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseCreateCommand.java`
  - 包含：storeId（必填）、supplierId、supplierName、items（明细列表）、remark
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseItemDTO.java`
  - 包含：productId、qty、price、productionDate、expireDate
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseSubmitCommand.java`
  - 包含：orderId（采购单ID）
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseInboundCommand.java`
  - 包含：orderId（采购单ID）

**VO类：**
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/vo/PurchaseDetailVO.java`
  - 包含采购单主表信息和明细列表
- ✅ `core-application/src/main/java/com/agri_system/application/purchase/dto/vo/PurchaseItemVO.java`
  - 包含采购明细信息

### 3. Domain层（core-domain模块）

**实体类：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/purchase/model/PurchaseOrder.java`
  - 采购订单聚合根
  - 包含业务方法：isPendingSubmit()、isPendingInbound()、isInbound()、isCancelled()
  - 包含状态转换方法：submit()、cancel()
  
- ✅ `core-domain/src/main/java/com/agri_system/domain/purchase/model/PurchaseOrderItem.java`
  - 采购订单明细实体
  - 包含：productId、qty、price、productionDate、expireDate

**领域服务：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/purchase/service/PurchaseDomainService.java`
  - 生成采购单号（generateOrderNo）：规则为 PO + yyyyMMdd + 6位流水号
  - 计算总金额（calculateTotalAmount）：汇总明细金额
  - 状态校验（validateStatus）：校验状态转换是否合法

**仓储接口：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/purchase/repository/PurchaseRepository.java`
  - 采购单仓储接口
  - 包含：save、findById、findByOrderNo、findMaxOrderNoByPrefix
  
- ✅ `core-domain/src/main/java/com/agri_system/domain/purchase/repository/PurchaseItemRepository.java`
  - 采购明细仓储接口
  - 包含：save、saveAll、findByOrderId、deleteByOrderId

### 4. Infrastructure层（core-infrastructure模块）

**Mapper接口：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/mapper/PurchaseMapper.java`
  - MyBatis Mapper接口
  - 包含：findMaxOrderNoByPrefix（查询最大订单号，用于生成流水号）
  
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/mapper/PurchaseItemMapper.java`
  - MyBatis Mapper接口

**Repository实现：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/repository/PurchaseRepositoryImpl.java`
  - 使用MyBatis-Flex实现仓储接口
  
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/repository/PurchaseItemRepositoryImpl.java`
  - 使用MyBatis-Flex实现仓储接口

**扩展功能：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/inventory/repository/InventoryBatchRepositoryImpl.java`
  - 扩展方法：`sumQuantityByProduct(Long productId)` - 查询商品在全门店的总库存数量，用于计算平均成本

**Flyway脚本：**
- ✅ `core-infrastructure/src/main/resources/db/migration/V30__purchase_module.sql`
  - 采购订单主表（purchase_order）
  - 采购订单明细表（purchase_order_item）
  - 索引和外键约束

### 5. 单元测试

- ✅ `core-application/src/test/java/com/agri_system/application/purchase/service/PurchaseApplicationServiceTest.java`
  - 测试采购单创建功能
  - 测试采购单提交功能
  - 测试采购入库后批次数量正确
  - 测试采购入库后商品avg_cost_price正确更新
  - 测试状态机转换

## 功能实现清单

### ✅ 采购单管理

- [x] 创建采购单
  - 自动生成采购单号（PO + yyyyMMdd + 6位流水）
  - 校验门店和商品是否存在
  - 校验数量和价格合法性
  - 自动计算总金额
  - 初始状态为"待提交"（status=1）
  - store_id 必填
  
- [x] 提交采购单
  - 状态从"待提交"（1）转为"待入库"（2）
  - 校验状态合法性
  
- [x] 采购入库
  - 状态从"待入库"（2）转为"已入库"（3）
  - 校验状态合法性（禁止重复入库）
  - 调用库存模块进行入库
  - **批次号 = 采购单号（order_no）**
  - 使用 sourceType="PURCHASE"、sourceId=orderId、sourceLineNo=itemId
  - 自动更新商品平均成本（avg_cost_price）
  
- [x] 查询采购单详情
  - 包含主表信息和明细列表
  - 支持按采购单ID查询

### ✅ 状态机管理

- [x] 状态定义
  - 1-待提交（初始状态）
  - 2-待入库（已提交）
  - 3-已入库（已完成）
  - 0-作废（可取消）
  
- [x] 状态转换规则
  - 待提交 → 待入库（提交操作）
  - 待提交 → 作废（取消操作）
  - 待入库 → 已入库（入库操作）
  - 已入库状态禁止重复入库

### ✅ 商品平均成本更新

- [x] 移动加权平均算法
  - 公式：`avg_cost_price_new = (old_avg_cost_price * old_total_qty + item.qty * item.price) / (old_total_qty + item.qty)`
  - 边界情况：若 old_total_qty = 0，则 avg_cost_price_new = price
  - 精度：保留2位小数，四舍五入
  
- [x] 成本更新时机
  - 采购入库时自动触发
  - 基于商品在全门店的总库存数量计算

### ✅ 库存集成

- [x] 入库操作
  - 调用 InventoryApplicationService.inbound() 方法
  - 传递完整的入库信息（门店、商品、数量、成本价、生产日期、过期日期）
  - 批次号使用采购单号
  - 同源合并标识：sourceType="PURCHASE"、sourceId、sourceLineNo

## 错误码实现

- ✅ purchase-3001：采购单不存在
- ✅ purchase-3002：采购单状态不允许当前操作
- ✅ purchase-3003：采购单已入库，禁止重复入库
- ✅ purchase-3004：商品或门店不存在
- ✅ purchase-3005：数量或价格不合法

## 数据库表

- ✅ `purchase_order`（采购订单主表）
  - 基础字段：id, order_no, store_id, supplier_id, supplier_name, total_amount, status, remark
  - 审计字段：created_at, updated_at, created_by
  - 索引：uk_order_no（唯一索引）, idx_store_id, idx_status, idx_created_at
  - 引用：关联 store 表（store_id），可选关联 supplier 表（supplier_id）
  
- ✅ `purchase_order_item`（采购订单明细表）
  - 基础字段：id, purchase_order_id, product_id, qty, price, production_date, expire_date
  - 审计字段：created_at, updated_at
  - 索引：idx_order_id, idx_product_id
  - 引用：关联 purchase_order 表（purchase_order_id），关联 product 表（product_id）

## 技术规范遵循

- ✅ 遵循DDD-Lite分层规范
- ✅ Controller只负责参数校验、调用应用服务、返回DTO
- ✅ Application Service编排业务流程，不写领域规则
- ✅ Domain层包含核心业务规则（订单号生成、总金额计算、状态校验）
- ✅ Infrastructure层负责数据持久化（MyBatis-Flex）
- ✅ 所有写操作加@Transactional
- ✅ 完整的OpenAPI注解
- ✅ 错误码统一管理
- ✅ Sa-Token鉴权（所有接口仅限 ADMIN 和 MANAGER 访问）
- ✅ 使用Flyway管理数据库迁移

## 关键业务规则

### 采购单号生成规则

- 格式：`PO + yyyyMMdd + 6位流水号`
- 示例：`PO20240101000001`
- 实现：通过查询当天最大订单号，自动递增生成

### 批次号规则

- **批次号 = 采购单号（order_no）**
- 入库时，库存模块的 batchNo 字段使用采购单号
- 保证同一采购单的所有商品使用相同的批次号

### 同源合并标识

- sourceType = "PURCHASE"（固定值）
- sourceId = 采购单ID（orderId）
- sourceLineNo = 明细ID（itemId）
- 用于库存模块的同源合并功能

### 商品平均成本计算

- **算法**：移动加权平均
- **公式**：`新平均成本 = (旧平均成本 × 旧总库存 + 本次数量 × 本次进价) / (旧总库存 + 本次数量)`
- **边界情况**：如果旧总库存为0，则新平均成本 = 本次进价
- **精度**：保留2位小数，四舍五入
- **更新时机**：采购入库时自动更新

### 状态机规则

1. **待提交（1）**：初始状态，可提交或作废
2. **待入库（2）**：已提交，可入库
3. **已入库（3）**：已完成，禁止重复入库
4. **作废（0）**：已取消，不可恢复

## 交付检查

### ✅ 生成的文件路径列表

**Controller层：**
- `api-admin/src/main/java/com/agri_system/api/admin/controller/PurchaseController.java`

**Application层：**
- `core-application/src/main/java/com/agri_system/application/purchase/service/PurchaseApplicationService.java`
- `core-application/src/main/java/com/agri_system/application/purchase/exception/PurchaseErrorCode.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseCreateCommand.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseItemDTO.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseSubmitCommand.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/command/PurchaseInboundCommand.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/vo/PurchaseDetailVO.java`
- `core-application/src/main/java/com/agri_system/application/purchase/dto/vo/PurchaseItemVO.java`

**Domain层：**
- `core-domain/src/main/java/com/agri_system/domain/purchase/model/PurchaseOrder.java`
- `core-domain/src/main/java/com/agri_system/domain/purchase/model/PurchaseOrderItem.java`
- `core-domain/src/main/java/com/agri_system/domain/purchase/service/PurchaseDomainService.java`
- `core-domain/src/main/java/com/agri_system/domain/purchase/repository/PurchaseRepository.java`
- `core-domain/src/main/java/com/agri_system/domain/purchase/repository/PurchaseItemRepository.java`

**Infrastructure层：**
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/mapper/PurchaseMapper.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/mapper/PurchaseItemMapper.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/repository/PurchaseRepositoryImpl.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/purchase/repository/PurchaseItemRepositoryImpl.java`
- `core-infrastructure/src/main/resources/db/migration/V30__purchase_module.sql`

**测试：**
- `core-application/src/test/java/com/agri_system/application/purchase/service/PurchaseApplicationServiceTest.java`

### ✅ 所有DTO是否完整

- ✅ PurchaseCreateCommand（包含storeId、supplierId、supplierName、items、remark）
- ✅ PurchaseItemDTO（包含productId、qty、price、productionDate、expireDate）
- ✅ PurchaseSubmitCommand（包含orderId）
- ✅ PurchaseInboundCommand（包含orderId）
- ✅ PurchaseDetailVO（包含主表信息和明细列表）
- ✅ PurchaseItemVO（包含明细信息）

### ✅ Domain / Application / Controller是否齐全

- ✅ Domain：实体（PurchaseOrder、PurchaseOrderItem）、领域服务（PurchaseDomainService）、仓储接口（PurchaseRepository、PurchaseItemRepository）
- ✅ Application：ApplicationService、DTO、错误码
- ✅ Controller：PurchaseController（包含创建、提交、入库、查询接口）

### ✅ Flyway是否生成

- ✅ 已生成`V30__purchase_module.sql`
- ✅ 包含 purchase_order 和 purchase_order_item 表
- ✅ 字段与数据库设计文档一致
- ✅ 包含必要的索引和外键约束

### ✅ 错误码是否实现

- ✅ purchase-3001：采购单不存在
- ✅ purchase-3002：采购单状态不允许当前操作
- ✅ purchase-3003：采购单已入库，禁止重复入库
- ✅ purchase-3004：商品或门店不存在
- ✅ purchase-3005：数量或价格不合法

### ✅ 关键业务规则是否实现

- ✅ 采购单号自动生成（PO + yyyyMMdd + 6位流水）
- ✅ 批次号 = 采购单号
- ✅ 同源合并标识（sourceType="PURCHASE"、sourceId、sourceLineNo）
- ✅ 商品平均成本自动更新（移动加权平均算法）
- ✅ 状态机完整实现（待提交 → 待入库 → 已入库）
- ✅ store_id 必填校验

## 特殊说明

### 批次号规则

- 采购入库时，批次号（batchNo）固定使用采购单号（order_no）
- 这样设计可以保证同一采购单的所有商品使用相同的批次号
- 便于后续的批次管理和追溯

### 同源合并

- 通过 sourceType="PURCHASE"、sourceId（采购单ID）、sourceLineNo（明细ID）标识采购来源
- 库存模块可以根据这些标识进行同源合并
- 保证同一采购单的同一商品明细在库存中合并显示

### 商品平均成本更新

- 使用移动加权平均算法计算商品平均成本
- 基于商品在全门店的总库存数量计算
- 采购入库时自动触发更新
- 边界情况处理：如果商品当前总库存为0，则直接使用本次进价作为平均成本

### 状态机设计

- 状态转换严格遵循业务规则
- 已入库状态禁止重复入库
- 状态转换通过领域服务进行校验
- 保证数据一致性和业务完整性

### 供应商信息

- supplier_id 和 supplier_name 字段支持供应商模块未开发的情况
- supplier_id 可为空，supplier_name 作为快照保存
- 后续供应商模块开发完成后，可以完善关联关系

## 总结

采购模块已完整实现所有要求的功能，包括：

1. ✅ 采购单的完整生命周期管理（创建 → 提交 → 入库）
2. ✅ 采购单号自动生成（PO + 日期 + 流水号）
3. ✅ 批次号规则（批次号 = 采购单号）
4. ✅ 同源合并标识（sourceType/sourceId/sourceLineNo）
5. ✅ 商品平均成本自动更新（移动加权平均算法）
6. ✅ 状态机完整实现（待提交 → 待入库 → 已入库）
7. ✅ 完整的错误码定义和处理
8. ✅ 符合DDD-Lite架构规范的分层实现
9. ✅ 完整的单元测试覆盖
10. ✅ Sa-Token鉴权集成（仅限 ADMIN 和 MANAGER 访问）
11. ✅ 所有写操作事务管理（@Transactional）
12. ✅ store_id 必填校验

所有代码遵循项目规范，无编译错误，可以直接使用。

---

**生成时间**：2024年
**模块版本**：1.0.0
**状态**：✅ 已完成基础实现，所有功能已交付








