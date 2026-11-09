const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage,setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/pickup-preparation.js'),ids=require('../../packages/mobile-contracts/dist/ids.js');
const allocation={id:'1',batchId:'101',internalBatchNo:'B101',locationId:'12',locationName:'后仓',quantity:'2',unit:'袋'};
const line={requestId:'42',productName:'肥料',productCode:'P1',status:'PENDING',canPrepare:true,blockReason:null,revision:'b'.repeat(64),allocations:[allocation],fulfilledAt:null};
const snapshot={storeId:'3',orderId:'50',orderNo:'O50',customerLabel:'张**',pendingCount:1,fulfilledCount:0,lines:[line]};
async function fixture(storage=new Map(),permissions=['order:pickup:verify']){
 const user={id:'8',name:'库管',permissions,extra:{storeId:'3'}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'TEST',userInfo:user}:user}));await auth.login('test','test');
 let answer=structuredClone(snapshot),scan={status:'SCANNED',code:'B101'},calls=0;
 const platform={readStorage:key=>storage.get(key)??null,writeStorage:(key,value)=>storage.set(key,value),removeStorage:key=>storage.delete(key),scan:async()=>typeof scan==='function'?scan():scan};
 const p=setupPage('pickup/order',auth,true,{'../../../services/auth':{staffAuth:auth},'../../../services/pickup':{readPickupPreparation:async()=>{calls++;return typeof answer==='function'?answer():structuredClone(answer);},confirmPickup:()=>{throw Error('preparation must never confirm');}},'../../../services/platform':{mobilePlatform:platform},'@agri/mobile-contracts/pickup-preparation':contracts,'@agri/mobile-contracts/ids':ids});
 p.hooks.load({orderId:'50'});return {p,auth,storage,platform,calls:()=>calls,set:v=>answer=v,setScan:v=>scan=v};
}
test('整单备货勾选只存个人编号摘要，跨进程读取最新证据才恢复，变更项失效',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.setAllocations('42',{detail:{value:['1']}});assert.equal(f.p.state.readyCount.value,1);assert.equal(f.p.state.preparation.value.fulfilledCount,0);
 f.p.hooks.hide();const second=await fixture(f.storage);await second.p.hooks.show();assert.equal(second.p.state.readyCount.value,1);
 second.p.hooks.hide();second.set({...snapshot,lines:[{...line,revision:'c'.repeat(64)}]});await second.p.hooks.show();assert.equal(second.p.state.readyCount.value,0);
 const raw=[...f.storage.values()].join('');assert.doesNotMatch(raw,/肥料|张|后仓|B101|confirmationKey|quantity/);
});
test('未备好筛选不漏暂停项，整单备好不当成已交，原交货仍逐项打开',async()=>{
 const f=await fixture();f.set({...snapshot,pendingCount:2,lines:[line,{...line,requestId:'43',canPrepare:false,revision:null,allocations:[],blockReason:'售后暂停'}]});await f.p.hooks.show();
 f.p.state.onlyUnchecked.value=true;f.p.state.setAllocations('42',{detail:{value:['1']}});assert.deepEqual(Array.from(f.p.state.visibleLines.value,l=>l.requestId),['43']);
 f.p.state.open(f.p.state.preparation.value.lines[0]);assert.deepEqual(f.p.routes,['/pages/pickup/detail/index?requestId=42']);
});
test('扫码同一批次多货位只定位候选，不自动勾选和确认，迟到扫码不可污染重进页',async()=>{
 const f=await fixture();f.set({...snapshot,pendingCount:2,lines:[line,{...line,requestId:'43',allocations:[{...allocation,id:'2',locationId:'13',locationName:'货架'}]}]});await f.p.hooks.show();await f.p.state.scan();
 assert.equal(f.p.state.scanMatches.value.length,2);assert.equal(f.p.state.readyCount.value,0);assert.equal(f.p.routes.length,0);assert.equal(f.storage.size,0);
 let done;f.setScan(()=>new Promise(resolve=>done=resolve));const pending=f.p.state.scan();f.p.hooks.hide();await f.p.hooks.show();done({status:'SCANNED',code:'P1'});await pending;assert.equal(f.p.state.scanMatches.value.length,0);
});
test('原交货未知必须先核原请求，清备货缓存不能删除pending或放行其他项',async()=>{
 const storage=new Map([['agri.pickup.pending.v1:8:3:42','ORIGINAL']]),f=await fixture(storage);f.set({...snapshot,pendingCount:2,lines:[line,{...line,requestId:'43',allocations:[{...allocation,id:'2'}]}]});await f.p.hooks.show();
 f.p.state.open(f.p.state.preparation.value.lines[1]);assert.equal(f.p.routes.length,0);assert.match(f.p.state.message.value,/先核对/);
 f.p.state.resetProgress();assert.equal(storage.get('agri.pickup.pending.v1:8:3:42'),'ORIGINAL');f.p.state.open(f.p.state.preparation.value.lines[1]);assert.equal(f.p.routes.length,0);
 f.p.state.open(f.p.state.preparation.value.lines[0]);assert.deepEqual(f.p.routes,['/pages/pickup/detail/index?requestId=42']);
});
test('无权不取数、存储损坏不猜准备成功、旧读取不覆盖新会话或隐藏页',async()=>{
 const denied=await fixture(new Map(),[]);await denied.p.hooks.show();assert.equal(denied.calls(),0);
 const broken=await fixture(new Map([['agri.pickup.preparation.v1:8:3:50','BAD']]));await broken.p.hooks.show();assert.equal(broken.p.state.readyCount.value,0);assert.match(broken.p.state.storageMessage.value,/未能/);
 broken.p.state.resetProgress();broken.p.state.setAllocations('42',{detail:{value:['1']}});assert.equal(broken.p.state.readyCount.value,1);
 const f=await fixture();let done;f.set(()=>new Promise(resolve=>done=resolve));const pending=f.p.hooks.show();await new Promise(r=>setTimeout(r,0));f.p.hooks.hide();await f.auth.logout();done(snapshot);await pending;assert.equal(f.p.state.preparation.value,null);assert.equal(f.p.routes.length,0);
});
test('备货写入失败不显示已保存、也不发送业务请求',async()=>{
 const f=await fixture();await f.p.hooks.show();f.platform.writeStorage=()=>{throw Error('DISK');};f.p.state.setAllocations('42',{detail:{value:['1']}});assert.equal(f.p.state.readyCount.value,0);assert.match(f.p.state.storageMessage.value,/未能/);assert.equal(f.p.routes.length,0);
});
test('整单列表进入备货页仍保留逐项原交货入口',async()=>{
 const f=await fixture();const order={orderId:'50',orderNo:'O50',customerLabel:null,pendingCount:1,fulfilledCount:0,lines:[{requestId:'42',productName:'肥料',unit:'袋',remainingQuantity:'2',status:'PENDING',canConfirm:true,blockReason:null,fulfilledAt:null}]};
 const p=setupPage('pickup/list',f.auth,true,{'../../../services/auth':{staffAuth:f.auth},'../../../services/pickup':{readPickupOrders:async()=>({storeId:'3',state:'PENDING',orders:[order],nextCursor:null})}});
 await p.hooks.show();p.state.openOrder(p.state.orders.value[0]);assert.equal(p.routes[0],'/pages/pickup/order/index?orderId=50');
 p.state.open(p.state.orders.value[0].lines[0]);assert.equal(p.routes[1],'/pages/pickup/detail/index?requestId=42');
});
test('整单备货真实服务只读且核对门店和对象，旧401不能清掉新账号',async()=>{
 const user={id:'8',name:'库管',permissions:['order:pickup:verify'],extra:{storeId:'3'}};let answer=snapshot,last;
 const auth=createStaffAuth(async req=>{if(req.url.endsWith('/login'))return {statusCode:200,data:{tokenName:'Authorization',tokenValue:'TEST',userInfo:user}};last=req;return typeof answer==='function'?answer(req):{statusCode:200,data:answer};});await auth.login('test','test');
 const service=setupService('pickup',{'./auth':{staffAuth:auth},'@agri/mobile-contracts/pickup':require('../../packages/mobile-contracts/dist/pickup.js'),'@agri/mobile-contracts/pickup-preparation':contracts,'@agri/mobile-contracts/ids':ids});const epoch=auth.current().epoch;
 await service.readPickupPreparation('50',epoch);assert.equal(last.method,'GET');assert.equal(last.url,'/api/admin/mobile/pickup/orders/50');assert.equal(last.data,undefined);
 answer={...snapshot,storeId:'4'};await assert.rejects(service.readPickupPreparation('50',epoch),/STORE_MISMATCH/);
 answer={...snapshot,orderId:'51'};await assert.rejects(service.readPickupPreparation('50',epoch),/RESPONSE_INVALID/);
 let done;answer=()=>new Promise(resolve=>done=resolve);const pending=service.readPickupPreparation('50',epoch);await auth.login('other','test');done({statusCode:401,data:null});await assert.rejects(pending,/STALE_SESSION/);assert.ok(auth.current());
});
