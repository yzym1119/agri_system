const test=require('node:test'),assert=require('node:assert/strict');
test('整单列表支持合法51项物料，不把50当业务上限',async()=>{
 const {isPickupOrders}=await import('../dist/pickup.js');
 const lines=Array.from({length:51},(_,n)=>({requestId:String(n+1),productName:'肥料',unit:'袋',remainingQuantity:'1',status:'PENDING',canConfirm:true,blockReason:null,fulfilledAt:null}));
 assert.equal(isPickupOrders({storeId:'3',state:'PENDING',orders:[{orderId:'50',orderNo:'O50',customerLabel:null,pendingCount:51,fulfilledCount:0,lines}],nextCursor:null}),true);
});
test('提货详情保留精确编号和数量，拒绝重复分配、未知单位与完成时伪造余量',async()=>{
 const {isPickupPreview}=await import('../dist/pickup.js');
 const p={storeId:'3',requestId:'9223372036854775807',orderId:'50',orderNo:'ORDER50',productName:'肥料',status:'PENDING',canConfirm:true,blockReason:null,confirmationKey:'a'.repeat(64),fulfilledAt:null,
 allocations:[{id:'1',batchId:'101',internalBatchNo:'BATCH101',locationId:'12',locationName:'后仓',quantity:'1.5',unit:'公斤'}]};
 assert.equal(isPickupPreview(p),true);
 for(const bad of [{...p,requestId:9223372036854775807},{...p,allocations:[...p.allocations,...p.allocations]},{...p,allocations:[{...p.allocations[0],unit:''}]},{...p,status:'FULFILLED'}])assert.equal(isPickupPreview(bad),false);
 assert.equal(isPickupPreview({...p,status:'FULFILLED',canConfirm:false,confirmationKey:null,allocations:[],fulfilledAt:'2026-11-01T10:00'}),true);
});
test('整单清单按原订单聚合：项数是整单口径且必须与行一致',async()=>{
 const {isPickupOrders}=await import('../dist/pickup.js');
 const line={requestId:'42',productName:'肥料',unit:'公斤',remainingQuantity:'1.5',status:'PENDING',canConfirm:true,blockReason:null,fulfilledAt:null};
 const done={requestId:'43',productName:'农药',unit:null,remainingQuantity:null,status:'FULFILLED',canConfirm:false,blockReason:null,fulfilledAt:'2026-11-01T09:00'};
 const page={storeId:'3',state:'PENDING',orders:[{orderId:'50',orderNo:'ORDER50',customerLabel:'张**',pendingCount:1,fulfilledCount:1,lines:[line,done]}],nextCursor:null};
 assert.equal(isPickupOrders(page),true);
 // 项数覆盖整单，已有交付项不能从待交列表里消失，否则会提前显示整单完成。
 assert.equal(isPickupOrders({...page,orders:[{...page.orders[0],pendingCount:2}]}),false);
 assert.equal(isPickupOrders({...page,orders:[{...page.orders[0],fulfilledCount:0}]}),false);
 assert.equal(isPickupOrders({...page,orders:[{...page.orders[0],lines:[line]}]}),false);
});
test('待交页不出现整单已交完的单，历史行不得回填余量或单位',async()=>{
 const {isPickupOrders}=await import('../dist/pickup.js');
 const done={requestId:'43',productName:'农药',unit:null,remainingQuantity:null,status:'FULFILLED',canConfirm:false,blockReason:null,fulfilledAt:'2026-11-01T09:00'};
 const allDone={storeId:'3',state:'PENDING',orders:[{orderId:'50',orderNo:'ORDER50',customerLabel:null,pendingCount:0,fulfilledCount:1,lines:[done]}],nextCursor:null};
 assert.equal(isPickupOrders(allDone),false);
 assert.equal(isPickupOrders({...allDone,state:'FULFILLED'}),true);
 const pending={requestId:'42',productName:'肥料',unit:'公斤',remainingQuantity:'2',status:'PENDING',canConfirm:true,blockReason:null,fulfilledAt:null};
 const one={storeId:'3',state:'PENDING',orders:[{orderId:'50',orderNo:'ORDER50',customerLabel:null,pendingCount:1,fulfilledCount:0,lines:[pending]}],nextCursor:null};
 assert.equal(isPickupOrders(one),true);
 assert.equal(isPickupOrders({...one,orders:[{...one.orders[0],lines:[{...pending,remainingQuantity:null}]}]}),false);
 assert.equal(isPickupOrders({...one,orders:[{...one.orders[0],lines:[{...pending,unit:null}]}]}),false);
 assert.equal(isPickupOrders({...one,orders:[{...one.orders[0],lines:[{...pending,fulfilledAt:'2026-11-01T09:00'}]}]}),false);
});
test('整单清单拒绝跨订单重复请求、非倒序订单、越界游标与超长客户称谓',async()=>{
 const {isPickupOrders}=await import('../dist/pickup.js');
 const line=id=>({requestId:id,productName:'肥料',unit:'公斤',remainingQuantity:'2',status:'PENDING',canConfirm:true,blockReason:null,fulfilledAt:null});
 const order=(id,lineId)=>({orderId:id,orderNo:'ORDER'+id,customerLabel:null,pendingCount:1,fulfilledCount:0,lines:[line(lineId)]});
 const one={storeId:'3',state:'PENDING',orders:[order('50','42')],nextCursor:null};
 assert.equal(isPickupOrders(one),true);
 assert.equal(isPickupOrders({...one,orders:[order('50','42'),order('51','42')]}),false);
 assert.equal(isPickupOrders({...one,orders:[order('50','42'),order('51','43')]}),false);
 assert.equal(isPickupOrders({...one,nextCursor:'50'}),false);
 assert.equal(isPickupOrders({...one,orders:[{...order('50','42'),customerLabel:'张'.repeat(101)}]}),false);
 assert.equal(isPickupOrders({...one,storeId:'0'}),false);
 assert.equal(isPickupOrders({...one,orders:[{...order('50','42'),lines:[]}]}),false);
});
