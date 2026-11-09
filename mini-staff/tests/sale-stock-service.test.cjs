const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/sale.js');
async function fixture(){
 let last,answer={statusCode:200,data:{storeId:'1',sufficient:false,issues:['整单需要15，本店可售12袋']}};
 const user={id:'7',name:'验收员工',permissions:['pos:order:create'],extra:{storeId:'1'}};
 const auth=createStaffAuth(async req=>{
  if(req.url.endsWith('/login'))return {statusCode:200,data:{tokenName:'Authorization',tokenValue:'CURRENT',userInfo:user}};
  last=req;return typeof answer==='function'?answer(req):answer;
 });await auth.login('test','pass');
 const queries=setupService('sale',{'./auth':{staffAuth:auth},'./pos-recovery':{posRecovery:{submitOnce(){throw Error('unexpected payment')}}},'@agri/mobile-contracts/sale':contracts});
 return {auth,queries,last:()=>last,set:v=>answer=v};
}
const lines=[{kind:'PACKAGE',packageId:'20',packageQuoteId:'Q',name:'套餐',quantity:'1',quotedUnitPrice:'10.00',materials:[]}];
test('预检发送真实本店与整套意图，不发收款请求或前端拆分物料',async()=>{
 const f=await fixture();const result=await f.queries.checkSaleStock(lines,f.auth.current().epoch,{});
 assert.equal(result.sufficient,false);assert.equal(f.last().url,'/api/admin/pos/catalog/stock-preview');
 assert.equal(f.last().header.Authorization,'CURRENT');assert.equal(f.last().data.storeId,'1');
 assert.equal(f.last().data.items[0].packageId,'20');assert.equal('productId' in f.last().data.items[0],false);
 for(const forbidden of ['requestKey','paymentType','payments','staffId'])assert.equal(forbidden in f.last().data,false);
});
test('预检拒绝异店和旧包装，迟到401不能退出新账号',async()=>{
 const f=await fixture(),epoch=f.auth.current().epoch;
 f.set({statusCode:200,data:{storeId:'2',sufficient:true,issues:[]}});await assert.rejects(f.queries.checkSaleStock(lines,epoch),/STORE_MISMATCH/);
 f.set({statusCode:200,data:{code:'200',data:{storeId:'1',sufficient:true,issues:[]}}});await assert.rejects(f.queries.checkSaleStock(lines,epoch),/RESPONSE_INVALID/);
 let done;f.set(()=>new Promise(resolve=>done=resolve));const pending=f.queries.checkSaleStock(lines,epoch);
 await f.auth.login('other','pass');done({statusCode:401,data:null});await assert.rejects(pending,/STALE_SESSION/);assert.ok(f.auth.current());
});
