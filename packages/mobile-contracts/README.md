# 移动端共享合同

纯TypeScript平台端口，员工/客户前端可复用；不包含库存、报价、身份或资金规则。当前实现单次扫码互斥、取消/拒绝授权区分、存储回读核验、安全UUIDv4及明确GCJ02导航。

运行：`npm ci --ignore-scripts`，然后`npm test`。测试调用真实适配器、以内存端口模拟平台回调；只能证明适配逻辑，不证明手机相机、微信基础库或业务写入。

读取错误与记录不存在分开；写入/删除后再次核验。交易调用者必须先成功保存原请求摘要，失败就禁止提交。接口没有业务POST和自动重试，不能把本包当成已经实现POS请求恢复。

安全随机源通过显式注入提供；缺失、失败、长度错误都拒绝，不回退Math.random。地址没有可信坐标时仅返回可手工使用的地址，不猜坐标或自动授权定位。

## 客户身份响应契约

`@agri/mobile-contracts/customer-auth`对应现有客户后端：`decodeCustomerLogin`、`decodeCustomerOnboarding`、`decodeCustomerProfile`、`decodeCustomerEnrollment`。这是白名单解码，不请求接口、不持久化令牌，也不代替后端授权。

- 客户与员工会话类型分开，不从客户返回拼staffId/storeId或员工权限。真实微信登录只接受LINKED，拒绝DEV_ONLY与员工角色；原手机号等宽DTO字段不进入客户登录状态。
- PHONE_REQUIRED/PHONE_VERIFIED仅临时凭据。POST签发与GET info分开，读取阶段不得冒充新令牌；期限保留后端epoch秒，原600秒由服务器执行，不以手机时钟拒绝或续期。
- 开户CREATED/关联LINKED结果仍须重新平台登录；REVIEW_REQUIRED联系门店，UNKNOWN仅沿原UUID核对。结果串号或status/nextAction不匹配拒绝，不能据结果创建客户会话。
- 目前仅前置契约，mini-customer工程与页面尚未创建；不代表真实微信授权或手机验收通过。
