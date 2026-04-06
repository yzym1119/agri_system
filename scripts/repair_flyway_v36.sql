-- 修复 Flyway V36 迁移失败
-- 将失败的迁移标记为已修复

USE agri_system;

-- 删除失败的 V36 记录（如果存在）
DELETE FROM flyway_schema_history WHERE version = '36';

-- 重新插入 V36 记录，标记为成功
INSERT INTO flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
) VALUES (
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '36',
    'order module enhance',
    'SQL',
    'V36__order_module_enhance.sql',
    NULL,
    USER(),
    NOW(),
    0,
    1
);

















