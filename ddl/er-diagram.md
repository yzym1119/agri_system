# 农资行业进销存系统 - 数据库 ER 图

## Mermaid ER 图

```mermaid
erDiagram
    %% 基础数据表
    STORE ||--o{ STAFF : "管理"
    STORE ||--o{ INVENTORY : "拥有"
    STORE ||--o{ ORDER : "产生"
    STORE ||--o{ INVENTORY_TRANSFER : "调出/调入"
    STORE ||--o{ INVENTORY_ADJUSTMENT : "调整"
    STORE ||--o{ PRODUCT_PRICE : "定价"
    
    STAFF ||--o{ ORDER : "销售"
    STAFF ||--o{ ORDER_DELIVERY : "配送"
    STAFF ||--o{ STAFF_PERFORMANCE : "绩效"
    STAFF ||--o{ COMMISSION_RULE : "提成规则"
    
    CUSTOMER ||--o{ ORDER : "下单"
    CUSTOMER ||--o{ CUSTOMER_WALLET : "拥有"
    CUSTOMER ||--o{ WALLET_TRANSACTION : "交易"
    CUSTOMER ||--o{ CREDIT_ACCOUNT : "赊销"
    CUSTOMER ||--o{ CREDIT_STATEMENT : "对账"
    CUSTOMER ||--o{ STAFF_PERFORMANCE : "关联"
    
    %% 商品相关
    PRODUCT ||--o{ PRODUCT_UNIT_CONVERSION : "换算"
    PRODUCT ||--o{ PRODUCT_PACKAGE_ITEM : "组成"
    PRODUCT ||--o{ INVENTORY : "库存"
    PRODUCT ||--o{ INVENTORY_TRANSFER : "调拨"
    PRODUCT ||--o{ INVENTORY_ADJUSTMENT : "调整"
    PRODUCT ||--o{ INVENTORY_TRANSACTION : "流水"
    PRODUCT ||--o{ INVENTORY_WARNING : "预警"
    PRODUCT ||--o{ ORDER_ITEM : "订单明细"
    PRODUCT ||--o{ PRODUCT_PRICE : "价格"
    PRODUCT ||--o{ COMMISSION_RULE : "提成规则"
    PRODUCT ||--o{ STAFF_PERFORMANCE : "关联"
    
    PRODUCT_PACKAGE ||--o{ PRODUCT_PACKAGE_ITEM : "包含"
    PRODUCT_PACKAGE ||--o{ ORDER_ITEM : "订单明细"
    
    %% 库存相关
    INVENTORY ||--o{ INVENTORY_TRANSACTION : "流水"
    INVENTORY ||--o{ INVENTORY_WARNING : "预警"
    
    INVENTORY_TRANSFER }o--|| STORE : "调出"
    INVENTORY_TRANSFER }o--|| STORE : "调入"
    INVENTORY_TRANSFER }o--|| PRODUCT : "商品"
    
    INVENTORY_ADJUSTMENT }o--|| STORE : "门店"
    INVENTORY_ADJUSTMENT }o--|| PRODUCT : "商品"
    
    %% 订单相关
    ORDER ||--o{ ORDER_ITEM : "明细"
    ORDER ||--o| ORDER_DELIVERY : "配送"
    ORDER ||--o| CREDIT_ACCOUNT : "赊销"
    ORDER ||--o{ STAFF_PERFORMANCE : "关联"
    
    ORDER_ITEM }o--o| PRODUCT : "商品"
    ORDER_ITEM }o--o| PRODUCT_PACKAGE : "套餐"
    
    ORDER_DELIVERY }o--o| STAFF : "配送员"
    
    %% 价格体系
    PRODUCT_PRICE }o--o| STORE : "门店"
    PRODUCT_PRICE }o--|| PRODUCT : "商品"
    
    COMMISSION_RULE }o--o| PRODUCT : "商品"
    COMMISSION_RULE }o--o| STAFF : "员工"
    
    %% 钱包相关
    CUSTOMER_WALLET ||--o{ WALLET_TRANSACTION : "交易"
    CUSTOMER_WALLET }o--|| CUSTOMER : "客户"
    
    %% 赊销相关
    CREDIT_ACCOUNT }o--|| CUSTOMER : "客户"
    CREDIT_ACCOUNT }o--|| ORDER : "订单"
    
    CREDIT_STATEMENT }o--|| CUSTOMER : "客户"
    
    %% 绩效相关
    STAFF_PERFORMANCE }o--|| STAFF : "员工"
    STAFF_PERFORMANCE }o--o| ORDER : "订单"
    STAFF_PERFORMANCE }o--o| CUSTOMER : "客户"
    STAFF_PERFORMANCE }o--o| PRODUCT : "商品"
    
    %% 表定义
    STORE {
        bigint id PK
        varchar code UK
        varchar name
        varchar address
        varchar phone
        bigint manager_id FK
        tinyint status
        datetime created_at
        datetime updated_at
    }
    
    STAFF {
        bigint id PK
        varchar code UK
        varchar name
        varchar phone UK
        bigint store_id FK
        varchar role
        tinyint status
        datetime created_at
    }
    
    CUSTOMER {
        bigint id PK
        varchar code UK
        varchar name
        varchar phone UK
        tinyint type
        tinyint vip_level
        decimal credit_limit
        tinyint status
        datetime created_at
    }
    
    PRODUCT {
        bigint id PK
        varchar code UK
        varchar name
        varchar specification
        varchar dosage_form
        varchar manufacturer
        varchar main_unit
        varchar aux_unit
        decimal suggested_retail_price
        decimal vip_price
        decimal min_price
        decimal purchase_price
        tinyint status
        datetime created_at
    }
    
    PRODUCT_UNIT_CONVERSION {
        bigint id PK
        bigint product_id FK
        varchar from_unit
        varchar to_unit
        decimal conversion_ratio
    }
    
    PRODUCT_PACKAGE {
        bigint id PK
        varchar code UK
        varchar name
        decimal suggested_retail_price
        decimal vip_price
        decimal min_price
        tinyint status
    }
    
    PRODUCT_PACKAGE_ITEM {
        bigint id PK
        bigint package_id FK
        bigint product_id FK
        decimal quantity
        varchar unit
    }
    
    INVENTORY {
        bigint id PK
        bigint store_id FK
        bigint product_id FK
        decimal quantity
        varchar unit
        date expiry_date
        varchar batch_no
        int version
    }
    
    INVENTORY_TRANSFER {
        bigint id PK
        varchar transfer_no UK
        bigint from_store_id FK
        bigint to_store_id FK
        bigint product_id FK
        decimal quantity
        tinyint status
    }
    
    INVENTORY_ADJUSTMENT {
        bigint id PK
        varchar adjustment_no UK
        bigint store_id FK
        bigint product_id FK
        tinyint adjustment_type
        decimal quantity_before
        decimal quantity_after
    }
    
    INVENTORY_TRANSACTION {
        bigint id PK
        bigint store_id FK
        bigint product_id FK
        tinyint transaction_type
        decimal quantity
        decimal quantity_after
        varchar reference_type
        bigint reference_id
    }
    
    INVENTORY_WARNING {
        bigint id PK
        bigint store_id FK
        bigint product_id FK
        tinyint warning_type
        decimal current_quantity
        tinyint status
    }
    
    ORDER {
        bigint id PK
        varchar order_no UK
        bigint store_id FK
        bigint customer_id FK
        tinyint order_type
        tinyint order_status
        decimal total_amount
        decimal pay_amount
        tinyint payment_method
        bigint staff_id FK
        datetime created_at
    }
    
    ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        bigint package_id FK
        tinyint item_type
        decimal quantity
        decimal unit_price
        decimal total_price
        decimal commission_amount
    }
    
    ORDER_DELIVERY {
        bigint id PK
        bigint order_id FK
        varchar delivery_no UK
        tinyint delivery_status
        tinyint audit_status
        bigint delivery_staff_id FK
        datetime audit_at
    }
    
    PRODUCT_PRICE {
        bigint id PK
        bigint product_id FK
        bigint store_id FK
        tinyint customer_type
        tinyint price_type
        decimal price
        date effective_date
        date expiry_date
    }
    
    COMMISSION_RULE {
        bigint id PK
        bigint product_id FK
        bigint staff_id FK
        tinyint rule_type
        decimal fixed_amount
        decimal profit_percentage
        int priority
    }
    
    CUSTOMER_WALLET {
        bigint id PK
        bigint customer_id FK
        tinyint wallet_type
        decimal balance
        int version
    }
    
    WALLET_TRANSACTION {
        bigint id PK
        bigint customer_id FK
        tinyint wallet_type
        tinyint transaction_type
        decimal amount
        decimal balance_before
        decimal balance_after
    }
    
    CREDIT_ACCOUNT {
        bigint id PK
        bigint customer_id FK
        bigint order_id FK
        decimal credit_amount
        decimal paid_amount
        decimal remaining_amount
        date due_date
        tinyint status
    }
    
    CREDIT_STATEMENT {
        bigint id PK
        bigint customer_id FK
        varchar statement_no UK
        date statement_date
        decimal total_credit_amount
        decimal total_paid_amount
        tinyint status
    }
    
    STAFF_PERFORMANCE {
        bigint id PK
        bigint staff_id FK
        tinyint performance_type
        bigint order_id FK
        bigint customer_id FK
        bigint product_id FK
        decimal commission_amount
        date performance_date
    }
```

## 表关系说明

### 核心实体关系

1. **门店 (STORE)** - 系统的基础组织单位
   - 管理员工 (STAFF)
   - 拥有库存 (INVENTORY)
   - 产生订单 (ORDER)

2. **商品 (PRODUCT)** - 核心业务实体
   - 支持多级单位换算 (PRODUCT_UNIT_CONVERSION)
   - 可组成套餐 (PRODUCT_PACKAGE_ITEM)
   - 在各门店有独立库存 (INVENTORY)
   - 支持差异化定价 (PRODUCT_PRICE)

3. **订单 (ORDER)** - 业务交易核心
   - 支持线下POS、小程序自提、小程序配送三种类型
   - 可包含商品或套餐 (ORDER_ITEM)
   - 支持配送审核 (ORDER_DELIVERY)
   - 可关联赊销 (CREDIT_ACCOUNT)

4. **客户 (CUSTOMER)** - 客户管理
   - 支持普通客户和VIP客户
   - 拥有三钱包 (CUSTOMER_WALLET)
   - 支持赊销 (CREDIT_ACCOUNT)

5. **库存 (INVENTORY)** - 多门店独立库存
   - 按门店+商品+批次唯一
   - 支持调拨 (INVENTORY_TRANSFER)
   - 支持调整 (INVENTORY_ADJUSTMENT)
   - 记录流水 (INVENTORY_TRANSACTION)
   - 支持预警 (INVENTORY_WARNING)

## 枚举值说明

### 门店状态 (store.status)
- 1: 正常
- 2: 停用

### 员工角色 (staff.role)
- ADMIN: 管理员
- MANAGER: 店长
- STAFF: 员工

### 员工状态 (staff.status)
- 1: 在职
- 2: 离职

### 客户类型 (customer.type)
- 1: 普通客户
- 2: VIP客户

### 客户状态 (customer.status)
- 1: 正常
- 2: 停用

### VIP等级 (customer.vip_level)
- 1: 普通VIP
- 2: 银卡VIP
- 3: 金卡VIP
- 4: 钻石VIP

### 商品状态 (product.status)
- 1: 正常
- 2: 停用

### 库存调拨状态 (inventory_transfer.status)
- 1: 待调出
- 2: 已调出
- 3: 已入库
- 4: 已取消

### 库存调整类型 (inventory_adjustment.adjustment_type)
- 1: 盘盈
- 2: 盘亏
- 3: 报损
- 4: 其他

### 库存流水类型 (inventory_transaction.transaction_type)
- 1: 入库
- 2: 出库
- 3: 调拨出
- 4: 调拨入
- 5: 调整
- 6: 销售
- 7: 退货

### 库存预警类型 (inventory_warning.warning_type)
- 1: 库存不足
- 2: 库存积压
- 3: 即将过期
- 4: 已过期

### 订单类型 (order.order_type)
- 1: 线下POS
- 2: 小程序自提
- 3: 小程序配送

### 订单状态 (order.order_status)
- 1: 待支付
- 2: 已支付
- 3: 待配送
- 4: 配送中
- 5: 已完成
- 6: 已取消
- 7: 已退款

### 支付方式 (order.payment_method)
- 1: 现金
- 2: 微信
- 3: 支付宝
- 4: 钱包
- 5: 赊销

### 配送状态 (order_delivery.delivery_status)
- 1: 待审核
- 2: 已审核
- 3: 配送中
- 4: 已送达
- 5: 已拒收

### 审核状态 (order_delivery.audit_status)
- 1: 待审核
- 2: 已通过
- 3: 已拒绝

### 价格类型 (product_price.price_type)
- 1: 建议零售价
- 2: VIP价
- 3: 最低限价
- 4: 进价

### 提成规则类型 (commission_rule.rule_type)
- 1: 固定金额
- 2: 毛利百分比

### 钱包类型 (customer_wallet.wallet_type)
- 1: 本金
- 2: 专用金
- 3: 积分

### 钱包交易类型 (wallet_transaction.transaction_type)
- 1: 充值
- 2: 消费
- 3: 退款
- 4: 赠送
- 5: 扣除

### 赊销状态 (credit_account.status)
- 1: 未还清
- 2: 已还清
- 3: 已逾期

### 对账状态 (credit_statement.status)
- 1: 待确认
- 2: 已确认
- 3: 已争议

### 绩效类型 (staff_performance.performance_type)
- 1: 推荐行为
- 2: 服务行为











