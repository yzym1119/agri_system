-- V116: 新增"门店统计"和"我的绩效"菜单

-- ============================================
-- 1. 门店统计 (挂在运营管理组 2400 下)
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2700, 2400, '门店统计', 'store:analytics:view', 1, 25, '/operation/store-analytics', 'Shop', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    permission = VALUES(permission),
    sort_order = VALUES(sort_order),
    path = VALUES(path),
    icon = VALUES(icon),
    visible = VALUES(visible);

-- ============================================
-- 2. 我的绩效 (独立顶级菜单，所有登录员工可见)
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2701, 0, '我的绩效', NULL, 1, 50, '/my-performance', 'TrendCharts', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort_order = VALUES(sort_order),
    path = VALUES(path),
    icon = VALUES(icon),
    visible = VALUES(visible);

-- ============================================
-- 3. 为 SUPER_ADMIN (role_id=1) 分配权限
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 2700),
(1, 2701);
