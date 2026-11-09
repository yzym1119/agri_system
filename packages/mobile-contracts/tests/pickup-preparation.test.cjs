const test=require('node:test'),assert=require('node:assert/strict');
const alloc={id:'1',batchId:'101',internalBatchNo:'批次标签',locationId:'12',locationName:'后仓',quantity:'2',unit:'袋'};
const line={requestId:'42',productName:'肥料',productCode:'P1',status:'PENDING',canPrepare:true,blockReason:null,revision:'b'.repeat(64),allocations:[alloc],fulfilledAt:null};
const snapshot={storeId:'3',orderId:'50',orderNo:'O50',customerLabel:'张**',pendingCount:1,fulfilledCount:0,lines:[line]};
const actor={staffId:'8',storeId:'3'};
const load=()=>import('../dist/pickup-preparation.js');
function fixture(storage=new Map()){
 const port={readStorage:key=>storage.get(key)??null,writeStorage:(key,value)=>storage.set(key,value),removeStorage:key=>storage.delete(key)};
 return {storage,port};
}
test('整单备货契约允许20以上物料，拒绝跨项重复分配和伪完成以及交货令牌',async()=>{
 const {isPickupPreparation}=await load();assert.equal(isPickupPreparation(snapshot),true);
 const lines=Array.from({length:51},(_,n)=>({...line,requestId:String(n+1),allocations:[{...alloc,id:String(n+1)}]}));
 assert.equal(isPickupPreparation({...snapshot,pendingCount:51,lines}),true);
 assert.equal(isPickupPreparation({...snapshot,lines:[{...line,confirmationKey:'a'.repeat(64)}]}),false);
 assert.equal(isPickupPreparation({...snapshot,lines:[line,{...line,requestId:'43'}],pendingCount:2}),false);
 assert.equal(isPickupPreparation({...snapshot,fulfilledCount:1}),false);
 assert.equal(isPickupPreparation({...snapshot,lines:[{...line,canPrepare:false,revision:null,allocations:[],blockReason:'售后暂停'}]}),true);
});
test('准备缓存跨实例仅恢复同员工本店同单和相同最新摘要，不保存业务明文',async()=>{
 const {createPickupPreparationProgress}=await load();const f=fixture(),first=createPickupPreparationProgress(f.port,actor,'50');
 first.restore(snapshot);first.save(snapshot,{'42':['1']});
 const restored=createPickupPreparationProgress(f.port,actor,'50').restore(snapshot);assert.deepEqual(restored.checked,{'42':['1']});
 assert.deepEqual(createPickupPreparationProgress(f.port,{staffId:'9',storeId:'3'},'50').restore(snapshot).checked,{});
 assert.throws(()=>createPickupPreparationProgress(f.port,{staffId:'8',storeId:'4'},'50').restore(snapshot));
 const raw=[...f.storage.values()].join('');for(const secret of ['肥料','张','后仓','批次标签','confirmationKey','quantity'])assert.equal(raw.includes(secret),false);
});
test('售后变更完成或暂停使旧准备失效并清理，健康请求仍可恢复',async()=>{
 const {createPickupPreparationProgress}=await load();const f=fixture(),p=createPickupPreparationProgress(f.port,actor,'50');
 const two={...snapshot,pendingCount:2,lines:[line,{...line,requestId:'43',allocations:[{...alloc,id:'2'}]}]};p.restore(two);p.save(two,{'42':['1'],'43':['2']});
 const changed={...two,lines:[{...line,revision:'c'.repeat(64)},two.lines[1]]};assert.deepEqual(p.restore(changed).checked,{'43':['2']});
 assert.deepEqual(createPickupPreparationProgress(f.port,actor,'50').restore(two).checked,{'43':['2']});
 const blocked={...two,lines:[line,{...two.lines[1],canPrepare:false,revision:null,allocations:[],blockReason:'暂停'}]};assert.deepEqual(p.restore(blocked).checked,{});
 const other=fixture(),pending=createPickupPreparationProgress(other.port,actor,'50');pending.restore(snapshot);pending.save(snapshot,{'42':['1']});
 const completed={...snapshot,pendingCount:0,fulfilledCount:1,lines:[{...line,status:'FULFILLED',canPrepare:false,revision:null,allocations:[],fulfilledAt:'2026-11-09T10:00'}]};
 assert.deepEqual(pending.restore(completed).checked,{});assert.deepEqual(createPickupPreparationProgress(other.port,actor,'50').restore(snapshot).checked,{});
});
test('缓存损坏或被另一页面替换不能覆盖，存储异常不伪装保存成功，重置只删准备缓存',async()=>{
 const {createPickupPreparationProgress}=await load();const f=fixture(),p=createPickupPreparationProgress(f.port,actor,'50');p.restore(snapshot);p.save(snapshot,{'42':['1']});
 const key=[...f.storage.keys()][0];f.storage.set('agri.pickup.pending.v1:8:3:42','DO_NOT_TOUCH');f.storage.set(key,'INVALID');assert.throws(()=>p.save(snapshot,{}),/CONFLICT/);assert.throws(()=>p.restore(snapshot));
 p.reset();assert.equal(f.storage.get('agri.pickup.pending.v1:8:3:42'),'DO_NOT_TOUCH');assert.deepEqual(p.restore(snapshot).checked,{});
 f.port.writeStorage=()=>{throw Error('DISK');};assert.throws(()=>p.save(snapshot,{'42':['1']}),/DISK/);
 f.port.readStorage=()=>{throw Error('READ');};assert.throws(()=>p.restore(snapshot),/READ/);
});
test('准备缓存拒绝重复勾选、别请求和别分配，扫码或缓存不能成为交货确认',async()=>{
 const {createPickupPreparationProgress}=await load();const f=fixture(),p=createPickupPreparationProgress(f.port,actor,'50');p.restore(snapshot);
 for(const v of [{'42':['1','1']},{'42':['99']},{'99':['1']}])assert.throws(()=>p.save(snapshot,v));
 assert.equal(f.storage.size,0);
});
