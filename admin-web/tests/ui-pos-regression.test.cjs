const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const vue = require('vue')
function load(file, names, overrides = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/pos/', file), 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  const script = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  const events = [], messages = []
  const context = { ...vue, onMounted(){}, defineProps:()=>({storeId:1}), defineEmits:()=> (...args)=>events.push(args), defineExpose(){},
    usePermissionStore:()=>({permissions:['*']}), ElMessage:{warning:m=>messages.push(m)}, localStorage:{getItem(){return null}}, ...overrides }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(script+`\nglobalThis.page={${names.join(',')}}`, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText, context)
  return {...context.page,events,messages}
}
function goods() {
  const p=load('components/ModernGoodsPanel.vue',['products','inventoryMap','searchKeyword','handleSearch','activeTab'])
  p.products.value=[{id:1,name:'测试化肥',code:'SKU-001',mainUnit:'袋',units:[{status:1,allowSale:1,isBaseUnit:1,unitName:'袋'}],suggestedRetailPrice:10}]
  p.inventoryMap.value={1:5}
  return p
}
test('精确SKU回车加入一件并清空搜索，允许继续扫码',()=>{
  const p=goods(); p.searchKeyword.value=' sku-001 ';p.handleSearch()
  assert.equal(p.events[0]?.[0],'addToCart');assert.equal(p.events[0]?.[1].quantity,1);assert.equal(p.searchKeyword.value,'')
})
test('商品名称唯一命中和编码前缀仍只定位，不能自动加入',()=>{
  for(const keyword of ['测试化肥','SKU-00']) {const p=goods();p.searchKeyword.value=keyword;p.handleSearch();assert.equal(p.events.length,0)}
})
test('重复SKU或缺货SKU拒绝自动加入',()=>{
  const p=goods();p.products.value.push({...p.products.value[0],id:2});p.inventoryMap.value[2]=5;p.searchKeyword.value='SKU-001';p.handleSearch();assert.equal(p.events.length,0)
  const q=goods();q.inventoryMap.value={1:0};q.searchKeyword.value='SKU-001';q.handleSearch();assert.equal(q.events.length,0)
})
function cart(){return load('components/ModernCartList.vue',['handleQuantityChange'],{defineProps:()=>({cartItems:[]})})}
test('商品支持4位正小数，拒绝超精度、空值和非有限数量',()=>{
  const p=cart(), item={itemType:1,quantity:1,unitPrice:10,subtotal:10}
  p.handleQuantityChange(item,'0.0001');assert.equal(item.quantity,0.0001)
  for(const invalid of ['1.00001','','NaN','Infinity','0','-2']) {p.handleQuantityChange(item,invalid);assert.equal(item.quantity,0.0001,invalid)}
})
test('套餐只能整数套数，数量更新后必须重新报价',()=>{
  const p=cart(),item={id:'pkg',itemType:2,quantity:1,unitPrice:10,subtotal:10}
  p.handleQuantityChange(item,'1.5');assert.equal(item.quantity,1)
  p.handleQuantityChange(item,'3');assert.equal(item.quantity,3);assert.equal(item.pricePending,true);assert.equal(p.events[0][0],'packageChange')
})
test('空车和未完成报价的结算栏不发出checkout',()=>{
  for(const cartItems of [[],[{quantity:1,pricePending:true}],[{quantity:1,priceFallback:true}],[{quantity:1,quoteId:null}]]) {
    const p=load('components/ModernSettlementBar.vue',['handleCheckout'],{defineProps:()=>({cartItems,totalAmount:10})});p.handleCheckout();assert.equal(p.events.length,0)
  }
})
test('数量提交清除草稿，连续扫码增加实际数量不会显示旧数量，重复失焦不重报价',()=>{
  const p=load('components/ModernCartList.vue',['handleQuantityChange','quantityDrafts'],{defineProps:()=>({cartItems:[]})})
  const item={id:'line',itemType:2,quantity:1,unitPrice:10,subtotal:10}
  p.quantityDrafts.line='2';p.handleQuantityChange(item,'2')
  assert.equal(p.quantityDrafts.line,undefined)
  p.handleQuantityChange(item,'2');assert.equal(p.events.filter(e=>e[0]==='packageChange').length,1)
  item.quantity=3;assert.equal(p.quantityDrafts.line ?? String(item.quantity),'3')
  p.quantityDrafts.line='1.00001';p.handleQuantityChange(item,'1.00001');assert.equal(p.quantityDrafts.line,undefined);assert.equal(item.quantity,3)
})
test('四位小数数量连续加减不受浮点尾数影响',()=>{
  const p=load('components/ModernCartList.vue',['adjustQuantity'],{defineProps:()=>({cartItems:[]})})
  const item={id:'line',itemType:1,quantity:1.0001,unitPrice:10,subtotal:10}
  p.adjustQuantity(item,1);assert.equal(item.quantity,2.0001)
  p.adjustQuantity(item,-1);assert.equal(item.quantity,1.0001)
})
