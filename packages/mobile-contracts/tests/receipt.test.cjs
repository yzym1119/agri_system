const test=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/receipt.js');
const KEY='11111111-1111-4111-8111-111111111111';
const detail={id:'8',storeId:'1',orderNo:'PO8',supplierName:'测试供货',items:[{id:'81',productId:'10',productCode:'P10',productName:'测试商品',orderedQuantity:'2',receivedBaseQuantity:'0',remainingBaseQuantity:'24',orderedUnit:'箱',baseUnitName:'瓶',allowFractionalBaseQuantity:false,scanCodes:['P10'],units:[{id:'100',name:'瓶',baseQuantityPerUnit:'1'},{id:'101',name:'箱',baseQuantityPerUnit:'12'}]}]};
const draft=(quantity='0.5',cost='120')=>[{purchaseOrderItemId:'81',remark:'',batches:[{originalUnit:'箱',originalQuantity:quantity,originalUnitCost:cost,supplierBatchNo:'',productionDate:'',expiryDate:'',unknownDates:true}]}];
const completed=(key=KEY)=>({requestKey:key,status:'COMPLETED',storeId:'1',purchaseOrderId:'8',receiptId:'90',receiptNo:'RCV90',lines:[{purchaseOrderItemId:'81',productId:'10',remark:null,batches:[{id:'92',internalBatchNo:'B92',supplierBatchNo:null,originalUnitName:'箱',originalQuantity:'0.5',originalUnitCost:'120',productionDate:null,expiryDate:null,dateStatus:'UNKNOWN',inspectionReference:null}]}]});
async function fixture(storageOverride={}) {
 const m=await load(),data=new Map();let actor={staffId:'7',storeId:'1',epoch:1},writes=0;
 const storage={readStorage:key=>data.get(key)??null,writeStorage:(key,v)=>{writes++;data.set(key,v);},removeStorage:key=>data.delete(key),...storageOverride};
 const recovery=m.createReceiptRecovery(storage,()=>actor,async()=>KEY);
 const snapshot=m.buildReceiptSnapshot(detail,draft(),{});
 return {m,data,storage,recovery,snapshot,actor,setActor:a=>actor=a,writes:()=>writes};
}
test('收货精度校验先读原文，整件按后台粒度换算，不猜单位白名单',async()=>{
 const m=await load();assert.equal(m.isReceivingDetail(detail),true);
 const s=m.buildReceiptSnapshot(detail,draft(),{});assert.equal(s.lines[0].batches[0].baseQuantity,'6');
 for(const [q,c] of [['0.1','120'],['0','120'],['1',''],['1','0'],['1','10.123456789'],['1e0','120'],['1','1000000000000']])assert.throws(()=>m.buildReceiptSnapshot(detail,draft(q,c),{}));
 const measured=structuredClone(detail);measured.items[0].allowFractionalBaseQuantity=true;measured.items[0].baseUnitName='公斤';
 assert.equal(m.buildReceiptSnapshot(measured,draft('0.1'),{}).lines[0].batches[0].baseQuantity,'1.2');
});
test('日期未知需明确确认；仅有效期真实保留；同SKU可以有多个实际批次',async()=>{
 const m=await load(),d=draft();d[0].batches[0].unknownDates=false;assert.throws(()=>m.buildReceiptSnapshot(detail,d,{}));
 d[0].batches[0].expiryDate='2027-01-01';const s=m.buildReceiptSnapshot(detail,d,{});assert.equal(s.lines[0].batches[0].productionDate,null);
 d[0].batches[0].productionDate='2027-02-01';assert.throws(()=>m.buildReceiptSnapshot(detail,d,{}));
 const two=draft();two[0].batches.push({...two[0].batches[0],originalQuantity:'1'});assert.equal(m.buildReceiptSnapshot(detail,two,{}).lines[0].batches.length,2);
});
test('先保存回读再发送，成功丢响应后沿同key核对，不再增加一笔收货',async()=>{
 const f=await fixture();let sends=0;
 const r=await f.recovery.submit(f.snapshot,async(command,actor)=>{sends++;assert.equal(f.data.size,1);assert.equal(command.requestKey,KEY);assert.equal(command.lines[0].batches[0].originalQuantity,'0.5');assert.ok(Object.isFrozen(actor));throw Error('LOST_RESPONSE');},async key=>completed(key));
 assert.equal(sends,1);assert.equal(r.status,'COMPLETED');assert.equal(f.data.size,1);
 const restored=f.m.createReceiptRecovery(f.storage,()=>f.actor,async()=>KEY);assert.equal(restored.restore().requestKey,KEY);
 await assert.rejects(f.recovery.submit(f.snapshot,async()=>{},async()=>completed()),/RECOVERY_REQUIRED/);
});
test('UNKNOWN固定原摘要；显式重试只发原体原号，已完成则只读不重发',async()=>{
 const f=await fixture();const unknown={requestKey:KEY,status:'UNKNOWN',storeId:'1',purchaseOrderId:null,receiptId:null,receiptNo:null,lines:[]};
 await f.recovery.submit(f.snapshot,async()=>{},async()=>unknown);f.snapshot.lines[0].batches[0].originalQuantity='99';
 await assert.rejects(f.recovery.retryOriginal(false,async()=>{},async()=>unknown),/CONFIRMATION_REQUIRED/);
 let reads=0,sends=0;const result=await f.recovery.retryOriginal(true,async command=>{sends++;assert.equal(command.lines[0].batches[0].originalQuantity,'0.5');},async()=>++reads===1?unknown:completed());
 assert.equal(result.status,'COMPLETED');assert.equal(sends,1);
 await f.recovery.retryOriginal(true,async()=>sends++,async()=>completed());assert.equal(sends,1);assert.equal(f.data.size,1);
});
test('同key异体事实、异门店及错误收货ID不能结束；原结果二读确认后才清记录',async()=>{
 const f=await fixture();await f.recovery.submit(f.snapshot,async()=>{},async()=>completed());
 const changed=completed();changed.lines[0].batches[0].originalQuantity='1';
 await assert.rejects(f.recovery.lookupOriginal(async()=>changed),/FACT_MISMATCH/);
 await assert.rejects(f.recovery.finishCompleted('91',true,async()=>completed()),/RESULT_CHANGED/);
 await assert.rejects(f.recovery.finishCompleted('90',true,async()=>({...completed(),storeId:'2'})),/RESPONSE_INVALID/);
 assert.equal(f.data.size,1);await f.recovery.finishCompleted('90',true,async()=>completed());assert.equal(f.data.size,0);
});
test('存储异常或已观察记录被替换均冻结；读取失败不发送',async()=>{
 const broken=await fixture({writeStorage:()=>{throw Error('FULL');}});let sends=0;
 await assert.rejects(broken.recovery.submit(broken.snapshot,async()=>sends++,async()=>completed()),/STORAGE_BLOCKED/);assert.equal(sends,0);
 const f=await fixture();await f.recovery.submit(f.snapshot,async()=>{},async()=>completed());const key=[...f.data.keys()][0];f.data.delete(key);
 assert.throws(()=>f.recovery.restore(),/STORAGE_BLOCKED/);
});
test('旧员工响应不能回填新员工或删除其记录；回调不可改写作用域',async()=>{
 const f=await fixture();let done;
 const pending=f.recovery.submit(f.snapshot,async(command,actor)=>{try{actor.staffId='9';}catch{};await new Promise(resolve=>done=resolve);},async()=>completed());
 await new Promise(resolve=>setTimeout(resolve,0));f.setActor({staffId:'9',storeId:'1',epoch:2});done();await assert.rejects(pending,/STALE_SESSION/);
 assert.equal(f.recovery.restore(),null);f.setActor({staffId:'7',storeId:'1',epoch:3});assert.equal(f.recovery.restore().requestKey,KEY);
});
test('精确十进制乘法不依赖浮点或BigInt，校验大数有效尾数',async()=>{
 const m=await load();assert.equal(m.receiptMultiply('999999999999.99999999','1'),'999999999999.99999999');
 assert.equal(m.receiptMultiply('0.00000001','0.00000001'),'0.0000000000000001');assert.equal(m.receiptMultiply('0.5','12'),'6');
});
test('同采购行各批合计超收须先填说明，未发请求保持可更正；计量相加不出现浮点超收',async()=>{
 const m=await load();assert.throws(()=>m.buildReceiptSnapshot(detail,draft('3'),{}),/OVER_RECEIPT_REASON_REQUIRED/);
 const two=draft('1.5');two[0].batches.push({...two[0].batches[0]});assert.throws(()=>m.buildReceiptSnapshot(detail,two,{}),/OVER_RECEIPT_REASON_REQUIRED/);
 assert.equal(m.buildReceiptSnapshot(detail,draft('3'),{exceptionReason:'供应商实际多发1箱'}).lines[0].batches[0].baseQuantity,'36');
 const measured=structuredClone(detail);measured.items[0].allowFractionalBaseQuantity=true;measured.items[0].baseUnitName='公斤';measured.items[0].units[1].baseQuantityPerUnit='1';measured.items[0].remainingBaseQuantity='0.3';
 const d=draft('0.1');d[0].batches.push({...d[0].batches[0],originalQuantity:'0.2'});assert.equal(m.buildReceiptSnapshot(measured,d,{}).lines[0].batches.length,2);
});
