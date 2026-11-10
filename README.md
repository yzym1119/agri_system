# agri_system

当前库存已统一为采购收货、销售、原单退货、盘点和调拨共用的一套库存账，无 SKU 模式开关。运行及验收见 [统一库存说明](docs/runbooks/retail-fact-chain-rollout.md) 和 [修复验收](docs/reviews/2026-10-12-库存统一修复验收.md)。开发修改遵循 [AGENTS.md](AGENTS.md)。


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
- MySQL 8
- Redis 7
- Node.js 18+（前端需要）

### 1. 准备环境变量

```bash
cp .env.example .env
```

默认本地开发使用：

- MySQL: `127.0.0.1:3306/agri_system`
- 用户名: `agri`
- 密码: `agri1234`
- Redis: `127.0.0.1:6379`

如果你要改端口、密码或主机，只改 `.env` 这一处。

### 2. 启动基础设施

方案 A：本机已有 MySQL / Redis

- 只需要确保 `.env` 中的连接信息和本机实例一致
- MySQL 中存在库 `agri_system`

方案 B：使用 Docker

```bash
docker compose --env-file .env up -d mysql redis
```

### 3. 构建后端

```bash
mvn clean install -DskipTests
```

如果你是空库首次启动，先启动任一后端入口让 Flyway 自动建表，再执行本地开发初始化脚本创建默认门店和管理员账号：

```bash
MYSQL_PWD=agri1234 mysql -h 127.0.0.1 -P 3306 -u agri -D agri_system < scripts/dev-bootstrap.sql
```

默认管理端账号仅用于本地开发：`admin / admin123`。不要在生产库执行该脚本。

### 4. 启动服务

```bash
mvn -pl api-admin spring-boot:run
mvn -pl api-client spring-boot:run
```

管理端默认端口 `8080`，客户端默认端口 `8081`。

### 5. 启动前端

```bash
cd admin-web
npm install
npm run dev
```

### 启动自检

```bash
nc -z localhost 3306
nc -z localhost 6379
curl http://localhost:8080/health
curl http://localhost:8081/health
```

如果 `3306` 或 `6379` 不通，后端一定起不来。

## 文档入口

- `docs/README.md`：当前文档导航
- `ddl/README.md`：数据库说明
- `openapi/`：接口契约
- `docs/archive/2025/`：历史过程文档归档

## 说明

- 仓库已做文档精简，旧的阶段性汇报、交付记录、问题排查材料已迁入归档目录。
- 归档文档仅用于追溯历史背景，不作为当前开发规范或交付标准。


### 客户端微信小程序

工程在`mini-customer/`，当前C0含首页、微信登录、本人资料、档案核验四页，未完成咨询/AI/交易客户端。安装、配置和实际验证命令见[客户工程入口](mini-customer/README.md)，设计与进度见[统一客户手册](docs/current/07-客户端设计与实施手册.md)。不在前端填写AppSecret，不把编译通过当真实微信/手机验收。
