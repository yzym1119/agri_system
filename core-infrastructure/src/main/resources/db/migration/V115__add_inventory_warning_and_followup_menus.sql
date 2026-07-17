-- V115: 库存预警 + 客户跟进菜单

-- ============================================
-- 1. 库存预警页面菜单
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2701, 2100, '库存预警', 'inventory:warning:query', 1, 50, '/inventory/warning', 'Warning', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 2. 客户跟进页面菜单
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2702, 2300, '客户跟进', 'customer:followup:manage', 1, 50, '/marketing/customer/follow-up', 'ChatDotRound', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- ============================================
-- 3. SUPER_ADMIN 权限分配
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2701);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2702);
