const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/pickup.js');
test('手机提货适配绑定原请求与员工门店，原确认只提交摘要和字符串分配编号',async()=>{
 const user={id:'8',name:'库管',permissions:['order:pickup:verify'],extra:{storeId:'3'}};let response={statusCode:200,data:{storeId:'3',state:'PENDING',tasks:[],nextCursor:null}},last;
 const auth=createStaffAuth(async req=>{if(req.url.endsWith('/login'))return {statusCode:200,data:{tokenName:'Authorization',tokenValue:'test',userInfo:user}};last=req;return response;});await auth.login('test','pass');
 const api=setupService('pickup',{'./auth':{staffAuth:auth},'@agri/mobile-contracts/pickup':contracts});const epoch=auth.current().epoch;
 await api.readPickupTasks('PENDING',null,epoch);assert.equal(last.url.includes('storeId'),false);
 response={statusCode:200,data:{storeId:'9',state:'PENDING',tasks:[],nextCursor:null}};await assert.rejects(api.readPickupTasks('PENDING',null,epoch),/STORE_MISMATCH/);
 response={statusCode:200,data:''};await api.confirmPickup('42','a'.repeat(64),['9223372036854775807'],epoch);
 assert.equal(last.url,'/api/admin/orders/pickup/requests/42/fulfill');assert.equal(last.data.confirmationKey,'a'.repeat(64));assert.equal(last.data.allocationIds[0],'9223372036854775807');
 response={statusCode:200,data:{code:10001,message:'拒绝'}};await assert.rejects(api.confirmPickup('42','a'.repeat(64),['1'],epoch));
});
