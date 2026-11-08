const test = require('node:test');
const assert = require('node:assert/strict');
const { loadUtil } = require('./helpers/page.cjs');
const { money } = loadUtil('money');

test('后端十进制字符串统一显示两位小数', () => {
  assert.equal(money('25'), '25.00');
  assert.equal(money('38.5'), '38.50');
  assert.equal(money('12.34'), '12.34');
  assert.equal(money('0'), '0.00');
  assert.equal(money('0.00'), '0.00');
  assert.equal(money(25), '25.00');
});

test('缺数据返回空串而不是零元', () => {
  assert.equal(money(''), '');
  assert.equal(money(null), '');
  assert.equal(money(undefined), '');
  assert.equal(money('   '), '');
});

test('无法解析的原样返回，不猜测数值', () => {
  assert.equal(money('按议价'), '按议价');
  assert.equal(money('1,234.5'), '1,234.5');
});


test('原始单位成本与库存小数展示保留有效精度，不按两位金额舍入',()=>{
 const { preciseDecimal }=loadUtil('money');
 assert.equal(typeof preciseDecimal,'function','精确小数展示不能复用两位金额函数');
 assert.equal(preciseDecimal('0.00400000'),'0.004');
 assert.equal(preciseDecimal('1.23456789'),'1.23456789');
 assert.equal(preciseDecimal('0.0001'),'0.0001');
 assert.equal(preciseDecimal('12.0000'),'12');
 assert.equal(preciseDecimal(null),'');
});
