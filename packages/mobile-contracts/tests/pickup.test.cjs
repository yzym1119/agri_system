const test=require('node:test'),assert=require('node:assert/strict');
test('提货详情保留精确编号和数量，拒绝重复分配、未知单位与完成时伪造余量',async()=>{
 const {isPickupPreview,isPickupTasks}=await import('../dist/pickup.js');
 const p={storeId:'3',requestId:'9223372036854775807',orderId:'50',orderNo:'ORDER50',productName:'肥料',status:'PENDING',canConfirm:true,blockReason:null,confirmationKey:'a'.repeat(64),fulfilledAt:null,
 allocations:[{id:'1',batchId:'101',internalBatchNo:'BATCH101',locationId:'12',locationName:'后仓',quantity:'1.5',unit:'公斤'}]};
 assert.equal(isPickupPreview(p),true);
 for(const bad of [{...p,requestId:9223372036854775807},{...p,allocations:[...p.allocations,...p.allocations]},{...p,allocations:[{...p.allocations[0],unit:''}]},{...p,status:'FULFILLED'}])assert.equal(isPickupPreview(bad),false);
 assert.equal(isPickupPreview({...p,status:'FULFILLED',canConfirm:false,confirmationKey:null,allocations:[],fulfilledAt:'2026-11-01T10:00'}),true);
 assert.equal(isPickupTasks({storeId:'3',state:'PENDING',tasks:[],nextCursor:null}),true);
 assert.equal(isPickupTasks({storeId:'3',state:'PENDING',tasks:[],nextCursor:'1'}),false);
});
