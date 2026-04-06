-- ============================================
-- V56: 添加采购取消权限
-- ============================================

-- 添加采购取消权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(208, 201, '取消采购单', 'purchase:cancel', 2, NULL, NULL, 50, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配新权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id = 208
ON DUPLICATE KEY UPDATE role_id = role_id;









