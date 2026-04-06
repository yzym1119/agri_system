-- ============================================
-- V49: 初始化完整权限菜单数据 + 编码生成支持
-- ============================================

-- 1. 清空现有菜单数据（保留ID=1的系统管理父菜单）
DELETE FROM `sys_role_menu` WHERE menu_id > 1;
DELETE FROM `sys_menu` WHERE id > 1;

-- 2. 插入完整的权限菜单数据
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `type`, `path`, `icon`, `sort_order`, `visible`) VALUES
-- 系统管理子菜单
(2, 1, '员工管理', 'staff:list', 1, '/system/staff', 'Avatar', 10, 1),
(3, 1, '角色管理', 'role:list', 1, '/system/role', 'UserFilled', 20, 1),
(4, 1, '菜单管理', 'menu:list', 1, '/system/menu', 'Menu', 30, 1),
(5, 1, '字典管理', 'dict:list', 1, '/system/dict', 'List', 40, 1),
(6, 1, '门店管理', 'store:list', 1, '/system/store', 'Shop', 50, 1),
(7, 1, '操作日志', 'log:view', 1, '/system/log', 'Document', 60, 1),

-- 员工管理权限按钮
(10, 2, '新增员工', 'staff:create', 2, NULL, NULL, 10, 1),
(11, 2, '编辑员工', 'staff:update', 2, NULL, NULL, 20, 1),
(12, 2, '删除员工', 'staff:delete', 2, NULL, NULL, 30, 1),
(13, 2, '禁用员工', 'staff:disable', 2, NULL, NULL, 40, 1),
(14, 2, '启用员工', 'staff:enable', 2, NULL, NULL, 50, 1),
(15, 2, '重置密码', 'staff:resetPassword', 2, NULL, NULL, 60, 1),
(16, 2, '查看详情', 'staff:view', 2, NULL, NULL, 70, 1),

-- 角色管理权限按钮
(20, 3, '新增角色', 'role:save', 2, NULL, NULL, 10, 1),
(21, 3, '编辑角色', 'role:save', 2, NULL, NULL, 20, 1),
(22, 3, '删除角色', 'role:delete', 2, NULL, NULL, 30, 1),
(23, 3, '分配权限', 'role:assign', 2, NULL, NULL, 40, 1),
(24, 3, '查看详情', 'role:view', 2, NULL, NULL, 50, 1),

-- 菜单管理权限按钮
(30, 4, '新增菜单', 'menu:save', 2, NULL, NULL, 10, 1),
(31, 4, '编辑菜单', 'menu:save', 2, NULL, NULL, 20, 1),
(32, 4, '删除菜单', 'menu:delete', 2, NULL, NULL, 30, 1),
(33, 4, '查看详情', 'menu:view', 2, NULL, NULL, 40, 1),

-- 字典管理权限按钮
(40, 5, '新增字典', 'dict:save', 2, NULL, NULL, 10, 1),
(41, 5, '编辑字典', 'dict:save', 2, NULL, NULL, 20, 1),
(42, 5, '删除字典', 'dict:delete', 2, NULL, NULL, 30, 1),
(43, 5, '查看详情', 'dict:view', 2, NULL, NULL, 40, 1),

-- 门店管理权限按钮
(50, 6, '新增门店', 'store:create', 2, NULL, NULL, 10, 1),
(51, 6, '编辑门店', 'store:update', 2, NULL, NULL, 20, 1),
(52, 6, '删除门店', 'store:delete', 2, NULL, NULL, 30, 1),
(53, 6, '查看详情', 'store:view', 2, NULL, NULL, 40, 1),

-- 商品管理模块
(100, 0, '商品管理', 'product', 1, '/product', 'Goods', 100, 1),
(101, 100, '商品列表', 'product:list', 1, '/product/list', 'List', 10, 1),
(102, 100, '商品分类', 'product:category', 1, '/product/category', 'Folder', 20, 1),
(103, 100, '商品单位', 'product:unit', 1, '/product/unit', 'Scale', 30, 1),
(104, 100, '价格体系', 'product:price', 1, '/product/price', 'Money', 40, 1),

-- 商品管理权限按钮
(110, 101, '新增商品', 'product:create', 2, NULL, NULL, 10, 1),
(111, 101, '编辑商品', 'product:update', 2, NULL, NULL, 20, 1),
(112, 101, '删除商品', 'product:delete', 2, NULL, NULL, 30, 1),
(113, 101, '查看详情', 'product:query', 2, NULL, NULL, 40, 1),
(114, 101, '上架/下架', 'product:update', 2, NULL, NULL, 50, 1),

(120, 102, '新增分类', 'product:category:create', 2, NULL, NULL, 10, 1),
(121, 102, '编辑分类', 'product:category:update', 2, NULL, NULL, 20, 1),
(122, 102, '删除分类', 'product:category:delete', 2, NULL, NULL, 30, 1),
(123, 102, '查看分类', 'product:category:query', 2, NULL, NULL, 40, 1),

(130, 103, '新增单位', 'product:unit:create', 2, NULL, NULL, 10, 1),
(131, 103, '删除单位', 'product:unit:delete', 2, NULL, NULL, 20, 1),
(132, 103, '查看单位', 'product:unit:query', 2, NULL, NULL, 30, 1),

(140, 104, '新增价格', 'product:price:create', 2, NULL, NULL, 10, 1),
(141, 104, '编辑价格', 'product:price:update', 2, NULL, NULL, 20, 1),
(142, 104, '删除价格', 'product:price:delete', 2, NULL, NULL, 30, 1),
(143, 104, '查看价格', 'product:price:query', 2, NULL, NULL, 40, 1);

-- 3. 为 SUPER_ADMIN 角色分配所有权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id > 0
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 4. 创建编码序号表（用于生成唯一编码）
CREATE TABLE IF NOT EXISTS `sys_code_sequence` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code_type` VARCHAR(50) NOT NULL COMMENT '编码类型：STORE, STAFF, PRODUCT, CUSTOMER, SUPPLIER, ROLE',
    `last_code` VARCHAR(100) COMMENT '最后一个编码',
    `sequence` BIGINT DEFAULT 0 COMMENT '当前序号',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code_type` (`code_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编码序号表';

-- 5. 初始化编码序号（从1开始）
INSERT INTO `sys_code_sequence` (`code_type`, `last_code`, `sequence`) VALUES
('STORE', 'STORE000000', 0),
('STAFF', 'EMP000000', 0),
('PRODUCT', 'PROD00000000', 0),
('CUSTOMER', 'CUST00000000', 0),
('SUPPLIER', 'SUPP000000', 0),
('ROLE', 'ROLE0000', 0)
ON DUPLICATE KEY UPDATE code_type = code_type;






