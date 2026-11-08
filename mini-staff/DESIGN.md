# 员工端小程序界面规范

本文件约束 `mini-staff` 的视觉与交互。门店端 PC 规范见根目录 `../DESIGN.md`，两者共用同一套品牌色与字号档位，不各自另造主题。

## 视觉方向

员工在库房、货车、货架间单手操作，多为白天强光与移动场景。因此：浅底、大触控面、一屏一主操作、关键口径（数量/金额/单号）用等宽数字放大，状态用「文字 + 色块」双通道表达，不靠颜色单独承载信息。

## 令牌

令牌定义在 `src/styles/app.css` 的 `page{}` 选择器上，页面通过 `var(--c-xxx)` 引用。新增颜色必须先加令牌，不在页面写死色值。

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--c-primary` | `#1d5d73` | 主操作、选中、金额强调 |
| `--c-primary-strong` | `#16495a` | 主按钮按下、深色身份卡底 |
| `--c-primary-soft` / `--c-primary-line` | `#e9f2f5` / `#cfe0e7` | 主色浅底与描边 |
| `--c-accent` / `--c-accent-soft` | `#7ea76d` / `#eff5ea` | 品牌辅色、完成态辅助 |
| `--c-text` / `--c-text-2` / `--c-text-3` | `#1f2937` / `#4b5563` / `#76838f` | 正文 / 说明 / 次要 |
| `--c-line` / `--c-line-2` | `#e6eaee` / `#f0f2f5` | 边框 / 分隔线 |
| `--c-bg` / `--c-surface` | `#f5f6f8` / `#ffffff` | 页面底 / 卡片底 |
| `--c-warn` `--c-warn-bg` `--c-warn-line` | `#8a5a12` `#fdf7ec` `#f0e0c2` | 待核对、需人工确认 |
| `--c-danger` `--c-danger-bg` `--c-danger-line` | `#a12925` `#fdf1f0` `#f1d2cf` | 失败、不可逆提示 |
| `--c-ok` `--c-ok-bg` `--c-ok-line` | `#2b6a4a` `#eef6f1` `#cfe6d9` | 已确认、已同步 |

字号：正文 15px（`page` 基准）、次要 13px、辅助最小 12px、区块标题 15px 加粗、主数字 26px、金额 20px。行高 1.6。

## 间距与布局

- 页面统一用 `.page`（左右 16px、上 12px、底部叠加安全区）；有底部操作栏时再加 `.page-dock` 预留 96px，避免内容被遮挡。
- 垂直节奏只用 8 / 12 / 16 / 20 四档：卡片间距 `.block`（12px）、大分区间 `.block-lg`（20px）。
- 横向排列用 `.row` / `.row-between` / `.row-top` + `.grow`，不用手写 `display:flex`；间距用 `.mr8` `.ml10` 一类工具类，不写行内 `style`。
- 单列流式布局，最大宽度 600px 居中；不做左右分栏——手机竖屏放不下两列有效信息。
- 一屏一个主操作：主按钮要么在流末尾全宽，要么固定在底部 `.dock`。

## 组件类

全部为全局类（定义在 `app.css`，经 `App.vue` 的 `@import` 注入），页面 `<style scoped>` 只处理自身独有布局。

- 文本：`t-title` `t-sub` `t-body` `t-meta` `t-cap` `t-strong` `t-num`（数字等宽放大） `t-money` `t-code`（单号/编码） `t-primary` `t-warn` `t-danger` `t-ok`
- 区块：`section` + `section-head` + `section-title` + `section-note`，首个区块加 `section-first`
- 卡片：`card`（常规） `card-flat`（无阴影轻底） `card-tight`（内边距收紧） `card-hero`（深色，仅身份卡） `card-ok` `divider`
- 列表：`list-item` / `list-item-last`，可点击加 `tap`
- 标签：`chip` + `chip-primary|chip-ok|chip-warn|chip-danger|chip-line`，逆色 `chip-on`，小号 `chip-sm`
- 按钮：`btn` + `btn-primary|btn-ghost|btn-quiet|btn-danger-quiet`，尺寸 `btn-sm` `btn-auto`，并排用 `btn-gap`；纯图标按钮 `icon-btn`（配 `.icon-btn-label` 保证可读文字）
- 表单：`field` `field-label` `input` `input-search` `textarea` `picker` `picker-value` `picker-placeholder`；搜索框外层套 `search-wrap`，图标定位由全局 `.search-wrap .ai` 负责
- 反馈：`alert` + `alert-info|alert-danger` + `alert-icon`；空态 `empty` `empty-icon` `empty-text`
- 工作台：`tiles` `tile` `tile-box` `tile-icon` `tile-icon-accent` `tile-name` `tile-desc`
- 底部栏：`dock` `dock-inner` `dock-row` `dock-label` `dock-amount` `dock-btn`
- 明细：`kv` `kv-key` `kv-val`；勾选 `check-row` `check-box` `check-card` `qty` `addr`；登录品牌块 `brand` `brand-mark` `brand-title` `brand-desc` `foot`

## 图标

纯 CSS 线性图标，组件 `components/AppIcon/AppIcon.vue`（easycom 自动注册，无需 import）：

```vue
<AppIcon name="scan" :size="20" />
```

- 结构：根 `.ai` + 三个绝对定位的 `.s1/.s2/.s3` + 伪元素，全部用 `currentColor` 描边，颜色跟随父级 `color`。
- 现有 18 个：`scan` `doc` `box` `truck` `bag` `search` `user` `check` `alert` `chevron` `clock` `phone` `copy` `pin` `device` `logout` `plus` `store`。新增图标加在 `styles/icons.css`，并在本文件登记。
- 尺寸只用 16 / 18 / 20 / 22px；图标不单独承载语义，旁边必须有文字或 `aria` 等价标签。
- 图标与文字的间距用 `.mr8` / `.ml8`，不写行内 margin。

## 原生组件约束

`button`、`picker`、`checkbox-group`、`input` 是原生组件，样式隔离：scoped 样式穿透不到其内部子节点。因此：

- 按钮外观一律走全局 `btn` 系列类，不在页面写 `button {}`。
- 复选框用 `<label class="check-row">` + `<checkbox-group>`，选中框样式由全局 `.check-box` 控制。
- 不使用 `wxss` 不支持的子代组合器（`>`）、兄弟组合器（`+` `~`）与属性选择器。
- 页面里不写行内 `style`（`placeholder-style` 等原生属性除外）。

## 交互约定

- 每次提交按钮点击后立即置 loading 并禁用，防重复提交；错误就地显示在操作区上方，不用全屏 toast 顶替可读原因。
- 危险操作（作废、强制结束）用 `btn-danger-quiet` + 二次确认，主操作永不用红色。
- 列表空态给「下一步做什么」，不给「暂无数据」四个字了事。
- 长单号、长编码用 `t-code` 折行显示，提供复制按钮；存储值不做截断。
- 状态文案统一为业务口径（待核对 / 已确认 / 已同步 / 已作废），不用「成功」「失败」这类无主语词。

## 设计稿与验证

- `design/preview.html`：8 屏静态设计墙（登录、工作台、查货、库存详情、移动开单、采购收货、收货登记、配送核对），引用 `src/styles` 下同一份 CSS，改样式后重截 `design/preview.png` 即可复核。
  ```bash
  # 单屏 2 倍放大：?only=<从 0 开始的屏序号>&zoom=2
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    --headless=new --no-sandbox --disable-gpu --window-size=430,932 \
    --screenshot=design/preview.png "file://$PWD/design/preview.html"
  ```
- 页面测试（`npm test`，87 项）只断言 `state.*`，不断言文案与模板结构，因此调整排版不会破坏逻辑回归；但改类名后必须跑一次 `npm run build:mp-weixin`，确认产物 `app.wxss` 仍含 `--c-primary` / `ai--*` / `dock`。
