-- ============================================
-- V54: 添加采购查询权限
-- ============================================

-- 添加 purchase:query 权限（使用新ID 305，因为304已被purchase:view使用）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(305, 300, '查询采购单', 'purchase:query', 2, NULL, NULL, 35, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配 purchase:query 权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, 305
ON DUPLICATE KEY UPDATE role_id = role_id;

