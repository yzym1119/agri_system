-- ============================================
-- V52: 添加供应商管理权限
-- ============================================

-- 供应商管理模块
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(310, 0, '供应商管理', 'supplier', 1, '/product/supplier', 'Truck', 310, 1),
(311, 310, '新增供应商', 'supplier:create', 2, NULL, NULL, 10, 1),
(312, 310, '编辑供应商', 'supplier:update', 2, NULL, NULL, 20, 1),
(313, 310, '删除供应商', 'supplier:delete', 2, NULL, NULL, 30, 1),
(314, 310, '查看供应商', 'supplier:view', 2, NULL, NULL, 40, 1),
(315, 310, '查询供应商列表', 'supplier:list', 2, NULL, NULL, 50, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配供应商管理权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id >= 310 AND id <= 315
ON DUPLICATE KEY UPDATE role_id = role_id;

