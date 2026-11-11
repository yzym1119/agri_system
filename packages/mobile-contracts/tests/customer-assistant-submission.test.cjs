const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-assistant-submission.js');
const key='11111111-1111-4111-8111-111111111111';
function fixture(create){let raw=null,epoch=1,posts=0,state='REFERENCE',fail=true;const f={auth:{current:()=>({kind:'CUSTOMER',epoch,customerId:'101'})},storage:{read:()=>raw,write:(c,i,v)=>raw=v,remove:()=>raw=null},createKey:async()=>key,api:{submit:async(id,command)=>{assert.equal(JSON.parse(raw).phase,'SENT');posts++;if(fail)throw Error('NETWORK_FAILED');return{requestKey:key,caseId:id,state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''}},result:async id=>({requestKey:key,caseId:id,state,queuedVersion:state==='NOT_FOUND'?null:'2',factsVersion:state==='NOT_FOUND'?null:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:state==='REFERENCE'?[{title:'官方软件资料',url:'https://www.moa.gov.cn/a.html',publishedOn:'2026-11-10',quote:'这是一条用于软件验收的资料引用。'}]:[]})}};return{options:f,make:()=>create(f),raw:()=>raw,replace:v=>raw=v,posts:()=>posts,setState:v=>state=v,allow:()=>fail=false,switch:()=>epoch++};}
test('先可靠保存和SENT再发，丢响应与NOT_FOUND保留原稿，不能自动再付费',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
 assert.equal(s.current('7').command.requestKey,key);f.setState('NOT_FOUND');assert.equal((await s.query('7')).state,'NOT_FOUND');assert.equal(f.posts(),1);assert.equal(s.current('7').phase,'SENT');
 await assert.rejects(s.submit('7',{expectedVersion:'2',factsVersion:'1',shareContextConfirmed:true}),/ASSISTANT_PENDING/);
});
test('重建运行时按原号核已完成结果，完整原体重放后才释放，保留已核原号供再读',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);await assert.rejects(f.make().submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));f.allow();const next=f.make();assert.equal((await next.query('7')).state,'REFERENCE');assert.equal(f.posts(),2);assert.equal(next.current('7').phase,'VERIFIED');assert.equal(next.current('7').command.requestKey,key);
});
test('业务拒绝也不能推断AI多阶段未提交，存储替换/失效会话不能另起新号',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);f.options.api.submit=async()=>{throw Error('BUSINESS_REJECTED')};const s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));assert.equal(s.current('7').phase,'SENT');f.replace(null);assert.throws(()=>s.current('7'),/STORAGE_RECORD_CHANGED/);
});
test('可靠存储失败零请求，账号切换后旧结果不能写VERIFIED',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);f.options.storage.write=()=>{throw Error('disk')};await assert.rejects(f.make().submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}),/STORAGE_WRITE_FAILED/);assert.equal(f.posts(),0);
 const g=fixture(createAssistantSubmissions);let resolve;g.options.api.submit=async()=>new Promise(a=>resolve=a);const s=g.make(),work=s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true});await new Promise(r=>setImmediate(r));g.switch();resolve({requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''});await assert.rejects(work,/STALE_SESSION/);assert.equal(JSON.parse(g.raw()).phase,'SENT');
});
test('已确认原稿重放若版本或内容不一致，继续冻结；查询运行中不POST派出',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));f.setState('QUEUED');assert.equal((await s.query('7')).state,'QUEUED');assert.equal(f.posts(),1);
 f.setState('REFERENCE');f.options.api.submit=async()=>({requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'3',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''});await assert.rejects(s.query('7'),/ASSISTANT_RESPONSE_INVALID/);assert.equal(s.current('7').phase,'SENT');
});
test('查询返回伪装QUEUED的数组必须拒绝，不得误判终态后POST派出',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
 f.options.api.result=async()=>({requestKey:key,caseId:'7',state:['QUEUED'],queuedVersion:'2',factsVersion:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]});
 await assert.rejects(s.query('7'));assert.equal(f.posts(),1);assert.equal(s.current('7').phase,'SENT');
});
test('结束响应丢失不解冻，原号重放取消屏障确认后允许同版本新意图',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));let ends=0;
 f.options.api.end=async()=>{ends++;if(ends===1)throw Error('NETWORK_FAILED');return{requestKey:key,caseId:'7',state:'CANCELLED',expectedVersion:'1',factsVersion:'1'}};
 f.options.api.result=async()=>({requestKey:key,caseId:'7',state:'CANCELLED',queuedVersion:null,factsVersion:null,currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]});
 await assert.rejects(s.end('7'));assert.equal(s.current('7').phase,'ENDING');assert.equal((await s.end('7')).state,'CANCELLED');assert.equal(s.current('7').phase,'CANCELLED');
 const next='22222222-2222-4222-8222-222222222222';f.options.createKey=async()=>next;const reloaded=f.make();await assert.rejects(reloaded.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}),/NETWORK_FAILED/);assert.equal(reloaded.current('7').command.requestKey,next);
});
test('已派出只结束等待，query重核结束屏障不重新提交原执行',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
 f.options.api.end=async()=>({requestKey:key,caseId:'7',state:'CLOSED',expectedVersion:'1',factsVersion:'1'});f.setState('CLOSED');
 assert.equal((await s.end('7')).state,'CLOSED');assert.equal(s.current('7').phase,'CLOSED');assert.equal((await s.query('7')).state,'CLOSED');assert.equal(f.posts(),1);
});
test('新设备采用服务器原号只作核对，不创建新号，不覆盖本机未知稿',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();const original={requestKey:key,caseId:'7',state:'QUEUED',queuedVersion:'2',factsVersion:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]};
 await s.adopt('7',original);f.setState('QUEUED');assert.equal((await s.query('7')).state,'QUEUED');assert.equal(f.posts(),0);
 await assert.rejects(s.adopt('7',{...original,requestKey:'22222222-2222-4222-8222-222222222222'}),/ASSISTANT_PENDING/);assert.equal(s.current('7').command.requestKey,key);
});
test('确认结束但请求未到达后重建运行时，retry/query只能重放结束而不能派出原请求',async()=>{
 const {createAssistantSubmissions}=await load();for(const method of ['retry','query']){
  const f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));let calls=0,committed=false;
  f.options.api.end=async()=>{calls++;if(calls===1)throw Error('NETWORK_FAILED');committed=true;return{requestKey:key,caseId:'7',state:'CANCELLED',expectedVersion:'1',factsVersion:'1'}};
  f.options.api.result=async()=>({requestKey:key,caseId:'7',state:committed?'CANCELLED':'NOT_FOUND',queuedVersion:null,factsVersion:null,currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]});
  await assert.rejects(s.end('7'));const restored=f.make();let recovered;try{recovered=await restored[method]('7');}catch{}
  assert.equal(f.posts(),1);assert.equal(calls,2);assert.equal(recovered.state,'CANCELLED');assert.equal(restored.current('7').phase,'CANCELLED');
 }
});
