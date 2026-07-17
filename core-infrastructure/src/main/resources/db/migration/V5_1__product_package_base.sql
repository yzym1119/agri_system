-- ============================================
-- V5.1: 套餐主表提前创建
-- ============================================
-- V6 订单明细存在 package_id 外键，必须在 V6 前保证 product_package 已存在。
-- V11 仍保留套餐完整迁移，CREATE IF NOT EXISTS 会跳过主表并继续创建套餐明细表。

CREATE TABLE IF NOT EXISTS `product_package` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '套餐编码（系统生成、唯一）',
    `name` VARCHAR(200) NOT NULL COMMENT '套餐名称',
    `suggested_retail_price` DECIMAL(10,2) NOT NULL COMMENT '零售价（必填）',
    `vip_price` DECIMAL(10,2) COMMENT 'VIP价（可空，null=不启用VIP价）',
    `min_price` DECIMAL(10,2) COMMENT '最低售价',
    `status` INT NOT NULL DEFAULT 0 COMMENT '状态：1=上架，0=下架，-1=删除',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_package_code` (`code`),
    KEY `idx_package_name` (`name`),
    KEY `idx_package_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐主表';
