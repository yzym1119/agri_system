const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/inventory.js');
async function fixture() {
 let answer={statusCode:200,data:{storeId:'1',type:'UNKNOWN',matches:[],hasMore:false}},last;
 const user={id:'1',name:'测试',extra:{storeId:'1'},permissions:['inventory:view']};
 const auth=createStaffAuth(async req=>{
   if(req.url.endsWith('/login'))return {statusCode:200,data:{tokenName:'Authorization',tokenValue:'ONLY_CURRENT',userInfo:user}};
   last=req;return typeof answer==='function'?answer(req):answer;
 });
 await auth.login('test','pass');
 const queries=setupService('scan',{'./auth':{staffAuth:auth},'@agri/mobile-contracts/inventory':contracts});
 return {auth,queries,set:v=>answer=v,last:()=>last};
}
test('查货只发送当前令牌与用途，拒绝外店、旧包装和错误页合同',async()=>{
 const f=await fixture(),epoch=f.auth.current().epoch;
 await f.queries.resolveCode('P1',epoch);
 assert.equal(f.last().header.Authorization,'ONLY_CURRENT');assert.equal(f.last().data.purpose,'INVENTORY_LOOKUP');assert.equal('storeId' in f.last().data,false);
 f.set({statusCode:200,data:{storeId:'2',type:'UNKNOWN',matches:[],hasMore:false}});await assert.rejects(f.queries.resolveCode('P1',epoch),/STORE_MISMATCH/);
 f.set({statusCode:200,data:{code:'200',data:{storeId:'1',type:'UNKNOWN',matches:[],hasMore:false}}});await assert.rejects(f.queries.resolveCode('P1',epoch),/RESPONSE_INVALID/);
 f.set({statusCode:200,data:{storeId:'1',page:1,matches:[],hasMore:false}});await assert.rejects(f.queries.searchProducts('种子',2,epoch),/RESPONSE_INVALID/);
});
test('库存按字符串原定位读取，异商品响应拒绝，换身份后的旧401不退出新登录',async()=>{
 const f=await fixture(),epoch=f.auth.current().epoch;
 f.set({statusCode:200,data:{storeId:'1',productId:'2',productCode:'P2',productName:'商品',productEnabled:true,mainUnitName:'袋',quantity:'0',unreservedQuantity:'0',page:1,totalRows:0,rows:[]}});
 await assert.rejects(f.queries.readInventory({productId:'1',batchId:'9223372036854775807',locationId:'3'},1,epoch),/RESPONSE_INVALID/);
 assert.match(f.last().url,/batchId=9223372036854775807&locationId=3/);
 let done;f.set(()=>new Promise(resolve=>done=resolve));const pending=f.queries.resolveCode('P1',epoch);
 await f.auth.login('test2','pass');done({statusCode:401,data:null});await assert.rejects(pending,/STALE_SESSION/);assert.ok(f.auth.current());
});
