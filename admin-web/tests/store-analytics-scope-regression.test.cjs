const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript'), vue = require('vue')
test('切换门店时全局新增客户卡仍明确档案全局口径', () => {
  const filename = path.join(__dirname, '../src/views/store-analytics/index.vue'), source = fs.readFileSync(filename, 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true), imports = Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s => [s.importClause?.name?.text, ...(s.importClause?.namedBindings?.elements || []).map(e => e.name.text)].filter(Boolean).map(n => [n, {}])))
  const context = {exports: {}, ...imports, ...vue, onMounted() {}, use() {}, CanvasRenderer: {}, LineChart: {}, PieChart: {}, BarChart: {}}
  vm.createContext(context); vm.runInContext(ts.transpileModule(ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n') + '\nglobalThis.p={storeId,dashboard,metrics}', {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText, context)
  for (const id of [1, 2]) {context.p.storeId.value = id; context.p.dashboard.value = {newCustomerCount: 1}; const customer = context.p.metrics.value.find(m => m.title.includes('新增客户')); assert.match(customer.title, /全局/); assert.match(customer.foot, /客户档案/); assert.match(customer.foot, /不随门店/); assert.equal(customer.value, '1')}
})
