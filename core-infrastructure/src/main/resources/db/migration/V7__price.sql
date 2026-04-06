-- ============================================
-- V7: 价格体系相关表
-- ============================================

-- 商品价格表（支持按门店、客户类型差异化定价）
CREATE TABLE IF NOT EXISTS `product_price` (
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

-- 提成规则表
CREATE TABLE IF NOT EXISTS `commission_rule` (
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











