const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript')
function api(file) {
  const source=fs.readFileSync(path.join(__dirname,'../src/api',file),'utf8')
  const ast=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true)
  const script=ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')
  const calls=[],scope={exports:{},request:async args=>{calls.push(args);return {records:[{id:1,name:'本店'}],totalRow:1}}}
  vm.createContext(scope);vm.runInContext(ts.transpileModule(script,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,scope)
  return {functions:scope.exports,calls}
}
test('收银目录读取不依赖商品管理或库存管理接口',async()=>{
  const p=api('pos-catalog.ts')
  await p.functions.getPosProductList({pageNum:1});await p.functions.getPosCategoryTree()
  await p.functions.getPosPackageList({storeId:1});await p.functions.getPosInventoryList({storeId:1})
  assert.deepEqual(p.calls.map(c=>c.url),['/api/admin/pos/catalog/products','/api/admin/pos/catalog/categories','/api/admin/pos/catalog/packages','/api/admin/pos/catalog/inventory'])
})
test('业务门店下拉不读取管理员门店档案，收货只查专门待收货入口',async()=>{
  const stores=api('store.ts');await stores.functions.getBusinessStoreOptions()
  assert.equal(stores.calls[0].url,'/api/admin/stores/business-options')
  const p=api('purchase.ts');const page=await p.functions.getReceivingPurchaseList({pageSize:50})
  await p.functions.getReceivingPurchaseDetail(9)
  assert.equal(page.total,1);assert.equal(p.calls[0].url,'/api/admin/purchase/receiving-orders')
  assert.equal(p.calls[1].url,'/api/admin/purchase/receiving-orders/9')
})
