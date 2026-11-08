const test=require('node:test'),assert=require('node:assert/strict');
const {setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/sale.js');
async function fixture(permissions=['customer:query']) {
 const user={id:'8',name:'验收员工',permissions,extra:{storeId:'3'}};let last,body={records:[{id:'9007199254740993',name:'验收客户',phone:'13800000000',status:1,balance:'99'}]};
 const auth=createStaffAuth(async req=>{if(req.url.endsWith('/login'))return{statusCode:200,data:{tokenName:'Authorization',tokenValue:'test',userInfo:user}};last=req;return{statusCode:200,data:body};});
 await auth.login('test','pass');
 const api=setupService('customers',{'./auth':{staffAuth:auth},'@agri/mobile-contracts/sale':contracts});
 return{auth,api,last:()=>last,setBody:v=>body=v};
}
test('选客按姓名或手机号走真实列表参数，保留Long精度且不保留钱包字段',async()=>{
 const f=await fixture();const rows=await f.api.searchSaleCustomers('验收 & 客户',f.auth.current().epoch);
 assert.match(f.last().url,/name=%E9/);assert.ok(f.last().url.includes(encodeURIComponent('验收 & 客户')));
 assert.equal(rows[0].id,'9007199254740993');assert.deepEqual(Object.keys(rows[0]),['id','name','phone']);
 await f.api.searchSaleCustomers('138',f.auth.current().epoch);assert.match(f.last().url,/phone=138/);
});
test('无查询权限、旧会话和空关键词均不发送客户请求',async()=>{
 const f=await fixture(['pos:order:create']);await assert.rejects(f.api.searchSaleCustomers('张',f.auth.current().epoch),/FORBIDDEN/);assert.equal(f.last(),undefined);
 const g=await fixture();await assert.rejects(g.api.searchSaleCustomers('张',-1),/STALE_SESSION/);await assert.rejects(g.api.searchSaleCustomers(' ',g.auth.current().epoch),/QUERY/);assert.equal(g.last(),undefined);
});
test('客户响应停用、非法ID或非列表均拒绝，不能猜配客户',async()=>{
 const f=await fixture();for(const body of [{records:[{id:9007199254740992,name:'客户',phone:null,status:1}]},{records:[{id:'9',name:'客户',phone:null,status:2}]},{data:[]}]) {
 f.setBody(body);await assert.rejects(f.api.searchSaleCustomers('客户',f.auth.current().epoch),/RESPONSE_INVALID/);
 }
});
