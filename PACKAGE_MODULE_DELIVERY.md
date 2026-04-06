# 套餐模块（Product Package Module）交付清单

## 模块概述

套餐模块实现了完整的套餐管理功能，包括套餐主档、套餐明细管理、上下架操作，以及提供给订单模块的销售支撑能力。

## 交付文件清单

### 1. Controller层（api-admin模块）

- ✅ `api-admin/src/main/java/com/agri_system/api/admin/controller/ProductPackageController.java`
  - 套餐CRUD接口
  - 上下架操作接口
  - 完整的OpenAPI注解

### 2. Application Service层（core-application模块）

- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/service/ProductPackageApplicationService.java`
  - 业务流程编排
  - 事务管理
  - DTO转换
  - 提供给订单模块的getPackageItems方法

- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/exception/PackageErrorCode.java`
  - 错误码定义（package-1001 ~ package-1005）

#### DTO类

**Command类：**
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/ProductPackageCreateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/ProductPackageUpdateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/PackageItemDTO.java`

**Query类：**
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/query/ProductPackageQuery.java`

**VO类：**
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/vo/ProductPackageDetailVO.java`
- ✅ `core-application/src/main/java/com/agri_system/application/productpackage/dto/vo/ProductPackageItemVO.java`

### 3. Domain层（core-domain模块）

**实体类：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/productpackage/model/ProductPackage.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/productpackage/model/ProductPackageItem.java`

**领域服务：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/productpackage/service/ProductPackageDomainService.java`
  - 校验重复商品
  - 校验商品有效性
  - 上下架逻辑校验
  - 生成套餐编码（使用统一编码生成器）

**仓储接口：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/productpackage/repository/ProductPackageRepository.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/productpackage/repository/ProductPackageItemRepository.java`
  - 必须实现：`List<ProductPackageItem> findItemsByPackageId(Long packageId);`

### 4. Infrastructure层（core-infrastructure模块）

**Mapper接口：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/mapper/ProductPackageMapper.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/mapper/ProductPackageItemMapper.java`

**Repository实现：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/repository/ProductPackageRepositoryImpl.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/repository/ProductPackageItemRepositoryImpl.java`
  - 关联查询使用productPackageId条件

**Flyway脚本：**
- ✅ `core-infrastructure/src/main/resources/db/migration/V6__product_package.sql`
  - 字段与数据库设计文档一致

### 5. 单元测试

- ✅ `core-application/src/test/java/com/agri_system/application/productpackage/service/PackageCreateTest.java`
  - 测试不传vip_price应允许创建
  - 测试items商品重复应抛package-1005
  
- ✅ `core-application/src/test/java/com/agri_system/application/productpackage/service/PackageUpdateTest.java`
  - 测试全量替换明细必须正确
  - 测试删除旧明细、增添新明细、更新数量均正常

- ✅ `core-infrastructure/src/test/java/com/agri_system/infrastructure/productpackage/repository/RepositoryTest.java`
  - 测试findItemsByPackageId正确返回多条
  - 测试分页条件查询有效

### 6. OpenAPI文档

- ✅ `openapi/product-package.yaml`
  - 包含所有DTO定义
  - 包含响应示例
  - 包含所有请求接口
  - 包含错误码说明

## 功能实现清单

### 套餐主档（CRUD + 上下架）

- ✅ 创建套餐（系统自动生成编码）
- ✅ 更新套餐（采用全量替换明细模式）
- ✅ 删除套餐（软删除，不校验订单引用）
- ✅ 查询套餐详情（含明细列表）
- ✅ 套餐分页查询（支持名称、编码、状态）
- ✅ 上架/下架操作（status字段控制）

### 套餐明细管理（商品组合）

- ✅ 采用全量替换策略
- ✅ update接口传入items[]数组
- ✅ 后端根据新列表自动计算增删改
- ✅ 校验规则：
  - ✅ 套餐至少包含1个商品（package-1004）
  - ✅ 明细列表内商品不能重复（package-1005）
  - ✅ 商品必须存在（引用product表）
  - ✅ 数量 > 0

### 销售支撑能力（提供给订单模块）

- ✅ Domain Service提供：`List<ProductPackageItem> getPackageItems(Long packageId);`
- ✅ 订单模块在扣库存时展开套餐
- ✅ 此模块不依赖订单模块（仅提供查询能力）

## 错误码实现

- ✅ package-1001：套餐不存在
- ✅ package-1002：套餐编码重复
- ✅ package-1004：套餐必须至少包含一个商品
- ✅ package-1005：套餐包含重复商品

## 数据库表

- ✅ `product_package`（套餐主表）
- ✅ `product_package_item`（套餐明细表）
- ✅ 引用`product`表进行校验

## 技术规范遵循

- ✅ 遵循DDD-Lite分层规范
- ✅ Controller只负责参数校验、调用应用服务、返回DTO
- ✅ Application Service编排业务流程，不写领域规则
- ✅ Domain层包含核心业务规则
- ✅ Infrastructure层负责数据持久化（MyBatis-Flex）
- ✅ 所有写操作加@Transactional
- ✅ 完整的OpenAPI注解
- ✅ 错误码统一管理
- ✅ 禁止跨模块直接依赖订单模块

## 交付检查

### ✅ 生成的文件路径列表

**Controller层：**
- `api-admin/src/main/java/com/agri_system/api/admin/controller/ProductPackageController.java`

**Application层：**
- `core-application/src/main/java/com/agri_system/application/productpackage/service/ProductPackageApplicationService.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/exception/PackageErrorCode.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/ProductPackageCreateCommand.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/ProductPackageUpdateCommand.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/command/PackageItemDTO.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/query/ProductPackageQuery.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/vo/ProductPackageDetailVO.java`
- `core-application/src/main/java/com/agri_system/application/productpackage/dto/vo/ProductPackageItemVO.java`

**Domain层：**
- `core-domain/src/main/java/com/agri_system/domain/productpackage/model/ProductPackage.java`
- `core-domain/src/main/java/com/agri_system/domain/productpackage/model/ProductPackageItem.java`
- `core-domain/src/main/java/com/agri_system/domain/productpackage/service/ProductPackageDomainService.java`
- `core-domain/src/main/java/com/agri_system/domain/productpackage/repository/ProductPackageRepository.java`
- `core-domain/src/main/java/com/agri_system/domain/productpackage/repository/ProductPackageItemRepository.java`

**Infrastructure层：**
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/mapper/ProductPackageMapper.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/mapper/ProductPackageItemMapper.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/repository/ProductPackageRepositoryImpl.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/productpackage/repository/ProductPackageItemRepositoryImpl.java`
- `core-infrastructure/src/main/resources/db/migration/V6__product_package.sql`

**测试：**
- `core-application/src/test/java/com/agri_system/application/productpackage/service/PackageCreateTest.java`
- `core-application/src/test/java/com/agri_system/application/productpackage/service/PackageUpdateTest.java`
- `core-infrastructure/src/test/java/com/agri_system/infrastructure/productpackage/repository/RepositoryTest.java`

**OpenAPI：**
- `openapi/product-package.yaml`

### ✅ 所有DTO是否完整

- ✅ ProductPackageCreateCommand（包含name、prices、items[]）
- ✅ ProductPackageUpdateCommand（包含name、prices、items[]）
- ✅ PackageItemDTO（供items[]使用）
- ✅ ProductPackageQuery（分页条件）
- ✅ ProductPackageDetailVO（含明细列表）
- ✅ ProductPackageItemVO

### ✅ Domain / Application / Controller是否齐全

- ✅ Domain：实体、领域服务、仓储接口
- ✅ Application：ApplicationService、DTO
- ✅ Controller：ProductPackageController

### ✅ OpenAPI是否更新

- ✅ 已生成`openapi/product-package.yaml`
- ✅ 包含所有DTO定义
- ✅ 包含响应示例
- ✅ 包含所有请求接口
- ✅ 包含错误码说明

### ✅ Flyway是否生成

- ✅ 已生成`V6__product_package.sql`
- ✅ 字段与数据库设计文档一致

### ✅ 错误码是否实现

- ✅ package-1001：套餐不存在
- ✅ package-1002：套餐编码重复
- ✅ package-1004：套餐必须至少包含一个商品
- ✅ package-1005：套餐包含重复商品

## 总结

套餐模块已完整实现所有要求的功能，包括：
1. 套餐主档的完整CRUD和上下架功能
2. 套餐明细的全量替换策略
3. 提供给订单模块的销售支撑能力
4. 完整的错误码定义和处理
5. 符合DDD-Lite架构规范的分层实现
6. 完整的单元测试覆盖
7. 详细的OpenAPI文档

所有代码遵循项目规范，无编译错误，可以直接使用。










