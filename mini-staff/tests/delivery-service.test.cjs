const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/fulfillment.js');
test('本人配送读取与确认绑定当前门店、同订单和原核对key，不发送客户端所有者',async()=>{
 const user={id:'8',name:'配送',permissions:['order:delivery:assigned'],extra:{storeId:'3'}};let response={statusCode:200,data:{storeId:'3',state:'PENDING',tasks:[],nextCursor:null}},last;
 const auth=createStaffAuth(async req=>{if(req.url.endsWith('/login'))return{statusCode:200,data:{tokenName:'Authorization',tokenValue:'test',userInfo:user}};last=req;return response;});await auth.login('test','pass');
 const api=setupService('fulfillment',{'./auth':{staffAuth:auth},'@agri/mobile-contracts/fulfillment':contracts});const epoch=auth.current().epoch;
 await api.readAssignedTasks('PENDING',null,epoch);assert.equal(last.header.Authorization,'test');assert.equal(last.url.includes('staffId'),false);
 response={statusCode:200,data:{storeId:'9',state:'PENDING',tasks:[],nextCursor:null}};await assert.rejects(api.readAssignedTasks('PENDING',null,epoch),/STORE_MISMATCH/);
 response={statusCode:200,data:{storeId:'3',orderId:'41',status:'COMPLETED',receivedAt:null}};await assert.rejects(api.confirmAssignedReceipt('42','a'.repeat(64),epoch),/RESPONSE_INVALID/);
 assert.equal(Object.keys(last.data).join(','),'confirmationKey');assert.equal(last.data.confirmationKey,'a'.repeat(64));
});
