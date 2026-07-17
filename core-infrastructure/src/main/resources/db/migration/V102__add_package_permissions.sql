-- ============================================
-- V102: 套餐管理权限
-- ============================================

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(105, 100, '套餐管理', 'package:list', 1, '/product/package', 'Box', 50, 1),
(150, 105, '套餐查询', 'package:query', 2, NULL, NULL, 10, 1),
(151, 105, '新增套餐', 'package:create', 2, NULL, NULL, 20, 1),
(152, 105, '编辑套餐', 'package:update', 2, NULL, NULL, 30, 1),
(153, 105, '上下架套餐', 'package:update', 2, NULL, NULL, 40, 1),
(154, 105, '删除套餐', 'package:delete', 2, NULL, NULL, 50, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `visible` = VALUES(`visible`);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (105, 150, 151, 152, 153, 154)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
