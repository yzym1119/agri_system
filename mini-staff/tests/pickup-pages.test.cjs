const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/pickup.js'),ids=require('../../packages/mobile-contracts/dist/ids.js');
const ready={storeId:'3',requestId:'42',orderId:'50',orderNo:'ORDER50',productName:'肥料',status:'PENDING',canConfirm:true,blockReason:null,confirmationKey:'a'.repeat(64),fulfilledAt:null,
 allocations:[{id:'1',batchId:'101',internalBatchNo:'BATCH101',locationId:'12',locationName:'后仓',quantity:'2',unit:'袋'}]};
async function fixture(permissions=['order:pickup:verify'],storage=new Map()){
 const user={id:'8',name:'库管',permissions:['order:pickup:verify'],extra:{storeId:'3'}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));await auth.login('x','y');
 user.permissions=permissions;
 let preview=structuredClone(ready),fail=false,scan={status:'SCANNED',code:'BATCH101',scanType:'qrCode'};const calls=[];
 const api={readPickupPreview:async()=>typeof preview==='function'?preview():structuredClone(preview),confirmPickup:async(id,key,allocations)=>{calls.push({id,key,allocations});if(typeof fail==='function')return fail();if(fail)throw Error('NETWORK_FAILED');preview={...preview,status:'FULFILLED',canConfirm:false,confirmationKey:null,allocations:[],fulfilledAt:'2026-11-01T10:00'};}};
 const p=setupPage('pickup/detail',auth,true,{'../../../services/auth':{staffAuth:auth},'../../../services/pickup':api,'@agri/mobile-contracts/pickup':contracts,'@agri/mobile-contracts/ids':ids,'../../../services/platform':{mobilePlatform:{scan:async()=>scan,readStorage:key=>storage.get(key)??null,writeStorage:(key,value)=>storage.set(key,value),removeStorage:key=>storage.delete(key)}}});
 p.hooks.load({requestId:'42'});return {p,auth,calls,setPreview:v=>preview=v,setFail:v=>fail=v,setScan:v=>scan=v};
}
test('提货逐批及实际交付双重确认后才发送，成功读取原完成事实',async()=>{
 const f=await fixture();await f.p.hooks.show();await f.p.state.confirm();assert.equal(f.calls.length,0);
 f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();assert.equal(f.calls.length,1);assert.equal(f.calls[0].key,'a'.repeat(64));assert.equal(f.p.state.preview.value.status,'FULFILLED');
 await f.p.state.confirm();assert.equal(f.calls.length,1);
});
test('结果未知保留原key，隐藏后重读新数量也不能换key再交',async()=>{
 const f=await fixture();await f.p.hooks.show();f.setFail(true);f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();
 assert.equal(f.p.state.uncertain.value,true);f.p.hooks.hide();assert.equal(f.p.state.preview.value,null);
 f.setPreview({...ready,confirmationKey:'b'.repeat(64)});await f.p.hooks.show();f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();assert.equal(f.calls.length,1);
});
test('扫描只定位已加载真实批次，不自动确认或改变数量',async()=>{
 const f=await fixture();await f.p.hooks.show();await f.p.state.scan();assert.match(f.p.state.message.value,/后仓/);assert.equal(f.p.state.checked.value.length,0);assert.equal(f.calls.length,0);
 f.setScan({status:'SCANNED',code:'SKU101',scanType:'barCode'});await f.p.state.scan();assert.match(f.p.state.message.value,/不属于/);assert.equal(f.p.state.preview.value.allocations[0].quantity,'2');
});
test('提货列表按当前权限读取并使用原请求编号打开详情，隐藏清空任务',async()=>{
 const f=await fixture(),calls=[];
 const p=setupPage('pickup/list',f.auth,true,{'../../../services/auth':{staffAuth:f.auth},'../../../services/pickup':{readPickupTasks:async(state,after)=>{calls.push({state,after});return {storeId:'3',state,tasks:[{requestId:'42',orderNo:'ORDER50',productName:'肥料',status:state,createdAt:null,fulfilledAt:null}],nextCursor:null};}}});
 await p.hooks.show();assert.equal(calls.length,1);p.state.open(p.state.tasks.value[0]);assert.equal(p.routes[0],'/pages/pickup/detail/index?requestId=42');
 p.hooks.hide();assert.equal(p.state.tasks.value.length,0);
});
test('库存查询权限不能替代提货确认权限，缺权或售后暂停不能提交',async()=>{
 const denied=await fixture(['inventory:view']);await denied.p.hooks.show();assert.equal(denied.p.state.preview.value,null);assert.match(denied.p.state.message.value,/权限/);
 const f=await fixture();f.setPreview({...ready,canConfirm:false,confirmationKey:null,allocations:[],blockReason:'售后暂停'});await f.p.hooks.show();f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();assert.equal(f.calls.length,0);
});
test('同一批次两个货位必须逐分配核对，重复勾选与扫描不能补足遗漏',async()=>{
 const f=await fixture();f.setPreview({...ready,allocations:[ready.allocations[0],{...ready.allocations[0],id:'2',locationId:'13',locationName:'货架'}]});await f.p.hooks.show();
 await f.p.state.scan();assert.match(f.p.state.message.value,/后仓.*货架/);f.p.state.handedOver.value=true;f.p.state.checked.value=['1','1'];await f.p.state.confirm();assert.equal(f.calls.length,0);
 f.p.state.checked.value=['1','2'];await f.p.state.confirm();assert.equal(f.calls.length,1);assert.deepEqual(Array.from(f.calls[0].allocations),['1','2']);
});
test('重复点击不并发提交，隐藏后的旧确认结果不能清除新页面资料',async()=>{
 const f=await fixture();await f.p.hooks.show();let resolve;f.setFail(()=>new Promise(done=>resolve=done));f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;
 const pending=f.p.state.confirm();await f.p.state.confirm();assert.equal(f.calls.length,1);f.p.hooks.hide();await f.p.hooks.show();resolve();await pending;
 assert.equal(f.p.state.preview.value.status,'PENDING');assert.equal(f.p.state.uncertain.value,true);assert.equal(f.p.state.checked.value.length,0);
});
test('旧读取迟到不覆盖返回前台后的新数量，退出身份禁止原页提交',async()=>{
 const f=await fixture();let resolve;f.setPreview(()=>new Promise(done=>resolve=done));const old=f.p.hooks.show();await new Promise(done=>setImmediate(done));
 f.p.hooks.hide();f.setPreview({...ready,confirmationKey:'b'.repeat(64)});await f.p.hooks.show();resolve(ready);await old;
 assert.equal(f.p.state.preview.value.confirmationKey,'b'.repeat(64));await f.auth.logout();f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();assert.equal(f.calls.length,0);
});
test('未知请求销毁页面再进入仍保留原摘要，不能用新数量再次交货',async()=>{
 const storage=new Map(),first=await fixture(undefined,storage);await first.p.hooks.show();first.setFail(true);first.p.state.checked.value=['1'];first.p.state.handedOver.value=true;await first.p.state.confirm();first.p.hooks.hide();
 const fresh=await fixture(undefined,storage);fresh.setPreview({...ready,confirmationKey:'b'.repeat(64)});await fresh.p.hooks.show();
 assert.equal(fresh.p.state.uncertain.value,true);fresh.p.state.checked.value=['1'];fresh.p.state.handedOver.value=true;await fresh.p.state.confirm();assert.equal(fresh.calls.length,0);
});
test('本机记录保存失败或被替换时不能提交，完成后的删除失败不能冒称已清理',async()=>{
 const broken=new Map();broken.set=()=>{throw Error('NO_STORAGE');};const first=await fixture(undefined,broken);await first.p.hooks.show();first.p.state.checked.value=['1'];first.p.state.handedOver.value=true;await first.p.state.confirm();assert.equal(first.calls.length,0);assert.equal(first.p.state.uncertain.value,true);
 const storage=new Map(),f=await fixture(undefined,storage);await f.p.hooks.show();storage.delete=()=>{throw Error('DELETE_FAILED');};f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();assert.equal(f.calls.length,1);assert.equal(f.p.state.preview.value.status,'FULFILLED');assert.equal(f.p.state.uncertain.value,true);assert.equal(storage.size,1);
});
test('隐藏期间合法原记录被替换，不能按新批次数量再次提货',async()=>{
 const storage=new Map(),f=await fixture(undefined,storage);await f.p.hooks.show();f.setFail(true);f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();f.p.hooks.hide();
 const key=[...storage.keys()][0],record=JSON.parse(storage.get(key));record.confirmationKey='b'.repeat(64);storage.set(key,JSON.stringify(record));f.setPreview({...ready,confirmationKey:'b'.repeat(64)});
 for(let i=0;i<2;i++){await f.p.hooks.show();f.p.state.checked.value=['1'];f.p.state.handedOver.value=true;await f.p.state.confirm();f.p.hooks.hide();}
 assert.equal(f.calls.length,1);assert.equal(f.p.state.uncertain.value,true);
});

test('提货列表读取失败不呈现成功空记录，重试后才建立空结果状态',async()=>{
 const f=await fixture();let fail=true;
 const p=setupPage('pickup/list',f.auth,true,{'../../../services/auth':{staffAuth:f.auth},'../../../services/pickup':{readPickupTasks:async state=>{if(fail)throw Error('offline');return{storeId:'3',state,tasks:[],nextCursor:null};}}});
 await p.hooks.show();assert.equal(p.state.loaded.value,false);assert.match(p.state.message.value,/未完成/);
 fail=false;await p.state.load(null);assert.equal(p.state.loaded.value,true);assert.equal(p.state.tasks.value.length,0);
});
