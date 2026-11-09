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

test('员工端只开放现金：赊销与其它支付方式都在契约层被拒绝', () => {
  assert.deepEqual(sale.resolveSaleOptions().paymentType, 1);
  assert.throws(() => sale.resolveSaleOptions({ paymentType: 6 }), /PAYMENT_INVALID/);
  assert.throws(() => sale.resolveSaleOptions({ paymentType: 4 }), /PAYMENT_INVALID/);
  assert.throws(() => sale.resolveSaleOptions({ paymentType: 2 }), /PAYMENT_INVALID/);
  assert.throws(() => sale.resolveSaleOptions({ customerId: '0' }), /CUSTOMER_INVALID/);
  assert.throws(() => sale.resolveSaleOptions({ deliveryType: 3 }), /DELIVERY_INVALID/);
  assert.throws(() => sale.resolveSaleOptions({ remark: 'x'.repeat(201) }), /REMARK_INVALID/);
  assert.equal(sale.resolveSaleOptions({ customerId: '77' }).customerId, '77');
});

test('门店配送只认客户：不传收货人，命令里三项收货信息恒为空由后端按档案带出', () => {
  assert.throws(() => sale.resolveSaleOptions({ deliveryType: 2 }), /CUSTOMER_REQUIRED/);
  const options = sale.resolveSaleOptions({ customerId: '77', deliveryType: 2, remark: '备注' });
  const summary = sale.toSaleSummary([line], options);
  assert.equal(summary.paymentMethod, 1); assert.equal(summary.customerId, '77');
  const command = sale.buildSaleCommand('k', actor, [line], options);
  assert.equal(command.paymentType, 1); assert.equal(command.customerId, '77');
  assert.equal(command.deliveryType, 2);
  assert.equal(command.receiverName, null); assert.equal(command.receiverPhone, null);
  assert.equal(command.receiverAddress, null);
  assert.equal(command.remark, '备注');
  assert.ok(!('amount' in command) && !('payments' in command)
    && !('discountAmount' in command) && !('receiveAmount' in command));
  assert.throws(() => sale.buildSaleCommand('k', actor, [line], { deliveryType: 2 }), /CUSTOMER_REQUIRED/);
});

test('客户检索解码只接受开单所需字段，缺姓名或手机号一律拒绝', () => {
  const row = { id: 9101, code: 'TEST-C001', name: '联调客户', phone: '13900000001',
    address: '联调测试地址1号', type: 2, vipLevel: 1, vipActive: true };
  const rows = sale.decodeCustomers(catalogBody({ records: [row], totalRow: 1 }));
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, '9101'); assert.equal(rows[0].address, '联调测试地址1号');
  assert.equal(rows[0].vipActive, true);
  const noAddress = sale.decodeCustomers(catalogBody({ records: [{ ...row, address: null }] }));
  assert.equal(noAddress[0].address, null);
  assert.throws(() => sale.decodeCustomers(catalogBody({ records: [{ ...row, name: '' }] })), /CUSTOMER_INVALID/);
  assert.throws(() => sale.decodeCustomers(catalogBody({ records: [{ ...row, phone: null }] })), /CUSTOMER_INVALID/);
  assert.throws(() => sale.decodeCustomers({ code: 200, data: { records: [row] } }), /CUSTOMER_INVALID/);
});

test('订单号守卫只接受可规范化为正长整型的裸值', () => {
  assert.equal(sale.isOrderId(900), true);
  assert.equal(sale.isOrderId('900'), true);
  assert.equal(sale.isOrderId(null), false);
  assert.equal(sale.isOrderId({}), false);
});

test('目录可售量保留原十进制和门店，缺失或非法证据明确未知',()=>{
 const decode=row=>sale.decodeCatalog(catalogBody({records:[row]}))[0];
 const current={...product,availabilityStoreId:'3',availableBaseQuantity:'999999999999.00000001'};
 assert.equal(decode(current).availableBaseQuantity,'999999999999.00000001');
 assert.equal(decode(current).availabilityStoreId,'3');assert.equal(decode(current).pricingUnit,'袋');
 assert.equal(decode({...product}).availableBaseQuantity,null);
 assert.equal(decode({...current,availableBaseQuantity:0.0001}).availableBaseQuantity,null,'不通过浮点猜精度');
 assert.equal(decode({...current,availableBaseQuantity:'-1'}).availableBaseQuantity,null);
 assert.equal(decode({...current,baseUnitName:null}).availableBaseQuantity,null);
 assert.equal(decode({...current,availabilityStoreId:'0'}).availableBaseQuantity,null);
});

const pkgRow = { id: 501, code: 'PKG20261001001', name: '春季套餐', suggestedRetailPrice: 200,
  vipPrice: 180, minPrice: 150, status: 1, stock: 3.0000,
  items: [{ productId: 9101, productName: '复合肥', quantity: 2.0000, unit: '袋' }] };
const pkgQuote = { quoteId: 'PQ-501-1', packageId: 501, packageName: '春季套餐', quantity: 2,
  suggestedUnitPrice: 200.00, referenceFloor: 150.00, belowReference: false, dealUnitPrice: 200.00,
  totalAmount: 400.00, specialFundEligibleAmount: 0, items: [] };
const pkgLine = { kind: 'PACKAGE', packageId: '501', packageQuoteId: 'PQ-501-1', name: '春季套餐',
  quantity: '2', quotedUnitPrice: '200.00', materials: [{ productId: '9101', name: '复合肥', quantity: '2.0000', unit: '袋' }] };

test('套餐目录只带核对所需字段，可售套数缺失保持未知而不是 0', () => {
  const rows = sale.decodePackages(catalogBody({ records: [pkgRow], totalRow: 1 }));
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, '501');
  assert.equal(rows[0].suggestedRetailPrice, '200');
  assert.equal(rows[0].saleablePackages, '3');
  assert.deepEqual(rows[0].materials.map(m => m.productId + '/' + m.quantity + m.unit), ['9101/2袋']);
  const unknown = sale.decodePackages(catalogBody({ records: [{ ...pkgRow, stock: null }] }))[0];
  assert.equal(unknown.saleablePackages, null, '后端没给可信值就是未知，不能当 0');
  assert.throws(() => sale.decodePackages(catalogBody({ records: [{ ...pkgRow, items: [] }] })), /PACKAGE_INVALID/);
  assert.throws(() => sale.decodePackages(catalogBody({ records: [{ ...pkgRow, id: '0' }] })), /PACKAGE_INVALID/);
  assert.throws(() => sale.decodePackages(catalogBody({ records: [{ ...pkgRow, suggestedRetailPrice: null }] })), /PACKAGE_INVALID/);
  assert.throws(() => sale.decodePackages({ code: 200, data: { records: [pkgRow] } }), /PACKAGE_INVALID/);
});

test('套餐报价必须以 quoteId 作指纹，套数或套餐对不上直接拒绝', () => {
  const quote = sale.decodePackageQuote(quoteBody(pkgQuote), '501', '2');
  assert.equal(quote.quoteId, 'PQ-501-1');
  assert.equal(quote.suggestedUnitPrice, '200');
  assert.equal(quote.totalAmount, '400');
  assert.throws(() => sale.decodePackageQuote(quoteBody({ ...pkgQuote, quoteId: '' }), '501', '2'), /PACKAGE_QUOTE_INVALID/);
  assert.throws(() => sale.decodePackageQuote(quoteBody({ ...pkgQuote, packageId: 502 }), '501', '2'), /PACKAGE_QUOTE_INVALID/);
  assert.throws(() => sale.decodePackageQuote(quoteBody(pkgQuote), '501', '3'), /PACKAGE_QUOTE_INVALID/);
  assert.throws(() => sale.decodePackageQuote({ code: '200', data: null }, '501', '2'), /PACKAGE_QUOTE_INVALID/);
});

test('套餐整套提交：命令只带套餐ID/套数/报价指纹，不拆物料也不分摊金额', () => {
  const summary = sale.toSaleSummary([line, pkgLine]);
  assert.equal(summary.totalCents, 2468 + 40000);
  assert.deepEqual(summary.lines.map(l => [l.productId, l.packageId, l.unit]),
    [['11', null, '袋'], [null, '501', '套']]);
  const command = sale.buildSaleCommand('c'.repeat(8), actor, [line, pkgLine]);
  const bundle = command.items[1];
  assert.equal(bundle.itemType, 2); assert.equal(bundle.packageId, '501');
  assert.equal(bundle.packageQuoteId, 'PQ-501-1'); assert.equal(bundle.quantity, '2');
  assert.equal(bundle.quotedUnitPrice, '200.00');
  assert.equal(bundle.productId, undefined, '套餐行不能带商品ID，否则后端按单品处理');
  assert.equal(bundle.unit, '套', '与后端套餐行同口径，物料单位仍由后端展开');
  assert.equal(bundle.unitPrice, undefined);
  assert.equal(command.items[0].itemType, 1);
});

test('半套套餐与缺报价的套餐行在契约层就被拒绝', () => {
  assert.throws(() => sale.toSaleSummary([{ ...pkgLine, quantity: '1.5' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.toSaleSummary([{ ...pkgLine, quantity: '0' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.toSaleSummary([{ ...pkgLine, packageQuoteId: '' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.toSaleSummary([{ ...pkgLine, packageId: '0' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.buildSaleCommand('k', actor, [{ ...pkgLine, quantity: '2.5' }]), /SUMMARY_INVALID/);
  assert.throws(() => sale.toSaleSummary([pkgLine, { ...pkgLine, quantity: '100000' }]), /SUMMARY_INVALID/);
});

test('整单库存预检只提交销售意图，读取合同区分不足与失败',()=>{
 const s=require('../dist/sale.js');
 assert.equal(s.isStockPreview({storeId:'3',sufficient:true,issues:[]}),true);
 assert.equal(s.isStockPreview({storeId:'3',sufficient:true,issues:['不足']}),false);
 assert.equal(s.isStockPreview({storeId:'3',sufficient:false,issues:[]}),false);
 assert.equal(s.isStockPreview({storeId:3,sufficient:true,issues:[]}),false);
 const c=s.buildStockCheckCommand({staffId:'8',storeId:'3',epoch:1},[{productId:'11',name:'肥料',unit:'袋',quantity:'2',quotedUnitPrice:'12.34'}],null);
 assert.equal(c.storeId,'3');assert.equal(c.items[0].quantity,'2');assert.equal('requestKey' in c,false);assert.equal('paymentType' in c,false);
});
