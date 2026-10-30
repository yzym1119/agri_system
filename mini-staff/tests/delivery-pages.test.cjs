const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/fulfillment.js'),ids=require('../../packages/mobile-contracts/dist/ids.js');
const ready={storeId:'3',orderId:'42',orderNo:'TEST42',status:'PENDING',canConfirm:true,blockReason:null,receiverName:'虚构收货人',receiverPhone:'13800000000',receiverAddress:'测试地址',confirmationKey:'a'.repeat(64),items:[{orderItemId:'1',productName:'肥料',quantity:'2',unit:'袋'}],receivedAt:null};
const done={storeId:'3',orderId:'42',status:'COMPLETED',receivedAt:'2026-10-30T12:00:00'};
async function fixture(page='delivery/detail',permissions=['order:delivery:assigned']) {
 const user={id:'8',name:'虚构配送员',permissions,extra:{storeId:'3'}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));await auth.login('test','pass');
 let preview=structuredClone(ready),confirmation=done,listResult;
 const calls={reads:0,confirm:[],list:[]};
 const api={readAssignedReceipt:async()=>{calls.reads++;return typeof preview==='function'?preview():structuredClone(preview);},
  confirmAssignedReceipt:async(id,key)=>{calls.confirm.push({id,key});return typeof confirmation==='function'?confirmation():confirmation;},
  readAssignedTasks:async(state,after)=>{calls.list.push({state,after});return listResult?listResult(state,after):{storeId:'3',state,tasks:[{orderId:'42',orderNo:'TEST42',status:state,deliveryAt:null,receivedAt:null}],nextCursor:null};}};
 const p=setupPage(page,auth,true,{'../../../services/auth':{staffAuth:auth},'../../../services/fulfillment':api,
  '../../../services/platform':{mobilePlatform:{navigateToAddress:async address=>({status:'ADDRESS_ONLY',address})}},
  '@agri/mobile-contracts/fulfillment':contracts,'@agri/mobile-contracts/ids':ids});
 return {p,auth,calls,setPreview:v=>preview=v,setConfirmation:v=>confirmation=v,setList:v=>listResult=v};
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
