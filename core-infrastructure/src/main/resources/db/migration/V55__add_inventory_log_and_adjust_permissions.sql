-- ============================================
-- V55: 添加库存日志和盘点权限
-- ============================================

-- 添加库存日志查询权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(206, 200, '库存日志', 'inventory:log:list', 2, NULL, NULL, 60, 1),
(207, 200, '库存盘点', 'inventory:adjust', 2, NULL, NULL, 70, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配新权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id IN (206, 207)
ON DUPLICATE KEY UPDATE role_id = role_id;









