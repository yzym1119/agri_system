-- V110: 生产环境字段补全 + 权限对齐
-- 目标：农资零售连锁正式部署前必须有的字段和权限配置
-- 注意：此迁移已重写为幂等模式，因 sections 1-3 在首次失败前已执行

-- ============================================
-- Helper: 安全添加列的存储过程
-- ============================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl_name VARCHAR(64),
    IN col_name VARCHAR(64),
    IN col_def  VARCHAR(500),
    IN after_col VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl_name AND COLUMN_NAME = col_name
    ) THEN
        IF after_col IS NOT NULL AND after_col != '' THEN
            SET @ddl = CONCAT('ALTER TABLE `', tbl_name, '` ADD COLUMN `', col_name, '` ', col_def, ' AFTER `', after_col, '`');
        ELSE
            SET @ddl = CONCAT('ALTER TABLE `', tbl_name, '` ADD COLUMN `', col_name, '` ', col_def);
        END IF;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ============================================
-- 1. 商品表：农资产品合规与仓储管理字段
-- ============================================
CALL add_column_if_not_exists('product', 'barcode', 'VARCHAR(50) COMMENT ''条形码（EAN-13/Code128）''', 'code');
CALL add_column_if_not_exists('product', 'registration_no', 'VARCHAR(50) COMMENT ''农药登记证号/肥料登记证号''', 'manufacturer');
CALL add_column_if_not_exists('product', 'storage_conditions', 'VARCHAR(200) COMMENT ''存储条件（如：阴凉干燥处、避光保存）''', 'remark');
CALL add_column_if_not_exists('product', 'shelf_life_days', 'INT COMMENT ''保质期（天）''', 'storage_conditions');
CALL add_column_if_not_exists('product', 'usage_method', 'VARCHAR(500) COMMENT ''使用方法/稀释比例''', 'shelf_life_days');
CALL add_column_if_not_exists('product', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：1-已删 0-未删''', 'status');

-- 索引安全创建（使用下面的存储过程）
DROP PROCEDURE IF EXISTS create_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE create_index_if_not_exists(
    IN tbl VARCHAR(64), IN idx VARCHAR(64), IN ddl VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @s = ddl;
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL create_index_if_not_exists('product', 'idx_product_barcode', 'CREATE INDEX idx_product_barcode ON `product`(`barcode`)');
CALL create_index_if_not_exists('product', 'idx_product_deleted', 'CREATE INDEX idx_product_deleted ON `product`(`deleted`)');

-- ============================================
-- 2. 客户表：对公业务与精准营销字段
-- ============================================
CALL add_column_if_not_exists('customer', 'company_name', 'VARCHAR(200) COMMENT ''公司/农场名称''', 'name');
CALL add_column_if_not_exists('customer', 'tax_id', 'VARCHAR(50) COMMENT ''纳税人识别号''', 'company_name');
CALL add_column_if_not_exists('customer', 'invoice_title', 'VARCHAR(200) COMMENT ''发票抬头''', 'tax_id');
CALL add_column_if_not_exists('customer', 'invoice_bank', 'VARCHAR(200) COMMENT ''开票银行及账号''', 'invoice_title');
CALL add_column_if_not_exists('customer', 'invoice_address', 'VARCHAR(500) COMMENT ''开票地址''', 'invoice_bank');
CALL add_column_if_not_exists('customer', 'invoice_phone', 'VARCHAR(20) COMMENT ''开票电话''', 'invoice_address');
CALL add_column_if_not_exists('customer', 'land_area', 'DECIMAL(10,2) COMMENT ''种植面积（亩）''', 'address');
CALL add_column_if_not_exists('customer', 'crop_type', 'VARCHAR(200) COMMENT ''主要种植作物（如：番茄、柑橘、水稻）''', 'land_area');

-- ============================================
-- 3. 员工表：人事管理基础字段
-- ============================================
CALL add_column_if_not_exists('staff', 'contract_end_date', 'DATE COMMENT ''合同到期日''', 'hire_date');
CALL add_column_if_not_exists('staff', 'emergency_contact', 'VARCHAR(50) COMMENT ''紧急联系人''', 'contract_end_date');
CALL add_column_if_not_exists('staff', 'emergency_phone', 'VARCHAR(20) COMMENT ''紧急联系人电话''', 'emergency_contact');
CALL add_column_if_not_exists('staff', 'position', 'VARCHAR(50) COMMENT ''岗位（如：店长、收银员、仓管、农技顾问）''', 'role');
CALL add_column_if_not_exists('staff', 'shift_type', 'VARCHAR(20) COMMENT ''班次类型（如：早班、晚班、全天）''', 'position');
CALL add_column_if_not_exists('staff', 'salary_base', 'DECIMAL(10,2) COMMENT ''基本底薪（元）''', 'shift_type');

-- ============================================
-- 4. 订单表：软删除（防止误删订单数据丢失）
--    FIX: order 表的字段名是 order_status 不是 status
-- ============================================
CALL add_column_if_not_exists('order', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：1-已删 0-未删''', 'order_status');
CALL create_index_if_not_exists('order', 'idx_order_deleted', 'CREATE INDEX idx_order_deleted ON `order`(`deleted`)');

-- ============================================
-- 5. 采购订单表：软删除
-- ============================================
CALL add_column_if_not_exists('purchase_order', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：1-已删 0-未删''', 'status');
CALL create_index_if_not_exists('purchase_order', 'idx_purchase_order_deleted', 'CREATE INDEX idx_purchase_order_deleted ON `purchase_order`(`deleted`)');

-- ============================================
-- 6. 优惠券模板表：软删除
-- ============================================
CALL add_column_if_not_exists('coupon_template', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：1-已删 0-未删''', 'status');

-- ============================================
-- 7. 权限对齐：report:finance:query 菜单权限
--    原 ReportFinanceController 用 @SaCheckRole，已改为 @SaCheckPermission
--    需将新权限码分配给 ADMIN / BOSS / FINANCE 角色
-- ============================================
INSERT INTO `sys_menu` (`parent_id`, `name`, `permission`, `type`, `sort_order`, `visible`)
SELECT 0, '财务风控查询', 'report:finance:query', 2, 999, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'report:finance:query');

-- 将权限分配给 ADMIN、BOSS、FINANCE 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
CROSS JOIN (SELECT id FROM `sys_menu` WHERE `permission` = 'report:finance:query') m
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'BOSS', 'FINANCE')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ============================================
-- 8. 优惠券表：增加 type 字段，支持折扣券按比例计算
--    Bug: 原来 customer_coupon 没有 type 字段，折扣券被当作固定金额抵扣
--    例如 8.5折券 只减了 8.5元 而不是 订单金额×15%
-- ============================================
CALL add_column_if_not_exists('customer_coupon', 'type', 'TINYINT DEFAULT 1 COMMENT ''类型：1-满减券，2-折扣券（从模板复制）''', 'name');

-- 回填已发放的折扣券 type（仅当 type 为 NULL 时）
UPDATE `customer_coupon` cc
INNER JOIN `coupon_template` ct ON cc.template_id = ct.id
SET cc.type = ct.type
WHERE cc.type IS NULL;

-- ============================================
-- Cleanup: 移除临时存储过程
-- ============================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS create_index_if_not_exists;
