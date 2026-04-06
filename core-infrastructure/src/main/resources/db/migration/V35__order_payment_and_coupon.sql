-- ============================================
-- V35: 订单支付增强 + 红包券体系（最终修正版）
-- ============================================

-- 客户红包/代金券表
CREATE TABLE IF NOT EXISTS `customer_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '关联客户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '券名称',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '面额（元）',
    `min_order_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额（元）',
    `start_time` DATETIME COMMENT '生效时间',
    `end_time` DATETIME COMMENT '过期时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-未使用, 2-已使用, 3-已过期, 4-已作废',
    `limit_category_id` BIGINT COMMENT '限制商品分类ID',
    `limit_product_id` BIGINT COMMENT '限制具体商品ID',
    `used_order_id` BIGINT COMMENT '使用时关联的订单ID',
    `used_time` DATETIME COMMENT '使用时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_limit_category` (`limit_category_id`),
    KEY `idx_limit_product` (`limit_product_id`),
    KEY `idx_used_order` (`used_order_id`),
    KEY `idx_customer_status` (`customer_id`, `status`),
    CONSTRAINT `fk_coupon_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_coupon_limit_category` FOREIGN KEY (`limit_category_id`) REFERENCES `product_category` (`id`),
    CONSTRAINT `fk_coupon_limit_product` FOREIGN KEY (`limit_product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_coupon_used_order` FOREIGN KEY (`used_order_id`) REFERENCES `order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户红包/代金券表';

-- 订单支付明细表（支持混合支付）
CREATE TABLE IF NOT EXISTS `order_payment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `payment_type` TINYINT NOT NULL COMMENT '支付类型：1-微信, 2-支付宝, 3-本金余额, 4-专用金余额, 5-红包/优惠券, 6-赊销',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '该方式支付金额（元）',
    `coupon_id` BIGINT COMMENT '红包/优惠券ID（当payment_type=5时填充）',
    `outer_trans_no` VARCHAR(100) COMMENT '外部支付流水号（微信/支付宝）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_coupon_id` (`coupon_id`),
    CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`),
    CONSTRAINT `fk_payment_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `customer_coupon` (`id`),
    CONSTRAINT `chk_payment_no_points` CHECK (`payment_type` <> 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单支付明细表';

-- 订单表结构调整：新增自提码、优惠券抵扣金额，并更新主支付方式定义
ALTER TABLE `order`
    ADD COLUMN `pickup_code` VARCHAR(10) COMMENT '自提核销码（仅自提订单生成）' AFTER `remark`;

ALTER TABLE `order`
    ADD UNIQUE KEY `uk_pickup_code` (`pickup_code`);

ALTER TABLE `order`
    ADD COLUMN `coupon_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣总额（元）' AFTER `discount_amount`;

ALTER TABLE `order`
    MODIFY COLUMN `payment_method` TINYINT COMMENT '主支付方式：1-现金, 2-微信, 3-支付宝, 5-赊销, 10-混合支付(详见order_payment表)';








