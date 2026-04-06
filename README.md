# 农资行业进销存系统

## 项目简介

基于 DDD-Lite 架构的高性能、可维护、可扩展的农资行业进销存系统。

## 技术栈

- **Java**: 17
- **Spring Boot**: 3.2.0
- **MyBatis-Flex**: 数据持久化
- **MySQL**: 8.x
- **Redis**: 7.x
- **Sa-Token**: 权限认证
- **Flyway**: 数据库迁移
- **OpenAPI/Swagger**: API 文档
- **OpenTelemetry**: 链路追踪

## 项目结构

```
agri-system-parent/
├── core-domain/          # 核心领域层
├── core-application/     # 核心应用层
├── core-infrastructure/  # 核心基础设施层
├── api-admin/            # 管理端 API
└── api-client/           # 客户端 API
```

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose（用于 MySQL 和 Redis）

### 启动步骤

1. 启动基础设施服务：
```bash
docker-compose up -d mysql redis
```

2. 构建项目：
```bash
mvn clean install -DskipTests
```

3. 启动管理端 API：
```bash
mvn spring-boot:run -pl api-admin
```

4. 启动客户端 API（新终端）：
```bash
mvn spring-boot:run -pl api-client
```

### 测试接口

```bash
# 管理端健康检查
curl http://localhost:8080/health

# 客户端健康检查
curl http://localhost:8081/health

# Swagger 文档
# 管理端: http://localhost:8080/swagger-ui.html
# 客户端: http://localhost:8081/swagger-ui.html
```

## 开发规范

请参考 [AI_GENERATION_RULES.md](./AI_GENERATION_RULES.md) 了解详细的开发规范和约束。

### 核心原则

- DDD-Lite 分层架构
- 高内聚低耦合
- 无幻觉约束（禁止生成未定义的字段）
- 所有写操作必须幂等
- 关键逻辑保持强一致性

## 模块说明

- [core-domain](./core-domain/README.md) - 领域层
- [core-application](./core-application/README.md) - 应用层
- [core-infrastructure](./core-infrastructure/README.md) - 基础设施层
- [api-admin](./api-admin/README.md) - 管理端 API
- [api-client](./api-client/README.md) - 客户端 API

## CI/CD

项目使用 GitHub Actions 进行持续集成和部署，工作流包括：

- 代码检查（SpotBugs, Checkstyle）
- 单元测试
- 构建 Docker 镜像
- 推送到容器镜像仓库

详见 [.github/workflows/ci-cd.yml](./.github/workflows/ci-cd.yml)

## 部署

### Docker Compose 部署

```bash
# 启动所有服务（MySQL + Redis + 应用）
docker-compose up -d
```

### 单独部署应用

参考各模块的 README 文件。

## 许可证

待定

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request






















