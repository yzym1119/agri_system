-- ============================================
-- V106: 补建供应商表、采购订单表、采购权限、客户类型字典
-- ============================================

-- 1. 供应商表
CREATE TABLE IF NOT EXISTS `supplier` (
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

-- 2. 采购订单表
CREATE TABLE IF NOT EXISTS `purchase_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `purchase_no` VARCHAR(50) NOT NULL COMMENT '采购单号',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `store_id` BIGINT NOT NULL COMMENT '入库门店ID',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总金额（元）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待提交，2-待入库，3-已入库，4-已取消',
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
    KEY `idx_purchase_supplier` (`supplier_id`),
    KEY `idx_purchase_store` (`store_id`),
    KEY `idx_purchase_status` (`status`),
    KEY `idx_purchase_date` (`purchase_date`),
    CONSTRAINT `fk_purchase_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
    CONSTRAINT `fk_purchase_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单表';

-- 3. 采购订单明细表
CREATE TABLE IF NOT EXISTS `purchase_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `purchase_order_id` BIGINT NOT NULL COMMENT '采购订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` DECIMAL(10,4) NOT NULL COMMENT '数量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `buy_price` DECIMAL(10,2) NOT NULL COMMENT '本次真实进价（元）',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '小计金额（元）',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_poi_order` (`purchase_order_id`),
    KEY `idx_poi_product` (`product_id`),
    CONSTRAINT `fk_poi_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
    CONSTRAINT `fk_poi_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单明细表';

-- 4. 采购管理权限菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1400, 0, '采购管理', 'purchase', 1, '/inventory/purchase/order', 'ShoppingCart', 400, 1),
(1401, 1400, '查询采购单', 'purchase:query', 2, NULL, NULL, 10, 1),
(1402, 1400, '创建采购单', 'purchase:create', 2, NULL, NULL, 20, 1),
(1403, 1400, '提交采购单', 'purchase:submit', 2, NULL, NULL, 30, 1),
(1404, 1400, '采购入库', 'purchase:inbound', 2, NULL, NULL, 40, 1),
(1405, 1400, '取消采购单', 'purchase:cancel', 2, NULL, NULL, 50, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色（id=1）分配采购权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id >= 1400 AND id <= 1405
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 5. 客户类型字典
INSERT INTO `sys_dict_type` (`code`, `name`, `status`, `remark`) VALUES
('customer_type', '客户类型', 1, '客户分类')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `sys_dict_data` (`type_code`, `label`, `value`, `sort_order`, `status`, `is_default`) VALUES
('customer_type', '普通客户', '1', 10, 1, 1),
('customer_type', 'VIP客户', '2', 20, 1, 0)
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`);
