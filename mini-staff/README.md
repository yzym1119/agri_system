# 员工小程序

当前仅E0设备验证工程，非已交付员工业务端。AppID留空；由实际账号持有人配置，绝不填写AppSecret。

```bash
npm --prefix packages/mobile-contracts ci --ignore-scripts
npm --prefix packages/mobile-contracts test
npm --prefix mini-staff ci --ignore-scripts
npm --prefix mini-staff run type-check
npm --prefix mini-staff run build:mp-weixin
```

将dist/build/mp-weixin导入微信开发者工具，使用自己的员工AppID。源码AppID为空时，DCloud会在生成的project.config.json自动填写touristappid；它仅为游客构建占位，不是实际申请账号。不能把AppSecret放到前端。真机扫码、录音、导航、前后台和弱网门槛仍按员工实施计划E0逐项验证；当前页面只实现扫码/存储/安全随机源检查，录音、导航及设备实测继续补齐。无后台连接、登录或交易提交；不能据页面成功提示宣称业务闭环通过。

依赖版本来自[DCloud官方Vue3 TS模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)，仅保留微信构建依赖并锁版本；安全随机源接口依据[腾讯官方文档](https://intl.cloud.tencent.com/ind/document/product/1219/57715)，运行时缺失即拒绝生成请求号。此版本组合已编译后才能列为工程验证通过，真实基础库兼容仍待真机。
