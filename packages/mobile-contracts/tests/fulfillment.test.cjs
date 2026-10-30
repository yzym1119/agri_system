const test=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/fulfillment.js');
const ready={storeId:'3',orderId:'42',orderNo:'TEST42',status:'PENDING',canConfirm:true,blockReason:null,receiverName:'虚构收货人',receiverPhone:'13800000000',receiverAddress:'本地测试地址',confirmationKey:'a'.repeat(64),items:[{orderItemId:'1',productName:'肥料',quantity:'2',unit:'袋'}],receivedAt:null};
test('本人配送预览严格区分可交、暂停及完成，完成不泄露联系人或猜历史数量',async()=>{
 const {isAssignedReceipt}=await load();assert.equal(isAssignedReceipt(ready),true);
 assert.equal(isAssignedReceipt({...ready,canConfirm:false,blockReason:'售后暂停',receiverName:null,receiverPhone:null,receiverAddress:null,confirmationKey:null,items:[]}),true);
 assert.equal(isAssignedReceipt({...ready,canConfirm:false,blockReason:'暂停'}),false);
 const done={...ready,status:'COMPLETED',canConfirm:false,receiverName:null,receiverPhone:null,receiverAddress:null,confirmationKey:null,items:[],receivedAt:'2026-10-30T12:00:00'};
 assert.equal(isAssignedReceipt(done),true);assert.equal(isAssignedReceipt({...done,items:ready.items}),false);assert.equal(isAssignedReceipt({...done,receiverPhone:ready.receiverPhone}),false);
});
test('配送编号和基准数量保持字符串，旧包装、空key、负量及重复物料拒绝',async()=>{
 const {isAssignedReceipt}=await load();
 assert.equal(isAssignedReceipt({...ready,orderId:'9223372036854775807'}),true);assert.equal(isAssignedReceipt({...ready,orderId:9007199254740992}),false);
 assert.equal(isAssignedReceipt({code:'200',data:ready}),false);assert.equal(isAssignedReceipt({...ready,confirmationKey:''}),false);
 assert.equal(isAssignedReceipt({...ready,items:[{...ready.items[0],quantity:'-1'}]}),false);
 assert.equal(isAssignedReceipt({...ready,items:[ready.items[0],ready.items[0]]}),false);
});
test('任务游标须按同状态排序且绑定最后一项，结果必须是同订单完成事实',async()=>{
 const {isAssignedTasks,isDeliveryResult}=await load();const tasks=Array.from({length:20},(_,i)=>({orderId:String(30-i),orderNo:'TEST'+(30-i),status:'PENDING',deliveryAt:null,receivedAt:null}));
 const page={storeId:'3',state:'PENDING',tasks,nextCursor:'11'};assert.equal(isAssignedTasks(page),true);
 assert.equal(isAssignedTasks({...page,nextCursor:'12'}),false);assert.equal(isAssignedTasks({...page,state:'COMPLETED'}),false);
 assert.equal(isDeliveryResult({storeId:'3',orderId:'42',status:'COMPLETED',receivedAt:null}),true);
 assert.equal(isDeliveryResult({storeId:'3',orderId:'42',status:'UNKNOWN',receivedAt:null}),false);
});
