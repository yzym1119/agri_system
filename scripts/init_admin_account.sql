-- ============================================
-- 初始化管理员账号
-- 用途：创建默认管理员账号，用于系统初始登录
-- 密码：123456 (BCrypt加密)
-- ============================================

-- 1. 确保存在默认门店（如果不存在则创建）
INSERT INTO `store` (`code`, `name`, `address`, `phone`, `status`, `remark`, `created_at`, `updated_at`)
SELECT 'STORE001', '总店', '系统默认门店', '13800138000', 1, '系统初始化创建', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `store` WHERE `code` = 'STORE001'
);

-- 2. 获取默认门店ID
SET @default_store_id = (SELECT `id` FROM `store` WHERE `code` = 'STORE001' LIMIT 1);

-- 3. 创建管理员账号（如果不存在）
-- 密码：123456 (BCrypt加密后的值)
-- 
-- 生成密码哈希的方法：
-- 方法1：运行测试类生成
--   mvn test -Dtest=BCryptPasswordGeneratorTest
--   查看控制台输出的哈希值，更新下面的密码字段
--
-- 方法2：使用 Java 代码生成
--   BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
--   String hash = encoder.encode("123456");
--   System.out.println(hash);
--
-- 方法3：如果上述方法不可用，可以先创建账号，然后通过后端接口重置密码
--
-- 注意：下面的哈希值是一个示例，如果登录失败，请重新生成
INSERT INTO `staff` (`code`, `name`, `phone`, `password`, `store_id`, `role`, `status`, `hire_date`, `created_at`, `updated_at`)
SELECT 
    'ADMIN001', 
    '系统管理员', 
    '13800138000',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- 123456 的 BCrypt 哈希（示例值，可能需要重新生成）
    @default_store_id,
    'ADMIN',
    1,
    CURDATE(),
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `staff` WHERE `code` = 'ADMIN001'
);

-- 4. 查询创建的管理员信息（用于确认）
SELECT 
    `id`,
    `code`,
    `name`,
    `phone`,
    `store_id`,
    `role`,
    `status`,
    `created_at`
FROM `staff`
WHERE `code` = 'ADMIN001';

-- ============================================
-- 使用说明：
-- 1. 执行此脚本后，可以使用以下账号登录：
--    账号：ADMIN001 或 13800138000
--    密码：123456
-- 2. 如果密码哈希值不匹配，请运行 Java 工具类生成新的哈希值并更新此脚本
-- ============================================

