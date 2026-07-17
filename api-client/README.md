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
mvn -pl api-client spring-boot:run
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

- 统一使用仓库根目录 `.env` 中的配置项
- 推荐默认值见仓库根目录 `.env.example`

## DB 迁移说明

数据库迁移由 Flyway 自动执行，迁移脚本位于 `core-infrastructure/src/main/resources/db/migration/`。

## 已知问题

暂无

## TODO

- [ ] 实现完整的业务 API
- [ ] 添加幂等性支持
- [ ] 完善错误码文档




















