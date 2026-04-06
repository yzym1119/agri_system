# 农资行业进销存系统 - 数据库设计文档

## 1. 概述

本文档描述了农资行业进销存系统的完整数据库设计，包含**31张核心业务表**，覆盖供应商、采购、商品、库存、订单、价格体系、客户、积分商城、赊销、员工、门店等业务领域。

### 1.1 设计原则

- **多门店支持**：所有库存、订单按门店隔离，支持多门店独立运营
- **库存共享但不混淆**：门店间可查看库存，但扣减时只能操作本门店库存
- **灵活的价格体系**：支持全局价格、门店价格、客户类型价格、VIP等级价格
- **完整的业务闭环**：从供应商采购到订单完成，从库存管理到绩效统计
- **数据一致性**：使用外键约束、乐观锁保证数据一致性
- **移动加权平均成本**：通过采购入库记录真实进价，自动计算商品成本

### 1.2 技术规范

- **数据库**：MySQL 8.x
- **字符集**：utf8mb4
- **排序规则**：utf8mb4_unicode_ci
- **存储引擎**：InnoDB
- **版本管理**：Flyway

## 2. 表结构设计

### 2.1 基础数据表

#### 2.1.1 门店表 (store)

**功能**：管理门店基础信息

**关键字段**：
- `code`: 门店编码（唯一）
- `manager_id`: 店长ID（关联staff表，可为空）

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`status`, `manager_id`

#### 2.1.2 员工表 (staff)

**功能**：管理员工信息，支持多门店

**关键字段**：
- `code`: 员工编码（唯一）
- `phone`: 手机号（唯一）
- `store_id`: 所属门店（外键）
- `role`: 角色（ADMIN-管理员，MANAGER-店长，STAFF-员工，DRIVER-司机）

**索引策略**：
- 主键：`id`
- 唯一索引：`code`, `phone`
- 普通索引：`store_id`, `status`, `role`

#### 2.1.3 客户表 (customer)

**功能**：管理客户信息，支持普通客户和VIP客户

**关键字段**：
- `code`: 客户编码（唯一）
- `phone`: 手机号（唯一）
- `type`: 客户类型（1-普通，2-VIP）
- `vip_level`: VIP等级（1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP）
- `credit_limit`: 赊销额度

**索引策略**：
- 主键：`id`
- 唯一索引：`code`, `phone`
- 普通索引：`type`, `status`, `vip_level`

### 2.2 供应商与采购模块

#### 2.2.1 供应商表 (supplier)

**功能**：管理供应商信息

**关键字段**：
- `code`: 供应商编码（唯一）
- `name`: 供应商名称
- `contact_name`: 联系人姓名
- `contact_phone`: 联系人电话
- `bank_account`: 银行账户
- `bank_name`: 开户银行

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`status`

#### 2.2.2 采购订单表 (purchase_order)

**功能**：管理采购订单主表

**关键字段**：
- `purchase_no`: 采购单号（唯一）
- `supplier_id`: 供应商ID（外键）
- `store_id`: 入库门店ID（外键）
- `total_amount`: 总金额
- `status`: 状态（1-待审核，2-已审核，3-已入库，4-已取消）
- `purchase_date`: 采购日期
- `expected_arrival_date`: 预计到货日期
- `arrival_date`: 实际到货日期

**索引策略**：
- 主键：`id`
- 唯一索引：`purchase_no`
- 普通索引：`supplier_id`, `store_id`, `status`, `purchase_date`

#### 2.2.3 采购订单明细表 (purchase_order_item)

**功能**：记录采购订单明细，包含每次真实进价

**关键字段**：
- `purchase_order_id`: 采购订单ID（外键）
- `product_id`: 商品ID（外键）
- `quantity`: 数量
- `unit`: 单位
- `buy_price`: **本次真实进价**（用于计算加权平均成本）
- `total_price`: 小计金额

**索引策略**：
- 主键：`id`
- 普通索引：`purchase_order_id`, `product_id`

**设计说明**：
- `buy_price` 字段记录每次采购的真实进价，后端根据此价格计算商品的移动加权平均成本
- 采购入库时，会触发 `product.avg_cost_price` 的重新计算

### 2.3 商品相关表

#### 2.3.1 商品分类表 (product_category)

**功能**：商品分类管理（树形结构）

**关键字段**：
- `code`: 分类编码（唯一）
- `name`: 分类名称
- `parent_id`: 父分类ID（外键，为空表示顶级分类）
- `level`: 分类层级（1-一级，2-二级，以此类推）
- `sort_order`: 排序顺序

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`parent_id`, `level`, `status`

**设计说明**：
- 支持无限层级分类（树形结构）
- 通过 `parent_id` 和 `level` 字段实现层级管理

#### 2.3.2 商品表 (product) - 重构版

**功能**：商品档案管理，包含规格、剂型、厂家、价格、线上配置等信息

**关键字段**：
- `code`: 商品编码（唯一）
- `category_id`: 商品分类ID（外键，关联product_category）
- `specification`: 规格（如：500g/袋）
- `dosage_form`: 剂型（如：颗粒剂、水剂、粉剂）
- `manufacturer`: 生产厂家
- `main_unit`: 主单位
- `aux_unit`: 辅单位
- `main_to_aux_ratio`: 主辅单位换算比例
- `suggested_retail_price`: 建议零售价
- `vip_price`: VIP价格
- `min_price`: 最低限价
- `avg_cost_price`: **移动加权平均成本**（由采购入库触发计算）
- `image_url`: 主图URL（列表/详情共用）
- `detail_text`: 详情文字（TEXT类型，纯文字详情）
- `is_online`: 是否上架（1-上架，0-下架）
- `online_price`: 线上价（可为空，为空则取零售价）
- `base_shipping_fee`: 基础运费（默认0）

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`name`, `category_id`, `status`, `is_online`

**设计变更说明**：
- **删除**：`category` 字符串字段
- **新增**：`category_id` 外键字段（关联product_category表）
- **删除**：`purchase_price` 静态进价字段
- **新增**：`avg_cost_price` 移动加权平均成本字段
- **新增**：`image_url` 主图字段
- **新增**：`detail_text` 详情文字字段
- **新增**：`is_online` 上架状态字段
- **新增**：`online_price` 线上价字段
- **新增**：`base_shipping_fee` 基础运费字段

#### 2.3.3 商品单位换算表 (product_unit_conversion)

**功能**：支持多级单位换算（如：袋->箱->件）

**关键字段**：
- `product_id`: 商品ID（外键）
- `from_unit`: 源单位
- `to_unit`: 目标单位
- `conversion_ratio`: 换算比例

**索引策略**：
- 主键：`id`
- 唯一索引：`(product_id, from_unit, to_unit)`
- 普通索引：`product_id`

#### 2.3.4 套餐/配方表 (product_package)

**功能**：管理套餐/配方，多个商品组合销售

**关键字段**：
- `code`: 套餐编码（唯一）
- `name`: 套餐名称
- `suggested_retail_price`: 建议零售价
- `vip_price`: VIP价格
- `min_price`: 最低限价

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`status`

#### 2.3.5 套餐明细表 (product_package_item)

**功能**：套餐包含的商品明细

**关键字段**：
- `package_id`: 套餐ID（外键）
- `product_id`: 商品ID（外键）
- `quantity`: 数量
- `unit`: 单位

**索引策略**：
- 主键：`id`
- 普通索引：`package_id`, `product_id`
- 外键约束：`package_id`, `product_id`

### 2.4 库存相关表

#### 2.4.1 库存表 (inventory)

**功能**：多门店独立库存管理，支持批次和效期管理

**关键字段**：
- `store_id`: 门店ID（外键）
- `product_id`: 商品ID（外键）
- `quantity`: 库存数量
- `unit`: 单位
- `production_date`: 生产日期
- `expiry_date`: 有效期至
- `batch_no`: 批次号
- `version`: 乐观锁版本号

**索引策略**：
- 主键：`id`
- 唯一索引：`(store_id, product_id, batch_no)`
- 普通索引：`store_id`, `product_id`, `expiry_date`

**设计说明**：
- 使用 `(store_id, product_id, batch_no)` 作为唯一约束，确保同一门店同一商品同一批次只有一条记录
- 使用乐观锁 `version` 字段防止并发更新冲突

#### 2.4.2 库存调拨表 (inventory_transfer)

**功能**：门店间库存调拨

**关键字段**：
- `transfer_no`: 调拨单号（唯一）
- `from_store_id`: 调出门店ID（外键）
- `to_store_id`: 调入门店ID（外键）
- `product_id`: 商品ID（外键）
- `quantity`: 调拨数量
- `status`: 状态（1-待调出，2-已调出，3-已入库，4-已取消）

**索引策略**：
- 主键：`id`
- 唯一索引：`transfer_no`
- 普通索引：`from_store_id`, `to_store_id`, `product_id`, `status`

#### 2.4.3 库存调整表 (inventory_adjustment)

**功能**：库存盘盈、盘亏、报损等调整

**关键字段**：
- `adjustment_no`: 调整单号（唯一）
- `store_id`: 门店ID（外键）
- `product_id`: 商品ID（外键）
- `adjustment_type`: 调整类型（1-盘盈，2-盘亏，3-报损，4-其他）
- `quantity_before`: 调整前数量
- `quantity_after`: 调整后数量
- `quantity_diff`: 调整数量

**索引策略**：
- 主键：`id`
- 唯一索引：`adjustment_no`
- 普通索引：`store_id`, `product_id`, `adjustment_type`

#### 2.4.4 库存流水表 (inventory_transaction)

**功能**：记录所有库存变动流水

**关键字段**：
- `store_id`: 门店ID（外键）
- `product_id`: 商品ID（外键）
- `transaction_type`: 流水类型（1-入库，2-出库，3-调拨出，4-调拨入，5-调整，6-销售，7-退货）
- `quantity`: 数量（正数为增加，负数为减少）
- `quantity_after`: 操作后库存数量
- `reference_type`: 关联类型（如：ORDER-订单，TRANSFER-调拨，ADJUSTMENT-调整，PURCHASE-采购）
- `reference_id`: 关联ID

**索引策略**：
- 主键：`id`
- 普通索引：`(store_id, product_id)`, `transaction_type`, `(reference_type, reference_id)`, `created_at`

#### 2.4.5 库存预警表 (inventory_warning)

**功能**：库存不足、积压、过期预警

**关键字段**：
- `store_id`: 门店ID（外键）
- `product_id`: 商品ID（外键）
- `warning_type`: 预警类型（1-库存不足，2-库存积压，3-即将过期，4-已过期）
- `current_quantity`: 当前库存数量
- `threshold_quantity`: 预警阈值数量
- `expiry_date`: 过期日期
- `status`: 状态（1-待处理，2-已处理，3-已忽略）

**索引策略**：
- 主键：`id`
- 普通索引：`(store_id, product_id)`, `warning_type`, `status`

### 2.5 订单相关表

#### 2.5.1 订单表 (order) - 增强版

**功能**：管理所有订单（线下POS、小程序自提、小程序配送）

**关键字段**：
- `order_no`: 订单号（唯一）
- `store_id`: 门店ID（外键，线上快递订单通常为总店ID，自提订单为具体门店ID）
- `customer_id`: 客户ID（外键，可为空支持散客）
- `order_type`: 订单类型（1-线下POS，2-小程序自提，3-小程序配送）
- `delivery_type`: **配送方式**（1-门店自提，2-同城配送，3-快递发货，仅小程序订单有效）
- `order_status`: 订单状态（1-待支付，2-已支付，3-待配送，4-配送中，5-已完成，6-已取消，7-已退款）
- `total_amount`: 订单总金额
- `discount_amount`: 优惠金额
- `freight_amount`: **运费金额**（新增）
- `pay_amount`: 实付金额
- `payment_method`: 支付方式（1-现金，2-微信，3-支付宝，4-钱包，5-赊销）
- `staff_id`: 销售员工ID（外键，线下POS必填）
- `receiver_name`: **收货人姓名**（下单时固化，新增）
- `receiver_phone`: **收货人电话**（下单时固化，新增）
- `receiver_address`: **收货地址**（下单时固化，新增）

**索引策略**：
- 主键：`id`
- 唯一索引：`order_no`
- 普通索引：`store_id`, `customer_id`, `order_type`, `delivery_type`, `order_status`, `staff_id`, `created_at`

**设计变更说明**：
- **修正**：`delivery_type` 枚举值（1-门店自提，2-同城配送，3-快递发货）
- **新增**：`freight_amount` 运费金额字段
- **新增**：`receiver_name`, `receiver_phone`, `receiver_address` 地址快照字段
- **删除**：`delivery_address`, `delivery_phone`, `delivery_status` 字段（移至order_delivery表）

#### 2.5.2 订单明细表 (order_item)

**功能**：订单商品/套餐明细

**关键字段**：
- `order_id`: 订单ID（外键）
- `product_id`: 商品ID（外键，单品订单）
- `package_id`: 套餐ID（外键，套餐订单）
- `item_type`: 明细类型（1-商品，2-套餐）
- `product_name`: 商品/套餐名称（冗余字段）
- `quantity`: 数量
- `unit_price`: 单价
- `total_price`: 小计金额
- `commission_amount`: 提成金额

**索引策略**：
- 主键：`id`
- 普通索引：`order_id`, `product_id`, `package_id`

#### 2.5.3 配送表 (order_delivery) - 增强版

**功能**：小程序配送订单的配送管理

**关键字段**：
- `order_id`: 订单ID（外键）
- `delivery_no`: 配送单号（唯一）
- `delivery_method`: **配送方式**（1-门店自提，2-同城配送，3-快递发货，同订单delivery_type，新增）
- `delivery_status`: 配送状态（1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收）
- `delivery_address`: 配送地址
- `delivery_phone`: 配送联系电话
- `delivery_staff_id`: **配送员ID**（外键，同城配送时使用）
- `logistics_company`: **快递公司**（快递发货时使用，新增）
- `tracking_no`: **快递单号**（快递发货时使用，允许手动输入，新增）
- `audit_status`: **审核状态**（1-待审核，2-已通过，3-已拒绝，同城模式使用）

**索引策略**：
- 主键：`id`
- 唯一索引：`delivery_no`
- 普通索引：`order_id`, `delivery_method`, `delivery_status`, `audit_status`

**设计变更说明**：
- **新增**：`delivery_method` 配送方式字段
- **新增**：`logistics_company` 快递公司字段
- **新增**：`tracking_no` 快递单号字段
- **保留**：`delivery_staff_id` 配送员ID（同城配送使用）
- **保留**：`audit_status` 审核状态（同城配送使用）

### 2.6 价格体系相关表

#### 2.6.1 商品价格表 (product_price)

**功能**：支持按门店、客户类型、VIP等级的差异化定价

**关键字段**：
- `product_id`: 商品ID（外键）
- `store_id`: 门店ID（外键，为空表示全局价格）
- `customer_type`: 客户类型（1-普通客户，2-VIP客户，为空表示通用价格）
- `vip_level`: VIP等级（仅VIP客户有效）
- `price_type`: 价格类型（1-建议零售价，2-VIP价，3-最低限价，4-进价）
- `price`: 价格
- `effective_date`: 生效日期
- `expiry_date`: 失效日期

**索引策略**：
- 主键：`id`
- 普通索引：`product_id`, `store_id`, `customer_type`, `price_type`, `status`

**设计说明**：
- 支持全局价格（store_id为空）
- 支持门店价格（store_id不为空）
- 支持客户类型价格（customer_type不为空）
- 支持VIP等级价格（vip_level不为空）
- 价格查询时按优先级匹配：门店+客户类型+VIP等级 > 门店+客户类型 > 门店 > 全局

#### 2.6.2 提成规则表 (commission_rule)

**功能**：管理员工提成规则（固定金额或毛利百分比）

**关键字段**：
- `product_id`: 商品ID（外键，为空表示通用规则）
- `staff_id`: 员工ID（外键，为空表示通用规则）
- `rule_type`: 规则类型（1-固定金额，2-毛利百分比）
- `fixed_amount`: 固定金额（规则类型为1时有效）
- `profit_percentage`: 毛利百分比（规则类型为2时有效）
- `priority`: 优先级（数字越大优先级越高）

**索引策略**：
- 主键：`id`
- 普通索引：`product_id`, `staff_id`, `rule_type`, `status`, `priority`

**设计说明**：
- 支持商品级规则（product_id不为空）
- 支持员工级规则（staff_id不为空）
- 支持商品+员工组合规则（product_id和staff_id都不为空）
- 支持通用规则（product_id和staff_id都为空）
- 规则匹配时按优先级匹配：商品+员工 > 商品 > 员工 > 通用

### 2.7 客户钱包相关表

#### 2.7.1 客户钱包表 (customer_wallet)

**功能**：管理客户三钱包（本金、专用金、积分）

**关键字段**：
- `customer_id`: 客户ID（外键）
- `wallet_type`: 钱包类型（1-本金，2-专用金，3-积分）
- `balance`: 余额
- `version`: 乐观锁版本号

**索引策略**：
- 主键：`id`
- 唯一索引：`(customer_id, wallet_type)`
- 普通索引：`customer_id`

**设计说明**：
- 使用 `(customer_id, wallet_type)` 作为唯一约束，确保每个客户每种钱包类型只有一条记录
- 使用乐观锁 `version` 字段防止并发更新冲突

#### 2.7.2 钱包交易记录表 (wallet_transaction)

**功能**：记录所有钱包交易流水

**关键字段**：
- `customer_id`: 客户ID（外键）
- `wallet_type`: 钱包类型（1-本金，2-专用金，3-积分）
- `transaction_type`: 交易类型（1-充值，2-消费，3-退款，4-赠送，5-扣除）
- `amount`: 金额（正数为增加，负数为减少）
- `balance_before`: 交易前余额
- `balance_after`: 交易后余额
- `reference_type`: 关联类型（如：ORDER-订单，RECHARGE-充值）
- `reference_id`: 关联ID

**索引策略**：
- 主键：`id`
- 普通索引：`customer_id`, `wallet_type`, `transaction_type`, `(reference_type, reference_id)`, `created_at`

### 2.8 客户身份与地址管理

#### 2.8.1 客户身份表 (customer_identity)

**功能**：存储客户身份信息，支持多端登录（微信OpenID/手机号）

**关键字段**：
- `customer_id`: 客户ID（外键）
- `identity_type`: 身份类型（WECHAT_OPENID-微信OpenID，PHONE-手机号，OTHER-其他）
- `identity_value`: 身份值（OpenID/手机号等）
- `status`: 状态（1-正常，2-停用）

**索引策略**：
- 主键：`id`
- 唯一索引：`(identity_type, identity_value)`
- 普通索引：`customer_id`

**设计说明**：
- 支持一个客户多个身份（如：微信OpenID + 手机号）
- 通过 `(identity_type, identity_value)` 唯一约束确保身份唯一性

#### 2.8.2 客户地址表 (customer_address)

**功能**：小程序收货地址管理

**关键字段**：
- `customer_id`: 客户ID（外键）
- `receiver_name`: 收货人姓名
- `receiver_phone`: 收货人电话
- `province`: 省份
- `city`: 城市
- `district`: 区县
- `address`: 详细地址
- `is_default`: 是否默认地址（1-是，0-否）

**索引策略**：
- 主键：`id`
- 普通索引：`customer_id`, `is_default`, `status`

**设计说明**：
- 支持客户管理多个收货地址
- 支持设置默认地址

### 2.9 积分商城模块

#### 2.9.1 积分礼品表 (points_goods)

**功能**：管理积分商城礼品

**关键字段**：
- `code`: 礼品编码（唯一）
- `name`: 礼品名称
- `points`: 所需积分数
- `stock`: 库存数量
- `image_url`: 礼品图片URL
- `description`: 礼品描述
- `status`: 状态（1-上架，2-下架）

**索引策略**：
- 主键：`id`
- 唯一索引：`code`
- 普通索引：`status`, `points`

#### 2.9.2 积分兑换订单表 (points_exchange_order)

**功能**：记录积分兑换订单

**关键字段**：
- `exchange_no`: 兑换单号（唯一）
- `customer_id`: 客户ID（外键）
- `points_goods_id`: 积分礼品ID（外键）
- `points`: 消耗积分数
- `quantity`: 兑换数量
- `receiver_name`: 收货人姓名
- `receiver_phone`: 收货人电话
- `receiver_address`: 收货地址
- `status`: 状态（1-待发货，2-已发货，3-已完成，4-已取消）
- `logistics_company`: 快递公司
- `tracking_no`: 快递单号

**索引策略**：
- 主键：`id`
- 唯一索引：`exchange_no`
- 普通索引：`customer_id`, `points_goods_id`, `status`

### 2.10 支付风控模块

#### 2.10.1 钱包支付验证码表 (wallet_auth_code)

**功能**：线下余额支付验证码管理（支付风控）

**关键字段**：
- `customer_id`: 客户ID（外键）
- `order_id`: 订单ID（外键，可为空）
- `auth_code`: 验证码（6位数字）
- `amount`: 支付金额
- `status`: 状态（1-未使用，2-已使用，3-已过期）
- `expire_at`: 过期时间
- `used_at`: 使用时间

**索引策略**：
- 主键：`id`
- 普通索引：`customer_id`, `order_id`, `auth_code`, `status`, `expire_at`

**设计说明**：
- 用于线下余额支付时的安全验证
- 验证码有效期通常为5-10分钟
- 使用后立即失效

### 2.11 赊销相关表

#### 2.11.1 赊销台账表 (credit_account)

**功能**：管理客户赊销记录

**关键字段**：
- `customer_id`: 客户ID（外键）
- `order_id`: 订单ID（外键）
- `credit_amount`: 赊销金额
- `paid_amount`: 已还金额
- `remaining_amount`: 剩余金额
- `due_date`: 到期日期
- `status`: 状态（1-未还清，2-已还清，3-已逾期）

**索引策略**：
- 主键：`id`
- 普通索引：`customer_id`, `order_id`, `status`, `due_date`

#### 2.11.2 对账历史表 (credit_statement)

**功能**：记录客户对账历史

**关键字段**：
- `customer_id`: 客户ID（外键）
- `statement_no`: 对账单号（唯一）
- `statement_date`: 对账日期
- `start_date`: 对账开始日期
- `end_date`: 对账结束日期
- `total_credit_amount`: 赊销总额
- `total_paid_amount`: 已还总额
- `total_remaining_amount`: 剩余总额
- `status`: 状态（1-待确认，2-已确认，3-已争议）

**索引策略**：
- 主键：`id`
- 唯一索引：`statement_no`
- 普通索引：`customer_id`, `statement_date`, `status`

### 2.12 员工绩效相关表

#### 2.12.1 员工绩效记录表 (staff_performance)

**功能**：记录员工绩效（推荐行为、服务行为）

**关键字段**：
- `staff_id`: 员工ID（外键）
- `performance_type`: 绩效类型（1-推荐行为，2-服务行为）
- `order_id`: 关联订单ID（外键）
- `customer_id`: 关联客户ID（外键）
- `product_id`: 关联商品ID（外键）
- `performance_score`: 绩效分数
- `commission_amount`: 提成金额
- `performance_date`: 绩效日期

**索引策略**：
- 主键：`id`
- 普通索引：`staff_id`, `performance_type`, `order_id`, `customer_id`, `performance_date`

## 3. 索引设计原则

### 3.1 主键索引
- 所有表使用 `BIGINT` 自增主键
- 主键索引自动创建

### 3.2 唯一索引
- 业务编码字段（如：`code`, `order_no`）
- 业务唯一约束（如：`(customer_id, wallet_type)`）

### 3.3 普通索引
- 外键字段
- 常用查询字段（如：`status`, `created_at`）
- 组合查询字段（如：`(store_id, product_id)`）

### 3.4 索引优化建议
- 避免在小表上创建过多索引
- 大表（如：`inventory_transaction`, `wallet_transaction`）按时间分区或归档
- 定期分析索引使用情况，删除无用索引

## 4. 外键约束

### 4.1 外键设计
- 所有外键关系都定义了外键约束
- 外键字段都创建了索引
- 外键删除策略：默认 `RESTRICT`（禁止删除被引用记录）

### 4.2 外键关系图
详见 `er-diagram.md` 和 `er-diagram.puml`

## 5. 数据一致性保证

### 5.1 乐观锁
- `inventory.version`: 库存表乐观锁
- `customer_wallet.version`: 钱包表乐观锁

### 5.2 唯一约束
- 业务编码唯一性
- 业务组合唯一性（如：`(customer_id, wallet_type)`）

### 5.3 外键约束
- 保证引用完整性
- 防止数据孤立

## 6. 性能优化建议

### 6.1 查询优化
- 大表按时间范围查询时，使用 `created_at` 索引
- 组合查询使用组合索引
- 避免全表扫描

### 6.2 写入优化
- 批量插入使用 `INSERT ... VALUES (...), (...), (...)`
- 大批量更新使用事务批量提交

### 6.3 归档策略
- 历史流水表（如：`inventory_transaction`, `wallet_transaction`）定期归档
- 已完成订单定期归档

## 7. 数据迁移说明

### 7.1 Flyway 迁移脚本
- V1: 门店表
- V2: 员工表
- V3: 客户表
- V4: 供应商与采购模块
- V5: 商品分类表
- V6: 商品表及相关表
- V7: 库存表及相关表
- V8: 订单表及相关表
- V9: 价格体系相关表
- V10: 客户钱包相关表
- V11: 客户身份与地址管理
- V12: 积分商城模块
- V13: 支付风控模块
- V14: 赊销相关表
- V15: 员工绩效相关表

### 7.2 迁移顺序
- 必须按版本号顺序执行
- 先创建基础表（门店、员工、客户），再创建业务表
- 外键依赖的表必须在被依赖表之后创建

## 8. 枚举值完整列表

### 8.1 员工角色 (staff.role)
- `ADMIN`: 管理员
- `MANAGER`: 店长
- `STAFF`: 员工
- `DRIVER`: 司机

### 8.2 客户类型 (customer.type)
- `1`: 普通客户
- `2`: VIP客户

### 8.3 VIP等级 (customer.vip_level)
- `1`: 普通VIP
- `2`: 银卡VIP
- `3`: 金卡VIP
- `4`: 钻石VIP

### 8.4 订单类型 (order.order_type)
- `1`: 线下POS
- `2`: 小程序自提
- `3`: 小程序配送

### 8.5 配送方式 (order.delivery_type / order_delivery.delivery_method)
- `1`: 门店自提
- `2`: 同城配送
- `3`: 快递发货

### 8.6 订单状态 (order.order_status)
- `1`: 待支付
- `2`: 已支付
- `3`: 待配送
- `4`: 配送中
- `5`: 已完成
- `6`: 已取消
- `7`: 已退款

### 8.7 支付方式 (order.payment_method)
- `1`: 现金
- `2`: 微信
- `3`: 支付宝
- `4`: 钱包
- `5`: 赊销

### 8.8 采购订单状态 (purchase_order.status)
- `1`: 待审核
- `2`: 已审核
- `3`: 已入库
- `4`: 已取消

### 8.9 商品上架状态 (product.is_online)
- `1`: 上架
- `0`: 下架

### 8.10 钱包类型 (customer_wallet.wallet_type)
- `1`: 本金
- `2`: 专用金
- `3`: 积分

### 8.11 客户身份类型 (customer_identity.identity_type)
- `WECHAT_OPENID`: 微信OpenID
- `PHONE`: 手机号
- `OTHER`: 其他

## 9. 后续扩展建议

### 9.1 可能需要新增的表
- 退货单表（如果需要独立管理退货流程）
- 促销活动表（如果需要管理促销活动）
- 优惠券表（如果需要管理优惠券）

### 9.2 可能需要新增的字段
- 订单表：`cancel_reason`（取消原因）
- 商品表：`barcode`（条形码）
- 客户表：`birthday`（生日，用于生日营销）

### 9.3 扩展注意事项
- 新增字段前必须明确业务需求
- 新增表前必须设计好表关系
- 所有变更必须通过 Flyway 迁移脚本管理

## 10. 表统计

### 10.1 表数量统计
- **基础数据表**：3张（store, staff, customer）
- **供应商与采购模块**：3张（supplier, purchase_order, purchase_order_item）
- **商品相关表**：5张（product_category, product, product_unit_conversion, product_package, product_package_item）
- **库存相关表**：5张（inventory, inventory_transfer, inventory_adjustment, inventory_transaction, inventory_warning）
- **订单相关表**：3张（order, order_item, order_delivery）
- **价格体系相关表**：2张（product_price, commission_rule）
- **客户钱包相关表**：2张（customer_wallet, wallet_transaction）
- **客户身份与地址管理**：2张（customer_identity, customer_address）
- **积分商城模块**：2张（points_goods, points_exchange_order）
- **支付风控模块**：1张（wallet_auth_code）
- **赊销相关表**：2张（credit_account, credit_statement）
- **员工绩效相关表**：1张（staff_performance）

**总计：31张表**
