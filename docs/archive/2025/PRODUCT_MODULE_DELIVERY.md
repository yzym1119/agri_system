> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

# 商品档案模块（Product Module）交付清单

## 模块概述

商品档案模块实现了完整的商品管理功能，包括商品主档、商品分类、多单位管理、多维价格体系。

## 交付文件清单

### 1. Controller层（api-admin模块）

- ✅ `api-admin/src/main/java/com/agri_system/api/admin/controller/ProductController.java`
  - 商品CRUD接口
  - 分类管理接口
  - 单位换算管理接口
  - 价格规则管理接口
  - 完整的OpenAPI注解

### 2. Application Service层（core-application模块）

- ✅ `core-application/src/main/java/com/agri_system/application/product/service/ProductApplicationService.java`
  - 业务流程编排
  - 事务管理
  - DTO转换

- ✅ `core-application/src/main/java/com/agri_system/application/product/exception/ProductErrorCode.java`
  - 错误码定义（product-1001 ~ product-1006）

#### DTO类

**Command类：**
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/ProductCreateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/ProductUpdateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/CategoryCreateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/CategoryUpdateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/UnitConversionCreateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/PriceRuleCreateCommand.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/command/PriceRuleUpdateCommand.java`

**Query类：**
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/query/ProductQuery.java`

**VO类：**
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/vo/ProductDetailVO.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/vo/CategoryTreeVO.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/vo/UnitConversionVO.java`
- ✅ `core-application/src/main/java/com/agri_system/application/product/dto/vo/PriceRuleVO.java`

### 3. Domain层（core-domain模块）

**实体类：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/model/Product.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/model/ProductCategory.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/model/ProductUnitConversion.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/model/ProductPrice.java`

**领域服务：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/service/ProductDomainService.java`

**仓储接口：**
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductRepository.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductCategoryRepository.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductUnitConversionRepository.java`
- ✅ `core-domain/src/main/java/com/agri_system/domain/product/repository/ProductPriceRepository.java`

### 4. Infrastructure层（core-infrastructure模块）

**Mapper接口：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductMapper.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductCategoryMapper.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductUnitConversionMapper.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/mapper/ProductPriceMapper.java`

**Repository实现：**
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/repository/ProductRepositoryImpl.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/repository/ProductCategoryRepositoryImpl.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/repository/ProductUnitConversionRepositoryImpl.java`
- ✅ `core-infrastructure/src/main/java/com/agri_system/infrastructure/product/repository/ProductPriceRepositoryImpl.java`

### 5. 数据库迁移脚本

- ✅ `core-infrastructure/src/main/resources/db/migration/V4__product.sql`
  - 已更新为最新表结构
  - 包含product_category、product、product_unit_conversion表
  - product_price表在V7__price.sql中定义

### 6. 测试文件

**单元测试：**
- ✅ `core-application/src/test/java/com/agri_system/application/product/service/ProductApplicationServiceTest.java`
  - 验证avg_cost_price未被赋值
  - 校验编码重复抛出异常

- ✅ `core-infrastructure/src/test/java/com/agri_system/infrastructure/product/repository/ProductRepositoryTest.java`
  - 测试MyBatis-Flex分页查询
  - 确保分类筛选有效

**集成测试：**
- ✅ `api-admin/src/test/java/com/agri_system/api/admin/controller/ProductControllerTest.java`
  - 确保存储策略价格时，store_id和vip_level可以为空

## 功能实现清单

### ✅ 商品基本档案（CRUD）

- [x] 创建商品
- [x] 更新商品
- [x] 查询商品详情（包含主辅单位信息、分类名称）
- [x] 商品分页查询（筛选条件：编码、名称、分类ID、上架状态、创建时间范围）
- [x] 快捷操作：上架/下架
- [x] 快捷操作：修改排序（预留接口）

### ✅ 商品分类管理

- [x] 创建分类
- [x] 更新分类（名称、排序）
- [x] 删除分类（校验是否被商品引用）
- [x] 分类树查询（递归构建树结构）

### ✅ 多单位管理逻辑

- [x] 主辅单位（一级换算）：直接读写product表的main_unit, aux_unit, main_to_aux_ratio
- [x] 多级单位（二级扩展）：product_unit_conversion表
- [x] 增加/删除/查询扩展单位
- [x] 校验：同一个商品ID下，from_unit和to_unit组合必须唯一

### ✅ 多维价格体系

- [x] 新增/更新价格规则（参数：商品ID、门店ID[可选]、客户类型[可选]、VIP等级[可选]、价格）
- [x] 查询某商品的价格规则列表
- [x] 删除指定ID的价格规则

## 错误码定义

- ✅ product-1001：商品不存在
- ✅ product-1002：商品编码(code)已存在
- ✅ product-1003：分类不存在或已失效
- ✅ product-1004：分类下存在商品，无法删除
- ✅ product-1005：单位换算配置冲突
- ✅ product-1006：商品已上架，禁止敏感修改（如需修改需先下架）

## 技术要点

1. **DDD-Lite架构**：严格按照分层规范实现
2. **MyBatis-Flex**：使用QueryWrapper进行类型安全的查询
3. **事务管理**：所有写操作使用@Transactional
4. **OpenAPI注解**：完整的API文档注解
5. **字段约束**：
   - avg_cost_price只读，由采购模块维护
   - suggested_retail_price和online_price存入product主表
   - base_shipping_fee字段支持

## 待完成事项

1. ⚠️ **Sa-Token鉴权**：Controller中已预留TODO，需要添加@SaCheckRole或@SaCheckPermission注解
2. ⚠️ **OpenAPI文档生成**：需要启动应用后访问/swagger-ui.html验证
3. ⚠️ **完整测试覆盖**：当前测试为框架代码，需要补充完整测试用例
4. ⚠️ **异常处理**：需要统一异常处理机制，将IllegalArgumentException转换为标准错误响应

## 验证步骤

1. **编译验证**：
   ```bash
   mvn clean compile
   ```

2. **启动应用**：
   ```bash
   mvn spring-boot:run -pl api-admin
   ```

3. **访问Swagger文档**：
   ```
   http://localhost:8080/swagger-ui.html
   ```

4. **验证OpenAPI文档生成**：
   ```
   http://localhost:8080/v3/api-docs
   ```

## 注意事项

1. 数据库表结构已通过Flyway迁移脚本管理
2. 所有实体类已添加MyBatis-Flex注解（@Table, @Id）
3. Repository实现使用MyBatis-Flex的QueryWrapper进行查询
4. 分类树查询使用内存递归构建，适合数据量不大的场景
5. 价格规则支持store_id和vip_level为空，表示全局或通用价格

---

**生成时间**：2024年
**模块版本**：1.0.0
**状态**：✅ 已完成基础实现，待完善测试和鉴权











