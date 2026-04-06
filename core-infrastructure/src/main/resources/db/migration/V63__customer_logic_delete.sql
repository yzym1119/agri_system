-- ============================================
-- V63: 客户表逻辑删除字段
-- ============================================

-- 添加逻辑删除字段
ALTER TABLE `customer`
    ADD COLUMN `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：1-已删 0-未删' AFTER `status`;

-- 添加删除权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(408, 400, '客户删除', 'customer:delete', 2, NULL, NULL, 8, 1)
ON DUPLICATE KEY UPDATE 
  `name` = VALUES(`name`), 
  `permission` = VALUES(`permission`);

-- 将权限分配给 SUPER_ADMIN 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` = 408
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;









