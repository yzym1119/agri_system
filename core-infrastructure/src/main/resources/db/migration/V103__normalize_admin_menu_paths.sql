-- ============================================
-- V103: 统一管理端菜单路径到当前 Web 路由口径
-- ============================================

-- 说明：
-- 早期权限菜单的 path 与当前管理端页面路由并不完全一致，
-- 前端为了兼容旧数据不得不保留 alias 和按权限兜底匹配。
-- 本迁移将几个仍在使用的历史 path 统一到当前 Web 端真实页面路径。

-- 库存查询：首页实际对应 /inventory/list
UPDATE `sys_menu`
SET `path` = '/inventory/list'
WHERE `id` = 200
  AND (`path` IS NULL OR `path` <> '/inventory/list');

-- 采购管理：当前页面实际挂在库存模块下 /inventory/purchase/order
UPDATE `sys_menu`
SET `path` = '/inventory/purchase/order'
WHERE `id` = 300
  AND (`path` IS NULL OR `path` <> '/inventory/purchase/order');

-- 客户管理：当前页面实际挂在客户营销模块下 /marketing/customer/list
UPDATE `sys_menu`
SET `path` = '/marketing/customer/list'
WHERE `id` = 400
  AND (`path` IS NULL OR `path` <> '/marketing/customer/list');
