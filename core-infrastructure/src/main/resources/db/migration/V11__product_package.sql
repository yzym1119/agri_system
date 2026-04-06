-- ============================================
-- V11: 套餐表及相关表
-- ============================================

-- 套餐主表
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

-- 套餐明细表
CREATE TABLE IF NOT EXISTS `product_package_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `package_id` BIGINT NOT NULL COMMENT '套餐ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量（必须 > 0）',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_package_id` (`package_id`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_package_item_package` FOREIGN KEY (`package_id`) REFERENCES `product_package` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_package_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐明细表';

