-- ============================================
-- V6: 订单表及相关表
-- ============================================

-- 订单表
CREATE TABLE IF NOT EXISTS `order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `customer_id` BIGINT COMMENT '客户ID（可为空，支持散客）',
    `order_type` TINYINT NOT NULL COMMENT '订单类型：1-线下POS，2-小程序自提，3-小程序配送',
    `order_status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态：1-待支付，2-已支付，3-待配送，4-配送中，5-已完成，6-已取消，7-已退款',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额（元）',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额（元）',
    `pay_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额（元）',
    `payment_method` TINYINT COMMENT '支付方式：1-现金，2-微信，3-支付宝，4-钱包，5-赊销',
    `staff_id` BIGINT COMMENT '销售员工ID（线下POS必填）',
    `delivery_address` VARCHAR(500) COMMENT '配送地址（配送订单必填）',
    `delivery_phone` VARCHAR(20) COMMENT '配送联系电话',
    `delivery_status` TINYINT COMMENT '配送状态：1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收（仅配送订单有效）',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_store_id` (`store_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_type` (`order_type`),
    KEY `idx_order_status` (`order_status`),
    KEY `idx_staff_id` (`staff_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_order_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_order_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS `order_item` (
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

-- 配送表
CREATE TABLE IF NOT EXISTS `order_delivery` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `delivery_no` VARCHAR(50) COMMENT '配送单号',
    `delivery_status` TINYINT NOT NULL DEFAULT 1 COMMENT '配送状态：1-待审核，2-已审核，3-配送中，4-已送达，5-已拒收',
    `delivery_address` VARCHAR(500) NOT NULL COMMENT '配送地址',
    `delivery_phone` VARCHAR(20) NOT NULL COMMENT '配送联系电话',
    `delivery_staff_id` BIGINT COMMENT '配送员工ID',
    `audit_status` TINYINT DEFAULT 1 COMMENT '审核状态：1-待审核，2-已通过，3-已拒绝',
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
    KEY `idx_delivery_status` (`delivery_status`),
    KEY `idx_audit_status` (`audit_status`),
    CONSTRAINT `fk_delivery_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_delivery_staff` FOREIGN KEY (`delivery_staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送表';











