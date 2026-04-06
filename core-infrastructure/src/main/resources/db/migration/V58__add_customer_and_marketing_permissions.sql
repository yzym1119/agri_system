-- ============================================
-- V58: 客户管理与营销模块权限
-- ============================================

-- 更新客户管理权限（V50 中已定义 400-404，这里更新并补充）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(400, 0, '客户管理', 'customer:query', 1, '/customer/list', 'User', 40, 1),
(401, 400, '客户查询', 'customer:query', 2, NULL, NULL, 1, 1),
(402, 400, '客户充值', 'customer:recharge', 2, NULL, NULL, 2, 1),
(403, 400, '手动发券', 'customer:coupon:grant', 2, NULL, NULL, 3, 1),
(404, 400, '发放专用金', 'customer:fund:grant', 2, NULL, NULL, 4, 1),
(405, 400, '手动调账', 'customer:balance:grant', 2, NULL, NULL, 5, 1)
ON DUPLICATE KEY UPDATE 
  `name` = VALUES(`name`), 
  `permission` = VALUES(`permission`), 
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`);

-- 营销管理权限（使用新的 ID 范围 1100+ 避免与已有权限冲突）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1100, 0, '营销管理', NULL, 1, NULL, 'Promotion', 50, 1),
(1101, 1100, '优惠券管理', 'marketing:coupon:query', 1, '/marketing/coupon', 'Ticket', 1, 1),
(1102, 1100, '营销规则', 'marketing:rule:query', 1, '/marketing/rule', 'Promotion', 2, 1),
(1103, 1100, '积分商城', 'marketing:points:goods:query', 1, '/marketing/points', 'Star', 3, 1);

-- 优惠券管理权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1104, 1101, '优惠券查询', 'marketing:coupon:query', 2, NULL, NULL, 1, 1),
(1105, 1101, '优惠券创建', 'marketing:coupon:create', 2, NULL, NULL, 2, 1),
(1106, 1101, '优惠券编辑', 'marketing:coupon:update', 2, NULL, NULL, 3, 1),
(1107, 1101, '优惠券删除', 'marketing:coupon:delete', 2, NULL, NULL, 4, 1);

-- 营销规则权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1108, 1102, '规则查询', 'marketing:rule:query', 2, NULL, NULL, 1, 1),
(1109, 1102, '规则创建', 'marketing:rule:create', 2, NULL, NULL, 2, 1),
(1110, 1102, '规则编辑', 'marketing:rule:update', 2, NULL, NULL, 3, 1),
(1111, 1102, '规则删除', 'marketing:rule:delete', 2, NULL, NULL, 4, 1);

-- 积分商城权限
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(1112, 1103, '礼品查询', 'marketing:points:goods:query', 2, NULL, NULL, 1, 1),
(1113, 1103, '礼品创建', 'marketing:points:goods:create', 2, NULL, NULL, 2, 1),
(1114, 1103, '礼品编辑', 'marketing:points:goods:update', 2, NULL, NULL, 3, 1),
(1115, 1103, '礼品删除', 'marketing:points:goods:delete', 2, NULL, NULL, 4, 1),
(1116, 1103, '订单查询', 'marketing:points:order:query', 2, NULL, NULL, 5, 1),
(1117, 1103, '订单审核', 'marketing:points:order:audit', 2, NULL, NULL, 6, 1),
(1118, 1103, '订单核销', 'marketing:points:order:verify', 2, NULL, NULL, 7, 1);

-- 将权限分配给 SUPER_ADMIN 角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (400, 401, 402, 403, 404, 405, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112, 1113, 1114, 1115, 1116, 1117, 1118)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

