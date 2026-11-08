const { test } = require('node:test');
const assert = require('node:assert/strict');
const { setupPage } = require('./helpers/page.cjs');
const { createPOSRecovery } = require('../../packages/mobile-contracts/dist/submission.js');
const { createStaffAuth } = require('../../packages/mobile-contracts/dist/staff-auth.js');
const KEY='11111111-1111-4111-8111-111111111111';
const sale={totalCents:5000,netCents:5000,paymentMethod:1,customerId:null,lines:[{productId:'1',packageId:null,unit:'袋',quantity:'1',lineCents:5000}]};
const complete=id=>({requestKey:KEY,status:'COMPLETED',orderId:id});
async function fixture(permissions=['pos:order:create']) {
  const user={id:1,name:'虚构员工',permissions,extra:{storeId:1}};
  const auth=createStaffAuth(async request=>({statusCode:200,data:request.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));
  await auth.login('test','password');
  const data=new Map(); let reads=0,cancels=0;
  const recovery=createPOSRecovery({readStorage:k=>data.get(k)??null,writeStorage:(k,v)=>data.set(k,v),removeStorage:k=>data.delete(k)},()=>auth.current(),async()=>KEY);
  await recovery.submitOnce(sale,async()=>undefined);
  let result=complete(9), cancelResult={requestKey:KEY,status:'CANCELLED',orderId:null};
  const page=setupPage('recovery',auth,true,{'../../services/pos-recovery':{
    posRecovery:recovery,readPOSResult:async key=>{reads++;assert.equal(key,KEY);return typeof result==='function'?result():result;},
    cancelPOSRequest:async key=>{cancels++;assert.equal(key,KEY);return cancelResult;},
  }});
  return {page,data,auth,recovery,setResult:v=>result=v,setCancel:v=>cancelResult=v,counts:()=>({reads,cancels})};
}
test('读取本机原单不自动提交或查后台；UNKNOWN后仍保留原请求', async()=>{
  const f=await fixture();await f.page.hooks.show();
  assert.equal(f.page.state.pending.value.requestKey,KEY);assert.equal(f.counts().reads,0);
  f.setResult({requestKey:KEY,status:'UNKNOWN',orderId:null});await f.page.state.act('read');
  assert.equal(f.data.size,1);assert.match(f.page.state.message.value,/不代表未成交/);
  f.page.state.confirmed.value=false;await f.page.state.act('cancel');assert.equal(f.counts().cancels,0);
});
test('取消返回原成交单不得清记录；再次确认原成交才可结束', async()=>{
  const f=await fixture();await f.page.hooks.show();f.setCancel(complete(9));
  f.page.state.confirmed.value=true;await f.page.state.act('cancel');
  assert.equal(f.data.size,1);assert.equal(f.page.state.result.value.orderId,'9');assert.equal(f.page.state.confirmed.value,false);
  f.page.state.confirmed.value=true;await f.page.state.act('finish');
  assert.equal(f.data.size,0);assert.match(f.page.state.message.value,/没有再次收款/);
});
test('已确认的订单在二读时变化则撤销确认并保持冻结', async()=>{
  const f=await fixture();await f.page.hooks.show();await f.page.state.act('read');
  f.page.state.confirmed.value=true;f.setResult(complete(10));await f.page.state.act('finish');
  assert.equal(f.data.size,1);assert.equal(f.page.state.confirmed.value,false);assert.equal(f.page.state.result.value,null);
});
test('无真实权限不读本机金融摘要、不请求核对接口；坏记录明确冻结', async()=>{
  const f=await fixture([]);await f.page.hooks.show();
  assert.equal(f.page.state.pending.value,null);await f.page.state.act('read');
  assert.equal(f.counts().reads,0);assert.match(f.page.state.message.value,/没有核对收银权限/);
  const broken=await fixture();const key=[...broken.data.keys()][0];broken.data.set(key,'broken');
  await broken.page.hooks.show();assert.match(broken.page.state.message.value,/记录读取异常/);
  assert.equal(broken.page.state.pending.value,null);
});
test('旧页核对响应不能覆盖返回前台后的新视图', async()=>{
  const f=await fixture();await f.page.hooks.show();let resolve;
  f.setResult(()=>new Promise(done=>resolve=done));const old=f.page.state.act('read');
  f.page.hooks.hide();await f.page.hooks.show();resolve(complete(9));await old;
  assert.equal(f.page.state.result.value,null);assert.equal(f.page.state.busy.value,false);assert.equal(f.data.size,1);
});
test('收银核对隐藏即清理金融视图，不删除原请求，旧页面不能继续发起核对',async()=>{
 const f=await fixture();await f.page.hooks.show();assert.ok(f.page.state.pending.value);
 f.page.hooks.hide();assert.equal(f.page.state.pending.value,null);assert.equal(f.page.state.result.value,null);
 await f.page.state.act('read');assert.equal(f.counts().reads,0);assert.equal(f.data.size,1);
});
test('本机坏记录不显示成无待办，同员工新登录的旧页面不能操作原视图',async()=>{
 const broken=await fixture();broken.data.set([...broken.data.keys()][0],'broken');await broken.page.hooks.show();
 assert.equal(broken.page.state.restored.value,false);
 const f=await fixture();await f.page.hooks.show();await f.auth.login('test','password');
 await f.page.state.act('read');assert.equal(f.counts().reads,0);assert.equal(f.data.size,1);
});
