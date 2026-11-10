# 客户端微信小程序

设计与进度统一维护于[07 客户端手册](../docs/current/07-客户端设计与实施手册.md)、[02 当前状态](../docs/current/02-当前开发状态.md)和[10 运行验收](../docs/current/10-运行部署与验收手册.md)，本文件仅为工程入口。

当前C0四页：首页、微信登录、本人资料、档案核验。首页不强制登录；手机号授权只是临时核验，申请完成后重新微信登录，旧档案须门店审核。订单/钱包/知识/咨询/AI尚未接客户端，不以空列表或0元冒充完成。底部目前首页/我的两项，农技接口可用后再增加问农技。

## 本地安装与验证

仓库根目录执行：

```bash
npm --prefix packages/mobile-contracts ci --ignore-scripts
npm --prefix mini-customer ci --ignore-scripts
npm --prefix packages/mobile-contracts test
npm --prefix mini-customer test
npm --prefix mini-customer run type-check
npm --prefix mini-customer run build:mp-weixin
```

员工端同一套已锁版本，无依赖升级；禁止从其他工程直接复制node_modules作为交付。测试执行真实共享会话/恢复服务和编译后的真实Vue setup，以平台及网络替身覆盖异步边界，不是平台真实授权或手机验收。

## 配置与运行

1. 复制`.env.example`为本机`.env.local`，设置`VITE_API_BASE_URL`为api-client真实源站（默认服务端口8081），不带`/api/client`路径、查询或凭据。空配置时不发送微信code。生产只接受HTTPS；开发构建可接受合法内网/回环HTTP。
2. 微信AppID需要与后端客户AppID匹配；`src/manifest.json`默认留空，正式值只填`mp-weixin.appid`或在独立开发者工具工程中配置。AppSecret只由后端安全配置，不能放前端、报告或Git。
3. `npm --prefix mini-customer run dev:mp-weixin`生成`dist/dev/mp-weixin`；生产构建在`dist/build/mp-weixin`。微信开发者工具导入对应目录。`urlCheck`默认开启，本工程未为本地测试放宽它。没有AppID/域名与平台手机号能力时，只可验证编译和未连接页面，不能宣称完整微信授权可用。
4. 实际申请存储按运行时`wx.getAccountInfoSync()`的AppID与源站区分，只保存版本和安全UUID，不存手机号、姓名或登录令牌。缺实际AppID时不能写申请，不用随机名字冒充命名空间。
5. 首页可随时访问。个人资料读取失败会清当前客户会话并要求重登；退出先清内存，保留未知申请线索。待核验不开放旧资金/订单；UNKNOWN继续原号核对，不清缓存、换号或伪造成功。

当前无H5构建脚本，无生产发布。独立审查及真实微信/安卓/iPhone验收仍须另过，不能把自动绿当上线完成。
