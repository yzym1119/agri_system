-- ============================================
-- V61: 添加客户创建和更新权限
-- ============================================

-- 添加客户创建和更新权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(406, 400, '客户创建', 'customer:create', 2, NULL, NULL, 6, 1),
(407, 400, '客户编辑', 'customer:update', 2, NULL, NULL, 7, 1)
ON DUPLICATE KEY UPDATE 
  `name` = VALUES(`name`), 
  `permission` = VALUES(`permission`);

-- 将权限分配给 SUPER_ADMIN 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (406, 407)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;









