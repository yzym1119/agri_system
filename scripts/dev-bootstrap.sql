-- Local development seed data. Do not run this in production.
-- Default admin login: admin / admin123

START TRANSACTION;

INSERT INTO `store` (`code`, `name`, `address`, `phone`, `status`, `support_pickup`, `remark`)
VALUES ('STORE_DEV', '默认开发门店', '本地开发环境', '13800000000', 1, 1, 'Local development seed store')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `address` = VALUES(`address`),
    `phone` = VALUES(`phone`),
    `status` = VALUES(`status`),
    `support_pickup` = VALUES(`support_pickup`),
    `remark` = VALUES(`remark`);

SET @dev_store_id = (SELECT `id` FROM `store` WHERE `code` = 'STORE_DEV' LIMIT 1);

INSERT INTO `staff` (`code`, `name`, `phone`, `password`, `store_id`, `role`, `status`, `deleted`, `hire_date`, `remark`)
VALUES (
    'admin',
    '本地开发管理员',
    '13800000001',
    '$2a$10$VGj6E9eTjei7.3st9wDeceuVCpgso4ONmwVtun/lTs2KAtasvY4Xu',
    @dev_store_id,
    'ADMIN',
    1,
    0,
    CURDATE(),
    'Local development seed admin; password is admin123'
)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `phone` = VALUES(`phone`),
    `password` = VALUES(`password`),
    `store_id` = VALUES(`store_id`),
    `role` = VALUES(`role`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`),
    `remark` = VALUES(`remark`);

SET @dev_staff_id = (SELECT `id` FROM `staff` WHERE `code` = 'admin' LIMIT 1);
SET @super_admin_role_id = (SELECT `id` FROM `sys_role` WHERE `code` = 'SUPER_ADMIN' LIMIT 1);

INSERT IGNORE INTO `sys_staff_role` (`staff_id`, `role_id`)
SELECT @dev_staff_id, @super_admin_role_id
WHERE @dev_staff_id IS NOT NULL AND @super_admin_role_id IS NOT NULL;

UPDATE `store`
SET `manager_id` = @dev_staff_id
WHERE `code` = 'STORE_DEV' AND @dev_staff_id IS NOT NULL;

COMMIT;
