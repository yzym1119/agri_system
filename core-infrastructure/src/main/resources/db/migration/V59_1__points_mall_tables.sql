-- ============================================
-- V59.1: 积分商城基础表
-- ============================================
-- V60 会继续为 points_exchange_order 添加 pickup_code 字段。

CREATE TABLE IF NOT EXISTS `points_goods` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '礼品编码',
    `name` VARCHAR(100) NOT NULL COMMENT '礼品名称',
    `points` INT NOT NULL COMMENT '所需积分',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `image_url` VARCHAR(500) COMMENT '图片地址',
    `description` VARCHAR(1000) COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 2 COMMENT '状态：1-上架，2-下架',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_points_goods_code` (`code`),
    KEY `idx_points_goods_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分礼品表';

CREATE TABLE IF NOT EXISTS `points_exchange_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exchange_no` VARCHAR(50) NOT NULL COMMENT '兑换单号',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `points_goods_id` BIGINT NOT NULL COMMENT '积分礼品ID',
    `points` INT NOT NULL COMMENT '单件所需积分',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '兑换数量',
    `receiver_name` VARCHAR(100) COMMENT '收货人',
    `receiver_phone` VARCHAR(30) COMMENT '联系电话',
    `receiver_address` VARCHAR(500) COMMENT '收货地址',
    `status` TINYINT NOT NULL DEFAULT 10 COMMENT '状态：10-待审核，20-备货中，30-待自提，50-已完成，90-已拒绝',
    `logistics_company` VARCHAR(100) COMMENT '物流公司',
    `tracking_no` VARCHAR(100) COMMENT '物流单号',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_points_exchange_no` (`exchange_no`),
    KEY `idx_points_order_customer` (`customer_id`),
    KEY `idx_points_order_goods` (`points_goods_id`),
    KEY `idx_points_order_status` (`status`),
    KEY `idx_points_order_created_at` (`created_at`),
    CONSTRAINT `fk_points_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_points_order_goods` FOREIGN KEY (`points_goods_id`) REFERENCES `points_goods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换订单表';
