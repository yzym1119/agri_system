const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')

const webRoot = path.resolve(__dirname, '..')

/** 真实执行生产工具脚本，与既有回归测试同一套加载方式。 */
function loadUtils(relativePath) {
  const code = fs.readFileSync(path.join(webRoot, relativePath), 'utf8')
  const ast = ts.createSourceFile(relativePath, code, ts.ScriptTarget.Latest, true)
  const script = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  const scope = {exports: {}}
  vm.createContext(scope)
  vm.runInContext(ts.transpileModule(script, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020}}).outputText, scope)
  return scope.exports
}

const {subtotalAmount, sumAmount} = loadUtils('src/utils/money.ts')

test('小计按整数分计算，半箱和四位小数数量不再出现浮点尾差', () => {
  assert.equal(subtotalAmount(0.5, 12.34), 6.17)
  assert.equal(subtotalAmount(3, 12.34), 37.02)
  assert.equal(subtotalAmount(0.0001, 10000), 1)
  assert.equal(subtotalAmount(1.5, 0.01), 0.02, '0.015 元 HALF_UP 到分应为 0.02')
})

test('金额累加不产生浮点累积误差', () => {
  assert.equal(sumAmount([0.1, 0.2]), 0.3)
  assert.equal(sumAmount([19.99, 0.01]), 20)
  assert.equal(sumAmount([0.07, 0.01, 0.01]), 0.09)
})

test('采购合计按每行小计累加，空行与非数字不中断展示', () => {
  const items = [{qty: 2, price: 3.33}, {qty: 0.5, price: 12.34}, {qty: 1, price: 0.01}]
  assert.equal(sumAmount(items.map(i => subtotalAmount(i.qty, i.price))), 12.84)
  assert.equal(sumAmount([subtotalAmount(undefined, 10), subtotalAmount(1, null), subtotalAmount('abc', 3)]), 0)
  assert.equal(subtotalAmount(1, Number.NaN), 0)
  assert.equal(subtotalAmount(null, null), 0)
})

test('大值不因 Number 精度溢出', () => {
  assert.equal(subtotalAmount(9999.9999, 99999.99), 999999890, '万分位×分应走 BigInt，不能丢精度')
})

test('POS 与采购页面不再各自用浮点重算金额，统一走 money 工具', () => {
  const targets = ['src/views/pos/index.vue', 'src/views/pos/components/ModernCartList.vue']
  for (const relative of targets) {
    const source = fs.readFileSync(path.join(webRoot, relative), 'utf8')
    const script = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
    assert.ok(!/Math\.round\([^)]*\*[^)]*\*\s*100\)\s*\/\s*100/.test(script), `${relative} 不应保留浮点小计`)
    assert.ok(script.includes("from '@/utils/money'"), `${relative} 应引入统一金额工具`)
    assert.ok(script.includes('subtotalAmount('), `${relative} 应使用 subtotalAmount`)
  }
  const purchase = fs.readFileSync(path.join(webRoot, 'src/views/purchase/order/index.vue'), 'utf8')
    .match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  assert.ok(!/\(\s*item\.qty\s*\|\|\s*0\s*\)\s*\*\s*\(\s*item\.price\s*\|\|\s*0\s*\)/.test(purchase), '采购合计不应再逐行浮点相乘')
  assert.ok(purchase.includes('sumAmount('), '采购合计应使用 sumAmount')
})
