const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue'),path=require('node:path')
function page(pendingPost=false) {
 const source=fs.readFileSync(path.join(__dirname,'../src/views/order/components/ReceiptConfirmDialog.vue'),'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
 const ast=ts.createSourceFile('dialog.ts',source,ts.ScriptTarget.Latest,true)
 const code=ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')
 const props=vue.reactive({modelValue:false,orderId:1}),user=vue.reactive({userInfo:{id:1}}),permission=vue.reactive({permissions:['order:ship']})
 const reads=[],writes=[],posts=[],events=[],messages=[]
 const scope={...vue,onUnmounted(){},defineProps:()=>props,defineEmits:()=> (...args)=>events.push(args),useUserStore:()=>user,usePermissionStore:()=>permission,hasPermission:(r,p)=>p.includes(r),getOrderDetail:id=>new Promise((resolve,reject)=>reads.push({id,resolve,reject})),confirmReceipt:(...args)=>{writes.push(args);return pendingPost?new Promise((resolve,reject)=>posts.push({resolve,reject})):Promise.resolve()},ElMessage:{success:m=>messages.push(m),error:m=>messages.push(m)}}
 vm.createContext(scope);vm.runInContext(ts.transpileModule(code+'\nglobalThis.out={detail,loading,error,canConfirm,submit,reload};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,scope)
 return {...scope.out,props,user,permission,reads,writes,posts,events,messages}
}
const tick=async()=>{await vue.nextTick();await new Promise(r=>setImmediate(r))}
const detail=(id=1)=>({id,orderNo:'TEST'+id,canConfirmReceipt:true,deliveryConfirmationKey:'key'+id,delivery:{deliveryStatus:3},deliveryItems:[{orderItemId:2,productName:'肥料',quantity:3,unit:'袋'}]})
test('签收只提交已加载订单及后台核对摘要，不提交前端重算数量',async()=>{const p=page();p.props.modelValue=true;await tick();assert.equal(p.canConfirm.value,false);p.reads[0].resolve(detail());await tick();assert.equal(p.canConfirm.value,true);await p.submit();assert.deepEqual(Array.from(p.writes[0]),[1,'key1'])})
test('关闭后旧读取不能回填下一订单，未知单位与空清单禁止确认',async()=>{const p=page();p.props.modelValue=true;await tick();p.props.orderId=2;await tick();p.reads[0].resolve(detail(1));await tick();assert.equal(p.detail.value,null);const d=detail(2);d.deliveryItems[0].unit=null;p.reads[1].resolve(d);await tick();assert.equal(p.canConfirm.value,false);await p.submit();assert.equal(p.writes.length,0)})
test('读取失败有错误和重新读取入口，失败不是零商品成功',async()=>{const p=page();p.props.modelValue=true;await tick();p.reads[0].reject(Error('连接失败'));await tick();assert.ok(p.error.value);assert.equal(p.canConfirm.value,false);p.reload();await tick();assert.equal(p.reads.length,2);p.reads[1].resolve(detail());await tick();assert.equal(p.canConfirm.value,true)})
test('切换员工后旧读取不能恢复签收',async()=>{const p=page();p.props.modelValue=true;await tick();p.user.userInfo.id=2;await tick();p.reads[0].resolve(detail());await tick();assert.equal(p.canConfirm.value,false);assert.equal(p.detail.value,null)})
test('后端已签收或缺少核对摘要不再发起新签收',async()=>{const p=page();p.props.modelValue=true;await tick();const d=detail();d.delivery.deliveryStatus=4;d.canConfirmReceipt=false;p.reads[0].resolve(d);await tick();assert.equal(p.canConfirm.value,false);await p.submit();assert.equal(p.writes.length,0)})

test('权限撤销立即禁止已加载清单提交',async()=>{const p=page();p.props.modelValue=true;await tick();p.reads[0].resolve(detail());await tick();p.permission.permissions=[];await tick();assert.equal(p.canConfirm.value,false);await p.submit();assert.equal(p.writes.length,0)})
for(const switchTo of ['order','employee']) for(const outcome of ['success','failure']) test(`POST挂起后切换${switchTo}，旧${outcome}不污染新页面`,async()=>{
 const p=page(true);p.props.modelValue=true;await tick();p.reads[0].resolve(detail());await tick();const posting=p.submit();await tick();await p.submit();assert.equal(p.writes.length,1);
 if(switchTo==='order')p.props.orderId=2;else p.user.userInfo.id=2;await tick();
 if(switchTo==='order'){p.reads[1].resolve(detail(2));await tick()}
 const before=p.events.length;if(outcome==='success')p.posts[0].resolve();else p.posts[0].reject(Error('旧请求失败'));await posting;await tick();
 assert.equal(p.events.length,before);assert.equal(p.messages.length,0);assert.equal(p.error.value,'');
 if(switchTo==='order')assert.equal(p.detail.value.id,2);else assert.equal(p.detail.value,null);
})
