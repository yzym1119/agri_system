-- V111: 库存预警字段 + 系统通知基础设施

-- ============================================
-- 1. 商品表：库存预警阈值
-- ============================================
ALTER TABLE `product`
    ADD COLUMN `min_stock` DECIMAL(12,2) COMMENT '最低库存预警值（低于此值触发预警）' AFTER `usage_method`,
    ADD COLUMN `max_stock` DECIMAL(12,2) COMMENT '最高库存预警值（高于此值触发积压预警）' AFTER `min_stock`,
    ADD COLUMN `reorder_quantity` DECIMAL(12,2) COMMENT '建议补货数量' AFTER `max_stock`;

-- ============================================
-- 2. 系统通知表（站内消息）
-- ============================================
CREATE TABLE IF NOT EXISTS `system_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT COMMENT '通知内容',
    `type` VARCHAR(50) NOT NULL COMMENT '类型：stock_warning-库存预警, order_event-订单事件, approval-审批, system-系统通知, commission-提成通知',
    `target_type` VARCHAR(20) COMMENT '目标类型：staff-指定员工, role-指定角色, all-全员',
    `target_id` BIGINT COMMENT '目标ID（staff_id或role_id，all时为空）',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：1-已读 0-未读',
    `read_at` DATETIME COMMENT '阅读时间',
    `extra_data` JSON COMMENT '附加数据（如关联的订单号、商品ID等）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_target` (`target_type`, `target_id`, `is_read`),
    KEY `idx_notification_type` (`type`, `created_at`),
    KEY `idx_notification_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- ============================================
-- 3. 客户跟进记录表（农资使用周期提醒）
-- ============================================
CREATE TABLE IF NOT EXISTS `customer_follow_up` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `staff_id` BIGINT NOT NULL COMMENT '跟进员工ID',
    `follow_up_type` VARCHAR(30) NOT NULL COMMENT '跟进类型：repurchase-复购提醒, usage-使用回访, complaint-投诉跟进, birthday-生日关怀',
    `content` VARCHAR(500) COMMENT '跟进内容/备注',
    `next_follow_date` DATE COMMENT '下次跟进日期',
    `related_order_id` BIGINT COMMENT '关联订单ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待跟进, 1-已完成, 2-已取消',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_followup_customer` (`customer_id`),
    KEY `idx_followup_staff_date` (`staff_id`, `next_follow_date`),
    KEY `idx_followup_status` (`status`, `next_follow_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户跟进记录表';

-- ============================================
-- 4. 新增权限码
-- ============================================
INSERT INTO `sys_menu` (`parent_id`, `name`, `permission`, `type`, `sort_order`, `visible`)
SELECT 0, '库存预警查询', 'inventory:warning:query', 2, 1001, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'inventory:warning:query');

INSERT INTO `sys_menu` (`parent_id`, `name`, `permission`, `type`, `sort_order`, `visible`)
SELECT 0, '通知管理', 'notification:query', 2, 1002, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'notification:query');

INSERT INTO `sys_menu` (`parent_id`, `name`, `permission`, `type`, `sort_order`, `visible`)
SELECT 0, '客户跟进管理', 'customer:followup:manage', 2, 1003, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'customer:followup:manage');

-- 分配给 ADMIN 和 MANAGER 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
CROSS JOIN (SELECT id FROM `sys_menu` WHERE `permission` IN ('inventory:warning:query', 'notification:query', 'customer:followup:manage')) m
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 通知查询权限给所有角色（每个员工都能查看自己的通知）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
CROSS JOIN (SELECT id FROM `sys_menu` WHERE `permission` = 'notification:query') m
WHERE r.code NOT IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
