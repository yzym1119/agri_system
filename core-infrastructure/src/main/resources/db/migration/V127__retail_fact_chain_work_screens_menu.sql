-- V127: 零售事实链作业界面菜单（单位设置 / 收货 / 货位 / 取货）

-- ============================================
-- 1. 单位设置页面菜单（商品供应 2000）
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2703, 2000, '单位设置', 'product:unit:query', 1, 60, '/product/units', 'ScaleToOriginal', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 2. 收货页面菜单（库存物流 2100）
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2704, 2100, '收货', 'purchase:inbound', 1, 60, '/purchase/receipt', 'Van', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 3. 货位页面菜单（库存物流 2100）
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2705, 2100, '货位', 'inventory:view', 1, 70, '/inventory/locations', 'Grid', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 4. 取货页面菜单（库存物流 2100）
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2706, 2100, '取货', 'order:pickup:verify', 1, 80, '/inventory/pickup', 'Box', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 5. SUPER_ADMIN 权限分配
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2703);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2704);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2705);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2706);
