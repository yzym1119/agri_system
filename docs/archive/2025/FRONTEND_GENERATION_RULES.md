> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

# 前端代码生成规范 (Frontend Generation Rules)

**重要：** 生成任何前端代码前，必须阅读并遵守本规范。

## 1. 技术栈规范
### 管理后台 (Admin Web)
*   **框架：** Vue 3 (Composition API) + TypeScript + Vite
*   **UI库：** Element Plus
*   **状态管理：** Pinia (禁止使用 Vuex)
*   **路由：** Vue Router 4
*   **HTTP工具：** Axios (必须封装拦截器)
*   **CSS：** SCSS

### 小程序端 (Mobile)
*   **框架：** Uni-app (Vue 3 版本) + TypeScript
*   **UI库：** Uview-Plus 或 Uni-ui
*   **平台：** 重点适配微信小程序

## 2. 目录结构规范
*   `src/api`: 所有 API 请求必须独立封装，**禁止**在 .vue 文件中直接写 `axios.get`。
    *   例如：`src/api/product.ts` 对应后端 ProductController。
*   `src/types`: 所有接口的入参和响应必须定义 TS Interface。
    *   必须与后端 DTO 保持一致。
*   `src/views` (Web) / `src/pages` (Uni): 页面文件。
*   `src/components`: 通用组件。

## 3. 编码风格
*   **脚本风格：** 必须使用 `<script setup lang="ts">`。
*   **响应式：** 优先使用 `ref` 和 `computed`。
*   **命名：** 文件名使用小写连字符 (e.g., `product-list.vue`)，变量名使用驼峰。
*   **注释：** 复杂逻辑必须写注释。

## 4. 交互与错误处理
*   **Loading：** 所有异步请求必须有 Loading 状态（按钮 Loading 或 全局 Loading）。
*   **错误处理：** 必须通过 Axios 拦截器统一处理后端返回的 `code != 200` 的情况，**禁止**在组件内重复写 `try-catch` 弹窗。
*   **枚举处理：** 状态码（如订单状态）必须定义前端枚举或 Map，禁止硬编码数字（如 `v-if="status === 1"` 是禁止的）。

## 5. 鉴权处理
*   Token 必须存储在 `localStorage` (Web) 或 `uni.setStorageSync` (App)。
*   HTTP 请求头必须携带 `Authorization` 或 `satoken` 字段。
*   401 错误必须自动跳转登录页。