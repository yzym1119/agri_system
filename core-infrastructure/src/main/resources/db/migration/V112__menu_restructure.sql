-- V112: 菜单结构整理 —— 分组 + 排序 + 去重
-- 解决问题：左侧菜单全部平铺、排序混乱、采购管理重复

-- ============================================
-- 1. 创建父级分组目录 (type=1, parent_id=0)
--    前端 registry.ts parentGroups 定义，此处对齐
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2000, 0, '商品供应', NULL, 1, 100, NULL, 'Goods', 1),
(2100, 0, '库存物流', NULL, 1, 200, NULL, 'Box', 1),
(2300, 0, '客户营销', NULL, 1, 400, NULL, 'Connection', 1),
(2400, 0, '运营管理', NULL, 1, 500, NULL, 'DataAnalysis', 1),
(2500, 0, '系统设置', NULL, 1, 600, NULL, 'Setting', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    type = VALUES(type),
    sort_order = VALUES(sort_order),
    icon = VALUES(icon),
    visible = VALUES(visible);

-- 补充缺失的顶级页面菜单（前端 registry.ts 有定义但 DB 里没有的）
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2600, 0, '工作台',   'report:dashboard',  1, 10,  '/dashboard', 'Odometer', 1)
ON DUPLICATE KEY UPDATE
    sort_order = VALUES(sort_order),
    icon = VALUES(icon),
    visible = VALUES(visible);

-- 更新已有 POS收银台 的排序和图标
UPDATE sys_menu SET sort_order = 20, icon = 'Money' WHERE id = 1200;

-- ============================================
-- 2. 将现有菜单挂到分组下 + 设置排序
-- ============================================

-- ── 隐藏旧的独立页面（已被 /standalone/* 条目替代） ──
UPDATE sys_menu SET parent_id = 0, sort_order = 120, icon = 'List',        visible = 0 WHERE id = 1300; -- 订单管理(旧)
UPDATE sys_menu SET parent_id = 0, sort_order = 130, icon = 'RefreshLeft',  visible = 0 WHERE id = 1310; -- 售后管理(旧)
UPDATE sys_menu SET parent_id = 0, sort_order = 140, icon = 'CreditCard',   visible = 0 WHERE id = 500;  -- 赊销管理(旧)

-- ── 商品供应组 (2000) ──
UPDATE sys_menu SET parent_id = 2000, sort_order = 10 WHERE id = 100;  -- 商品管理
UPDATE sys_menu SET parent_id = 100,  sort_order = 10 WHERE id = 101;  -- 商品列表
UPDATE sys_menu SET parent_id = 100,  sort_order = 20 WHERE id = 102;  -- 商品分类
UPDATE sys_menu SET parent_id = 100,  sort_order = 30 WHERE id = 103;  -- 商品单位
UPDATE sys_menu SET parent_id = 100,  sort_order = 40 WHERE id = 104;  -- 价格体系
UPDATE sys_menu SET parent_id = 100,  sort_order = 50 WHERE id = 105;  -- 套餐管理
UPDATE sys_menu SET parent_id = 2000, sort_order = 20 WHERE id = 310;  -- 供应商管理

-- ── 库存物流组 (2100) ──
UPDATE sys_menu SET parent_id = 2100, sort_order = 10 WHERE id = 200;  -- 库存查询
UPDATE sys_menu SET parent_id = 2100, sort_order = 20 WHERE id = 300;  -- 采购管理

-- ── 客户营销组 (2300) ──
UPDATE sys_menu SET parent_id = 2300, sort_order = 10 WHERE id = 400;  -- 客户管理
UPDATE sys_menu SET parent_id = 2300, sort_order = 20 WHERE id = 1101; -- 优惠券管理
UPDATE sys_menu SET parent_id = 2300, sort_order = 30 WHERE id = 1102; -- 营销规则
UPDATE sys_menu SET parent_id = 2300, sort_order = 40 WHERE id = 1103; -- 积分商城

-- ── 运营管理组 (2400) ──
UPDATE sys_menu SET parent_id = 2400, sort_order = 10, path = '/operation/report'      WHERE id = 600;  -- 报表中心
UPDATE sys_menu SET parent_id = 2400, sort_order = 20, path = '/operation/performance'  WHERE id = 700;  -- 绩效管理
UPDATE sys_menu SET parent_id = 2400, sort_order = 30 WHERE id = 900;  -- 二维码管理

-- ── 系统设置组 (2500) ──
UPDATE sys_menu SET parent_id = 2500, sort_order = 10 WHERE id = 6;    -- 门店管理
UPDATE sys_menu SET parent_id = 2500, sort_order = 20 WHERE id = 2;    -- 员工管理
UPDATE sys_menu SET parent_id = 2500, sort_order = 30 WHERE id = 3;    -- 角色管理
UPDATE sys_menu SET parent_id = 2500, sort_order = 40 WHERE id = 4;    -- 菜单管理
UPDATE sys_menu SET parent_id = 2500, sort_order = 50 WHERE id = 5;    -- 字典管理
UPDATE sys_menu SET parent_id = 2500, sort_order = 60 WHERE id = 7;    -- 操作日志

-- ============================================
-- 3. 权限按钮（type=2）排序整理
-- ============================================
UPDATE sys_menu SET sort_order = 100 WHERE id = 1406; -- 财务风控查询
UPDATE sys_menu SET sort_order = 110 WHERE id = 1407; -- 库存预警查询
UPDATE sys_menu SET sort_order = 120 WHERE id = 1408; -- 通知管理
UPDATE sys_menu SET sort_order = 130 WHERE id = 1409; -- 客户跟进管理

-- ============================================
-- 4. 删除重复的"采购管理" (ID 1400)
--    保留 ID 300，将其权限子项保留（301-305）
-- ============================================
DELETE FROM sys_role_menu WHERE menu_id = 1400;
DELETE FROM sys_role_menu WHERE menu_id IN (1401, 1402, 1403, 1404, 1405);
DELETE FROM sys_menu WHERE id IN (1401, 1402, 1403, 1404, 1405);
DELETE FROM sys_menu WHERE id = 1400;

-- ============================================
-- 5. 为新分组分配 SUPER_ADMIN 权限
--    代码会自动将父级 ID 传播到子级
-- ============================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (2000, 2100, 2300, 2400, 2500, 50, 1250);

-- ============================================
-- 6. 清理旧的营销管理空壳 (ID 1100)
--    其子项已直接挂到 客户营销(2300) 下
-- ============================================
UPDATE sys_menu SET parent_id = 0, sort_order = 999, visible = 0 WHERE id = 1100;

-- ============================================
-- 7. 为独立页面创建专用排序条目 (visible=1, 供前端 menuPath 匹配排序)
--    使用 /standalone/* 路径避免与分组内同名条目冲突
-- ============================================
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2602, 0, '订单管理', 'order:query',         1, 300, '/standalone/order',       'List',         1),
(2603, 0, '售后管理', 'afterSales:query',    1, 310, '/standalone/after-sales', 'RefreshLeft',  1),
(2604, 0, '赊销管理', 'credit:account:view', 1, 320, '/standalone/credit',      'CreditCard',   1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), icon = VALUES(icon), visible = VALUES(visible);

-- 分组虚拟路径条目（供前端 menuPath 精确匹配 sortOrder）
INSERT INTO sys_menu (id, parent_id, name, permission, type, sort_order, path, icon, visible) VALUES
(2001, 2000, '商品供应组', NULL, 1, 100, '/groups/product',   NULL, 1),
(2101, 2100, '库存物流组', NULL, 1, 200, '/groups/inventory', NULL, 1),
(2301, 2300, '客户营销组', NULL, 1, 400, '/groups/marketing', NULL, 1),
(2401, 2400, '运营管理组', NULL, 1, 500, '/groups/operation', NULL, 1),
(2501, 2500, '系统设置组', NULL, 1, 600, '/groups/system',    NULL, 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), visible = VALUES(visible);

-- 分配角色权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 2602), (1, 2603), (1, 2604),
(1, 2001), (1, 2101), (1, 2301), (1, 2401), (1, 2501);
