-- V128: 补齐后台页面入口菜单与授权
-- 1) V112 授权列表把 2600(工作台) 误写为 1250,工作台从未授予任何角色
-- 2) 库存日志/库存调拨只有按钮权限(206/203),没有页面菜单行,路由无法注册
-- 3) 客户跟进页面菜单 2702 被 V117 覆盖为「库存预警」,改用新 id 2709 重建

INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2707, 2100, '库存日志', 'inventory:log:list', 1, 30, '/inventory/log', 'Document', 1),
(2708, 2100, '库存调拨', 'inventory:transfer', 1, 40, '/inventory/transfer', 'Switch', 1),
(2709, 2300, '客户跟进', 'customer:followup:manage', 1, 50, '/marketing/customer/follow-up', 'ChatDotRound', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    permission = VALUES(permission),
    type = VALUES(type),
    sort_order = VALUES(sort_order),
    path = VALUES(path),
    icon = VALUES(icon),
    visible = VALUES(visible);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2600), (1, 2707), (1, 2708), (1, 2709);

-- 已持有对应按钮权限的角色,同步获得页面入口
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2707 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'inventory:log:list' AND m.type = 2);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2708 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'inventory:transfer' AND m.type = 2);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2709 FROM sys_role_menu rm
WHERE rm.menu_id IN (SELECT m.id FROM sys_menu m WHERE m.permission = 'customer:followup:manage' AND m.type = 2);
