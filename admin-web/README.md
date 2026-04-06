# 农资管理系统 - Web 管理后台

## 技术栈

- **框架：** Vue 3 (Composition API + Script Setup)
- **构建工具：** Vite
- **语言：** TypeScript
- **UI 库：** Element Plus (按需自动导入)
- **状态管理：** Pinia (持久化)
- **路由：** Vue Router 4
- **HTTP：** Axios

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置环境变量

复制 `.env.development` 并根据实际情况修改 API 地址：

```env
VITE_API_BASE_URL=http://localhost:8080
```

### 3. 启动开发服务器

```bash
npm run dev
```

访问：http://localhost:3000

### 4. 构建生产版本

```bash
npm run build
```

## 默认管理员账号

执行 `scripts/init_admin_account.sql` 脚本后，可以使用以下账号登录：

- **账号：** `ADMIN001` 或 `13800138000`
- **密码：** `123456`

## 项目结构

```
admin-web/
├── src/
│   ├── api/              # API 接口定义
│   ├── components/       # 通用组件
│   ├── router/          # 路由配置
│   ├── store/           # Pinia 状态管理
│   ├── types/           # TypeScript 类型定义
│   ├── utils/           # 工具类
│   ├── views/           # 页面组件
│   │   ├── login/       # 登录页
│   │   ├── layout/      # 布局组件
│   │   └── dashboard/   # 工作台
│   └── styles/          # 全局样式
├── .env                 # 环境变量
└── vite.config.ts       # Vite 配置
```

## 开发规范

请参考 `docs/FRONTEND_GENERATION_RULES.md`

## 接口文档

请参考 `docs/SYSTEM_INTERFACE_SUMMARY.md`



