# Core Infrastructure 模块

## 功能说明

核心基础设施层模块，负责技术实现和外部系统集成。

## 模块职责

- **infrastructure/repository**: 数据持久化实现（MyBatis-Flex）
- **infrastructure/config**: 配置类
- **infrastructure/external**: 第三方接口实现
- **infrastructure/mapper**: MyBatis Mapper 接口和 XML

## 技术栈

- MyBatis-Flex: 数据持久化
- MySQL: 关系型数据库
- Redis: 缓存和会话存储
- Flyway: 数据库迁移
- Sa-Token: 权限认证
- OpenTelemetry: 链路追踪

## 依赖关系

- 依赖 `core-domain` 模块
- 依赖 `core-application` 模块
- 实现应用层定义的仓储接口

## 数据库迁移

数据库迁移脚本位于 `src/main/resources/db/migration/`，使用 Flyway 管理。

命名规范：`V{version}__{description}.sql`

## 使用说明

通过 Spring Boot 自动配置，无需手动初始化。

## 已知问题

暂无

## TODO

- [ ] 完善 OpenTelemetry 配置
- [ ] 添加分布式锁实现
- [ ] 实现消息队列集成






















