-- ============================================
-- V2: 员工表
-- ============================================

CREATE TABLE IF NOT EXISTS `staff` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '员工编码',
    `name` VARCHAR(50) NOT NULL COMMENT '员工姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `id_card` VARCHAR(18) COMMENT '身份证号',
    `store_id` BIGINT NOT NULL COMMENT '所属门店ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：ADMIN-管理员，MANAGER-店长，STAFF-员工',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-在职，2-离职',
    `hire_date` DATE COMMENT '入职日期',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT COMMENT '创建人ID',
    `updated_by` BIGINT COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_staff_code` (`code`),
    UNIQUE KEY `uk_staff_phone` (`phone`),
    KEY `idx_staff_store` (`store_id`),
    KEY `idx_staff_status` (`status`),
    KEY `idx_staff_role` (`role`),
    CONSTRAINT `fk_staff_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';




















