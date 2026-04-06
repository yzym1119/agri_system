-- ============================================
-- V51: 添加店长权限定义
-- ============================================

-- 店长权限（用于门店管理）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1000, 6, '店长权限', 'store:manager', 2, NULL, NULL, 50, 1)

ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配店长权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, 1000
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = 1000
);

