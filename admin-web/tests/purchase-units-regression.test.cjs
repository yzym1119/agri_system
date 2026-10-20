const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript'), vue = require('vue')
function load(overrides = {}) {
  const filename = path.join(__dirname, '../src/views/purchase/order/index.vue'), source = fs.readFileSync(filename, 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true)
  const names = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations].filter(d => ts.isIdentifier(d.name)).map(d => d.name.text))
  const imports = Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s => [s.importClause?.name?.text, ...(s.importClause?.namedBindings?.elements || []).map(e => e.name.text)].filter(Boolean).map(n => [n, {}])))
  const sent = [], messages = [], context = { exports: {}, ...imports, ...vue, watch: vue.watch, useRoute: () => ({query: {}}), useRouter: () => ({}), usePermissionStore: () => ({permissions: []}), hasPermission: () => true, getAllStores: async () => [], getAllSuppliers: async () => [], getPurchaseList: async () => ({records: []}), getProductDetail: async id => product(id), createPurchase: async data => sent.push(data), ElMessage: {error: m => messages.push(m), warning: m => messages.push(m), success: () => {}}, ElMessageBox: {confirm: async () => {}}, ...overrides }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n') + `\nglobalThis.bindings={${names.join(',')}}`, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText, context)
  const p = {...context.bindings, sent, messages}; p.handleOpenCreate(); p.createFormRef.value = {validate: async () => true}; return p
}
function product(id = 1) { return {id, name: '农资B', mainUnit: '瓶', auxUnit: '旧包', mainToAuxRatio: 100, avgCostPrice: 8, units: [{id: 11, unitName: '瓶', baseQuantityPerUnit: 1, isBaseUnit: 1, allowPurchase: 1, status: 1}, {id: 12, unitName: '箱', baseQuantityPerUnit: 12, isBaseUnit: 0, allowPurchase: 1, status: 1}, {id: 13, unitName: '停用包', baseQuantityPerUnit: 2, isBaseUnit: 0, allowPurchase: 1, status: 0}, {id: 14, unitName: '销售包', baseQuantityPerUnit: 3, isBaseUnit: 0, allowPurchase: 0, status: 1}]} }
function deferred() { let resolve; return {promise: new Promise(r => resolve = r), resolve} }
test('采购显示持久化可采购箱及直接基准系数，实际进价留空且切单位不猜成本', async () => {
  const p = load(); await p.handleProductSelect([product()]); const row = p.createForm.items[0]
  assert.deepEqual(Array.from(row.unitOptions || [], u => u.unitName), ['瓶', '箱']); assert.equal(row.unitOptions[1].baseQuantityPerUnit, 12)
  assert.equal(row.price, undefined); row.price = 8; row.unit = '箱'; p.handleUnitChange(row); assert.equal(row.price, undefined)
})
test('没有持久化采购单位拒绝添加，不回退旧主辅单位', async () => {
  const p = load({getProductDetail: async () => ({...product(), units: []})}); await p.handleProductSelect([product()]); assert.equal(p.createForm.items.length, 0)
})
test('关闭并重开采购弹窗后，旧选品响应不能污染新单；并发重复选品只加一行', async () => {
  const wait = deferred(), p = load({getProductDetail: () => wait.promise}); const first = p.handleProductSelect([product()]); p.createVisible.value = false; await vue.nextTick(); p.handleOpenCreate(); wait.resolve(product()); await first; assert.equal(p.createForm.items.length, 0)
  const q = load(); await Promise.all([q.handleProductSelect([product()]), q.handleProductSelect([product()])]); assert.equal(q.createForm.items.length, 1)
})
test('空进价及已失效采购单位不能提交；已输入成本按原采购单位原值发送', async () => {
  let current = product(); const p = load({getProductDetail: async () => current}); await p.handleProductSelect([product()]); const row = p.createForm.items[0]
  await p.handleCreate(); assert.equal(p.sent.length, 0); row.unit = '箱'; row.price = 24; current = {...product(), units: product().units.filter(u => u.unitName !== '箱')}; await p.handleCreate(); assert.equal(p.sent.length, 0)
  current = product(); await p.handleCreate(); assert.equal(p.sent.length, 1); assert.equal(p.sent[0].items[0].price, 24); assert.equal(p.sent[0].items[0].unit, '箱')
})
test('表单异步校验期间重复提交只创建一单，关闭弹窗后的校验不发送', async () => {
  const wait = deferred(), p = load(); await p.handleProductSelect([product()]); p.createForm.items[0].price = 12; p.createFormRef.value = {validate: () => wait.promise}
  const first = p.handleCreate(), second = p.handleCreate(); wait.resolve(true); await Promise.all([first, second]); assert.equal(p.sent.length, 1)
  const pending = deferred(), q = load(); await q.handleProductSelect([product()]); q.createForm.items[0].price = 12; q.createFormRef.value = {validate: () => pending.promise}
  const old = q.handleCreate(); q.createVisible.value = false; q.handleOpenCreate(); pending.resolve(true); await old; assert.equal(q.sent.length, 0)
})
test('并发选品仍有商品待加载时不能提前提交遗漏商品', async () => {
  const wait = deferred(), p = load({getProductDetail: id => id === 2 ? wait.promise : Promise.resolve(product(id))})
  const first = p.handleProductSelect([product(1)]), second = p.handleProductSelect([product(2)]); await first; p.createForm.items[0].price = 12
  await p.handleCreate(); assert.equal(p.sent.length, 0); wait.resolve(product(2)); await second; assert.equal(p.createForm.items.length, 2)
})
