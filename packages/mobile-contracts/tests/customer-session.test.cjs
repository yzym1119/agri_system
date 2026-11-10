const {test}=require('node:test');
const assert=require('node:assert/strict');
const load=()=>import('../dist/customer-session.js');
const linked=(id=7)=>({state:'LINKED',login:{tokenName:'Authorization',tokenValue:'fictional-customer-'+id,userInfo:{id,name:'验收农户',role:'NORMAL',extra:{bindingState:'LINKED'}}}});
const phone=(state='PHONE_REQUIRED')=>({state,tokenName:'Authorization',tokenValue:'fictional-flow-'+state,expiresAt:1900000000,phoneMasked:state==='PHONE_VERIFIED'?'138****0000':null});
const profile=(id='7')=>({customerId:id,name:'验收农户',phoneMasked:'138****0000',bindingState:'LINKED'});
const ok=data=>({statusCode:200,data});
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}};
test('微信登录仅固定接口，页面快照不包含任何令牌、code和员工权限',async()=>{
 const {createCustomerAuth}=await load();const calls=[];const auth=createCustomerAuth(async r=>{calls.push(r);return ok(linked())},async()=> 'fictional-wx-code');
 await auth.beginWechatLogin().result;assert.equal(auth.current().kind,'CUSTOMER');assert.equal(auth.current().customerId,'7');
 assert.deepEqual(calls[0],{url:'/auth/client/onboarding/wechat',method:'POST',data:{code:'fictional-wx-code'}});
 assert.equal(/fictional|token|permissions|code/.test(JSON.stringify(auth.current())),false);
});
test('隐藏期间晚到微信code不发请求，旧登录响应不覆盖新客户',async()=>{
 const {createCustomerAuth}=await load();let sends=0;const code=deferred();const auth=createCustomerAuth(async()=>{sends++;return ok(linked())},()=>code.promise);
 const attempt=auth.beginWechatLogin();attempt.cancel();code.resolve('late');await assert.rejects(attempt.result,/STALE_SESSION/);assert.equal(sends,0);
 const old=deferred();let count=0;const other=createCustomerAuth(async()=>++count===1?old.promise:ok(linked(8)),async()=> 'code');
 const first=other.beginWechatLogin().result;await new Promise(setImmediate);await other.beginWechatLogin().result;old.resolve(ok(linked(7)));
 await assert.rejects(first,/STALE_SESSION/);assert.equal(other.current().customerId,'8');
});
test('已确认登录后的页面隐藏不退出；临时凭据不能读客户资料',async()=>{
 const {createCustomerAuth}=await load();let sends=0;const auth=createCustomerAuth(async()=>{sends++;return ok(phone())},async()=> 'code');
 const attempt=auth.beginWechatLogin();await attempt.result;attempt.cancel();assert.equal(auth.current().kind,'ONBOARDING');
 await assert.rejects(auth.refreshProfile(),/CUSTOMER_LOGIN_REQUIRED/);assert.equal(sends,1);
});
test('手机号code只提交一次，重复点击拒绝，旧结果不写入新登录',async()=>{
 const {createCustomerAuth}=await load();const delayed=deferred(),calls=[];const auth=createCustomerAuth(async r=>{calls.push(r);return r.url.endsWith('/phone')?delayed.promise:ok(phone())},async()=> 'wx');
 await auth.beginWechatLogin().result;const first=auth.beginPhoneAuthorization('one-use-code');
 assert.throws(()=>auth.beginPhoneAuthorization('another-code'),/AUTH_BUSY/);
 await auth.beginWechatLogin().result;delayed.resolve(ok(phone('PHONE_VERIFIED')));await assert.rejects(first.result,/STALE_SESSION/);
 assert.equal(calls.filter(c=>c.url.endsWith('/phone')).length,1);assert.equal(auth.current().state,'PHONE_REQUIRED');
});
test('本人资料必须同客户；重叠旧401不撤销新刷新或新登录',async()=>{
 const {createCustomerAuth}=await load();let response=ok(profile('8'));const auth=createCustomerAuth(async r=>r.url.endsWith('/wechat')?ok(linked()):response,async()=> 'wx');
 await auth.beginWechatLogin().result;await assert.rejects(auth.refreshProfile(),/CUSTOMER_IDENTITY_CHANGED/);assert.equal(auth.current().kind,'GUEST');
 const a=deferred(),b=deferred();let reads=0;const other=createCustomerAuth(async r=>r.url.endsWith('/wechat')?ok(linked()):++reads===1?a.promise:b.promise,async()=> 'wx');
 await other.beginWechatLogin().result;const old=other.refreshProfile();const next=other.refreshProfile();b.resolve(ok(profile()));await next;a.resolve({statusCode:401});await assert.rejects(old,/STALE_SESSION/);assert.equal(other.current().kind,'CUSTOMER');
});
test('退出先清本机再使用原令牌，原401不退出后来登录',async()=>{
 const {createCustomerAuth}=await load();const delayed=deferred();let auth;auth=createCustomerAuth(async r=>r.url.endsWith('/logout')?(assert.equal(auth.current().kind,'GUEST'),delayed.promise):ok(linked()),async()=> 'wx');
 await auth.beginWechatLogin().result;const pending=auth.logout();await auth.beginWechatLogin().result;delayed.resolve({statusCode:401});await pending;assert.equal(auth.current().kind,'CUSTOMER');
});
test('原申请仅固定UUID/当前临时或客户令牌，成功不自动切成客户',async()=>{
 const {createCustomerAuth}=await load();const key='11111111-1111-4111-8111-111111111111';const calls=[];
 const auth=createCustomerAuth(async r=>{calls.push(r);if(r.url.endsWith('/wechat'))return ok(phone());if(r.url.endsWith('/phone'))return ok(phone('PHONE_VERIFIED'));return ok({requestKey:key,status:'CREATED',nextAction:'LOGIN'})},async()=> 'wx');
 await auth.beginWechatLogin().result;await auth.beginPhoneAuthorization('phone-code').result;const epoch=auth.current().epoch;const result=await auth.register(key,epoch);
 assert.equal(result.status,'CREATED');assert.equal(auth.current().kind,'ONBOARDING');assert.deepEqual(calls[2].data,{requestKey:key});
 assert.equal(calls[2].header.Authorization,'fictional-flow-PHONE_VERIFIED');
 await assert.rejects(auth.register('bad-key',epoch),/CUSTOMER_REQUEST_INVALID/);assert.equal(calls.length,3);
 await auth.beginWechatLogin().result;await assert.rejects(auth.enrollment(key,epoch),/STALE_SESSION/);assert.equal(calls.length,4);
});
test('PHONE_REQUIRED不能注册，未知/错端点阶段和原始平台错误不泄漏',async()=>{
 const {createCustomerAuth}=await load();let sends=0;const auth=createCustomerAuth(async()=>{sends++;return ok(phone())},async()=> 'wx');
 await auth.beginWechatLogin().result;await assert.rejects(auth.register('11111111-1111-4111-8111-111111111111',auth.current().epoch),/PHONE_VERIFICATION_REQUIRED/);assert.equal(sends,1);
 const other=createCustomerAuth(async()=>{throw Error('secret-platform-url')},async()=> 'wx');await assert.rejects(other.beginWechatLogin().result,e=>e.message==='RESULT_UNKNOWN');
});
test('手机号回复不能延长原期限或混入正式登录，结束申请仅清原临时证明',async()=>{
 const {createCustomerAuth}=await load();
 for(const response of [{...phone('PHONE_VERIFIED'),expiresAt:1900000600},linked()]){
  const auth=createCustomerAuth(async r=>ok(r.url.endsWith('/phone')?response:phone()),async()=> 'wx');await auth.beginWechatLogin().result;
  await assert.rejects(auth.beginPhoneAuthorization('code').result,/CUSTOMER_RESPONSE_INVALID/);assert.equal(auth.current().state,'PHONE_REQUIRED');
 }
 const auth=createCustomerAuth(async()=>ok(linked()),async()=> 'wx');await auth.beginWechatLogin().result;const epoch=auth.current().epoch;auth.completeEnrollment(epoch);assert.equal(auth.current().kind,'CUSTOMER');assert.equal(auth.current().epoch,epoch);
});
