-- ============================================
-- V59: POS 收银权限
-- ============================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
-- POS 收银模块（菜单）
(1200, 0, 'POS收银台', 'pos', 1, '/pos', 'ShoppingCart', 120, 1),
-- POS 开单（按钮权限）
(1201, 1200, 'POS收款', 'pos:order:create', 2, NULL, NULL, 10, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`);

-- 将 POS 权限分配给 SUPER_ADMIN 角色，其他角色通过后台角色管理配置
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (1200, 1201)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;










