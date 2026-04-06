-- ============================================
-- V72: 订单管理权限
-- ============================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
-- 订单管理模块（菜单）
(1300, 0, '订单管理', 'order', 1, '/order', 'List', 130, 1),
-- 订单管理权限（按钮权限）
(1301, 1300, '订单查询', 'order:query', 2, NULL, NULL, 10, 1),
(1302, 1300, '订单发货', 'order:ship', 2, NULL, NULL, 20, 1),
(1303, 1300, '自提核销', 'order:pickup:verify', 2, NULL, NULL, 30, 1),
(1304, 1300, '订单退款', 'order:refund', 2, NULL, NULL, 40, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`);

-- 将订单管理权限分配给 SUPER_ADMIN 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (1300, 1301, 1302, 1303, 1304)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;









