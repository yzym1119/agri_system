const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/fulfillment.js'),ids=require('../../packages/mobile-contracts/dist/ids.js');
const ready={storeId:'3',orderId:'42',orderNo:'TEST42',status:'PENDING',canConfirm:true,blockReason:null,receiverName:'虚构收货人',receiverPhone:'13800000000',receiverAddress:'测试地址',confirmationKey:'a'.repeat(64),items:[{orderItemId:'1',productName:'肥料',quantity:'2',unit:'袋'}],receivedAt:null};
const done={storeId:'3',orderId:'42',status:'COMPLETED',receivedAt:'2026-10-30T12:00:00'};
async function fixture(page='delivery/detail',permissions=['order:delivery:assigned'],storage=new Map()) {
 const user={id:'8',name:'虚构配送员',permissions,extra:{storeId:'3'}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));await auth.login('test','pass');
 let preview=structuredClone(ready),confirmation=done,listResult;
 const calls={reads:0,confirm:[],list:[]};
 const api={readAssignedReceipt:async()=>{calls.reads++;return typeof preview==='function'?preview():structuredClone(preview);},
  confirmAssignedReceipt:async(id,key)=>{calls.confirm.push({id,key});return typeof confirmation==='function'?confirmation():confirmation;},
  readAssignedTasks:async(state,after)=>{calls.list.push({state,after});return listResult?listResult(state,after):{storeId:'3',state,tasks:[{orderId:'42',orderNo:'TEST42',status:state,deliveryAt:null,receivedAt:null}],nextCursor:null};}};
 const p=setupPage(page,auth,true,{'../../../services/auth':{staffAuth:auth},'../../../services/fulfillment':api,
  '../../../services/platform':{mobilePlatform:{readStorage:key=>storage.get(key)??null,writeStorage:(key,value)=>storage.set(key,value),removeStorage:key=>storage.delete(key),navigateToAddress:async address=>({status:'ADDRESS_ONLY',address})}},
  '@agri/mobile-contracts/fulfillment':contracts,'@agri/mobile-contracts/ids':ids});
 return {p,auth,calls,storage,setUser:v=>Object.assign(user,v),setPreview:v=>preview=v,setConfirmation:v=>confirmation=v,setList:v=>listResult=v};
}
function check(f){f.p.state.checkedItems.value=['1:袋'];f.p.state.receiverConfirmed.value=true;}
test('配送员逐项核对商品与收货人后才确认，成功后清联系人且不二次提交',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();await f.p.state.confirm();assert.equal(f.calls.confirm.length,0);
 check(f);await f.p.state.confirm();assert.deepEqual(f.calls.confirm,[{id:'42',key:'a'.repeat(64)}]);assert.equal(f.p.state.result.value.status,'COMPLETED');assert.equal(f.p.state.preview.value,null);
 await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
});
test('有整店发货权限不能替代本人配送权限；暂停和完成不开放确认',async()=>{
 const denied=await fixture('delivery/detail',['order:ship']);denied.p.hooks.load({orderId:'42'});await denied.p.hooks.show();assert.equal(denied.calls.reads,0);assert.match(denied.p.state.message.value,/权限/);
 const f=await fixture();f.p.hooks.load({orderId:'42'});f.setPreview({...ready,canConfirm:false,blockReason:'售后暂停',receiverName:null,receiverPhone:null,receiverAddress:null,confirmationKey:null,items:[]});
 await f.p.hooks.show();check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,0);
});
test('签收响应未知先核对同任务，不能自动换新key，原key明确重试仍只确认同单',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);f.setConfirmation(()=>{throw Error('RESULT_UNKNOWN');});await f.p.state.confirm();
 assert.equal(f.p.state.uncertain.value,true);assert.equal(f.p.state.receiverConfirmed.value,false);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
 f.setPreview({...ready,confirmationKey:'b'.repeat(64)});await f.p.state.readCurrent();check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
 f.setPreview(ready);await f.p.state.readCurrent();check(f);f.setConfirmation(done);await f.p.state.confirm();assert.equal(f.calls.confirm.length,2);assert.equal(f.calls.confirm[1].key,'a'.repeat(64));
});
test('旧详情与旧确认响应不污染新前台，隐藏立即清联系人和勾选',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);let resolve;
 f.setConfirmation(()=>new Promise(done=>resolve=done));const pending=f.p.state.confirm();f.p.hooks.hide();assert.equal(f.p.state.preview.value,null);assert.equal(f.p.state.receiverConfirmed.value,false);
 await f.p.hooks.show();resolve(done);await pending;assert.equal(f.p.state.result.value,null);assert.equal(f.p.state.preview.value.orderId,'42');
});
test('明确拒绝后必须重读并重新核对，未知之后的拒绝不能抹掉未知状态',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);f.setConfirmation(()=>{throw Error('BUSINESS_REJECTED');});await f.p.state.confirm();
 assert.equal(f.p.state.preview.value,null);assert.equal(f.p.state.uncertain.value,false);await f.p.state.readCurrent();assert.equal(f.p.state.checkedItems.value.length,0);
});
test('任务列表切状态使旧查询失效，没有权限不读取名单',async()=>{
 const f=await fixture('delivery/list');let resolve;f.setList(state=>state==='PENDING'?new Promise(done=>resolve=done):{storeId:'3',state,tasks:[],nextCursor:null});
 const first=f.p.hooks.show();await new Promise(r=>setTimeout(r,0));await f.p.state.chooseState('COMPLETED');resolve({storeId:'3',state:'PENDING',tasks:[{orderId:'42',orderNo:'OLD',status:'PENDING',deliveryAt:null,receivedAt:null}],nextCursor:null});await first;
 assert.equal(f.p.state.state.value,'COMPLETED');assert.equal(f.p.state.tasks.value.length,0);
 const denied=await fixture('delivery/list',[]);await denied.p.hooks.show();assert.equal(denied.calls.list.length,0);
});
test('没有坐标时保留文字地址并允许人工复制，不猜目的地坐标',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();await f.p.state.showNavigation();assert.match(f.p.state.message.value,/地图/);
 f.p.state.copyAddress();assert.deepEqual(f.p.clipboard,['测试地址']);assert.equal(f.p.state.preview.value.receiverAddress,'测试地址');
});

async function unknown(storage=new Map()){
 const f=await fixture(undefined,undefined,storage);f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);
 f.setConfirmation(()=>{throw Error('RESULT_UNKNOWN');});await f.p.state.confirm();return f;
}
test('丢响应后隐藏重进且内容已变，不可换新key签收',async()=>{
 const f=await unknown();f.p.hooks.hide();f.setPreview({...ready,confirmationKey:'b'.repeat(64)});await f.p.hooks.show();
 assert.equal(f.p.state.uncertain.value,true);check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
});
test('销毁重建仍恢复原签收摘要，记录不含联系人',async()=>{
 const storage=new Map(),first=await unknown(storage);first.p.hooks.hide();assert.equal(storage.size,1);assert.doesNotMatch([...storage.values()].join(''),/13800000000|测试地址|虚构收货人/);
 const fresh=await fixture(undefined,undefined,storage);fresh.p.hooks.load({orderId:'42'});fresh.setPreview({...ready,confirmationKey:'b'.repeat(64)});await fresh.p.hooks.show();
 assert.equal(fresh.p.state.uncertain.value,true);check(fresh);await fresh.p.state.confirm();assert.equal(fresh.calls.confirm.length,0);
});
test('发送前已保存原记录，挂起离页可恢复且旧响应不清记录',async()=>{
 const storage=new Map(),f=await fixture(undefined,undefined,storage);f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);let resolve;
 f.setConfirmation(()=>new Promise(done=>resolve=done));const pending=f.p.state.confirm();assert.equal(storage.size,1);f.p.hooks.hide();
 const fresh=await fixture(undefined,undefined,storage);fresh.p.hooks.load({orderId:'42'});await fresh.p.hooks.show();assert.equal(fresh.p.state.uncertain.value,true);
 resolve(done);await pending;assert.equal(storage.size,1);assert.equal(fresh.p.state.result.value,null);
});
test('存储保存失败停止签收，不发送未保存的请求',async()=>{
 const storage=new Map();storage.set=()=>{throw Error('DISK_FAILED');};const f=await fixture(undefined,undefined,storage);f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);await f.p.state.confirm();
 assert.equal(f.calls.confirm.length,0);assert.equal(f.p.state.uncertain.value,true);
});
test('未知后重试明确拒绝仍保留原记录，读取完成后才清理',async()=>{
 const f=await unknown();await f.p.state.readCurrent();check(f);f.setConfirmation(()=>{throw Error('BUSINESS_REJECTED');});await f.p.state.confirm();assert.equal(f.p.state.uncertain.value,true);assert.equal(f.storage.size,1);
 f.setPreview({...ready,status:'COMPLETED',canConfirm:false,confirmationKey:null,items:[]});await f.p.state.readCurrent();assert.equal(f.p.state.uncertain.value,false);assert.equal(f.storage.size,0);
});
test('完成后清理失败仍提示核对且不能再次签收',async()=>{
 const storage=new Map(),f=await fixture(undefined,undefined,storage);storage.delete=()=>{throw Error('DELETE_FAILED');};f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);await f.p.state.confirm();
 assert.equal(f.p.state.uncertain.value,true);assert.equal(storage.size,1);check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
});
test('另一员工不读取或清除原员工记录',async()=>{
 const storage=new Map();await unknown(storage);const other=await fixture(undefined,undefined,storage);other.setUser({id:'9'});other.p.hooks.load({orderId:'42'});await other.p.hooks.show();assert.equal(other.p.state.uncertain.value,false);assert.equal(storage.size,1);
});
test('恢复记录损坏、读取失败或被替换均冻结，不能悄悄重发',async()=>{
 for(const mode of ['corrupt','read-failed','replaced','removed']){
  const storage=new Map(),f=await unknown(storage);f.p.hooks.hide();const key=[...storage.keys()][0];
  if(mode==='corrupt')storage.set(key,'{bad');
  if(mode==='read-failed')storage.get=()=>{throw Error('READ_FAILED');};
  await f.p.hooks.show();
  if(mode==='replaced')storage.set(key,'changed');
  if(mode==='removed')storage.delete(key);
  check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1,mode);assert.equal(f.p.state.uncertain.value,true,mode);
 }
});
test('首次明确拒绝清理原记录，完成记录被替换时保留冻结',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);f.setConfirmation(()=>{throw Error('BUSINESS_REJECTED');});await f.p.state.confirm();assert.equal(f.storage.size,0);assert.equal(f.p.state.uncertain.value,false);
 const g=await unknown();g.storage.set([...g.storage.keys()][0],'changed');g.setPreview({...ready,status:'COMPLETED',canConfirm:false,confirmationKey:null,items:[]});await g.p.state.readCurrent();assert.equal(g.p.state.uncertain.value,true);assert.equal(g.storage.size,1);
});
test('其他门店和订单不消费原记录，返回原身份仍可恢复',async()=>{
 const storage=new Map();await unknown(storage);
 for(const mode of ['store','order']){
  const f=await fixture(undefined,undefined,storage);if(mode==='store')f.setUser({extra:{storeId:'4'}});f.p.hooks.load({orderId:mode==='order'?'43':'42'});await f.p.hooks.show();assert.equal(f.p.state.uncertain.value,false);assert.equal(storage.size,1);
 }
 const original=await fixture(undefined,undefined,storage);original.p.hooks.load({orderId:'42'});await original.p.hooks.show();assert.equal(original.p.state.uncertain.value,true);
});
test('切换登录后旧签收拒绝响应不能清除原待核对记录',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'42'});await f.p.hooks.show();check(f);let reject;
 f.setConfirmation(()=>new Promise((_,fail)=>reject=fail));const pending=f.p.state.confirm();
 await f.auth.logout();await f.auth.login('again','pass');reject(Error('BUSINESS_REJECTED'));await pending;
 assert.equal(f.storage.size,1);assert.equal(f.p.state.uncertain.value,true);
});
test('隐藏期间同scope合法记录被替换也不能接受新key',async()=>{
 const f=await unknown();f.p.hooks.hide();const key=[...f.storage.keys()][0],replacement=JSON.parse(f.storage.get(key));replacement.confirmationKey='b'.repeat(64);f.storage.set(key,JSON.stringify(replacement));f.setPreview({...ready,confirmationKey:'b'.repeat(64)});
 await f.p.hooks.show();check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
 f.p.hooks.hide();await f.p.hooks.show();check(f);await f.p.state.confirm();assert.equal(f.calls.confirm.length,1);
});
