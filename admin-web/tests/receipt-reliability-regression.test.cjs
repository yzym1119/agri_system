const test=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path'), vm=require('node:vm'), ts=require('typescript'), vue=require('vue')
function load(overrides={}) {
 const filename=path.join(__dirname,'../src/views/purchase/receipt/index.vue'),file=fs.readFileSync(filename,'utf8'),source=file.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],ast=ts.createSourceFile(filename,source,ts.ScriptTarget.Latest,true)
 const names=ast.statements.filter(ts.isVariableStatement).flatMap(s=>[...s.declarationList.declarations].filter(d=>ts.isIdentifier(d.name)).map(d=>d.name.text))
 const imports=Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s=>[s.importClause?.name?.text,...(s.importClause?.namedBindings?.elements||[]).map(e=>e.name.text)].filter(Boolean).map(n=>[n,{}])))
 let id=0;const messages=[],user=vue.reactive({token:'employee-a',userInfo:{id:1}}),context={exports:{},...imports,...vue,watch(){},onBeforeUnmount(){},useRoute:()=>({query:{}}),useUserStore:()=>user,crypto:{randomUUID:()=>`receipt-test-${++id}`},ElMessage:{error:m=>messages.push(m),warning:m=>messages.push(m),success:m=>messages.push(m)},getPurchaseList:async()=>({records:[]}),getPurchaseDetail:async i=>detail(i),receivePurchase:async()=>({receiptId:1,batches:[]}),...overrides}
 vm.createContext(context);vm.runInContext(ts.transpileModule(ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')+`\nglobalThis.bindings={${names.join(',')}}`,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context)
 return {...context.bindings,messages,user}
}
function detail(id){return {id,orderNo:`PO-${id}`,storeId:id,status:2,items:[{id:id*10,productId:id,productName:`商品${id}`,unit:'箱',qty:2,price:12}]}}
function deferred(){let resolve,reject;return {promise:new Promise((a,b)=>{resolve=a;reject=b}),resolve,reject}}
async function ready(p,id=1){p.purchaseOrderId.value=id;await p.loadDetail()}

test('采购详情A晚到不得覆盖B，切换立即清旧行且旧finally不结束B加载',async()=>{
 const a=deferred(),b=deferred(),p=load({getPurchaseDetail:id=>id===1?a.promise:b.promise})
 p.lineRows.value=[{productId:99}];p.purchaseOrderId.value=1;const first=p.loadDetail();assert.equal(p.lineRows.value.length,0)
 p.purchaseOrderId.value=2;const second=p.loadDetail();a.resolve(detail(1));await first;assert.equal(p.lineRows.value.length,0);assert.equal(p.detailLoading.value,true)
 b.resolve(detail(2));await second;assert.equal(p.detail.value.id,2);assert.equal(p.lineRows.value[0].productId,2)
})
test('清空采购单及员工换会话后，旧响应不恢复详情',async()=>{
 const a=deferred(),p=load({getPurchaseDetail:()=>a.promise});p.purchaseOrderId.value=1;const first=p.loadDetail();p.purchaseOrderId.value=undefined;await p.loadDetail();a.resolve(detail(1));await first;assert.equal(p.lineRows.value.length,0)
 const b=deferred(),q=load({getPurchaseDetail:()=>b.promise});q.purchaseOrderId.value=2;const second=q.loadDetail();q.user.token='employee-b';b.resolve(detail(2));await second;assert.equal(q.detail.value,null);assert.equal(q.lineRows.value.length,0)
})
test('采购详情和列表失败有独立错误，重新读取可恢复，不伪装空记录',async()=>{
 let fail=true;const p=load({getPurchaseDetail:async id=>{if(fail)throw Error('详情断网');return detail(id)},getPurchaseList:async()=>{throw Error('列表断网')}})
 await ready(p);assert.match(p.detailError.value,/详情/);assert.equal(p.lineRows.value.length,0);await p.loadOrders();assert.match(p.ordersError.value,/采购单/)
 fail=false;await p.loadDetail();assert.equal(p.detailError.value,'');assert.equal(p.lineRows.value.length,1)
})
test('新增收货批次不虚构成本和数量，超8位/科学计数/无穷值不提交',async()=>{
 const sent=[],p=load({receivePurchase:async data=>{sent.push(data);return {batches:[]}}});await ready(p)
 const fresh=p.newBatch('箱');assert.equal(fresh.originalUnitCost,'');assert.equal(fresh.originalQuantity,'')
 for(const value of ['1.000000001','1e2','Infinity']){p.lineRows.value[0].batches[0].originalQuantity=value;await p.submit()}
 assert.equal(sent.length,0)
 p.lineRows.value[0].batches[0].originalQuantity='1';p.lineRows.value[0].batches[0].originalUnitCost='0.123456789';await p.submit();assert.equal(sent.length,0)
})
test('详情加载中与详情身份不一致都不能提交，重复点击只发一个请求',async()=>{
 const waiting=deferred(),sent=[],p=load({receivePurchase:data=>{sent.push(data);return waiting.promise}});await ready(p)
 p.detailLoading.value=true;const blocked=p.submit();assert.equal(sent.length,0);await blocked;p.detailLoading.value=false
 p.purchaseOrderId.value=2;await p.submit();assert.equal(sent.length,0);p.purchaseOrderId.value=1
 const a=p.submit(),b=p.submit();assert.equal(sent.length,1);waiting.resolve({receiptId:1,batches:[]});await Promise.all([a,b])
})
test('提交期间旧成功不清空另一采购单，打印使用提交时快照',async()=>{
 const waiting=deferred(),p=load({receivePurchase:()=>waiting.promise});await ready(p);p.printLabels.value=true
 const a=p.submit();p.purchaseOrderId.value=2;p.detail.value=detail(2);p.lineRows.value=[{productId:2,productName:'另一商品',batches:[]}]
 waiting.resolve({receiptId:1,batches:[{productId:1,internalReceiptBatchNo:'RCV',originalQuantity:2,originalUnitName:'箱',dateStatus:'UNKNOWN'}]});await a
 assert.equal(p.purchaseOrderId.value,2);assert.equal(p.lineRows.value[0].productId,2);assert.equal(p.printVisible.value,false)
})
test('结果未知后只能重发原请求，编辑不能悄悄更改数量、原因和请求身份',async()=>{
 const sent=[];let call=0;const p=load({receivePurchase:async data=>{sent.push(JSON.parse(JSON.stringify(data)));if(++call===1)throw Error('连接中断');return {receiptId:1,batches:[]}}});await ready(p)
 await p.submit();const original=JSON.stringify(sent[0]);assert.ok(p.pendingReceipt.value)
 p.lineRows.value[0].batches[0].originalQuantity=9;p.remark.value='偷偷改备注';await p.submit();assert.equal(JSON.stringify(sent[1]),original)
})
test('首次明确业务拒绝允许修正；未知请求重试遭业务拒绝仍不能改请求',async()=>{
 const reject=()=>{throw Object.assign(Error('业务拒绝'),{apiBusinessCode:400})};const p=load({receivePurchase:async()=>reject()});await ready(p);await p.submit();assert.equal(p.pendingReceipt.value,null)
 let i=0;const q=load({receivePurchase:async()=>{if(i++===0)throw Error('超时');reject()}});await ready(q);await q.submit();await q.submit();assert.ok(q.pendingReceipt.value)
})
test('已加载详情属于原员工，换登录会话后不能以新员工提交旧资料',async()=>{
 const sent=[],p=load({receivePurchase:async data=>{sent.push(data);return {batches:[]}}});await ready(p);assert.equal(p.canSubmit.value,true)
 p.user.token='employee-b';assert.equal(p.canSubmit.value,false);await p.submit();assert.equal(sent.length,0)
})
test('转换为请求数字会丢失尾数的大数量和进价必须拒绝，不能静默改值',async()=>{
 const sent=[],p=load({receivePurchase:async data=>{sent.push(data);return {batches:[]}}});await ready(p)
 p.lineRows.value[0].batches[0].originalQuantity='1000000000.00000001';await p.submit();assert.equal(sent.length,0)
 p.lineRows.value[0].batches[0].originalQuantity='1';p.lineRows.value[0].batches[0].originalUnitCost='9999999999.12345678';await p.submit();assert.equal(sent.length,0)
 assert.equal(p.validPositiveDecimal('0.00000001'),true);assert.equal(p.validPositiveDecimal('0.12345678'),true);assert.equal(p.validPositiveDecimal('000.10000000'),true)
})
test('有效期单独填写时保持生产日期空，正常8位数值提交且打印沿原快照',async()=>{
 const sent=[],waiting=deferred(),p=load({receivePurchase:data=>{sent.push(data);return waiting.promise}});await ready(p)
 const batch=p.lineRows.value[0].batches[0];batch.originalQuantity='0.12345678';batch.originalUnitCost='0.00000001';batch.productionDate=null;batch.expiryDate='2028-12-31';p.printLabels.value=true;p.labelType.value='CASE'
 const a=p.submit();assert.equal(sent[0].lines[0].batches[0].productionDate,null);assert.equal(sent[0].lines[0].batches[0].expiryDate,'2028-12-31');assert.equal(sent[0].lines[0].batches[0].originalQuantity,.12345678)
 p.lineRows.value[0].productName='后来的编辑';p.detail.value.storeName='后来的门店';p.labelType.value='TOTE'
 waiting.resolve({receiptId:1,batches:[{productId:1,originalQuantity:.12345678,originalUnitName:'箱',dateStatus:'PARTIAL',expiryDate:'2028-12-31'}]});await a
 assert.equal(p.labels.value[0].productName,'商品1');assert.equal(p.labels.value[0].storeName,'1');assert.equal(p.labelType.value,'CASE');assert.equal(p.printVisible.value,true);assert.equal(p.submitting.value,false)
})
