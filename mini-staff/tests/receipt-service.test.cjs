const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/receipt.js');
test('采购手机读取使用裸合同并绑定当前门店；原写入口保持精确字符串与请求号',async()=>{
 const user={id:'7',name:'库管',permissions:['purchase:inbound'],extra:{storeId:'1'}};let response={statusCode:200,data:{storeId:'1',page:1,hasMore:false,orders:[]}},last;
 const auth=createStaffAuth(async req=>{if(req.url.endsWith('/login'))return{statusCode:200,data:{tokenName:'Authorization',tokenValue:'test',userInfo:user}};last=req;return response;});await auth.login('test','pass');
 const service=setupService('receipt',{'./auth':{staffAuth:auth},'./platform':{mobilePlatform:{}},'@agri/mobile-contracts/receipt':contracts});
 const actor=auth.current();await service.readReceivingOrders(1,'',actor.epoch);assert.equal(last.header.Authorization,'test');
 response={statusCode:200,data:{storeId:'2',page:1,hasMore:false,orders:[]}};await assert.rejects(service.readReceivingOrders(1,'',actor.epoch),/STORE_MISMATCH/);
 response={statusCode:200,data:{code:'200',data:{receiptId:'9223372036854775807',batches:[]}}};
 const command={requestKey:'11111111-1111-4111-8111-111111111111',purchaseOrderId:'8',lines:[{purchaseOrderItemId:'81',productId:'10',batches:[{originalUnit:'箱',originalQuantity:'0.5',originalUnitCost:'999999999999.99999999'}]}]};
 await service.sendReceipt(command,actor);assert.equal(last.url,'/api/admin/purchase/receipts');assert.equal(last.data.lines[0].batches[0].originalUnitCost,'999999999999.99999999');
});
