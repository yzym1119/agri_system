const test = require('node:test'), assert = require('node:assert/strict');
const { setupPage } = require('./helpers/page.cjs');
const { createStaffAuth } = require('../../packages/mobile-contracts/dist/staff-auth.js');
const sale = require('../../packages/mobile-contracts/dist/sale.js');

const product = { id: '11', code: 'P001', name: '复合肥', mainUnit: '袋', suggestedRetailPrice: '12.34',
  baseUnitName: '袋', pricingUnit:'袋', availableBaseQuantity:'102',availabilityStoreId:'3', allowFractionalBaseQuantity: false, status: 1 };
const quote = { quoteId: 'q1', productId: '11', saleUnit: '袋', suggestedUnitPrice: '12.34' };
const customer77 = { id: '77', code: 'C077', name: '张三农资', phone: '13800000077',
  address: '测试村77号', type: 1, vipLevel: null, vipActive: false };
const customer88 = { ...customer77, id: '88', code: 'C088', name: '李四农资', phone: '13800000088' };

const pkg = { id: '501', code: 'PKG001', name: '春季套餐', suggestedRetailPrice: '200', saleablePackages: '3',
  materials: [{ productId: '11', name: '复合肥', quantity: '2', unit: '袋' }] };
const pkgQuote = { quoteId: 'PQ-501-1', packageId: '501', quantity: '2', suggestedUnitPrice: '200.00', totalAmount: '400.00' };

async function fixture(permissions = ['pos:order:create']) {
  const user = { id: '8', name: '虚构店员', permissions, extra: { storeId: '3' } };
  const auth = createStaffAuth(async req => ({ statusCode: 200,
    data: req.url.endsWith('/login') ? { tokenName: 'Authorization', tokenValue: 'test', userInfo: user } : user }));
  await auth.login('test', 'pass');
  let catalog = [product], priceQuote = quote, submit = async () => ({ status: 'COMPLETED', orderId: '900' }), restore = null;
  let customers = [customer77], searchOverride = null, stockPreview = {storeId:"3",sufficient:true,issues:[]};
  let packageList = [pkg], packageQuote = pkgQuote, packageFailure = false;
  const calls = { catalog: 0, search: [], quote: [], submit: [], customerSearch: [], finish: [],
    packages: 0, packageQuote: [], stockChecks: [] };
  const p = setupPage('sale', auth, true, {
    '../../services/sale': {
      readCatalog: async () => { calls.catalog++; return catalog; },
      searchCatalog: async (kw) => {
        calls.search.push(kw);
        if (searchOverride) return searchOverride(kw);
        return catalog.filter(item => item.name.includes(kw) || item.code.includes(kw));
      },
      searchCustomers: async (kw) => {
        calls.customerSearch.push(kw);
        return customers.filter(item => !kw || item.name.includes(kw) || item.phone.includes(kw));
      },
      quoteProduct: async (id, unit, epoch, customerId) => { calls.quote.push({ id, unit, customerId: customerId ?? null }); return typeof priceQuote === 'function' ? priceQuote() : priceQuote; },
      readPackages: async () => { calls.packages++; if (packageFailure) throw new Error('NETWORK'); return packageList; },
      quotePackage: async (id, packages, epoch, customerId) => {
        calls.packageQuote.push({ id, packages, customerId: customerId ?? null });
        const built = typeof packageQuote === 'function' ? packageQuote(packages) : packageQuote;
        return built instanceof Promise ? built : { ...built, quantity: packages,
          totalAmount: (Number(built.suggestedUnitPrice) * Number(packages)).toFixed(2) };
      },
      checkSaleStock: async (lines,epoch,options) => { calls.stockChecks.push({lines,options}); return typeof stockPreview === "function" ? stockPreview() : stockPreview; },
      submitSale: async (lines, epoch, options) => { calls.submit.push({ lines, epoch, options }); return submit(); },
    },
    '../../services/pos-recovery': { readPOSResult: async () => ({}),
      posRecovery: { restore: () => restore,
        finishCompleted: async (expectedOrderId, confirmed) => { calls.finish.push({ expectedOrderId, confirmed }); } } },
    '@agri/mobile-contracts/sale': sale,
  });
  return { p, auth, calls, setCatalog: v => catalog = v, setQuote: v => priceQuote = v,
    setSubmit: v => submit = v, setRestore: v => restore = v, setUser: v => Object.assign(user, v),
    setStockPreview: v => stockPreview = v, setCustomers: v => customers = v, setSearch: v => searchOverride = v,
    setPackages: v => packageList = v, setPackageQuote: v => packageQuote = v,
    failPackages: () => { packageFailure = true; } };
}

function selectCustomer(f,item){f.p.state.customerOpen.value=true;f.p.state.customerResults.value=[item];f.p.state.pickCustomer(f.p.state.customerResults.value[0]);}

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

test('本店可售量来自当前目录并保留原始单位精度', async () => {
  const f = await fixture(); await f.p.hooks.show();
  assert.equal(f.p.state.products.value[0].availableBaseQuantity, '102');
  assert.match(f.p.state.stockText(product), /本店可售 102 袋/);
  assert.equal(f.p.state.outOfStock(product), false);
  assert.match(f.p.state.stockText({ ...product, id: '99', availableBaseQuantity:null }), /本店可售量待核对/);
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
  selectCustomer(f,customer77); // 期间选了客户；购物车为空，重报价监听跳过
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
  selectCustomer(f,customer77);
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

  selectCustomer(f,{ ...customer77, address: null });
  await new Promise(r => setImmediate(r));
  f.p.state.deliveryType.value = 2;
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /缺少地址/);

  selectCustomer(f,{ ...customer77, phone: '' });
  await new Promise(r => setImmediate(r));
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 0);
  assert.match(f.p.state.message.value, /缺少手机号/);
});

test('选了客户即可配送：命令不带收货人，三项恒为空由后端按档案带出', async () => {
  const f = await fixture(); await f.p.hooks.show();
  selectCustomer(f,customer77);
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
  selectCustomer(f,customer77);
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
  selectCustomer(f,customer77); // 第一次重报价（挂起）
  await new Promise(r => setImmediate(r));
  selectCustomer(f,customer88); // 期间再次变更，第二次重报价
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
  selectCustomer(f,customer77);
  await new Promise(r => setImmediate(r));
  assert.match(f.p.state.message.value, /重新报价失败/);
  await f.p.state.checkout(); // 指纹仍是旧客户 → 结算被转为重新触发重报价而不是提交
  assert.equal(f.calls.submit.length, 0, '重报价失败时不得直接结算');
  assert.equal(f.calls.quote.length, 3, 'checkout 检测到指纹不一致会再次触发重报价');
});

test('审查复现：客户重新报价期间移除商品后不得被慢报价重新加入', async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.choose(product);await f.p.state.addLine();
 let finish;f.setQuote(()=>new Promise(resolve=>{finish=resolve;}));
 selectCustomer(f,customer77);await new Promise(r=>setImmediate(r));
 assert.equal(f.p.state.requoting.value,true);f.p.state.removeLine(0);
 assert.equal(f.p.state.lines.value.length,1,'报价未完成时保留清单并阻止移除');
 finish({...quote,suggestedUnitPrice:'9.90'});await new Promise(r=>setImmediate(r));
 assert.equal(f.p.state.lines.value.length,1,'报价只更新原清单');
});
test('审查复现：旧行重报价失败后新增商品不能伪装整单报价成功',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.choose(product);await f.p.state.addLine();
 f.setQuote(()=>{throw Error('offline');});selectCustomer(f,customer77);await new Promise(r=>setImmediate(r));
 assert.match(f.p.state.message.value,/重新报价失败/);
 f.setQuote({...quote,suggestedUnitPrice:'9.90'});f.p.state.choose(product);await f.p.state.addLine();
 assert.equal(f.p.state.lines.value.length,1,'先修复旧行报价，本次不继续加入新商品');
 assert.equal(f.p.state.lines.value[0].quotedUnitPrice,'9.90');assert.equal(f.calls.submit.length,0);
 await f.p.state.checkout();assert.equal(f.calls.submit.length,1);assert.equal(f.calls.submit[0].lines[0].quotedUnitPrice,'9.90');
});

test('客户关键词改变立即作废旧列表，关闭面板不再发送防抖查询',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.openCustomers();await new Promise(r=>setImmediate(r));
 const old=f.p.state.customerResults.value[0];assert.ok(old);
 f.p.state.customerKeyword.value='另一客户';f.p.state.scheduleCustomerSearch();
 assert.equal(f.p.state.customerResults.value.length,0,'防抖等待期间不能保留旧客户可选行');
 f.p.state.pickCustomer(old);assert.equal(f.p.state.customer.value,null);
 f.p.state.closeCustomers();const before=f.calls.customerSearch.length;await new Promise(r=>setTimeout(r,350));
 assert.equal(f.calls.customerSearch.length,before,'关闭后取消未发送的查询');
});

test('客户查询失败保留失败状态，不显示成没有客户档案',async()=>{
 const f=await fixture();await f.p.hooks.show();f.setCustomers(null);
 f.p.state.openCustomers();await new Promise(r=>setImmediate(r));
 assert.equal(f.p.state.customerResults.value.length,0);assert.match(f.p.state.customerError.value,/查询未完成/);
 assert.equal(f.p.state.customerLoading.value,false);assert.equal(f.calls.submit.length,0);
});

test('商品关键词变化在防抖前立即清理旧候选，旧词响应不能变成新词列表',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.keyword.value='P001';await new Promise(r=>setTimeout(r,350));
 assert.equal(f.p.state.searchResults.value.length,1);
 f.p.state.keyword.value='没有这个商品';assert.equal(f.p.state.searchResults.value.length,0);
 await f.p.state.runSearch('P001');assert.equal(f.p.state.searchResults.value.length,0);
 f.p.hooks.hide();
});

test('只读目录的本店可售量，零值和未知拒绝加购而不发报价',async()=>{
 const f=await fixture();await f.p.hooks.show();
 const none={...product,availableBaseQuantity:'0',availabilityStoreId:'3'};
 f.p.state.choose(none);assert.equal(f.p.state.selected.value,null);assert.match(f.p.state.message.value,/无可售/);
 const unknown={...product,availableBaseQuantity:null,availabilityStoreId:'3'};
 f.p.state.choose(unknown);assert.equal(f.p.state.selected.value,null);assert.match(f.p.state.message.value,/待核对/);
 const other={...product,availableBaseQuantity:'100',availabilityStoreId:'4'};
 f.p.state.choose(other);assert.equal(f.p.state.selected.value,null);assert.equal(f.calls.quote.length,0);
 const fractional={...product,baseUnitName:'公斤',availableBaseQuantity:'0.0001',availabilityStoreId:'3'};
 assert.match(f.p.state.stockText(fractional),/本店可售 0.0001 公斤/);
 f.p.state.choose(fractional);assert.ok(f.p.state.selected.value);
});


test('刷新保留关键词重新查询，刷新前的在途搜索不能回填旧可售量',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.keyword.value='P001';
 await new Promise(r=>setTimeout(r,350));assert.equal(f.p.state.filtered.value.length,1);
 let finishOld;f.setSearch(()=>new Promise(resolve=>finishOld=resolve));
 const old=f.p.state.runSearch('P001');
 f.setSearch(async()=>[{...product,availableBaseQuantity:'7'}]);
 await f.p.state.reload();
 assert.equal(f.p.state.keyword.value,'P001');assert.equal(f.p.state.filtered.value[0]?.availableBaseQuantity,'7');
 finishOld([{...product,availableBaseQuantity:'102'}]);await old;
 assert.equal(f.p.state.filtered.value[0]?.availableBaseQuantity,'7');f.p.hooks.hide();
});

test('套餐按整数套加入清单，命令只带套餐ID/套数/报价指纹', async () => {
  const f = await fixture(); await f.p.hooks.show();
  assert.equal(f.calls.packages, 1, '进页面同时读套餐目录');
  f.p.state.mode.value = 'PACKAGE';
  assert.equal(f.p.state.filteredPackages.value.length, 1);
  f.p.state.choosePackage(pkg);
  assert.equal(f.p.state.selectedPackage.value.id, '501');
  f.p.state.packageCount.value = '2';
  await f.p.state.addPackageLine();
  assert.deepEqual(f.calls.packageQuote, [{ id: '501', packages: '2', customerId: null }]);
  const line = f.p.state.lines.value[0];
  assert.equal(line.kind, 'PACKAGE');
  assert.equal(line.packageId, '501'); assert.equal(line.packageQuoteId, 'PQ-501-1');
  assert.equal(line.quantity, '2'); assert.equal(line.quotedUnitPrice, '200.00');
  assert.equal(f.p.state.totalCents.value, 40000);
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.calls.submit[0].lines[0].kind, 'PACKAGE');
  assert.equal(f.calls.submit[0].lines[0].materials.length, 1, '清单保留物料供核对，但提交时由契约只发套餐行');
});

test('套餐与单品可混合开单，换客户后两者一起重新报价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); await f.p.state.addLine();
  f.p.state.choosePackage(pkg); f.p.state.packageCount.value = '1'; await f.p.state.addPackageLine();
  assert.equal(f.p.state.lines.value.length, 2);
  assert.equal(f.p.state.totalCents.value, 1234 + 20000);
  f.setQuote({ ...quote, suggestedUnitPrice: '9.90' });      // 会员价
  f.setPackageQuote({ ...pkgQuote, suggestedUnitPrice: '180.00' });
  selectCustomer(f, customer77);
  await new Promise(r => setImmediate(r));
  const productLine = f.p.state.lines.value[0], bundleLine = f.p.state.lines.value[1];
  assert.equal(productLine.quotedUnitPrice, '9.90');
  assert.equal(bundleLine.quotedUnitPrice, '180.00', '套餐按新客户重报价，旧指纹作废');
  assert.deepEqual(f.calls.packageQuote.at(-1), { id: '501', packages: '1', customerId: '77' });
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.calls.submit[0].lines.length, 2);
});

test('可售套数为 0 或未知都不允许加入，半套直接拒绝', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choosePackage({ ...pkg, saleablePackages: '0' });
  assert.equal(f.p.state.selectedPackage.value, null);
  assert.match(f.p.state.message.value, /可售套数为 0/);
  f.p.state.choosePackage({ ...pkg, saleablePackages: null });
  assert.equal(f.p.state.selectedPackage.value, null);
  assert.match(f.p.state.message.value, /待核对/);
  f.p.state.choosePackage(pkg); f.p.state.packageCount.value = '1.5';
  await f.p.state.addPackageLine();
  assert.match(f.p.state.message.value, /整数/);
  assert.equal(f.p.state.lines.value.length, 0);
  assert.equal(f.calls.packageQuote.length, 0, '套数非法时不发报价');
});

test('套餐报价期间客户变更则放弃加入，不把零售价记成会员价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  let release; f.setPackageQuote(() => new Promise(res => release = res));
  f.p.state.choosePackage(pkg); f.p.state.packageCount.value = '1';
  const pending = f.p.state.addPackageLine();
  selectCustomer(f, customer77);
  release({ ...pkgQuote, quantity: '1', suggestedUnitPrice: '180.00', totalAmount: '180.00' });
  await pending;
  assert.equal(f.p.state.lines.value.length, 0);
  assert.match(f.p.state.message.value, /客户已变更/);
});

test('套餐目录读取失败不影响商品开单', async () => {
  const f = await fixture(); f.failPackages(); await f.p.hooks.show();
  assert.match(f.p.state.packageNotice.value, /套餐目录读取失败/);
  f.p.state.choose(product); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 1, '商品销售不被套餐故障拖死');
  assert.equal(f.p.state.message.value, '', '商品加购成功后不再显示套餐错误');
});

test('切换商品/套餐会清掉上一类的搜索词与提示，套餐筛选不打商品搜索接口', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.keyword.value = 'P001';
  await new Promise(r => setTimeout(r, 350));
  assert.equal(f.calls.search.length, 1);
  f.p.state.switchMode('PACKAGE');
  assert.equal(f.p.state.keyword.value, '');
  assert.equal(f.p.state.message.value, '');
  f.p.state.keyword.value = '春季';
  await new Promise(r => setTimeout(r, 350));
  assert.equal(f.calls.search.length, 1, '套餐只做本地过滤');
  assert.deepEqual(f.p.state.filteredPackages.value.map(p => p.id), ['501']);
  f.p.state.keyword.value = '不存在';
  assert.equal(f.p.state.filteredPackages.value.length, 0);
});


test('整单物料不足先显示核对原因，不产生收银提交或恢复记录',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.choose(product);await f.p.state.addLine();
 f.setStockPreview({storeId:'3',sufficient:false,issues:['肥料：整单需要15，本店可售12袋']});
 await f.p.state.checkout();assert.equal(f.calls.stockChecks.length,1);assert.equal(f.calls.submit.length,0);
 assert.match(f.p.state.message.value,/15/);assert.equal(f.calls.finish.length,0);
 f.setStockPreview(()=>Promise.reject(Error('offline')));await f.p.state.checkout();assert.equal(f.calls.submit.length,0);assert.match(f.p.state.message.value,/库存核对|物料核对/);
});
test('库存核对期间客户或清单变化，不得提交另一份内容',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.choose(product);await f.p.state.addLine();
 let resolve;f.setStockPreview(()=>new Promise(done=>resolve=done));const pending=f.p.state.checkout();
 f.p.state.lines.value=[{...f.p.state.lines.value[0],quantity:'2'}];resolve({storeId:'3',sufficient:true,issues:[]});await pending;
 assert.equal(f.calls.submit.length,0);assert.match(f.p.state.message.value,/变化|变更/);
});
