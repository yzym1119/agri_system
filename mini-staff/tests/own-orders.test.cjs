const test=require('node:test'),assert=require('node:assert/strict');
const {setupPage,setupService}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/staff-orders.js');
const finance={payAmount:'65',receivedAmount:'25',creditAmount:'40',creditReductionAmount:'10',cashRefundedAmount:'0',remainingCreditAmount:null,repaidAmount:'5'};
const order={orderId:'50',orderNo:'O50',createdAt:'2026-11-09T12:00',state:9,customerLabel:'张**',finance,deliveryType:null,deliveryStatus:null};
async function authFixture(permissions=['order:own:query']){
 const user={id:'8',name:'员工',permissions,extra:{storeId:'3'}};
 let response={storeId:'3',orders:[order],nextCursor:null},last;
 const auth=createStaffAuth(async req=>{last=req;return req.url.endsWith('/login')?{statusCode:200,data:{tokenName:'Authorization',tokenValue:'TEST',userInfo:user}}:req.url==='/auth/staff/info'?{statusCode:200,data:user}:typeof response==='function'?response(req):{statusCode:200,data:response};});await auth.login('test','test');
 return {auth,last:()=>last,set:v=>response=v};
}
test('本人订单服务使用真实会话并拒绝跨店跨对象与迟到401',async()=>{
 const f=await authFixture();const s=setupService('orders',{'./auth':{staffAuth:f.auth},'@agri/mobile-contracts/staff-orders':contracts,'@agri/mobile-contracts/ids':require('../../packages/mobile-contracts/dist/ids.js')});const epoch=f.auth.current().epoch;
 assert.equal((await s.readOwnOrders({state:9},epoch)).orders[0].orderId,'50');assert.match(f.last().url,/state=9/);assert.equal(f.last().header.Authorization,'TEST');
 f.set({storeId:'4',orders:[order],nextCursor:null});await assert.rejects(s.readOwnOrders({},epoch),/STORE_MISMATCH/);
 f.set({storeId:'3',order:{...order,orderId:'51'},items:[]});await assert.rejects(s.readOwnOrder('50',epoch),/RESPONSE_INVALID/);
 let done;f.set(()=>new Promise(resolve=>done=resolve));const pending=s.readOwnOrders({},epoch);await f.auth.login('other','test');done({statusCode:401,data:null});await assert.rejects(pending,/STALE_SESSION/);assert.ok(f.auth.current());
});
async function pageFixture(page='orders/list',permissions){
 const f=await authFixture(permissions),calls=[];let list=async()=>({storeId:'3',orders:[order],nextCursor:null}),detail=async()=>({storeId:'3',order,items:[]});
 const p=setupPage(page,f.auth,true,{'../../../services/auth':{staffAuth:f.auth},'../../../services/orders':{readOwnOrders:async(...args)=>{calls.push(args);return list(...args);},readOwnOrder:async(...args)=>{calls.push(args);return detail(...args);}},'@agri/mobile-contracts/staff-orders':contracts,'@agri/mobile-contracts/ids':require('../../packages/mobile-contracts/dist/ids.js')});
 return {f,p,calls,setList:v=>list=v,setDetail:v=>detail=v};
}
test('本人订单无权限不读，筛选改变使旧页失效并保留新筛选',async()=>{
 const denied=await pageFixture('orders/list',[]);await denied.p.hooks.show();assert.equal(denied.calls.length,0);
 const f=await pageFixture();let done;f.setList(()=>new Promise(resolve=>done=resolve));const opening=f.p.hooks.show();await new Promise(r=>setTimeout(r,0));
 f.setList(async()=>({storeId:'3',orders:[],nextCursor:null}));f.p.state.from.value='2026-11-09';await f.p.state.search();done({storeId:'3',orders:[order],nextCursor:null});await opening;
 assert.equal(f.p.state.orders.value.length,0);assert.equal(f.p.state.from.value,'2026-11-09');
 f.setList(async()=>{throw Error('NETWORK');});await f.p.state.search();assert.match(f.p.state.message.value,/未完成/);assert.equal(f.p.state.from.value,'2026-11-09');
});
test('本人订单离页和换会话后旧成功失败均不能写页面或导航',async()=>{
 for(const reject of [false,true]){const f=await pageFixture();let done,fail;f.setList(()=>new Promise((r,j)=>{done=r;fail=j;}));const opening=f.p.hooks.show();await new Promise(r=>setTimeout(r,0));f.p.hooks.hide();await f.f.auth.logout();reject?fail(Error('OLD')):done({storeId:'3',orders:[order],nextCursor:null});await opening;assert.equal(f.p.state.orders.value.length,0);assert.equal(f.p.routes.length,0);}
});
test('本人订单详情拒绝坏编号，隐藏清除旧明细，读失败可重试',async()=>{
 const f=await pageFixture('orders/detail');f.p.hooks.load({orderId:'50'});await f.p.hooks.show();assert.equal(f.p.state.detail.value.order.orderId,'50');
 f.p.hooks.hide();assert.equal(f.p.state.detail.value,null);f.setDetail(async()=>{throw Error('OFFLINE');});await f.p.hooks.show();assert.match(f.p.state.message.value,/未完成/);assert.equal(f.p.state.detail.value,null);
 const bad=await pageFixture('orders/detail');bad.p.hooks.load({orderId:'9e18'});await bad.p.hooks.show();assert.equal(bad.calls.length,0);
});
