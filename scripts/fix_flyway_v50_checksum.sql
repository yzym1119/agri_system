-- ============================================
-- 修复 Flyway V50 迁移校验和
-- ============================================
-- 说明：由于我们修改了 V50 文件后又恢复，需要更新数据库中的校验和
-- 执行此脚本后，重启应用即可

-- 更新 V50 的校验和以匹配当前文件内容
-- 注意：需要先计算当前文件的校验和，然后更新数据库
-- 如果文件已经恢复到原始状态，可以使用 Flyway repair 命令，或者手动更新

-- 方法1：使用 Flyway repair（推荐）
-- 在应用启动时，Flyway 会自动修复校验和

-- 方法2：手动更新校验和（如果 repair 不工作）
-- 先查看当前 V50 的状态
SELECT `version`, `description`, `checksum`, `installed_on`, `success`
FROM `flyway_schema_history`
WHERE `version` = '50';

-- 如果需要手动修复，取消下面的注释并执行
-- 注意：需要先计算当前文件的正确校验和
-- UPDATE `flyway_schema_history`
-- SET `checksum` = [新的校验和]
-- WHERE `version` = '50';
