const assert=require('node:assert/strict'), test=require('node:test'), fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue')
function load(relative,overrides={}) {
 const filename=path.join(__dirname,'..',relative),file=fs.readFileSync(filename,'utf8'),source=relative.endsWith('.vue')?file.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]:file
 const ast=ts.createSourceFile(filename,source,ts.ScriptTarget.Latest,true),names=ast.statements.filter(ts.isVariableStatement).flatMap(s=>[...s.declarationList.declarations].filter(d=>ts.isIdentifier(d.name)).map(d=>d.name.text)),events=[]
 const imports=Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s=>[s.importClause?.name?.text,...(s.importClause?.namedBindings?.elements||[]).map(e=>e.name.text)].filter(Boolean).map(n=>[n,{}])))
 const context={exports:{},...imports,...vue,onMounted(){},defineProps:()=>({modelValue:true,order:{id:1}}),defineEmits:()=>()=>{},defineExpose(){},useUserStore:()=>({userInfo:{id:1}}),usePermissionStore:()=>({permissions:['product:unit:create']}),getProductList:async()=>({records:[]}),ElMessage:{warning:m=>events.push(m),error(){},success(){}},...overrides}
 vm.createContext(context);vm.runInContext(ts.transpileModule(ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')+`\nglobalThis.bindings={${names.join(',')}}`,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context)
 return {...context.bindings,exports:context.exports,events}
}
test('数量显示去无意义零，未知数量不猜成0，保留最小有效位',()=>{
 const {formatQuantity}=load('src/utils/number.ts').exports
 assert.equal(formatQuantity(2),'2');assert.equal(formatQuantity(0),'0');assert.equal(formatQuantity(.0001),'0.0001');assert.equal(formatQuantity(null),'待核对')
})
test('退货原始数量拒绝超精度和非有限值，合法半箱沿原单位预览',async()=>{
 const requests=[],p=load('src/views/order/components/RefundDialog.vue',{getOrderPaymentSummary:()=>({}),getRefundPreview:async(id,data)=>{requests.push(data);return {quoteId:'q',orderId:id,items:data.items.map(i=>({...i,refundAmount:5})),refundAmount:5}}})
 const item={orderItemId:1,returnQuantity:0,refundableQuantity:2,unit:'箱'};p.form.items=[item]
 await p.setReturnQuantity(item,'1.00001');assert.equal(item.returnQuantity,0);assert.equal(requests.length,0)
 await p.setReturnQuantity(item,'0.5');assert.equal(item.returnQuantity,.5);assert.equal(requests[0].items[0].quantity,.5);assert.equal(item.unit,'箱')
 await p.setReturnQuantity(item,'Infinity');assert.equal(item.returnQuantity,.5)
})
test('已配置基准单位不能通过按钮被新行替换，不能移除主/基准单位',()=>{
 const p=load('src/views/product/units/index.vue'),base={id:1,unitName:'瓶',baseQuantityPerUnit:1,isBaseUnit:1},box={id:2,unitName:'箱',baseQuantityPerUnit:12,isBaseUnit:0},fresh={unitName:'件',baseQuantityPerUnit:3,isBaseUnit:0}
 p.currentProduct.value={id:1,mainUnit:'箱'};p.unitRows.value=[base,box,fresh]
 p.setBaseUnit(fresh);assert.equal(p.unitRows.value[0].isBaseUnit,1);assert.equal(fresh.baseQuantityPerUnit,3)
 p.removeRow(base);p.removeRow(box);assert.equal(p.unitRows.value.length,3)
})
test('单位弹窗切换商品后旧响应不得覆盖当前单位，查询失败不能保存空单位',async()=>{
 const pending=[],saved=[],p=load('src/views/product/units/index.vue',{getProductUnits:id=>new Promise((resolve,reject)=>pending.push({id,resolve,reject})),replaceProductUnits:async(...args)=>saved.push(args)})
 const a=p.openUnits({id:1,name:'甲',mainUnit:'瓶'}),b=p.openUnits({id:2,name:'乙',mainUnit:'袋'})
 pending[1].resolve([{id:2,unitName:'袋',baseQuantityPerUnit:1,isBaseUnit:1}]);await b
 pending[0].resolve([{id:1,unitName:'瓶',baseQuantityPerUnit:1,isBaseUnit:1}]);await a;assert.equal(p.unitRows.value[0].unitName,'袋')
 const c=p.openUnits({id:3,name:'丙'});pending[2].reject(Error('断网'));await c;await p.save();assert.equal(saved.length,0)
})
test('整件按精确换算判定，半箱12瓶可退，0.1箱和半瓶不可退',()=>{
 const {isWholeBaseQuantity}=load('src/utils/quantity.ts').exports
 assert.equal(isWholeBaseQuantity('0.5',12),true);assert.equal(isWholeBaseQuantity('0.1',12),false);assert.equal(isWholeBaseQuantity('0.5',1),false)
 const p=load('src/views/order/components/RefundDialog.vue',{getOrderPaymentSummary:()=>({}),isWholeBaseQuantity})
 const item={orderItemId:1,returnQuantity:0,refundableQuantity:2,unit:'箱',baseUnitName:'瓶',allowFractionalBaseQuantity:false,originalToBaseRatio:12}
 p.form.items=[item];p.setReturnQuantity(item,'0.1');assert.equal(item.returnQuantity,0)
})

test('POS整件不能半瓶，半箱12瓶可以；计量商品可填写小数',()=>{
 const {isWholeBaseQuantity}=load('src/utils/quantity.ts').exports
 const p=load('src/views/pos/components/ModernCartList.vue',{defineProps:()=>({cartItems:[]}),isWholeBaseQuantity})
 const bottle={id:'a',itemType:1,quantity:1,unitPrice:10,currentUnit:'瓶',baseUnitName:'瓶',allowFractionalBaseQuantity:false,saleUnits:[{unitName:'瓶',baseQuantityPerUnit:1}]}
 p.handleQuantityChange(bottle,'0.5');assert.equal(bottle.quantity,1)
 const box={...bottle,id:'b',currentUnit:'箱',saleUnits:[{unitName:'箱',baseQuantityPerUnit:12}]}
 p.handleQuantityChange(box,'0.5');assert.equal(box.quantity,.5)
 const weight={...bottle,id:'c',currentUnit:'公斤',allowFractionalBaseQuantity:true};p.handleQuantityChange(weight,'0.125');assert.equal(weight.quantity,.125)
})
test('只有单位查看权限时可展示无记录的单位说明，但不可新增、改基准或保存',async()=>{
 const saved=[],p=load('src/views/product/units/index.vue',{usePermissionStore:()=>({permissions:['product:unit:query']}),getProductUnits:async()=>[],replaceProductUnits:async()=>saved.push(1)})
 await p.openUnits({id:1,mainUnit:'瓶'});assert.equal(p.unitRows.value[0].unitName,'瓶');assert.equal(p.unitsError.value,'')
 p.addRow();await p.save();assert.equal(p.unitRows.value.length,1);assert.equal(saved.length,0)
})
test('旧商品单位保存响应不能关闭新商品弹窗',async()=>{
 let finish;const p=load('src/views/product/units/index.vue',{getProductUnits:async id=>[{id,unitName:'瓶',isBaseUnit:1,baseQuantityPerUnit:1}],replaceProductUnits:()=>new Promise(resolve=>finish=resolve)})
 await p.openUnits({id:1,mainUnit:'瓶'});const a=p.save();p.closeUnits();await p.openUnits({id:2,mainUnit:'瓶'});finish();await a
 assert.equal(p.currentProduct.value.id,2);assert.equal(p.dialogVisible.value,true)
})
test('POS换销售单位不能把合法半箱变成半瓶',()=>{
 const {isWholeBaseQuantity}=load('src/utils/quantity.ts').exports
 const p=load('src/views/pos/components/ModernCartList.vue',{defineProps:()=>({cartItems:[]}),isWholeBaseQuantity})
 const item={id:'a',itemType:1,quantity:.5,currentUnit:'箱',allowFractionalBaseQuantity:false,baseUnitName:'瓶',saleUnits:[{unitName:'瓶',baseQuantityPerUnit:1},{unitName:'箱',baseQuantityPerUnit:12}]}
 p.handleUnitChange(item,'瓶');assert.equal(item.currentUnit,'箱')
})
