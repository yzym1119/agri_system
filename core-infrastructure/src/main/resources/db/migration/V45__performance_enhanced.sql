-- ============================================
-- V45: 绩效模块增强与扫码防作弊支撑
-- ============================================

SET @missing_cost_snapshot := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item'
      AND COLUMN_NAME = 'cost_price_snapshot'
);
SET @sql_cost_snapshot := IF(@missing_cost_snapshot = 0,
    'ALTER TABLE `order_item` ADD COLUMN `cost_price_snapshot` DECIMAL(10,2) NULL COMMENT ''下单时成本快照'' AFTER `unit_price`;',
    'SELECT 1');
PREPARE stmt FROM @sql_cost_snapshot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) staff_performance_log：绩效流水
CREATE TABLE IF NOT EXISTS `staff_performance_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `staff_id` BIGINT NOT NULL,
    `store_id` BIGINT NOT NULL,
    `biz_type` TINYINT NOT NULL COMMENT '1-sale,2-recharge,3-new_customer,4-refund,5-adjust,6-maintain,7-package,8-brand',
    `biz_id` BIGINT NULL COMMENT 'order_id 或 recharge_id 等',
    `order_item_id` BIGINT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `calc_desc` VARCHAR(1000) NULL,
    `need_review` TINYINT DEFAULT 0 COMMENT '0-正常 1-待复核',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_staff` (`staff_id`),
    INDEX `idx_biz` (`biz_type`, `biz_id`)
) COMMENT='绩效流水记录表';

-- 3) performance_event_record：事件幂等
CREATE TABLE IF NOT EXISTS `performance_event_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `event_key` VARCHAR(200) NOT NULL,
    `event_type` VARCHAR(100) NOT NULL,
    `biz_id` BIGINT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_event_key` (`event_key`)
) COMMENT='绩效模块事件幂等记录';

-- 4) staff_monthly_goal
CREATE TABLE IF NOT EXISTS `staff_monthly_goal` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `staff_id` BIGINT NOT NULL,
    `month` VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    `target_sales_amount` DECIMAL(14,2) DEFAULT 0,
    `target_new_customer` INT DEFAULT 0,
    `target_commission` DECIMAL(14,2) DEFAULT 0,
    `status` TINYINT DEFAULT 1 COMMENT '1-running,2-settled',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_staff_month` (`staff_id`,`month`)
) COMMENT='员工月目标表';

-- 5) staff_salary_bill
CREATE TABLE IF NOT EXISTS `staff_salary_bill` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `staff_id` BIGINT NOT NULL,
    `month` VARCHAR(7) NOT NULL,
    `total_commission` DECIMAL(14,2) DEFAULT 0,
    `total_sales` DECIMAL(14,2) DEFAULT 0,
    `new_customer_count` INT DEFAULT 0,
    `status` TINYINT DEFAULT 0 COMMENT '0-pending,1-confirmed',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `confirmed_at` DATETIME NULL,
    UNIQUE KEY `uk_staff_month_bill` (`staff_id`,`month`)
) COMMENT='员工工资单（月度汇总）';

-- 6) staff_customer_relation
CREATE TABLE IF NOT EXISTS `staff_customer_relation` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `staff_id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `bind_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `status` TINYINT DEFAULT 1 COMMENT '1=pending,2=approved,3=rejected',
    `approved_at` DATETIME NULL,
    `first_effective_at` DATETIME NULL,
    `bind_source` VARCHAR(32) NULL COMMENT 'QR / MANUAL 等',
    `bind_order_id` BIGINT NULL,
    UNIQUE KEY `uk_staff_customer` (`staff_id`,`customer_id`)
) COMMENT='员工与客户绑定关系（拉新归属）';

-- 7) one_time_qr
CREATE TABLE IF NOT EXISTS `one_time_qr` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `qr_token` VARCHAR(128) NOT NULL,
    `staff_id` BIGINT NOT NULL,
    `store_id` BIGINT NOT NULL,
    `order_temp_token` VARCHAR(128) NULL,
    `scene_data` VARCHAR(512) NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0=CREATED,1=BOUND,2=USED,3=EXPIRED',
    `customer_id` BIGINT NULL,
    `openid` VARCHAR(128) NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `bind_at` DATETIME NULL,
    `expire_at` DATETIME NULL,
    UNIQUE KEY `uk_qr_token` (`qr_token`),
    INDEX `idx_staff` (`staff_id`),
    INDEX `idx_status` (`status`)
) COMMENT='一次性二维码记录表';

-- 8) commission_rule 扩展
SET @missing_rule_category := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'rule_category'
);
SET @sql_rule_category := IF(@missing_rule_category = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `rule_category` TINYINT NOT NULL DEFAULT 1 COMMENT ''1-销售 2-充值 3-拉新 4-品牌推广 5-套餐'' AFTER `staff_id`;',
'SELECT 1');
PREPARE stmt FROM @sql_rule_category;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_target_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'target_id'
);
SET @sql_target_id := IF(@missing_target_id = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `target_id` BIGINT NULL COMMENT ''商品/套餐/规则 等ID'' AFTER `rule_category`;',
'SELECT 1');
PREPARE stmt FROM @sql_target_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_calc_mode := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'calc_mode'
);
SET @sql_calc_mode := IF(@missing_calc_mode = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `calc_mode` VARCHAR(20) NOT NULL DEFAULT ''FIXED'' COMMENT ''FIXED/PROFIT_PERCENT/SALES_PERCENT'' AFTER `target_id`;',
'SELECT 1');
PREPARE stmt FROM @sql_calc_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_calc_value := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'calc_value'
);
SET @sql_calc_value := IF(@missing_calc_value = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `calc_value` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT ''固定金额或比例'' AFTER `calc_mode`;',
'SELECT 1');
PREPARE stmt FROM @sql_calc_value;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_max_per_order := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'max_per_order'
);
SET @sql_max_per_order := IF(@missing_max_per_order = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `max_per_order` DECIMAL(10,2) NULL COMMENT ''单订单提成封顶'' AFTER `calc_value`;',
'SELECT 1');
PREPARE stmt FROM @sql_max_per_order;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_max_per_item := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'max_per_item'
);
SET @sql_max_per_item := IF(@missing_max_per_item = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `max_per_item` DECIMAL(10,2) NULL COMMENT ''单行提成封顶'' AFTER `max_per_order`;',
'SELECT 1');
PREPARE stmt FROM @sql_max_per_item;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @missing_enabled := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'commission_rule'
      AND COLUMN_NAME = 'enabled'
);
SET @sql_enabled := IF(@missing_enabled = 0,
'ALTER TABLE `commission_rule` ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''1-启用 0-禁用'' AFTER `status`;',
'SELECT 1');
PREPARE stmt FROM @sql_enabled;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `commission_rule`
    CHANGE COLUMN `rule_type` `legacy_rule_type` TINYINT NULL COMMENT '历史字段-规则类型',
    CHANGE COLUMN `fixed_amount` `legacy_fixed_amount` DECIMAL(10,2) NULL,
    CHANGE COLUMN `profit_percentage` `legacy_profit_percentage` DECIMAL(5,2) NULL;

ALTER TABLE `commission_rule`
    ADD INDEX `idx_rule_category` (`rule_category`),
    ADD INDEX `idx_enabled` (`enabled`);

