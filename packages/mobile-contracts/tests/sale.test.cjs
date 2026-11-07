const test = require('node:test'), assert = require('node:assert/strict');
const sale = require('../dist/sale.js');

const product = { id: '11', code: 'P001', name: '复合肥', specification: '40kg', manufacturer: '虚构厂',
  mainUnit: '袋', imageUrl: null, suggestedRetailPrice: '12.34', pricingUnit: '袋', baseUnitName: '袋',
  allowFractionalBaseQuantity: false, allowSpecialFund: 0, units: [], status: 1 };
const line = { productId: '11', name: '复合肥', unit: '袋', quantity: '2', quotedUnitPrice: '12.34' };
const actor = { staffId: '8', storeId: '3', epoch: 1 };

const catalogBody = data => ({ code: '200', message: '查询成功', data });
const quoteBody = data => ({ code: '200', message: '报价成功', data });

test('目录与报价的 code 是字符串 200，必须显式解包且拒绝异常包装', () => {
  const products = sale.decodeCatalog(catalogBody({ records: [product], totalRow: 1 }));
  assert.equal(products.length, 1); assert.equal(products[0].id, '11');
  assert.equal(products[0].suggestedRetailPrice, '12.34');
  assert.deepEqual(sale.decodeCatalog(catalogBody([product])).map(p => p.id), ['11']);
  assert.throws(() => sale.decodeCatalog({ code: 200, data: { records: [product] } }), /CATALOG_INVALID/);
  assert.throws(() => sale.decodeCatalog({ code: '500', message: '失败', data: null }), /CATALOG_INVALID/);
  assert.throws(() => sale.decodeCatalog(catalogBody({ records: [{ ...product, id: '0' }] })), /CATALOG_INVALID/);
  const quote = sale.decodeQuote(quoteBody({ quoteId: 'q1', productId: 11, saleUnit: '袋', suggestedUnitPrice: '12.34' }), '11');
  assert.equal(quote.suggestedUnitPrice, '12.34');
  assert.throws(() => sale.decodeQuote(quoteBody({ quoteId: 'q1', productId: 12, saleUnit: '袋', suggestedUnitPrice: '12.34' }), '11'), /QUOTE_INVALID/);
  assert.throws(() => sale.decodeQuote(quoteBody({ quoteId: 'q1', productId: 11, saleUnit: '袋', suggestedUnitPrice: '12.345' }), '11'), /QUOTE_INVALID/);
});

test('金额按整数分换算，数量最多四位小数，不引入浮点累积', () => {
  assert.equal(sale.toCents('12.34'), 1234);
  assert.equal(sale.toCents('0.01'), 1);
  assert.equal(sale.lineCents(line), 2468);
  assert.equal(sale.lineCents({ ...line, quantity: '0.5' }), 617);
  assert.equal(sale.lineCents({ ...line, quantity: '0.0001', quotedUnitPrice: '0.01' }), 0);
  assert.throws(() => sale.toCents('12.345'), /PRICE_INVALID/);
  assert.throws(() => sale.lineCents({ ...line, quantity: '0' }), /QUANTITY_INVALID/);
});

test('本机摘要只保留可核对事实，提交命令不含任何成交金额', () => {
  const summary = sale.toSaleSummary([line, { ...line, quantity: '1', quotedUnitPrice: '0.01' }]);
  assert.equal(summary.totalCents, 2469); assert.equal(summary.netCents, 2469);
  assert.equal(summary.paymentMethod, 1); assert.equal(summary.customerId, null);
  assert.deepEqual(summary.lines.map(l => l.lineCents), [2468, 1]);
  const command = sale.buildSaleCommand('c'.repeat(8), actor, [line]);
  assert.equal(command.storeId, '3'); assert.equal(command.staffId, '8');
  assert.equal(command.paymentType, 1);
  assert.ok(!('amount' in command) && !('totalAmount' in command) && !('receiveAmount' in command));
  assert.equal(command.items[0].quotedUnitPrice, '12.34');
  assert.ok(!('unitPrice' in command.items[0]) && !('totalPrice' in command.items[0]));
  assert.throws(() => sale.toSaleSummary([]), /SUMMARY_INVALID/);
  assert.throws(() => sale.toSaleSummary([{ ...line, quantity: '0' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.buildSaleCommand('k', { ...actor, staffId: '0' }, [line]), /UNAUTHORIZED/);
});

test('订单号守卫只接受可规范化为正长整型的裸值', () => {
  assert.equal(sale.isOrderId(900), true);
  assert.equal(sale.isOrderId('900'), true);
  assert.equal(sale.isOrderId(null), false);
  assert.equal(sale.isOrderId({}), false);
});
