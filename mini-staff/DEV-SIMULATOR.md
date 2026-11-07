# 员工端 · 测试号 + 微信开发者工具模拟器开发指南

本文件只讲**开发阶段**怎么把员工端跑起来，不代替真机验收，也不代表可以上线。

## 两个 AppID 不要混

| 类型 | 状态 | 用途 |
|---|---|---|
| **小程序测试号 AppID** | ✅ 已有：`wxa4ce54aa69b2c3fc`（见下方来源） | 微信开发者工具导入工程、模拟器里开发/联调全部功能 |
| 正式主体认证 AppID | ❌ 未申请 | 发布、真机预览、微信支付、正式域名；**不是开发的前置条件** |

测试号来源（本机 ignored 配置，不提交）：
`.superpowers/sdd/2026-10-29-员工小程序实施计划/wechat-local-backup/project.config.json` 的 `appid`。
`src/manifest.json` 里 appid 为空是**故意的**——DCloud 会生成 `touristappid` 占位，
正式账号由账号持有人在开发者工具导入工程时填入，源码不写 AppID、绝不写 AppSecret。

## 一、构建并配置测试号

```bash
npm --prefix packages/mobile-contracts ci --ignore-scripts
npm --prefix mini-staff ci --ignore-scripts
npm --prefix mini-staff test              # 页面自动测试
npm --prefix mini-staff run type-check
npm --prefix mini-staff run build:mp-weixin
```

构建产物 `mini-staff/dist/build/mp-weixin/project.config.json` 会带 `appid: "touristappid"`。
导入开发者工具前，把该文件的 `appid` 改成测试号 `wxa4ce54aa69b2c3fc`。
**该文件是构建产物、不在 git 跟踪内**，改它不会污染源码；每次重新构建后需要再改一次
（或把测试号写进开发者工具的项目设置，由工具持久化）。

## 二、配置后端地址

`mini-staff/.env.local`（ignored，不提交）：

```bash
VITE_API_BASE_URL=http://<服务器IP>:<端口>
```

- 生产构建（`npm run build:mp-weixin`）只接受 **HTTPS** 源站；
- 开发构建（`npm run dev:mp-weixin`，`import.meta.env.DEV`）额外允许 localhost / 内网 HTTP；
- 地址为空时 `serviceConfigured=false`，登录被禁用，但设备检查页仍可进入（不会向公共测试站发账号密码）。

两种方式二选一：

1. **连已部署的服务器**（用户说后端已部署、暂无域名）：填服务器 IP:端口，
   在开发者工具「详情 → 本地设置」临时勾选
   「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」。
2. **连本机后端**：启动 `api-admin` 后填 `http://127.0.0.1:<端口>`，同样临时关闭域名校验。

> ⚠️ 「不校验合法域名」只用于本机/内网开发联调，**联调完必须恢复未勾选**；
> 正式构建默认 `urlCheck: true`，不得关闭。

## 三、导入与联调

```bash
"/Applications/wechatwebdevtools.app/Contents/MacOS/cli" open --project /Users/yzym/project/agri_system/mini-staff/dist/build/mp-weixin
```

模拟器里可完整开发/联调：

- 登录 `/auth/staff/login` → 工作台 `/auth/staff/info`（按真实权限显隐入口）
- E2 扫码查货 → 库存详情（本店、主单位、批次/货位、分页）
- E3 分次收货 / E4 本人配送签收 / 库房提货 / POS 原请求核对与恢复
- 请求结果未知 → 冻结原请求 → 同 requestKey 核对，不自动重发
- 改派后旧任务被明确拒绝的提示（2026-11-07 收口）

## 四、这套环境能验与不能验

**能验**：页面与交互、接口联调、身份/门店/权限隔离、请求恢复与幂等、构建产物可运行。

**不能验**（仍属真机验收，见 `docs/reviews/2026-11-07-员工端E4收尾与真机验收清单.md`）：
原生扫码与相机权限拒绝、录音/试听、GCJ02 导航、弱网与断网中断、杀进程重启、
微信存储可靠性、真机兼容性、微信支付、正式域名与证书。

模拟器结果与自动测试**不得**写成真机通过。

## 五、禁止事项

- 不把 AppSecret、`.env.local`、测试号的正式凭据写进源码或提交；
- 不在生产库造单、清库或虚构成本（需用户明确授权）；
- 不长期关闭域名/TLS 校验；
- 不用测试号对外发布或面向真实客户收款。
