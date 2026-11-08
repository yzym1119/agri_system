# 员工小程序

库房提货已接入原`order:pickup:verify`权限：本店游标任务列表、原批次/货位/最新数量核对、扫码只辅助定位及原确认入口。逐批人工勾选并确认已实际交付；未知结果按员工/门店/请求保存有限原摘要，重建页面仍先核对同请求，不自动换摘要提交。存储失败冻结，完成事实读取后验证清理。实际微信扫码、杀进程及手机存储可靠性仍待AppID和真机验收。阶段记录见`../docs/reviews/2026-11-01-提货数量核对进度.md`；下文旧“手机提货仍待”已由本条更新。

当前已含E0设备验证、E1登录/原收银核对、E2只读扫码查货、E3分次收货/原请求核对及E4本人配送部分工程，非完整员工业务端。用户明确尚未申请AppID，先完成工程和自动测试；真机验收后续单列。AppID由实际账号持有人配置，绝不填写AppSecret。

界面规范见`DESIGN.md`：色板与字号令牌定义在`src/styles/app.css`的`page{}`上，图标为`components/AppIcon`纯CSS线性图标；页面只用全局类，`<style scoped>`只处理自身布局。静态设计稿为`design/preview.html`（8屏，`?only=N&zoom=2`单屏放大）。

```bash
npm --prefix packages/mobile-contracts ci --ignore-scripts
npm --prefix packages/mobile-contracts test
npm --prefix mini-staff ci --ignore-scripts
npm --prefix mini-staff test
npm --prefix mini-staff run type-check
npm --prefix mini-staff run build:mp-weixin
```

将dist/build/mp-weixin导入微信开发者工具，使用自己的员工AppID。源码AppID为空时，DCloud自动生成touristappid游客占位，不是实际申请账号。

服务地址在本目录.env.local设置VITE_API_BASE_URL，参考.env.example；不要提交本地配置。生产构建只接受HTTPS源站，dev构建额外接受本机/私有网段HTTP。地址为空时禁用登录，仍可进入设备检查，不默认向公共测试站发账号密码。

登录沿现有/auth/staff/login，工作台每次显示前读取/auth/staff/info；只在内存保存令牌，不保存密码。无权限和缺门店不回退旧权限；隐藏中的旧登录/身份读取不能污染新会话。工作台已接设备检查和按权限进入的原收银核对页，不显示虚构收货或配送任务。原请求持久化与核对页已实现，未知只核原UUID；取消须后台屏障，结束已成交单据须二读匹配原订单。尚未开放销售写入，协调器发送回调在E6接入。

查货入口使用inventory:view，只能读取当前员工所属门店。商品/包装码、真实内部收货批次和货位分型，重码人工选择，拒相机可手输/按商品名搜索。库存主单位汇总和30条分页来自同一账本快照，缺原基准证据显示待核对，缺日期保持未知；不展示成本或写库存。新`/api/admin/mobile`四个入口是裸DTO，严格区分旧库存字符串200包装。后端源码已构建回归，旧3092固定预览JAR尚未升级；配置服务地址时需使用包含新API的后端。详见`../docs/reviews/2026-10-30-员工扫码与库存只读验收.md`。

收货入口只需purchase:inbound，读取本店待收采购及原单位/粒度。扫码只定位本采购行，重码选行；实际数量/成本留空，按原文精确校验，超收说明在保存前检查。原POST receipts仍是唯一写入口；先存回读固定摘要，随后只按同requestKey核对原行批次事实、同体重试，二读同receiptId后才结束。没有取消未知或强制清记录按钮。进程内未提交草稿可在同员工/店/单前后台恢复，摘要必须重新确认；杀进程恢复只覆盖已持久化的提交。详见`../docs/reviews/2026-10-30-员工分次收货与原请求核对.md`。新三个只读采购入口为裸DTO，不能与原POST字符串200包装混解。

本人配送使用独立order:delivery:assigned，不授整店发货/订单查询或员工名单。V134只增权限项，须按实际角色明确授权，原环境尚未升级。只列本人任务；可交明细才给必要电话地址，暂停/完成不返回联系人。逐项与收货人确认后提交原指纹，锁内重核指派和余量；未知先读同任务，不自动POST或换key。只有文字地址时复制到地图，不猜坐标。手机提货和管理端实际改派仍待E4后续，详见`../docs/reviews/2026-10-30-员工本人配送工程验收.md`。

设备页提供扫码、存储、安全请求号、录音/试听/清除、网络状态及明确GCJ02目的地导航检查。录音仅手机临时文件，不上传；隐藏时停止并清除页面引用，不能把它当业务资料采集完成。网络类型可读不证明API可达或弱网业务恢复。真机扫码、权限拒绝、录音、导航、前后台和弱网仍按E0实测；目前自动测试不证明这些硬件能力。

mini-staff的页面测试经真实Vue compileScript和TypeScript转译后在隔离VM中执行实际setup脚本及真实认证服务，注入生命周期/网络替身，覆盖隐藏登录、成功跳转、重叠刷新、旧401、断网及无配置。它不是微信模拟器或手机真机验收。

依赖版本来自[DCloud官方Vue3 TS模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)，仅保留微信构建依赖并锁版本；安全随机源接口依据[腾讯官方文档](https://intl.cloud.tencent.com/ind/document/product/1219/57715)，运行时缺失即拒绝生成请求号。此版本组合已编译后才能列为工程验证通过，真实基础库兼容仍待真机。
