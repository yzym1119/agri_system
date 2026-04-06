-- ============================================
-- V46: Staff RBAC & Operation Log
-- ============================================

-- 1. 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `status` TINYINT DEFAULT 1 COMMENT '1-启用 0-停用',
    `remark` VARCHAR(200),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_code` (`code`)
) COMMENT='系统角色';

-- 2. 菜单/权限表
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `parent_id` BIGINT DEFAULT 0,
    `name` VARCHAR(50) NOT NULL,
    `permission` VARCHAR(100) COMMENT '权限标识',
    `type` TINYINT COMMENT '1-菜单 2-按钮',
    `path` VARCHAR(255),
    `icon` VARCHAR(50),
    `sort_order` INT DEFAULT 0,
    `visible` TINYINT DEFAULT 1 COMMENT '1-显示 0-隐藏'
) COMMENT='系统菜单/权限';

-- 3. 角色-菜单关联
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `menu_id`),
    CONSTRAINT `fk_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_menu_menu` FOREIGN KEY (`menu_id`) REFERENCES `sys_menu`(`id`) ON DELETE CASCADE
) COMMENT='角色菜单关联';

-- 4. 员工-角色关联
CREATE TABLE IF NOT EXISTS `sys_staff_role` (
    `staff_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`staff_id`, `role_id`),
    CONSTRAINT `fk_staff_role_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_staff_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role`(`id`) ON DELETE CASCADE
) COMMENT='员工角色关联';

-- 5. 操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `staff_id` BIGINT,
    `module` VARCHAR(50),
    `action` VARCHAR(50),
    `method` VARCHAR(10),
    `request_uri` VARCHAR(255),
    `request_params` TEXT,
    `ip` VARCHAR(50),
    `cost_time` BIGINT,
    `status` TINYINT COMMENT '1-成功 0-失败',
    `error_msg` TEXT,
    `trace_id` VARCHAR(64),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_staff_time` (`staff_id`, `created_at`)
) COMMENT='系统操作日志';





