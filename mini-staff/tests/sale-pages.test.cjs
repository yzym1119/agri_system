const test = require('node:test'), assert = require('node:assert/strict');
const { setupPage } = require('./helpers/page.cjs');
const { createStaffAuth } = require('../../packages/mobile-contracts/dist/staff-auth.js');
const sale = require('../../packages/mobile-contracts/dist/sale.js');

const product = { id: '11', code: 'P001', name: '复合肥', mainUnit: '袋', suggestedRetailPrice: '12.34',
  baseUnitName: '袋', allowFractionalBaseQuantity: false, status: 1 };
const quote = { quoteId: 'q1', productId: '11', saleUnit: '袋', suggestedUnitPrice: '12.34' };

async function fixture(permissions = ['pos:order:create']) {
  const user = { id: '8', name: '虚构店员', permissions, extra: { storeId: '3' } };
  const auth = createStaffAuth(async req => ({ statusCode: 200,
    data: req.url.endsWith('/login') ? { tokenName: 'Authorization', tokenValue: 'test', userInfo: user } : user }));
  await auth.login('test', 'pass');
  let catalog = [product], priceQuote = quote, submit = async () => ({ status: 'COMPLETED', orderId: '900' }), restore = null;
  const calls = { catalog: 0, quote: [], submit: [] };
  const p = setupPage('sale', auth, true, {
    '../../services/sale': {
      readCatalog: async () => { calls.catalog++; return catalog; },
      quoteProduct: async (id, unit) => { calls.quote.push({ id, unit }); return typeof priceQuote === 'function' ? priceQuote() : priceQuote; },
      submitSale: async (lines, epoch) => { calls.submit.push({ lines, epoch }); return submit(); },
    },
    '../../services/pos-recovery': { posRecovery: { restore: () => restore } },
    '@agri/mobile-contracts/sale': sale,
  });
  return { p, auth, calls, setCatalog: v => catalog = v, setQuote: v => priceQuote = v,
    setSubmit: v => submit = v, setRestore: v => restore = v, setUser: v => Object.assign(user, v) };
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
  f.p.state.keyword.value = '不存在的商品'; assert.equal(f.p.state.filtered.value.length, 0);
  f.p.state.keyword.value = 'P001'; assert.equal(f.p.state.filtered.value.length, 1);
  f.p.state.choose(product); f.p.state.quantity.value = '2'; await f.p.state.addLine();
  assert.deepEqual(f.calls.quote, [{ id: '11', unit: '袋' }]);
  assert.equal(f.p.state.lines.value.length, 1);
  assert.equal(f.p.state.totalCents.value, 2468);
  await f.p.state.checkout();
  assert.equal(f.calls.submit.length, 1);
  assert.equal(f.p.state.orderId.value, '900');
  assert.equal(f.p.state.lines.value.length, 0);
  assert.ok(!('amount' in f.calls.submit[0].lines[0]));
});

test('报价失败或单位不一致都不加入，不能回退到本地目录价', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.state.choose(product); f.setQuote(() => { throw Error('QUOTE_FAILED'); }); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 0); assert.match(f.p.state.message.value, /报价失败/);
  f.setQuote({ ...quote, saleUnit: '箱' }); await f.p.state.addLine();
  assert.equal(f.p.state.lines.value.length, 0); assert.match(f.p.state.message.value, /单位/);
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

test('旧会话与隐藏后的异步响应不污染当前页面', async () => {
  const f = await fixture(); await f.p.hooks.show();
  f.p.hooks.hide();
  const fresh = await fixture(); await fresh.p.hooks.show();
  assert.equal(fresh.calls.catalog, 1);
  assert.equal(f.p.state.allowed.value, false);
});
