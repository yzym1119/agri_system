const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/receipt.js');
const ids=require('../../packages/mobile-contracts/dist/ids.js');
const KEY='11111111-1111-4111-8111-111111111111';
const detail={id:'8',storeId:'1',orderNo:'PO8',supplierName:'测试供应商',items:[{id:'81',productId:'10',productCode:'P10',productName:'测试商品',orderedQuantity:'2',receivedBaseQuantity:'0',remainingBaseQuantity:'24',orderedUnit:'箱',baseUnitName:'瓶',allowFractionalBaseQuantity:false,scanCodes:['P10','BOX10'],units:[{id:'100',name:'瓶',baseQuantityPerUnit:'1'},{id:'101',name:'箱',baseQuantityPerUnit:'12'}]}]};
const completed=()=>({requestKey:KEY,status:'COMPLETED',storeId:'1',purchaseOrderId:'8',receiptId:'90',receiptNo:'RCV90',lines:[{purchaseOrderItemId:'81',productId:'10',remark:null,batches:[{id:'92',internalBatchNo:'B92',supplierBatchNo:null,originalUnitName:'箱',originalQuantity:'0.5',originalUnitCost:'120',productionDate:null,expiryDate:null,dateStatus:'UNKNOWN',inspectionReference:null}]}]});
async function fixture(page='receipt/detail',permissions=['purchase:inbound']){
 let user={id:'7',name:'测试库管',permissions,extra:{storeId:'1'}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));await auth.login('test','pass');
 const data=new Map(),storage={readStorage:k=>data.get(k)??null,writeStorage:(k,v)=>data.set(k,v),removeStorage:k=>data.delete(k)};
 const recovery=contracts.createReceiptRecovery(storage,()=>auth.current(),async()=>KEY);
 let source=detail,result=completed(),camera={status:'SCANNED',code:'BOX10',scanType:'CODE_128'};const calls={detail:0,list:0,send:0,read:0};
 const api={receiptRecovery:recovery,readReceivingDetail:async()=>{calls.detail++;return typeof source==='function'?source():structuredClone(source);},
   readReceivingOrders:async page=>{calls.list++;return{storeId:'1',page,hasMore:false,orders:[{id:'8',orderNo:'PO8',supplierName:'测试供应商'}]};},
   sendReceipt:async command=>{calls.send++;assert.equal(data.size,1);assert.equal(command.requestKey,KEY);return undefined;},
   readReceiptResult:async key=>{calls.read++;assert.equal(key,KEY);return typeof result==='function'?result():result;}};
 const p=setupPage(page,auth,true,{'../../../services/auth':{staffAuth:auth},'../../../services/receipt':api,
   '../../../services/platform':{mobilePlatform:{scan:async()=>camera}},'@agri/mobile-contracts/receipt':contracts,'@agri/mobile-contracts/ids':ids});
 return {p,auth,data,recovery,calls,setDetail:v=>source=v,setResult:v=>result=v,setCamera:v=>camera=v,setUser:v=>user=v};
}
function fill(f){const b=f.p.state.drafts.value[0].batches[0];b.originalQuantity='0.5';b.originalUnitCost='120';b.unknownDates=true;}
test('手机收货空成本不预造，半箱换算完整最小单位，提交先摘要再确认',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();
 assert.equal(f.p.state.drafts.value[0].batches[0].originalUnitCost,'');f.p.state.review();assert.equal(f.p.state.preview.value,null);assert.equal(f.calls.send,0);
 fill(f);f.p.state.review();assert.equal(f.p.state.preview.value.lines[0].batches[0].baseQuantity,'6');assert.equal(f.calls.send,0);
 await f.p.state.submit();assert.equal(f.calls.send,1);assert.equal(f.p.state.pending.value.requestKey,KEY);assert.equal(f.p.state.result.value.receiptId,'90');
});
test('未知结果冻结原体，不准换采购单新收；显式同体重试仍用原号',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();fill(f);f.p.state.review();
 const unknown={requestKey:KEY,status:'UNKNOWN',storeId:'1',purchaseOrderId:null,receiptId:null,receiptNo:null,lines:[]};f.setResult(unknown);await f.p.state.submit();
 assert.equal(f.data.size,1);assert.equal(f.p.state.preview.value,null);assert.equal(f.p.state.pending.value.requestKey,KEY);
 await f.p.state.submit();assert.equal(f.calls.send,1);
 f.p.hooks.hide();f.p.hooks.load({orderId:'9'});await f.p.hooks.show();assert.equal(f.calls.detail,1);assert.equal(f.p.state.pending.value.snapshot.purchaseOrderId,'8');
 f.p.state.confirmed.value=true;let reads=0;f.setResult(()=>++reads===1?unknown:completed());await f.p.state.act('retry');assert.equal(f.calls.send,2);assert.equal(f.p.state.result.value.status,'COMPLETED');
});
test('隐藏或换会话使旧详情和旧确认失效，无权限不读成本资料',async()=>{
 const denied=await fixture('receipt/detail',[]);denied.p.hooks.load({orderId:'8'});await denied.p.hooks.show();assert.equal(denied.calls.detail,0);assert.match(denied.p.state.message.value,/权限/);
 const f=await fixture();f.p.hooks.load({orderId:'8'});let done;f.setDetail(()=>new Promise(resolve=>done=resolve));const loading=f.p.hooks.show();await new Promise(r=>setTimeout(r,0));
 f.p.hooks.hide();f.setDetail(detail);await f.p.hooks.show();done({...detail,orderNo:'OLD'});await loading;assert.equal(f.p.state.detail.value.orderNo,'PO8');
 fill(f);f.p.state.review();f.p.hooks.hide();await f.p.state.submit();assert.equal(f.calls.send,0);assert.equal(f.p.state.preview.value,null);
});
test('结束前必须二读同一收货单，变化不能清除；记录坏掉保持暂停',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();fill(f);f.p.state.review();await f.p.state.submit();
 f.p.state.confirmed.value=true;f.setResult({...completed(),receiptId:'91'});await f.p.state.act('finish');assert.equal(f.data.size,1);assert.equal(f.p.state.confirmed.value,false);
 f.setResult(completed());await f.p.state.act('read');f.p.state.confirmed.value=true;await f.p.state.act('finish');assert.equal(f.data.size,0);
 const broken=await fixture();broken.data.set('agri:receipt-pending:v1:7:1','broken');broken.p.hooks.load({orderId:'8'});await broken.p.hooks.show();assert.equal(broken.calls.detail,0);assert.match(broken.p.state.message.value,/暂停/);
});
test('改单位要重填数量和单位进价，所有新批次均为空；原待核对不允许编辑',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();fill(f);const line=f.p.state.drafts.value[0],batch=line.batches[0];
 f.p.state.changeUnit(line,batch,0);assert.equal(batch.originalUnit,'瓶');assert.equal(batch.originalQuantity,'');assert.equal(batch.originalUnitCost,'');
 f.p.state.addBatch(line);assert.equal(line.batches[1].originalUnitCost,'');assert.equal(line.batches[1].originalQuantity,'');
});
test('待收列表先核对当前身份，本机有未核对收货时保留原入口',async()=>{
 const f=await fixture('receipt/list');await f.p.hooks.show();assert.equal(f.calls.list,1);assert.equal(f.p.state.orders.value.length,1);
 f.p.state.open('8');assert.equal(f.p.routes[0],'/pages/receipt/detail/index?orderId=8');
 const noPermission=await fixture('receipt/list',[]);await noPermission.p.hooks.show();assert.equal(noPermission.calls.list,0);
});
test('超收缺说明在摘要前拒绝，不保存不发送，表单可补说明后再次核对',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();fill(f);f.p.state.drafts.value[0].batches[0].originalQuantity='3';
 f.p.state.review();assert.equal(f.p.state.preview.value,null);assert.equal(f.data.size,0);assert.equal(f.calls.send,0);assert.match(f.p.state.message.value,/超收/);
 f.p.state.exceptionReason.value='供应商实发多1箱';f.p.state.review();assert.equal(f.p.state.preview.value.lines[0].batches[0].baseQuantity,'36');
});
test('收货扫码只定位本采购行，重码必须选行，不代填数量成本或猜内部批次',async()=>{
 const f=await fixture();const two=structuredClone(detail);two.items.push({...structuredClone(two.items[0]),id:'82'});f.setDetail(two);
 f.p.hooks.load({orderId:'8'});await f.p.hooks.show();await f.p.state.scanProduct();
 assert.equal(f.p.state.scanMatches.value.length,2);assert.equal(f.p.scrolls.length,0);
 f.p.state.chooseLine(f.p.state.scanMatches.value[1]);assert.equal(f.p.scrolls[0],'#receipt-line-82');assert.equal(f.p.state.drafts.value[1].batches[0].originalQuantity,'');assert.equal(f.calls.send,0);
 f.setCamera({status:'SCANNED',code:'INTERNAL-FAKE',scanType:'CODE_128'});await f.p.state.scanProduct();assert.equal(f.p.state.scanMatches.value.length,0);assert.match(f.p.state.scanMessage.value,/没有匹配/);
 f.setCamera({status:'DENIED'});await f.p.state.scanProduct();assert.match(f.p.state.scanMessage.value,/手输/);
 f.p.state.locateCode('BOX10');assert.equal(f.p.state.scanMatches.value.length,2);
});
test('前后台同员工同单恢复未提交原文，但废弃确认摘要；换员工不继承成本草稿',async()=>{
 const f=await fixture();f.p.hooks.load({orderId:'8'});await f.p.hooks.show();fill(f);f.p.state.review();f.p.hooks.hide();await f.p.hooks.show();
 assert.equal(f.p.state.preview.value,null);assert.equal(f.p.state.drafts.value[0].batches[0].originalQuantity,'0.5');assert.equal(f.p.state.drafts.value[0].batches[0].originalUnitCost,'120');
 f.p.hooks.hide();f.setUser({id:'9',name:'另一库管',permissions:['purchase:inbound'],extra:{storeId:'1'}});await f.auth.login('other','pass');await f.p.hooks.show();
 assert.equal(f.p.state.drafts.value[0].batches[0].originalUnitCost,'');assert.equal(f.calls.send,0);
});
