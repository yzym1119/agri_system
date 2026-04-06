# API Client 模块

## 功能说明

客户端 API 模块，提供给客户端使用的 REST API。

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

1. 启动 MySQL 和 Redis（使用 docker-compose）:
```bash
docker-compose up -d mysql redis
```

2. 配置环境变量（可选）:
```bash
export DB_PASSWORD=your_password
export REDIS_PASSWORD=your_redis_password
```

3. 运行应用:
```bash
mvn spring-boot:run -pl api-client
```

或直接运行主类：
```bash
java -jar api-client/target/api-client-0.0.1-SNAPSHOT.jar
```

### 测试接口

```bash
# 健康检查
curl http://localhost:8081/health

# Swagger UI
open http://localhost:8081/swagger-ui.html
```

## 部署说明

### Docker 部署

```bash
# 构建镜像
docker build -f api-client/Dockerfile -t api-client:latest .

# 运行容器
docker run -d \
  -p 8081:8081 \
  -e DB_HOST=mysql \
  -e DB_PASSWORD=your_password \
  -e REDIS_HOST=redis \
  api-client:latest
```

### 环境变量

- `DB_HOST`: MySQL 主机地址
- `DB_PORT`: MySQL 端口（默认 3306）
- `DB_NAME`: 数据库名（默认 agri_system）
- `DB_USERNAME`: 数据库用户名
- `DB_PASSWORD`: 数据库密码
- `REDIS_HOST`: Redis 主机地址
- `REDIS_PORT`: Redis 端口（默认 6379）
- `REDIS_PASSWORD`: Redis 密码

## DB 迁移说明

数据库迁移由 Flyway 自动执行，迁移脚本位于 `core-infrastructure/src/main/resources/db/migration/`。

## 已知问题

暂无

## TODO

- [ ] 实现完整的业务 API
- [ ] 添加幂等性支持
- [ ] 完善错误码文档






















