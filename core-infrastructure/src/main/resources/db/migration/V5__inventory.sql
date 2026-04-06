-- ============================================
-- V5: 库存表及相关表
-- ============================================

-- 库存表（按门店+商品）
CREATE TABLE IF NOT EXISTS `inventory` (
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

-- 库存调拨表
CREATE TABLE IF NOT EXISTS `inventory_transfer` (
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

-- 库存调整表
CREATE TABLE IF NOT EXISTS `inventory_adjustment` (
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

-- 库存流水表
CREATE TABLE IF NOT EXISTS `inventory_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `transaction_type` TINYINT NOT NULL COMMENT '流水类型：1-入库，2-出库，3-调拨出，4-调拨入，5-调整，6-销售，7-退货',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量（正数为增加，负数为减少）',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `quantity_after` DECIMAL(10,4) NOT NULL COMMENT '操作后库存数量',
    `reference_type` VARCHAR(50) COMMENT '关联类型（如：ORDER-订单，TRANSFER-调拨，ADJUSTMENT-调整）',
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

-- 库存预警表
CREATE TABLE IF NOT EXISTS `inventory_warning` (
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











