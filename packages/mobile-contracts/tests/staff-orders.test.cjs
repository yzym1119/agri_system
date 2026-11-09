const test=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/staff-orders.js');
const finance={payAmount:'65',receivedAmount:'25',creditAmount:'40',creditReductionAmount:'10',cashRefundedAmount:'0',remainingCreditAmount:null,repaidAmount:'5'};
const order={orderId:'9223372036854775807',orderNo:'O50',createdAt:'2026-11-09T12:00',state:9,customerLabel:'张**',finance,deliveryType:null,deliveryStatus:null};
const item={itemId:'1',productName:'肥料',quantity:'2',unit:'袋',totalPrice:'25',refundedQuantity:'1',refundedAmount:'10',packageGroupNo:1,packageName:'原套餐',packageQuantity:'1',packageUnitPrice:'65'};
test('本人订单裸契约保留Long和未知资金，不接受数字ID或支付写令牌',async()=>{
 const {isOwnOrders,isOwnOrderDetail}=await load();
 assert.equal(isOwnOrders({storeId:'3',orders:[order],nextCursor:null}),true);
 assert.equal(isOwnOrderDetail({storeId:'3',order,items:[item]}),true);
 assert.equal(isOwnOrderDetail({storeId:'3',order:{...order,orderId:9007199254740992},items:[item]}),false);
 assert.equal(isOwnOrders({code:200,data:{storeId:'3',orders:[order],nextCursor:null}}),false);
 assert.equal(isOwnOrderDetail({storeId:'3',order,items:[item],deliveryConfirmationKey:'SECRET'}),false);
 assert.equal(isOwnOrderDetail({storeId:'3',order:{...order,finance:{...finance,remainingCreditAmount:'NaN'}},items:[item]}),false);
});
test('本人订单游标绑定20条末项，重复行和不完整套餐快照拒绝',async()=>{
 const {isOwnOrders,isOwnOrderDetail}=await load();const rows=Array.from({length:20},(_,i)=>({...order,orderId:String(30-i)}));
 assert.equal(isOwnOrders({storeId:'3',orders:rows,nextCursor:'11'}),true);assert.equal(isOwnOrders({storeId:'3',orders:rows,nextCursor:'12'}),false);
 assert.equal(isOwnOrders({storeId:'3',orders:[order,order],nextCursor:null}),false);
 assert.equal(isOwnOrderDetail({storeId:'3',order,items:[item,item]}),false);
 assert.equal(isOwnOrderDetail({storeId:'3',order,items:[{...item,packageQuantity:null}]}),false);
 assert.equal(isOwnOrderDetail({storeId:'3',order,items:[{...item,unit:null,packageGroupNo:null,packageName:null,packageQuantity:null,packageUnitPrice:null}]}),true);
});
test('本人订单筛选只含日期状态游标，不允许无效自然日或反向日期',async()=>{
 const {ownOrderPath}=await load();assert.equal(ownOrderPath({from:'2026-11-09',to:'2026-11-09',state:9,after:'9223372036854775807'}),'/api/admin/mobile/orders?from=2026-11-09&to=2026-11-09&state=9&after=9223372036854775807');
 assert.throws(()=>ownOrderPath({from:'2026-02-30'}));assert.throws(()=>ownOrderPath({from:'2026-11-10',to:'2026-11-09'}));assert.throws(()=>ownOrderPath({state:10}));assert.throws(()=>ownOrderPath({after:'0'}));
});
