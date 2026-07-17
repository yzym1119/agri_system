-- V114: 分组名称修正 + 站内信菜单 + 订单页面确认

-- ============================================
-- 1. Remove "组" suffix from virtual group path entries
-- ============================================
UPDATE sys_menu SET name = '商品供应' WHERE id = 2001;
UPDATE sys_menu SET name = '库存物流' WHERE id = 2101;
UPDATE sys_menu SET name = '客户营销' WHERE id = 2301;
UPDATE sys_menu SET name = '运营管理' WHERE id = 2401;
UPDATE sys_menu SET name = '系统设置' WHERE id = 2501;

-- ============================================
-- 2. Ensure order page role-menu mapping (idempotent)
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2602);

-- ============================================
-- 3. Notification menu entry under 系统设置
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2502, 2500, '站内信', 'notification:query', 1, 70, '/system/notification', 'Bell', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 4. Notification role-menu mapping for admin
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2502);
