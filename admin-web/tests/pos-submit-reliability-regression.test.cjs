const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript'), vue = require('vue')
function storage() { const values = new Map(); return { getItem:k=>values.get(k) ?? null, setItem:(k,v)=>values.set(k,v), removeItem:k=>values.delete(k), values } }
function load(overrides = {}) {
  const file = path.join(__dirname, '../src/views/pos/index.vue'), source = fs.readFileSync(file,'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true), names = ast.statements.filter(ts.isVariableStatement).flatMap(s=>[...s.declarationList.declarations].filter(d=>ts.isIdentifier(d.name)).map(d=>d.name.text))
  const user = vue.reactive({token:'session-a',userInfo:{id:8,extra:{storeId:2}}}), messages=[], sent=[], local = overrides.sessionStorage || storage()
  const context = {crypto:require("node:crypto").webcrypto,cancelPOSSubmission:async key=>({requestKey:key,status:"CANCELLED",orderId:null}),...vue,onMounted(){},onUnmounted(){},useUserStore:()=>user,useRouter:()=>({push(){}}),sessionStorage:local,getOrderList:async()=>({records:[{id:77,orderNo:'77'}]}),ElMessage:Object.fromEntries(['error','warning','success','info'].map(k=>[k,m=>messages.push(m)])),createPOSOrder:async data=>{sent.push(data);throw Error('响应丢失')},console:{warn(){},error(){}},...overrides}
  vm.createContext(context);vm.runInContext(ts.transpileModule(ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')+`\nglobalThis.page={${names.join(',')}}`,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context)
  const p={...context.page,user,messages,sent,storage:local};p.cartItems.value=[line()];return p
}
const line=()=>({id:'item',itemType:1,productId:11,name:'肥料',quantity:1,currentUnit:'袋',unitPrice:10,subtotal:10,suggestedUnitPrice:10,quoteId:'quote'})
const order=()=>({items:[{itemType:1,productId:11,quantity:1,unit:'袋',quotedUnitPrice:10}],paymentType:1,receiveAmount:10,discountAmount:0,walletAuthCode:'123456'})
function deferred(){let resolve,reject;return {promise:new Promise((a,b)=>{resolve=a;reject=b}),resolve,reject}}

test('POS响应丢失后原单冻结，重复提交只发一次，不能换商品客户或清空恢复',async()=>{
  const p=load();await p.handleSubmitOrder(order());assert.ok(p.pendingSale.value)
  await p.handleSubmitOrder(order());await p.handleAddToCart(line());p.handleCartUpdate([]);p.handleClearCart();await p.handleCustomerChange({id:99});await p.handleRestoreCart({cartItems:[],customer:null,discountAmount:0})
  assert.equal(p.sent.length,1);assert.equal(p.cartItems.value.length,1);assert.equal(p.selectedCustomer.value,null);assert.equal(p.pendingSale.value.storeId,2);assert.equal(p.pendingSale.value.staffId,8)
  const saved=[...p.storage.values.values()].join('');assert.ok(!saved.includes('123456'),'不持久化钱包验证码');assert.match(p.messages.join(' '),/核对/)
})
test('POS提交前即保存冻结，重复并发点击只发一次，成功才清理摘要和购物车',async()=>{
  const wait=deferred(),sent=[],p=load({createPOSOrder:data=>{sent.push(data);return wait.promise}})
  const a=p.handleSubmitOrder(order());assert.ok(p.pendingSale.value);assert.equal(p.storage.values.size,1);await p.handleSubmitOrder(order());assert.equal(sent.length,1)
  wait.resolve(77);await a;assert.equal(p.pendingSale.value,null);assert.equal(p.cartItems.value.length,0);assert.equal(p.storage.values.size,0)
})
test('明确业务拒绝允许更正，5xx系统错、超时和无响应保持冻结',async()=>{
  for(const error of [Object.assign(Error('库存不足'),{apiBusinessCode:10001}),Object.assign(Error('参数错误'),{response:{status:400}})]) {
    let calls=0;const p=load({createPOSOrder:async()=>{calls++;if(calls===1)throw error;return 77}});await p.handleSubmitOrder(order());assert.equal(p.pendingSale.value,null);await p.handleSubmitOrder(order());assert.equal(calls,2)
  }
  for(const error of [Error('timeout'),Object.assign(Error('系统繁忙'),{apiBusinessCode:10002}),Object.assign(Error('网关故障'),{response:{status:502}})]) {
    let calls=0;const p=load({createPOSOrder:async()=>{calls++;throw error}});await p.handleSubmitOrder(order());await p.handleSubmitOrder(order());assert.ok(p.pendingSale.value);assert.equal(calls,1)
  }
})
test('离开重进和同员工重新登录仍保留原冻结，另一个员工门店隔离',async()=>{
  const local=storage(),p=load({sessionStorage:local});await p.handleSubmitOrder(order())
  const q=load({sessionStorage:local});assert.ok(q.pendingSale.value);await q.handleSubmitOrder(order());assert.equal(q.sent.length,0)
  q.user.token='session-b';q.user.userInfo={id:9,extra:{storeId:3}};await vue.nextTick();assert.equal(q.pendingSale.value,null);assert.equal(q.cartItems.value.length,0)
  q.user.token='session-c';q.user.userInfo={id:8,extra:{storeId:2}};await vue.nextTick();assert.ok(q.pendingSale.value)
})
test('旧提交成功或失败不得清空新员工单据和弹窗，也不覆盖新员工冻结',async()=>{
  for(const success of [true,false]) {
    const wait=deferred(),p=load({createPOSOrder:()=>wait.promise});const first=p.handleSubmitOrder(order())
    p.user.token='session-b';p.user.userInfo={id:9,extra:{storeId:3}};await vue.nextTick();p.cartItems.value=[{...line(),productId:99}];p.checkoutVisible.value=true
    if(success)wait.resolve(77);else wait.reject(Error('超时'));await first
    assert.equal(p.cartItems.value[0].productId,99);assert.equal(p.checkoutVisible.value,true);assert.equal(p.pendingSale.value,null);assert.equal(p.submittingOrder.value,false)
  }
})
test('sessionStorage不可写拒绝开单，不能发送一个重进会遗忘的请求',async()=>{
  const p=load({sessionStorage:{getItem:()=>null,setItem(){throw Error('禁用存储')},removeItem(){}}});await p.handleSubmitOrder(order());assert.equal(p.sent.length,0);assert.match(p.messages.join(' '),/保存|记录|存储/)
})
const detail=()=>({id:77,orderNo:'77',orderType:1,orderStatus:6,paymentMethod:1,payments:[{paymentType:1,amount:10}],createdAt:new Date().toISOString(),storeId:2,operatorStaffId:8,customerId:null,totalAmount:10,payAmount:10,discountAmount:0,items:[{productId:11,unit:'袋',quantity:1,totalPrice:10}]})
test('找到原单只读核验后必须人工确认，确认重读一致才清理，不发送第二笔收款',async()=>{
  let reads=0;const p=load({getOrderDetail:async id=>{assert.equal(id,77);reads++;return detail()}});await p.handleSubmitOrder(order());p.recoveryOrderId.value='77'
  await p.verifyRecoveredSale();assert.ok(p.recoveryCandidate.value);await p.confirmRecoveredSale();assert.ok(p.pendingSale.value)
  p.recoveryConfirmed.value=true;await p.confirmRecoveredSale();assert.equal(reads,2);assert.equal(p.pendingSale.value,null);assert.equal(p.storage.values.size,0);assert.equal(p.sent.length,1)
})
test('原单门店员工客户净额物料任一不符、缺员工事实、未找到或没人工确认都不能解除',async()=>{
  for(const delta of [{id:78},{orderNo:'78'},{createdAt:new Date(Date.now()-60000).toISOString()},{createdAt:new Date(Date.now()+60000).toISOString()},{createdAt:null},{paymentMethod:2},{paymentMethod:null},{storeId:3},{operatorStaffId:9},{operatorStaffId:undefined,operatorName:'同名收银员'},{customerId:99},{payAmount:9},{totalAmount:11},{items:[{productId:99,unit:'袋',quantity:1,totalPrice:10}]},{items:[{productId:11,unit:'袋',quantity:2,totalPrice:10}]}]) {
    const p=load({getOrderDetail:async()=>({...detail(),...delta})});await p.handleSubmitOrder(order());p.recoveryOrderId.value='77';await p.verifyRecoveredSale();assert.equal(p.recoveryCandidate.value,null);assert.ok(p.pendingSale.value)
  }
  const p=load({getOrderDetail:async()=>{throw Error('不存在')}});await p.handleSubmitOrder(order());p.recoveryOrderId.value='77';await p.verifyRecoveredSale();assert.ok(p.pendingSale.value);assert.equal(p.recoveryCandidate.value,null)
})
test('核验响应过时、核验后修改ID、确认时原单变化均不能解除',async()=>{
  const wait=deferred(),p=load({getOrderDetail:()=>wait.promise});await p.handleSubmitOrder(order());p.recoveryOrderId.value='77';const reading=p.verifyRecoveredSale();p.recoveryOrderId.value='78';wait.resolve(detail());await reading;assert.equal(p.recoveryCandidate.value,null)
  let reads=0;const q=load({getOrderDetail:async()=>++reads===1?detail():{...detail(),payAmount:8}});await q.handleSubmitOrder(order());q.recoveryOrderId.value='77';await q.verifyRecoveredSale();q.recoveryConfirmed.value=true;await q.confirmRecoveredSale();assert.ok(q.pendingSale.value);assert.equal(q.recoveryCandidate.value,null)
})
test('完整订单号精确唯一匹配后才读详情，模糊结果或重复结果不能猜配',async()=>{
  let detailReads=0;const number='20261020-9ca49813-6d9d-4e19-9900-90a5b4c21291'
  const p=load({getOrderList:async query=>{assert.equal(query.keyword,number);return {records:[{id:77,orderNo:number}]}},getOrderDetail:async()=>{detailReads++;return {...detail(),orderNo:number}}});await p.handleSubmitOrder(order());p.recoveryOrderId.value=number;await p.verifyRecoveredSale();assert.ok(p.recoveryCandidate.value);assert.equal(detailReads,1)
  for(const records of [[{id:77,orderNo:'前缀-'+number}],[{id:77,orderNo:number},{id:78,orderNo:number}]]) {
    const q=load({getOrderList:async()=>({records}),getOrderDetail:async()=>{throw Error('不能读取猜配详情')}});await q.handleSubmitOrder(order());q.recoveryOrderId.value=number;await q.verifyRecoveredSale();assert.equal(q.recoveryCandidate.value,null);assert.ok(q.pendingSale.value)
  }
})
test('成功响应缺有效订单ID也保持结果未知，不能清车后再次收款',async()=>{
  for(const response of [null,undefined,{},0]) {let sent=0;const p=load({createPOSOrder:async()=>{sent++;return response}});await p.handleSubmitOrder(order());assert.ok(p.pendingSale.value);await p.handleSubmitOrder(order());assert.equal(sent,1)}
})
test('订单中心核对入口使用真实路由名称，不使用不存在的路径',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/views/pos/index.vue'),'utf8'), registry=fs.readFileSync(path.join(__dirname,'../src/router/registry.ts'),'utf8')
  assert.match(source,/router\.push\(\{ name: 'OrderList' \}\)/);assert.match(registry,/name: 'OrderList'/)
})
test('混合支付必须逐来源核对；本金和专用金合并核对原钱包金额，不持久化验证码',async()=>{
  const request={...order(),paymentType:10,payments:[{paymentType:1,amount:4},{paymentType:4,amount:6,authCode:'654321'}]}
  for(const cash of [4,3]) {
    const p=load({getOrderDetail:async()=>({...detail(),paymentMethod:10,payments:[{paymentType:1,amount:cash},{paymentType:4,amount:4},{paymentType:8,amount:2},{paymentType:5,amount:1}]})})
    await p.handleSubmitOrder(request);p.recoveryOrderId.value='77';await p.verifyRecoveredSale();assert.equal(!!p.recoveryCandidate.value,cash===4);assert.ok(![...p.storage.values.values()].join('').includes('654321'))
  }
})
test('混合界面仅分配一种支付时，按后端实际主支付方式核对',async()=>{
  const p=load({getOrderDetail:async()=>detail()});await p.handleSubmitOrder({...order(),paymentType:10,payments:[{paymentType:1,amount:10}]});p.recoveryOrderId.value='77';await p.verifyRecoveredSale();assert.ok(p.recoveryCandidate.value)
})
test('提交冻结不能卸载结算组件，明确业务拒绝后保留原配送、券与混合支付草稿',async()=>{
  // 模板AST约束实例身份；只运行父setup会漏掉v-if卸载导致的子草稿丢失。
  const file=path.join(__dirname,'../src/views/pos/index.vue'), descriptor=require('@vue/compiler-sfc').parse(fs.readFileSync(file,'utf8')).descriptor
  const root=require('@vue/compiler-dom').baseParse(descriptor.template.content)
  const find=(node)=>node.tag==='CheckoutDialog'?node:(node.children||[]).map(find).find(Boolean)
  const checkout=find(root);assert.ok(checkout);assert.ok(!checkout.props.some(p=>p.type===7&&p.name==='if'),'结算组件必须跨pendingSale状态保留实例')
  assert.ok(checkout.props.some(p=>p.type===7&&p.name==='bind'&&p.arg?.content==='submitting'&&p.exp?.content==='submittingOrder'))
  const p=load({createPOSOrder:async()=>{throw Object.assign(Error('库存不足'),{apiBusinessCode:10001})}})
  const props=vue.reactive({get modelValue(){return p.checkoutVisible.value},get submitting(){return p.submittingOrder.value},totalAmount:10,discountAmount:0,checkoutData:{paymentType:1},cartItems:[line()],customer:null})
  const filename=path.join(__dirname,'../src/views/pos/components/CheckoutDialog.vue'), source=fs.readFileSync(filename,'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1], ast=ts.createSourceFile(filename,source,ts.ScriptTarget.Latest,true)
  const context={...vue,defineProps:()=>props,defineEmits:()=>()=>{},getCustomerCoupons:async()=>[],ElMessage:{warning(){}},console:{warn(){},error(){}}};vm.createContext(context)
  vm.runInContext(ts.transpileModule(ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')+'\nglobalThis.dialog={deliveryForm,paymentType,selectedCouponId,mixedDetails,confirmForm}',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context)
  p.checkoutVisible.value=true;await vue.nextTick();const dialog=context.dialog
  dialog.deliveryForm.deliveryType=2;dialog.deliveryForm.receiverName='虚构收货人';dialog.deliveryForm.receiverPhone='00000000000';dialog.deliveryForm.receiverAddress='测试地点';dialog.paymentType.value=10;dialog.selectedCouponId.value=38;dialog.mixedDetails.cash=4;dialog.mixedDetails.wechat=6;dialog.confirmForm.value.remark='原配送草稿'
  await p.handleSubmitOrder({...order(),deliveryType:2});await vue.nextTick()
  assert.equal(p.checkoutVisible.value,true);assert.equal(p.pendingSale.value,null);assert.equal(dialog.deliveryForm.deliveryType,2);assert.equal(dialog.deliveryForm.receiverAddress,'测试地点');assert.equal(dialog.paymentType.value,10);assert.equal(dialog.selectedCouponId.value,38);assert.equal(dialog.mixedDetails.cash,4);assert.equal(dialog.mixedDetails.wechat,6);assert.equal(dialog.confirmForm.value.remark,'原配送草稿')
})

test('新收银在发请求前持久化同一安全请求号，异常后不自动重发',async()=>{
 let key;const p=load({createPOSOrder:async request=>{key=request.requestKey;assert.match(key,/^[0-9a-f-]{36}$/);assert.ok([...p.storage.values.values()].some(v=>JSON.parse(v).requestKey===key));throw Error('timeout')}});
 await p.handleSubmitOrder(order());assert.equal(p.pendingSale.value.requestKey,key);await p.handleSubmitOrder(order());assert.equal(p.pendingSale.value.requestKey,key);
})
test('查不到原请求不能解冻，后台取消屏障确认后才允许继续',async()=>{
 const calls=[];const p=load({getPOSSubmission:async key=>({requestKey:key,status:'UNKNOWN',orderId:null}),cancelPOSSubmission:async key=>{calls.push(key);return {requestKey:key,status:'CANCELLED',orderId:null}}});
 await p.handleSubmitOrder(order());await p.checkPendingSubmission();assert.ok(p.pendingSale.value);assert.equal(p.recoveryResult.value.status,'UNKNOWN');
 await p.endPendingSubmission();assert.equal(calls.length,0);p.recoveryEndConfirmed.value=true;await p.endPendingSubmission();assert.equal(calls.length,1);assert.equal(p.pendingSale.value,null);assert.equal(p.sent.length,1);
})
test('取消竞争中原单已成交不能当未成交解冻，确认成交不再次收款',async()=>{
 const p=load({cancelPOSSubmission:async key=>({requestKey:key,status:'COMPLETED',orderId:77}),getPOSSubmission:async key=>({requestKey:key,status:'COMPLETED',orderId:77})});
 await p.handleSubmitOrder(order());p.recoveryEndConfirmed.value=true;await p.endPendingSubmission();assert.ok(p.pendingSale.value);assert.equal(p.recoveryResult.value.orderId,77);
 p.recoveryConfirmed.value=true;await p.finishCompletedSubmission();assert.equal(p.pendingSale.value,null);assert.equal(p.cartItems.value.length,0);assert.equal(p.sent.length,1);
})
test('取消失败或不匹配响应仍冻结；旧员工取消响应不清除新会话',async()=>{
 for(const response of [null,{requestKey:'other',status:'CANCELLED'},{status:'CANCELLED'},{status:'UNKNOWN'}]) {
 const p=load({cancelPOSSubmission:async()=>response});await p.handleSubmitOrder(order());p.recoveryEndConfirmed.value=true;await p.endPendingSubmission();assert.ok(p.pendingSale.value);
 }
 const wait=deferred(),p=load({cancelPOSSubmission:()=>wait.promise});await p.handleSubmitOrder(order());const key=p.pendingSale.value.requestKey;p.recoveryEndConfirmed.value=true;const ending=p.endPendingSubmission();
 p.user.token='new';p.user.userInfo={id:9,extra:{storeId:3}};await vue.nextTick();p.cartItems.value=[{...line(),productId:99}];wait.resolve({requestKey:key,status:'CANCELLED'});await ending;assert.equal(p.cartItems.value[0].productId,99);
})

test('明确业务拒绝也须后台取消确认；原请求若已成交或取消失败不能解冻',async()=>{
 for(const mode of ['completed','offline']) {
 const p=load({createPOSOrder:async()=>{throw Object.assign(Error('正在处理中'),{apiBusinessCode:10001})},cancelPOSSubmission:async key=>{if(mode==='offline')throw Error('断网');return {requestKey:key,status:'COMPLETED',orderId:77}}});
 await p.handleSubmitOrder(order());assert.ok(p.pendingSale.value);assert.equal(p.checkoutVisible.value,false);
 }
})
test('恢复时本机记录读取、删除失败或被替换均保持冻结',async()=>{
 for(const fault of ['read','remove','replace']) {
 const local=storage(),p=load({sessionStorage:local,cancelPOSSubmission:async key=>({requestKey:key,status:'CANCELLED',orderId:null})});
 await p.handleSubmitOrder(order());
 if(fault==='read')local.getItem=()=>{throw Error('不可读')};
 if(fault==='remove')local.removeItem=()=>{throw Error('不可删')};
 if(fault==='replace')for(const key of local.values.keys())local.setItem(key,'other-record');
 p.recoveryEndConfirmed.value=true;await p.endPendingSubmission();assert.ok(p.pendingSale.value);
 }
})
