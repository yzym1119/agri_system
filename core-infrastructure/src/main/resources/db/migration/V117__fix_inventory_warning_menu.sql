-- V117: 修复库存预警菜单项
-- V115 和 V116 共用 id=2701 导致库存预警菜单丢失

-- ============================================
-- 1. 修复 id=2701：归还为「我的绩效」，parent_id=0（顶级菜单）
-- ============================================
UPDATE sys_menu SET
    parent_id = 0,
    name = '我的绩效',
    permission = NULL,
    type = 1,
    sort_order = 50,
    path = '/my-performance',
    icon = 'TrendCharts',
    visible = 1
WHERE id = 2701;

-- ============================================
-- 2. 新增「库存预警」菜单，挂在「库存管理」(id=200) 下
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2702, 200, '库存预警', 'inventory:warning:query', 1, 50, '/inventory/warning', 'Warning', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    permission = VALUES(permission),
    type = VALUES(type),
    sort_order = VALUES(sort_order),
    path = VALUES(path),
    icon = VALUES(icon),
    visible = VALUES(visible);

-- ============================================
-- 3. 删除旧的纯权限节点（id=1407），由新菜单项替代
-- ============================================
DELETE FROM sys_role_menu WHERE menu_id = 1407;
DELETE FROM sys_menu WHERE id = 1407 AND type = 2;

-- ============================================
-- 4. 为新菜单分配权限：SUPER_ADMIN(id=1), ADMIN(id=2), MANAGER(id=3)
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 2702
FROM sys_role r
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 2702
  );

-- 确保我的绩效(2701)分配给所有角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 2701
FROM sys_role r
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 2701
);
