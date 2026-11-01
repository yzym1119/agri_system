const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue');
function page(overrides={},live=false,storage=new Map()){
 const source=fs.readFileSync(path.join(__dirname,'../src/views/order/components/ReassignDialog.vue'),'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
 const file=ts.createSourceFile('dialog.ts',source,ts.ScriptTarget.Latest,true),body=file.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(file)).join('\n');
 const props=vue.reactive({modelValue:true,orderId:42}),events=[],user=vue.reactive({userInfo:{id:8}}),permissions=vue.reactive({permissions:['order:ship','staff:list']});
 const context={localStorage:{getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},ref:vue.ref,computed:vue.computed,watch:live?vue.watch:()=>{},onUnmounted:()=>{},defineProps:()=>props,defineEmits:()=> (...args)=>events.push(args),useUserStore:()=>user,usePermissionStore:()=>permissions,hasPermission:(p,ps)=>ps.includes(p)||ps.includes('*'),
 getReassignment:async()=>({storeId:'3',orderId:'42',currentStaffId:'9',version:'0',canReassign:true,blockReason:null}),getStaffList:async()=>({records:[{id:10,name:'新配送员',status:1,storeId:3}]}),reassignDelivery:async()=>{},ElMessage:{success(){},warning(){},error(){}},...overrides};
 vm.createContext(context);vm.runInContext(ts.transpileModule(body+'\nglobalThis.page={reload,submit,finishReview,canFinishReview,canSubmit,detail,target,reason,busy,uncertain,message,options};',{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText,context);
 return {...context.page,props,user,permissions,events};
}
test('改派必须读取原版本并填写不同配送员和原因，提交保持原版本字符串',async()=>{
 const calls=[],p=page({reassignDelivery:async(id,body)=>calls.push({id,body})});await p.reload();await p.submit();assert.equal(calls.length,0);
 p.target.value='10';p.reason.value='原配送员请假';await p.submit();assert.equal(calls.length,1);assert.equal(calls[0].body.expectedVersion,'0');assert.equal(calls[0].body.targetStaffId,'10');
});
test('结果未知不再次提交，重读当前版本不能自动认为原请求失败',async()=>{
 const calls=[],p=page({reassignDelivery:async(...args)=>{calls.push(args);throw Error('timeout');}});await p.reload();p.target.value='10';p.reason.value='请假';await p.submit();assert.equal(p.uncertain.value,true);
 await p.reload();p.target.value='10';p.reason.value='新原因';await p.submit();assert.equal(calls.length,1);
});
test('缺员工查询权限不读取员工名单，旧订单响应不回填新对象',async()=>{
 let reads=0,resolve;const p=page({getStaffList:async()=>{reads++;return{records:[]};},getReassignment:()=>new Promise(done=>resolve=done)});p.permissions.permissions=['order:ship'];const old=p.reload();p.props.orderId=43;resolve({storeId:'3',orderId:'42',currentStaffId:'9',version:'0',canReassign:true});await old;assert.equal(p.detail.value,null);assert.equal(reads,0);
});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('首次渲染先读取禁用状态后，异步资料加载和填写仍能启用确认按钮',async()=>{
 const calls=[],p=page({reassignDelivery:async(...args)=>calls.push(args)},true);
 assert.equal(p.canSubmit.value,false);
 await tick();p.target.value='10';p.reason.value='临时换人';
 assert.equal(p.canSubmit.value,true);
 await p.submit();assert.equal(calls.length,1);
});
test('真实watch切订单后旧预览不能覆盖新订单',async()=>{
 const reads=[],p=page({getReassignment:id=>new Promise(resolve=>reads.push({id,resolve}))},true);await tick();
 p.props.orderId=43;await tick();assert.equal(reads.length,2);
 reads[1].resolve({storeId:'3',orderId:'43',currentStaffId:'9',version:'2',canReassign:true,blockReason:null});await tick();
 reads[0].resolve({storeId:'3',orderId:'42',currentStaffId:'9',version:'0',canReassign:true,blockReason:null});await tick();assert.equal(p.detail.value.orderId,'43');
});
test('真实watch撤销权限使挂起提交失效且仍可关闭，不受旧成功污染',async()=>{
 let resolve;const calls=[],p=page({reassignDelivery:(id,body)=>{calls.push({id,body});return new Promise(done=>resolve=done);}},true);await tick();p.target.value='10';p.reason.value='临时调整';const pending=p.submit();await tick();assert.equal(calls.length,1);
 p.permissions.permissions=[];await tick();assert.equal(p.detail.value,null);assert.equal(p.busy.value,false);assert.equal(p.uncertain.value,true);
 resolve();await pending;assert.equal(p.events.length,0);await p.submit();assert.equal(calls.length,1);
});
test('真实watch更换员工清空候选与草稿，旧失败不污染新身份',async()=>{
 let reject;const p=page({reassignDelivery:()=>new Promise((_,fail)=>reject=fail)},true);await tick();p.target.value='10';p.reason.value='临时调整';const old=p.submit();
 p.user.userInfo.id=11;await tick();assert.equal(p.target.value,'');assert.equal(p.reason.value,'');
 reject(Error('旧身份失败'));await old;assert.equal(p.uncertain.value,false);assert.equal(p.message.value,'');
});
test('未知改派关闭重开仍冻结，不能用新版本直接再次提交',async()=>{
 let version='0',calls=0;const p=page({getReassignment:async()=>({storeId:'3',orderId:'42',currentStaffId:version==='0'?'9':'10',version,canReassign:true,blockReason:null}),reassignDelivery:async()=>{calls++;version='1';throw Error('timeout');}},true);
 await tick();p.target.value='10';p.reason.value='换人';await p.submit();assert.equal(p.uncertain.value,true);
 p.props.modelValue=false;await tick();p.props.modelValue=true;await tick();assert.equal(p.uncertain.value,true);
 p.target.value='10';p.reason.value='再次操作';await p.submit();assert.equal(calls,1);
});
test('组件重建仍恢复原版本，只有版本前进才可人工结束旧核对',async()=>{
 const storage=new Map();let version='0';const api={getReassignment:async()=>({storeId:'3',orderId:'42',currentStaffId:'9',version,canReassign:true,blockReason:null}),reassignDelivery:async()=>{throw Error('timeout');}};
 const first=page(api,true,storage);await tick();first.target.value='10';first.reason.value='换人';await first.submit();
 const next=page(api,true,storage);await tick();assert.equal(next.uncertain.value,true);assert.equal(next.canFinishReview.value,false);next.finishReview();assert.equal(storage.size,1);
 version='1';await next.reload();assert.equal(next.canFinishReview.value,true);next.finishReview();await tick();assert.equal(storage.size,0);assert.equal(next.uncertain.value,false);assert.equal(next.target.value,'');
});
test('本机写入失败必须阻止改派提交',async()=>{
 let posts=0;const p=page({localStorage:{getItem:()=>null,setItem:()=>{throw Error('quota');},removeItem:()=>{}},reassignDelivery:async()=>{posts++;}},true);await tick();p.target.value='10';p.reason.value='换人';await p.submit();assert.equal(posts,0);assert.equal(p.uncertain.value,true);
});
