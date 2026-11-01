const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')

// 运行真实页面 setup；仅隔离路由、权限宿主、剪贴板与网络，不复写筛选/操作分支。
function page(kind, query = {}, permissions = [], overrides = {}) {
  const filename = path.join(__dirname, '..', `src/views/${kind}/index.vue`)
  const script = fs.readFileSync(filename, 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(filename, script, ts.ScriptTarget.Latest, true)
  const names = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations].filter(d => ts.isIdentifier(d.name)).map(d => d.name.text))
  const code = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  const mounted = [], requests = [], copied = [], pushes = []
  const route = vue.reactive({query}), permission = vue.reactive({permissions})
  const scope = {...vue, onMounted: fn => mounted.push(fn), useRoute: () => route,
    useRouter: () => ({push: x => pushes.push(x)}), usePermissionStore: () => permission,
    hasPermission: (r, p) => p.includes(r), getOrderPaymentSummary: () => ({}),
    getOrderList: async q => {requests.push({...q}); return {records: [], total: 0}},
    getPurchaseList: async q => {requests.push({...q}); return {records: [], total: 0}},
    getAllStores: async () => [], getAllSuppliers: async () => [],
    navigator: {clipboard: {writeText: async value => {copied.push(value)}}},
    ElMessage: {error(){},success(){}}, console: {error(){}}, ...overrides}
  vm.createContext(scope)
  vm.runInContext(ts.transpileModule(code + `\nglobalThis.bindings={${names.join(',')}}`, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText, scope, {filename})
  return {...scope.bindings, mounted, requests, copied, pushes, route, permission}
}
const tick = async () => {await vue.nextTick(); await new Promise(resolve => setImmediate(resolve))}

test('工作台待派配送直达首次查询就携带配送状态，切页签清除旧状态', async () => {
  const p = page('order/list', {deliveryStatus:'2'})
  await Promise.all(p.mounted.map(fn=>fn())); await tick()
  assert.equal(p.activeTab.value,'delivery-2')
  assert.equal(p.requests[0].deliveryStatus,2)
  p.handleTabChange('6'); await tick()
  assert.equal(p.requests.at(-1).deliveryStatus,undefined)
  assert.equal(p.requests.at(-1).orderStatus,'6')
})
test('同一订单页面收到新任务参数会重新筛选，不带入旧配送条件', async () => {
  const p = page('order/list',{deliveryStatus:'2'})
  await Promise.all(p.mounted.map(fn=>fn())); await tick()
  p.route.query = {orderStatus:'8,9'}; await tick()
  assert.equal(p.activeTab.value,'refund')
  assert.equal(p.requests.at(-1).deliveryStatus,undefined)
  assert.equal(p.requests.at(-1).orderStatus,'8,9')
  p.route.query = {deliveryStatus:'not-a-status'}; await tick()
  assert.equal(p.activeTab.value,'all')
  assert.equal(p.requests.at(-1).orderStatus,undefined)
})
test('订单重置恢复全部查询，且长号展示不会改变复制的完整原号', async () => {
  const p = page('order/list',{deliveryStatus:'3'})
  await Promise.all(p.mounted.map(fn=>fn())); await tick()
  p.handleReset(); await tick()
  assert.equal(p.activeTab.value,'all'); assert.equal(p.requests.at(-1).deliveryStatus,undefined)
  const original='POS20261018091825-48dbf6a7-347d-42a2-9659-7a18c617ad17'
  const label=p.formatOrderNo(original)
  assert.ok(label.length<25)
  assert.ok(label.includes('…'))
  await p.handleCopyOrderNo(original); await tick()
  assert.equal(p.copied[0],original)
})
test('订单菜单随当前权限收紧，不放出退款且保留原履约能力守卫', async () => {
  const p=page('order/list',{},['order:ship','order:refund'])
  const row={id:7,orderStatus:9,deliveryType:2,deliveryStatus:2,canShip:true}
  assert.deepEqual(Array.from(p.getRowActions(row),a=>a.key),['local','refund'])
  assert.ok(Array.from(p.getRowActions({...row,deliveryType:2,deliveryStatus:3,canShip:false,canConfirmReceipt:true}),a=>a.key).includes('reassign'))
  p.handleRowAction('local',row)
  assert.equal(p.shipVisible.value,true); assert.equal(p.shipMode.value,'local')
  p.permission.permissions=[]
  assert.equal(p.getRowActions(row).length,0)
  p.handleRowAction('refund',row); assert.equal(p.refundVisible.value,false)
  p.permission.permissions=['order:refund']
  assert.equal(p.getRowActions({...row,orderStatus:8,hasReturnableItems:false}).length,0)
  assert.deepEqual(Array.from(p.getRowActions({...row,orderStatus:8,hasReturnableItems:true}),a=>a.key),['refund'])
})
test('采购待收货直达携带真实状态，重置与新参数都不保留旧筛选',async()=>{
  const p=page('purchase/order',{status:'2'})
  await tick()
  assert.equal(p.requests[0].status,2)
  p.route.query={status:'1'};await tick();assert.equal(p.requests.at(-1).status,1)
  p.handleReset();await tick();assert.equal(p.requests.at(-1).status,undefined)
  p.route.query={status:'99'};await tick();assert.equal(p.requests.at(-1).status,undefined)
})
test('采购更多操作沿用权限和状态，收货入口传原采购单身份',async()=>{
  const p=page('purchase/order',{},['purchase:inbound','purchase:cancel'])
  await tick()
  const row={id:51,status:2}
  assert.deepEqual(Array.from(p.getRowActions(row),a=>a.key),['inbound','cancel'])
  p.handleRowAction('inbound',row)
  assert.equal(p.pushes[0].path,'/purchase/receipt')
  assert.equal(p.pushes[0].query.purchaseOrderId,'51')
  p.permission.permissions=[]
  assert.equal(p.getRowActions(row).length,0)
  assert.equal(p.getRowActions({...row,status:3}).length,0)
})
test('采购切换查询时旧请求不能覆盖新列表',async()=>{
  const pending=[]
  const p=page('purchase/order',{status:'2'},[],{getPurchaseList:q=>new Promise(resolve=>pending.push({q:{...q},resolve}))})
  await tick()
  p.route.query={status:'3'};await tick()
  pending[1].resolve({records:[{id:52,status:3}],total:1});await tick()
  pending[0].resolve({records:[{id:51,status:2}],total:1});await tick()
  assert.equal(p.tableData.value[0].id,52)
  assert.equal(p.loading.value,false)
})

test('POS无配送事实时使用中性门店销售文案，未知线上方式仍待核对',()=>{
 const p=page('order/list')
 assert.equal(p.getHandoverText({orderType:1,deliveryType:null}),'门店销售')
 assert.equal(p.getHandoverText({orderType:1,deliveryType:2}),'同城配送')
 assert.equal(p.getHandoverText({orderType:1,deliveryType:99}),'交货方式待核对')
 assert.equal(p.getHandoverText({orderType:3,deliveryType:null}),'交货方式待核对')
 assert.equal(p.getHandoverText({orderType:2,deliveryType:1}),'门店自提')
})
