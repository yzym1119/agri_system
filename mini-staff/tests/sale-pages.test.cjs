const test = require('node:test'), assert = require('node:assert/strict');
const { setupPage } = require('./helpers/page.cjs');
const { createStaffAuth } = require('../../packages/mobile-contracts/dist/staff-auth.js');
const sale = require('../../packages/mobile-contracts/dist/sale.js');

const product = { id: '11', code: 'P001', name: '复合肥', mainUnit: '袋', suggestedRetailPrice: '12.34',
  baseUnitName: '袋', allowFractionalBaseQuantity: false, status: 1 };
const quote = { quoteId: 'q1', productId: '11', saleUnit: '袋', suggestedUnitPrice: '12.34' };
const customer77 = { id: '77', code: 'C077', name: '张三农资', phone: '13800000077',
  address: '测试村77号', type: 1, vipLevel: null, vipActive: false };
const customer88 = { ...customer77, id: '88', code: 'C088', name: '李四农资', phone: '13800000088' };

async function fixture(permissions = ['pos:order:create']) {
  const user = { id: '8', name: '虚构店员', permissions, extra: { storeId: '3' } };
  const auth = createStaffAuth(async req => ({ statusCode: 200,
    data: req.url.endsWith('/login') ? { tokenName: 'Authorization', tokenValue: 'test', userInfo: user } : user }));
  await auth.login('test', 'pass');
  let catalog = [product], priceQuote = quote, submit = async () => ({ status: 'COMPLETED', orderId: '900' }), restore = null;
  let customers = [customer77];
  const calls = { catalog: 0, search: [], quote: [], submit: [], customerSearch: [], finish: [] };
  const p = setupPage('sale', auth, true, {
    '../../services/sale': {
      readCatalog: async () => { calls.catalog++; return catalog; },
      searchCatalog: async (kw) => {
        calls.search.push(kw);
        return catalog.filter(item => item.name.includes(kw) || item.code.includes(kw));
      },
      readStock: async () => ({ 11: { quantity: '102.00000000', unit: '袋' } }),
      searchCustomers: async (kw) => {
        calls.customerSearch.push(kw);
        return customers.filter(item => !kw || item.name.includes(kw) || item.phone.includes(kw));
      },
      quoteProduct: async (id, unit, epoch, customerId) => { calls.quote.push({ id, unit, customerId: customerId ?? null }); return typeof priceQuote === 'function' ? priceQuote() : priceQuote; },
      submitSale: async (lines, epoch, options) => { calls.submit.push({ lines, epoch, options }); return submit(); },
    },
    '../../services/pos-recovery': { readPOSResult: async () => ({}),
      posRecovery: { restore: () => restore,
        finishCompleted: async (expectedOrderId, confirmed) => { calls.finish.push({ expectedOrderId, confirmed }); } } },
    '@agri/mobile-contracts/sale': sale,
  });
  return { p, auth, calls, setCatalog: v => catalog = v, setQuote: v => priceQuote = v,
    setSubmit: v => submit = v, setRestore: v => restore = v, setUser: v => Object.assign(user, v),
    setCustomers: v => customers = v };
}

test('无收银权限不读取目录，也不开放开单', async () => {
  const f = await fixture(['inventory:view']);
  await f.p.hooks.show();
  assert.equal(f.calls.catalog, 0);
  assert.equal(f.p.state.allowed.value, false);
  assert.match(f.p.state.message.value, /权限/);
});

test('按后端报价加入商品，展示小计但不把金额提交给后端', async () => {
  const f = await fixture(); await f.p.hooks.show();
  assert.equal(f.calls.catalog, 1);
  assert.equal(f.p.state.filtered.value.length, 1);
  f.p.state.keyword.value = '不存在的商品';
  await new Promise(r => setTimeout(r, 350)); // 防抖后走服务端搜索
  assert.equal(f.calls.search.length, 1);
  assert.equal(f.p.state.filtered.value.length, 0);
  f.p.state.keyword.value = 'P001';
  await new Promise(r => setTimeout(r, 350));
  assert.equal(f.calls.search.length, 2);
  assert.equal(f.p.state.filtered.value.length, 1);
  f.p.state.keyword.value = ''; assert.equal(f.p.state.filtered.value.length, 1); // 清空回到完整目录
  f.p.state.choose(product); f.p.state.quantity.value = '2'; await f.p.state.addLine();
  assert.deepEqual(f.calls.quote, [{ id: '11', unit: '袋', customerId: null }]);
  assert.equal(f.p.state.lines.value.length, 1);
  assert.equal(f.p.state.totalCents.value, 2468);
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.p.state.orderId.value, '900');
  assert.equal(f.p.state.lines.value.length, 0);
  assert.ok(!('amount' in f.calls.submit[0].lines[0]));
  assert.deepEqual(f.calls.finish, [{ expectedOrderId: '900', confirmed: true }],
    '成交后必须按回执订单号核对并清掉本机原请求，否则下一单会被误判为待核对');
});

test('本店库存只作参考展示，缺记录时明确说明', async () => {
  const f = await fixture(); await f.p.hooks.show();
  assert.equal(f.p.state.stock.value['11'].unit, '袋');
  assert.match(f.p.state.stockText(product), /库存 102 袋/);
  assert.equal(f.p.state.outOfStock(product), false);
  assert.match(f.p.state.stockText({ ...product, id: '99' }), /无库存记录/);
});

test('报价失败或单位不一致都不加入，不能回退到本地目录价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); f.setQuote(() => { throw Error('QUOTE_FAILED'); }); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 0); assert.match(f.p.state.message.value, /报价失败/);
  f.setQuote({ ...quote, saleUnit: '箱' }); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 0); assert.match(f.p.state.message.value, /单位/);
});

test('添加商品过程中切换客户：普通零售价不得标成按新客户报价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  let resolveQuote;
  f.setQuote(() => new Promise(res => { resolveQuote = res; }));
  f.p.state.choose(product);
  const adding = f.p.state.addLine(); // 未选客户时的普通零售价报价进行中
  await new Promise(r => setImmediate(r));
  f.p.state.pickCustomer(customer77); // 期间选了客户；购物车为空，重报价监听跳过
  await new Promise(r => setImmediate(r));
  resolveQuote({ ...quote, suggestedUnitPrice: '12.34' }); // 普通零售价返回
  await adding;
  assert.equal(f.p.state.lines.value.length, 0, '客户已变更的报价不得加入购物车');
  assert.match(f.p.state.message.value, /客户已变更/);
  f.setQuote(quote); // 恢复正常报价，按所选客户重新添加 → 指纹一致可结算
  f.p.state.choose(product); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 1);
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1, '报价客户指纹一致后可直接结算');
  assert.equal(f.calls.submit[0].options.customerId, '77');
});

test('整件单位拒绝小数数量，计量单位允许四位小数', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); f.p.state.quantity.value = '0.5'; await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 0); assert.equal(f.calls.quote.length, 0);
  f.setCatalog([{ ...product, allowFractionalBaseQuantity: true }]);
  await f.p.hooks.show(); f.p.state.choose({ ...product, allowFractionalBaseQuantity: true });
  f.p.state.quantity.value = '0.5'; await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 1);
});

test('数量面板步进：整件单位按 1 递增，计量单位按 0.5 递增且保留小数', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product);
  f.p.state.stepQuantity(1); assert.equal(f.p.state.quantity.value, '2');
  f.p.state.stepQuantity(-1); f.p.state.stepQuantity(-1); assert.equal(f.p.state.quantity.value, '1');
  f.p.state.choose({ ...product, allowFractionalBaseQuantity: true });
  f.p.state.quantity.value = '1.25'; f.p.state.stepQuantity(1); assert.equal(f.p.state.quantity.value, '1.75');
  f.p.state.closeAdd(); assert.equal(f.p.state.selected.value, null);
});

test('结果未知只提示核对原请求，不换号重收、不清空购物车', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  f.setSubmit(async () => ({ status: 'VERIFYING', requestKey: 'k', orderId: null }));
  await f.p.state.checkout();
  assert.equal(f.p.state.orderId.value, '');
  assert.equal(f.p.state.lines.value.length, 1);
  assert.match(f.p.state.message.value, /待核对/);
  assert.deepEqual(f.p.routes, ['/pages/recovery/index']);
  assert.equal(f.calls.submit.length, 1);
});

test('存在未核对的原请求时禁止新开单', async () => {
  const f = await fixture(); f.setRestore({ requestKey: 'k', state: 'VERIFYING' });
  await f.p.hooks.show();
  assert.equal(f.p.state.blocked.value, true);
  assert.equal(f.calls.catalog, 0);
  await f.p.state.checkout(); assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /上次收银/);
});

test('客户检索面板按关键字查后台档案，选中后关闭面板并成为开单上下文', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.openCustomers();
  await new Promise(r => setImmediate(r));
  assert.equal(f.p.state.customerOpen.value, true);
  assert.equal(f.calls.customerSearch.length, 1);
  assert.equal(f.p.state.customerResults.value.length, 1);
  f.p.state.customerKeyword.value = '139'; f.p.state.scheduleCustomerSearch();
  await new Promise(r => setTimeout(r, 350));
  assert.equal(f.calls.customerSearch.length, 2);
  assert.equal(f.p.state.customerResults.value.length, 0);
  f.p.state.customerKeyword.value = ''; f.p.state.scheduleCustomerSearch();
  await new Promise(r => setTimeout(r, 350));
  f.p.state.pickCustomer(customer77);
  assert.equal(f.p.state.customerOpen.value, false);
  assert.equal(f.p.state.customer.value.name, '张三农资');
  assert.equal(f.p.state.customerId.value, '77');
  f.p.state.pickWalkIn();
  assert.equal(f.p.state.customer.value, null);
  assert.equal(f.p.state.customerId.value, '');
});

test('门店配送必须先选客户，且客户档案缺电话或地址时阻止提交', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  f.p.state.deliveryType.value = 2;
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /先选客户/);

  f.p.state.pickCustomer({ ...customer77, address: null });
  await new Promise(r => setImmediate(r));
  f.p.state.deliveryType.value = 2;
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /缺少地址/);

  f.p.state.pickCustomer({ ...customer77, phone: '' });
  await new Promise(r => setImmediate(r));
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /缺少手机号/);
});

test('选了客户即可配送：命令不带收货人，三项恒为空由后端按档案带出', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.pickCustomer(customer77);
  await new Promise(r => setImmediate(r));
  f.p.state.choose(product); await f.p.state.addLine();
  f.p.state.deliveryType.value = 2; f.p.state.remark.value = '备注';
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  const options = f.calls.submit[0].options;
  assert.equal(options.paymentType, 1);
  assert.equal(options.customerId, '77');
  assert.equal(options.deliveryType, 2);
  assert.equal(options.remark, '备注');
  assert.ok(!('receiverName' in options) && !('receiverPhone' in options) && !('receiverAddress' in options));
  assert.ok(!('amount' in options) && !('totalCents' in options));
});

test('结算方式只有现金：页面不再提供赊销选项', async () => {
  const f = await fixture(); await f.p.hooks.show();
  assert.ok(!('paymentType' in f.p.state), '页面不应再维护可切换的支付方式状态');
  f.p.state.choose(product); await f.p.state.addLine();
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.calls.submit[0].options.paymentType, 1);
});

test('客户变更后购物车全部明细按新客户重取报价指纹', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  f.p.state.choose({ ...product, id: '12', code: 'P002', name: '尿素' }); await f.p.state.addLine();
  const retailCount = f.calls.quote.length;
  f.setQuote({ ...quote, suggestedUnitPrice: '9.90' }); // 模拟 VIP 会员价
  f.p.state.pickCustomer(customer77);
  await new Promise(r => setImmediate(r));
  assert.equal(f.calls.quote.length, retailCount + 2, '每条购物车明细都重取一次报价');
  assert.ok(f.calls.quote.slice(retailCount).every(c => c.customerId === '77'));
  assert.equal(f.p.state.lines.value[0].quotedUnitPrice, '9.90');
  assert.equal(f.p.state.lines.value[1].quotedUnitPrice, '9.90');
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1, '重报价完成后可直接结算，无价格冲突');
  assert.equal(f.calls.submit[0].lines[0].quotedUnitPrice, '9.90');
});

test('旧会话与隐藏后的异步响应不污染当前页面', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.hooks.hide();
  const fresh = await fixture(); await fresh.p.hooks.show();
  assert.equal(fresh.calls.catalog, 1);
  assert.equal(f.p.state.allowed.value, false);
});

test('客户连续变更时慢的旧重报价结果被丢弃，指纹只在成功后提交', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value[0].quotedUnitPrice, '12.34');
  const resolvers = [];
  f.setQuote(() => new Promise(res => resolvers.push(res)));
  f.p.state.pickCustomer(customer77); // 第一次重报价（挂起）
  await new Promise(r => setImmediate(r));
  f.p.state.pickCustomer(customer88); // 期间再次变更，第二次重报价
  await new Promise(r => setImmediate(r));
  assert.equal(f.calls.quote.length, 3); // 首报价 + 两次重报价各一行
  resolvers[0]({ ...quote, suggestedUnitPrice: '5.00' }); // 慢的旧请求后到
  await new Promise(r => setImmediate(r));
  assert.equal(f.p.state.lines.value[0].quotedUnitPrice, '12.34', '旧请求结果必须丢弃');
  resolvers[1]({ ...quote, suggestedUnitPrice: '9.90' }); // 最新请求成功
  await new Promise(r => setImmediate(r));
  assert.equal(f.p.state.lines.value[0].quotedUnitPrice, '9.90', '按最新客户上下文提交报价');
  await f.p.state.checkout(); // 指纹一致，可直接结算
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.calls.submit[0].options.customerId, '88');
});

test('重报价失败后指纹不得伪装成新客户，结算会触发重新报价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  f.setQuote(() => { throw Error('QUOTE_FAILED'); });
  f.p.state.pickCustomer(customer77);
  await new Promise(r => setImmediate(r));
  assert.match(f.p.state.message.value, /重新报价失败/);
  await f.p.state.checkout(); // 指纹仍是旧客户 → 结算被转为重新触发重报价而不是提交
  assert.equal(f.calls.submit.length, 0, '重报价失败时不得直接结算');
  assert.equal(f.calls.quote.length, 3, 'checkout 检测到指纹不一致会再次触发重报价');
});
