-- ============================================
-- V4: 商品表及相关表（重构版）
-- ============================================

-- 商品分类表（树形结构）
CREATE TABLE IF NOT EXISTS `product_category` (
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

-- 商品表（重构版）
CREATE TABLE IF NOT EXISTS `product` (
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
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用',
    `remark` VARCHAR(500) COMMENT '备注',
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

-- 商品单位换算表（支持多级单位换算）
CREATE TABLE IF NOT EXISTS `product_unit_conversion` (
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

-- 注意：product_price表在V7__price.sql中定义
