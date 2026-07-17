-- ============================================
-- 农资行业进销存系统 - 完整数据库 DDL
-- 数据库: MySQL 8.x
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci
-- 版本: 2.0 (重构版)
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 基础数据表
-- ============================================

-- 1.1 门店表
DROP TABLE IF EXISTS `store`;
CREATE TABLE `store` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '门店编码',
    `name` VARCHAR(100) NOT NULL COMMENT '门店名称',
    `address` VARCHAR(500) COMMENT '门店地址',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `manager_id` BIGINT COMMENT '店长ID（关联staff表）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_code` (`code`),
    KEY `idx_store_status` (`status`),
    KEY `idx_store_manager` (`manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店表';

-- 1.2 员工表
DROP TABLE IF EXISTS `staff`;
CREATE TABLE `staff` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '员工编码',
    `name` VARCHAR(50) NOT NULL COMMENT '员工姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `id_card` VARCHAR(18) COMMENT '身份证号',
    `store_id` BIGINT NOT NULL COMMENT '所属门店ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：ADMIN-管理员，MANAGER-店长，STAFF-员工，DRIVER-司机',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-在职，2-离职',
    `hire_date` DATE COMMENT '入职日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_staff_code` (`code`),
    UNIQUE KEY `uk_staff_phone` (`phone`),
    KEY `idx_staff_store` (`store_id`),
    KEY `idx_staff_status` (`status`),
    KEY `idx_staff_role` (`role`),
    CONSTRAINT `fk_staff_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';

-- 1.3 客户表
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '客户编码',
    `name` VARCHAR(100) NOT NULL COMMENT '客户姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '客户类型：1-普通客户，2-VIP客户',
    `vip_level` TINYINT COMMENT 'VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）',
    `vip_start_date` DATE COMMENT 'VIP开始日期',
    `vip_end_date` DATE COMMENT 'VIP结束日期',
    `address` VARCHAR(500) COMMENT '地址',
    `id_card` VARCHAR(18) COMMENT '身份证号',
    `credit_limit` DECIMAL(10,2) DEFAULT 0.00 COMMENT '赊销额度（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_code` (`code`),
    UNIQUE KEY `uk_customer_phone` (`phone`),
    KEY `idx_customer_type` (`type`),
    KEY `idx_customer_status` (`status`),
    KEY `idx_customer_vip_level` (`vip_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表';

-- ============================================
-- 2. 供应商与采购模块
-- ============================================

-- 2.1 供应商表
DROP TABLE IF EXISTS `supplier`;
CREATE TABLE `supplier` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '供应商编码',
    `name` VARCHAR(200) NOT NULL COMMENT '供应商名称',
    `contact_name` VARCHAR(50) COMMENT '联系人姓名',
    `contact_phone` VARCHAR(20) COMMENT '联系人电话',
    `bank_account` VARCHAR(100) COMMENT '银行账户',
    `bank_name` VARCHAR(100) COMMENT '开户银行',
    `address` VARCHAR(500) COMMENT '供应商地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_code` (`code`),
    KEY `idx_supplier_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';

-- 2.2 采购订单表
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `purchase_no` VARCHAR(50) NOT NULL COMMENT '采购单号',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `store_id` BIGINT NOT NULL COMMENT '入库门店ID',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总金额（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待审核，2-已审核，3-已入库，4-已取消',
    `purchase_date` DATE COMMENT '采购日期',
    `expected_arrival_date` DATE COMMENT '预计到货日期',
    `arrival_date` DATE COMMENT '实际到货日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_purchase_no` (`purchase_no`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_status` (`status`),
    KEY `idx_purchase_date` (`purchase_date`),
    CONSTRAINT `fk_purchase_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
    CONSTRAINT `fk_purchase_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单表';

-- 2.3 采购订单明细表
DROP TABLE IF EXISTS `purchase_order_item`;
CREATE TABLE `purchase_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `purchase_order_id` BIGINT NOT NULL COMMENT '采购订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `buy_price` DECIMAL(10,2) NOT NULL COMMENT '本次真实进价（元，用于计算加权平均成本）',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '小计金额（元）',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_purchase_order_id` (`purchase_order_id`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_purchase_item_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
    CONSTRAINT `fk_purchase_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单明细表';

-- ============================================
-- 3. 商品相关表
-- ============================================

-- 3.1 商品分类表（树形结构）
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '分类编码',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT COMMENT '父分类ID（为空表示顶级分类）',
    `level` INT NOT NULL DEFAULT 1 COMMENT '分类层级（1-一级，2-二级，以此类推）',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_level` (`level`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 3.2 商品表（重构版）
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '商品编码',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `category_id` BIGINT COMMENT '商品分类ID（外键关联product_category）',
    `specification` VARCHAR(100) COMMENT '规格（如：500g/袋）',
    `dosage_form` VARCHAR(50) COMMENT '剂型（如：颗粒剂、水剂、粉剂）',
    `manufacturer` VARCHAR(200) COMMENT '生产厂家',
    `main_unit` VARCHAR(20) NOT NULL COMMENT '主单位（如：袋、瓶、箱）',
    `aux_unit` VARCHAR(20) COMMENT '辅单位（如：箱、件）',
    `main_to_aux_ratio` DECIMAL(10,4) COMMENT '主辅单位换算比例（1主单位=？辅单位，如：1箱=20袋，则比例为20）',
    `suggested_retail_price` DECIMAL(10,2) COMMENT '建议零售价（元）',
    `vip_price` DECIMAL(10,2) COMMENT 'VIP价格（元）',
    `min_price` DECIMAL(10,2) COMMENT '最低限价（元）',
    `avg_cost_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '移动加权平均成本（元，由采购入库触发计算）',
    `image_url` VARCHAR(500) COMMENT '主图URL（列表/详情共用）',
    `detail_text` TEXT COMMENT '详情文字（纯文字详情）',
    `is_online` TINYINT NOT NULL DEFAULT 0 COMMENT '是否上架：1-上架，0-下架',
    `online_price` DECIMAL(10,2) COMMENT '线上价（元，可为空，为空则取零售价）',
    `base_shipping_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '基础运费（元，默认0）',
    `allow_special_fund` TINYINT NOT NULL DEFAULT 0 COMMENT '允许专用金：1-允许，0-不允许',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删，1-已删',
    `remark` VARCHAR(500) COMMENT '备注',
    `barcode` VARCHAR(50) COMMENT '条形码（EAN-13/Code128）',
    `registration_no` VARCHAR(100) COMMENT '农药/肥料登记证号',
    `storage_conditions` VARCHAR(200) COMMENT '存储条件（如：阴凉干燥处、避光保存）',
    `shelf_life_days` INT COMMENT '保质期（天）',
    `usage_method` TEXT COMMENT '使用方法/稀释比例',
    `min_stock` DECIMAL(10,2) COMMENT '最低库存预警值',
    `max_stock` DECIMAL(10,2) COMMENT '最高库存预警值',
    `reorder_quantity` DECIMAL(10,2) COMMENT '建议补货数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_code` (`code`),
    KEY `idx_product_name` (`name`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_product_status` (`status`),
    KEY `idx_is_online` (`is_online`),
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 3.3 商品单位换算表（支持多级单位换算）
DROP TABLE IF EXISTS `product_unit_conversion`;
CREATE TABLE `product_unit_conversion` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `from_unit` VARCHAR(20) NOT NULL COMMENT '源单位',
    `to_unit` VARCHAR(20) NOT NULL COMMENT '目标单位',
    `conversion_ratio` DECIMAL(10,4) NOT NULL COMMENT '换算比例（1源单位=？目标单位）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_unit` (`product_id`, `from_unit`, `to_unit`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_unit_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品单位换算表';

-- 3.4 套餐/配方表
DROP TABLE IF EXISTS `product_package`;
CREATE TABLE `product_package` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '套餐编码',
    `name` VARCHAR(200) NOT NULL COMMENT '套餐名称',
    `suggested_retail_price` DECIMAL(10,2) COMMENT '建议零售价（元）',
    `vip_price` DECIMAL(10,2) COMMENT 'VIP价格（元）',
    `min_price` DECIMAL(10,2) COMMENT '最低限价（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_package_code` (`code`),
    KEY `idx_package_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐/配方表';

-- 3.5 套餐明细表
DROP TABLE IF EXISTS `product_package_item`;
CREATE TABLE `product_package_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_id` BIGINT NOT NULL COMMENT '套餐ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_package_id` (`package_id`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_package_item_package` FOREIGN KEY (`package_id`) REFERENCES `product_package` (`id`),
    CONSTRAINT `fk_package_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐明细表';

-- ============================================
-- 4. 库存相关表
-- ============================================

-- 4.1 库存表（按门店+商品）
DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '库存数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `production_date` DATE COMMENT '生产日期',
    `expiry_date` DATE COMMENT '有效期至',
    `batch_no` VARCHAR(50) COMMENT '批次号',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_store_product` (`store_id`, `product_id`, `batch_no`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_expiry_date` (`expiry_date`),
    CONSTRAINT `fk_inventory_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_inventory_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 4.2 库存调拨表
DROP TABLE IF EXISTS `inventory_transfer`;
CREATE TABLE `inventory_transfer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `transfer_no` VARCHAR(50) NOT NULL COMMENT '调拨单号',
    `from_store_id` BIGINT NOT NULL COMMENT '调出门店ID',
    `to_store_id` BIGINT NOT NULL COMMENT '调入门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '调拨数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待调出，2-已调出，3-已入库，4-已取消',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_no` (`transfer_no`),
    KEY `idx_from_store` (`from_store_id`),
    KEY `idx_to_store` (`to_store_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_transfer_from_store` FOREIGN KEY (`from_store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_transfer_to_store` FOREIGN KEY (`to_store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_transfer_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨表';

-- 4.3 库存调整表
DROP TABLE IF EXISTS `inventory_adjustment`;
CREATE TABLE `inventory_adjustment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `adjustment_no` VARCHAR(50) NOT NULL COMMENT '调整单号',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `adjustment_type` TINYINT NOT NULL COMMENT '调整类型：1-盘盈，2-盘亏，3-报损，4-其他',
    `quantity_before` DECIMAL(10,4) NOT NULL COMMENT '调整前数量',
    `quantity_after` DECIMAL(10,4) NOT NULL COMMENT '调整后数量',
    `quantity_diff` DECIMAL(10,4) NOT NULL COMMENT '调整数量（正数为增加，负数为减少）',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `reason` VARCHAR(500) COMMENT '调整原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_adjustment_no` (`adjustment_no`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_adjustment_type` (`adjustment_type`),
    CONSTRAINT `fk_adjustment_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_adjustment_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调整表';

-- 4.4 库存流水表
DROP TABLE IF EXISTS `inventory_transaction`;
CREATE TABLE `inventory_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `transaction_type` TINYINT NOT NULL COMMENT '流水类型：1-入库，2-出库，3-调拨出，4-调拨入，5-调整，6-销售，7-退货',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量（正数为增加，负数为减少）',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `quantity_after` DECIMAL(10,4) NOT NULL COMMENT '操作后库存数量',
    `reference_type` VARCHAR(50) COMMENT '关联类型（如：ORDER-订单，TRANSFER-调拨，ADJUSTMENT-调整，PURCHASE-采购）',
    `reference_id` BIGINT COMMENT '关联ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '操作人ID',
    PRIMARY KEY (`id`),
    KEY `idx_store_product` (`store_id`, `product_id`),
    KEY `idx_transaction_type` (`transaction_type`),
    KEY `idx_reference` (`reference_type`, `reference_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_transaction_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_transaction_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

-- 4.5 库存预警表
DROP TABLE IF EXISTS `inventory_warning`;
CREATE TABLE `inventory_warning` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `warning_type` TINYINT NOT NULL COMMENT '预警类型：1-库存不足，2-库存积压，3-即将过期，4-已过期',
    `current_quantity` DECIMAL(10,4) NOT NULL COMMENT '当前库存数量',
    `threshold_quantity` DECIMAL(10,4) COMMENT '预警阈值数量',
    `expiry_date` DATE COMMENT '过期日期（仅过期预警有效）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待处理，2-已处理，3-已忽略',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_store_product` (`store_id`, `product_id`),
    KEY `idx_warning_type` (`warning_type`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_warning_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_warning_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预警表';

-- ============================================
-- 5. 订单相关表
-- ============================================

-- 5.1 订单表（增强版）
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `store_id` BIGINT NOT NULL COMMENT '门店ID（线上快递订单通常为总店ID，自提订单为具体门店ID）',
    `customer_id` BIGINT COMMENT '客户ID（可为空，支持散客）',
    `order_type` TINYINT NOT NULL COMMENT '订单类型：1-线下POS，2-小程序自提，3-小程序配送',
    `delivery_type` TINYINT COMMENT '配送方式：1-门店自提，2-同城配送，3-快递发货（仅小程序订单有效）',
    `order_status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态：1-待支付，2-已支付，3-待配送，4-配送中，5-已完成，6-已取消，7-已退款',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额（元）',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额（元）',
    `freight_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '运费金额（元）',
    `pay_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额（元）',
    `payment_method` TINYINT COMMENT '支付方式：1-现金，2-微信，3-支付宝，4-钱包，5-赊销',
    `staff_id` BIGINT COMMENT '销售员工ID（线下POS必填）',
    `receiver_name` VARCHAR(50) COMMENT '收货人姓名（下单时固化）',
    `receiver_phone` VARCHAR(20) COMMENT '收货人电话（下单时固化）',
    `receiver_address` VARCHAR(500) COMMENT '收货地址（下单时固化）',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_type` (`order_type`),
    KEY `idx_delivery_type` (`delivery_type`),
    KEY `idx_order_status` (`order_status`),
    KEY `idx_staff_id` (`staff_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_order_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_order_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 5.2 订单明细表
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT COMMENT '商品ID（单品订单）',
    `package_id` BIGINT COMMENT '套餐ID（套餐订单）',
    `item_type` TINYINT NOT NULL COMMENT '明细类型：1-商品，2-套餐',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品/套餐名称（冗余字段，便于查询）',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `unit_price` DECIMAL(10,2) NOT NULL COMMENT '单价（元）',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '小计金额（元）',
    `commission_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '提成金额（元）',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_package_id` (`package_id`),
    CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_order_item_package` FOREIGN KEY (`package_id`) REFERENCES `product_package` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- 5.3 配送表（增强版）
DROP TABLE IF EXISTS `order_delivery`;
CREATE TABLE `order_delivery` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `delivery_no` VARCHAR(50) COMMENT '配送单号',
    `delivery_method` TINYINT COMMENT '配送方式：1-门店自提，2-同城配送，3-快递发货（同订单delivery_type）',
    `delivery_status` TINYINT NOT NULL DEFAULT 1 COMMENT '配送状态：1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收',
    `delivery_address` VARCHAR(500) NOT NULL COMMENT '配送地址',
    `delivery_phone` VARCHAR(20) NOT NULL COMMENT '配送联系电话',
    `delivery_staff_id` BIGINT COMMENT '配送员ID（同城配送时使用）',
    `logistics_company` VARCHAR(100) COMMENT '快递公司（快递发货时使用）',
    `tracking_no` VARCHAR(100) COMMENT '快递单号（快递发货时使用，允许手动输入）',
    `audit_status` TINYINT DEFAULT 1 COMMENT '审核状态：1-待审核，2-已通过，3-已拒绝（同城配送时使用）',
    `audit_remark` VARCHAR(500) COMMENT '审核备注',
    `audit_at` DATETIME COMMENT '审核时间',
    `audit_by` BIGINT COMMENT '审核人ID',
    `delivery_at` DATETIME COMMENT '配送时间',
    `received_at` DATETIME COMMENT '签收时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_no` (`delivery_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_delivery_method` (`delivery_method`),
    KEY `idx_delivery_status` (`delivery_status`),
    KEY `idx_audit_status` (`audit_status`),
    CONSTRAINT `fk_delivery_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_delivery_staff` FOREIGN KEY (`delivery_staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送表';

-- ============================================
-- 6. 价格体系相关表
-- ============================================

-- 6.1 商品价格表（支持按门店、客户类型差异化定价）
DROP TABLE IF EXISTS `product_price`;
CREATE TABLE `product_price` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `store_id` BIGINT COMMENT '门店ID（为空表示全局价格）',
    `customer_type` TINYINT COMMENT '客户类型：1-普通客户，2-VIP客户（为空表示通用价格）',
    `vip_level` TINYINT COMMENT 'VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）',
    `price_type` TINYINT NOT NULL COMMENT '价格类型：1-建议零售价，2-VIP价，3-最低限价，4-进价',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格（元）',
    `effective_date` DATE COMMENT '生效日期',
    `expiry_date` DATE COMMENT '失效日期',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-有效，2-无效',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_customer_type` (`customer_type`),
    KEY `idx_price_type` (`price_type`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_price_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_price_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品价格表';

-- 6.2 提成规则表
DROP TABLE IF EXISTS `commission_rule`;
CREATE TABLE `commission_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` BIGINT COMMENT '商品ID（为空表示通用规则）',
    `staff_id` BIGINT COMMENT '员工ID（为空表示通用规则）',
    `rule_type` TINYINT NOT NULL COMMENT '规则类型：1-固定金额，2-毛利百分比',
    `fixed_amount` DECIMAL(10,2) COMMENT '固定金额（元，规则类型为1时有效）',
    `profit_percentage` DECIMAL(5,2) COMMENT '毛利百分比（%，规则类型为2时有效，如15.50表示15.5%）',
    `priority` INT DEFAULT 0 COMMENT '优先级（数字越大优先级越高）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-有效，2-无效',
    `effective_date` DATE COMMENT '生效日期',
    `expiry_date` DATE COMMENT '失效日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_staff_id` (`staff_id`),
    KEY `idx_rule_type` (`rule_type`),
    KEY `idx_status` (`status`),
    KEY `idx_priority` (`priority`),
    CONSTRAINT `fk_commission_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_commission_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提成规则表';

-- ============================================
-- 7. 客户钱包相关表
-- ============================================

-- 7.1 客户钱包表
DROP TABLE IF EXISTS `customer_wallet`;
CREATE TABLE `customer_wallet` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `wallet_type` TINYINT NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
    `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_wallet_type` (`customer_id`, `wallet_type`),
    KEY `idx_customer_id` (`customer_id`),
    CONSTRAINT `fk_wallet_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户钱包表';

-- 7.2 钱包交易记录表
DROP TABLE IF EXISTS `wallet_transaction`;
CREATE TABLE `wallet_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `wallet_type` TINYINT NOT NULL COMMENT '钱包类型：1-本金，2-专用金，3-积分',
    `transaction_type` TINYINT NOT NULL COMMENT '交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '金额（正数为增加，负数为减少）',
    `balance_before` DECIMAL(10,2) NOT NULL COMMENT '交易前余额',
    `balance_after` DECIMAL(10,2) NOT NULL COMMENT '交易后余额',
    `reference_type` VARCHAR(50) COMMENT '关联类型（如：ORDER-订单，RECHARGE-充值）',
    `reference_id` BIGINT COMMENT '关联ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '操作人ID',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_wallet_type` (`wallet_type`),
    KEY `idx_transaction_type` (`transaction_type`),
    KEY `idx_reference` (`reference_type`, `reference_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_wallet_transaction_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易记录表';

-- ============================================
-- 8. 客户身份与地址管理
-- ============================================

-- 8.1 客户身份表（支持多端登录）
DROP TABLE IF EXISTS `customer_identity`;
CREATE TABLE `customer_identity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `identity_type` VARCHAR(20) NOT NULL COMMENT '身份类型：WECHAT_OPENID-微信OpenID，PHONE-手机号，OTHER-其他',
    `identity_value` VARCHAR(200) NOT NULL COMMENT '身份值（OpenID/手机号等）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_type_value` (`identity_type`, `identity_value`),
    KEY `idx_customer_id` (`customer_id`),
    CONSTRAINT `fk_identity_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户身份表';

-- 8.2 客户地址表（小程序收货地址管理）
DROP TABLE IF EXISTS `customer_address`;
CREATE TABLE `customer_address` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
    `province` VARCHAR(50) COMMENT '省份',
    `city` VARCHAR(50) COMMENT '城市',
    `district` VARCHAR(50) COMMENT '区县',
    `address` VARCHAR(500) NOT NULL COMMENT '详细地址',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认地址：1-是，0-否',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_is_default` (`is_default`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_address_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户地址表';

-- ============================================
-- 9. 积分商城模块
-- ============================================

-- 9.1 积分礼品表
DROP TABLE IF EXISTS `points_goods`;
CREATE TABLE `points_goods` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '礼品编码',
    `name` VARCHAR(200) NOT NULL COMMENT '礼品名称',
    `points` INT NOT NULL COMMENT '所需积分数',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `image_url` VARCHAR(500) COMMENT '礼品图片URL',
    `description` TEXT COMMENT '礼品描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-上架，2-下架',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_points_goods_code` (`code`),
    KEY `idx_status` (`status`),
    KEY `idx_points` (`points`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分礼品表';

-- 9.2 积分兑换订单表
DROP TABLE IF EXISTS `points_exchange_order`;
CREATE TABLE `points_exchange_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exchange_no` VARCHAR(50) NOT NULL COMMENT '兑换单号',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `points_goods_id` BIGINT NOT NULL COMMENT '积分礼品ID',
    `points` INT NOT NULL COMMENT '消耗积分数',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '兑换数量',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
    `receiver_address` VARCHAR(500) NOT NULL COMMENT '收货地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待发货，2-已发货，3-已完成，4-已取消',
    `logistics_company` VARCHAR(100) COMMENT '快递公司',
    `tracking_no` VARCHAR(100) COMMENT '快递单号',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exchange_no` (`exchange_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_points_goods_id` (`points_goods_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_exchange_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_exchange_points_goods` FOREIGN KEY (`points_goods_id`) REFERENCES `points_goods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换订单表';

-- ============================================
-- 10. 支付风控模块
-- ============================================

-- 10.1 钱包支付验证码表（线下余额支付验证码）
DROP TABLE IF EXISTS `wallet_auth_code`;
CREATE TABLE `wallet_auth_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `order_id` BIGINT COMMENT '订单ID',
    `auth_code` VARCHAR(10) NOT NULL COMMENT '验证码（6位数字）',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-未使用，2-已使用，3-已过期',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    `used_at` DATETIME COMMENT '使用时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_auth_code` (`auth_code`),
    KEY `idx_status` (`status`),
    KEY `idx_expire_at` (`expire_at`),
    CONSTRAINT `fk_auth_code_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_auth_code_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包支付验证码表';

-- ============================================
-- 11. 赊销相关表
-- ============================================

-- 11.1 赊销台账表
DROP TABLE IF EXISTS `credit_account`;
CREATE TABLE `credit_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `credit_amount` DECIMAL(10,2) NOT NULL COMMENT '赊销金额（元）',
    `paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已还金额（元）',
    `remaining_amount` DECIMAL(10,2) NOT NULL COMMENT '剩余金额（元）',
    `due_date` DATE COMMENT '到期日期',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-未还清，2-已还清，3-已逾期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_status` (`status`),
    KEY `idx_due_date` (`due_date`),
    CONSTRAINT `fk_credit_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_credit_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赊销台账表';

-- 11.2 对账历史表
DROP TABLE IF EXISTS `credit_statement`;
CREATE TABLE `credit_statement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `statement_no` VARCHAR(50) NOT NULL COMMENT '对账单号',
    `statement_date` DATE NOT NULL COMMENT '对账日期',
    `start_date` DATE NOT NULL COMMENT '对账开始日期',
    `end_date` DATE NOT NULL COMMENT '对账结束日期',
    `total_credit_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '赊销总额（元）',
    `total_paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已还总额（元）',
    `total_remaining_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '剩余总额（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待确认，2-已确认，3-已争议',
    `confirmed_at` DATETIME COMMENT '确认时间',
    `confirmed_by` BIGINT COMMENT '确认人ID（客户或员工）',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_statement_no` (`statement_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_statement_date` (`statement_date`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_statement_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账历史表';

-- ============================================
-- 12. 员工绩效相关表
-- ============================================

-- 12.1 员工绩效记录表
DROP TABLE IF EXISTS `staff_performance`;
CREATE TABLE `staff_performance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '员工ID',
    `performance_type` TINYINT NOT NULL COMMENT '绩效类型：1-推荐行为，2-服务行为',
    `order_id` BIGINT COMMENT '关联订单ID',
    `customer_id` BIGINT COMMENT '关联客户ID',
    `product_id` BIGINT COMMENT '关联商品ID',
    `performance_score` INT COMMENT '绩效分数',
    `commission_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '提成金额（元）',
    `performance_date` DATE NOT NULL COMMENT '绩效日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    KEY `idx_staff_id` (`staff_id`),
    KEY `idx_performance_type` (`performance_type`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_performance_date` (`performance_date`),
    CONSTRAINT `fk_performance_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
    CONSTRAINT `fk_performance_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_performance_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_performance_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工绩效记录表';

SET FOREIGN_KEY_CHECKS = 1;
