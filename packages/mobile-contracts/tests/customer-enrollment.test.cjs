const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-enrollment.js');
const key='11111111-1111-4111-8111-111111111111';
function fixture(overrides={}){let raw=null,epoch=1,calls=0,kind='ONBOARDING';return{port:{auth:{current:()=>({kind,state:'PHONE_VERIFIED',epoch}),completeEnrollment:expected=>{assert.equal(expected,epoch);kind='GUEST';return ++epoch},register:async()=>{calls++;assert.ok(raw);return{requestKey:key,status:'UNKNOWN',nextAction:'RETRY_SAME_REQUEST'}},enrollment:async()=>({requestKey:key,status:'UNKNOWN',nextAction:'RETRY_SAME_REQUEST'})},storage:{read:()=>raw,write:v=>raw=v,remove:()=>raw=null},createKey:async()=>key,...overrides},get raw(){return raw},set raw(v){raw=v},get calls(){return calls},switch(){epoch++}}}
test('申请先保存读回再发，UNKNOWN重复提交始终原UUID，存储不含手机号或令牌',async()=>{
 const {createCustomerEnrollment}=await load();const f=fixture(),manager=createCustomerEnrollment(f.port);
 await manager.submit();await manager.submit();assert.equal(f.calls,2);assert.deepEqual(JSON.parse(f.raw),{schemaVersion:1,requestKey:key});assert.equal(manager.current().requestKey,key);
});
test('已存在申请即使重新建管理器仍同号，REVIEW_REQUIRED保留而完整终态清原记录',async()=>{
 const {createCustomerEnrollment}=await load();const f=fixture();f.raw=JSON.stringify({schemaVersion:1,requestKey:key});
 f.port.auth.enrollment=async()=>({requestKey:key,status:'REVIEW_REQUIRED',nextAction:'CONTACT_STORE'});const manager=createCustomerEnrollment(f.port);await manager.query();assert.ok(f.raw);
 f.port.auth.enrollment=async()=>({requestKey:key,status:'LINKED',nextAction:'LOGIN'});await manager.query();assert.equal(f.raw,null);assert.equal(manager.current(),null);
});
test('存储失败或读回不符时零POST；恢复时损坏记录不猜成无申请',async()=>{
 const {createCustomerEnrollment}=await load();for(const storage of [{read:()=>null,write(){throw Error('STORAGE_WRITE_FAILED')},remove(){}},{read:()=>null,write(){},remove(){}}]){
  const f=fixture({storage});await assert.rejects(createCustomerEnrollment(f.port).submit(),/STORAGE/);assert.equal(f.calls,0);
 }
 const f=fixture();f.raw='broken';assert.throws(()=>createCustomerEnrollment(f.port).current(),/STORAGE/);
});
test('安全随机源等待中切换身份不得写记录，查询中切换不得清原记录',async()=>{
 const {createCustomerEnrollment}=await load();let release;const f=fixture({createKey:()=>new Promise(r=>release=r)}),manager=createCustomerEnrollment(f.port);
 const pending=manager.submit();f.switch();release(key);await assert.rejects(pending,/STALE_SESSION/);assert.equal(f.raw,null);assert.equal(f.calls,0);
 f.raw=JSON.stringify({schemaVersion:1,requestKey:key});f.port.auth.enrollment=()=>new Promise(r=>release=r);const reading=createCustomerEnrollment(f.port).query();f.switch();release({requestKey:key,status:'CREATED',nextAction:'LOGIN'});await assert.rejects(reading,/STALE_SESSION/);assert.ok(f.raw);
});
test('原记录被替换不能覆盖或删除；删除后验证失败不伪造核对完成',async()=>{
 const {createCustomerEnrollment}=await load();const f=fixture(),manager=createCustomerEnrollment(f.port);await manager.submit();f.raw=JSON.stringify({schemaVersion:1,requestKey:'22222222-2222-4222-8222-222222222222'});
 await assert.rejects(manager.submit(),/STORAGE_RECORD_CHANGED/);assert.equal(f.calls,1);
 const other=fixture();other.raw=JSON.stringify({schemaVersion:1,requestKey:key});other.port.auth.enrollment=async()=>({requestKey:key,status:'REJECTED',nextAction:'RESTART'});other.port.storage.remove=()=>{};
 await assert.rejects(createCustomerEnrollment(other.port).query(),/STORAGE_REMOVE_FAILED/);assert.ok(other.raw);
});
test('响应必须原请求和合法终态；临时未核验不能注册，未登录不能查询',async()=>{
 const {createCustomerEnrollment}=await load();const f=fixture();f.port.auth.current=()=>({kind:'ONBOARDING',state:'PHONE_REQUIRED',epoch:1});await assert.rejects(createCustomerEnrollment(f.port).submit(),/PHONE_VERIFICATION_REQUIRED/);assert.equal(f.calls,0);
 const other=fixture();other.raw=JSON.stringify({schemaVersion:1,requestKey:key});other.port.auth.enrollment=async()=>({requestKey:'22222222-2222-4222-8222-222222222222',status:'LINKED',nextAction:'LOGIN'});
 await assert.rejects(createCustomerEnrollment(other.port).query(),/CUSTOMER_REQUEST_MISMATCH/);assert.ok(other.raw);
 const guest=fixture();guest.raw=JSON.stringify({schemaVersion:1,requestKey:key});guest.port.auth.current=()=>({kind:'GUEST',epoch:1});let reads=0;guest.port.auth.enrollment=async()=>{reads++;return null};
 await assert.rejects(createCustomerEnrollment(guest.port).query(),/CUSTOMER_LOGIN_REQUIRED/);assert.equal(reads,0);assert.ok(guest.raw);
});
test('即使页面已离开，完整终态清原记录并废弃临时证明，不能据旧证明新建第二申请',async()=>{
 const {createCustomerEnrollment}=await load();const f=fixture();f.raw=JSON.stringify({schemaVersion:1,requestKey:key});f.port.auth.enrollment=async()=>({requestKey:key,status:'CREATED',nextAction:'LOGIN'});
 const manager=createCustomerEnrollment(f.port),reply=await manager.query();assert.equal(f.port.auth.current().kind,'GUEST');assert.equal(reply.epoch,2);assert.equal(reply.result.status,'CREATED');
 await assert.rejects(manager.submit(),/CUSTOMER_LOGIN_REQUIRED/);assert.equal(f.calls,0);
});
