# API Admin 模块

## 功能说明

管理端 API 模块，提供给管理员使用的 REST API。

## 功能特性

- RESTful API
- OpenAPI/Swagger 文档
- Sa-Token 权限认证
- 统一错误处理
- 请求日志和链路追踪

## API 列表

### 健康检查
- `GET /health` - 系统健康检查

### Swagger 文档
- `GET /swagger-ui.html` - Swagger UI
- `GET /api-docs` - OpenAPI 文档

## 本地运行说明

### 前置条件

1. JDK 17+
2. Maven 3.6+
3. MySQL 8.x
4. Redis 7.x

### 启动步骤

1. 在仓库根目录准备环境变量:
```bash
cp .env.example .env
```

2. 启动 MySQL 和 Redis:
```bash
docker compose --env-file .env up -d mysql redis
```

3. 运行应用:
```bash
mvn -pl api-admin spring-boot:run
```

4. 空库本地开发初始化账号:
```bash
MYSQL_PWD=agri1234 mysql -h 127.0.0.1 -P 3306 -u agri -D agri_system < scripts/dev-bootstrap.sql
```

默认账号：`admin / admin123`。该脚本只用于本地开发，不要在生产库执行。

或直接运行主类：
```bash
java -jar api-admin/target/api-admin-0.0.1-SNAPSHOT.jar
```

### 测试接口

```bash
# 健康检查
curl http://localhost:8080/health

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

## 部署说明

### Docker 部署

```bash
# 构建镜像
docker build -f api-admin/Dockerfile -t api-admin:latest .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e DB_HOST=mysql \
  -e DB_PASSWORD=your_password \
  -e REDIS_HOST=redis \
  api-admin:latest
```

### 环境变量

- 统一使用仓库根目录 `.env` 中的配置项
- 推荐默认值见仓库根目录 `.env.example`

## DB 迁移说明

数据库迁移由 Flyway 自动执行，迁移脚本位于 `core-infrastructure/src/main/resources/db/migration/`。

## 已知问题

暂无

## TODO

- [ ] 实现完整的权限管理 API
- [ ] 添加操作日志记录
- [ ] 完善错误码文档




















