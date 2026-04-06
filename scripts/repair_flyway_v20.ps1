# ============================================
# Flyway 修复脚本：清理 V20 失败的迁移记录
# ============================================
# 使用说明：
# 1. 此脚本会连接到 MySQL 数据库并清理失败的 V20 迁移记录
# 2. 执行后可以重新启动应用，Flyway 会重新执行迁移
# ============================================

$dbName = "agri_system"
$dbUser = "root"
$dbPassword = "1234"
$dbHost = "localhost"
$dbPort = "3306"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Flyway V20 修复脚本" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 检查 MySQL 是否可用
Write-Host "正在检查 MySQL 连接..." -ForegroundColor Yellow
$testConnection = mysql -h $dbHost -P $dbPort -u $dbUser -p$dbPassword -e "SELECT 1" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：无法连接到 MySQL 数据库" -ForegroundColor Red
    Write-Host "请检查："
    Write-Host "  1. MySQL 服务是否运行"
    Write-Host "  2. 数据库用户名和密码是否正确"
    Write-Host "  3. 数据库 $dbName 是否存在"
    exit 1
}

Write-Host "MySQL 连接成功！" -ForegroundColor Green
Write-Host ""

# 查看当前状态
Write-Host "查看当前 V20 迁移状态..." -ForegroundColor Yellow
mysql -h $dbHost -P $dbPort -u $dbUser -p$dbPassword $dbName -e "
SELECT 
    version, 
    description, 
    type, 
    script, 
    installed_on, 
    success, 
    installed_rank
FROM flyway_schema_history 
WHERE version = '20' 
ORDER BY installed_rank DESC;
"

Write-Host ""
Write-Host "正在删除失败的迁移记录..." -ForegroundColor Yellow

# 删除失败的记录
mysql -h $dbHost -P $dbPort -u $dbUser -p$dbPassword $dbName -e "
DELETE FROM flyway_schema_history 
WHERE version = '20' AND success = 0;
"

if ($LASTEXITCODE -eq 0) {
    Write-Host "成功删除失败的迁移记录！" -ForegroundColor Green
} else {
    Write-Host "警告：删除操作可能失败，请手动检查" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "查看修复后的状态..." -ForegroundColor Yellow
mysql -h $dbHost -P $dbPort -u $dbUser -p$dbPassword $dbName -e "
SELECT 
    version, 
    description, 
    type, 
    script, 
    installed_on, 
    success 
FROM flyway_schema_history 
WHERE version = '20' 
ORDER BY installed_rank DESC 
LIMIT 5;
"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "修复完成！" -ForegroundColor Green
Write-Host "现在可以重新启动应用程序" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan








