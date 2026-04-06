# Core Domain 模块

## 功能说明

核心领域层模块，包含领域模型、实体、值对象、聚合根和领域服务。

## 模块职责

- **domain/model/entity**: 领域实体，包含业务属性和行为
- **domain/model/value-object**: 值对象，不可变对象
- **domain/service**: 领域服务，处理不能归属单一实体的业务规则
- **domain/event**: 领域事件定义

## 设计原则

- 高内聚低耦合
- 领域模型不依赖基础设施
- 实体包含业务逻辑，不只有 getter/setter
- 值对象不可变

## 依赖关系

- 不依赖其他业务模块
- 只依赖 Spring Boot Validation（用于参数校验）

## 使用说明

其他模块通过依赖 `core-domain` 来使用领域模型。

## 已知问题

暂无

## TODO

- [ ] 添加领域事件支持
- [ ] 完善值对象验证逻辑






















