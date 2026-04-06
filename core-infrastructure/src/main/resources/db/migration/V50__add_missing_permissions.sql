-- ============================================
-- V50: 补充缺失的权限定义
-- ============================================

-- 库存管理模块
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
(200, 0, '库存管理', 'inventory', 1, '/inventory', 'Box', 200, 1),
(201, 200, '入库', 'inventory:inbound', 2, NULL, NULL, 10, 1),
(202, 200, '出库', 'inventory:outbound', 2, NULL, NULL, 20, 1),
(203, 200, '调拨', 'inventory:transfer', 2, NULL, NULL, 30, 1),
(204, 200, '查看库存', 'inventory:view', 2, NULL, NULL, 40, 1),
(205, 200, '查看批次', 'inventory:batch:view', 2, NULL, NULL, 50, 1),

-- 采购管理模块
(300, 0, '采购管理', 'purchase', 1, '/purchase', 'ShoppingCart', 300, 1),
(301, 300, '创建采购单', 'purchase:create', 2, NULL, NULL, 10, 1),
(302, 300, '提交采购单', 'purchase:submit', 2, NULL, NULL, 20, 1),
(303, 300, '采购入库', 'purchase:inbound', 2, NULL, NULL, 30, 1),
(304, 300, '查看采购单', 'purchase:view', 2, NULL, NULL, 40, 1),

-- 客户管理模块
(400, 0, '客户管理', 'customer', 1, '/customer', 'User', 400, 1),
(401, 400, '充值', 'customer:recharge', 2, NULL, NULL, 10, 1),
(402, 400, '发放优惠券', 'customer:coupon:grant', 2, NULL, NULL, 20, 1),
(403, 400, '发放专项基金', 'customer:fund:grant', 2, NULL, NULL, 30, 1),
(404, 400, '发放余额', 'customer:balance:grant', 2, NULL, NULL, 40, 1),

-- 赊销管理模块
(500, 0, '赊销管理', 'credit', 1, '/credit', 'CreditCard', 500, 1),
(501, 500, '录入还款', 'credit:repay', 2, NULL, NULL, 10, 1),
(502, 500, '查询欠款', 'credit:account:view', 2, NULL, NULL, 20, 1),
(503, 500, '查询还款记录', 'credit:repayment:view', 2, NULL, NULL, 30, 1),
(504, 500, '查询统计', 'credit:statistics:view', 2, NULL, NULL, 40, 1),
(505, 500, '调整额度', 'credit:limit:update', 2, NULL, NULL, 50, 1),
(506, 500, '生成对账单', 'credit:statement:generate', 2, NULL, NULL, 60, 1),

-- 报表模块
(600, 0, '报表管理', 'report', 1, '/report', 'DataAnalysis', 600, 1),
(601, 600, '客户报表', 'report:customer', 2, NULL, NULL, 10, 1),
(602, 600, '库存报表', 'report:inventory', 2, NULL, NULL, 20, 1),
(603, 600, '财务报表', 'report:finance', 2, NULL, NULL, 30, 1),
(604, 600, '销售报表', 'report:sales', 2, NULL, NULL, 40, 1),
(605, 600, '经营驾驶舱', 'report:dashboard', 2, NULL, NULL, 50, 1),

-- 绩效管理模块
(700, 0, '绩效管理', 'performance', 1, '/performance', 'Trophy', 700, 1),
(701, 700, '提成规则管理', 'performance:rule', 2, NULL, NULL, 10, 1),
(702, 700, '设置月目标', 'performance:goal:set', 2, NULL, NULL, 20, 1),
(703, 700, '查询工资单', 'performance:salary:view', 2, NULL, NULL, 30, 1),
(704, 700, '确认工资单', 'performance:salary:confirm', 2, NULL, NULL, 40, 1),
(705, 700, '员工绩效查询', 'performance:staff:view', 2, NULL, NULL, 50, 1),

-- 积分管理模块
(800, 0, '积分管理', 'points', 1, '/points', 'Star', 800, 1),
(801, 800, '积分订单列表', 'points:order:list', 2, NULL, NULL, 10, 1),
(802, 800, '审核积分订单', 'points:order:audit', 2, NULL, NULL, 20, 1),
(803, 800, '通知取货', 'points:order:notify', 2, NULL, NULL, 30, 1),
(804, 800, '核销积分订单', 'points:order:verify', 2, NULL, NULL, 40, 1),
(805, 800, '积分礼品列表', 'points:goods:list', 2, NULL, NULL, 50, 1),
(806, 800, '新增积分礼品', 'points:goods:create', 2, NULL, NULL, 60, 1),
(807, 800, '编辑积分礼品', 'points:goods:update', 2, NULL, NULL, 70, 1),
(808, 800, '删除积分礼品', 'points:goods:delete', 2, NULL, NULL, 80, 1),
(809, 800, '上架积分礼品', 'points:goods:online', 2, NULL, NULL, 90, 1),
(810, 800, '下架积分礼品', 'points:goods:offline', 2, NULL, NULL, 100, 1),

-- 二维码模块
(900, 0, '二维码管理', 'qr', 1, '/qr', 'QrCode', 900, 1),
(901, 900, '创建二维码', 'qr:create', 2, NULL, NULL, 10, 1),
(902, 900, '查询二维码', 'qr:view', 2, NULL, NULL, 20, 1)

ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);

-- 为 SUPER_ADMIN 角色分配所有新权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id >= 200 AND id <= 902
ON DUPLICATE KEY UPDATE role_id = role_id;

