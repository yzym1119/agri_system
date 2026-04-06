# Core Application 模块

## 功能说明

核心应用层模块，负责业务流程编排和用例实现。

## 模块职责

- **application/service**: 应用服务，编排业务流程
- **application/dto**: 数据传输对象
- **application/command**: 命令对象
- **application/query**: 查询对象

## 设计原则

- 应用服务不包含业务规则，只负责编排
- 业务规则在领域层实现
- 应用服务调用领域服务和仓储
- 事务边界在应用服务层

## 依赖关系

- 依赖 `core-domain` 模块
- 不依赖 `core-infrastructure`（通过接口依赖）

## 使用说明

API 层通过依赖 `core-application` 来调用应用服务。

## 已知问题

暂无

## TODO

- [ ] 添加命令处理器模式
- [ ] 实现 CQRS 查询模型






















