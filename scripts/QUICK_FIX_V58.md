# 快速修复 V58 Flyway 校验和问题

## 问题说明
V58 迁移文件已被修改，但数据库中已执行了旧版本，导致校验和不匹配。

## 解决方案（选择其一）

### 方案 1：在 MySQL 中执行 SQL（推荐）

1. 打开 MySQL 客户端（命令行、Workbench 或 Navicat）
2. 连接到 `agri_system` 数据库
3. 执行以下 SQL：

```sql
-- 修复 V58 校验和
UPDATE `flyway_schema_history` 
SET `checksum` = 14658361 
WHERE `version` = '58';

-- 更新权限标识
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1103;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:query' WHERE `id` = 1112;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:create' WHERE `id` = 1113;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:update' WHERE `id` = 1114;
UPDATE `sys_menu` SET `permission` = 'marketing:points:goods:delete' WHERE `id` = 1115;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:query' WHERE `id` = 1116;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:audit' WHERE `id` = 1117;
UPDATE `sys_menu` SET `permission` = 'marketing:points:order:verify' WHERE `id` = 1118;
```

4. 重新启动应用

### 方案 2：使用 MySQL 命令行

```bash
mysql -u root -p agri_system < scripts/fix_flyway_v58_and_update_permissions.sql
```

### 方案 3：临时禁用 Flyway 验证（不推荐，仅用于紧急情况）

在 `application-dev.yml` 中添加：
```yaml
spring:
  flyway:
    validate-on-migrate: false
```

**注意**：方案 3 只是临时绕过验证，建议使用方案 1 或 2 彻底解决问题。









