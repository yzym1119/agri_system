# agri_system

农业行业进销存系统，采用多模块 Maven 结构，包含管理端接口、客户端接口、领域层、应用层与基础设施层。

## 项目结构

- `core-domain`：领域模型与领域服务
- `core-application`：应用服务与业务编排
- `core-infrastructure`：持久化、配置、三方集成
- `api-admin`：管理端 API
- `api-client`：客户端 API
- `admin-web`：管理端前端
- `ddl`：数据库设计与初始化脚本
- `openapi`：接口契约文件

## 技术栈

- Java 17
- Spring Boot 3
- MyBatis-Flex
- MySQL 8
- Redis 7
- Sa-Token
- Flyway
- Vite + Vue 3

## 快速启动

### 依赖

- JDK 17+
- Maven 3.6+
- Docker / Docker Compose
- Node.js 18+（前端需要）

### 启动基础设施

```bash
docker-compose up -d mysql redis
```

### 构建后端

```bash
mvn clean install -DskipTests
```

### 启动服务

```bash
mvn spring-boot:run -pl api-admin
mvn spring-boot:run -pl api-client
```

### 启动前端

```bash
cd admin-web
npm install
npm run dev
```

## 文档入口

- `docs/README.md`：当前文档导航
- `ddl/README.md`：数据库说明
- `openapi/`：接口契约
- `docs/archive/2025/`：历史过程文档归档

## 说明

- 仓库已做文档精简，旧的阶段性汇报、交付记录、问题排查材料已迁入归档目录。
- 归档文档仅用于追溯历史背景，不作为当前开发规范或交付标准。
