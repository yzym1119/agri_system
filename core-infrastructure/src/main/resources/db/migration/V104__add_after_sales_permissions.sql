-- ============================================
-- V104: 售后管理菜单与权限
-- ============================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1310, 0, '售后管理', 'afterSales', 1, '/after-sales', 'RefreshLeft', 135, 1),
(1311, 1310, '售后查询', 'afterSales:query', 2, NULL, NULL, 10, 1),
(1312, 1310, '售后审核', 'afterSales:audit', 2, NULL, NULL, 20, 1),
(1313, 1310, '售后退款', 'afterSales:refund', 2, NULL, NULL, 30, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `visible` = VALUES(`visible`);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (1310, 1311, 1312, 1313)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
