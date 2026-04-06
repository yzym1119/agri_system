-- ============================================
-- V37: 售后模块表结构
-- ============================================

-- 售后主表
CREATE TABLE IF NOT EXISTS `after_sales` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `after_sales_no` VARCHAR(50) NOT NULL COMMENT '售后单号',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `customer_id` BIGINT COMMENT '客户ID（可为空，POS散客）',
    `store_id` BIGINT NOT NULL COMMENT '门店ID',
    `type` TINYINT NOT NULL COMMENT '售后类型：1-订单取消/拦截，2-退货退款',
    `status` TINYINT NOT NULL COMMENT '状态：10-待审核，40-待退款，50-已完成，90-已驳回',
    `refund_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '应退金额（元）',
    `reason` VARCHAR(500) COMMENT '申请原因',
    `audit_remark` VARCHAR(500) COMMENT '审核备注',
    `audit_by` BIGINT COMMENT '审核人ID',
    `audit_time` DATETIME COMMENT '审核时间',
    `refund_by` BIGINT COMMENT '退款操作人ID',
    `refund_time` DATETIME COMMENT '退款时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_after_sales_no` (`after_sales_no`),
    KEY `idx_order_status` (`order_id`, `status`),
    KEY `idx_customer` (`customer_id`),
    CONSTRAINT `fk_after_sales_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后主表';

-- 售后明细表
CREATE TABLE IF NOT EXISTS `after_sales_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `after_sales_id` BIGINT NOT NULL COMMENT '售后单ID',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '退货数量',
    `refund_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '明细退款金额（元）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_order_item_id` (`order_item_id`),
    CONSTRAINT `fk_after_sales_item_main` FOREIGN KEY (`after_sales_id`) REFERENCES `after_sales` (`id`),
    CONSTRAINT `fk_after_sales_item_order_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
    CONSTRAINT `fk_after_sales_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后明细表';








